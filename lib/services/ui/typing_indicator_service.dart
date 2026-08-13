import 'package:bluebubbles/services/backend/interfaces/chat_interface.dart';
import 'package:get/get.dart';
import 'package:get_it/get_it.dart';

// ignore: non_constant_identifier_names
TypingIndicatorService get TypingIndicatorSvc => GetIt.I<TypingIndicatorService>();

class TypingIndicatorService extends GetxController {
  String? _activeTypingChatGuid;

  bool get isTyping => _activeTypingChatGuid != null;

  Future<void> startTyping(String chatGuid) async {
    _activeTypingChatGuid = chatGuid;
    await ChatInterface.startTyping(chatGuid: chatGuid);
  }

  Future<void> stopTyping(String chatGuid) async {
    if (_activeTypingChatGuid == chatGuid) _activeTypingChatGuid = null;
    await ChatInterface.stopTyping(chatGuid: chatGuid);
  }

  /// Stops the active typing indicator for any chat.
  /// Called by LifecycleService before the app backgrounds.
  /// Must complete before GlobalIsolate.drainAndStop() is invoked so the
  /// HTTP request has a chance to succeed.
  Future<void> stopAllTyping() async {
    if (_activeTypingChatGuid == null) return;
    final guid = _activeTypingChatGuid!;
    _activeTypingChatGuid = null;
    await ChatInterface.stopTyping(chatGuid: guid);
  }
}
