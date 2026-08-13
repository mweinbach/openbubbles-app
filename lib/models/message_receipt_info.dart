/// Pairs the GUID of a message with the tier-relevant comparison date used
/// by [MessagesService] to decide indicator ownership.
///
/// Each indicator tier stores a different date:
/// - **Read**:      [Message.dateRead]
/// - **Delivered**: [Message.dateDelivered], or [Message.dateCreated] when
///                  [Message.isDelivered] is true but no timestamp is present.
/// - **Sent**:      [Message.dateCreated]
class MessageReceiptInfo {
  final String guid;

  /// The tier-relevant comparison date.
  final DateTime? date;

  /// The message's [Message.dateCreated] used for cross-tier ordering comparisons.
  final DateTime? createdDate;

  const MessageReceiptInfo(this.guid, {this.date, this.createdDate});
}
