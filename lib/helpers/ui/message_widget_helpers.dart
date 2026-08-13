import 'package:bluebubbles/database/models.dart' hide Entity;
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:collection/collection.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:google_ml_kit/google_ml_kit.dart' hide Message;
import 'package:maps_launcher/maps_launcher.dart';
import 'package:tuple/tuple.dart';
import 'package:url_launcher/url_launcher.dart';

List<InlineSpan> buildMessageSpans(BuildContext context, MessagePart part, Message message,
    {Color? colorOverride, bool hideBodyText = false}) {
  final textSpans = <InlineSpan>[];
  final bubbleColors = context.theme.extensions[BubbleColors] as BubbleColors?;
  final textStyle = (context.theme.extensions[BubbleText] as BubbleText).bubbleText.apply(
        color: colorOverride ??
            (message.isFromMe!
                ? context.theme.colorScheme.onBubble(context, message.chat.target?.isIMessage ?? true)
                : bubbleColors?.onReceivedBubbleColor ?? context.theme.colorScheme.onSurfaceVariant),
        fontSizeFactor: message.isBigEmoji ? 3 : 1,
      );

  if (!isNullOrEmpty(part.subject)) {
    textSpans.addAll(MessageHelper.buildEmojiText(
      "${part.displaySubject}${!hideBodyText ? "\n" : ""}",
      textStyle.apply(fontWeightDelta: 2),
    ));
  }
  if (part.annotations.isNotEmpty) {
    part.annotations.forEachIndexed((i, e) {
      final range = part.annotations[i].range;
      var style = textStyle;
      if (e.bold ?? false) style = style.apply(fontWeightDelta: 2);
      if (e.italic ?? false) style = style.apply(fontStyle: FontStyle.italic);
      style = style.apply(
          decoration: TextDecoration.combine([
        if (e.strikethrough ?? false) TextDecoration.lineThrough,
        if (e.underline ?? false) TextDecoration.underline,
      ]));
      if (e.textEffect == Attributes.BIG) style = style.apply(fontSizeDelta: 4);
      if (e.textEffect == Attributes.SMALL) style = style.apply(fontSizeDelta: -2);
      if (e.mentionedAddress != null) {
        textSpans.addAll(MessageHelper.buildEmojiText(
            part.displayText!.substring(range.first, range.last), style.apply(fontWeightDelta: 2),
            recognizer: TapGestureRecognizer()
              ..onTap = () async {
                if (kIsDesktop || kIsWeb) return;
                final handle = ChatsSvc.activeChat!.chat.handles
                    .firstWhereOrNull((h) => h.address == part.annotations[i].mentionedAddress);
                if (handle?.contactsV2.isNotEmpty == true && handle!.contactsV2.first.isNative) {
                  try {
                    await MethodChannelSvc.actions.viewContactForm(
                      nativeContactId: handle.contactsV2.first.nativeContactId,
                    );
                  } catch (_) {
                    showSnackbar("Error", "Failed to find contact on device!");
                  }
                } else if (handle != null) {
                  await MethodChannelSvc.actions.openContactForm(
                    address: handle.address,
                    isEmail: handle.address.isEmail,
                  );
                }
              }));
      } else {
        textSpans.addAll(MessageHelper.buildEmojiText(
          part.displayText!.substring(range.first, range.last),
          style,
        ));
      }
    });
  } else if (!isNullOrEmpty(part.displayText)) {
    textSpans.addAll(MessageHelper.buildEmojiText(
      part.displayText!,
      textStyle,
    ));
  }

  return textSpans;
}

Future<List<InlineSpan>> buildEnrichedMessageSpans(BuildContext context, MessagePart part, Message message,
    {Color? colorOverride, bool hideBodyText = false}) async {
  final textSpans = <InlineSpan>[];
  final bubbleColors = context.theme.extensions[BubbleColors] as BubbleColors?;
  final textStyle = (context.theme.extensions[BubbleText] as BubbleText).bubbleText.apply(
        color: colorOverride ??
            (message.isFromMe!
                ? context.theme.colorScheme.onBubble(context, message.chat.target?.isIMessage ?? true)
                : bubbleColors?.onReceivedBubbleColor ?? context.theme.colorScheme.onSurfaceVariant),
        fontSizeFactor: message.isBigEmoji ? 3 : 1,
      );

  // extract rich content
  final urlRegex = RegExp(
      r'((https?://)|(www\.))[-a-zA-Z0-9@:%._+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}([-a-zA-Z0-9/()@:%_.~#?&=*\[\]]*)\b');
  List<Annotation> annotations = part.annotations.isNotEmpty
      ? part.annotations.map((a) => a.copy()).toList()
      : (!isNullOrEmpty(part.displayText)
          ? [
              Annotation(range: [0, part.displayText!.length])
            ]
          : []);
  void markRange(Tuple3<String, List<int>, List?> annotation) {
    var range = annotation.item2;
    final extras = <Annotation>[];
    for (final a in annotations) {
      if (a.range[0] < range[0] && a.range[1] > range[0]) {
        final dup = a.copy();
        dup.range[1] = range[0];
        extras.add(dup);
        a.range[0] = range[0];
      }

      if (a.range[0] < range[1] && a.range[1] > range[1]) {
        final dup = a.copy();
        dup.range[0] = range[1];
        extras.add(dup);
        a.range[1] = range[1];
      }
    }
    annotations.addAll(extras);

    for (final a in annotations) {
      if (a.range[0] >= range[0] && a.range[1] <= range[1]) {
        a.renderExtras.add(annotation);
      }
    }
  }

  final controller = cvc(message.chat.target ?? ChatsSvc.activeChat!.chat);
  if (!isNullOrEmpty(part.text)) {
    if (!kIsWeb && !kIsDesktop && SettingsSvc.settings.smartReply.value) {
      if (controller.mlKitParsedText["${message.guid!}-${part.part}"] == null) {
        try {
          controller.mlKitParsedText["${message.guid!}-${part.part}"] =
              await GoogleMlKit.nlp.entityExtractor(EntityExtractorLanguage.english).annotateText(part.text!);
        } catch (ex, stack) {
          Logger.warn('Failed to extract entities using mlkit!', error: ex, trace: stack);
        }
      }
      final entities = controller.mlKitParsedText["${message.guid!}-${part.part}"] ?? [];
      for (EntityAnnotation element in entities) {
        if (element.entities.first is AddressEntity) {
          markRange(Tuple3("map", [element.start, element.end], null));
        } else if (element.entities.first is PhoneEntity) {
          markRange(Tuple3("phone", [element.start, element.end], null));
        } else if (element.entities.first is EmailEntity) {
          markRange(Tuple3("email", [element.start, element.end], null));
        } else if (element.entities.first is UrlEntity) {
          markRange(Tuple3("link", [element.start, element.end], null));
        } else if (element.entities.first is DateTimeEntity) {
          final ent = (element.entities.first as DateTimeEntity);
          if (part.text?.substring(element.start, element.end).toLowerCase() == "now") {
            continue;
          }
          markRange(Tuple3("date", [element.start, element.end], [ent.timestamp]));
        } else if (element.entities.first is TrackingNumberEntity) {
          final ent = (element.entities.first as TrackingNumberEntity);
          markRange(Tuple3("tracking", [element.start, element.end], [ent.carrier, ent.number]));
        } else if (element.entities.first is FlightNumberEntity) {
          final ent = (element.entities.first as FlightNumberEntity);
          markRange(Tuple3("flight", [element.start, element.end], [ent.airlineCode, ent.flightNumber]));
        }
      }
    } else {
      final matches = urlRegex.allMatches(part.text!).toList();
      for (final match in matches) {
        markRange(Tuple3("link", [match.start, match.end], null));
      }
    }
  }
  // render subject
  if (!isNullOrEmpty(part.subject)) {
    textSpans.addAll(MessageHelper.buildEmojiText(
      "${part.displaySubject}${!hideBodyText ? "\n" : ""}",
      textStyle.apply(fontWeightDelta: 2),
    ));
  }
  annotations.sort((a, b) => a.range[0].compareTo(b.range[0]));
  if (annotations.isNotEmpty) {
    annotations.forEachIndexed((i, e) {
      final item = e.renderExtras.firstOrNull;
      final type = item?.item1;
      final range = e.range;
      final data = item?.item3;
      final text = part.displayText!.substring(range.first, range.last);
      var style = textStyle;
      if (e.bold ?? false) style = style.apply(fontWeightDelta: 2);
      if (e.italic ?? false) style = style.apply(fontStyle: FontStyle.italic);
      style = style.apply(
          decoration: TextDecoration.combine([
        if (e.strikethrough ?? false) TextDecoration.lineThrough,
        if (e.underline ?? false) TextDecoration.underline,
      ]));
      if (e.textEffect == Attributes.BIG) style = style.apply(fontSizeDelta: 4);
      if (e.textEffect == Attributes.SMALL) style = style.apply(fontSizeDelta: -2);
      if (e.mentionedAddress != null) {
        textSpans.addAll(MessageHelper.buildEmojiText(text, style.apply(fontWeightDelta: 2),
            recognizer: TapGestureRecognizer()
              ..onTap = () async {
                if (kIsDesktop || kIsWeb) return;
                final handle =
                    ChatsSvc.activeChat!.chat.handles.firstWhereOrNull((h) => h.address == e.mentionedAddress);
                if (handle?.contactsV2.isNotEmpty == true && handle!.contactsV2.first.isNative) {
                  try {
                    await MethodChannelSvc.actions.viewContactForm(
                      nativeContactId: handle.contactsV2.first.nativeContactId,
                    );
                  } catch (_) {
                    showSnackbar("Error", "Failed to find contact on device!");
                  }
                } else if (handle != null) {
                  await MethodChannelSvc.actions.openContactForm(
                    address: handle.address,
                    isEmail: handle.address.isEmail,
                  );
                }
              }));
      } else if (urlRegex.hasMatch(text) ||
          type == "map" ||
          text.isPhoneNumber ||
          text.isEmail ||
          type == "date" ||
          type == "tracking" ||
          type == "flight") {
        textSpans.add(
          TextSpan(
            text: text,
            recognizer: TapGestureRecognizer()
              ..onTap = () async {
                if (type == "link") {
                  String url = text;
                  if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "http://$url";
                  }
                  await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
                } else if (type == "map") {
                  await MapsLauncher.launchQuery(text);
                } else if (type == "phone") {
                  await launchUrl(Uri(scheme: "tel", path: text));
                } else if (type == "email") {
                  await launchUrl(Uri(scheme: "mailto", path: text));
                } else if (type == "date") {
                  await MethodChannelSvc.actions.openCalendar(dateEpochMillis: data!.first as int);
                } else if (type == "tracking") {
                  final TrackingCarrier c = data!.first;
                  final String number = data.last;
                  Clipboard.setData(ClipboardData(text: number));
                  await launchUrl(Uri.parse("https://www.google.com/search?q=${c.name} $number"),
                      mode: LaunchMode.externalApplication);
                } else if (type == "flight") {
                  final String c = data!.first;
                  final String number = data.last;
                  await launchUrl(Uri.parse("https://www.google.com/search?q=flight $c$number"),
                      mode: LaunchMode.externalApplication);
                }
              },
            style: style.apply(decoration: TextDecoration.underline),
          ),
        );
      } else {
        textSpans.addAll(MessageHelper.buildEmojiText(
          text,
          style,
        ));
      }
    });
  } else if (!isNullOrEmpty(part.displayText)) {
    textSpans.addAll(MessageHelper.buildEmojiText(
      part.displayText!,
      textStyle,
    ));
  }

  return textSpans;
}
