use jni::objects::{JClass, JObject, JString, JValue};
use jni::sys::jstring;
use jni::JNIEnv;
use serde::Deserialize;
use std::collections::HashMap;

mod builder;
mod parsing;
mod radio;
mod schema;

pub struct BuildErr(pub String);

impl From<String> for BuildErr {
    fn from(s: String) -> Self { Self(s) }
}
impl From<&str> for BuildErr {
    fn from(s: &str) -> Self { Self(s.to_string()) }
}
impl From<rusqlite::Error> for BuildErr {
    fn from(e: rusqlite::Error) -> Self { Self(e.to_string()) }
}

pub type BResult<T> = Result<T, BuildErr>;

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BuildRequest {
    pub pack_mobile: bool,
    pub pack_radio: bool,
    pub allowed_service_mask: i32,
    pub data_zip_path: String,
    pub ref_zip_path: Option<String>,
    pub observatoire_csv_path: Option<String>,
    pub arcep_csv_paths: Vec<String>,
    pub built_mobile_path: String,
    pub staging_mobile_path: String,
    pub built_radio_path: String,
    pub staging_radio_path: String,
    pub version: String,
    pub zip_version: String,
    pub quarterly_version: Option<String>,
    pub total_ram_bytes: i64,
    pub communes: HashMap<String, String>,
    pub departments: HashMap<String, DepartmentRef>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DepartmentRef {
    pub code: String,
    pub name: String,
    pub region_code: String,
    pub area_km2: Option<f64>,
    pub population: Option<i64>,
    pub population_year: Option<String>,
}

pub trait ProgressSink {
    fn emit(&mut self, phase_ordinal: i32, percent: i32, detail: Option<&str>, processed: i64);
    fn check_paused_or_cancelled(&mut self) -> Result<(), String>;
}

struct JniProgressSink<'local, 'a> {
    env: &'a mut JNIEnv<'local>,
    callback: &'a JObject<'local>,
}

impl<'local, 'a> ProgressSink for JniProgressSink<'local, 'a> {
    fn emit(&mut self, phase_ordinal: i32, percent: i32, detail: Option<&str>, processed: i64) {
        let j_detail = match detail {
            Some(s) => match self.env.new_string(s) {
                Ok(js) => js.into(),
                Err(_) => JObject::null(),
            },
            None => JObject::null(),
        };
        if self
            .env
            .call_method(
                self.callback,
                "onProgress",
                "(IILjava/lang/String;J)V",
                &[
                    JValue::Int(phase_ordinal),
                    JValue::Int(percent),
                    JValue::Object(&j_detail),
                    JValue::Long(processed),
                ],
            )
            .is_err()
        {
            let _ = self.env.exception_clear();
        }
        let _ = self.env.delete_local_ref(j_detail);
    }

    fn check_paused_or_cancelled(&mut self) -> Result<(), String> {
        let res = self.env.call_method(self.callback, "checkControl", "()I", &[]);
        match res {
            Ok(v) => match v.i() {
                Ok(1) => Err("CANCELLED".to_string()),
                _ => Ok(()),
            },
            Err(_) => {
                let _ = self.env.exception_clear();
                Ok(())
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_fr_geotower_data_build_RustDbBuilder_nativeBuildDatabases<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    config_json: JString<'local>,
    callback: JObject<'local>,
) -> jstring {
    let json_str: String = match env.get_string(&config_json) {
        Ok(s) => s.into(),
        Err(e) => {
            return env
                .new_string(format!("Erreur lecture config JNI: {e}"))
                .map(|s| s.into_raw())
                .unwrap_or(std::ptr::null_mut());
        }
    };

    let req: BuildRequest = match serde_json::from_str(&json_str) {
        Ok(r) => r,
        Err(e) => {
            return env
                .new_string(format!("Erreur JSON config Rust: {e}"))
                .map(|s| s.into_raw())
                .unwrap_or(std::ptr::null_mut());
        }
    };

    let mut sink = JniProgressSink {
        env: &mut env,
        callback: &callback,
    };

    let result = builder::run_build(&req, &mut sink);
    match result {
        Ok(()) => env
            .new_string("")
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        Err(err) => env
            .new_string(err.0)
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut()),
    }
}
