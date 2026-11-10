#![forbid(unsafe_code)]
#![deny(missing_docs)]

//! Shared, dependency-free foundations for the FZ Office document engines.
//! This crate is independent of Android and UI code.

mod format;
mod geometry;
mod limits;

pub use format::{detect_document, Detection, DetectionSource, DocumentKind};
pub use geometry::{GeometryError, Margins, PageGeometry, PageSize};
pub use limits::{LimitError, ResourceLimits};

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pdf_signature_wins_over_a_misleading_extension() {
        let result = detect_document(b"%PDF-1.7\n", Some("report.docx"), None);
        assert_eq!(result.kind, DocumentKind::Pdf);
        assert_eq!(result.source, DetectionSource::Signature);
    }

    #[test]
    fn office_zip_uses_known_extension() {
        let result = detect_document(b"PK\x03\x04payload", Some("sheet.xlsx"), None);
        assert_eq!(result.kind, DocumentKind::Spreadsheet);
        assert_eq!(result.source, DetectionSource::Signature);
    }

    #[test]
    fn unknown_zip_stays_a_generic_archive() {
        let result = detect_document(b"PK\x03\x04payload", Some("backup.zip"), None);
        assert_eq!(result.kind, DocumentKind::ZipArchive);
    }

    #[test]
    fn mime_detection_is_case_insensitive_and_ignores_parameters() {
        let result = detect_document(
            b"",
            None,
            Some("Application/Vnd.Openxmlformats-officedocument.wordprocessingml.document; charset=binary"),
        );
        assert_eq!(result.kind, DocumentKind::Word);
        assert_eq!(result.source, DetectionSource::MimeType);
    }

    #[test]
    fn extension_detection_is_case_insensitive() {
        let result = detect_document(b"", Some("PRESENTATION.PPTX"), None);
        assert_eq!(result.kind, DocumentKind::Presentation);
        assert_eq!(result.source, DetectionSource::Extension);
    }

    #[test]
    fn devanagari_plain_text_is_detected() {
        let result = detect_document("नमस्ते FZ Office".as_bytes(), None, None);
        assert_eq!(result.kind, DocumentKind::Text);
        assert_eq!(result.source, DetectionSource::TextContent);
    }

    #[test]
    fn page_geometry_rejects_margins_that_consume_the_page() {
        let page = PageSize::from_twips(12_240, 15_840).expect("valid page");
        let margins = Margins::from_twips(1_440, 1_440, 1_440, 1_440)
            .expect("valid margins");
        assert!(PageGeometry::new(page, margins).is_ok());
        let invalid = Margins::from_twips(20_000, 0, 0, 0).expect("non-negative margins");
        assert_eq!(
            PageGeometry::new(page, invalid),
            Err(GeometryError::MarginsExceedPage)
        );
    }

    #[test]
    fn resource_limits_reject_oversized_input() {
        let limits = ResourceLimits::default();
        assert!(limits.check_input(limits.max_input_bytes).is_ok());
        assert!(limits.check_input(limits.max_input_bytes + 1).is_err());
    }
}
