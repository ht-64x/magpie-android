# magpie for Android

A thin Android shell for magpie: local AI gateway (OpenAI / Anthropic / Responses / Gemini), its own console in a WebView, plus a phone layer (gateway switch, API tab, WorkBuddy channel, daily check-in).

## Build

1. clone magpie beside this repo: git clone --depth 1 https://github.com/yetone/magpie ../magpie-src
2. cd ../magpie-src; set GOOS=android; set GOARCH=arm64; set CGO_ENABLED=0; go build -tags nogui -trimpath -ldflags "-s -w" -o dist/magpie-android-arm64 .
3. optional: scripts/fetch-preload.ps1 (Bun + WorkBuddy plugin)
4. scripts/build-apk.ps1 -CoreSo ../magpie-src/dist/magpie-android-arm64

Requires JDK 17, Android SDK build-tools 34 + platform 34, Go 1.26.

## Notes

- Android has no /etc/resolv.conf: the app passes system DNS servers as MAGPIE_DNS.
- Bun ships as libbun.so (nativeLibraryDir is the only exec-allowed place).
- No personal data is included; bring your own provider keys and sign-in.

MIT. magpie itself is MIT, by yetone.
