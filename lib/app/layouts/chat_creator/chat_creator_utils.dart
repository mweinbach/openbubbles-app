import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/utils/string_utils.dart';
import 'package:collection/collection.dart';
import 'package:get/get_utils/src/extensions/string_extensions.dart';

class ChatCreatorUtils {
  static const List<int> _phoneMatchLengths = <int>[15, 14, 13, 12, 11, 10, 9, 8, 7];

  static List<ContactV2> filterContacts(List<ContactV2> contacts, String query) {
    return contacts
        .where((e) =>
            e.computedDisplayName.toLowerCase().contains(query) ||
            (e.nickname?.toLowerCase().contains(query) ?? false) ||
            e.phoneNumbers.firstWhereOrNull((p) => cleansePhoneNumber(p.number.toLowerCase()).contains(query)) !=
                null ||
            e.emailAddresses.firstWhereOrNull((email) => email.address.toLowerCase().contains(query)) != null)
        .toList();
  }

  static List<Chat> filterChats(List<Chat> chats, String query, ChatServiceType selectedService) {
    return chats
        .where(
          (chat) =>
              (selectedService.isIMessageService == chat.isIMessage) &&
              (chat.getTitle().toLowerCase().contains(query) ||
                  chat.handles.firstWhereOrNull((handle) =>
                          handle.address.contains(query) || handle.displayName.toLowerCase().contains(query)) !=
                      null),
        )
        .toList();
  }

  static bool chatMatchesSelectedContacts(Chat chat, List<String> selectedAddresses) {
    if (chat.handles.length != selectedAddresses.length) return false;

    int matches = 0;
    for (final address in selectedAddresses) {
      for (final participant in chat.handles) {
        if (address.isEmail && !participant.address.isEmail) continue;
        if (address == participant.address) {
          matches += 1;
          break;
        }

        final numeric = address.numericOnly();
        if (_phoneMatchLengths.contains(numeric.length) && cleansePhoneNumber(participant.address).endsWith(numeric)) {
          matches += 1;
          break;
        }
      }
    }

    return matches == selectedAddresses.length;
  }
}
