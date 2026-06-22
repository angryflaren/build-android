package main

import (
	"encoding/json"
	"math/rand"
	"os"

	"github.com/bogdanfinn/tls-client/profiles"
)

type Profile struct {
	UserAgent       string `json:"user_agent"`
	SecChUa         string `json:"sec_ch_ua"`
	SecChUaMobile   string `json:"sec_ch_ua_mobile"`
	SecChUaPlatform string `json:"sec_ch_ua_platform"`
}

type SavedProfile struct {
	Profile
	DeviceJSON string `json:"device_json"`
	BrowserFp  string `json:"browser_fp"`
}

type BrowserProfile struct {
	Profile    Profile
	TLSProfile profiles.ClientProfile
}

const profileFile = "vk_profile.json"

func LoadProfileFromDisk() (*SavedProfile, error) {
	data, err := os.ReadFile(profileFile)
	if err != nil {
		return nil, err
	}

	var sp SavedProfile

	if err := json.Unmarshal(data, &sp); err != nil {
		return nil, err
	}

	return &sp, nil
}

var activeFingerprint = "chrome"

func SetActiveFingerprint(fp string) {
	activeFingerprint = fp
}

func GetActiveFingerprint() string {
	return activeFingerprint
}

var chromeProfiles = []Profile{
	{
		UserAgent:       "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36",
		SecChUa:         `"Chromium";v="146", "Not-A.Brand";v="24", "Google Chrome";v="146"`,
		SecChUaMobile:   "?0",
		SecChUaPlatform: `"Windows"`,
	},
	{
		UserAgent:       "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36",
		SecChUa:         `"Chromium";v="144", "Not-A.Brand";v="8", "Google Chrome";v="144"`,
		SecChUaMobile:   "?0",
		SecChUaPlatform: `"Windows"`,
	},
	{
		UserAgent:       "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36",
		SecChUa:         `"Chromium";v="146", "Not-A.Brand";v="24", "Google Chrome";v="146"`,
		SecChUaMobile:   "?0",
		SecChUaPlatform: `"macOS"`,
	},
}

var firefoxProfiles = []Profile{
	{
		UserAgent:       "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0",
		SecChUa:         "",
		SecChUaMobile:   "?0",
		SecChUaPlatform: `"Windows"`,
	},
}

var androidProfiles = []Profile{
	{
		UserAgent:       "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
		SecChUa:         `"Chromium";v="131", "Google Chrome";v="131", "Not-A.Brand";v="24"`,
		SecChUaMobile:   "?1",
		SecChUaPlatform: `"Android"`,
	},
}

var iosProfiles = []Profile{
	{
		UserAgent:       "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1",
		SecChUa:         "",
		SecChUaMobile:   "?1",
		SecChUaPlatform: `"iOS"`,
	},
}

func getRandomProfile() Profile {
	switch activeFingerprint {

	case "android":
		return androidProfiles[rand.Intn(len(androidProfiles))]

	case "ios":
		return iosProfiles[rand.Intn(len(iosProfiles))]

	case "firefox":
		return firefoxProfiles[rand.Intn(len(firefoxProfiles))]

	default:
		return chromeProfiles[rand.Intn(len(chromeProfiles))]
	}
}

func getRandomBrowserProfile() BrowserProfile {
	switch activeFingerprint {

	case "android":
		return BrowserProfile{
			Profile:    androidProfiles[rand.Intn(len(androidProfiles))],
			TLSProfile: profiles.Chrome_131,
		}

	case "ios":
		return BrowserProfile{
			Profile:    iosProfiles[rand.Intn(len(iosProfiles))],
			TLSProfile: profiles.Safari_IOS_18_0,
		}

	case "firefox":
		return BrowserProfile{
			Profile:    firefoxProfiles[rand.Intn(len(firefoxProfiles))],
			TLSProfile: profiles.Firefox_133,
		}

	default:
		return BrowserProfile{
			Profile:    chromeProfiles[rand.Intn(len(chromeProfiles))],
			TLSProfile: profiles.Chrome_146,
		}
	}
}