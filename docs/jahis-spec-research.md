# JAHIS QR Fragment Research Memo

Date: 2026-06-14

## Overview

This memo captures the current understanding of the vendor-specific JAHIS QR split format used in the scanning flow.

## Key Findings

- JAHIS prescription QR fragments are byte-stream splits, not individually complete records.
- The first fragment carries the `JAHISTC01` header.
- Later fragments can begin mid-record, including drug-name continuations such as `g（ロゼレム）...`.
- Fragments must be concatenated in left-to-right order.
- Structured Append metadata is not reliable for this format in the observed data.
- The assembler now distinguishes between:
  - `Success`
  - `Incomplete(MISSING_HEADER)`
  - `Incomplete(UNTERMINATED)`
  - `Incomplete(MISSING_USAGE)`
  - `NoHeader`

## Current Implementation Notes

- `BarcodeAnalyzer` accepts JAHIS-like fragments rather than rejecting partial fragments too early.
- `JahisQrAssembler` uses fragment order and completeness checks after concatenation.
- `ScanScreen` keeps manual parsing available when the assembler returns `Incomplete`.
- `JAHISTC07` is treated as a supported JAHIS header in the current parser path.

## Validation Status

- Source changes for the fragment split redesign are in place.
- Local Gradle verification is currently blocked by Android Gradle Plugin resolution for `com.android.application:8.5.2`.
- No release decision should be made from this memo alone; device verification still remains the final gate.

## Next Steps

- Re-run Gradle verification in an environment that can resolve AGP 8.5.2.
- Validate the manual Parse flow for SA-less fragments on device.
- Confirm that `Success` only occurs when a complete prescription is present.

