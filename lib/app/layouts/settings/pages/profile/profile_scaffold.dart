import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'dart:ui';

import 'package:bluebubbles/app/components/avatars/contact_avatar_widget.dart';
import 'package:bluebubbles/app/layouts/chat_creator/chat_creator.dart';
import 'package:bluebubbles/app/layouts/settings/pages/profile/poster_edit.dart';
import 'package:bluebubbles/app/layouts/settings/pages/profile/posterkit.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:bluebubbles/database/database.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/app/layouts/conversation_list/pages/conversation_list.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/tile/conversation_tile.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/tile/pinned_conversation_tile.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/conversation_list_fab.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/header/cupertino_header.dart';
import 'package:bluebubbles/app/wrappers/stateful_boilerplate.dart';
import 'package:bluebubbles/app/wrappers/scrollbar_wrapper.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_acrylic/flutter_acrylic.dart';
import 'package:get/get.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:palette_generator/palette_generator.dart';
import 'package:smooth_page_indicator/smooth_page_indicator.dart';
import 'package:flutter/material.dart';
import 'dart:ui' as ui;
import 'dart:math' as math;

class ProfileScaffold extends StatefulWidget {
  final List<Widget> bodySlivers;
  final Handle? handle;
  final void Function()? posterEdited;
  final List<Widget> actions;
  final Widget? chatOptions;
  const ProfileScaffold(
      {Key? key,
      required this.bodySlivers,
      required this.handle,
      this.posterEdited,
      this.actions = const [],
      this.chatOptions});

  @override
  State<StatefulWidget> createState() => ProfileScaffoldState();
}

class ProfileScaffoldState extends State<ProfileScaffold> with ThemeHelpers {
  final ScrollController iosScrollController = ScrollController();

  api.SimplifiedIncomingCallPoster? poster;
  Map<String, ui.Image>? images;

  FragmentShader? shader;

  Color profileBackground = Colors.transparent;

  Color get backgroundColor => SettingsSvc.settings.windowEffect.value == WindowEffect.disabled
      ? context.theme.colorScheme.background
      : Colors.transparent;

  bool loaded = false;

  bool track = false;
  bool appBarVisible = false;
  bool _shouldDisplay = false;

  StreamSubscription<String?>? registration;

  @override
  void dispose() {
    super.dispose();
    registration?.cancel();
  }

  @override
  void initState() {
    super.initState();
    // update widget when background color changes
    if (kIsDesktop) {
      SettingsSvc.settings.windowEffect.listen((WindowEffect effect) {
        setState(() {});
      });
    }
    registration = SettingsSvc.settings.userAvatarPath.listen((event) {
      updatePoster();
      setState(() {});
    });
    iosScrollController.addListener(() {
      var meets = poster != null ? iosScrollController.offset > 500 : iosScrollController.offset > 200;
      if (meets != track) {
        track = meets;
        if (meets) {
          setState(() {
            _shouldDisplay = true;
          });
          WidgetsBinding.instance.addPostFrameCallback((_) {
            setState(() {
              appBarVisible = true; // Fade in
            });
          });
        } else {
          setState(() {
            appBarVisible = false;
          });
        }
      }
    });
    (() async {
      updatePoster();

      var program = await FragmentProgram.fromAsset('shaders/vertical_blur_gradient.frag');
      shader = program.fragmentShader();
    })();
  }

  String? get posterPath =>
      widget.handle != null ? widget.handle!.getPoster() : SettingsSvc.settings.userPosterPath.value;
  bool get hasAvatar =>
      (SettingsSvc.settings.userAvatarPath.value != null && widget.handle == null) ||
      (widget.handle != null && widget.handle!.contactsV2.firstOrNull?.avatarPath != null);

  void updatePoster() async {
    if (posterPath == null || kIsDesktop) {
      if (hasAvatar) {
        final provider = widget.handle == null
            ? FileImage(File(SettingsSvc.settings.userAvatarPath.value!)) as ImageProvider<Object>
            : FileImage(File(widget.handle!.contactsV2.firstOrNull!.avatarPath!)) as ImageProvider<Object>;
        var palette = await PaletteGenerator.fromImageProvider(provider);
        profileBackground = darken(palette.dominantColor?.color ?? Colors.transparent, 0.2);
      } else {
        profileBackground = Get.context!.theme.colorScheme.outline;
      }
      setState(() {
        loaded = true;
      });
      return;
    }
    var data = await File("${posterPath!}.jpg").readAsBytes();
    print("Parsing file");
    poster = await api.fromPosterSave(poster: data);
    images = await loadPosterImages(posterPath!, poster!.poster);
    setState(() {
      loaded = true;
    });
  }

  @override
  Widget build(BuildContext context) {
    var wasVisible = appBarVisible;
    return Scaffold(
      backgroundColor: SettingsSvc.settings.windowEffect.value != WindowEffect.disabled
          ? Colors.transparent
          : context.theme.colorScheme.background,
      extendBodyBehindAppBar: true,
      appBar: PreferredSize(
          preferredSize: Size(NavigationSvc.width(context), 50),
          child: !_shouldDisplay
              ? const SizedBox.shrink()
              : AnimatedOpacity(
                  opacity: wasVisible ? 1 : 0,
                  duration: const Duration(milliseconds: 200),
                  onEnd: () {
                    if (!wasVisible && !appBarVisible) {
                      setState(() {
                        _shouldDisplay = false; // Remove widget after fade out
                      });
                    }
                  },
                  child: AppBar(
                    systemOverlayStyle: context.theme.colorScheme.brightness == Brightness.dark
                        ? SystemUiOverlayStyle.light
                        : SystemUiOverlayStyle.dark,
                    toolbarHeight: 50,
                    elevation: 0,
                    scrolledUnderElevation: 3,
                    surfaceTintColor: context.theme.colorScheme.primary,
                    leading: buildBackButton(context),
                    backgroundColor: headerColor,
                    centerTitle: SettingsSvc.settings.skin.value == Skins.iOS,
                    title: Text(
                      widget.handle?.displayName ?? "Profile",
                      style: context.theme.textTheme.titleLarge,
                    ),
                    actions: widget.actions,
                  ),
                )),
      body: Stack(
        children: [
          ScrollbarWrapper(
            showScrollbar: true,
            controller: iosScrollController,
            child: CustomScrollView(
              controller: iosScrollController,
              physics: ThemeSvc.scrollPhysics,
              slivers: <Widget>[
                SliverToBoxAdapter(
                  child: Stack(children: [
                    if (poster != null && images != null && loaded)
                      ConstrainedBox(
                        constraints: const BoxConstraints(
                          maxHeight: 600, // Limit height
                          minWidth: double.infinity, // Fill parent width
                        ),
                        child: ClipRect(
                          child: ImagePoster(
                            poster: poster!.poster,
                            images: images!,
                          ),
                        ),
                      ),
                    if (poster == null || images == null)
                      Container(
                        color: profileBackground,
                        child: ConstrainedBox(
                            constraints: const BoxConstraints(
                              maxHeight: 600, // Limit height
                              minWidth: double.infinity, // Fill parent width
                            ),
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: loaded
                                  ? [
                                      SizedBox(
                                        height: MediaQuery.of(context).viewPadding.top + 50,
                                      ),
                                      ContactAvatarWidget(
                                        handle: widget.handle,
                                        borderThickness: 0.1,
                                        editable: false,
                                        fontSize: 44,
                                        size: hasAvatar ? 300 : 100,
                                      ),
                                      SizedBox(
                                        height: widget.actions.isNotEmpty ? 150 : 100,
                                      ),
                                    ]
                                  : [
                                      const SizedBox(
                                        height: 600,
                                      )
                                    ],
                            )),
                      ),
                    if (!kIsDesktop)
                      Positioned(
                        right: 10,
                        top: kIsDesktop ? 30 : MediaQuery.of(context).viewPadding.top,
                        child: ElevatedButton(
                          style: ElevatedButton.styleFrom(
                            backgroundColor: context.theme.colorScheme.outline.withAlpha(96),
                            padding: const EdgeInsets.symmetric(vertical: 5, horizontal: 13),
                            tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                            elevation: 0.0,
                            minimumSize: Size.zero,
                          ),
                          onPressed: () async {
                            api.SimplifiedIncomingCallPoster usePoster;
                            if (poster != null) {
                              usePoster = api.clonePoster(poster: poster!);
                            } else {
                              var randomColor = Color((math.Random().nextDouble() * 0xFFFFFF).toInt()).withOpacity(1.0);
                              var initials = widget.handle != null
                                  ? widget.handle!.initials ?? 'A'
                                  : "${SettingsSvc.settings.firstName.value?.substring(0, 1) ?? 'A'}${SettingsSvc.settings.lastName.value?.substring(0, 1) ?? ''}"
                                      .toUpperCase();
                              usePoster = api.SimplifiedIncomingCallPoster(
                                textMetadata: api.WallpaperMetadata(
                                  fontColorKey: const api.PosterColor(alpha: 0.5, blue: 1, green: 1, red: 1),
                                  fontNameKey: ".SFUI-Regular_wdth_opsz110000_GRAD_wght1900000",
                                  fontSizeKey: 12,
                                  fontWeightKey: 400,
                                  isVerticalKey: false,
                                  typeKey: "com.apple.ContactsUI.MonogramPosterExtension",
                                ),
                                poster: createNewPoster(
                                    api.PosterType.monogram(
                                      data: api.MonogramData(
                                          topBackgroundColorDescription: colorToPosterColor(randomColor),
                                          backgroundColorDescription: colorToPosterColor(randomColor),
                                          initials: initials,
                                          monogramSupportedForName: true),
                                      background: colorToPosterColor(randomColor),
                                    ),
                                    randomColor,
                                    api.PosterRole.prPosterRoleIncomingCall),
                                lowRes: Uint8List(0),
                              );
                            }
                            var activePath = widget.handle != null
                                ? widget.handle!.getPoster()
                                : SettingsSvc.settings.userPosterPath.value;
                            Navigator.of(context).push(
                              ThemeSwitcher.buildPageRoute(
                                builder: (context) => PosterEdit(
                                  poster: usePoster,
                                  handle: widget.handle,
                                  activePath: activePath,
                                  posterEdited: (newPath) async {
                                    if (activePath != newPath && activePath != null) {
                                      await PushSvc.deletePoster(activePath);
                                    }

                                    if (widget.handle != null) {
                                      widget.handle!.setPoster(newPath);
                                    } else {
                                      SettingsSvc.settings.userPosterPath.value = newPath;
                                      await SettingsSvc.settings.saveOneAsync('userPosterPath');
                                    }
                                    updatePoster();
                                    (widget.posterEdited ?? () {})();
                                  },
                                ),
                              ),
                            );
                          },
                          child: Text("Edit", style: context.theme.textTheme.bodyLarge?.copyWith(color: Colors.white)),
                        ),
                      ),
                    Positioned(
                      left: 10,
                      top: kIsDesktop ? 30 : MediaQuery.of(context).viewPadding.top,
                      child: ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: context.theme.colorScheme.outline.withAlpha(96),
                          padding: const EdgeInsets.symmetric(vertical: 5, horizontal: 5),
                          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                          elevation: 0.0,
                          minimumSize: Size.zero,
                        ),
                        onPressed: () async {
                          Navigator.of(context).pop();
                        },
                        child: const Icon(CupertinoIcons.back, color: Colors.white),
                      ),
                    ),
                    if (loaded)
                      Positioned(
                          bottom: 0,
                          left: 0,
                          right: 0,
                          child: ClipRect(
                            child: BackdropFilter(
                              filter: ImageFilter.blur(
                                  sigmaX: poster == null ? 1 : 10,
                                  sigmaY: poster == null ? 1 : 10,
                                  tileMode: TileMode.mirror),
                              child: Column(
                                children: [
                                  const SizedBox(
                                    height: 10,
                                  ),
                                  GestureDetector(
                                    child: Padding(
                                      padding: const EdgeInsets.symmetric(horizontal: 10),
                                      child: Center(
                                          child: poster != null
                                              ? posterText(poster!.poster, 40,
                                                  widget.handle?.displayName ?? SettingsSvc.settings.userName.value,
                                                  color: false)
                                              : Text(
                                                  widget.handle?.displayName ?? SettingsSvc.settings.userName.value,
                                                  textAlign: TextAlign.center,
                                                  style: context.theme.textTheme.displaySmall?.copyWith(
                                                    fontSize: 40,
                                                    color: Colors.white,
                                                    height: 1.3,
                                                  ),
                                                  overflow: TextOverflow.ellipsis,
                                                  softWrap: false,
                                                )),
                                    ),
                                    onTap: () {
                                      Clipboard.setData(ClipboardData(
                                          text: widget.handle?.displayName ?? SettingsSvc.settings.userName.value));
                                    },
                                  ),
                                  if (widget.chatOptions != null) widget.chatOptions!,
                                  const SizedBox(
                                    height: 10,
                                  ),
                                ],
                              ),
                            ),
                          )),
                  ]),
                ),
                ...widget.bodySlivers,
              ],
            ),
          ),
        ],
      ),
    );
  }
}
