# Download Distribution

Yakupita is distributed through the fixed GitHub raw file URL below.

```text
https://raw.githubusercontent.com/fyuuchan-lgtm/GX_handy_GE_VER2/master/docs/landing/yakupita/downloads/yakupita-latest.apk
```

Send this URL in LINE or show the QR code on `docs/landing/yakupita/index.html`.
Android will still show its normal APK download and install confirmation screens.

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

`.github/workflows/publish-latest-apk.yml` can also build a release APK and overwrite the
`yakupita-latest.apk` asset on the `latest` GitHub Release if release-based distribution is restored later.

Configure these repository secrets before running the workflow:

```text
RELEASE_KEYSTORE_BASE64
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

`RELEASE_KEYSTORE_BASE64` is the base64-encoded Android release keystore. The
workflow decodes it into a temporary file and passes the signing values to
Gradle through environment variables.
