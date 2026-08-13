import 'dart:async';
import 'dart:ui';

import 'package:bluebubbles/app/wrappers/bb_app_bar.dart';
import 'package:bluebubbles/app/wrappers/bb_scaffold.dart';
import 'package:bluebubbles/app/wrappers/scrollbar_wrapper.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:flutter/material.dart';
import 'package:flutter_acrylic/flutter_acrylic.dart';
import 'package:get/get.dart';

class LiveLoggingPanel extends StatefulWidget {
  const LiveLoggingPanel({super.key});

  @override
  State<StatefulWidget> createState() => _LiveLoggingPanel();
}

class _LiveLoggingPanel extends State<LiveLoggingPanel> {
  RxBool isPaused = false.obs;

  final List<String> _logs = [];
  final ScrollController scrollController = ScrollController();
  bool _shouldAutoScroll = true;
  StreamSubscription<String>? _logSubscription;

  @override
  void initState() {
    super.initState();
    Logger.enableLiveLogging();

    scrollController.addListener(() {
      // Check if the user has scrolled up.
      if (scrollController.position.atEdge) {
        _shouldAutoScroll = scrollController.position.pixels != 0;
      } else {
        _shouldAutoScroll = false;
      }
    });

    _logSubscription = Logger.logStream.stream.listen((event) {
      if (_shouldAutoScroll) {
        _scrollToBottom();
      }

      // Trim the logs list if it reaches out "max" which will be 500
      if (_logs.length > 500) {
        _logs.removeAt(0);
      }
    });
  }

  @override
  void dispose() {
    _logSubscription?.cancel();
    scrollController.dispose();
    Logger.disableLiveLogging();
    super.dispose();
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (scrollController.hasClients) {
        scrollController.jumpTo(scrollController.position.maxScrollExtent);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Obx(
      () => BBScaffold(
        appBar: PreferredSize(
          preferredSize: Size(NavigationSvc.width(context), kIsDesktop ? 80 : 50),
          child: ClipRRect(
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 15, sigmaY: 15),
              child: BBAppBar(
                titleText: "Live Logging",
                leading: buildBackButton(context),
                backgroundColor: SettingsSvc.settings.windowEffect.value != WindowEffect.disabled
                    ? Colors.transparent
                    : context.theme.colorScheme.surface,
                actions: [
                  // Menu button with 2 options, Play/Pause and Clear
                  PopupMenuButton(
                    icon: const Icon(Icons.more_vert),
                    itemBuilder: (context) => [
                      PopupMenuItem(
                        child: Obx(
                          () => ListTile(
                            title: Text(isPaused.value ? "Play" : "Pause"),
                            onTap: () {
                              isPaused.toggle();
                              if (isPaused.value) {
                                Logger.disableLiveLogging();
                              } else {
                                Logger.enableLiveLogging();
                              }
                            },
                          ),
                        ),
                      ),
                      PopupMenuItem(
                        child: ListTile(
                          title: const Text("Clear"),
                          onTap: () {
                            _logs.clear();
                            setState(() {});
                          },
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
        body: ScrollbarWrapper(
          showScrollbar: true,
          controller: scrollController,
          child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 10.0),
              child: StreamBuilder<String>(
                stream: Logger.logStream.stream,
                builder: (context, snapshot) {
                  if (snapshot.connectionState == ConnectionState.waiting) {
                    return const Center(child: Text('Waiting for logs...'));
                  } else if (snapshot.hasError) {
                    return Center(child: Text('Error: ${snapshot.error}'));
                  } else if (snapshot.hasData) {
                    _logs.add(snapshot.data!);
                  }

                  return ListView.separated(
                    itemCount: _logs.length,
                    shrinkWrap: true,
                    controller: scrollController,
                    separatorBuilder: (context, index) =>
                        Divider(thickness: 0.25, color: context.theme.colorScheme.onSurface),
                    itemBuilder: (context, index) {
                      Color textColor = Colors.black;
                      if (_logs[index].startsWith('[ERROR]')) {
                        textColor = Colors.red;
                      } else if (_logs[index].startsWith('[WARNING]')) {
                        textColor = Colors.orange;
                      } else if (_logs[index].startsWith('[TRACE]')) {
                        textColor = context.theme.colorScheme.primary;
                      } else if (_logs[index].startsWith('[FATAL]')) {
                        textColor = Colors.red;
                      } else if (_logs[index].startsWith('[DEBUG]')) {
                        textColor = context.theme.colorScheme.secondary;
                      }

                      return Text(
                        _logs[index].trim(),
                        style: TextStyle(fontSize: 12.0, color: textColor),
                      );
                    },
                  );
                },
              )),
        ),
      ),
    );
  }
}
