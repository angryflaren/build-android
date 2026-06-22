package main

import "github.com/pion/dtls/v3"

type IDTLSFingerprint interface {
	Name() string
	Apply(*dtls.Config) error
}

type PionDefaultFingerprint struct{}

func (f PionDefaultFingerprint) Name() string {
	return "pion-default"
}

func (f PionDefaultFingerprint) Apply(cfg *dtls.Config) error {
	return nil
}