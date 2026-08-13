import 'package:animated_size_and_fade/animated_size_and_fade.dart';
import 'package:audio_waveforms/audio_waveforms.dart' as aw;
import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/reaction/reaction.dart';
import 'package:bluebubbles/app/layouts/settings/pages/message_view/message_options_order_panel.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/content/next_button.dart';
import 'package:bluebubbles/helpers/backend/message_retention_task.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/settings_widgets.dart';
import 'package:bluebubbles/database/models.dart' hide PlatformFile;
import 'package:bluebubbles/services/network/backend_service.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart' hide Response;
import 'package:path/path.dart';
import 'package:universal_io/io.dart';

class ConversationPanel extends StatefulWidget {
  const ConversationPanel({super.key});

  @override
  State<StatefulWidget> createState() => _ConversationPanelState();
}

class _ConversationPanelState extends State<ConversationPanel> with ThemeHelpers {
  static const List<int> _deleteAfterDaysOptions = [1, 3, 7, 14, 30, 60, 90, 180, 365];
  final RxnBool gettingIcons = RxnBool(null);
  final RxBool playingSendSound = false.obs;
  final RxBool playingReceiveSound = false.obs;
  late final dynamic sendPlayer;
  late final dynamic receivePlayer;

  bool sendPrepared = false;
  bool receivePrepared = false;

  @override
  void initState() {
    super.initState();

    if (kIsDesktop) {
      sendPlayer = Player();
      receivePlayer = Player();
      (sendPlayer as Player).stream.playing.listen((value) => playingSendSound.value = value);
      (receivePlayer as Player).stream.playing.listen((value) => playingReceiveSound.value = value);
    } else {
      sendPlayer = aw.PlayerController();
      receivePlayer = aw.PlayerController();
      (sendPlayer as aw.PlayerController)
          .onPlayerStateChanged
          .listen((value) => playingSendSound.value = value == aw.PlayerState.playing);
      (receivePlayer as aw.PlayerController)
          .onPlayerStateChanged
          .listen((value) => playingReceiveSound.value = value == aw.PlayerState.playing);
    }
  }

  @override
  Widget build(BuildContext context) {
    return SettingsScaffold(
      title: "Conversations",
      initialHeader: "Customization",
      iosSubtitle: iosSubtitle,
      materialSubtitle: materialSubtitle,
      tileColor: tileColor,
      headerColor: headerColor,
      bodySlivers: [
        SliverList(
          delegate: SliverChildListDelegate(
            <Widget>[
              SettingsSection(
                backgroundColor: tileColor,
                children: [
                  Obx(() => SettingsSwitch(
                        onChanged: (bool val) async {
                          SettingsSvc.settings.showDeliveryTimestamps.value = val;
                          await SettingsSvc.settings.saveOneAsync('showDeliveryTimestamps');
                        },
                        initialVal: SettingsSvc.settings.showDeliveryTimestamps.value,
                        title: "Show Delivery Timestamps",
                        backgroundColor: tileColor,
                      )),
                  const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                  Obx(() => SettingsSwitch(
                        onChanged: (bool val) async {
                          SettingsSvc.settings.recipientAsPlaceholder.value = val;
                          await SettingsSvc.settings.saveOneAsync('recipientAsPlaceholder');
                        },
                        initialVal: SettingsSvc.settings.recipientAsPlaceholder.value,
                        title: "Show Chat Name as Placeholder",
                        subtitle: "Changes the default hint text in the message box to display the recipient name",
                        backgroundColor: tileColor,
                        isThreeLine: true,
                      )),
                  const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                  Obx(() => SettingsSwitch(
                        onChanged: (bool val) async {
                          SettingsSvc.settings.alwaysShowAvatars.value = val;
                          await SettingsSvc.settings.saveOneAsync('alwaysShowAvatars');
                        },
                        initialVal: SettingsSvc.settings.alwaysShowAvatars.value,
                        title: "Show Avatars in DM Chats",
                        subtitle: "Shows contact avatars in direct messages rather than just in group messages",
                        backgroundColor: tileColor,
                        isThreeLine: true,
                      )),
                  if (!kIsWeb && !kIsDesktop) const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                  if (!kIsWeb && !kIsDesktop)
                    Obx(() => SettingsSwitch(
                          onChanged: (bool val) async {
                            SettingsSvc.settings.smartReply.value = val;
                            await SettingsSvc.settings.saveOneAsync('smartReply');
                          },
                          initialVal: SettingsSvc.settings.smartReply.value,
                          title: "Smart Suggestions",
                          subtitle:
                              "Shows smart reply suggestions above the message text field and detects various interactive content in message text",
                          backgroundColor: tileColor,
                          isThreeLine: true,
                        )),
                  const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                  Obx(() => SettingsSwitch(
                        onChanged: (bool val) async {
                          SettingsSvc.settings.repliesToPrevious.value = val;
                          await SettingsSvc.settings.saveOneAsync('repliesToPrevious');
                        },
                        initialVal: SettingsSvc.settings.repliesToPrevious.value,
                        title: "Show Replies To Previous Message",
                        subtitle: "Shows replies to the previous message in the thread rather than the original",
                        backgroundColor: tileColor,
                        isThreeLine: true,
                      )),
                  if (!kIsWeb && BackendSvc.getRemoteService() != null)
                    const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                  if (!kIsWeb && BackendSvc.getRemoteService() != null)
                    SettingsTile(
                      title: "Message Options Order",
                      subtitle:
                          "Set the order for the options when ${SettingsSvc.settings.doubleTapForDetails.value ? "double-tapping" : "pressing and holding"} a message",
                      onTap: () {
                        NavigationSvc.pushSettings(
                          context,
                          const MessageOptionsOrderPanel(),
                        );
                      },
                      trailing: const NextButton(),
                    ),
                  if (!kIsWeb)
                    const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                  if (!kIsWeb)
                    Obx(() => SettingsSwitch(
                          onChanged: (bool val) async {
                            SettingsSvc.settings.deleteMessagesAfterDays.value = val;
                            await SettingsSvc.settings.saveOneAsync("deleteMessagesAfterDays");
                            if (val) await MessageRetentionTask.run(force: true);
                          },
                          initialVal: SettingsSvc.settings.deleteMessagesAfterDays.value,
                          title: "Delete Old Messages",
                          subtitle: "Automatically deletes local messages older than the selected number of days",
                          backgroundColor: tileColor,
                          isThreeLine: true,
                        )),
                  if (!kIsWeb)
                    Obx(() => SettingsSvc.settings.deleteMessagesAfterDays.value
                        ? SettingsOptions<int>(
                            title: "Delete After",
                            initial: _deleteAfterDaysOptions.contains(SettingsSvc.settings.deleteMessagesAfterDaysCount.value)
                                ? SettingsSvc.settings.deleteMessagesAfterDaysCount.value
                                : 30,
                            clampWidth: false,
                            options: _deleteAfterDaysOptions,
                            secondaryColor: headerColor,
                            useCupertino: false,
                            capitalize: false,
                            textProcessing: (value) => "$value day${value == 1 ? "" : "s"}",
                            onChanged: (value) async {
                              if (value == null) return;
                              SettingsSvc.settings.deleteMessagesAfterDaysCount.value = value;
                            await SettingsSvc.settings.saveOneAsync("deleteMessagesAfterDaysCount");
                              await MessageRetentionTask.run(force: true);
                            },
                          )
                        : const SizedBox.shrink()),
                  if (!kIsWeb && BackendSvc.getRemoteService() != null)
                    const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                  if (!kIsWeb && BackendSvc.getRemoteService() != null)
                    SettingsTile(
                      title: "Sync Group Chat Icons",
                      trailing: Obx(() => gettingIcons.value == null
                          ? const SizedBox.shrink()
                          : gettingIcons.value == true
                              ? Container(
                                  constraints: const BoxConstraints(
                                    maxHeight: 20,
                                    maxWidth: 20,
                                  ),
                                  child: CircularProgressIndicator(
                                    strokeWidth: 3,
                                    valueColor: AlwaysStoppedAnimation<Color>(context.theme.colorScheme.primary),
                                  ))
                              : Icon(Icons.check, color: context.theme.colorScheme.outline)),
                      onTap: () async {
                        gettingIcons.value = true;
                        for (Chat c in ChatsSvc.groupChats) {
                          await Chat.getIcon(c, force: true);
                        }
                        gettingIcons.value = false;
                      },
                      subtitle: "Get iMessage group chat icons from the server",
                    ),
                  if (!kIsWeb && BackendSvc.getRemoteService() != null)
                    const SettingsSubtitle(
                      subtitle: "Note: Overrides any custom avatars set for group chats.",
                    ),
                  if (!kIsWeb) const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                  if (!kIsWeb)
                    Obx(() => SettingsSwitch(
                          onChanged: (bool val) async {
                            SettingsSvc.settings.scrollToLastUnread.value = val;
                            await SettingsSvc.settings.saveOneAsync('scrollToLastUnread');
                          },
                          initialVal: SettingsSvc.settings.scrollToLastUnread.value,
                          title: "Store Last Read Message",
                          subtitle:
                              "Remembers the last opened message and allows automatically scrolling back to it if out of view",
                          backgroundColor: tileColor,
                          isThreeLine: true,
                        )),
                  Obx(() => SettingsSwitch(
                        onChanged: (bool val) {
                          SettingsSvc.settings.hideNamesForReactions.value = val;
                          SettingsSvc.settings.saveOneAsync("hideNamesForReactions");
                        },
                        initialVal: SettingsSvc.settings.hideNamesForReactions.value,
                        title: "Hide Names in Reaction Details",
                        subtitle:
                            "Enable this to hide names under participant avatars when you view a message's reactions",
                        backgroundColor: tileColor,
                      )),
                ],
              ),
              if (!kIsWeb)
                SettingsHeader(
                  iosSubtitle: iosSubtitle,
                  materialSubtitle: materialSubtitle,
                  text: "Sounds",
                ),
              if (!kIsWeb && BackendSvc.getRemoteService() != null)
                Obx(
                  () => SettingsSection(
                    backgroundColor: tileColor,
                    children: [
                      SettingsTile(
                        title: "Download Sounds",
                        subtitle: "Downloads the official send/receive sounds",
                        onTap: () async {
                          showDialog(
                            context: context,
                            builder: (BuildContext context) {
                              return AlertDialog(
                                backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                                title: Text(
                                  "Downloading sounds...",
                                  style: context.theme.textTheme.titleLarge,
                                ),
                                content: SizedBox(
                                  height: 70,
                                  child: Center(
                                    child: CircularProgressIndicator(
                                      backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                                      valueColor: AlwaysStoppedAnimation<Color>(context.theme.colorScheme.primary),
                                    ),
                                  ),
                                ),
                              );
                            },
                          );
                          try {
                            final sendAudio = await HttpSvc.downloadFromUrl(
                              "https://chatsupport.apple.com/WebAPI801/sound/send-message-audio.m4a",
                            );
                            final receiveAudio = await HttpSvc.downloadFromUrl(
                              "https://chatsupport.apple.com/WebAPI801/sound/received-message-audio.m4a",
                            );
                            final sendPath = "${FilesystemSvc.soundsPath}/send-message-audio.m4a";
                            await File(sendPath).create(recursive: true);
                            await File(sendPath).writeAsBytes(sendAudio.data!);
                            SettingsSvc.settings.sendSoundPath.value = sendPath;
                            final receivePath = "${FilesystemSvc.soundsPath}/receive-message-audio.m4a";
                            await File(receivePath).create(recursive: true);
                            await File(receivePath).writeAsBytes(receiveAudio.data!);
                            SettingsSvc.settings.receiveSoundPath.value = receivePath;
                            await SettingsSvc.settings.saveAsync();
                            Get.back();
                          } catch (e, s) {
                            Get.back();
                            Logger.error("Failed to download official sounds", error: e, trace: s);
                            showSnackbar("Error", "Failed to fetch audio");
                          }
                        },
                      ),
                      SettingsTile(
                        title: "${SettingsSvc.settings.sendSoundPath.value == null ? "Add" : "Change"} Send Sound",
                        subtitle: SettingsSvc.settings.sendSoundPath.value != null
                            ? basename(SettingsSvc.settings.sendSoundPath.value!).substring("send-".length)
                            : "Adds a sound to be played when sending a message",
                        onTap: () async {
                          FilePickerResult? result = await FilePicker.pickFiles(type: FileType.audio, withData: true);
                          if (result != null) {
                            PlatformFile platformFile = result.files.first;
                            String path = join(FilesystemSvc.soundsPath, "send-${platformFile.name}");
                            await File(path).create(recursive: true);
                            await File(path).writeAsBytes(platformFile.bytes!);
                            SettingsSvc.settings.sendSoundPath.value = path;
                            await SettingsSvc.settings.saveOneAsync('sendSoundPath');
                          }
                        },
                        trailing: (SettingsSvc.settings.sendSoundPath.value == null)
                            ? const SizedBox.shrink()
                            : Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  IconButton(
                                      icon: playingSendSound.value
                                          ? Icon(SettingsSvc.settings.skin.value == Skins.iOS
                                              ? CupertinoIcons.stop
                                              : Icons.stop_outlined)
                                          : Icon(SettingsSvc.settings.skin.value == Skins.iOS
                                              ? CupertinoIcons.play
                                              : Icons.play_arrow_outlined),
                                      onPressed: () async {
                                        if (sendPlayer is Player) {
                                          final Player _sendPlayer = sendPlayer as Player;
                                          if (playingSendSound.value) {
                                            await _sendPlayer.stop();
                                          } else {
                                            await _sendPlayer
                                                .setVolume(SettingsSvc.settings.soundVolume.value.toDouble());
                                            await _sendPlayer.open(Media(SettingsSvc.settings.sendSoundPath.value!));
                                          }
                                        } else if (sendPlayer is aw.PlayerController) {
                                          final aw.PlayerController _sendPlayer = sendPlayer as aw.PlayerController;
                                          if (playingSendSound.value) {
                                            await _sendPlayer.pausePlayer();
                                          } else {
                                            if (!sendPrepared) {
                                              await _sendPlayer.preparePlayer(
                                                  path: SettingsSvc.settings.sendSoundPath.value!,
                                                  volume: SettingsSvc.settings.soundVolume.value.toDouble() / 100);
                                              sendPrepared = true;
                                            }
                                            _sendPlayer.setFinishMode(finishMode: aw.FinishMode.pause);
                                            await _sendPlayer.startPlayer();
                                          }
                                        }
                                      }),
                                  IconButton(
                                    icon: Icon(SettingsSvc.settings.skin.value == Skins.iOS
                                        ? CupertinoIcons.trash
                                        : Icons.delete_outline),
                                    onPressed: () async {
                                      File file = File(SettingsSvc.settings.sendSoundPath.value!);
                                      if (await file.exists()) {
                                        await file.delete();
                                      }
                                      SettingsSvc.settings.sendSoundPath.value = null;
                                      await SettingsSvc.settings.saveOneAsync('sendSoundPath');
                                    },
                                  ),
                                ],
                              ),
                      ),
                      const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                      SettingsTile(
                        title:
                            "${SettingsSvc.settings.receiveSoundPath.value == null ? "Add" : "Change"} Receive Sound",
                        subtitle: SettingsSvc.settings.receiveSoundPath.value != null
                            ? basename(SettingsSvc.settings.receiveSoundPath.value!).substring("receive-".length)
                            : "Adds a sound to be played when receiving a message",
                        onTap: () async {
                          FilePickerResult? result = await FilePicker.pickFiles(type: FileType.audio, withData: true);
                          if (result != null) {
                            PlatformFile platformFile = result.files.first;
                            String path = join(FilesystemSvc.soundsPath, "receive-${platformFile.name}");
                            await File(path).create(recursive: true);
                            await File(path).writeAsBytes(platformFile.bytes!);
                            SettingsSvc.settings.receiveSoundPath.value = path;
                            await SettingsSvc.settings.saveOneAsync('receiveSoundPath');
                          }
                        },
                        trailing: (SettingsSvc.settings.receiveSoundPath.value == null)
                            ? const SizedBox.shrink()
                            : Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  IconButton(
                                      icon: playingReceiveSound.value
                                          ? Icon(SettingsSvc.settings.skin.value == Skins.iOS
                                              ? CupertinoIcons.stop
                                              : Icons.stop_outlined)
                                          : Icon(SettingsSvc.settings.skin.value == Skins.iOS
                                              ? CupertinoIcons.play
                                              : Icons.play_arrow_outlined),
                                      onPressed: () async {
                                        if (receivePlayer is Player) {
                                          final Player _receivePlayer = receivePlayer as Player;
                                          if (playingReceiveSound.value) {
                                            await _receivePlayer.stop();
                                          } else {
                                            await _receivePlayer
                                                .setVolume(SettingsSvc.settings.soundVolume.value.toDouble());
                                            await _receivePlayer
                                                .open(Media(SettingsSvc.settings.receiveSoundPath.value!));
                                          }
                                        } else if (receivePlayer is aw.PlayerController) {
                                          final aw.PlayerController _receivePlayer =
                                              receivePlayer as aw.PlayerController;
                                          if (playingReceiveSound.value) {
                                            await _receivePlayer.pausePlayer();
                                          } else {
                                            if (!receivePrepared) {
                                              await _receivePlayer.preparePlayer(
                                                  path: SettingsSvc.settings.receiveSoundPath.value!,
                                                  volume: SettingsSvc.settings.soundVolume.value / 100);
                                              receivePrepared = true;
                                            }
                                            _receivePlayer.setFinishMode(finishMode: aw.FinishMode.pause);
                                            await _receivePlayer.startPlayer();
                                          }
                                        }
                                      }),
                                  IconButton(
                                    icon: Icon(SettingsSvc.settings.skin.value == Skins.iOS
                                        ? CupertinoIcons.trash
                                        : Icons.delete_outline),
                                    onPressed: () async {
                                      File file = File(SettingsSvc.settings.receiveSoundPath.value!);
                                      if (await file.exists()) {
                                        await file.delete();
                                      }
                                      SettingsSvc.settings.receiveSoundPath.value = null;
                                      await SettingsSvc.settings.saveOneAsync('receiveSoundPath');
                                    },
                                  ),
                                ],
                              ),
                      ),
                      const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                      const SettingsTile(
                        title: "Send/Receive Sound Volume",
                        subtitle: "Controls the volume of the send and receive sounds",
                      ),
                      Obx(() => SettingsSlider(
                            startingVal: SettingsSvc.settings.soundVolume.value.toDouble(),
                            min: 0,
                            max: 100,
                            divisions: 100,
                            formatValue: (val) => "${val.toInt()}",
                            update: (val) => SettingsSvc.settings.soundVolume.value = val.toInt(),
                          )),
                    ],
                  ),
                ),
              SettingsHeader(
                iosSubtitle: iosSubtitle,
                materialSubtitle: materialSubtitle,
                text: "Gestures",
              ),
              SettingsSection(
                backgroundColor: tileColor,
                children: [
                  if (!kIsWeb && !kIsDesktop)
                    Obx(() => SettingsSwitch(
                          onChanged: (bool val) async {
                            SettingsSvc.settings.autoOpenKeyboard.value = val;
                            await SettingsSvc.settings.saveOneAsync('autoOpenKeyboard');
                          },
                          initialVal: SettingsSvc.settings.autoOpenKeyboard.value,
                          title: "Auto-open Keyboard",
                          subtitle: "Automatically open the keyboard when entering a chat",
                          backgroundColor: tileColor,
                        )),
                  if (!kIsWeb && !kIsDesktop) const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                  if (!kIsWeb && !kIsDesktop)
                    Obx(() => SettingsSwitch(
                          onChanged: (bool val) async {
                            SettingsSvc.settings.swipeToCloseKeyboard.value = val;
                            await SettingsSvc.settings.saveOneAsync('swipeToCloseKeyboard');
                          },
                          initialVal: SettingsSvc.settings.swipeToCloseKeyboard.value,
                          title: "Swipe Message Box to Close Keyboard",
                          subtitle: "Swipe down on the message box to hide the keyboard",
                          backgroundColor: tileColor,
                        )),
                  if (!kIsWeb && !kIsDesktop) const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                  if (!kIsWeb && !kIsDesktop)
                    Obx(() => SettingsSwitch(
                          onChanged: (bool val) async {
                            SettingsSvc.settings.swipeToOpenKeyboard.value = val;
                            await SettingsSvc.settings.saveOneAsync('swipeToOpenKeyboard');
                          },
                          initialVal: SettingsSvc.settings.swipeToOpenKeyboard.value,
                          title: "Swipe Message Box to Open Keyboard",
                          subtitle: "Swipe up on the message box to show the keyboard",
                          backgroundColor: tileColor,
                        )),
                  if (!kIsWeb && !kIsDesktop) const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                  if (!kIsWeb && !kIsDesktop)
                    Obx(() => SettingsSwitch(
                          onChanged: (bool val) async {
                            SettingsSvc.settings.hideKeyboardOnScroll.value = val;
                            await SettingsSvc.settings.saveOneAsync('hideKeyboardOnScroll');
                          },
                          initialVal: SettingsSvc.settings.hideKeyboardOnScroll.value,
                          title: "Hide Keyboard When Scrolling",
                          backgroundColor: tileColor,
                        )),
                  if (!kIsWeb && !kIsDesktop) const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                  if (!kIsWeb && !kIsDesktop)
                    Obx(() => SettingsSwitch(
                          onChanged: (bool val) async {
                            SettingsSvc.settings.openKeyboardOnSTB.value = val;
                            await SettingsSvc.settings.saveOneAsync('openKeyboardOnSTB');
                          },
                          initialVal: SettingsSvc.settings.openKeyboardOnSTB.value,
                          title: "Open Keyboard After Tapping Scroll To Bottom",
                          backgroundColor: tileColor,
                        )),
                  if (!kIsWeb && !kIsDesktop) const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                  Obx(() => SettingsSwitch(
                        onChanged: (bool val) async {
                          SettingsSvc.settings.doubleTapForDetails.value = val;
                          if (val && SettingsSvc.settings.enableQuickTapback.value) {
                            SettingsSvc.settings.enableQuickTapback.value = false;
                            await SettingsSvc.settings.saveManyAsync(['doubleTapForDetails', 'enableQuickTapback']);
                          } else {
                            await SettingsSvc.settings.saveOneAsync('doubleTapForDetails');
                          }
                        },
                        initialVal: SettingsSvc.settings.doubleTapForDetails.value,
                        title: "Double-${kIsWeb || kIsDesktop ? "Click" : "Tap"} Message for Details",
                        subtitle:
                            "Opens the message details popup when double ${kIsWeb || kIsDesktop ? "click" : "tapp"}ing a message",
                        backgroundColor: tileColor,
                        isThreeLine: true,
                      )),
                  if (!kIsDesktop && !kIsWeb) const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                  if (!kIsDesktop && !kIsWeb)
                    Obx(() => SettingsSwitch(
                          onChanged: (bool val) async {
                            SettingsSvc.settings.sendWithReturn.value = val;
                            await SettingsSvc.settings.saveOneAsync('sendWithReturn');
                          },
                          initialVal: SettingsSvc.settings.sendWithReturn.value,
                          title: "Send Message with Enter",
                          backgroundColor: tileColor,
                        )),
                  const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                  Obx(() => SettingsSwitch(
                        onChanged: (bool val) async {
                          SettingsSvc.settings.scrollToBottomOnSend.value = val;
                          await SettingsSvc.settings.saveOneAsync('scrollToBottomOnSend');
                        },
                        initialVal: SettingsSvc.settings.scrollToBottomOnSend.value,
                        title: "Scroll To Bottom When Sending Messages",
                        subtitle: "Scroll to the most recent messages in the chat when sending a new text",
                        backgroundColor: tileColor,
                      )),
                ],
              ),
              SettingsHeader(
                  iosSubtitle: iosSubtitle, materialSubtitle: materialSubtitle, text: "Interaction Settings"),
              Obx(() => SettingsSection(
                    backgroundColor: tileColor,
                    children: [
                      SettingsSwitch(
                        onChanged: (bool val) {
                          SettingsSvc.settings.privateSendTypingIndicators.value = val;
                          saveSettings();
                        },
                        initialVal: SettingsSvc.settings.privateSendTypingIndicators.value,
                        title: "Send Typing Indicators",
                        subtitle: "Sends typing indicators to other iMessage users",
                        backgroundColor: tileColor,
                        leading: const SettingsLeadingIcon(
                          iosIcon: CupertinoIcons.keyboard_chevron_compact_down,
                          materialIcon: Icons.keyboard_alt_outlined,
                          containerColor: Colors.green,
                        ),
                      ),
                      AnimatedSizeAndFade(
                        child: !SettingsSvc.settings.privateManualMarkAsRead.value
                            ? Column(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  const SettingsDivider(),
                                  SettingsSwitch(
                                      onChanged: (bool val) {
                                        SettingsSvc.settings.privateMarkChatAsRead.value = val;
                                        if (val) {
                                          SettingsSvc.settings.privateManualMarkAsRead.value = false;
                                        }
                                        saveSettings();
                                      },
                                      initialVal: SettingsSvc.settings.privateMarkChatAsRead.value,
                                      title: "Automatic Mark Read / Send Read Receipts",
                                      subtitle:
                                          "Marks chats read in the iMessage app on your server and sends read receipts to other iMessage users",
                                      backgroundColor: tileColor,
                                      isThreeLine: true,
                                      leading: const SettingsLeadingIcon(
                                        iosIcon: CupertinoIcons.rectangle_fill_badge_checkmark,
                                        materialIcon: Icons.playlist_add_check,
                                        containerColor: Colors.blueAccent,
                                      )),
                                ],
                              )
                            : const SizedBox.shrink(),
                      ),
                      AnimatedSizeAndFade.showHide(
                        show: !SettingsSvc.settings.privateMarkChatAsRead.value,
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const SettingsDivider(),
                            SettingsSwitch(
                              onChanged: (bool val) {
                                SettingsSvc.settings.privateManualMarkAsRead.value = val;
                                saveSettings();
                              },
                              initialVal: SettingsSvc.settings.privateManualMarkAsRead.value,
                              title: "Manual Mark Read / Send Read Receipts",
                              subtitle: "Only mark a chat read when pressing the manual mark read button",
                              backgroundColor: tileColor,
                              isThreeLine: true,
                              leading: const SettingsLeadingIcon(
                                iosIcon: CupertinoIcons.check_mark_circled,
                                materialIcon: Icons.check_circle_outline,
                                containerColor: Colors.orange,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SettingsDivider(),
                      SettingsSwitch(
                        title: "Double-${kIsWeb || kIsDesktop ? "Click" : "Tap"} Message for Quick Tapback",
                        initialVal: SettingsSvc.settings.enableQuickTapback.value,
                        onChanged: (bool val) {
                          SettingsSvc.settings.enableQuickTapback.value = val;
                          if (val && SettingsSvc.settings.doubleTapForDetails.value) {
                            SettingsSvc.settings.doubleTapForDetails.value = false;
                          }
                          saveSettings();
                        },
                        subtitle:
                            "Send a tapback of your choosing when double ${kIsWeb || kIsDesktop ? "click" : "tapp"}ing a message",
                        backgroundColor: tileColor,
                        isThreeLine: true,
                        leading: const SettingsLeadingIcon(
                          iosIcon: CupertinoIcons.rays,
                          materialIcon: Icons.touch_app_outlined,
                          containerColor: Colors.purple,
                        ),
                      ),
                      AnimatedSizeAndFade.showHide(
                        show: SettingsSvc.settings.enableQuickTapback.value,
                        child: Padding(
                          padding: const EdgeInsets.only(bottom: 5.0),
                          child: SettingsOptions<String>(
                            title: "Quick Tapback",
                            options: ReactionTypes.toList().take(6).toList(),
                            cupertinoCustomWidgets: [
                              Padding(
                                padding: const EdgeInsets.symmetric(vertical: 7.5),
                                child: ReactionWidget(
                                  reaction: Message(
                                      guid: "",
                                      associatedMessageType: ReactionTypes.LOVE,
                                      isFromMe: SettingsSvc.settings.quickTapbackType.value != ReactionTypes.LOVE),
                                ),
                              ),
                              Padding(
                                padding: const EdgeInsets.symmetric(vertical: 7.5),
                                child: ReactionWidget(
                                  reaction: Message(
                                      guid: "",
                                      associatedMessageType: ReactionTypes.LIKE,
                                      isFromMe: SettingsSvc.settings.quickTapbackType.value != ReactionTypes.LIKE),
                                ),
                              ),
                              Padding(
                                padding: const EdgeInsets.symmetric(vertical: 7.5),
                                child: ReactionWidget(
                                  reaction: Message(
                                      guid: "",
                                      associatedMessageType: ReactionTypes.DISLIKE,
                                      isFromMe: SettingsSvc.settings.quickTapbackType.value != ReactionTypes.DISLIKE),
                                ),
                              ),
                              Padding(
                                padding: const EdgeInsets.symmetric(vertical: 7.5),
                                child: ReactionWidget(
                                  reaction: Message(
                                      guid: "",
                                      associatedMessageType: ReactionTypes.LAUGH,
                                      isFromMe: SettingsSvc.settings.quickTapbackType.value != ReactionTypes.LAUGH),
                                ),
                              ),
                              Padding(
                                padding: const EdgeInsets.symmetric(vertical: 7.5),
                                child: ReactionWidget(
                                  reaction: Message(
                                      guid: "",
                                      associatedMessageType: ReactionTypes.EMPHASIZE,
                                      isFromMe: SettingsSvc.settings.quickTapbackType.value != ReactionTypes.EMPHASIZE),
                                ),
                              ),
                              Padding(
                                padding: const EdgeInsets.symmetric(vertical: 7.5),
                                child: ReactionWidget(
                                  reaction: Message(
                                      guid: "",
                                      associatedMessageType: ReactionTypes.QUESTION,
                                      isFromMe: SettingsSvc.settings.quickTapbackType.value != ReactionTypes.QUESTION),
                                ),
                              ),
                            ],
                            initial: SettingsSvc.settings.quickTapbackType.value,
                            textProcessing: (val) => val,
                            onChanged: (val) {
                              if (val == null) return;
                              SettingsSvc.settings.quickTapbackType.value = val;
                              saveSettings();
                            },
                            secondaryColor: headerColor,
                          ),
                        ),
                      ),
                      AnimatedSizeAndFade.showHide(
                        show: BackendSvc.canEditUnsend(),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const SettingsDivider(),
                            SettingsSwitch(
                              title: "Up Arrow for Quick Edit",
                              initialVal: SettingsSvc.settings.editLastSentMessageOnUpArrow.value,
                              onChanged: (bool val) {
                                SettingsSvc.settings.editLastSentMessageOnUpArrow.value = val;
                                saveSettings();
                              },
                              subtitle: "Press the Up Arrow to begin editing the last message you sent",
                              backgroundColor: tileColor,
                              leading: const SettingsLeadingIcon(
                                iosIcon: CupertinoIcons.arrow_up_square,
                                materialIcon: Icons.arrow_circle_up,
                                containerColor: Colors.redAccent,
                              ),
                            ),
                          ],
                        ),
                      ),
                      AnimatedSizeAndFade.showHide(
                        show: BackendSvc.canSendSubject(),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const SettingsDivider(),
                            SettingsSwitch(
                              onChanged: (bool val) {
                                SettingsSvc.settings.privateSubjectLine.value = val;
                                saveSettings();
                              },
                              initialVal: SettingsSvc.settings.privateSubjectLine.value,
                              title: "Send Subject Lines",
                              subtitle: "Show the subject line field when sending a message",
                              backgroundColor: tileColor,
                              isThreeLine: true,
                              leading: const SettingsLeadingIcon(
                                iosIcon: CupertinoIcons.textformat,
                                materialIcon: Icons.text_format_rounded,
                                containerColor: Colors.blueAccent,
                              ),
                            ),
                          ],
                        ),
                      ),
                      AnimatedSizeAndFade.showHide(
                        show: Platform.isAndroid,
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const SettingsDivider(),
                            SettingsSwitch(
                              onChanged: (bool val) async {
                                PushSvc.setupZenMode(val);
                              },
                              initialVal: SettingsSvc.settings.enableShareZen.value,
                              title: "Share Status",
                              subtitle: "Other users will see when you have notifications silenced.",
                              backgroundColor: tileColor,
                              isThreeLine: true,
                              leading: const SettingsLeadingIcon(
                                iosIcon: CupertinoIcons.moon_fill,
                                materialIcon: CupertinoIcons.moon_fill,
                                containerColor: Colors.deepPurple,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ))
            ],
          ),
        ),
      ],
    );
  }

  @override
  void dispose() {
    if (kIsDesktop) {
      (sendPlayer as Player).dispose();
      (receivePlayer as Player).dispose();
    } else {
      (sendPlayer as aw.PlayerController).dispose();
      (receivePlayer as aw.PlayerController).dispose();
    }
    super.dispose();
  }

  void saveSettings() {
    SettingsSvc.settings.saveAsync();
  }
}
