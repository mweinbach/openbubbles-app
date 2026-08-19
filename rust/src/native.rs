use std::{collections::{BTreeMap, HashMap}, fmt::Debug, fs::{File, OpenOptions}, io::{Cursor, Read, Seek, Write}, path::PathBuf, sync::{Arc, LazyLock, OnceLock}, time::{Duration, SystemTime}};

use keystore::software::plist_to_bin;
use log::{error, info, warn};
use openssl::pkey::PKey;
use rustpush::{GenerateVerificationTokenRequest, MessageInst, get_gateways_for_mccmnc, passwords::{Passkey, PasswordCriteria, PasswordManagerMeta, PasswordRawEntry}};
use serde::{Deserialize, Serialize};
use tokio::sync::Mutex;

use futures::FutureExt;
use crate::{RUNTIME, api::api::{APSWatcher, PollResult, PushMessage, SharedPushState, approve_circle, decline_facetime, do_first_time_init, recv_wait, register_service, set_status, take_daemon, teardown_2fa}};

#[derive(uniffi::Record)] 
pub struct FileInfo {
    pub duration: Option<f64>,
    pub width: u32,
    pub height: u32,
    pub thumbnail: Option<Vec<u8>>,
}

#[derive(uniffi::Enum)]
pub enum PackagedFile {
    Info(FileInfo),
    Failure(String),
}

#[uniffi::export(with_foreign)]
pub trait KotlinFilePackager: Send + Sync + Debug {
    fn get_file(&self, path: String) -> PackagedFile;
    fn scan_files(&self, paths: Vec<String>);
}

pub static PACKAGER_LOCK: OnceLock<Arc<dyn KotlinFilePackager>> = OnceLock::new();

#[uniffi::export(with_foreign)]
pub trait MsgReceiver: Send + Sync + Debug {
    fn receieved_msg(&self, msg: u64, retry: u64);
    fn native_ready(&self, state: Option<Arc<NativePushState>>);
    fn native_error(&self, reason: String);
    fn twofa_event(&self, success: bool);
    fn finish(&self);
}

#[uniffi::export(with_foreign)]
pub trait CarrierHandler: Send + Sync + Debug {
    fn got_gateway(&self, gateway: Option<String>, error: Option<String>);
}

#[uniffi::export(with_foreign)]
pub trait InsertKeychainCallback: Send + Sync + Debug {
    fn done(&self, error: Option<String>);
}

#[uniffi::export(with_foreign)]
pub trait AvailableGroupsCallback: Send + Sync + Debug {
    fn groups(&self, groups: HashMap<String, String>);
}

#[derive(uniffi::Record)]
pub struct SavedPassword {
    cred_id: String,
    username: String,
    password: String,
    otp: Option<u32>,
}

#[derive(uniffi::Record)]
pub struct SavedPasskey {
    cred_id: String,
    id: Vec<u8>,
    tag: Vec<u8>,
    key: Vec<u8>,
}

#[uniffi::export(with_foreign)]
pub trait RetrieveKeysCallback: Send + Sync + Debug {
    fn keys(&self, passwords: Vec<SavedPassword>, passkeys: Vec<SavedPasskey>);
}

#[uniffi::export(with_foreign)]
pub trait SpecialAppleAuthCallback: Send + Sync + Debug {
    fn got_verification(&self, token: HashMap<String, String>, error: Option<String>);
}

pub static HANDLE_WIFI_NETWORKS: OnceLock<Arc<dyn HandleWifiNetworksCallback>> = OnceLock::new();
pub static CONFIG_PATH: OnceLock<String> = OnceLock::new();

#[uniffi::export(with_foreign)]
pub trait HandleWifiNetworksCallback: Send + Sync + Debug {
    fn handle_wifi_networks(&self, networks: HashMap<String, String>, user_approve: bool);
}

#[derive(uniffi::Object)] 
pub struct NativePushState {
    state: Arc<SharedPushState>,
    watcher: Mutex<APSWatcher>,
}

impl NativePushState {
    /// Access the shared push state from sibling modules (uniffi_ext).
    pub(crate) fn shared(&self) -> &SharedPushState {
        &self.state
    }

    /// Owned handle for async exports whose futures outlive the borrow.
    pub(crate) fn shared_arc(&self) -> Arc<SharedPushState> {
        self.state.clone()
    }
}

#[uniffi::export]
pub fn start(dir: String, packager: Arc<dyn KotlinFilePackager>, wifi: Arc<dyn HandleWifiNetworksCallback>) {
    let _ = PACKAGER_LOCK.set(packager);
    let _ = HANDLE_WIFI_NETWORKS.set(wifi);
    do_first_time_init(dir);
}

#[uniffi::export]
pub fn init_native(dir: String, handle: Option<String>, handler: Arc<dyn MsgReceiver>) {
    info!("rpljslf start");
    let _ = CONFIG_PATH.set(dir.clone());
    RUNTIME.spawn(async move {
        info!("rpljslf initting");

        let result = if let Some(handle) = handle {
            let parsed = handle.parse::<u64>().unwrap_or_default();
            info!("consuming pointer {handle} {parsed}");
            info!("consuming daemon token {handle}");
            take_daemon(&handle).map(|daemondata| Some(Arc::new(NativePushState {
                state: Arc::new(daemondata.state),
                watcher: Mutex::new(daemondata.watcher),
            })))
        } else {
            SharedPushState::restore_with_error(dir).await.map(|restored| restored.map(|a| Arc::new(NativePushState {
                state: Arc::new(a.0),
                watcher: Mutex::new(a.1),
            })))
        };

        info!("rpljslf raed");
        match result {
            Ok(state) => handler.native_ready(state),
            Err(error) => handler.native_error(error.to_string()),
        }
        info!("rpljslf dom");
    });
}

#[uniffi::export]
pub fn get_carrier(handler: Arc<dyn CarrierHandler>, mccmnc: String) {
    RUNTIME.spawn(async move {
        match get_gateways_for_mccmnc(&mccmnc).await {
            Ok(gateway) => handler.got_gateway(Some(gateway.gateway), None),
            Err(err) => handler.got_gateway(None, Some(err.to_string())),
        }
    });
}



#[allow(dead_code)]
pub fn plist_to_buf<T: serde::Serialize>(value: &T) -> Result<Vec<u8>, plist::Error> {
    let mut buf: Vec<u8> = Vec::new();
    let writer = Cursor::new(&mut buf);
    plist::to_writer_xml(writer, &value)?;
    Ok(buf)
}

#[allow(dead_code)]
pub fn plist_to_string<T: serde::Serialize>(value: &T) -> Result<String, plist::Error> {
    plist_to_buf(value).map(|val| String::from_utf8(val).unwrap())
}

#[derive(Serialize, Deserialize, Clone)]
pub struct MessageLogEntry {
    pub attempts: u8,
    pub msg: MessageInst,
}

#[derive(Serialize, Deserialize)]
enum MessageJournal {
    Message {
        id: u64,
        item: MessageLogEntry,
    },
    Attempt {
        id: u64,
    },
    Finish {
        id: u64,
        success: bool,
    }
}

const MAX_JOURNAL_RECORD_BYTES: usize = 16 * 1024 * 1024;

impl MessageJournal {
    fn read(mut reader: impl Read) -> anyhow::Result<(usize, Self)> {
        let mut len = [0u8; 4];
        reader.read_exact(&mut len)?;
        let len = u32::from_be_bytes(len) as usize;
        anyhow::ensure!(len <= MAX_JOURNAL_RECORD_BYTES, "journal record exceeds size limit");
        let mut data = vec![0u8; len];
        reader.read_exact(&mut data)?;

        Ok((len + 4, plist::from_bytes(&data)?))
    }

    fn write(&self, mut writer: impl Write) -> anyhow::Result<usize> {
        let bytes = plist_to_bin(self)?;
        anyhow::ensure!(bytes.len() <= MAX_JOURNAL_RECORD_BYTES, "journal record exceeds size limit");
        writer.write_all(&(bytes.len() as u32).to_be_bytes())?;
        writer.write_all(&bytes)?;

        Ok(bytes.len() + 4)
    }
}

pub struct MessageLog {
    pub messages: BTreeMap<u64, MessageLogEntry>,
    journal_path: PathBuf,
    journal: File,
    journal_len: usize,
    /// Size of the journal right after the last compaction. Rewriting the
    /// journal is O(live bytes); comparing against this makes compaction
    /// amortized O(appended bytes) instead of once per entry.
    last_compacted_len: usize,
    current_id: u64,
}

/// Compaction policy. The naive rule — rewrite whenever the file exceeds
/// [MIN_JOURNAL_BYTES] — rewrites the entire journal on *every* record while
/// a large backlog drains (each Finish only shrinks the live set by one), so
/// draining N entries costs O(N²) I/O. Instead:
///
/// - Skip compaction entirely for small journals.
/// - Otherwise rewrite only once the file has more than doubled since the
///   last compaction, so at least half of each rewrite is reclaimable and
///   total rewrite work stays proportional to total appends.
/// - Always tidy up when the live map is empty: a finished drain leaves a
///   file of dead Finish records, and one final rewrite empties it.
fn should_compact(journal_len: usize, last_compacted_len: usize, pending_is_empty: bool) -> bool {
    const MIN_JOURNAL_BYTES: usize = 1024 * 128;
    if journal_len <= MIN_JOURNAL_BYTES {
        return false;
    }
    pending_is_empty || journal_len > last_compacted_len.saturating_mul(2)
}

impl MessageLog {
    fn create(journal_path: PathBuf, mut journal: File) -> Self {
        let mut total_read = 0;
        let mut current_id = 0;
        let mut messages: BTreeMap<u64, MessageLogEntry> = BTreeMap::new();
        while let Ok((size, len)) = MessageJournal::read(&mut journal) {
            total_read += size;
            match len {
                MessageJournal::Message { id, item } => {
                    current_id = current_id.max(id);
                    messages.insert(id, item);
                },
                MessageJournal::Attempt { id } => {
                    let Some(msg) = messages.get_mut(&id) else { continue };
                    msg.attempts += 1;
                },
                MessageJournal::Finish { id, success: _ } => {
                    messages.remove(&id);
                    // TODO handle success later
                },
            }
        }
        journal.set_len(total_read as u64).unwrap();
        journal.seek(std::io::SeekFrom::Start(total_read as u64)).unwrap();
        Self {
            messages,
            journal_path,
            journal,
            journal_len: total_read,
            last_compacted_len: 0,
            current_id,
        }
    }

    fn compact_journal(&mut self) -> anyhow::Result<()> {
        info!("Compacting journal!");
        let temp_path = self.journal_path.with_extension("journal.tmp");
        let mut temp = OpenOptions::new()
            .write(true)
            .create(true)
            .truncate(true)
            .open(&temp_path)?;
        let mut compacted_len = 0;
        for (id, item) in &self.messages {
            let message = MessageJournal::Message {
                id: *id,
                item: item.clone(),
            };
            compacted_len += message.write(&mut temp).map_err(|error| {
                warn!("Failed to write to journal {error}");
                error
            })?;
        }
        temp.sync_data()?;
        drop(temp);

        if let Err(error) = std::fs::rename(&temp_path, &self.journal_path) {
            let _ = std::fs::remove_file(&temp_path);
            return Err(error.into());
        }
        if let Some(parent) = self.journal_path.parent() {
            File::open(parent)?.sync_data()?;
        }
        self.journal = OpenOptions::new()
            .read(true)
            .write(true)
            .open(&self.journal_path)?;
        self.journal.seek(std::io::SeekFrom::Start(compacted_len as u64))?;
        self.journal_len = compacted_len;
        self.last_compacted_len = compacted_len;

        info!("Compacted journal!");
        Ok(())
    }

    fn log_entry(&mut self, item: MessageJournal) -> anyhow::Result<()> {
        if should_compact(self.journal_len, self.last_compacted_len, self.messages.is_empty()) {
            if let Err(e) = self.compact_journal() {
                warn!("Error compacting journal {e}");
            }
        }

        match item.write(&mut self.journal) {
            Ok(len) => {
                self.journal.sync_data()?;
                self.journal_len += len;
                Ok(())
            },
            Err(e) => {
                warn!("Failed to write to journal {e}");
                let _ = self.journal.set_len(self.journal_len as u64);
                let _ = self.journal.seek(std::io::SeekFrom::Start(self.journal_len as u64));
                Err(e)
            }
        }
    }
    
    pub fn insert(&mut self, item: MessageInst) -> anyhow::Result<u64> {
        let id = self.current_id.wrapping_add(1);
        let log = MessageLogEntry { attempts: 0, msg: item.clone() };

        self.log_entry(MessageJournal::Message { 
            id, 
            item: log.clone(),
        })?;
        
        self.current_id = id;
        self.messages.insert(id, log);
        Ok(id)
    }

    pub fn attempt(&mut self, id: u64) -> anyhow::Result<u8> {
        let Some(msg) = self.messages.get(&id) else {
            warn!("Attempting unknown ID!");
            anyhow::bail!("attempting unknown journal id {id}");
        };
        let attempts = msg.attempts;
        self.log_entry(MessageJournal::Attempt { id })?;
        if let Some(msg) = self.messages.get_mut(&id) {
            msg.attempts = msg.attempts.saturating_add(1);
        }
        Ok(attempts)
    }

    pub fn finish(&mut self, id: u64) -> anyhow::Result<()> {
        if !self.messages.contains_key(&id) {
            return Ok(());
        }
        self.log_entry(MessageJournal::Finish { id, success: true })?;
        self.messages.remove(&id);
        Ok(())
    }
}

pub static MESSAGE_LOG: LazyLock<Mutex<MessageLog>> = LazyLock::new(|| {
    let path = PathBuf::from(format!("{}/messages.journal", CONFIG_PATH.get().expect("no path configured!")));
    let file = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .open(&path).expect("Failed to create journal!");
    
    Mutex::new(MessageLog::create(path, file))
});

pub static QUEUED_MESSAGES: LazyLock<Mutex<(u64, HashMap<u64, PushMessage>)>> = LazyLock::new(|| Mutex::new((0, HashMap::new())));

#[uniffi::export]
impl NativePushState {

    pub fn start_loop(self: Arc<NativePushState>, handler: Arc<dyn MsgReceiver>) {
        RUNTIME.spawn(async move {
            let mut watcher = self.watcher.lock().await;
            loop {
                match std::panic::AssertUnwindSafe(recv_wait(&mut watcher, &self.state)).catch_unwind().await {
                    Ok(yes) => {
                        match yes {
                            PollResult::Cont(Some(mut msg)) => {
                                if let PushMessage::TwoFaAuthEvent(event) = &msg {
                                    handler.twofa_event(*event);
                                    continue;
                                }

                                if let PushMessage::IMessage(message) = &msg {
                                    loop {
                                        // The journal write fsyncs; park a blocking-pool
                                        // thread, not a runtime worker that also drives
                                        // the APS socket.
                                        let entry = message.clone();
                                        let result = tokio::task::spawn_blocking(move || {
                                            MESSAGE_LOG.blocking_lock().insert(entry)
                                        }).await.unwrap_or_else(|join| {
                                            Err(anyhow::anyhow!("journal task panicked: {join}"))
                                        });
                                        match result {
                                            Ok(_) => break,
                                            Err(error) => {
                                                error!("Failed to persist incoming message journal entry: {error}");
                                                tokio::time::sleep(Duration::from_secs(5)).await;
                                            },
                                        }
                                    }
                                    msg = PushMessage::ProcessQueue;
                                }
                                let mut locked_messages = QUEUED_MESSAGES.lock().await;
                                let key = locked_messages.0;
                                locked_messages.1.insert(key, msg);
                                locked_messages.0 = locked_messages.0.wrapping_add(1);
                                drop(locked_messages);

                                let handler_ref = handler.clone();
                                tokio::spawn(async move {
                                    let mut retry = 0;
                                    // sheesh, downloads take time...
                                    tokio::time::sleep(Duration::from_secs(30)).await;
                                    while QUEUED_MESSAGES.lock().await.1.contains_key(&key) {
                                        retry += 1;
                                        if retry > 5 {
                                            warn!("Excessive retries, dropping pointer {key}");
                                            QUEUED_MESSAGES.lock().await.1.remove(&key);
                                            break;
                                        }
                                        info!("re-emitting pointer {key}, retry {retry}");
                                        // we still haven't been handled, attempt to handle again
                                        let handler_retry = handler_ref.clone();
                                        tokio::task::spawn_blocking(move || handler_retry.receieved_msg(key, retry));
                                        tokio::time::sleep(Duration::from_secs(30)).await;
                                    }
                                });

                                info!("emitting pointer {key}");
                                // The foreign callback crosses into Kotlin over JNA;
                                // whatever it does must never block the receive loop.
                                let handler_emit = handler.clone();
                                tokio::task::spawn_blocking(move || handler_emit.receieved_msg(key, 0));
                            },
                            PollResult::Cont(None) => continue,
                            PollResult::Stop => break
                        }
                    },
                    Err(payload) => {
                        let panic = match payload.downcast_ref::<&'static str>() {
                            Some(msg) => Some(*msg),
                            None => match payload.downcast_ref::<String>() {
                                Some(msg) => Some(msg.as_str()),
                                // Copy what rustc does in the default panic handler
                                None => None,
                            },
                        };
                        error!("Failed {:?}", panic);
                    }
                }
            }
            info!("finishing loop");
            handler.finish();
        });
    }

    pub fn get_available_groups(&self, groups_callback: Arc<dyn AvailableGroupsCallback>) {
        let passwords = self.state.icloud_services.as_ref().and_then(|i| i.passwords.clone()).expect("no icloud");
        RUNTIME.spawn(async move {
            let state = passwords.state.read().await;
            let result = state.groups.iter().filter_map(|g| Some((g.1.share.as_ref()?.display_name.clone(), g.0.clone()))).collect::<HashMap<_, _>>();
            groups_callback.groups(result);
        });
    }

    pub fn keychain_password_insert(&self, site: String, user: String, password: String, callback: Arc<dyn InsertKeychainCallback>, group: Option<String>) {
        let passwords = self.state.icloud_services.as_ref().and_then(|i| i.passwords.clone()).expect("no icloud");
        RUNTIME.spawn(async move {
            let criteria = PasswordCriteria {
                site,
                account: user,
            };
            let a = passwords.modify_password_entry::<PasswordRawEntry>(&criteria, |pw| {
                pw.data = password.as_bytes().to_vec();
            }, group.clone()).await.err();
            let b = passwords.modify_password_entry::<PasswordManagerMeta>(&criteria, |pw| {
                let mut data = pw.get_password_data().expect("Failed to get data?");
                data.set_last_used(SystemTime::now());
                data.change_password(password);
            }, group.clone()).await.err();
            let e = a.or(b);
            callback.done(e.map(|e| format!("{e}")));
        });
    }

    pub fn keychain_passkey_insert(&self, site: String, record_id: String, id: Vec<u8>, tag: Vec<u8>, key: Vec<u8>, callback: Arc<dyn InsertKeychainCallback>, group: Option<String>) {
        let passwords = self.state.icloud_services.as_ref().and_then(|i| i.passwords.clone()).expect("no icloud");
        RUNTIME.spawn(async move {
            // techincally this may insert into wrong group, but does anyone really ever update a passkey with the same ID??
            // and if they do, damn right it may go into the wrong group. Not my issue at this time lol
            let passkey_key = match PKey::private_key_from_pkcs8(&key).and_then(|key| key.ec_key()) {
                Ok(key) => key,
                Err(error) => {
                    callback.done(Some(format!("Invalid passkey key: {error}")));
                    return;
                }
            };
            let result = passwords.insert_password_entry(&record_id, &Passkey {
                cdat: SystemTime::now().duration_since(SystemTime::UNIX_EPOCH).unwrap().as_millis() as u64,
                mdat: SystemTime::now().duration_since(SystemTime::UNIX_EPOCH).unwrap().as_millis() as u64,
                agrp: "com.apple.webkit.webauthn".to_string(),
                labl: site,
                atag: tag,
                data: Passkey::encode_key(passkey_key),
                klbl: id,
            }, group.clone()).await.err();
            callback.done(result.map(|e| format!("{e}")));
        });
    }

    pub fn get_site_config(&self, site: String, callback: Arc<dyn RetrieveKeysCallback>) {
        let passwords = self.state.icloud_services.as_ref().and_then(|i| i.passwords.clone()).expect("no icloud");
        RUNTIME.spawn(async move {
            let passwords = passwords.get_password_for_site(site).await;
            callback.keys(passwords.passwords.into_iter().filter_map(|(k, p)| {
                let password = match String::from_utf8(p.data.clone()) {
                    Ok(password) => password,
                    Err(error) => {
                        warn!("Skipping malformed password {} with non-UTF-8 data: {}", k, error);
                        return None;
                    }
                };
                Some(SavedPassword {
                    cred_id: k,
                    username: p.acct.clone(),
                    password,
                    otp: passwords.passwords_meta.values().find_map(|m| {
                        if p.acct != m.acct { return None }
                        let totp = m.get_password_data().ok()?.totp?;
                        Some(totp.generate_otp().ok()?.0)
                    })
                })
            }).collect(), passwords.passkeys.into_iter().filter_map(|(k, p)| {
                let key = match p.get_key()
                    .and_then(|key| PKey::from_ec_key(key).map_err(Into::into))
                    .and_then(|key| key.private_key_to_pkcs8().map_err(Into::into)) {
                    Ok(key) => key,
                    Err(error) => {
                        warn!("Skipping malformed passkey {}: {}", k, error);
                        return None;
                    }
                };
                Some(SavedPasskey {
                    cred_id: k,
                    id: p.klbl.clone(),
                    tag: p.atag.clone(),
                    key,
                })
            }).collect());
        });
    }

    pub fn do_special_apple_auth(&self, client_data_hash: String, callback: Arc<dyn SpecialAppleAuthCallback>) {
        let token = self.state.icloud_services.as_ref().map(|i| i.token_provider.clone()).expect("no token");
        RUNTIME.spawn(async move {
            let result = std::panic::AssertUnwindSafe(token.generate_verification_token(GenerateVerificationTokenRequest::Passkey { 
                client_data_hash,
            })).catch_unwind().await;
            match result {
                Ok(Ok(success)) => {
                    let (key, value) = success.split_once(":").expect("Bad token form??");
                    callback.got_verification(HashMap::from_iter([
                        (key.to_string(), value.to_string())
                    ]), None);
                },
                Ok(Err(e)) => {
                    callback.got_verification(HashMap::new(), Some(format!("{e}")));
                }
                Err(payload) => {
                    let panic = match payload.downcast_ref::<&'static str>() {
                        Some(msg) => Some(*msg),
                        None => match payload.downcast_ref::<String>() {
                            Some(msg) => Some(msg.as_str()),
                            // Copy what rustc does in the default panic handler
                            None => None,
                        },
                    };
                    error!("Failed {:?}", panic);
                    callback.got_verification(HashMap::new(), Some(format!("{panic:?}")));
                }
            }
        });
    }

    pub fn get_state(self: Arc<NativePushState>) -> u64 {
        let arc_val = register_service(&self.state);
        info!("emitting state {arc_val}");
        arc_val
    }

    pub fn decline_facetime(&self, guid: String) {
        let state_ref = self.state.ft_client.clone();
        RUNTIME.spawn(async move {
            if let Err(e) = decline_facetime(&state_ref, guid).await {
                warn!("Failed to decline facetime {e}");
            }
        });
    }

    pub fn teardown_2fa(&self, action: String, txnid: String) {
        let state_ref = self.state.icloud_services.as_ref().expect("no icloud!").account.clone();
        RUNTIME.spawn(async move {
            if let Err(e) = teardown_2fa(&state_ref, action, txnid).await {
                warn!("Failed to teardown 2fa {e}");
            }
        });
    }

    pub fn get_auth_code(&self, txnid: String) -> u32 {
        let icloud = self.state.icloud_services.as_ref().expect("no icloud!");
        let state_ref = icloud.account.clone();
        let data_ref = self.state.active_circle_sessions.clone();
        RUNTIME.block_on(async move {
            match approve_circle(&data_ref, &state_ref, txnid).await {
                Ok(e) => e,
                Err(e) => {
                    warn!("Failed to get auth code {e}");
                    0
                }
            }
        })
    }
    
    pub fn publish_status(&self, guid: Option<String>) {
        let state_ref = self.state.icloud_services.as_ref().expect("no icloud!").statuskit_client.clone();
        RUNTIME.spawn(async move {
            if let Err(e) = set_status(&state_ref, guid).await {
                warn!("Failed to decline publish status {e}");
            }
        });
    }
}

#[cfg(test)]
mod journal_tests {
    use super::should_compact;

    #[test]
    fn small_journals_never_compact() {
        assert!(!should_compact(0, 0, false));
        assert!(!should_compact(1024 * 128, 200_000, false));
    }

    #[test]
    fn large_journal_compacts_only_after_doubling() {
        // Fresh start (never compacted): a large file reclaims once.
        assert!(should_compact(300_000, 0, false));
        // Just past the floor but under 2x the last compacted size: skip.
        assert!(!should_compact(300_000, 200_000, false));
        // Dead records now exceed the live set: rewrite.
        assert!(should_compact(500_000, 200_000, false));
    }

    #[test]
    fn emptied_journal_tidies_up() {
        // A finished drain leaves only dead Finish records; compact them.
        assert!(should_compact(300_000, 200_000, true));
        // But not while the file is small anyway.
        assert!(!should_compact(1_000, 0, true));
    }
}
