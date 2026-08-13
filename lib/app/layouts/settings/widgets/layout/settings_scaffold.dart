import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/app/wrappers/bb_app_bar.dart';
import 'package:bluebubbles/app/wrappers/bb_scaffold.dart';
import 'package:bluebubbles/app/wrappers/scrollbar_wrapper.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class SettingsScaffold extends StatelessWidget {
  final ScrollController controller = ScrollController();
  final String title;
  final String? initialHeader;
  final TextStyle? iosSubtitle;
  final TextStyle? materialSubtitle;
  final Color headerColor;
  final Color tileColor;
  final List<Widget> bodySlivers;
  final List<Widget> actions;
  final Widget? stickyPrefix;
  final Widget? stickySuffix;
  final Widget? fab;
  final Widget? leading;

  SettingsScaffold({
    super.key,
    required this.title,
    required this.initialHeader,
    required this.iosSubtitle,
    required this.materialSubtitle,
    required this.headerColor,
    required this.tileColor,
    required this.bodySlivers,
    this.actions = const [],
    this.stickyPrefix,
    this.stickySuffix,
    this.fab,
    this.leading,
  });

  bool get extend => actions.isNotEmpty && kIsDesktop;

  @override
  Widget build(BuildContext context) {
    final scaffoldSw = Stopwatch()..start();
    WidgetsBinding.instance.addPostFrameCallback((_) {});

    final widgetTree = BBScaffold(
      backgroundColor: SettingsSvc.settings.skin.value == Skins.Material ? tileColor : headerColor,
      appBar: SettingsSvc.settings.skin.value == Skins.Samsung
          ? null
          : BBAppBar(
              titleText: title,
              leading: leading ?? buildBackButton(context),
              backgroundColor: headerColor,
              toolbarHeight: extend ? 80 : 50,
              actions: actions,
            ),
      floatingActionButton: fab,
      extendBodyBehindAppBar: false,
      body: NotificationListener<ScrollEndNotification>(
        onNotification: (_) {
          if (SettingsSvc.settings.skin.value != Skins.Samsung || kIsWeb || kIsDesktop) return false;
          final scrollDistance = context.height / 3 - 57;
          if (controller.offset > 0 &&
              controller.offset < scrollDistance &&
              controller.offset != controller.position.maxScrollExtent) {
            final double snapOffset = controller.offset / scrollDistance > 0.5 ? scrollDistance : 0;

            Future.microtask(() =>
                controller.animateTo(snapOffset, duration: const Duration(milliseconds: 200), curve: Curves.linear));
          }
          return false;
        },
        child: ScrollbarWrapper(
          showScrollbar: kIsDesktop || kIsWeb,
          controller: controller,
          child: Column(
            children: [
              stickyPrefix ?? const SizedBox.shrink(),
              Expanded(
                child: Obx(
                  () {
                    final listSw = Stopwatch()..start();
                    final view = CustomScrollView(
                      controller: controller,
                      shrinkWrap: true,
                      physics: ThemeSwitcher.getScrollPhysics(),
                      slivers: <Widget>[
                        if (SettingsSvc.settings.skin.value == Skins.Samsung)
                          SliverAppBar(
                            backgroundColor: headerColor,
                            pinned: true,
                            stretch: true,
                            expandedHeight: context.height / 3,
                            elevation: 0,
                            automaticallyImplyLeading: false,
                            flexibleSpace: LayoutBuilder(
                              builder: (context, _) {
                                var expandRatio = 1 - (controller.offset) / (context.height / 3 - 50);
                                if (expandRatio > 1.0) expandRatio = 1.0;
                                if (expandRatio < 0.1) expandRatio = 0.0;
                                final animation = AlwaysStoppedAnimation<double>(expandRatio);

                                return Stack(
                                  fit: StackFit.expand,
                                  children: [
                                    FadeTransition(
                                      opacity: Tween(begin: 0.0, end: 1.0).animate(CurvedAnimation(
                                        parent: animation,
                                        curve: const Interval(0.3, 1.0, curve: Curves.easeIn),
                                      )),
                                      child: Center(
                                          child: Text(title,
                                              style: context.theme.textTheme.displaySmall!
                                                  .copyWith(color: context.theme.colorScheme.onSurface),
                                              textAlign: TextAlign.center)),
                                    ),
                                    FadeTransition(
                                      opacity: Tween(begin: 1.0, end: 0.0).animate(CurvedAnimation(
                                        parent: animation,
                                        curve: const Interval(0.0, 0.7, curve: Curves.easeOut),
                                      )),
                                      child: Align(
                                        alignment: Alignment.bottomLeft,
                                        child: Container(
                                          padding: const EdgeInsets.only(left: 50),
                                          height: 50,
                                          child: Align(
                                            alignment: Alignment.centerLeft,
                                            child: Text(
                                              title,
                                              style: context.theme.textTheme.titleLarge,
                                            ),
                                          ),
                                        ),
                                      ),
                                    ),
                                    Padding(
                                      padding: const EdgeInsets.only(left: 8.0),
                                      child: Align(
                                        alignment: Alignment.bottomLeft,
                                        child: SizedBox(
                                          height: 50,
                                          child: Align(
                                            alignment: Alignment.centerLeft,
                                            child: leading ?? buildBackButton(context),
                                          ),
                                        ),
                                      ),
                                    ),
                                    Align(
                                      alignment: Alignment.bottomRight,
                                      child: SizedBox(
                                        height: 50,
                                        child: Align(
                                          alignment: Alignment.centerRight,
                                          child: Row(
                                            mainAxisSize: MainAxisSize.min,
                                            children: actions,
                                          ),
                                        ),
                                      ),
                                    ),
                                  ],
                                );
                              },
                            ),
                          ),
                        if (SettingsSvc.settings.skin.value != Skins.Samsung && initialHeader != null)
                          SliverToBoxAdapter(
                            child: Container(
                                height: 50,
                                alignment: Alignment.bottomLeft,
                                color: SettingsSvc.settings.skin.value == Skins.iOS ? headerColor : tileColor,
                                child: Padding(
                                  padding: EdgeInsets.only(
                                      bottom: 8.0, left: SettingsSvc.settings.skin.value == Skins.iOS ? 30 : 15),
                                  child: Text(initialHeader!.psCapitalize,
                                      style: SettingsSvc.settings.skin.value == Skins.iOS
                                          ? iosSubtitle
                                          : materialSubtitle),
                                )),
                          ),
                        if (SettingsSvc.settings.skin.value != Skins.Samsung) ...bodySlivers,
                        if (SettingsSvc.settings.skin.value == Skins.Samsung)
                          SliverToBoxAdapter(
                            child: ConstrainedBox(
                              constraints: BoxConstraints(
                                  minHeight: context.height -
                                      50 -
                                      context.mediaQueryPadding.top -
                                      context.mediaQueryViewPadding.top),
                              child: CustomScrollView(
                                physics: const NeverScrollableScrollPhysics(),
                                shrinkWrap: true,
                                slivers: bodySlivers,
                              ),
                            ),
                          ),
                        SliverToBoxAdapter(
                          child: Container(
                            height: 30,
                          ),
                        ),
                      ],
                    );
                    listSw.stop();
                    return view;
                  },
                ),
              ),
              stickySuffix ?? const SizedBox.shrink(),
            ],
          ),
        ),
      ),
    );
    scaffoldSw.stop();
    return widgetTree;
  }
}
