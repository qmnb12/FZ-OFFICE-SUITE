//! Resource limits used by parsers to reject hostile or excessive inputs.

/// Configurable limits for document loading and archive expansion.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct ResourceLimits {
    /// Maximum source document size in bytes.
    pub max_input_bytes: u64,
    /// Maximum total uncompressed archive size in bytes.
    pub max_expanded_bytes: u64,
    /// Maximum number of entries in an OOXML archive.
    pub max_archive_entries: u32,
}

impl Default for ResourceLimits {
    fn default() -> Self {
        Self {
            max_input_bytes: 256 * 1024 * 1024,
            max_expanded_bytes: 512 * 1024 * 1024,
            max_archive_entries: 20_000,
        }
    }
}

impl ResourceLimits {
    /// Check the source document size.
    pub fn check_input(&self, actual_bytes: u64) -> Result<(), LimitError> {
        if actual_bytes > self.max_input_bytes {
            return Err(LimitError::InputTooLarge { limit: self.max_input_bytes, actual: actual_bytes });
        }
        Ok(())
    }

    /// Check cumulative uncompressed archive size.
    pub fn check_expanded(&self, actual_bytes: u64) -> Result<(), LimitError> {
        if actual_bytes > self.max_expanded_bytes {
            return Err(LimitError::ExpandedDataTooLarge { limit: self.max_expanded_bytes, actual: actual_bytes });
        }
        Ok(())
    }

    /// Check the number of archive entries.
    pub fn check_archive_entries(&self, actual_entries: u32) -> Result<(), LimitError> {
        if actual_entries > self.max_archive_entries {
            return Err(LimitError::TooManyArchiveEntries { limit: self.max_archive_entries, actual: actual_entries });
        }
        Ok(())
    }
}

/// A resource-limit violation detected before parsing a document.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum LimitError {
    /// Source document is larger than the configured limit.
    InputTooLarge { limit: u64, actual: u64 },
    /// Expanded archive data is larger than the configured limit.
    ExpandedDataTooLarge { limit: u64, actual: u64 },
    /// Archive contains more entries than the configured limit.
    TooManyArchiveEntries { limit: u32, actual: u32 },
}
