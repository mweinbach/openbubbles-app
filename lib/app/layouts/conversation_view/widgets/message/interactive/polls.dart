import 'dart:convert';

import 'package:bluebubbles/app/animations/balloon_rendering.dart';
import 'package:bluebubbles/app/components/avatars/contact_avatar_group_widget.dart';
import 'package:bluebubbles/app/components/custom_text_editing_controllers.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/effects/send_effect_picker.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/network/backend_service.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/ui/message/message_widget_controller.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:bluebubbles/helpers/types/constants.dart' as constants;
import 'package:bluebubbles/helpers/ui/theme_helpers.dart';
import 'package:collection/collection.dart';
import 'package:bluebubbles/app/state/message_state.dart';

PollMessage pollMessageFromJson(String str) => PollMessage.fromJson(json.decode(str));

String pollMessageToJson(PollMessage data) => json.encode(data.toJson());

class PollMessage {
  Item? item;
  int? version;

  PollMessage({
    this.item,
    this.version,
  });

  factory PollMessage.fromJson(Map<String, dynamic> json) => PollMessage(
        item: json["item"] == null ? null : Item.fromJson(json["item"]),
        version: json["version"],
      );

  Map<String, dynamic> toJson() => {
        "item": item?.toJson(),
        "version": version,
      };
}

class Item {
  List<OrderedPollOption>? orderedPollOptions;
  List<Vote>? votes;
  String? creatorHandle;
  String? title;

  Item({
    this.orderedPollOptions,
    this.votes,
    this.creatorHandle,
    this.title,
  });

  factory Item.fromJson(Map<String, dynamic> json) => Item(
        orderedPollOptions: json["orderedPollOptions"] == null
            ? []
            : List<OrderedPollOption>.from(json["orderedPollOptions"]!.map((x) => OrderedPollOption.fromJson(x))),
        votes: json["votes"] == null ? [] : List<Vote>.from(json["votes"]!.map((x) => Vote.fromJson(x))),
        creatorHandle: json["creatorHandle"],
        title: json["title"],
      );

  Map<String, dynamic> toJson() => {
        "orderedPollOptions":
            orderedPollOptions == null ? [] : List<dynamic>.from(orderedPollOptions!.map((x) => x.toJson())),
        "votes": votes == null ? [] : List<dynamic>.from(votes!.map((x) => x.toJson())),
        "creatorHandle": creatorHandle,
        "title": title,
      };
}

class OrderedPollOption {
  String? attributedText;
  bool? canBeEdited;
  String? creatorHandle;
  String? optionIdentifier;
  String? text;

  OrderedPollOption({
    this.attributedText,
    this.canBeEdited,
    this.creatorHandle,
    this.optionIdentifier,
    this.text,
  });

  factory OrderedPollOption.fromJson(Map<String, dynamic> json) => OrderedPollOption(
        attributedText: json["attributedText"],
        canBeEdited: json["canBeEdited"],
        creatorHandle: json["creatorHandle"],
        optionIdentifier: json["optionIdentifier"],
        text: json["text"],
      );

  Map<String, dynamic> toJson() => {
        "attributedText": attributedText,
        "canBeEdited": canBeEdited,
        "creatorHandle": creatorHandle,
        "optionIdentifier": optionIdentifier,
        "text": text,
      };
}

class Vote {
  String? voteOptionIdentifier;
  String? participantHandle;

  Vote({
    this.voteOptionIdentifier,
    this.participantHandle,
  });

  factory Vote.fromJson(Map<String, dynamic> json) => Vote(
        voteOptionIdentifier: json["voteOptionIdentifier"],
        participantHandle: json["participantHandle"],
      );

  Map<String, dynamic> toJson() => {
        "voteOptionIdentifier": voteOptionIdentifier,
        "participantHandle": participantHandle,
      };
}

class Polls extends StatefulWidget {
  final iMessageAppData data;
  final MessageState? messageState;
  Message? get message => messageState?.message;

  Polls({
    super.key,
    required this.data,
    required this.messageState,
  });

  @override
  State<Polls> createState() => _PollsState();
}

class _PollsState extends State<Polls> with AutomaticKeepAliveClientMixin<Polls>, ThemeHelpers {
  iMessageAppData get data => widget.data;

  Handle? myHandle;

  @override
  void initState() {
    super.initState();
    (() async {
      if (widget.message == null) return;
      var handle = RustPushBBUtils.rustHandleToBB(await widget.message!.chat.target!.ensureHandle());
      setState(() {
        myHandle = handle;
      });
    })();
  }

  // @override
  // void didUpdateWidget(Polls oldWidget) {
  //   super.didUpdateWidget(oldWidget);
  //   if (widget.message == null) return;
  //   getActiveMwc(widget.message!.guid!) ?? mwc(widget.message!);
  // }

  @override
  bool get wantKeepAlive => true;

  PollMessage decodePollMessage(String url) {
    var d = Uri.parse(url);
    var u = utf8.decode(base64Decode(d.path.substring(1)));
    print(u);
    return pollMessageFromJson(u);
  }

  void addOption(String option) async {
    var pollMessage = decodePollMessage(data.url!);
    var count = int.parse(Uri.parse(data.url!).queryParameters["c"]!) + 1;
    var item = pollMessage.item!;
    item.orderedPollOptions!.add(OrderedPollOption(
      attributedText: option,
      canBeEdited: false,
      creatorHandle: myHandle!.address,
      optionIdentifier: uuid.v4().toUpperCase(),
      text: option,
    ));
    if (widget.message == null) return;
    var message = await BackendSvc.updateMessage(
        widget.message!.chat.target!,
        widget.message!,
        PayloadData(type: constants.PayloadType.app, appData: [
          iMessageAppData(
            appName: "Polls",
            url: "data:,${base64Encode(utf8.encode(pollMessageToJson(pollMessage)))}?src=p&c=$count",
            session: widget.message!.amkSessionId,
            ldText: item.orderedPollOptions!.map((i) => i.text!).join(", "),
            userInfo: UserInfo(
              imageSubtitle: "",
              imageTitle: "",
              caption: "ENG: Update your device",
              secondarySubcaption: "",
              tertiarySubcaption: "",
              subcaption: "Learn about how to update your device to see this content.",
            ),
            isLive: true,
            appIcon: base64Encode((await rootBundle.load("assets/images/polls.jpg")).buffer.asUint8List()),
          )
        ]),
        null,
        false,
        null);
    await IncomingMsgHandler.handle(IncomingPayload(
      type: MessageEventType.newMessage,
      source: MessageSource.apiResponse,
      chat: widget.message!.chat.target!,
      message: message,
    ));
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return Obx(() {
      var pollMessage = decodePollMessage(data.url!).item!;
      Map<String, Message> latestMessages = {};

      if (widget.messageState != null) {
        for (var message in widget.messageState!.associatedMessages) {
          if (latestMessages[message.getHandle()!.address]?.dateCreated?.isBefore(message.dateCreated!) == false) {
            continue;
          }
          latestMessages[message.getHandle()!.address] = message;
        }
      }

      Map<String, List<Handle>> voters = {};

      for (var option in pollMessage.orderedPollOptions!) {
        voters[option.optionIdentifier!] = [];
      }

      List<String> myVotes = [];
      for (var result in latestMessages.values) {
        for (var vote in decodePollMessage(result.payloadData!.appData!.first.url!).item!.votes!) {
          voters[vote.voteOptionIdentifier!]!.add(result.getHandle()!);
          if (result.getHandle()!.address == myHandle?.address) {
            myVotes.add(vote.voteOptionIdentifier!);
          }
        }
      }

      var maxVoteCount = voters.values.map((i) => i.length).max;

      return Padding(
        padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          ...pollMessage.orderedPollOptions!.map((option) => Padding(
                padding: const EdgeInsets.symmetric(vertical: 3),
                child: FractionallySizedBox(
                  alignment: Alignment.centerLeft,
                  widthFactor:
                      (maxVoteCount == 0 ? 1 : voters[option.optionIdentifier!]!.length / maxVoteCount.toDouble()) *
                              0.6 +
                          0.4,
                  child: Material(
                    color: Colors.transparent,
                    borderRadius: BorderRadius.circular(50),
                    child: InkWell(
                      borderRadius: BorderRadius.circular(50),
                      onTap: () async {
                        if (widget.message == null) return;
                        var myVotes = voters.entries
                            .where((e) => e.value.any((h) => h.address == myHandle!.address))
                            .map((i) => i.key)
                            .toList();
                        if (myVotes.contains(option.optionIdentifier!)) {
                          myVotes.remove(option.optionIdentifier!);
                        } else {
                          myVotes.add(option.optionIdentifier!);
                        }
                        var message = await BackendSvc.updateMessage(
                            widget.message!.chat.target!,
                            widget.message!,
                            PayloadData(type: constants.PayloadType.app, appData: [
                              iMessageAppData(
                                appName: "Polls",
                                url: "data:,${base64Encode(utf8.encode(pollMessageToJson(PollMessage(
                                  item: Item(
                                    votes: myVotes
                                        .map((vote) =>
                                            Vote(voteOptionIdentifier: vote, participantHandle: myHandle!.address))
                                        .toList(),
                                  ),
                                  version: 1,
                                ))))}",
                                session: widget.message!.guid,
                              )
                            ]),
                            null,
                            true,
                            "1 Polls Message");
                        await IncomingMsgHandler.handle(IncomingPayload(
                          type: MessageEventType.newMessage,
                          source: MessageSource.apiResponse,
                          chat: widget.message!.chat.target!,
                          message: message,
                        ));
                      },
                      child: Container(
                        child: Row(
                          children: [
                            Expanded(
                              child: Text(
                                option.text!,
                                overflow: TextOverflow.ellipsis,
                                style: context.theme.textTheme.bodyMedium!.copyWith(
                                    fontWeight: FontWeight.w600,
                                    color: myVotes.contains(option.optionIdentifier!)
                                        ? context.theme.colorScheme.onPrimary
                                        : null),
                              ),
                            ),
                            if (voters[option.optionIdentifier!]!.isNotEmpty)
                              ContactAvatarGroupWidget(
                                participants: voters[option.optionIdentifier!]!,
                                size: 30,
                                key: UniqueKey(),
                              ),
                          ],
                        ),
                        height: 40,
                        padding: const EdgeInsets.fromLTRB(10, 5, 5, 5),
                        decoration: BoxDecoration(
                            border: Border.all(
                              color: context.theme.colorScheme.primary,
                              width: 2,
                            ),
                            borderRadius: BorderRadius.circular(50),
                            color:
                                myVotes.contains(option.optionIdentifier!) ? context.theme.colorScheme.primary : null),
                      ),
                    ),
                  ),
                ),
              )),
          if (widget.message != null)
            GestureDetector(
              onTap: () {
                final TextEditingController participantController = TextEditingController();
                showDialog(
                    context: context,
                    builder: (_) {
                      return AlertDialog(
                        actions: [
                          TextButton(
                            child: Text("Cancel",
                                style: context.theme.textTheme.bodyLarge!
                                    .copyWith(color: context.theme.colorScheme.primary)),
                            onPressed: () => Navigator.of(context, rootNavigator: true).pop(),
                          ),
                          TextButton(
                            child: Text("OK",
                                style: context.theme.textTheme.bodyLarge!
                                    .copyWith(color: context.theme.colorScheme.primary)),
                            onPressed: () async {
                              addOption(participantController.text);
                              Navigator.of(context, rootNavigator: true).pop();
                            },
                          ),
                        ],
                        content: TextField(
                          controller: participantController,
                          decoration: const InputDecoration(
                            labelText: "Text",
                            border: OutlineInputBorder(),
                          ),
                          autofocus: true,
                          onSubmitted: (String value) {
                            addOption(participantController.text);
                            Navigator.of(context, rootNavigator: true).pop();
                          },
                        ),
                        title: Text("New Option", style: context.theme.textTheme.titleLarge),
                        backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                      );
                    });
              },
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                child: Text(
                  "Add Choice",
                  style: context.theme.textTheme.labelMedium?.copyWith(color: context.theme.colorScheme.primary),
                ),
              ),
            ),
        ]),
      );
    });
  }
}
