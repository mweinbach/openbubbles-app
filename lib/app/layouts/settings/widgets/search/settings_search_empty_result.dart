import 'package:flutter/material.dart';

class EmptySearchResult extends StatelessWidget {
  final String searchQuery;

  const EmptySearchResult({super.key, required this.searchQuery});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(
              Icons.search,
              size: 48.0,
              color: Colors.grey,
            ),
            const SizedBox(height: 8.0),
            Text(
              'No Results for "$searchQuery"',
              style: const TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 16.0,
              ),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}
