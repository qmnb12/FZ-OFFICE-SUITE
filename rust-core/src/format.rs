//! File-type identification based on signatures, MIME types, and extensions.

/// A document family understood by FZ Office.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum DocumentKind {
    /// Portable Document Format.
    Pdf,
    /// Word-processing Open XML documents.
    Word,
    /// Spreadsheet formats, including CSV and Open XML workbooks.
    Spreadsheet,
    /// PowerPoint Open XML presentations.
    Presentation,
    /// Plain text.
    Text,
    /// A ZIP archive whose document family is not known.
    ZipArchive,
    /// An unsupported or unrecognized file.
    Unknown,
}

/// How a document type was identified.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum DetectionSource {
    /// Recognized binary file signature.
    Signature,
    /// Recognized MIME type supplied by the caller.
    MimeType,
    /// Recognized file extension supplied by the caller.
    Extension,
    /// Valid UTF-8 text content.
    TextContent,
    /// No reliable identification was possible.
    Unknown,
}

/// The identified document family and the evidence used to identify it.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct Detection {
    /// Identified document family.
    pub kind: DocumentKind,
    /// Identification evidence.
    pub source: DetectionSource,
}

/// Identify a document using bytes, an optional file name, and an optional MIME type.
///
/// Binary signatures take priority over caller-supplied metadata. ZIP-based
/// Office formats are distinguished using their extension or MIME type because
/// DOCX, XLSX, and PPTX share the same outer container signature.
pub fn detect_document(
    bytes: &[u8],
    file_name: Option<&str>,
    mime_type: Option<&str>,
) -> Detection {
    if bytes.starts_with(b"%PDF-") {
        return Detection { kind: DocumentKind::Pdf, source: DetectionSource::Signature };
    }

    if is_zip_signature(bytes) {
        let kind = kind_from_name(file_name)
            .or_else(|| kind_from_mime(mime_type))
            .unwrap_or(DocumentKind::ZipArchive);
        return Detection { kind, source: DetectionSource::Signature };
    }

    if let Some(kind) = kind_from_mime(mime_type) {
        return Detection { kind, source: DetectionSource::MimeType };
    }

    if let Some(kind) = kind_from_name(file_name) {
        return Detection { kind, source: DetectionSource::Extension };
    }

    if is_plain_utf8_text(bytes) {
        return Detection { kind: DocumentKind::Text, source: DetectionSource::TextContent };
    }

    Detection { kind: DocumentKind::Unknown, source: DetectionSource::Unknown }
}

fn is_zip_signature(bytes: &[u8]) -> bool {
    bytes.starts_with(b"PK\x03\x04")
        || bytes.starts_with(b"PK\x05\x06")
        || bytes.starts_with(b"PK\x07\x08")
}

fn kind_from_name(file_name: Option<&str>) -> Option<DocumentKind> {
    let name = file_name?.rsplit(['/', '\\']).next()?;
    let extension = name.rsplit_once('.')?.1.to_ascii_lowercase();
    match extension.as_str() {
        "pdf" => Some(DocumentKind::Pdf),
        "docx" | "dotx" => Some(DocumentKind::Word),
        "xlsx" | "xlsm" | "xlsb" | "xltx" | "xls" | "ods" | "csv" | "tsv" => {
            Some(DocumentKind::Spreadsheet)
        }
        "pptx" | "ppsx" | "pptm" | "potx" => Some(DocumentKind::Presentation),
        "txt" | "text" | "md" | "log" => Some(DocumentKind::Text),
        "zip" => Some(DocumentKind::ZipArchive),
        _ => None,
    }
}

fn kind_from_mime(mime_type: Option<&str>) -> Option<DocumentKind> {
    let mime = mime_type?.split(';').next()?.trim().to_ascii_lowercase();
    match mime.as_str() {
        "application/pdf" => Some(DocumentKind::Pdf),
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        | "application/vnd.openxmlformats-officedocument.wordprocessingml.template" => {
            Some(DocumentKind::Word)
        }
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        | "application/vnd.ms-excel"
        | "application/vnd.ms-excel.sheet.macroenabled.12"
        | "application/vnd.oasis.opendocument.spreadsheet"
        | "text/csv"
        | "text/tab-separated-values" => Some(DocumentKind::Spreadsheet),
        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        | "application/vnd.openxmlformats-officedocument.presentationml.slideshow"
        | "application/vnd.ms-powerpoint.presentation.macroenabled.12" => {
            Some(DocumentKind::Presentation)
        }
        "text/plain" | "text/markdown" => Some(DocumentKind::Text),
        "application/zip" => Some(DocumentKind::ZipArchive),
        _ => None,
    }
}

fn is_plain_utf8_text(bytes: &[u8]) -> bool {
    !bytes.is_empty() && !bytes.contains(&0) && std::str::from_utf8(bytes).is_ok()
}
