//! JNI entry points for the Android application.

use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::{JByteArray, JClass, JString};
use jni::EnvUnowned;

use crate::format::detect_format;
use crate::package::inspect_package_path;
use crate::ENGINE_VERSION;

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_fz_office_engine_FzNative_nativeVersion<'local>(
    mut unowned_env: EnvUnowned<'local>,
    _class: JClass<'local>,
) -> JString<'local> {
    unowned_env
        .with_env(|env| JString::from_str(env, ENGINE_VERSION))
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_fz_office_engine_FzNative_nativeDetectFormat<'local>(
    mut unowned_env: EnvUnowned<'local>,
    _class: JClass<'local>,
    file_name: JString<'local>,
    header: JByteArray<'local>,
) -> JString<'local> {
    unowned_env
        .with_env(|env| {
            let file_name = file_name.to_string();
            let header = env.convert_byte_array(&header)?;
            let detected = detect_format(&file_name, &header);
            JString::from_str(env, detected)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_fz_office_engine_FzNative_nativeInspectPackage<'local>(
    mut unowned_env: EnvUnowned<'local>,
    _class: JClass<'local>,
    file_path: JString<'local>,
) -> JString<'local> {
    unowned_env
        .with_env(|env| {
            let path = std::path::PathBuf::from(file_path.to_string());
            let result = inspect_package_path(&path)
                .unwrap_or_else(|error| format!("ERROR|{}", error));
            JString::from_str(env, result)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}
