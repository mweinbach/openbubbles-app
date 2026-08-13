class StructuredName {
  final String? givenName;
  final String? familyName;
  final String? middleName;
  final String? namePrefix;
  final String? nameSuffix;
  final String? nickname;

  const StructuredName({
    this.givenName,
    this.familyName,
    this.middleName,
    this.namePrefix,
    this.nameSuffix,
    this.nickname,
  });

  Map<String, dynamic> toMap() => {
        'givenName': givenName,
        'familyName': familyName,
        'middleName': middleName,
        'namePrefix': namePrefix,
        'nameSuffix': nameSuffix,
        'nickname': nickname,
      };

  factory StructuredName.fromMap(Map<String, dynamic> map) => StructuredName(
        givenName: map['givenName'],
        familyName: map['familyName'],
        middleName: map['middleName'],
        namePrefix: map['namePrefix'],
        nameSuffix: map['nameSuffix'],
        nickname: map['nickname'],
      );
}
