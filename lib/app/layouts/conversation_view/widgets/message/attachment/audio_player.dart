import 'dart:io';

import 'package:audio_waveforms/audio_waveforms.dart';
import 'package:bluebubbles/helpers/helpers.dart';
// it does actually export (Web only)
// ignore: undefined_hidden_name
import 'package:bluebubbles/database/models.dart' hide PlayerState;
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';

class AudioPlayer extends StatefulWidget {
  final PlatformFile file;
  final Attachment? attachment;
  final String? transcript;
  final ConversationViewController? controller;

  const AudioPlayer({
    super.key,
    required this.file,
    required this.attachment,
    this.transcript,
    this.controller,
    this.playButtonFocusNode,
    this.nextFocusNode,
  });

  final FocusNode? playButtonFocusNode;
  final FocusNode? nextFocusNode;

  @override
  State<StatefulWidget> createState() => _createState();

  State<StatefulWidget> _createState() {
    // Dumb (D-pad) devices use the desktop player (just_audio) instead of audio_waveforms,
    // which has been unreliable on mobile since the 2.x upgrade — mirrors the recording path.
    if (kIsDesktop || SettingsSvc.settings.isDumb.value) return _DesktopAudioPlayerState();
    return _AudioPlayerState();
  }
}

class _AudioPlayerState extends State<AudioPlayer> with AutomaticKeepAliveClientMixin, SingleTickerProviderStateMixin {
  Attachment? get attachment => widget.attachment;

  PlatformFile get file => widget.file;

  ConversationViewController? get cvController => widget.controller;

  PlayerController? controller;
  late final animController = AnimationController(
      vsync: this, duration: const Duration(milliseconds: 400), animationBehavior: AnimationBehavior.preserve);
  final playerState = Rx<PlayerState?>(null);
  final maxDuration = 0.obs;

  @override
  void initState() {
    super.initState();
    if (attachment != null) controller = cvController?.audioPlayers[attachment!.guid];
    initBytes();
  }

  @override
  void dispose() {
    if (attachment == null) {
      controller?.dispose();
    }
    animController.dispose();
    super.dispose();
  }

  void initBytes() async {
    if (attachment != null) controller = cvController?.audioPlayers[attachment!.guid];
    if (controller == null) {
      controller = PlayerController()
        ..addListener(() {
          maxDuration.value = controller!.maxDuration;
        });
      controller!.onPlayerStateChanged.listen((event) {
        if ((controller!.playerState == PlayerState.paused || controller!.playerState == PlayerState.stopped) &&
            animController.value > 0) {
          animController.reverse();
        }
        playerState.value = controller!.playerState;
      });
      await controller!.preparePlayer(path: file.path!);
      if (attachment != null) cvController?.audioPlayers[attachment!.guid!] = controller!;
    }
    playerState.value = controller?.playerState;
    maxDuration.value = controller?.maxDuration ?? 0;
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return Padding(
        padding: const EdgeInsets.all(5),
        child: Column(mainAxisSize: MainAxisSize.min, mainAxisAlignment: MainAxisAlignment.center, children: [
          Row(
            children: [
              CallbackShortcuts(
                bindings: {
                  const SingleActivator(LogicalKeyboardKey.arrowRight): () => widget.nextFocusNode?.requestFocus(),
                  const SingleActivator(LogicalKeyboardKey.arrowDown): () => widget.nextFocusNode?.requestFocus(),
                  const SingleActivator(LogicalKeyboardKey.enter): () async {
                    if (controller == null) return;
                    if (playerState.value == PlayerState.playing) {
                      animController.reverse();
                      await controller!.pausePlayer();
                    } else {
                      animController.forward();
                      controller!.setFinishMode(finishMode: FinishMode.pause);
                      await controller!.startPlayer();
                    }
                  },
                  const SingleActivator(LogicalKeyboardKey.select): () async {
                    if (controller == null) return;
                    if (playerState.value == PlayerState.playing) {
                      animController.reverse();
                      await controller!.pausePlayer();
                    } else {
                      animController.forward();
                      controller!.setFinishMode(finishMode: FinishMode.pause);
                      await controller!.startPlayer();
                    }
                  },
                  const SingleActivator(LogicalKeyboardKey.space): () async {
                    if (controller == null) return;
                    if (playerState.value == PlayerState.playing) {
                      animController.reverse();
                      await controller!.pausePlayer();
                    } else {
                      animController.forward();
                      controller!.setFinishMode(finishMode: FinishMode.pause);
                      await controller!.startPlayer();
                    }
                  },
                },
                child: IconButton(
                  focusNode: widget.playButtonFocusNode,
                  style: ButtonStyle(
                    backgroundColor: WidgetStateProperty.resolveWith(
                      (states) => states.contains(WidgetState.focused)
                          ? context.theme.colorScheme.outline.withOpacity(0.2)
                          : null,
                    ),
                  ),
                  onPressed: () async {
                    if (controller == null) return;
                    if (playerState.value == PlayerState.playing) {
                      animController.reverse();
                      await controller!.pausePlayer();
                    } else {
                      animController.forward();
                      controller!.setFinishMode(finishMode: FinishMode.pause);
                      await controller!.startPlayer();
                    }
                  },
                  icon: AnimatedIcon(
                    icon: AnimatedIcons.play_pause,
                    progress: animController,
                  ),
                  color: context.theme.colorScheme.onSurfaceVariant,
                  visualDensity: VisualDensity.compact,
                ),
              ),
              Obx(() => maxDuration.value == 0
                  ? SizedBox(width: NavigationSvc.width(context) * 0.25)
                  : AudioFileWaveforms(
                      size: Size(NavigationSvc.width(context) * 0.20, 40),
                      playerController: controller!,
                      padding: EdgeInsets.zero,
                      playerWaveStyle: PlayerWaveStyle(
                          fixedWaveColor: context.theme.colorScheme.surfaceContainerHighest.oppositeLightenOrDarken(20),
                          liveWaveColor: context.theme.colorScheme.onSurfaceVariant,
                          waveCap: StrokeCap.square,
                          waveThickness: 2,
                          seekLineThickness: 2,
                          showSeekLine: false),
                    )),
              const SizedBox(width: 5),
              Expanded(
                child: Center(
                  heightFactor: 1,
                  child: Obx(() => Text(prettyDuration(Duration(milliseconds: maxDuration.value)),
                      style: context.theme.textTheme.labelLarge!)),
                ),
              ),
            ],
          ),
          if (widget.transcript != null)
            Padding(
              padding: const EdgeInsets.only(top: 5, left: 10, right: 10, bottom: 5),
              child: Text(
                "${widget.transcript}",
                style: context.theme.textTheme.bodySmall,
              ),
            ),
        ]));
  }

  @override
  bool get wantKeepAlive => true;
}

class _DesktopAudioPlayerState extends State<AudioPlayer>
    with AutomaticKeepAliveClientMixin, SingleTickerProviderStateMixin {
  Attachment? get attachment => widget.attachment;

  PlatformFile get file => widget.file;

  ConversationViewController? get cvController => widget.controller;

  Player? controller;
  late final animController = AnimationController(
      vsync: this, duration: const Duration(milliseconds: 400), animationBehavior: AnimationBehavior.preserve);
  final isPlaying = false.obs;
  final position = Duration.zero.obs;
  final duration = Duration.zero.obs;

  @override
  void initState() {
    super.initState();
    if (attachment != null) controller = cvController?.audioPlayersDesktop[attachment!.guid];
    initBytes();
  }

  @override
  void dispose() {
    if (attachment == null) {
      controller?.dispose();
    }
    animController.dispose();
    super.dispose();
  }

  void initBytes() async {
    if (attachment != null) controller = cvController?.audioPlayersDesktop[attachment!.guid];
    if (controller == null) {
      controller = Player()
        ..stream.position.listen((pos) => position.value = pos)
        ..stream.duration.listen((dur) => duration.value = dur)
        ..stream.playing.listen((playing) => isPlaying.value = playing)
        ..stream.completed.listen((bool completed) async {
          if (completed) {
            // Pause before seeking so the player doesn't auto-replay — on Android seeking to zero
            // while still "playing" restarts it, looping forever. Reset to the start so the next
            // tap/Enter plays from the beginning.
            await controller!.pause();
            await controller!.seek(Duration.zero);
            animController.reverse();
          }
        });
      // Register the player immediately (before the async open) so a focused message can
      // play/pause it via Enter as soon as it mounts — _canToggleAudioMessage checks this map.
      if (attachment != null) cvController?.audioPlayersDesktop[attachment!.guid!] = controller!;
      await controller!.setPlaylistMode(PlaylistMode.none);
      await controller!.open(Media(file.path!), play: false);
    }
    isPlaying.value = controller?.state.playing ?? false;
    position.value = controller?.state.position ?? Duration.zero;
    duration.value = controller?.state.duration ?? Duration.zero;
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return Padding(
        padding: const EdgeInsets.all(5),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            CallbackShortcuts(
              bindings: {
                const SingleActivator(LogicalKeyboardKey.arrowRight): () => widget.nextFocusNode?.requestFocus(),
                const SingleActivator(LogicalKeyboardKey.arrowDown): () => widget.nextFocusNode?.requestFocus(),
                const SingleActivator(LogicalKeyboardKey.enter): () async {
                  if (controller == null) return;
                  if (isPlaying.value) {
                    animController.reverse();
                    await controller!.pause();
                  } else {
                    animController.forward();
                    await controller!.play();
                  }
                },
                const SingleActivator(LogicalKeyboardKey.select): () async {
                  if (controller == null) return;
                  if (isPlaying.value) {
                    animController.reverse();
                    await controller!.pause();
                  } else {
                    animController.forward();
                    await controller!.play();
                  }
                },
                const SingleActivator(LogicalKeyboardKey.space): () async {
                  if (controller == null) return;
                  if (isPlaying.value) {
                    animController.reverse();
                    await controller!.pause();
                  } else {
                    animController.forward();
                    await controller!.play();
                  }
                },
              },
              child: IconButton(
                focusNode: widget.playButtonFocusNode,
                style: ButtonStyle(
                  backgroundColor: WidgetStateProperty.resolveWith(
                    (states) => states.contains(WidgetState.focused)
                        ? context.theme.colorScheme.outline.withOpacity(0.2)
                        : null,
                  ),
                ),
                onPressed: () async {
                  if (controller == null) return;
                  if (isPlaying.value) {
                    animController.reverse();
                    await controller!.pause();
                  } else {
                    animController.forward();
                    await controller!.play();
                  }
                },
                icon: AnimatedIcon(
                  icon: AnimatedIcons.play_pause,
                  progress: animController,
                ),
                color: context.theme.colorScheme.onSurfaceVariant,
                visualDensity: VisualDensity.compact,
              ),
            ),
            if (controller != null)
              Obx(() => SizedBox(
                    height: 30,
                    child: Slider(
                      value: position.value.inSeconds.toDouble(),
                      onChanged: (double value) {
                        controller!.seek(Duration(seconds: value.toInt()));
                      },
                      min: 0,
                      max: duration.value.inSeconds.toDouble(),
                    ),
                  )),
            Obx(() => Padding(
                  padding: const EdgeInsets.only(left: 10, right: 16),
                  child: Text("${prettyDuration(position.value)} / ${prettyDuration(duration.value)}"),
                ))
          ],
        ));
  }

  @override
  bool get wantKeepAlive => true;
}
