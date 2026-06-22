package main

type Obfuscator interface {
	WrapWithPadding(payload []byte) []byte
	UnwrapWithPadding(raw []byte) ([]byte, error)
	// GenerateChaff создает пакет-пустышку для запутывания таймингов и ML-моделей ТСПУ
	GenerateChaff(maxSize int) []byte 
}