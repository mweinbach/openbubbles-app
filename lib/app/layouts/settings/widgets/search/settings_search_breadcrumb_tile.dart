import 'package:flutter/material.dart';

class SearchBreadcrumbTile extends StatelessWidget {
  const SearchBreadcrumbTile({super.key, this.origin, this.destination, this.onTap});

  final String? origin;
  final String? destination;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.only(right: 40, left: 40, top: 15, bottom: 15),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '$destination',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                Text('$origin', style: Theme.of(context).textTheme.bodyMedium)
              ],
            ),
            const Icon(Icons.keyboard_arrow_right_outlined)
          ],
        ),
      ),
    );
  }
}
