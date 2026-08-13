import 'package:flutter/foundation.dart';
import 'package:bluebubbles/database/models.dart';

@immutable
class HandleSyncPage {
  final double progress;
  final List<Handle> handles;

  const HandleSyncPage(this.progress, this.handles);
}
