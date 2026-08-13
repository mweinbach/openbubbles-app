import 'package:get/get.dart';

class AttachmentUploadProgress {
  final String guid;
  final RxDouble progress;

  AttachmentUploadProgress(this.guid, this.progress);
}
