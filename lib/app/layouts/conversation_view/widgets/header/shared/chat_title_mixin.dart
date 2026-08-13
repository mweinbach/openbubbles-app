import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/widgets.dart';
import 'package:get/get.dart';

/// Mixin for State subclasses that need a reactive chat title observable.
/// Delegates directly to ChatState — no extra subscriptions or caching.
mixin ChatTitleMixin<T extends StatefulWidget> on State<T> {
  RxnString getChatTitleObservable(Chat chat) => ChatsSvc.getChatState(chat.guid)?.title ?? RxnString(chat.getTitle());
}
