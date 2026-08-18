use std::{path::Path, sync::LazyLock};

use flexi_logger::{opt_format, Age, Cleanup, Criterion, FileSpec, Logger, Naming, WriteMode};
use log::info;


uniffi::setup_scaffolding!();

pub static RUNTIME: LazyLock<tokio::runtime::Runtime> = LazyLock::new(|| {
    info!("creating runner");
    tokio::runtime::Builder::new_multi_thread()
        .worker_threads(1)
        .thread_name("tokio-rustpush")
        .enable_all()
        .build().unwrap()
});

pub mod bbhwinfo {
    include!(concat!(env!("OUT_DIR"), "/bbhwinfo.rs"));
}

pub fn init_logger(path: &Path) {
    #[cfg(target_os = "android")]
    let system = android_logger::AndroidLogger::new(
        android_logger::Config::default().with_max_level(log::LevelFilter::Debug),
    );
    #[cfg(not(target_os = "android"))]
    let system = {
        if let Err(_) = std::env::var("RUST_LOG") {
            std::env::set_var("RUST_LOG", "debug");
        }
        pretty_env_logger::formatted_builder()
            .build()
    };

    println!("here??");
    
    let (logger, _) = Logger::try_with_str("debug").expect("No logger?")
        .log_to_file(FileSpec::default().directory(path.join("logs")).suppress_timestamp())
        .append()
        .format(opt_format)
        .cleanup_in_background_thread(false)
        .rotate(Criterion::AgeOrSize(Age::Day, 1024 * 1024 * 10 /* 10 MB */), Naming::Numbers, Cleanup::KeepLogFiles(1))
        .write_mode(WriteMode::BufferAndFlush)
        .build().unwrap();
    
    let _ = multi_log::MultiLogger::init(vec![Box::new(system), logger], log::Level::Trace);

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
