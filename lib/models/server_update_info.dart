class ServerUpdateInfo {
  final bool available;
  final String? version;
  final String? releaseDate;
  final String? releaseName;

  ServerUpdateInfo({
    required this.available,
    this.version,
    this.releaseDate,
    this.releaseName,
  });
}
