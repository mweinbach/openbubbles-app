import 'dart:async';

import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/models/models.dart';

/// Class to replace objectbox DB listener functionality with an old-fashioned
/// stream based listener
class WebListeners {
  static final Set<String> _messageGuids = {};
  static final Set<String> _chatGuids = {};

  static final StreamController<MessageUpdateEvent> _messageUpdate = StreamController.broadcast();
  static final StreamController<NewMessageEvent> _newMessage = StreamController.broadcast();

  static final StreamController<Chat> _chatUpdate = StreamController.broadcast();
  static final StreamController<Chat> _newChat = StreamController.broadcast();

  static Stream<MessageUpdateEvent> get messageUpdate => _messageUpdate.stream;
  static Stream<NewMessageEvent> get newMessage => _newMessage.stream;

  static Stream<Chat> get chatUpdate => _chatUpdate.stream;
  static Stream<Chat> get newChat => _newChat.stream;

  static void notifyMessage(Message m, {Chat? chat, String? tempGuid}) {
    if (tempGuid != null) {
      if (_messageGuids.contains(tempGuid)) {
        _messageGuids.add(m.guid!);
        _messageUpdate.add(MessageUpdateEvent(m, tempGuid, chat));
      } else {
        _messageGuids.add(tempGuid);
        _newMessage.add(NewMessageEvent(m, chat));
      }
    } else {
      if (_messageGuids.contains(m.guid)) {
        _messageUpdate.add(MessageUpdateEvent(m, null, chat));
      } else {
        _messageGuids.add(m.guid!);
        _newMessage.add(NewMessageEvent(m, chat));
      }
    }
  }

  static void notifyChat(Chat c) {
    if (_chatGuids.contains(c.guid)) {
      _chatUpdate.add(c);
    } else {
      _chatGuids.add(c.guid);
      _newChat.add(c);
    }
  }
}
