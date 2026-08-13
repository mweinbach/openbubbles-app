import 'dart:async';

import 'package:bluebubbles/app/components/avatars/contact_avatar_widget.dart';
import 'package:bluebubbles/app/wrappers/bb_app_bar.dart';
import 'package:bluebubbles/app/wrappers/bb_scaffold.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/database/database.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/utils/string_utils.dart';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:get/get.dart' hide Response;
import 'package:slugify/slugify.dart';

class HandleSelectorView extends StatefulWidget {
  const HandleSelectorView({
    super.key,
    required this.onSelect,
    this.forChat,
  });

  final void Function(Handle) onSelect;
  final Chat? forChat;

  @override
  HandleSelectorViewState createState() => HandleSelectorViewState();
}

class HandleSelectorViewState extends State<HandleSelectorView> with ThemeHelpers {
  final TextEditingController searchController = TextEditingController();
  final FocusNode searchNode = FocusNode();
  final ScrollController addressScrollController = ScrollController();

  Completer<void> loadedAllHandles = Completer<void>();
  List<Handle> handles = [];
  List<Handle> filteredHandles = [];
  String? oldSearch;
  Timer? _debounce;

  @override
  void initState() {
    super.initState();

    // Handle searching for a handle
    searchController.addListener(() {
      _debounce?.cancel();
      _debounce = Timer(const Duration(milliseconds: 250), () async {
        final searchHandles = await SchedulerBinding.instance.scheduleTask(() async {
          final query = slugify(searchController.text, delimiter: "");
          return handles
              .where((element) =>
                  slugify(element.displayName, delimiter: "").contains(query) || element.address.contains(query))
              .toList();
        }, Priority.animation);

        _debounce = null;
        setState(() {
          filteredHandles = List<Handle>.from(searchHandles);
        });
      });
    });

    if (loadedAllHandles.isCompleted) {
      if (mounted) {
        setState(() {
          filteredHandles = List<Handle>.from(handles);
        });
      }
    } else {
      loadedAllHandles.future.then((_) {
        if (mounted) {
          setState(() {
            filteredHandles = List<Handle>.from(handles);
          });
        }
      });
    }

    loadHandles();
  }

  Future<void> loadHandles() async {
    handles = Database.handles.getAll();

    // Sort alphabetically, prioritizing handles with contact associations
    handles.sort((a, b) {
      if (a.contactsV2.isNotEmpty && b.contactsV2.isEmpty) {
        return -1;
      } else if (a.contactsV2.isEmpty && b.contactsV2.isNotEmpty) {
        return 1;
      } else {
        return a.displayName.toLowerCase().compareTo(b.displayName.toLowerCase());
      }
    });

    // If there is a chat & participants, filter by participants
    if (widget.forChat != null && widget.forChat!.handles.isNotEmpty) {
      final addresses = widget.forChat!.handles.map((e) => e.address);
      handles = handles.where((element) => addresses.contains(element.address)).toList();
    }

    loadedAllHandles.complete();
  }

  @override
  Widget build(BuildContext context) {
    return BBScaffold(
      appBar: BBAppBar(
        titleText: "Select an Address",
        leading: buildBackButton(context),
        backgroundColor: Colors.transparent,
        toolbarHeight: kIsDesktop ? 90 : 50,
      ),
      body: FocusScope(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 10),
              child: TextField(
                controller: searchController,
                focusNode: searchNode,
                style: context.theme.textTheme.bodyLarge,
                decoration: InputDecoration(
                    hintText: "Search for an address...",
                    hintStyle: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.outline),
                    prefixIcon: Icon(
                      Icons.search,
                      color: context.theme.colorScheme.outline,
                    ),
                    suffixIcon: searchController.text.isNotEmpty
                        ? IconButton(
                            icon: const Icon(Icons.clear),
                            onPressed: () {
                              searchController.clear();
                            },
                          )
                        : null,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10),
                      borderSide: BorderSide.none,
                    ),
                    filled: false),
              ),
            ),
            Expanded(
              child: Obx(() {
                return Align(
                    alignment: Alignment.topCenter,
                    child: AnimatedSwitcher(
                        duration: const Duration(milliseconds: 150),
                        child: CustomScrollView(
                          shrinkWrap: true,
                          physics: ThemeSwitcher.getScrollPhysics(),
                          slivers: <Widget>[
                            SliverList(
                              delegate: SliverChildBuilderDelegate((context, index) {
                                if (filteredHandles.isEmpty) {
                                  return Column(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      Padding(
                                        padding: const EdgeInsets.all(8.0),
                                        child: Text(
                                          "Loading handles...",
                                          style: context.theme.textTheme.labelLarge,
                                        ),
                                      ),
                                      buildProgressIndicator(context, size: 15),
                                    ],
                                  );
                                }
                                final handle = filteredHandles[index];

                                return Obx(() {
                                  final hideInfo = SettingsSvc.settings.redactedMode.value &&
                                      SettingsSvc.settings.hideContactInfo.value;
                                  final handleState = HandleSvc.getOrCreateHandleState(handle);
                                  final _title = hideInfo
                                      ? handleState.fakeName
                                      : handleState.displayName.value ?? handle.displayName;

                                  return Material(
                                    color: Colors.transparent,
                                    child: InkWell(
                                        onTap: () {
                                          widget.onSelect(handle);
                                          Navigator.of(context).pop();
                                        },
                                        child: ListTile(
                                            mouseCursor: MouseCursor.defer,
                                            enableFeedback: true,
                                            dense: SettingsSvc.settings.denseChatTiles.value,
                                            minVerticalPadding: 10,
                                            horizontalTitleGap: 10,
                                            title: RichText(
                                              text: TextSpan(
                                                children: MessageHelper.buildEmojiText(
                                                  _title,
                                                  context.theme.textTheme.bodyLarge!,
                                                ),
                                              ),
                                              overflow: TextOverflow.ellipsis,
                                            ),
                                            subtitle: handle.address.isPhoneNumber
                                                ? Text(
                                                    formatPhoneNumber(cleansePhoneNumber(handle.address)),
                                                    style: context.theme.textTheme.bodySmall!
                                                        .copyWith(color: context.theme.colorScheme.outline),
                                                  )
                                                : Text(
                                                    handle.address,
                                                    style: context.theme.textTheme.bodySmall!
                                                        .copyWith(color: context.theme.colorScheme.outline),
                                                  ),
                                            leading: Padding(
                                              padding: const EdgeInsets.only(right: 5.0),
                                              child: ContactAvatarWidget(
                                                handle: handle,
                                                editable: false,
                                              ),
                                            ))),
                                  );
                                });
                              },
                                  childCount: filteredHandles.length
                                      .clamp(loadedAllHandles.isCompleted ? 0 : 1, double.infinity)
                                      .toInt()),
                            )
                          ],
                        )));
              }),
            ),
          ],
        ),
      ),
    );
  }
}
