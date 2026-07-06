# Camera navigation specification 2026-07-06

This document is an addendum to `docs/spec_v0.3.md`.

## Purpose

Keep enough information to rebuild the app behavior if the app source, APK, or development PC is lost.

The specific behavior recorded here is the camera transition from dispensing/fill scan screens to the audit document capture screen.

## Background

Audit document mode is different from dispensing mode and fill mode.

- Dispensing and fill modes read GS1, QR, DataBar, and similar codes through `Preview + ImageAnalysis + BarcodeAnalyzer`.
- Audit document capture takes a still image of the document and then sends it to Japanese OCR, so it uses `Preview + ImageCapture`.

Because `ImageCapture` creates a heavier camera session, audit document capture can take longer to start if the previous screen is still releasing its CameraX use cases.

## Observed behavior

- Home -> Audit document mode starts smoothly, roughly around 1 second on the tested device.
- Dispensing or fill -> audit tab can take around 3 seconds because the previous camera release and the audit `ImageCapture` startup overlap.
- Dispensing or fill -> Home -> Audit document mode starts smoothly because the Home screen gives the previous camera screen time to dispose.

## Required app behavior

When the user taps the audit tab from a screen that is using the camera, the app should internally behave like the Home route was briefly visited.

Required flow:

1. Navigate to `Routes.HOME`.
2. Wait briefly so the previous CameraX use cases can release.
3. Navigate to `Routes.AUDIT_SCAN`.

The user should only need to tap the audit tab once.
The app should not require the user to manually go back to Home first.

The Home -> Audit route should stay direct and should not add the delay.

## Implementation

File:

```text
app/src/main/java/com/example/yakuzaiapp/ui/navigation/AppNavigation.kt
```

Key elements:

```text
CAMERA_RELEASE_NAVIGATION_DELAY_MS = 250L
navigateToAuditAfterCameraRelease()
```

Routes that should use this helper:

- `FillModeScreen.onAuditClick`
- `DispensingScreen.onAuditClick`
- `DispensingPtpScanScreen.onAuditClick`
- `ScanScreen.onAuditClick`

Home screen audit navigation should remain:

```text
navController.navigate(Routes.AUDIT_SCAN)
```

## Verification

Build check:

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon
```

Device checks:

- Home -> Audit document mode starts smoothly.
- Fill -> audit tab starts without freezing.
- Dispensing -> audit tab starts without freezing.
- Dispensing PTP / shared scanner -> audit tab does not leave the previous camera preview active.

## Backup note

This file, `docs/spec_v0.3.md`, `docs/rebuild-blueprint-2026-06-27.md`, and the application source under `app/src/main/java/com/example/yakuzaiapp/` must be committed and pushed to GitHub after each milestone.
