use std::{collections::{BTreeMap, HashMap}, fmt::Debug, fs::{File, OpenOptions}, io::{Cursor, Read, Seek, Write}, path::PathBuf, sync::{Arc, LazyLock, OnceLock, atomic::{AtomicU64, Ordering}}, time::{Duration, SystemTime}};

use keystore::software::plist_to_bin;
use log::{debug, error, info, warn};
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
    photos_client: OnceLock<Arc<rustpush::photos::PhotosClient<rustpush::DefaultAnisetteProvider>>>,
    account_generation: u64,
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

    /// Reuse the account-scoped Photos client for the lifetime of this
    /// restored native state. The client owns the open CloudKit container
    /// and its in-memory PCS zone-key cache.
    pub(crate) fn cached_photos_client(
        &self,
        cloudkit: Arc<rustpush::cloudkit::CloudKitClient<rustpush::DefaultAnisetteProvider>>,
        keychain: Arc<rustpush::keychain::KeychainClient<rustpush::DefaultAnisetteProvider>>,
    ) -> Arc<rustpush::photos::PhotosClient<rustpush::DefaultAnisetteProvider>> {
        self.photos_client
            .get_or_init(|| Arc::new(rustpush::photos::PhotosClient::new(cloudkit, keychain)))
            .clone()
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
    let account_generation = MESSAGE_ACCOUNT_GENERATION.load(Ordering::Acquire);
    RUNTIME.spawn(async move {
        info!("rpljslf initting");

        let result = if let Some(handle) = handle {
            let parsed = handle.parse::<u64>().unwrap_or_default();
            info!("consuming pointer {handle} {parsed}");
            info!("consuming daemon token {handle}");
            take_daemon(&handle).map(|daemondata| Some(Arc::new(NativePushState {
                state: Arc::new(daemondata.state),
                watcher: Mutex::new(daemondata.watcher),
                photos_client: OnceLock::new(),
                account_generation,
            })))
        } else {
            SharedPushState::restore_with_error(dir).await.map(|restored| restored.map(|a| Arc::new(NativePushState {
                state: Arc::new(a.0),
                watcher: Mutex::new(a.1),
                photos_client: OnceLock::new(),
                account_generation,
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

const MAX_PERMANENT_JOURNAL_FAILURES: u8 = 3;

#[derive(Debug, PartialEq, Eq)]
pub(crate) enum PermanentJournalFailure {
    Retry(u8),
    Discarded(u8),
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
                warn!("Failed to write to the message journal");
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
            if let Err(_error) = self.compact_journal() {
                warn!("Failed to compact the message journal");
            }
        }

        match item.write(&mut self.journal) {
            Ok(len) => {
                self.journal.sync_data()?;
                self.journal_len += len;
                Ok(())
            },
            Err(e) => {
                warn!("Failed to write to the message journal");
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
        self.finish_with_outcome(id, true)
    }

    /// Only a caller-classified malformed payload consumes this budget.
    /// Infrastructure failures leave the durable entry completely untouched.
    pub(crate) fn mark_permanent_failure(&mut self, id: u64) -> anyhow::Result<PermanentJournalFailure> {
        let attempts = self.attempt(id)?.saturating_add(1);
        if attempts >= MAX_PERMANENT_JOURNAL_FAILURES {
            self.finish_with_outcome(id, false)?;
            Ok(PermanentJournalFailure::Discarded(attempts))
        } else {
            Ok(PermanentJournalFailure::Retry(attempts))
        }
    }

    fn finish_with_outcome(&mut self, id: u64, success: bool) -> anyhow::Result<()> {
        if !self.messages.contains_key(&id) {
            return Ok(());
        }
        self.log_entry(MessageJournal::Finish { id, success })?;
        self.messages.remove(&id);
        Ok(())
    }

    /// Account replacement must reset the open journal descriptor as well as
    /// the in-memory replay map. Keep the monotonic ID so an old callback can
    /// never accidentally address a new account's freshly inserted message.
    fn clear_account(&mut self) -> anyhow::Result<()> {
        let previous = std::mem::take(&mut self.messages);
        if let Err(error) = self.compact_journal() {
            self.messages = previous;
            return Err(error);
        }
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

/// Live callback payloads remain queued until Kotlin confirms successful
/// ingestion. Store them behind `Arc` so a UniFFI mapper can release the
/// queue lock without deep-cloning every message field first.
pub static QUEUED_MESSAGES: LazyLock<Mutex<(u64, HashMap<u64, Arc<PushMessage>>)>> =
    LazyLock::new(|| Mutex::new((0, HashMap::new())));

static MESSAGE_ACCOUNT_GENERATION: AtomicU64 = AtomicU64::new(0);

fn account_generation_is_current(generation: u64) -> bool {
    MESSAGE_ACCOUNT_GENERATION.load(Ordering::Acquire) == generation
}

/// Invalidate all old callbacks before atomically replacing the account-bound
/// journal. The journal mutex serializes cleanup with any in-flight fsync.
pub(crate) async fn clear_account_messages() -> anyhow::Result<()> {
    MESSAGE_ACCOUNT_GENERATION.fetch_add(1, Ordering::AcqRel);
    QUEUED_MESSAGES.lock().await.1.clear();
    tokio::task::spawn_blocking(|| MESSAGE_LOG.blocking_lock().clear_account())
        .await
        .map_err(|error| anyhow::anyhow!("message journal cleanup task failed: {error}"))?
}

#[uniffi::export]
impl NativePushState {

    pub fn start_loop(self: Arc<NativePushState>, handler: Arc<dyn MsgReceiver>) {
        RUNTIME.spawn(async move {
            let account_generation = self.account_generation;
            let mut watcher = self.watcher.lock().await;
            'receive: loop {
                if !account_generation_is_current(account_generation) {
                    break;
                }
                match std::panic::AssertUnwindSafe(recv_wait(&mut watcher, &self.state)).catch_unwind().await {
                    Ok(yes) => {
                        let (mut msg, deferred_acknowledgment) = match yes {
                            PollResult::Cont(Some(msg)) => (msg, None),
                            PollResult::DurableMessage(msg, notification_id) => {
                                (msg, Some(notification_id))
                            },
                            PollResult::Cont(None) => continue,
                            PollResult::Stop => break,
                        };
                        if !account_generation_is_current(account_generation) {
                            break;
                        }
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
                                    let mut journal = MESSAGE_LOG.blocking_lock();
                                    anyhow::ensure!(
                                        account_generation_is_current(account_generation),
                                        "incoming message belongs to a previous account",
                                    );
                                    journal.insert(entry)
                                }).await.unwrap_or_else(|join| {
                                    Err(anyhow::anyhow!("journal task panicked: {join}"))
                                });
                                match result {
                                    Ok(_) => break,
                                    Err(_error) => {
                                        if !account_generation_is_current(account_generation) {
                                            break 'receive;
                                        }
                                        error!("Failed to persist an incoming message journal entry");
                                        tokio::time::sleep(Duration::from_secs(5)).await;
                                    }
                                }
                            }
                            if let Some(notification_id) = deferred_acknowledgment {
                                if let Err(_error) = self.state.conn.acknowledge_notification(notification_id).await {
                                    warn!("Failed to acknowledge a durably persisted incoming message");
                                }
                            }
                            msg = PushMessage::ProcessQueue;
                        }
                        if !account_generation_is_current(account_generation) {
                            break;
                        }
                        let mut locked_messages = QUEUED_MESSAGES.lock().await;
                        if !account_generation_is_current(account_generation) {
                            break;
                        }
                        let key = locked_messages.0;
                        locked_messages.1.insert(key, Arc::new(msg));
                        locked_messages.0 = locked_messages.0.wrapping_add(1);
                        drop(locked_messages);

                        let handler_ref = handler.clone();
                        tokio::spawn(async move {
                            let mut retry = 0;
                            // sheesh, downloads take time...
                            tokio::time::sleep(Duration::from_secs(30)).await;
                            while account_generation_is_current(account_generation)
                                && QUEUED_MESSAGES.lock().await.1.contains_key(&key)
                            {
                                retry += 1;
                                if retry > 5 {
                                    warn!("Excessive retries; dropping an incoming callback pointer");
                                    QUEUED_MESSAGES.lock().await.1.remove(&key);
                                    break;
                                }
                                info!("re-emitting pointer {key}, retry {retry}");
                                // we still haven't been handled, attempt to handle again
                                let handler_retry = handler_ref.clone();
                                tokio::task::spawn_blocking(move || {
                                    if account_generation_is_current(account_generation) {
                                        handler_retry.receieved_msg(key, retry);
                                    }
                                });
                                tokio::time::sleep(Duration::from_secs(30)).await;
                            }
                        });

                        debug!("emitting pointer {key}");
                        // The foreign callback crosses into Kotlin over JNA;
                        // whatever it does must never block the receive loop.
                        let handler_emit = handler.clone();
                        tokio::task::spawn_blocking(move || {
                            if account_generation_is_current(account_generation) {
                                handler_emit.receieved_msg(key, 0);
                            }
                        });
                    },
                    Err(_payload) => {
                        error!("Native receive loop panicked (details suppressed)");
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
                    Err(_error) => {
                        warn!("Skipping malformed password data");
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
                    Err(_error) => {
                        warn!("Skipping malformed passkey data");
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
                Err(_payload) => {
                    error!("Passkey verification panicked (details suppressed)");
                    callback.got_verification(HashMap::new(), Some("Passkey verification failed".to_string()));
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
            if let Err(_error) = decline_facetime(&state_ref, guid).await {
                warn!("Failed to decline FaceTime call");
            }
        });
    }

    pub fn teardown_2fa(&self, action: String, txnid: String) {
        let state_ref = self.state.icloud_services.as_ref().expect("no icloud!").account.clone();
        RUNTIME.spawn(async move {
            if let Err(_error) = teardown_2fa(&state_ref, action, txnid).await {
                warn!("Failed to tear down two-factor session");
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
                Err(_error) => {
                    warn!("Failed to get authentication code");
                    0
                }
            }
        })
    }
    
    pub fn publish_status(&self, guid: Option<String>) {
        let state_ref = self.state.icloud_services.as_ref().expect("no icloud!").statuskit_client.clone();
        RUNTIME.spawn(async move {
            if let Err(_error) = set_status(&state_ref, guid).await {
                warn!("Failed to publish status");
            }
        });
    }
}

#[cfg(test)]
mod journal_tests {
    use super::{MessageJournal, MessageLog, PermanentJournalFailure, should_compact};
    use rustpush::{Message, MessageInst};
    use std::fs::{File, OpenOptions};

    fn message(id: &str) -> MessageInst {
        MessageInst {
            id: id.to_owned(),
            sender: None,
            conversation: None,
            message: Message::Delivered,
            sent_timestamp: 0,
            target: None,
            send_delivered: false,
            verification_failed: false,
            certified_context: None,
        }
    }

    fn open_log(path: &std::path::Path) -> MessageLog {
        let file = OpenOptions::new()
            .read(true)
            .write(true)
            .create(true)
            .open(path)
            .expect("open journal");
        MessageLog::create(path.to_path_buf(), file)
    }

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

    #[test]
    fn account_cleanup_replaces_open_journal_and_preserves_monotonic_ids() {
        let directory = tempfile::tempdir().expect("temporary journal directory");
        let path = directory.path().join("messages.journal");
        let mut log = open_log(&path);

        let previous_id = log.insert(message("old-account")).expect("old message");
        assert!(path.metadata().expect("journal metadata").len() > 0);

        log.clear_account().expect("account journal cleanup");
        assert!(log.messages.is_empty());
        assert_eq!(path.metadata().expect("journal metadata").len(), 0);
        assert!(!path.with_extension("journal.tmp").exists());

        let next_id = log.insert(message("new-account")).expect("new message");
        assert!(next_id > previous_id);

        let reopened = OpenOptions::new()
            .read(true)
            .write(true)
            .open(&path)
            .expect("reopen journal");
        let restored = MessageLog::create(path, reopened);
        assert_eq!(restored.messages.len(), 1);
        assert_eq!(restored.messages[&next_id].msg.id, "new-account");
    }

    #[test]
    fn transient_replays_preserve_durable_messages_and_permanent_failure_budget() {
        let directory = tempfile::tempdir().expect("temporary journal directory");
        let path = directory.path().join("messages.journal");
        let mut log = open_log(&path);
        let id = log.insert(message("recoverable-storage-failure")).expect("journal message");

        assert_eq!(
            log.mark_permanent_failure(id).expect("prior malformed attempt"),
            PermanentJournalFailure::Retry(1),
        );
        assert_eq!(
            log.mark_permanent_failure(id).expect("second malformed attempt"),
            PermanentJournalFailure::Retry(2),
        );
        let durable_len = path.metadata().expect("journal metadata").len();

        for _ in 0..16 {
            let (&pending_id, entry) = log.messages.iter().next().expect("durable retry");
            assert_eq!(pending_id, id);
            assert_eq!(entry.attempts, 2);
            assert_eq!(path.metadata().expect("journal metadata").len(), durable_len);
        }

        drop(log);
        let restored = open_log(&path);
        assert_eq!(restored.messages.len(), 1);
        assert_eq!(restored.messages[&id].attempts, 2);
        assert_eq!(restored.messages[&id].msg.id, "recoverable-storage-failure");
    }

    #[test]
    fn permanent_payload_failures_are_bounded_without_blocking_later_messages() {
        let directory = tempfile::tempdir().expect("temporary journal directory");
        let path = directory.path().join("messages.journal");
        let mut log = open_log(&path);
        let malformed = log.insert(message("malformed-payload")).expect("malformed entry");
        let next = log.insert(message("later-message")).expect("later entry");

        assert_eq!(
            log.mark_permanent_failure(malformed).expect("first permanent failure"),
            PermanentJournalFailure::Retry(1),
        );
        assert_eq!(
            log.mark_permanent_failure(malformed).expect("second permanent failure"),
            PermanentJournalFailure::Retry(2),
        );

        drop(log);
        let mut restored = open_log(&path);
        assert_eq!(restored.messages[&malformed].attempts, 2);
        assert_eq!(
            restored.mark_permanent_failure(malformed).expect("bounded permanent failure"),
            PermanentJournalFailure::Discarded(3),
        );
        assert_eq!(restored.messages.keys().next(), Some(&next));

        drop(restored);
        let reopened = open_log(&path);
        assert!(!reopened.messages.contains_key(&malformed));
        assert_eq!(reopened.messages[&next].msg.id, "later-message");

        let mut journal = File::open(&path).expect("inspect journal");
        let mut recorded_failure = false;
        while let Ok((_, entry)) = MessageJournal::read(&mut journal) {
            if let MessageJournal::Finish { id, success } = entry {
                if id == malformed {
                    assert!(!success, "malformed payload must not be recorded as successful");
                    recorded_failure = true;
                }
            }
        }
        assert!(recorded_failure, "permanent rejection must be durably recorded");
    }

    #[test]
    fn stale_account_failure_cannot_discard_the_next_accounts_message() {
        let directory = tempfile::tempdir().expect("temporary journal directory");
        let path = directory.path().join("messages.journal");
        let mut log = open_log(&path);
        let previous_id = log.insert(message("previous-account")).expect("previous message");

        log.clear_account().expect("clear previous account");
        let current_id = log.insert(message("current-account")).expect("current message");
        assert!(current_id > previous_id);

        assert!(log.mark_permanent_failure(previous_id).is_err());
        assert_eq!(log.messages[&current_id].attempts, 0);
        assert_eq!(log.messages[&current_id].msg.id, "current-account");

        drop(log);
        let restored = open_log(&path);
        assert_eq!(restored.messages.len(), 1);
        assert_eq!(restored.messages[&current_id].attempts, 0);
    }
}
