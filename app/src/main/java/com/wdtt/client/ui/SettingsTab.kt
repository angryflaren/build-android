package com.wdtt.client.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wdtt.client.SettingsStore
import com.wdtt.client.TunnelManager
import com.wdtt.client.TunnelService
import com.wdtt.client.WDTTColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }

    val currentDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(currentDensity.density, fontScale = 1f)
    ) {
        SettingsTabContent(context, scope, settingsStore)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTabContent(context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, settingsStore: SettingsStore) {
    val tunnelRunning by TunnelManager.running.collectAsStateWithLifecycle()
    
    var isLinkMode by rememberSaveable { mutableStateOf(true) }
    var linkInput by rememberSaveable { mutableStateOf("") }
    
    var peerInput by rememberSaveable { mutableStateOf("") }
    var vkHash1 by rememberSaveable { mutableStateOf("") }
    var vkHash2 by rememberSaveable { mutableStateOf("") }
    var vkHash3 by rememberSaveable { mutableStateOf("") }
    var vkHash4 by rememberSaveable { mutableStateOf("") }
    var workersInput by rememberSaveable { mutableFloatStateOf(1f) }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var serverDtlsPortInput by rememberSaveable { mutableStateOf("56000") }
    var serverWgPortInput by rememberSaveable { mutableStateOf("56001") }
    var localPortInput by rememberSaveable { mutableStateOf("9000") }

    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLinkMode = settingsStore.wdttLinkMode.first()
        linkInput = settingsStore.wdttLink.first()
        
        peerInput = settingsStore.peer.first()
        val hashes = settingsStore.vkHashes.first().split(",")
        vkHash1 = hashes.getOrElse(0) { "" }
        vkHash2 = hashes.getOrElse(1) { "" }
        vkHash3 = hashes.getOrElse(2) { "" }
        vkHash4 = hashes.getOrElse(3) { "" }
        workersInput = settingsStore.workersPerHash.first().toFloat().coerceIn(1f, 50f)
        passwordInput = settingsStore.connectionPassword.first()
        serverDtlsPortInput = settingsStore.serverDtlsPort.first().toString()
        serverWgPortInput = settingsStore.serverWgPort.first().toString()
        localPortInput = settingsStore.listenPort.first().toString()
        initialized = true
    }

    if (!initialized) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    fun startTunnelService() {
        val finalPeer: String
        val finalHashes: String
        val finalWorkers = workersInput.toInt()
        val finalLocalPort: Int
        val finalPass: String
        val finalDtlsPort: Int
        val finalWgPort: Int

        if (isLinkMode) {
            val cleanLink = linkInput.trim().removePrefix("wdtt://").substringBefore("#")
            val parts = cleanLink.split(":")
            if (parts.size < 5) {
                android.widget.Toast.makeText(context, "Неверный формат ссылки", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            finalPeer = parts[0]
            finalDtlsPort = parts[1].toIntOrNull() ?: 56000
            finalWgPort = parts[2].toIntOrNull() ?: 56001
            finalLocalPort = parts[3].toIntOrNull() ?: 9000
            finalPass = parts[4]
            finalHashes = if (parts.size > 5) parts[5] else ""

            scope.launch {
                settingsStore.saveWdttLink(linkInput)
                settingsStore.saveWdttLinkMode(true)
                settingsStore.save(finalPeer, finalHashes, "", finalWorkers, "udp", finalLocalPort, "", false)
                settingsStore.saveConnectionPassword(finalPass)
                settingsStore.saveManualPortsEnabled(true)
                settingsStore.savePorts(finalDtlsPort, finalWgPort, finalLocalPort)
                settingsStore.saveCaptchaMode("wv")
                settingsStore.saveCaptchaSolveMethod("manual")
                settingsStore.saveWbvCaptchaSolveMethod("manual")
            }
        } else {
            finalPeer = peerInput
            finalHashes = listOf(vkHash1, vkHash2, vkHash3, vkHash4).filter { it.isNotBlank() }.joinToString(",")
            finalLocalPort = localPortInput.toIntOrNull() ?: 9000
            finalPass = passwordInput
            finalDtlsPort = serverDtlsPortInput.toIntOrNull() ?: 56000
            finalWgPort = serverWgPortInput.toIntOrNull() ?: 56001

            scope.launch {
                settingsStore.saveWdttLinkMode(false)
                settingsStore.save(finalPeer, finalHashes, "", finalWorkers, "udp", finalLocalPort, "", false)
                settingsStore.saveConnectionPassword(finalPass)
                settingsStore.saveManualPortsEnabled(true)
                settingsStore.savePorts(finalDtlsPort, finalWgPort, finalLocalPort)
                settingsStore.saveCaptchaMode("wv")
                settingsStore.saveCaptchaSolveMethod("manual")
                settingsStore.saveWbvCaptchaSolveMethod("manual")
            }
        }

        val intent = android.content.Intent(context, TunnelService::class.java).apply {
            action = "START"
            putExtra("peer", "$finalPeer:$finalDtlsPort")
            putExtra("vk_hashes", finalHashes)
            putExtra("secondary_vk_hash", "")
            putExtra("workers_per_hash", finalWorkers)
            putExtra("port", finalLocalPort)
            putExtra("sni", "")
            putExtra("connection_password", finalPass)
            putExtra("captcha_mode", "wv")
            putExtra("captcha_solve_method", "manual")
            putExtra("fingerprint", "chrome")
            putExtra("client_ids", "6287487,8202606")
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
        else context.startService(intent)
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("WDTT Lite - Настройки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        TabRow(
			selectedTabIndex = if (isLinkMode) 0 else 1,
			containerColor = MaterialTheme.colorScheme.surfaceVariant,
			contentColor = MaterialTheme.colorScheme.primary,
			modifier = Modifier.fillMaxWidth().height(48.dp)
		) {
			Tab(
				selected = isLinkMode,
				onClick = { isLinkMode = true },
				text = { Text("По ссылке", fontWeight = FontWeight.SemiBold) }
			)
			Tab(
				selected = !isLinkMode,
				onClick = { isLinkMode = false },
				text = { Text("Вручную", fontWeight = FontWeight.SemiBold) }
			)
		}

        if (isLinkMode) {
            OutlinedTextField(
                value = linkInput,
                onValueChange = { linkInput = it },
                label = { Text("Ссылка-приглашение WDTT") },
                placeholder = { Text("wdtt://ip:dtls:wg:vpn:pass:hash#name") },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            OutlinedTextField(
                value = peerInput,
                onValueChange = { peerInput = it },
                label = { Text("IP-сервера") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = vkHash1,
                onValueChange = { vkHash1 = it },
                label = { Text("VK Хеш 1 (Обязательно)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vkHash2,
                onValueChange = { vkHash2 = it },
                label = { Text("VK Хеш 2 (Опционально)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vkHash3,
                onValueChange = { vkHash3 = it },
                label = { Text("VK Хеш 3 (Опционально)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vkHash4,
                onValueChange = { vkHash4 = it },
                label = { Text("VK Хеш 4 (Опционально)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = { Text("Заданный пароль туннеля") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = serverDtlsPortInput,
                    onValueChange = { serverDtlsPortInput = it },
                    label = { Text("DTLS порт") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = serverWgPortInput,
                    onValueChange = { serverWgPortInput = it },
                    label = { Text("WG порт") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = localPortInput,
                    onValueChange = { localPortInput = it },
                    label = { Text("Локал. порт") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }
        
        Text(
            text = "Мощность (потоки): ${workersInput.toInt()}", 
            fontWeight = FontWeight.SemiBold, 
            color = MaterialTheme.colorScheme.onSurface 
        )
        Slider(
            value = workersInput,
            onValueChange = { workersInput = it },
            valueRange = 1f..50f,
            steps = 48,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (tunnelRunning) {
                    val stopIntent = android.content.Intent(context, TunnelService::class.java).apply { action = "STOP" }
                    context.startService(stopIntent)
                } else {
                    val vpnIntent = android.net.VpnService.prepare(context)
                    if (vpnIntent != null) {
                        android.widget.Toast.makeText(context, "Откройте приложение и выдайте VPN-разрешение", android.widget.Toast.LENGTH_LONG).show()
                    }
                    startTunnelService()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (tunnelRunning) "Остановить" else "Запустить", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

// ═══ Reusable mode chip ═══
@Composable
private fun ProtocolChip(label: String, selected: Boolean, enabled: Boolean = true, isError: Boolean = false, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (isError) MaterialTheme.colorScheme.error else (if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            )
        },
        shape = RoundedCornerShape(16.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurface,
            disabledLabelColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            selectedBorderColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun CompactSteppedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    stepSize: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.38f)
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.55f)
    val thumbStrokeColor = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current
    val thumbRadiusPx = with(density) { 9.dp.toPx() }
    val trackWidthPx = with(density) { 5.dp.toPx() }

    fun snap(raw: Float): Float {
        val min = valueRange.start
        val max = valueRange.endInclusive
        val snapped = (((raw - min) / stepSize).roundToInt() * stepSize) + min
        return snapped.coerceIn(min, max)
    }

    fun positionToValue(x: Float, width: Float): Float {
        val left = thumbRadiusPx
        val right = (width - thumbRadiusPx).coerceAtLeast(left + 1f)
        val fraction = ((x.coerceIn(left, right) - left) / (right - left)).coerceIn(0f, 1f)
        return snap(valueRange.start + fraction * (valueRange.endInclusive - valueRange.start))
    }

    Canvas(
        modifier = modifier
            .height(34.dp)
            .pointerInput(enabled, valueRange, stepSize) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onValueChange(positionToValue(offset.x, size.width.toFloat()))
                }
            }
            .pointerInput(enabled, valueRange, stepSize) {
                if (!enabled) return@pointerInput
                detectDragGestures { change, _ ->
                    onValueChange(positionToValue(change.position.x, size.width.toFloat()))
                }
            }
    ) {
        val centerY = size.height / 2f
        val left = thumbRadiusPx
        val right = size.width - thumbRadiusPx
        val range = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
        val fraction = ((value - valueRange.start) / range).coerceIn(0f, 1f)
        val thumbX = left + (right - left) * fraction

        drawLine(
            color = inactiveColor,
            start = Offset(left, centerY),
            end = Offset(right, centerY),
            strokeWidth = trackWidthPx,
            cap = StrokeCap.Round
        )
        drawLine(
            color = activeColor,
            start = Offset(left, centerY),
            end = Offset(thumbX, centerY),
            strokeWidth = trackWidthPx,
            cap = StrokeCap.Round
        )

        val tickCount = (((valueRange.endInclusive - valueRange.start) / stepSize).roundToInt()).coerceAtLeast(1)
        repeat(tickCount + 1) { index ->
            val tickFraction = index / tickCount.toFloat()
            val tickX = left + (right - left) * tickFraction
            drawCircle(
                color = if (tickX <= thumbX) activeColor else inactiveColor,
                radius = 2.dp.toPx(),
                center = Offset(tickX, centerY)
            )
        }

        drawCircle(
            color = activeColor,
            radius = thumbRadiusPx,
            center = Offset(thumbX, centerY)
        )
        drawCircle(
            color = thumbStrokeColor,
            radius = thumbRadiusPx,
            center = Offset(thumbX, centerY),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )
    }
}

// ═══ Important Info Dialog ═══
@Composable
fun ImportantInfoDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Важная информация", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null)
                    }
                }

                Spacer(Modifier.height(16.dp))

                InfoSection("Капча ВК",
                    "По умолчанию в приложении установлен ручной режим (WBV + РУЧ), но его можно заменить на RJS-АВТ. Это продвинутый автоматический метод решения капчи без всплывающих окон и участия человека, основанный на реверс-инжиниринге JS-кода капчи. Он имитирует действия пользователя в фоновом режиме, обеспечивая бесперебойную работу.\n\nВАЖНО: Если в вашем случае RJS не проходит капчу или выдает ошибки (проблемы со связью или изменения на стороне ВК) — переключитесь обратно в ручной режим."
                )
                InfoSection("Как решать капчу",
                    "Она не сложная: нужно просто потянуть слайдер вправо так, чтобы все элементы (обычно это 3 слова) идеально сошлись в пазле."
                )
                InfoSection("Сетевое окружение",
                    "Отключите другие VPN/Прокси и «Приватный DNS» перед использованием."
                )
                InfoSection("Связь потоков и капч",
                    "Рекомендую выбирать 12-36 потока для меньшего количества капч. Если вам всё равно на частоту ввода капчи в фоне — ставьте 48 и более ради скорости."
                )

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("Понятно")
                }
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, body: String) {
    Spacer(Modifier.height(12.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(4.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(4.dp))
}

private fun roundToGroup(
    value: Float,
    maxW: Float = 96f
): Float {
    return value
        .roundToInt()
        .coerceIn(1, maxW.toInt())
        .toFloat()
}

/** Извлекает хеш из VK ссылки */
private fun stripVkUrlStatic(input: String): String {
    var s = input.trim()
    val lower = s.lowercase()
    val prefixes = listOf(
        "https://vk.com/call/join/",
        "http://vk.com/call/join/",
        "https://m.vk.com/call/join/",
        "http://m.vk.com/call/join/",
        "m.vk.com/call/join/",
        "vk.com/call/join/"
    )
    for (prefix in prefixes) {
        if (lower.startsWith(prefix)) {
            s = s.substring(prefix.length)
            break
        }
    }
    val qIdx = s.indexOf('?')
    if (qIdx != -1) s = s.substring(0, qIdx)
    val hIdx = s.indexOf('#')
    if (hIdx != -1) s = s.substring(0, hIdx)
    return s.trimEnd('/')
}

// ═══ Модальное окно хешей ═══
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HashesDialog(
    hash1: String,
    hash2: String,
    hash3: String,
    hash4: String,
    onSave: (String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var h1 by remember { mutableStateOf(hash1) }
    var h2 by remember { mutableStateOf(hash2) }
    var h3 by remember { mutableStateOf(hash3) }
    var h4 by remember { mutableStateOf(hash4) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 560.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tag, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("VK Хеши", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }

                Text(
                    text = "Больше хешей — выше лимит потоков и лучшее распределение нагрузки.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(
                        Triple("VK Хеш 1 *", h1) { v: String -> h1 = v },
                        Triple("VK Хеш 2", h2) { v: String -> h2 = v },
                        Triple("VK Хеш 3", h3) { v: String -> h3 = v },
                        Triple("VK Хеш 4", h4) { v: String -> h4 = v }
                    ).forEachIndexed { idx, (label, value, onChange) ->
                        val isShort = value.isNotBlank() && value.length < 16
                        OutlinedTextField(
                            value = value,
                            onValueChange = { raw ->
                                val cleaned = raw.filter { c -> c != ' ' && c != '\n' }
                                onChange(stripVkUrlStatic(cleaned))
                            },
                            label = { Text(label) },
                            placeholder = { Text("Ссылка звонка или хеш") },
                            singleLine = true,
                            isError = isShort,
                            supportingText = if (isShort) {
                                { Text("Хеш ${idx + 1} — короткий (мин. 16)", color = MaterialTheme.colorScheme.error) }
                            } else null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                }

                Button(
                    onClick = {
                        onSave(h1, h2, h3, h4)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = h1.isNotBlank() && h1.length >= 16,
                    colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("Сохранить", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ═══ Модальное окно секретов ═══
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecretsDialog(
    settingsStore: SettingsStore,
    initialPassword: String,
    manualPortsEnabled: Boolean,
    initialServerDtlsPort: String,
    initialServerWgPort: String,
    initialLocalPort: String,
    onSaved: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var passwordInput by rememberSaveable { mutableStateOf(initialPassword) }
    var serverDtlsPort by rememberSaveable { mutableStateOf(initialServerDtlsPort.ifBlank { "56000" }) }
    var serverWgPort by rememberSaveable { mutableStateOf(initialServerWgPort.ifBlank { "56001" }) }
    var localPort by rememberSaveable { mutableStateOf(initialLocalPort.ifBlank { "9000" }) }

    fun normalizePort(value: String, fallback: String): String {
        return value.toIntOrNull()?.takeIf { it in 1..65535 }?.toString() ?: fallback
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Секреты", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val isPasswordValid = passwordInput.isNotEmpty() && passwordInput.matches(Regex("^[a-zA-Z0-9_.!?:#/-]+$"))

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it.filter { c -> !c.isWhitespace() } },
                    label = { Text("Заданный пароль туннеля") },
                    placeholder = { Text("Придумайте надежный пароль") },
                    singleLine = true,
                    isError = passwordInput.isNotEmpty() && !isPasswordValid,
                    supportingText = if (passwordInput.isNotEmpty() && !isPasswordValid) {
                        { Text("Разрешены только буквы, цифры и знаки . ! ? : # - _ /", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )

                if (manualPortsEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Порты", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = serverDtlsPort,
                        onValueChange = { serverDtlsPort = it.filter(Char::isDigit).take(5) },
                        label = { Text("Порт сервера DTLS") },
                        placeholder = { Text("56000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = serverWgPort,
                        onValueChange = { serverWgPort = it.filter(Char::isDigit).take(5) },
                        label = { Text("Порт сервера WireGuard") },
                        placeholder = { Text("56001") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = localPort,
                        onValueChange = { localPort = it.filter(Char::isDigit).take(5) },
                        label = { Text("Локальный порт VPN") },
                        placeholder = { Text("9000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val finalDtls = normalizePort(serverDtlsPort, "56000")
                        val finalWg = normalizePort(serverWgPort, "56001")
                        val finalLocal = normalizePort(localPort, "9000")
                        scope.launch {
                            settingsStore.saveConnectionPassword(passwordInput)
                            settingsStore.savePorts(finalDtls.toInt(), finalWg.toInt(), finalLocal.toInt())
                            onSaved(finalDtls, finalWg, finalLocal)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = isPasswordValid,
                    colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("Сохранить", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// extension
private fun androidx.compose.ui.graphics.Color.luminance(): Float {
    val r = red
    val g = green
    val b = blue
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}
