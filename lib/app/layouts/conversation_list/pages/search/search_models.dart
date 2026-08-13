import 'package:bluebubbles/database/models.dart';

enum SearchMode { local, network }

class SearchResultItem {
  final Chat chat;
  final Message message;
  const SearchResultItem({required this.chat, required this.message});
}
