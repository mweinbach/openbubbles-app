import 'dart:async';
import 'dart:isolate';
import 'dart:ui' hide window;

import 'package:bluebubbles/database/database.dart';
import 'package:bluebubbles/helpers/backend/startup_tasks.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:bluebubbles/services/isolates/global_isolate.dart';

import 'package:universal_html/html.dart' hide Platform;
import 'dart:io' show Platform;
import 'package:get_it/get_it.dart';

// ignore: non_constant_identifier_names
LifecycleService get LifecycleSvc => GetIt.I<LifecycleService>();

class LifecycleService with WidgetsBindingObserver {
  bool isBubble = false;
  bool headless = false;
  bool windowFocused = true;
  bool? wasActiveAliveBefore;
  bool isDead = false;
  Timer? closeTimer;

  bool get isAlive => kIsWeb
      ? !(window.document.hidden ?? false)
      : kIsDesktop
          ? windowFocused
          : (WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed ||
              IsolateNameServer.lookupPortByName('bg_isolate') != null);

  AppLifecycleState? get currentState => WidgetsBinding.instance.lifecycleState;

  List<AppLifecycleState> statesSinceLastResume = [];

  bool get wasPaused => statesSinceLastResume.contains(AppLifecycleState.paused);
  bool get wasHidden =>
      statesSinceLastResume.contains(AppLifecycleState.inactive) ||
      statesSinceLastResume.contains(AppLifecycleState.detached);
  bool get hasResumed => statesSinceLastResume.contains(AppLifecycleState.resumed);

  Future<void> init({bool headless = false, bool isBubble = false}) async {
    Logger.debug("Initializing LifecycleService${headless ? " in headless mode" : ""}");

    if (!headless) {
      WidgetsFlutterBinding.ensureInitialized();
      WidgetsBinding.instance.addObserver(this);
    }

    this.headless = headless;
    this.isBubble = isBubble;

    unawaited(handleForegroundService(AppLifecycleState.resumed));
    Logger.debug("LifecycleService initialized");
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) async {
    if (headless) return;
    Logger.debug("App State changed to $state");

    // If the current state is resume, and we've already had a resume, clear states from before the last resume
    if (state == AppLifecycleState.resumed && statesSinceLastResume.contains(AppLifecycleState.resumed)) {
      // Remove all states up to and including the last resume
      final lastResumeIndex = statesSinceLastResume.lastIndexOf(AppLifecycleState.resumed);
      statesSinceLastResume.removeRange(0, lastResumeIndex + 1);
    }

    // Add the new state
    statesSinceLastResume.add(state);

    if (state == AppLifecycleState.resumed) {
      // Restore active-chat liveness immediately to avoid a race where
      // incoming messages are processed while lifecycle is already resumed
      // but chat state is still marked dead from the previous close().
      // This also happens in the `open -> StartupTasks.onAppResume` flow.
      // We still want to do it here to avoid any race conditions.
      if (GetIt.I.isRegistered<ChatsService>()) {
        if (!kIsDesktop || wasActiveAliveBefore != false) {
          ChatsSvc.setActiveToAlive();
        }
      }

      await Database.waitForInit();

      if (GetIt.I.isRegistered<SocketService>()) {
        GetIt.I<SocketService>().resetScheduledRestartBackoff(cancelPendingTimer: true);
      }

      open();
    } else if (state != AppLifecycleState.inactive) {
      SystemChannels.textInput.invokeMethod('TextInput.hide').catchError((e, stack) {
        Logger.error("Error caught while hiding keyboard!", error: e, trace: stack);
      });
      if (isBubble) {
        closeBubble();
      } else {
        unawaited(close());
      }
    }

    unawaited(handleForegroundService(state));

    if (state == AppLifecycleState.detached && !(kIsDesktop || kIsWeb)) {
      isDead = true;
      if (!_anyQueueProcessing) {
        Logger.info("Engine exit");
        await MethodChannelSvc.invokeMethod("engine-done");
      }
    }
  }

  /// True while either message-handler queue still has work in flight.
  bool get _anyQueueProcessing {
    final outgoing = GetIt.I.isRegistered<OutgoingMessageHandler>() && OutgoingMsgHandler.isProcessing;
    final incoming = GetIt.I.isRegistered<IncomingMessageHandler>() && IncomingMsgHandler.isProcessing;
    return outgoing || incoming;
  }

  /// Cancels a pending background-engine teardown.  Called by the message
  /// handlers when they pick up new work so the engine isn't closed mid-send.
  void cancelEngineClose() {
    closeTimer?.cancel();
    closeTimer = null;
  }

  /// Called by the outgoing/incoming message handlers whenever a queue drains.
  /// When the app is detached/headless and both queues are idle, tears down the
  /// background engine after a short straggler delay (replaces the old
  /// `queue_impl` drain trigger).
  void scheduleEngineCloseIfIdle() {
    if (kIsDesktop || kIsWeb) return;
    closeTimer?.cancel();
    closeTimer = null;
    if (!isDead || _anyQueueProcessing) return;
    Logger.info("Queues drained — closing background engine after stragglers");
    closeTimer = Timer(const Duration(seconds: 5), () {
      MethodChannelSvc.invokeMethod("engine-done");
    });
  }

  Future<void> handleForegroundService(AppLifecycleState state) async {
    // If an isolate is invoking this, we don't want to start/stop the foreground service.
    // It should already be running. We don't need to stop it because the socket service
    // is not started when in headless mode.
    if (headless) return;

    // Don't handle foreground service for inactive/hidden states
    if ([AppLifecycleState.inactive, AppLifecycleState.hidden].contains(state)) return;

    // Read live from the reactive settings value so toggling the setting during
    // a session takes effect immediately without requiring an app restart.
    if (Platform.isAndroid && SettingsSvc.settings.keepAppAlive.value) {
      // We only want the foreground service to run when the app is not active
      if (state == AppLifecycleState.resumed) {
        Logger.info(tag: "LifecycleService", "Stopping foreground service");
        if (GetIt.I.isRegistered<MethodChannelService>()) {
          await GetIt.I.isReady<MethodChannelService>();
          unawaited(GetIt.I<MethodChannelService>().actions.stopForegroundService());
        }
      } else if ([AppLifecycleState.paused, AppLifecycleState.detached].contains(state)) {
        Logger.info(tag: "LifecycleService", "Starting foreground service");
        if (GetIt.I.isRegistered<MethodChannelService>()) {
          await GetIt.I.isReady<MethodChannelService>();
          unawaited(GetIt.I<MethodChannelService>().actions.startForegroundService());
        }
      }
    }
  }

  void open() {
    // If we haven't finished setup, don't do anything
    if (!SettingsSvc.settings.finishedSetup.value) return;
    isDead = false;
    closeTimer?.cancel();

    final activeChat = ChatsSvc.activeChat;
    if (activeChat != null) {
      activeChat.chat.toggleHasUnread(false);
      activeChat.chat.fixZenModeShared();
      final cvcController = cvc(activeChat.chat);
      if (cvcController.suppressResumeRefocus) {
        // The voice-memo flow owns focus this resume (returning it to the record button after
        // the mic permission prompt), so don't steal it back to the text field. One-shot.
        cvcController.suppressResumeRefocus = false;
      } else if (!cvcController.showingOverlays && cvcController.editing.isEmpty) {
        cvcController.lastFocusedNode.requestFocus();
      }
    }

    PushSvc.tryWarnVpn();
    PushSvc.onboardZenMode();
    unawaited(ExtensionSvc.refreshCache());

    StartupTasks.onAppResume();
  }

  // clever trick so we can see if the app is active in an isolate or not
  void createFakePort() {
    final port = ReceivePort();
    IsolateNameServer.removePortNameMapping('bg_isolate');
    IsolateNameServer.registerPortWithName(port.sendPort, 'bg_isolate');
  }

  Future<void> close() async {
    // DO NOT remove observer here, it needs to stay registered to receive resumed events.
    // Leaving this commented out as a reminder.
    // WidgetsBinding.instance.removeObserver(this);

    if (kIsDesktop && GetIt.I.isRegistered<ChatsService>()) {
      wasActiveAliveBefore = ChatsSvc.activeChat?.isAlive.value;
    }

    if ((!kIsDesktop || wasActiveAliveBefore != false) && GetIt.I.isRegistered<ChatsService>()) {
      ChatsSvc.setActiveToDead();
    }

    // Stop any active typing indicators before draining the isolate so the
    // HTTP request completes and the recipient's typing indicator is cleared.
    if (GetIt.I.isRegistered<TypingIndicatorService>()) {
      await TypingIndicatorSvc.stopAllTyping();
    }

    // Only stop the isolate and disconnect if the app is paused.
    // This is when the app is actually in the background. If it's inactive or hidden,
    // the app is still technically in the foregronud, but might just be obscured.
    if (Platform.isAndroid && currentState == AppLifecycleState.paused) {
      IsolateNameServer.removePortNameMapping('bg_isolate');
      if (GetIt.I.isRegistered<SocketService>()) {
        GetIt.I<SocketService>().disconnect();
      }

      // Request graceful isolate shutdown. Do not force-kill on timeout:
      // in-flight MethodChannel handlers may still need to post their reply,
      // and killing early can trigger a fatal platform reply-port abort.
      if (GetIt.I.isRegistered<GlobalIsolate>()) {
        unawaited(GetIt.I<GlobalIsolate>().drainAndStop());
      }
    }

    if (GetIt.I.isRegistered<ChatsService>()) {
      final activeChat = ChatsSvc.activeChat;
      if (activeChat != null) {
        ConversationViewController _cvc = cvc(activeChat.chat);
        _cvc.lastFocusedNode.unfocus();
      }
    }

    if (kIsDesktop) {
      windowFocused = false;
    }
  }

  void closeBubble() {
    if (GetIt.I.isRegistered<ChatsService>()) {
      GetIt.I<ChatsService>().setActiveToDead();
    }

    if (GetIt.I.isRegistered<SocketService>()) {
      GetIt.I<SocketService>().disconnect();
    }
  }
}
