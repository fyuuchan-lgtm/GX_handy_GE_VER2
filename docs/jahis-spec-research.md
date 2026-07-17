# JAHIS QR Fragment Research Memo

Date: 2026-06-14

## Overview

This memo captures the current understanding of the vendor-specific JAHIS QR split format used in the scanning flow.

## Key Findings

- JAHIS prescription QR fragments are byte-stream splits, not individually complete records.
- The first fragment carries the `JAHISTC01` header.
- Later fragments can begin mid-record, including drug-name continuations such as `g（ロゼレム）...`.
- Structured Append sequence metadata is the primary fragment ordering source when present.
- The fragment byte arrays must be joined in sequence order before decoding as Windows-31J; decoding each fragment first can corrupt a multibyte character split across symbols.
- Screen-position ordering remains only as a fallback for standalone QR and legacy data without Structured Append metadata.
- The assembler now distinguishes between:
  - `Success`
  - `Incomplete(MISSING_HEADER)`
  - `Incomplete(UNTERMINATED)`
  - `Incomplete(MISSING_USAGE)`
  - `NoHeader`

## Current Implementation Notes

- `BarcodeAnalyzer` uses zxing-cpp first for JAHIS QR so that Structured Append sequence, total count, and group ID are retained; ML Kit remains the fallback.
- `JahisQrAssembler` joins raw fragment bytes in Structured Append order, decodes once, and then performs completeness checks.
- Once a Structured Append group is active, unrelated QR symbols and symbols from another group are ignored so the counter cannot exceed the expected total.
- `ScanScreen` keeps manual parsing available when the assembler returns `Incomplete`.
- `JAHISTC07` is treated as a supported JAHIS header in the current parser path.

## Validation Status

- Unit tests and the debug APK build passed on 2026-07-17.
- A three-symbol medication-information QR that previously failed with a missing RP drug record was successfully parsed on a Pixel 4a after the redesign.

## Next Steps

- Keep the standalone/No.911 fallback covered when adding new QR samples.
- Confirm future vendor samples preserve Structured Append metadata and Windows-31J byte ordering.
