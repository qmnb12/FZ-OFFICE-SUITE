//! Binary-signature and filename-based document format detection.

const ZIP_SIGNATURES: [&[u8]; 3] = [b"PK\x03\x04", b"PK\x05\x06", b"PK\x07\x08"];
const OLE_SIGNATURE: &[u8] = b"\xD0\xCF\x11\xE0\xA1\xB1\x1A\xE1";

pub fn detect_format(file_name: &str, header: &[u8]) -> &'static str {
    if header.starts_with(b"%PDF-") {
        return "PDF";
    }

    let extension = file_name
        .rsplit_once('.')
        .map(|(_, ext)| ext.trim().to_ascii_lowercase())
        .unwrap_or_default();

    if header.starts_with(OLE_SIGNATURE) {
        return match extension.as_str() {
            "doc" | "dot" | "xls" | "ppt" | "pps" => "LEGACY_OFFICE",
            _ => "OLE_CONTAINER",
        };
    }

    let is_zip = ZIP_SIGNATURES.iter().any(|signature| header.starts_with(signature));
    if is_zip {
        return match extension.as_str() {
            "docx" | "dotx" | "docm" => "DOCX",
            "xlsx" | "xlsm" | "xlsb" | "xltx" => "XLSX",
            "pptx" | "pptm" | "ppsx" | "potx" => "PPTX",
            "odt" | "ods" | "odp" => "OPEN_DOCUMENT",
            _ => "ZIP",
        };
    }

    match extension.as_str() {
        "txt" => "TXT",
        "csv" => "CSV",
        "pdf" | "docx" | "xlsx" | "pptx" => "UNKNOWN",
        _ => "UNKNOWN",
    }
}

#[cfg(test)]
mod tests {
    use super::detect_format;

    #[test]
    fn recognizes_pdf_by_signature_even_without_extension() {
        assert_eq!(detect_format("download", b"%PDF-1.7"), "PDF");
    }

    #[test]
    fn does_not_call_a_non_pdf_header_a_pdf() {
        assert_eq!(detect_format("report.pdf", b"not a pdf"), "UNKNOWN");
    }

    #[test]
    fn recognizes_office_zip_containers_by_extension_and_signature() {
        let zip_header = b"PK\x03\x04";
        assert_eq!(detect_format("report.docx", zip_header), "DOCX");
        assert_eq!(detect_format("budget.xlsx", zip_header), "XLSX");
        assert_eq!(detect_format("slides.pptx", zip_header), "PPTX");
    }

    #[test]
    fn rejects_a_mismatched_office_extension_without_zip_signature() {
        assert_eq!(detect_format("report.docx", b"plain text"), "UNKNOWN");
    }
}
