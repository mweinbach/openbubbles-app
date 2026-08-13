import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/scheduler.dart';

class SettingsSearchBariOS extends StatefulWidget {
  final ValueChanged<String>? onChanged;
  final TextEditingController controller;
  final FocusNode focusNode;

  const SettingsSearchBariOS({super.key, this.onChanged, required this.controller, required this.focusNode});

  @override
  State<SettingsSearchBariOS> createState() => _SettingsSearchBariOSState();
}

class _SettingsSearchBariOSState extends State<SettingsSearchBariOS> {
  String searchValue = '';

  @override
  Widget build(BuildContext context) {
    return CupertinoSearchTextField(
      controller: widget.controller,
      focusNode: widget.focusNode, // connect the focus node
      placeholder: "Search Settings",
      placeholderStyle: TextStyle(
          color: Theme.of(context).brightness == Brightness.dark
              ? Colors.white.withValues(alpha: 0.5)
              : Colors.black.withValues(alpha: 0.5),
          fontSize: Theme.of(context).textTheme.bodyLarge?.fontSize),
      style: TextStyle(
          color: Theme.of(context).brightness == Brightness.dark
              ? CupertinoColors.white // white text color for dark mode
              : CupertinoColors.black, // black text color for light mode
          fontSize: Theme.of(context).textTheme.bodyLarge?.fontSize),
      onChanged: (query) {
        setState(() {
          searchValue = query.toLowerCase();
        });
        // Defer the callback to prevent accessing size during layout
        SchedulerBinding.instance.addPostFrameCallback((_) {
          widget.onChanged?.call(query);
        });
      },
    );
  }
}
