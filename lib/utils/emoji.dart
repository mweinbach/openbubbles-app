import 'package:collection/collection.dart';
import 'package:unicode_emojis/unicode_emojis.dart';

extension Slugify on Emoji {
  String get slug => shortName.toLowerCase().replaceAll(RegExp(r'([^a-z]{1,})'), '_');
  String get skinToneSlug => "$slug$skinTone";

  String get skinTone => unified
      .split("-")
      .where((element) => element.startsWith("1F3F"))
      .map((e) => "_tone${int.parse(e.substring(4), radix: 16) - 10}")
      .join("");

  Emoji get slugCopy => copyWith(shortName: slug);
  Emoji get skinToneSlugCopy => copyWith(shortName: skinToneSlug);
}

final Map<String, Emoji> shortNameToEmoji =
    Map.fromEntries(UnicodeEmojis.allEmojis.map((e) => MapEntry(e.slug, e.slugCopy)));
final Map<String, Emoji> shortNameToSkinToneEmoji = Map.fromEntries(UnicodeEmojis.allEmojis
    .map((e) => e.skinVariations?.mapIndexed((i, e) => MapEntry(e.skinToneSlug, e.skinToneSlugCopy)))
    .nonNulls
    .flattened);

Iterable<Emoji> limitGenerator(Iterable<Emoji> generator, {int? limit}) sync* {
  int count = 0;
  for (final emoji in generator) {
    if (limit != null && count >= limit) break;
    yield emoji;
    count++;
  }
}

String removeLastUnderscore(String str) {
  int lastUnderscore = str.lastIndexOf(RegExp(r'(?<!tone\d)_'));
  return lastUnderscore == -1 ? str : str.substring(0, lastUnderscore);
}

Iterable<Emoji> emojiQuery(String emojiName) sync* {
  if (shortNameToEmoji.containsKey(emojiName)) {
    yield shortNameToEmoji[emojiName]!;

    for (final name in shortNameToSkinToneEmoji.keys) {
      if (name.startsWith("${emojiName}_tone")) {
        yield shortNameToSkinToneEmoji[name]!;
      }
    }
  }

  String withoutEnd = removeLastUnderscore(emojiName);
  if (shortNameToEmoji.containsKey(withoutEnd)) {
    for (final name in shortNameToSkinToneEmoji.keys) {
      if (name.startsWith("${withoutEnd}_tone") && name.startsWith(emojiName)) {
        yield shortNameToSkinToneEmoji[name]!;
      }
    }
  }

  for (final name in shortNameToEmoji.keys) {
    if (name.contains(emojiName) && name != emojiName) {
      yield shortNameToEmoji[name]!;
    }
  }
}
