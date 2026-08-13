import 'dart:async';

import 'package:flutter/scheduler.dart';
import 'package:bluebubbles/app/layouts/fullscreen_media/dialogs/metadata_dialog.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/services.dart';

// (needed for custom back button)
//ignore: implementation_imports
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:media_kit_video/media_kit_video_controls/media_kit_video_controls.dart' as media_kit_video_controls;
import 'package:universal_html/html.dart' as html;

class FullscreenVideo extends StatefulWidget {
  const FullscreenVideo({
    super.key,
    required this.file,
    required this.attachment,
    required this.showInteractions,
    this.videoController,
    this.mute,
    this.onOverlayToggle,
  });

  final PlatformFile file;
  final Attachment attachment;
  final bool showInteractions;

  final VideoController? videoController;
  final RxBool? mute;
  final Function(bool)? onOverlayToggle;

  @override
  State<StatefulWidget> createState() => _FullscreenVideoState();
}

class _FullscreenVideoState extends State<FullscreenVideo> with AutomaticKeepAliveClientMixin, ThemeHelpers {
  Timer? hideOverlayTimer;

  late VideoController videoController;

  bool hasListener = false;
  bool hasDisposed = false;
  Offset? _pointerDownPosition;
  final RxBool muted = SettingsSvc.settings.startVideosMutedFullscreen.value.obs;
  final RxBool showPlayPauseOverlay = true.obs;
  final RxDouble aspectRatio = 1.0.obs;

  @override
  void initState() {
    super.initState();

    if (widget.mute != null) {
      muted.value = widget.mute!.value;
    }

    _setFullscreen(true);
    initControllers();
  }

  void _setFullscreen(bool fullscreen) {
    if (fullscreen) {
      SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersive);
    } else if (SettingsSvc.settings.immersiveMode.value) {
      SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    } else {
      // Restore the non-immersive (manual) mode the app uses when Immersive Mode is off, rather
      // than forcing edgeToEdge — otherwise exiting the viewer leaves the app in the wrong UI mode.
      SystemChrome.setEnabledSystemUIMode(SystemUiMode.manual,
          overlays: [SystemUiOverlay.top, SystemUiOverlay.bottom]);
    }
  }

  void initControllers() async {
    if (widget.videoController != null) {
      // Reuse existing controller from in-chat player
      videoController = widget.videoController!;
      // Sync mute state
      await videoController.player.setVolume(muted.value ? 0 : 100);
    } else {
      // Create new controller
      final player = Player();
      videoController = VideoController(player);

      late final Media media;
      if (widget.file.path == null) {
        final blob = html.Blob([widget.file.bytes]);
        final url = html.Url.createObjectUrlFromBlob(blob);
        media = Media(url);
      } else {
        media = Media(widget.file.path!);
      }

      await videoController.player.setPlaylistMode(PlaylistMode.none);
      await videoController.player.open(media, play: false);
      await _configureAndroidVideoColorPipeline(videoController.player);
      await videoController.player.setVolume(muted.value ? 0 : 100);
    }

    createListener(videoController);

    if (!kIsDesktop && !kIsWeb) {
      // Start with all overlays hidden
      showPlayPauseOverlay.value = false;
      widget.onOverlayToggle?.call(false);
      // Auto-play after a short delay so the viewer is fully visible first
      Future.delayed(const Duration(milliseconds: 500), () {
        if (!hasDisposed) videoController.player.play();
      });
    } else {
      showPlayPauseOverlay.value = true;
    }

    setState(() {});
  }

  Future<void> _configureAndroidVideoColorPipeline(Player player) async {
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return;
    final platform = player.platform;
    if (platform is! NativePlayer) return;

    try {
      // Force conservative HDR->SDR conversion for iPhone HDR clips, which can
      // otherwise appear over-bright on some Android GPU + mpv combinations.
      await platform.setProperty('target-colorspace-hint', 'yes');
      await platform.setProperty('target-prim', 'bt.709');
      await platform.setProperty('target-trc', 'srgb');
      await platform.setProperty('tone-mapping', 'hable');
      await platform.setProperty('target-peak', '203');
      await platform.setProperty('hdr-compute-peak', 'no');
    } catch (e, s) {
      debugPrint('FullscreenVideo: Failed to apply Android video color pipeline: $e');
      debugPrint(s.toString());
    }
  }

  void createListener(VideoController controller) {
    if (hasListener) return;

    controller.rect.addListener(() {
      aspectRatio.value = controller.aspectRatio;
    });

    controller.player.stream.completed.listen((completed) async {
      // If the status is ended, restart
      if (completed && !hasDisposed) {
        await controller.player.pause();
        await controller.player.seek(Duration.zero);
        await controller.player.pause();
        _cancelHideTimer();
        showPlayPauseOverlay.value = true;
        showPlayPauseOverlay.refresh();
        widget.onOverlayToggle?.call(true);
      }
    });

    controller.player.stream.playing.listen((playing) {
      if (hasDisposed || kIsDesktop || kIsWeb) return;
      if (playing) {
        // Only start the hide timer if the overlays are currently visible
        if (showPlayPauseOverlay.value) {
          _startHideTimer();
        }
      } else {
        // Video paused — always show all overlays
        _cancelHideTimer();
        showPlayPauseOverlay.value = true;
        widget.onOverlayToggle?.call(true);
      }
    });

    hasListener = true;
  }

  void _cancelHideTimer() {
    hideOverlayTimer?.cancel();
    hideOverlayTimer = null;
  }

  void _startHideTimer() {
    _cancelHideTimer();
    hideOverlayTimer = Timer(const Duration(seconds: 3), () {
      if (!hasDisposed) {
        showPlayPauseOverlay.value = false;
        widget.onOverlayToggle?.call(false);
      }
    });
  }

  void _handleTap() {
    if (!widget.showInteractions || kIsDesktop || kIsWeb) return;
    final newVal = !showPlayPauseOverlay.value;
    showPlayPauseOverlay.value = newVal;
    widget.onOverlayToggle?.call(newVal);
    if (newVal && videoController.player.state.playing) {
      _startHideTimer();
    } else {
      _cancelHideTimer();
    }
  }

  @override
  void dispose() {
    hasDisposed = true;
    _cancelHideTimer();
    _setFullscreen(false);

    // Sync mute state back to parent — deferred to avoid mutating an Rx value
    // while the widget tree is locked (causes markNeedsBuild error in Obx).
    if (widget.mute != null) {
      final muteValue = muted.value;
      SchedulerBinding.instance.addPostFrameCallback((_) {
        widget.mute!.value = muteValue;
      });
    }

    // Only dispose the player if one was not passed in (via a controller)
    if (widget.videoController == null) {
      videoController.player.dispose();
    }

    super.dispose();
  }

  void refreshAttachment() {
    showSnackbar('In Progress', 'Redownloading attachment. Please wait...');
    AttachmentsSvc.redownloadAttachment(widget.attachment, onComplete: (file) async {
      if (hasDisposed) return;
      hasListener = false;
      late final Media media;
      if (widget.file.path == null) {
        final blob = html.Blob([widget.file.bytes]);
        final url = html.Url.createObjectUrlFromBlob(blob);
        media = Media(url);
      } else {
        media = Media(widget.file.path!);
      }
      await videoController.player.open(media, play: false);
      await videoController.player.setVolume(muted.value ? 0 : 100);
      createListener(videoController);
      showPlayPauseOverlay.value = !videoController.player.state.playing;
    });
  }

  @override
  bool get wantKeepAlive => true;

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final RxBool _hover = false.obs;
    return Container(
      color: Colors.black,
      child: Obx(
        () => MouseRegion(
          onEnter: (event) => showPlayPauseOverlay.value = true,
          onExit: (event) => showPlayPauseOverlay.value = !videoController.player.state.playing,
          child: Theme(
            data: context.theme.copyWith(
                platform: iOS ? TargetPlatform.iOS : TargetPlatform.android,
                dialogBackgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                iconTheme: context.theme.iconTheme.copyWith(color: context.theme.textTheme.bodyMedium?.color)),
            child: Stack(
              alignment: Alignment.center,
              children: <Widget>[
                Listener(
                  behavior: HitTestBehavior.translucent,
                  onPointerDown: (event) {
                    _pointerDownPosition = event.position;
                  },
                  onPointerUp: (event) {
                    if (_pointerDownPosition != null) {
                      final distance = (_pointerDownPosition! - event.position).distance;
                      if (distance < 20) _handleTap();
                      _pointerDownPosition = null;
                    }
                  },
                  child: Video(
                      controller: videoController,
                      controls: (state) => Padding(
                            padding: EdgeInsets.all(!kIsWeb && !kIsDesktop ? 0 : 20).copyWith(
                                bottom: !kIsWeb && !kIsDesktop ? (iOS && widget.showInteractions ? 100 : 10) : 0),
                            child: kIsDesktop
                                ? media_kit_video_controls.MaterialDesktopVideoControls(state)
                                : media_kit_video_controls.MaterialVideoControls(state),
                          ),
                      filterQuality: FilterQuality.medium),
                ),
                if (kIsWeb || kIsDesktop)
                  Obx(() {
                    return MouseRegion(
                      onEnter: (event) => _hover.value = true,
                      onExit: (event) => _hover.value = false,
                      child: AbsorbPointer(
                        absorbing: !showPlayPauseOverlay.value && !_hover.value,
                        child: AnimatedOpacity(
                          opacity: _hover.value
                              ? 1
                              : showPlayPauseOverlay.value
                                  ? 0.5
                                  : 0,
                          duration: const Duration(milliseconds: 100),
                          child: Material(
                            color: Colors.transparent,
                            child: InkWell(
                              borderRadius: BorderRadius.circular(40),
                              onTap: () async {
                                if (videoController.player.state.playing) {
                                  await videoController.player.pause();
                                  showPlayPauseOverlay.value = true;
                                } else {
                                  await videoController.player.play();
                                  showPlayPauseOverlay.value = false;
                                }
                              },
                              child: Container(
                                height: 75,
                                width: 75,
                                decoration: BoxDecoration(
                                  color: context.theme.colorScheme.surface.withValues(alpha: 0.5),
                                  borderRadius: BorderRadius.circular(40),
                                ),
                                clipBehavior: Clip.antiAlias,
                                child: Padding(
                                  padding: EdgeInsets.only(
                                    left: SettingsSvc.settings.skin.value == Skins.iOS &&
                                            !videoController.player.state.playing
                                        ? 17
                                        : 10,
                                    top: SettingsSvc.settings.skin.value == Skins.iOS ? 13 : 10,
                                    right: 10,
                                    bottom: 10,
                                  ),
                                  child: Obx(
                                    () => videoController.player.state.playing
                                        ? Icon(
                                            SettingsSvc.settings.skin.value == Skins.iOS
                                                ? CupertinoIcons.pause
                                                : Icons.pause,
                                            color: context.iconColor,
                                            size: 45,
                                          )
                                        : Icon(
                                            SettingsSvc.settings.skin.value == Skins.iOS
                                                ? CupertinoIcons.play
                                                : Icons.play_arrow,
                                            color: context.iconColor,
                                            size: 45,
                                          ),
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                    );
                  }),
                if (!iOS && (kIsWeb || kIsDesktop))
                  Positioned(
                    top: 10,
                    left: 10,
                    child: Obx(() {
                      return MouseRegion(
                        onEnter: (event) => _hover.value = true,
                        onExit: (event) => _hover.value = false,
                        child: AbsorbPointer(
                          absorbing: !showPlayPauseOverlay.value && !_hover.value,
                          child: AnimatedOpacity(
                            opacity: _hover.value
                                ? 1
                                : showPlayPauseOverlay.value
                                    ? 1
                                    : 0,
                            duration: const Duration(milliseconds: 100),
                            child: Material(
                              color: Colors.transparent,
                              child: InkWell(
                                borderRadius: BorderRadius.circular(40),
                                onTap: () async {
                                  Navigator.of(context).pop();
                                },
                                child: const Padding(
                                  padding: EdgeInsets.all(8.0),
                                  child: Icon(
                                    Icons.arrow_back,
                                    color: Colors.white,
                                    size: 25,
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                      );
                    }),
                  ),
                // Bottom action bar for iOS
                if (iOS && widget.showInteractions)
                  Positioned(
                    left: 0,
                    right: 0,
                    bottom: 0,
                    child: AnimatedOpacity(
                      opacity: showPlayPauseOverlay.value ? 1.0 : 0.0,
                      duration: const Duration(milliseconds: 200),
                      child: SafeArea(
                        top: false,
                        child: Container(
                          height: 60,
                          decoration: BoxDecoration(
                            color: samsung
                                ? Colors.black
                                : context.theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.9),
                          ),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                            children: [
                              IconButton(
                                icon: Icon(
                                  CupertinoIcons.cloud_download,
                                  color: samsung ? Colors.white : context.theme.colorScheme.primary,
                                ),
                                onPressed: () => AttachmentsSvc.saveToDisk(widget.file),
                              ),
                              IconButton(
                                icon: Icon(
                                  CupertinoIcons.info,
                                  color: samsung ? Colors.white : context.theme.colorScheme.primary,
                                ),
                                onPressed: () => showMetadataDialog(widget.attachment, context),
                              ),
                              IconButton(
                                icon: Icon(
                                  CupertinoIcons.refresh,
                                  color: samsung ? Colors.white : context.theme.colorScheme.primary,
                                ),
                                onPressed: () => refreshAttachment(),
                              ),
                              IconButton(
                                icon: Icon(
                                  muted.value ? CupertinoIcons.volume_mute : CupertinoIcons.volume_up,
                                  color: samsung ? Colors.white : context.theme.colorScheme.primary,
                                ),
                                onPressed: () async {
                                  muted.toggle();
                                  await videoController.player.setVolume(muted.value ? 0.0 : 100.0);
                                },
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
