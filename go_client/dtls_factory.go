package main

import (
	"crypto/tls"

	"github.com/pion/dtls/v3"
)

type DTLSConfigFactory struct {
	fingerprint IDTLSFingerprint
}

func NewDTLSConfigFactory(
	fp IDTLSFingerprint,
) *DTLSConfigFactory {
	if fp == nil {
		fp = PionDefaultFingerprint{}
	}

	return &DTLSConfigFactory{
		fingerprint: fp,
	}
}

func (f *DTLSConfigFactory) Build(
	cert tls.Certificate,
) (*dtls.Config, error) {

	cfg := &dtls.Config{
		Certificates: []tls.Certificate{cert},

		InsecureSkipVerify: true,

		ExtendedMasterSecret:
			dtls.RequireExtendedMasterSecret,

		CipherSuites: []dtls.CipherSuiteID{
			dtls.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
		},

		ConnectionIDGenerator:
			dtls.OnlySendCIDGenerator(),
	}

	if err := f.fingerprint.Apply(cfg); err != nil {
		return nil, err
	}

	return cfg, nil
}