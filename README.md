# SWO POS Android App

Native Android WebView shell for `pos.socialwifionline.com` with silent Senraise thermal printing.

## How it works

1. Loads `pos.socialwifionline.com` in a full-screen WebView
2. Injects `window.NATIVE_PRINT = true` so the PWA knows it's running natively
3. PWA calls `window.NativePrinter.print(code, plan, venue, validity)` instead of `window.open`
4. Native app calls the Senraise AIDL printer service → silent print, no dialog

## Build via GitHub Actions

Push this repo to GitHub → Actions auto-builds the APK → download from Artifacts tab.

## POS PWA change needed

In `pos.socialwifionline.com/app.jsx`, replace the `window.open(slipUrl)` call with:

```javascript
if (window.NATIVE_PRINT && window.NativePrinter) {
  if (vouchers.length === 1) {
    window.NativePrinter.print(vouchers[0].code, plan.name, session.venue?.name || '', fmtDuration(plan.duration_hours))
  } else {
    const codes = JSON.stringify(vouchers.map(v => v.code))
    window.NativePrinter.printMultiple(codes, plan.name, session.venue?.name || '', fmtDuration(plan.duration_hours))
  }
} else {
  // fallback for browser
  vouchers.forEach((v, i) => {
    setTimeout(() => window.open(`${API}/api/pos/vouchers/${v.id}/slip?token=${session.token}`, '_blank'), i * 1200)
  })
}
```

## Project structure

```
swo-pos-apk/
├── app/src/main/
│   ├── aidl/recieptservice/com/recieptservice/PrinterInterface.aidl
│   ├── java/com/swopos/MainActivity.kt
│   ├── res/
│   └── AndroidManifest.xml
├── .github/workflows/build.yml
├── build.gradle
└── settings.gradle
```
