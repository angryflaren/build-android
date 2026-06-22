package main

import (
	"context"
	"log"
	"net"
	"sync"
	"sync/atomic"
	"time"
	"math/rand"
)

var pktPool = sync.Pool{
	New: func() interface{} {
		return make([]byte, 2048)
	},
}

func getPktBuf(size int) []byte {
	b := pktPool.Get().([]byte)
	if cap(b) < size {
		b = make([]byte, size)
	}
	return b[:size]
}

func putPktBuf(b []byte) {
	if cap(b) < 2048 {
		return
	}
	pktPool.Put(b[:cap(b)])
}

const (
	returnChBuf = 384

	// chunkSize — количество последовательных пакетов, отправляемых в один worker
	// перед переключением на следующий.
	//
	// Зачем: при round-robin (chunk=1) каждый пакет летит через разный TURN relay
	// с разным latency, что приводит к reorder на сервере. TCP внутри WireGuard
	// интерпретирует reorder как потери → cwnd collapse → скорость single-flow
	// падает до ~8 KB/s.
	//
	// С chunk=8: пакеты в пределах одного TCP congestion window (~10 пакетов при
	// initial cwnd) уходят через один TURN relay → прилетают по порядку.
	// Reorder возможен только между chunk-границами, что покрывается WG replay
	// window (2048 пакетов).
	//
	// Агрегатная пропускная способность не меняется — все workers загружены
	// равномерно по-прежнему (каждый получает 1/N от общего трафика за время).
	chunkSize = 4
)

type WorkerSlot struct {
	ID     int
	SendCh chan []byte
}

type Dispatcher struct {
	localConn  net.PacketConn
	clientAddr atomic.Pointer[net.Addr]
	workers    atomic.Pointer[[]*WorkerSlot]
	mu         sync.Mutex // Используется только для записи
	rrIndex    int
	rrCount    int
	ReturnCh   chan []byte
	ctx        context.Context
	cancel     context.CancelFunc
	wg         sync.WaitGroup
	stats      *Stats
	obfs       Obfuscator
}

func NewDispatcher(ctx context.Context, localConn net.PacketConn, stats *Stats, obfs Obfuscator) *Dispatcher {
	dctx, dcancel := context.WithCancel(ctx)
	d := &Dispatcher{
		localConn: localConn,
		ReturnCh:  make(chan []byte, returnChBuf),
		ctx:       dctx,
		cancel:    dcancel,
		stats:     stats,
		obfs:      obfs,
	}
	
	empty := make([]*WorkerSlot, 0)
	d.workers.Store(&empty)

	d.wg.Add(3)
	go d.readLoop()
	go d.writeLoop()
	go d.chaffLoop()
	return d
}

// Инкапсулированный алгоритм генерации шума
func (d *Dispatcher) chaffLoop() {
	defer d.wg.Done()
	for {
		select {
		case <-d.ctx.Done():
			return
		case <-time.After(time.Duration(20 + rand.Intn(200)) * time.Millisecond):
			addrPtr := d.clientAddr.Load()
			if addrPtr != nil && d.obfs != nil {
				chaffPkt := d.obfs.GenerateChaff(1280) // Мусорный пакет случайного размера
				if len(chaffPkt) > 0 {
					d.localConn.WriteTo(chaffPkt, *addrPtr)
				}
		}
		}
	}
}

func (d *Dispatcher) Shutdown() {
	d.cancel()
	d.wg.Wait()
}

func (d *Dispatcher) Register(w *WorkerSlot) {
	d.mu.Lock()
	defer d.mu.Unlock()
	oldWorkers := d.workers.Load()
	newWorkers := make([]*WorkerSlot, len(*oldWorkers)+1)
	copy(newWorkers, *oldWorkers)
	newWorkers[len(*oldWorkers)] = w
	d.workers.Store(&newWorkers)
	log.Printf("[ДИСП] Воркер #%d зарегистрирован (всего: %d)", w.ID, len(newWorkers))
}

func (d *Dispatcher) Unregister(slot *WorkerSlot) {
	d.mu.Lock()
	defer d.mu.Unlock()
	oldWorkers := d.workers.Load()
	newWorkers := make([]*WorkerSlot, 0, len(*oldWorkers))
	for _, w := range *oldWorkers {
		if w != slot {
			newWorkers = append(newWorkers, w)
		}
	}
	d.workers.Store(&newWorkers)
	log.Printf("[ДИСП] Воркер #%d отключён (осталось: %d)", slot.ID, len(newWorkers))
}

// readLoop читает WireGuard-пакеты и распределяет по workers chunk'ами.
//
// Логика: отправляем chunkSize подряд пакетов в один worker, потом переходим
// к следующему. Если текущий worker перегружен (канал полный) — немедленно
// ищем свободный worker и начинаем новый chunk на нём. Это гарантирует:
//   - В рамках chunk пакеты идут через один TURN relay → in-order delivery
//   - Между chunks — разные relay → максимальная агрегатная скорость
//   - Нет блокировки, нет буферизации, нет дополнительного latency
func (d *Dispatcher) readLoop() {
	defer d.wg.Done()

	for {
		if err := d.ctx.Err(); err != nil {
			return
		}

		pkt := getPktBuf(2048)

		n, addr, err := d.localConn.ReadFrom(pkt)
		if err != nil {
			putPktBuf(pkt)
			if d.ctx.Err() != nil {
				return
			}
			time.Sleep(10 * time.Millisecond)
			continue
		}
		pkt = pkt[:n]

		d.clientAddr.Store(&addr)
		atomic.AddInt64(&d.stats.TotalBytesUp, int64(n))

		workersPtr := d.workers.Load()
		if workersPtr == nil || len(*workersPtr) == 0 {
			putPktBuf(pkt)
			continue
		}

		ws := *workersPtr
		nw := len(ws)

		sent := false
		idx := d.rrIndex % nw

		// Пробуем текущий worker (chunk affinity)
		w := ws[idx]
		select {
		case w.SendCh <- pkt:
			sent = true
			d.rrCount++
			if d.rrCount >= chunkSize {
				d.rrIndex = (idx + 1) % nw
				d.rrCount = 0
			}
		default:
			// Текущий worker перегружен — ищем свободный, начинаем новый chunk
			for i := 1; i < nw; i++ {
				altIdx := (idx + i) % nw
				select {
				case ws[altIdx].SendCh <- pkt:
					sent = true
					d.rrIndex = altIdx
					d.rrCount = 1 // первый пакет нового chunk'а уже отправлен
				default:
				}
				if sent {
					break
				}
			}
		}

		if !sent {
			// Все workers перегружены — сдвигаем указатель, пакет дропается
			d.rrIndex = (idx + 1) % nw
			d.rrCount = 0
			putPktBuf(pkt)
		}
	}
}

func (d *Dispatcher) writeLoop() {
	defer d.wg.Done()

	for {
		select {
		case <-d.ctx.Done():
			return
		case pkt := <-d.ReturnCh:
			addrPtr := d.clientAddr.Load()
			if addrPtr == nil {
				putPktBuf(pkt)
				continue
			}
			addr := *addrPtr
			var outPkt []byte
			if d.obfs != nil {
				outPkt = d.obfs.WrapWithPadding(pkt)
			} else {
				outPkt = pkt
			}
			
			if _, err := d.localConn.WriteTo(outPkt, addr); err != nil {
				if d.ctx.Err() != nil {
					putPktBuf(pkt)
					return
				}
			}
			
			
			atomic.AddInt64(&d.stats.TotalBytesDown, int64(len(pkt)))
			putPktBuf(pkt)
		}
	}
}
