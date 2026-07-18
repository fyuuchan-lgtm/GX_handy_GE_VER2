import Foundation

public enum GtinNormalizer {
    public static func normalize(_ rawCode: String) -> String? {
        let digits = normalizedDigits(in: rawCode)
        let candidate: String

        if digits.hasPrefix("01"), digits.count >= 16 {
            candidate = substring(digits, from: 2, length: 14)
        } else if digits.count == 14 {
            candidate = digits
        } else if digits.count == 13 {
            candidate = "0" + digits
        } else {
            return nil
        }

        return hasValidCheckDigit(candidate) ? candidate : nil
    }

    public static func normalizeMasterBarcode(_ rawCode: String) -> String? {
        if let gtin = normalize(rawCode) {
            return gtin
        }

        let value = rawCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard (3...64).contains(value.count), !containsControlCharacter(value) else {
            return nil
        }

        let decimalDigits = value.filter(\.isWholeNumber)
        let containsOnlyGtinFormatting = value.allSatisfy {
            $0.isWholeNumber || $0.isWhitespace || $0 == "(" || $0 == ")"
        }
        let looksLikeInvalidGtin = containsOnlyGtinFormatting && (
            decimalDigits.count == 13 ||
                decimalDigits.count == 14 ||
                (decimalDigits.hasPrefix("01") && decimalDigits.count >= 16)
        )

        guard !looksLikeInvalidGtin, !decimalDigits.isEmpty else {
            return nil
        }
        return value
    }

    public static func isPackageBarcode(_ gtin: String) -> Bool {
        let value = gtin.trimmingCharacters(in: .whitespacesAndNewlines)
        guard value.count == 14, value.allSatisfy({ $0.isASCII && $0.isWholeNumber }) else {
            return false
        }
        guard let firstValue = value.first?.asciiValue else { return false }
        return (0x31...0x38).contains(firstValue)
    }

    private static func normalizedDigits(in rawCode: String) -> String {
        var result = ""
        result.reserveCapacity(rawCode.count)

        for scalar in rawCode.unicodeScalars {
            switch scalar.value {
            case 0x30...0x39:
                result.unicodeScalars.append(scalar)
            case 0xFF10...0xFF19:
                guard let ascii = UnicodeScalar(0x30 + scalar.value - 0xFF10) else { continue }
                result.unicodeScalars.append(ascii)
            default:
                continue
            }
        }
        return result
    }

    private static func hasValidCheckDigit(_ gtin: String) -> Bool {
        guard !gtin.isEmpty, gtin.allSatisfy({ $0.isASCII && $0.isWholeNumber }) else {
            return false
        }

        let digits = gtin.compactMap(\.wholeNumberValue)
        guard digits.count == gtin.count, let checkDigit = digits.last else { return false }

        var sum = 0
        var weight = 3
        for digit in digits.dropLast().reversed() {
            sum += digit * weight
            weight = weight == 3 ? 1 : 3
        }
        return checkDigit == (10 - (sum % 10)) % 10
    }

    private static func containsControlCharacter(_ value: String) -> Bool {
        value.unicodeScalars.contains { scalar in
            scalar.properties.generalCategory == .control
        }
    }

    private static func substring(_ value: String, from offset: Int, length: Int) -> String {
        let start = value.index(value.startIndex, offsetBy: offset)
        let end = value.index(start, offsetBy: length)
        return String(value[start..<end])
    }
}
