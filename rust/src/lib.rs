use std::{path::Path, sync::LazyLock};

use flexi_logger::{opt_format, Age, Cleanup, Criterion, FileSpec, Logger, Naming, WriteMode};
use log::info;


uniffi::setup_scaffolding!();

pub static RUNTIME: LazyLock<tokio::runtime::Runtime> = LazyLock::new(|| {
    info!("creating runner");
    // A single worker head-of-line blocks the APS socket behind whatever
    // else is running (journal writes, CloudKit pages, foreign callbacks).
    // Keep the pool small — this is a phone — but never one thread.
    let workers = std::thread::available_parallelism()
        .map_or(2, |cores| cores.get().clamp(2, 4));
    tokio::runtime::Builder::new_multi_thread()
        .worker_threads(workers)
        .thread_name("tokio-rustpush")
        .enable_all()
        .build().unwrap()
});

pub mod bbhwinfo {
    include!(concat!(env!("OUT_DIR"), "/bbhwinfo.rs"));
}

pub fn init_logger(path: &Path) {
    // Every record is formatted and fanned out to both sinks (logcat + the
    // rotating file), and rustpush's hot paths log heavily at debug (full
    // payload hex dumps, per-page sync traces). Release builds cap at Info so
    // debug!/trace! arguments are never even evaluated; debug builds keep
    // the full firehose.
    let max_level = if cfg!(debug_assertions) { log::Level::Debug } else { log::Level::Info };
    let level_spec = if cfg!(debug_assertions) { "debug" } else { "info" };

    #[cfg(target_os = "android")]
    let system = android_logger::AndroidLogger::new(
        android_logger::Config::default().with_max_level(max_level.to_level_filter()),
    );
    #[cfg(not(target_os = "android"))]
    let system = {
        if let Err(_) = std::env::var("RUST_LOG") {
            std::env::set_var("RUST_LOG", level_spec);
        }
        pretty_env_logger::formatted_builder()
            .build()
    };

    let (logger, _) = Logger::try_with_str(level_spec).expect("No logger?")
        .log_to_file(FileSpec::default().directory(path.join("logs")).suppress_timestamp())
        .append()
        .format(opt_format)
        .cleanup_in_background_thread(true)
        .rotate(Criterion::AgeOrSize(Age::Day, 1024 * 1024 * 10 /* 10 MB */), Naming::Numbers, Cleanup::KeepLogFiles(1))
        .write_mode(WriteMode::BufferAndFlush)
        .build().unwrap();

    let _ = multi_log::MultiLogger::init(vec![Box::new(system), logger], max_level);

    // Rust's default panic hook writes to stderr, which is discarded on Android.
    // Route panics through `log` so they reach logcat (and the file logger above).
    let default_hook = std::panic::take_hook();
    std::panic::set_hook(Box::new(move |info| {
        let backtrace = std::backtrace::Backtrace::force_capture();
        // `info` Display includes "panicked at <file>:<line>:<col>:\n<message>".
        log::error!("RUST PANIC: {info}\n{backtrace}");
        default_hook(info);
    }));
}

mod native;
pub mod api;

mod keystore;
mod uniffi_ext;
mod frb_generated; /* AUTO INJECTED BY flutter_rust_bridge. This line may not be accurate, and you can change it according to your needs. */

/// Runs the complete Apple validation handshake without account credentials.
///
/// This debug-only entry point is used by the Android device smoke test. A
/// positive result is the generated validation-envelope length; negative values
/// indicate setup, network, or compatibility-backend failures.
#[cfg(all(target_os = "android", debug_assertions))]
#[no_mangle]
pub extern "C" fn openbubbles_debug_nac_round_trip() -> i32 {
    use rustpush::macos::{HardwareConfig, MacOSConfig};
    use rustpush::OSConfig;

    fn bytes(value: &str) -> Vec<u8> {
        value
            .as_bytes()
            .chunks_exact(2)
            .map(|pair| {
                let text = std::str::from_utf8(pair).expect("ASCII hex fixture");
                u8::from_str_radix(text, 16).expect("valid hex fixture")
            })
            .collect()
    }

    let run = std::panic::catch_unwind(|| {
        let config = MacOSConfig {
            inner: HardwareConfig {
                product_name: "MacBookAir8,1".to_owned(),
                io_mac_address: [0xa4, 0x83, 0xe7, 0x11, 0x47, 0x1c],
                platform_serial_number: "C02YT1YMJK7M".to_owned(),
                platform_uuid: "11D299A5-CF0B-544D-BAD3-7AC7A6E452D7".to_owned(),
                root_disk_uuid: "FCDB63B5-D208-4AEE-B368-3DE952B911FF".to_owned(),
                board_id: "Mac-827FAC58A8FDFA22".to_owned(),
                os_build_num: "22G120".to_owned(),
                platform_serial_number_enc: bytes("737919efe5a87f17236814c89c90b0495d"),
                platform_uuid_enc: bytes("8c682e3f79901fe570e9d3005aba993a79"),
                root_disk_uuid_enc: bytes("56f24e0c0b2e491746425841e643e07f63"),
                rom: bytes("57d04d9dd686"),
                rom_enc: bytes("c16175da07225f337ff7a6c8b7fb9f4c1d"),
                mlb: "C02923200KVKN3YAG".to_owned(),
                mlb_enc: bytes("08aa7844e8889c160076f9dbadb3638e43"),
            },
            version: "13.6.4".to_owned(),
            protocol_version: 1660,
            device_id: uuid::Uuid::new_v4().to_string(),
            icloud_ua: "com.apple.iCloudHelper/282 CFNetwork/1408.0.4 Darwin/22.5.0".to_owned(),
            aoskit_version: "com.apple.AOSKit/282 (com.apple.accountsd/113)".to_owned(),
            udid: Some(uuid::Uuid::new_v4().simple().to_string().to_uppercase()),
        };

        RUNTIME.block_on(config.generate_validation_data())
    });

    match run {
        Ok(Ok(validation)) => i32::try_from(validation.len()).unwrap_or(-3),
        Ok(Err(error)) => {
            log::error!("debug NAC round trip failed: {error:?}");
            -1
        }
        Err(_) => -2,
    }
}

/// Runs the account-free Apple validation handshake using this installation's
/// saved hardware identity. The path is supplied by the debug Android receiver
/// and is never logged; only the resulting envelope length or an error code is
/// returned to ADB.
#[cfg(all(target_os = "android", debug_assertions))]
#[no_mangle]
pub unsafe extern "C" fn openbubbles_debug_nac_round_trip_saved(
    path: *const std::ffi::c_char,
) -> i32 {
    if path.is_null() {
        return -5;
    }

    // SAFETY: JNA supplies a NUL-terminated string which remains valid for the
    // duration of this call. Copy it before entering the async handshake.
    let Ok(path) = unsafe { std::ffi::CStr::from_ptr(path) }.to_str() else {
        return -5;
    };
    let Some(hardware) = api::api::read_hardware(path.to_owned()) else {
        log::error!("debug saved-config NAC round trip found no readable hardware state");
        return -5;
    };

    let run = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        RUNTIME.block_on(hardware.os_config.generate_validation_data())
    }));

    match run {
        Ok(Ok(validation)) => i32::try_from(validation.len()).unwrap_or(-3),
        Ok(Err(error)) => {
            log::error!("debug saved-config NAC round trip failed: {error:?}");
            -1
        }
        Err(_) => -2,
    }
}
