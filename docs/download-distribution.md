# Download Distribution

Yakupita is distributed through the fixed GitHub raw file URL below.

```text
https://raw.githubusercontent.com/fyuuchan-lgtm/GX_handy_GE_VER2/master/docs/landing/yakupita/downloads/yakupita-latest.apk
```

Send this URL in LINE, show the QR code on `docs/landing/yakupita/index.html`,
or open `docs/landing/yakupita/qr.html` when you want a large QR code for scanning.
Android will still show its normal APK download and install confirmation screens.

## QR Install Flow

1. Open `docs/landing/yakupita/qr.html` on a PC or tablet.
2. Scan the QR code with the Android device camera.
3. Download `yakupita-latest.apk`.
4. Allow installation from the browser or camera app if Android asks for permission.
5. Open the downloaded APK and install Yakupita.

## Build Locally

```powershell
.\gradlew.bat :app:publishDownloadApk -PdownloadApkVariant=release
```

The APK is copied to:

```text
build/distributions/yakupita-latest.apk
```

Copy the APK to the public download path before committing:

```powershell
Copy-Item -LiteralPath build\distributions\yakupita-latest.apk -Destination docs\landing\yakupita\downloads\yakupita-latest.apk -Force
```

## GitHub Actions

`.github/workflows/publish-latest-apk.yml` runs when app, Gradle, or workflow files are pushed to
`master` or `main`.

The workflow:

1. Builds a release APK.
2. Copies it to `docs/landing/yakupita/downloads/yakupita-latest.apk`.
3. Commits and pushes that APK back to the repository when it changed.
4. Uploads the same APK to the `latest` GitHub Release asset.

For production signing, configure these repository secrets:

```text
RELEASE_KEYSTORE_BASE64
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

`RELEASE_KEYSTORE_BASE64` is the base64-encoded Android release keystore. The
workflow decodes it into a temporary file and passes the signing values to
Gradle through environment variables.

If the production signing secrets are incomplete, the workflow falls back to
the Android debug keystore and sets `allowDebugReleaseSigning=true` so the
public APK URL is still refreshed after app changes. Use release signing
secrets before sharing APKs outside internal testing.
