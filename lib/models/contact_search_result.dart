import 'package:flutter/foundation.dart';
import 'package:bluebubbles/database/models.dart';

@immutable
class ContactSearchResult {
  final List<ContactV2> contacts;
  final List<Chat> chats;

  const ContactSearchResult(this.contacts, this.chats);
}
