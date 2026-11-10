//! FZ Office's native document engine.
//!
//! Rendering and format readers are migrated incrementally. Keep this crate
//! independent from Android UI code so its document logic can be tested on the host.

pub mod format;
pub mod package;

#[cfg(target_os = "android")]
mod jni_bridge;

pub const ENGINE_VERSION: &str = env!("CARGO_PKG_VERSION");
