import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import 'package:bluebubbles/app/layouts/settings/pages/profile/poster_edit.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';
import 'package:get/get.dart';
import 'package:palette_generator/palette_generator.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'dart:ui' as ui;
import 'package:image/image.dart' as img;
import 'dart:math' as math;

Future<ui.Image> decodeImageFromBytes(Uint8List bytes) async {
  final codec = await ui.instantiateImageCodec(bytes);
  final frame = await codec.getNextFrame();
  return frame.image;
}

class ImagePoster extends StatefulWidget {
  final api.SimplifiedPoster poster;
  final Map<String, ui.Image> images;
  final String? name;
  final String? desc;

  const ImagePoster({super.key, required this.poster, required this.images, this.name, this.desc});

  @override
  State<ImagePoster> createState() => _ImagePosterState();
}

Color saturateColor(Color color) {
  return Color.fromARGB(color.alpha, min(color.red + 50, 255), min(color.green + 50, 255), min(color.blue + 50, 255));
}

Color darken(Color color, [double amount = .1]) {
  assert(amount >= 0 && amount <= 1);

  final hsl = HSLColor.fromColor(color);
  final hslDark = hsl.withLightness((hsl.lightness - amount).clamp(0.0, 1.0));

  return hslDark.toColor();
}

Color uiColorToColor(api.UIColor color) {
  if (color is api.UIColor_GrayscaleAlphaColorSpace) {
    return Color.fromRGBO(
        (color.white * 255).round(), (color.white * 255).round(), (color.white * 255).round(), color.alpha);
  }
  if (color is api.UIColor_RGBAColorSpace) {
    return Color.fromRGBO(
        (color.red * 255).round(), (color.green * 255).round(), (color.blue * 255).round(), color.alpha);
  }

  throw Exception("Unknown color type $color");
}

// alignment: {0, 0.5}
Alignment parseAlignment(String alignment) {
  var parsed = alignment.replaceAll("{", "").replaceAll("}", "").replaceAll(" ", "");
  var results = parsed.split(",");
  // flutter range is -1 to 1, ios range is 0 to 1
  return Alignment(double.parse(results[0]) * 2 - 1, double.parse(results[1]) * 2 - 1);
}

Map<String, AvailableFont> availableFonts = {
  "PRTimeFontIdentifierSFPro": AvailableFont(
      timeIdentifier: "PRTimeFontIdentifierSFPro",
      font: const TextStyle(fontFamily: "Inter"),
      name: (weight) =>
          ".SFUI-Regular_wdth_opsz110000_GRAD_wght${weight.round().toRadixString(16).toUpperCase().padRight(7, "0")}"),
  "PRTimeFontIdentifierSFRounded": AvailableFont(
      timeIdentifier: "PRTimeFontIdentifierSFRounded",
      font: const TextStyle(fontFamily: "Nunito"),
      name: (weight) =>
          ".SFUIRounded-Regular_GRAD_wght${weight.round().toRadixString(16).toUpperCase().padRight(7, "0")}"),
  "PRTimeFontIdentifierNewYorkAlpha": AvailableFont(
      timeIdentifier: "PRTimeFontIdentifierNewYorkAlpha",
      font: const TextStyle(fontFamily: "Alegreya"),
      name: (weight) =>
          ".NewYork-Regular_opszC0000_wght${weight.round().toRadixString(16).toUpperCase().padRight(7, "0")}_GRAD"),
  "PRTimeFontIdentifierSFCondensed": AvailableFont(
      timeIdentifier: "PRTimeFontIdentifierSFCondensed",
      font: const TextStyle(fontFamily: "RobotoCondensed"),
      name: (weight) =>
          ".SFUI-Regular_wdth3C0000_opsz110000_GRAD_wght${weight.round().toRadixString(16).toUpperCase().padRight(7, "0")}"),
};

void drawPosterText(Canvas canvas, Size size, List<TextSpan> text, api.SimplifiedPoster poster) {
  final textPainter = TextPainter(
    text: TextSpan(
      style: availableFonts[poster.titleConfiguration.timeFontConfiguration.timeFontIdentifier]?.font.copyWith(
          color: Colors.white,
          height: 1.1,
          fontVariations: [FontVariation.weight(poster.titleConfiguration.timeFontConfiguration.weight)]),
      children: text,
    ),
    textDirection: TextDirection.ltr,
    textAlign: TextAlign.center,
  );
  textPainter.layout();

  canvas.saveLayer(Offset.zero & size, Paint());
  Offset textOffset = Offset((size.width - textPainter.width) / 2, 215 - textPainter.height);
  textPainter.paint(canvas, textOffset);

  Shader? shader;

  var config = poster.titleConfiguration.titleStyle!;
  if (config is api.PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyle) {
    var color = uiColorToColor(config.colors.first);
    shader = LinearGradient(colors: [color, color]).createShader(textOffset & textPainter.size);
  }
  if (config is api.PRPosterContentMaterialStyle_PRPosterContentGradientStyle) {
    shader = LinearGradient(
            colors: config.colors.map((color) => uiColorToColor(color)).toList(),
            begin: parseAlignment(config.startPoint),
            end: parseAlignment(config.endPoint),
            stops: config.locations)
        .createShader(textOffset & textPainter.size);
  }
  if (config is api.PRPosterContentMaterialStyle_PRPosterContentVibrantMaterialStyle) {
    var color = uiColorToColor(poster.titleConfiguration.titleColor.color);
    shader = LinearGradient(colors: [color, color]).createShader(textOffset & textPainter.size);
  }

  canvas.drawRect(
      textOffset & textPainter.size,
      Paint()
        ..shader = shader
        ..blendMode = BlendMode.srcIn);
  canvas.restore();
}

Widget posterText(api.SimplifiedPoster poster, double fontSize, String text, {bool color = true}) {
  var t = Text(text,
      textAlign: TextAlign.center,
      overflow: TextOverflow.ellipsis,
      style: availableFonts[poster.titleConfiguration.timeFontConfiguration.timeFontIdentifier]?.font.copyWith(
          fontSize: fontSize,
          color: Colors.white,
          height: 1.3,
          fontVariations: [FontVariation.weight(poster.titleConfiguration.timeFontConfiguration.weight)]));
  if (!color) return t;

  return ShaderMask(
      blendMode: BlendMode.srcIn,
      shaderCallback: (Rect bounds) {
        var config = poster.titleConfiguration.titleStyle!;
        if (config is api.PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyle) {
          var color = uiColorToColor(config.colors.first);
          return LinearGradient(colors: [color, color]).createShader(bounds);
        }
        if (config is api.PRPosterContentMaterialStyle_PRPosterContentGradientStyle) {
          return LinearGradient(
                  colors: config.colors.map((color) => uiColorToColor(color)).toList(),
                  begin: parseAlignment(config.startPoint),
                  end: parseAlignment(config.endPoint),
                  stops: config.locations)
              .createShader(bounds);
        }
        if (config is api.PRPosterContentMaterialStyle_PRPosterContentVibrantMaterialStyle) {
          var color = uiColorToColor(poster.titleConfiguration.titleColor.color);
          return LinearGradient(colors: [color, color]).createShader(bounds);
        }
        throw Exception();
      },
      child: t);
}

double getPosterPadding(api.PosterAsset asset) {
  return (asset.contents.properties.portraitLayout.imageSize.height * 0.35).ceilToDouble();
}

Color posterColorToColor(api.PosterColor color) {
  return Color.fromARGB(
      (color.alpha * 255).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt());
}

api.PosterColor colorToPosterColor(Color color) {
  return api.PosterColor(
      alpha: color.alpha.toDouble() / 255,
      blue: color.blue.toDouble() / 255,
      green: color.green.toDouble() / 255,
      red: color.red.toDouble() / 255);
}

Future<Map<String, ui.Image>> loadAssetImages(String path, api.PosterAsset asset) async {
  Map<String, ui.Image> images = {};
  for (var file in asset.files.entries) {
    print("Decoding image");
    var data = await PushSvc.fileForAsset(path, asset, file.key, friendly: true).readAsBytes();
    images[file.key] = await decodeImageFromBytes(data);
  }
  return images;
}

Future<Map<String, ui.Image>> loadPosterImages(String path, api.SimplifiedPoster poster) async {
  if (poster.type is api.PosterType_Photo) {
    print("Loading assets");
    Map<String, ui.Image> images = await loadAssetImages(path, (poster.type as api.PosterType_Photo).assets[0]);
    print("Done");
    return images;
  }
  if (poster.type is api.PosterType_Memoji) {
    var bytes = await File("$path/memoji.png").readAsBytes();
    Map<String, ui.Image> images = {};
    images["memoji"] = await decodeImageFromBytes(bytes);
    return images;
  }
  return {};
}

class _ImagePosterState extends State<ImagePoster> {
  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      painter: PosterPainter(poster: widget.poster, images: widget.images, name: widget.name, desc: widget.desc),
      child: const SizedBox.expand(),
    );
  }
}

Future restorePoster(api.SimplifiedPoster? poster, String posterPath) async {
  if (poster == null) return;

  if (poster.type is api.PosterType_Photo) {
    var photo = poster.type as api.PosterType_Photo;
    for (var asset in photo.assets) {
      Map<String, Uint8List> entries = {};
      for (var file in asset.files.entries) {
        File f = PushSvc.fileForAsset(posterPath, asset, file.key);
        entries[file.key] = await f.readAsBytes();
      }
      asset.files = entries;
    }
  }

  if (poster.type is api.PosterType_Memoji) {
    var photo = poster.type as api.PosterType_Memoji;
    photo.data.avatarImageData = await File("$posterPath/memoji_orig.heic").readAsBytes();
  }
}

api.SimplifiedPoster createNewPoster(api.PosterType type, Color randomColor, api.PosterRole role) {
  return api.SimplifiedPoster(
    role: role,
    titleConfiguration: api.PRPosterTitleStyleConfiguration(
        alternateDateEnabled: false,
        contentsLuminence: 0,
        groupName: "PREditingLook",
        preferredTitleAlignment: 0,
        preferredTitleLayout: 0,
        timeFontConfiguration: api.PRPosterSystemTimeFontConfiguration(
          isSystemItem: true,
          timeFontIdentifier: "PRTimeFontIdentifierSFPro",
          weight: 400,
        ),
        titleColor: api.PRPosterColor(
          preferredStyle: 2,
          identifier: "vibrantMaterialColor",
          suggested: false,
          color: api.UIColor.grayscaleAlphaColorSpace(
              colorComponents: 2,
              white: 1,
              alpha: 0.5,
              bin: base64Decode("MSAwLjU="),
              colorSpace: 4,
              class_: "PRPosterColor"),
        ),
        titleContentStyle: Uint8List.fromList([]),
        userConfigured: false,
        timeNumberingSystem: api.nsNull(),
        titleStyle: api.PRPosterContentMaterialStyle.prPosterContentDiscreteColorsStyle(
            variation: 0,
            colors: [colorToUIColor(saturateColor(randomColor))],
            vibrant: true,
            supportsVariation: true,
            needsToResolveVariation: false)),
    type: type,
  );
}

Future<Uint8List> imageToJpeg(ui.Image image) async {
  var data = await image.toByteData(format: ui.ImageByteFormat.rawRgba);

  final img.Image converted = img.Image.fromBytes(
    width: image.width,
    height: image.height,
    bytes: data!.buffer,
    order: img.ChannelOrder.rgba,
  );

  // Step 4: Encode to JPEG
  return img.encodeJpg(converted, quality: 90);
}

Future<Color?> getVibrantColor(ui.Image image) async {
  var palette = await PaletteGenerator.fromImage(image);
  return palette.lightVibrantColor?.color;
}

void drawAsset(Map<String, ui.Image> images, Canvas canvas, api.PosterAsset asset, Map<String, double> alphas,
    {bool Function(api.PhotoPosterLayer)? predicate}) {
  asset.contents.layers.sort((a, b) => ((a.zPosition - b.zPosition) * 1000).round());
  var paint = Paint();
  for (var layer in asset.contents.layers) {
    if (!(predicate ?? (_) => true)(layer)) continue;
    var imagerect2 =
        Rect.fromLTWH(0, 0, images[layer.filename]!.width.toDouble(), images[layer.filename]!.height.toDouble());
    var alpha = alphas[layer.identifier] ?? 1;
    paint.color = Color.fromRGBO(0, 0, 0, alpha);
    canvas.drawImageRect(
      images[layer.filename]!,
      imagerect2,
      Rect.fromLTWH(layer.frame.x, layer.frame.y, layer.frame.width, layer.frame.height),
      paint,
    );
  }
}

void drawMonogram(api.PosterType_Monogram type, Size size, Canvas canvas) {
  var rect = Rect.fromLTWH(0, 0, size.width, size.height);
  final paint = Paint()
    ..shader = LinearGradient(
      begin: Alignment.topCenter,
      end: Alignment.bottomCenter,
      colors: [
        posterColorToColor(type.data.topBackgroundColorDescription),
        posterColorToColor(type.data.backgroundColorDescription),
      ],
    ).createShader(rect);
  canvas.drawRect(rect, paint);

  final textPainter = TextPainter(
    text: TextSpan(
      text: type.data.initials,
      style: const TextStyle(color: Colors.white, fontSize: 200, fontWeight: FontWeight.w500),
    ),
    textDirection: TextDirection.ltr,
  );
  textPainter.layout();

  canvas.saveLayer(Offset.zero & size, Paint());
  Offset textOffset = Offset((size.width - textPainter.width) / 2, (size.height - textPainter.height) / 2);
  textPainter.paint(canvas, textOffset);
  canvas.drawRect(
      textOffset & textPainter.size,
      Paint()
        ..shader = LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            saturateColor(posterColorToColor(type.data.topBackgroundColorDescription)),
            saturateColor(posterColorToColor(type.data.backgroundColorDescription)),
          ],
        ).createShader(rect)
        ..blendMode = BlendMode.srcIn);
  canvas.restore();
}

Future<Uint8List> drawMonogramProfile(api.PosterType_Monogram type) async {
  var recorder = ui.PictureRecorder();
  var canvas = Canvas(recorder);

  List<Color> colors = [
    posterColorToColor(type.data.topBackgroundColorDescription),
    posterColorToColor(type.data.backgroundColorDescription),
  ];

  if (colors[0].value == colors[1].value) {
    colors[0] = saturateColor(colors[1]);
  }

  var size = const Size(570, 570);
  var rect = Rect.fromLTWH(0, 0, size.width, size.height);
  final paint = Paint()
    ..shader = LinearGradient(
      begin: Alignment.topCenter,
      end: Alignment.bottomCenter,
      colors: colors,
    ).createShader(rect);
  canvas.drawRect(rect, paint);

  final textPainter = TextPainter(
    text: TextSpan(
      text: type.data.initials,
      style: const TextStyle(
          color: Colors.white, fontSize: 280, fontVariations: [FontVariation.weight(500)], fontFamily: "Nunito"),
    ),
    textDirection: TextDirection.ltr,
  );
  textPainter.layout();

  Offset textOffset = Offset((size.width - textPainter.width) / 2, (size.height - textPainter.height) / 2);
  textPainter.paint(canvas, textOffset);

  var picture = recorder.endRecording();
  var image = await picture.toImage(size.width.round(), size.height.round());
  return await imageToJpeg(image);
}

api.UIColor colorToUIColor(Color color) {
  var green = color.green.toDouble() / 255;
  var blue = color.blue.toDouble() / 255;
  var red = color.red.toDouble() / 255;
  return api.UIColor.rgbaColorSpace(
      colorComponents: 4,
      green: green,
      blue: blue,
      red: red,
      alpha: color.opacity,
      rgb: utf8.encode("${red.toStringAsFixed(3)} ${green.toStringAsFixed(3)} ${blue.toStringAsFixed(3)}"),
      colorSpace: 2,
      class_: "UIColor");
}

class PosterPainter extends CustomPainter {
  final api.SimplifiedPoster poster;
  final Map<String, ui.Image> images;
  final String? name;
  final String? desc;

  PosterPainter({
    required this.poster,
    required this.images,
    this.name,
    this.desc,
  });

  @override
  void paint(Canvas canvas, Size size) {
    canvas.clipRect(Rect.fromLTWH(0, 0, size.width, size.height));

    List<TextSpan> textParts = [];
    if (name != null) {
      var parts = name!.split(" ");
      if (desc != null) {
        textParts.add(TextSpan(
            text: "$desc\n",
            style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w600, fontFamily: "Roboto", height: 5)));
        textParts.add(TextSpan(text: parts.first, style: const TextStyle(fontSize: 70)));
      } else {
        if (parts.length == 1) {
          textParts.add(TextSpan(text: parts[0], style: const TextStyle(fontSize: 80)));
        } else {
          textParts.add(TextSpan(text: "${parts[0]}\n", style: const TextStyle(fontSize: 40)));
          textParts.add(TextSpan(text: parts.last, style: const TextStyle(fontSize: 70)));
        }
      }
    }

    if (poster.type is api.PosterType_Photo) {
      var asset = (poster.type as api.PosterType_Photo).assets[0];

      final layout = asset.contents.properties.portraitLayout;

      // Scale to cover
      final scaleX = size.width / layout.visibleFrame.width;
      final scaleY = size.height / layout.visibleFrame.height;
      double scale;
      double translateX = 0;
      double translateY = 0;
      if (scaleY > scaleX) {
        scale = scaleY;
        translateX = (layout.visibleFrame.width - size.width / scale) / 2;
      } else {
        scale = scaleX;
        translateY =
            (layout.visibleFrame.height - size.height / scale) / 3; // not / 2 to bias up, because faces are normally up
      }

      final y =
          layout.imageSize.height - layout.visibleFrame.y - layout.visibleFrame.height; // starts counting from bottom
      // Translate and scale to focus only on the viewport part of the image

      canvas.save();
      canvas.scale(scale, scale);
      canvas.translate(-layout.visibleFrame.x, -y);
      canvas.translate(-translateX, -translateY); // center vertically
      drawAsset(images, canvas, asset, {},
          predicate: (layer) =>
              (poster.type as api.PosterType_Photo).assets[0].contents.properties.portraitLayout.clockLayerOrder !=
                  "ClockAboveBackground" ||
              !layer.identifier.startsWith("foreground"));
      canvas.restore();

      if (name != null) {
        drawPosterText(canvas, size, textParts, poster);
      }

      if ((poster.type as api.PosterType_Photo).assets[0].contents.properties.portraitLayout.clockLayerOrder ==
          "ClockAboveBackground") {
        canvas.scale(scale, scale);
        canvas.translate(-layout.visibleFrame.x, -y);
        canvas.translate(-translateX, -translateY); // center vertically
        drawAsset(images, canvas, asset, {}, predicate: (layer) => layer.identifier.startsWith("foreground"));
      }
    } else if (poster.type is api.PosterType_Monogram) {
      drawMonogram(poster.type as api.PosterType_Monogram, size, canvas);
      drawPosterText(canvas, size, textParts, poster);
    } else if (poster.type is api.PosterType_Memoji) {
      var type = poster.type as api.PosterType_Memoji;
      canvas.drawRect(Offset.zero & size, Paint()..color = posterColorToColor(type.background));
      var image = images["memoji"]!;
      var scale = size.width / image.width.toDouble();
      var imageHeight = image.height.toDouble() * scale;
      canvas.drawImageRect(image, Rect.fromLTWH(0, 0, image.width.toDouble(), image.height.toDouble()),
          Rect.fromLTWH(0, (size.height - imageHeight) / 2, size.width, imageHeight), Paint());
      drawPosterText(canvas, size, textParts, poster);
    }
  }

  @override
  bool shouldRepaint(covariant PosterPainter oldDelegate) {
    return oldDelegate.poster != poster || oldDelegate.images != images;
  }
}
