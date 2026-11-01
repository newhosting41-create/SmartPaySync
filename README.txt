SmartPay Sync v6 (Android)

What it does
- Captures the official bKash "You have received Tk ..." SMS the moment it arrives,
  even with the screen off or the app closed.
- Keeps a foreground service running (persistent notification) and holds a WakeLock only
  while saving/uploading, so battery use stays low.
- Uploads each new payment SMS to your server (bkash_sms_sync.php) with X-Device-Token.
- Retries Pending items every 15 s and every 15 min (WorkManager) if the network was down.
- Checks which TrxIDs were used on the website and marks them Success.

Setup (once)
1. Install the APK. Open the app.
2. Settings: paste Server URL (https://...) and Device Token from the Admin Panel, Save.
3. Allow SMS and notification permission.
4. Set Battery to Unrestricted. Turn on Auto-start (Oppo/Realme/Infinix/Xiaomi/Vivo).
5. Do NOT Force Stop the app.

Logo
- Replace app/src/main/res/drawable/ic_launcher.xml with your logo, or add ic_launcher.png
  and change the manifest icon to @mipmap/ic_launcher.

Build
- Push to GitHub. The workflow builds app-debug.apk and uploads it as an artifact.
