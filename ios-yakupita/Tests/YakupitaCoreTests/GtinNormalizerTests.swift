import XCTest
@testable import YakupitaCore

final class GtinNormalizerTests: XCTestCase {
    func testNormalizeMasterBarcodeKeepsFacilitySpecificCode() {
        XCTAssertEqual(GtinNormalizer.normalizeMasterBarcode(" INHOUSE-001 "), "INHOUSE-001")
    }

    func testNormalizeMasterBarcodeNormalizesCommercialGtin() {
        XCTAssertEqual(GtinNormalizer.normalizeMasterBarcode("4987376861687"), "04987376861687")
    }

    func testNormalizeMasterBarcodeRejectsBlankAndControlCharacters() {
        XCTAssertNil(GtinNormalizer.normalizeMasterBarcode("  "))
        XCTAssertNil(GtinNormalizer.normalizeMasterBarcode("ABC\n123"))
    }

    func testAI01Prefixed16DigitsNormalizesTo14Digits() {
        XCTAssertEqual(GtinNormalizer.normalize("0104987732010087"), "04987732010087")
    }

    func testAI01PrefixedWithTrailingAIsNormalizesTo14Digits() {
        XCTAssertEqual(
            GtinNormalizer.normalize("01049877320100871725123110ABC123"),
            "04987732010087"
        )
    }

    func testAI01WithParenthesesNormalizesTo14Digits() {
        XCTAssertEqual(GtinNormalizer.normalize("(01)04987732010087(17)251231"), "04987732010087")
    }

    func testFullWidthDigitsNormalizeTo14Digits() {
        XCTAssertEqual(GtinNormalizer.normalize("０１０４９８７７３２０１００８７"), "04987732010087")
    }

    func testGTIN14ReturnsAsIs() {
        XCTAssertEqual(GtinNormalizer.normalize("04987224716428"), "04987224716428")
    }

    func testJAN13PrefixesZero() {
        XCTAssertEqual(GtinNormalizer.normalize("4987732010087"), "04987732010087")
    }

    func testUnknownFormatReturnsNil() {
        XCTAssertNil(GtinNormalizer.normalize("12345"))
        XCTAssertNil(GtinNormalizer.normalize(""))
    }

    func testInvalidCheckDigitReturnsNil() {
        XCTAssertNil(GtinNormalizer.normalize("14987732010086"))
    }

    func testInvalidCommercialGtinDoesNotFallBackToFacilityCode() {
        XCTAssertNil(GtinNormalizer.normalizeMasterBarcode("14987732010086"))
        XCTAssertNil(GtinNormalizer.normalizeMasterBarcode("(01)14987732010086"))
    }

    func testFacilityCodeRequiresDigitsAndValidLength() {
        XCTAssertNil(GtinNormalizer.normalizeMasterBarcode("ABC"))
        XCTAssertNil(GtinNormalizer.normalizeMasterBarcode("A1"))
        XCTAssertEqual(GtinNormalizer.normalizeMasterBarcode("ABC-1"), "ABC-1")
    }

    func testIsPackageBarcodeDetectsOnlyPackageGTINs() {
        XCTAssertFalse(GtinNormalizer.isPackageBarcode("04987732010087"))
        XCTAssertTrue(GtinNormalizer.isPackageBarcode("14987376861653"))
        XCTAssertFalse(GtinNormalizer.isPackageBarcode("04987376861687"))
        XCTAssertTrue(GtinNormalizer.isPackageBarcode("24987376861653"))
        XCTAssertFalse(GtinNormalizer.isPackageBarcode("94987376861653"))
        XCTAssertFalse(GtinNormalizer.isPackageBarcode("4987376861687"))
        XCTAssertFalse(GtinNormalizer.isPackageBarcode("99999999999999"))
    }
}
