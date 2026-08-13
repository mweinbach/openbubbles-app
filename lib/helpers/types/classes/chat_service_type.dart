/// Represents the messaging service type for a chat.
///
/// Derived from the GUID prefix (the part before `;-;` or `;+;`).
/// Add new service types here. Set [isVisible] to false to define a service
/// without exposing it in the UI (e.g. RCS, which is defined but not yet shown).
enum ChatServiceType {
  iMessage(label: 'iMessage', isVisible: true),
  sms(label: 'SMS', isVisible: true),
  rcs(label: 'RCS', isVisible: false);

  const ChatServiceType({required this.label, required this.isVisible});

  final String label;
  final bool isVisible;

  /// Returns the server-side method string for this service type.
  String get method {
    switch (this) {
      case ChatServiceType.iMessage:
        return 'iMessage';
      case ChatServiceType.sms:
        return 'SMS';
      case ChatServiceType.rcs:
        return 'RCS';
    }
  }

  /// Whether chats of this type are iMessage chats.
  bool get isIMessageService => this == ChatServiceType.iMessage;

  /// Parses the service type from a chat GUID by inspecting the prefix
  /// before the first `;-;` or `;+;` separator.
  ///
  /// Examples:
  ///   `iMessage;-;+12345678901` → [ChatServiceType.iMessage]
  ///   `SMS;-;+12345678901`       → [ChatServiceType.sms]
  ///   `chat1a2b3c...`            → [ChatServiceType.iMessage] (group chat, no prefix)
  static ChatServiceType fromGuid(String guid) {
    int sepIdx = guid.indexOf(';-;');
    if (sepIdx == -1) sepIdx = guid.indexOf(';+;');
    if (sepIdx == -1) return ChatServiceType.iMessage;

    final prefix = guid.substring(0, sepIdx).toUpperCase();
    switch (prefix) {
      case 'SMS':
        return ChatServiceType.sms;
      case 'RCS':
        return ChatServiceType.rcs;
      default:
        return ChatServiceType.iMessage;
    }
  }
}
