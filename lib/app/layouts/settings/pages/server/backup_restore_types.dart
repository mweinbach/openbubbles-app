enum BackupDestination {
  local,
  cloud;

  bool get isCloud => this == BackupDestination.cloud;
}

enum BackupKind {
  settings,
  theme;
}
