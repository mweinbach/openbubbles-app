import 'package:flutter/cupertino.dart';

/// A wrapper class representing a searchable settings tile.
///
/// This class is used to encapsulate a settings tile widget along with
/// metadata, this enables search functionality, by filtering title and associated
/// keywords, and an optional navigation callback.
class SearchableSettingItem extends StatelessWidget {
  final Widget child;
  final String title;
  final List<String> searchTags; // list of keywords which can be found on each page to build a breadcrumb
  final VoidCallback? onTap; // navigation to each page from breadcrumb tile

  const SearchableSettingItem({
    super.key,
    required this.child,
    required this.title,
    this.searchTags = const [],
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return child;
  }
}
