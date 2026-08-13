import 'package:bluebubbles/app/layouts/conversation_list/pages/conversation_list.dart';
import 'package:bluebubbles/app/wrappers/stateful_boilerplate.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';

class ConversationListFAB extends CustomStateful<ConversationListController> {
  const ConversationListFAB({super.key, required super.parentController});

  @override
  State<StatefulWidget> createState() => _ConversationListFABState();
}

class _ConversationListFABState extends CustomState<ConversationListFAB, void, ConversationListController> {
  // Cache these values to prevent rebuild on every scroll event
  double _lastScrollOffset = 0;
  ScrollDirection _lastScrollDirection = ScrollDirection.idle;

  void _focusBackToList() {
    if (!FocusScope.of(context).focusInDirection(TraversalDirection.left)) {
      FocusScope.of(context).previousFocus();
    }
  }

  Map<ShortcutActivator, VoidCallback> get _newMessageShortcuts => {
        const SingleActivator(LogicalKeyboardKey.arrowLeft): _focusBackToList,
        const SingleActivator(LogicalKeyboardKey.enter): () => controller.openNewChatCreator(context),
        const SingleActivator(LogicalKeyboardKey.select): () => controller.openNewChatCreator(context),
        const SingleActivator(LogicalKeyboardKey.space): () => controller.openNewChatCreator(context),
      };

  @override
  void initState() {
    super.initState();

    // Optimize scroll listener to reduce setState calls
    controller.materialScrollController.addListener(_handleScroll);

    NavigationSvc.listener.stream.listen((event) {
      if (!mounted) return;
      if (NavigationSvc.isAvatarOnly(context) && controller.showMaterialFABText) {
        setState(() {
          controller.showMaterialFABText = false;
        });
      }
    });
  }

  void _handleScroll() {
    if (!material) return;

    final offset = controller.materialScrollController.offset;
    final direction = controller.materialScrollController.position.userScrollDirection;

    // Only process if direction actually changed (reduces unnecessary checks)
    if (direction == _lastScrollDirection && (offset - _lastScrollOffset).abs() < 75) {
      return;
    }

    _lastScrollOffset = offset;
    _lastScrollDirection = direction;

    final scrollDelta = controller.materialScrollStartPosition - offset;

    if (scrollDelta < -75 && direction == ScrollDirection.reverse && controller.showMaterialFABText) {
      setState(() {
        controller.showMaterialFABText = false;
      });
    } else if (scrollDelta > 75 && direction == ScrollDirection.forward && !controller.showMaterialFABText) {
      setState(() {
        controller.showMaterialFABText = true;
      });
    }
  }

  @override
  void dispose() {
    controller.materialScrollController.removeListener(_handleScroll);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ThemeSwitcher(
      iOSSkin: Obx(() {
        final isIOS = SettingsSvc.settings.skin.value == Skins.iOS;
        return Column(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            if (SettingsSvc.settings.cameraFAB.value && isIOS && !kIsWeb && !kIsDesktop)
              _CameraFAB(
                controller: controller,
                context: context,
                isIOS: isIOS,
              ),
            if (SettingsSvc.settings.cameraFAB.value && isIOS && !kIsWeb && !kIsDesktop) const SizedBox(height: 10),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                if (ChatsSvc.isEmpty && ChatsSvc.loadedFirstChatBatch.value)
                  Text("Start a Chat >", style: context.textTheme.labelLarge?.copyWith(color: Colors.white)),
                if (ChatsSvc.isEmpty) const SizedBox(width: 16),
                InkWell(
                  onLongPress: isIOS || !SettingsSvc.settings.cameraFAB.value || kIsWeb || kIsDesktop
                      ? null
                      : () => controller.openCamera(context),
                  child: CallbackShortcuts(
                    bindings: _newMessageShortcuts,
                    child: _FabFocusRing(
                      focusNode: controller.newMessageFocusNode,
                      child: FloatingActionButton(
                        focusNode: controller.newMessageFocusNode,
                        backgroundColor: context.theme.colorScheme.primary,
                        onPressed: () => controller.openNewChatCreator(context),
                        child: Icon(
                          isIOS ? CupertinoIcons.pencil : Icons.message,
                          color: context.theme.colorScheme.onPrimary,
                          size: 25,
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ],
        );
      }),
      materialSkin: _MaterialFAB(
        controller: controller,
        onScrollToTop: () async {
          await controller.materialScrollController.animateTo(
            0,
            duration: const Duration(milliseconds: 300),
            curve: Curves.easeOut,
          );
          setState(() {
            controller.showMaterialFABText = true;
          });
        },
      ),
      samsungSkin: Obx(() {
        final isIOS = SettingsSvc.settings.skin.value == Skins.iOS;
        return Column(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            if (SettingsSvc.settings.cameraFAB.value && isIOS && !kIsWeb && !kIsDesktop)
              _CameraFAB(
                controller: controller,
                context: context,
                isIOS: isIOS,
              ),
            if (SettingsSvc.settings.cameraFAB.value && isIOS && !kIsWeb && !kIsDesktop) const SizedBox(height: 10),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                if (ChatsSvc.isEmpty && ChatsSvc.loadedFirstChatBatch.value)
                  Text("Start a Chat >", style: context.textTheme.labelLarge?.copyWith(color: Colors.white)),
                if (ChatsSvc.isEmpty) const SizedBox(width: 16),
                InkWell(
                  onLongPress: isIOS || !SettingsSvc.settings.cameraFAB.value || kIsWeb || kIsDesktop
                      ? null
                      : () => controller.openCamera(context),
                  child: CallbackShortcuts(
                    bindings: _newMessageShortcuts,
                    child: _FabFocusRing(
                      focusNode: controller.newMessageFocusNode,
                      child: FloatingActionButton(
                        focusNode: controller.newMessageFocusNode,
                        backgroundColor: context.theme.colorScheme.primary,
                        onPressed: () => controller.openNewChatCreator(context),
                        child: Icon(
                          isIOS ? CupertinoIcons.pencil : Icons.message,
                          color: context.theme.colorScheme.onPrimary,
                          size: 25,
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ],
        );
      }),
    );
  }
}

/// Extracted camera FAB to isolate rebuild scope
class _CameraFAB extends StatelessWidget {
  const _CameraFAB({
    required this.controller,
    required this.context,
    required this.isIOS,
  });

  final ConversationListController controller;
  final BuildContext context;
  final bool isIOS;

  @override
  Widget build(BuildContext context) {
    return ConstrainedBox(
      constraints: const BoxConstraints(
        maxWidth: 45,
        maxHeight: 45,
      ),
      child: FloatingActionButton(
        heroTag: null,
        backgroundColor: context.theme.colorScheme.primaryContainer,
        onPressed: () => controller.openCamera(context),
        child: Icon(
          isIOS ? CupertinoIcons.camera : Icons.photo_camera,
          size: 20,
          color: context.theme.colorScheme.onPrimaryContainer,
        ),
      ),
    );
  }
}

/// Extracted Material FAB to prevent iOS/Samsung rebuilds
class _MaterialFAB extends StatelessWidget {
  const _MaterialFAB({
    required this.controller,
    required this.onScrollToTop,
  });

  final ConversationListController controller;
  final VoidCallback onScrollToTop;

  @override
  Widget build(BuildContext context) {
    return AnimatedCrossFade(
      crossFadeState: controller.selectedChats.isEmpty ? CrossFadeState.showFirst : CrossFadeState.showSecond,
      alignment: Alignment.center,
      duration: const Duration(milliseconds: 300),
      secondChild: const SizedBox.shrink(),
      firstChild: SizedBox(
        width: NavigationSvc.width(context),
        height: 125,
        child: Stack(
          alignment: Alignment.bottomCenter,
          clipBehavior: Clip.none,
          children: [
            // Scroll to top FAB
            _ScrollToTopFAB(
              controller: controller,
              onPressed: onScrollToTop,
            ),
            // Main compose FAB
            Positioned(
              right: SettingsSvc.settings.skin.value == Skins.Material ? 15 : 0,
              child: _MaterialComposeFAB(controller: controller),
            ),
          ],
        ),
      ),
    );
  }
}

/// Extracted scroll to top FAB to isolate animation
class _ScrollToTopFAB extends StatelessWidget {
  const _ScrollToTopFAB({
    required this.controller,
    required this.onPressed,
  });

  final ConversationListController controller;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return AnimatedOpacity(
      opacity: !controller.showMaterialFABText ? 1 : 0,
      duration: const Duration(milliseconds: 300),
      child: FloatingActionButton.small(
        heroTag: null,
        backgroundColor: context.theme.colorScheme.secondary,
        onPressed: onPressed,
        child: Icon(
          Icons.arrow_upward,
          color: context.theme.colorScheme.onSecondary,
        ),
      ),
    );
  }
}

/// Extracted Material compose FAB
class _MaterialComposeFAB extends StatelessWidget {
  const _MaterialComposeFAB({
    required this.controller,
  });

  final ConversationListController controller;

  @override
  Widget build(BuildContext context) {
    final isMonet = ThemeSvc.isAnyMaterialYouSelected;
    final bgColor = isMonet ? context.theme.colorScheme.primaryContainer : context.theme.colorScheme.primary;
    final fgColor = isMonet ? context.theme.colorScheme.onPrimaryContainer : context.theme.colorScheme.onPrimary;

    return InkWell(
      onLongPress:
          SettingsSvc.settings.cameraFAB.value && !kIsWeb && !kIsDesktop ? () => controller.openCamera(context) : null,
      child: Padding(
        padding: const EdgeInsets.only(right: 4.5, bottom: 9),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeInOut,
          height: 56,
          decoration: BoxDecoration(
            color: bgColor,
            borderRadius: BorderRadius.circular(16),
          ),
          child: Material(
            color: Colors.transparent,
            borderRadius: BorderRadius.circular(16),
            clipBehavior: Clip.antiAlias,
            child: InkWell(
              onTap: () => controller.openNewChatCreator(context),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(CupertinoIcons.bubble_left, color: fgColor, size: 24),
                    ClipRect(
                      child: AnimatedAlign(
                        duration: const Duration(milliseconds: 250),
                        curve: Curves.easeInOut,
                        alignment: Alignment.centerLeft,
                        widthFactor: controller.showMaterialFABText ? 1.0 : 0.0,
                        child: Padding(
                          padding: const EdgeInsets.only(left: 10),
                          child: Text(
                            'Start chat',
                            style: context.theme.textTheme.labelLarge!.copyWith(
                              color: fgColor,
                              fontWeight: FontWeight.w600,
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
        ),
      ),
    );
  }
}

/// Draws a prominent outline over the compose FAB while it holds D-pad/keyboard focus,
/// so the selected state is obvious instead of relying on the FAB's subtle default
/// highlight. Uses a foreground [DecoratedBox] so it paints over the button without
/// changing its size or position, and repaints via the focus node (no setState).
class _FabFocusRing extends StatelessWidget {
  const _FabFocusRing({required this.focusNode, required this.child});

  final FocusNode focusNode;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: focusNode,
      child: child,
      builder: (context, child) {
        return AnimatedScale(
          duration: const Duration(milliseconds: 150),
          // Scaling is a visual transform, so the button grows without shifting layout.
          scale: focusNode.hasFocus ? 1.2 : 1.0,
          alignment: Alignment.bottomRight,
          child: child,
        );
      },
    );
  }
}
