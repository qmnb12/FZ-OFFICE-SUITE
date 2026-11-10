//! Bounded OOXML package inspection.
//!
//! This validates the ZIP container and confirms the expected main part exists.
//! It deliberately does not extract archive entries or trust paths from the file.

use std::fs::File;
use std::io::{Cursor, Read, Seek};
use std::path::Path;

use zip::ZipArchive;

const MAX_ENTRIES: usize = 100_000;
const MAX_TOTAL_UNCOMPRESSED_BYTES: u64 = 1_073_741_824;
const MAX_CONTENT_TYPES_BYTES: u64 = 2 * 1024 * 1024;

pub fn inspect_package_path(path: &Path) -> Result<String, String> {
    let file = File::open(path).map_err(|_| "Could not open document package".to_string())?;
    inspect_archive(file)
}

pub fn inspect_ooxml_bytes(bytes: &[u8]) -> Result<String, String> {
    inspect_archive(Cursor::new(bytes))
}

fn inspect_archive<R: Read + Seek>(reader: R) -> Result<String, String> {
    let mut archive =
        ZipArchive::new(reader).map_err(|_| "File is not a valid ZIP package".to_string())?;

    if archive.len() == 0 || archive.len() > MAX_ENTRIES {
        return Err("Package contains an unsupported number of entries".to_string());
    }

    let mut names = Vec::with_capacity(archive.len());
    let mut total_size = 0_u64;

    for index in 0..archive.len() {
        let entry = archive
            .by_index(index)
            .map_err(|_| "Could not read ZIP directory".to_string())?;
        let name = entry.name().to_string();

        if name.starts_with('/')
            || name.split('/').any(|segment| segment == "..")
            || name.contains('\\')
        {
            return Err("Package contains an unsafe entry path".to_string());
        }

        total_size = total_size.saturating_add(entry.size());
        if total_size > MAX_TOTAL_UNCOMPRESSED_BYTES {
            return Err("Package expands beyond the supported safety limit".to_string());
        }
        names.push(name);
    }

    if !names.iter().any(|name| name == "[Content_Types].xml") {
        return Err("Missing OOXML content types manifest".to_string());
    }

    let mut content_types = String::new();
    {
        let mut manifest = archive
            .by_name("[Content_Types].xml")
            .map_err(|_| "Missing OOXML content types manifest".to_string())?;
        if manifest.size() > MAX_CONTENT_TYPES_BYTES {
            return Err("OOXML content types manifest is too large".to_string());
        }
        manifest
            .read_to_string(&mut content_types)
            .map_err(|_| "OOXML content types manifest is unreadable".to_string())?;
    }

    let candidates = [
        ("word/document.xml", "wordprocessingml", "DOCX"),
        ("xl/workbook.xml", "spreadsheetml", "XLSX"),
        ("ppt/presentation.xml", "presentationml", "PPTX"),
    ];

    for (main_part, content_type_marker, format) in candidates {
        if names.iter().any(|name| name == main_part)
            && content_types.contains(content_type_marker)
        {
            return Ok(format!(
                "{}|valid=true|entries={}|main={}",
                format,
                names.len(),
                main_part
            ));
        }
    }

    Err("ZIP package is not a supported OOXML document".to_string())
}

#[cfg(test)]
mod tests {
    use std::io::{Cursor, Write};

    use zip::write::SimpleFileOptions;
    use zip::ZipWriter;

    use super::inspect_ooxml_bytes;

    fn make_package(entries: &[(&str, &str)]) -> Vec<u8> {
        let cursor = Cursor::new(Vec::new());
        let mut archive = ZipWriter::new(cursor);
        for (name, contents) in entries {
            archive
                .start_file(*name, SimpleFileOptions::default())
                .expect("start ZIP entry");
            archive.write_all(contents.as_bytes()).expect("write ZIP entry");
        }
        archive.finish().expect("finish ZIP package").into_inner()
    }

    #[test]
    fn identifies_a_well_formed_docx_package() {
        let bytes = make_package(&[
            (
                "[Content_Types].xml",
                "<Types><Override ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>",
            ),
            ("word/document.xml", "<w:document/>"),
        ]);
        let result = inspect_ooxml_bytes(&bytes).expect("valid DOCX");
        assert!(result.starts_with("DOCX|valid=true|"));
    }

    #[test]
    fn identifies_a_well_formed_xlsx_package() {
        let bytes = make_package(&[
            (
                "[Content_Types].xml",
                "<Types><Override ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/></Types>",
            ),
            ("xl/workbook.xml", "<workbook/>"),
        ]);
        let result = inspect_ooxml_bytes(&bytes).expect("valid XLSX");
        assert!(result.starts_with("XLSX|valid=true|"));
    }

    #[test]
    fn rejects_a_zip_without_ooxml_manifest() {
        let bytes = make_package(&[("readme.txt", "not an Office document")]);
        assert!(inspect_ooxml_bytes(&bytes).is_err());
    }

    #[test]
    fn rejects_unsafe_archive_paths() {
        let bytes = make_package(&[
            ("[Content_Types].xml", "<Types/>"),
            ("../word/document.xml", "<w:document/>"),
        ]);
        assert!(inspect_ooxml_bytes(&bytes).is_err());
    }
}
