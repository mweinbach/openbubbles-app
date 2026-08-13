import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'dart:ui';

import 'package:bluebubbles/app/components/avatars/contact_avatar_widget.dart';
import 'package:bluebubbles/app/components/custom/custom_bouncing_scroll_physics.dart';
import 'package:bluebubbles/app/layouts/chat_creator/chat_creator.dart';
import 'package:bluebubbles/app/layouts/settings/pages/profile/posterkit.dart';
import 'package:bluebubbles/database/database.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/app/layouts/conversation_list/pages/conversation_list.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/tile/conversation_tile.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/tile/pinned_conversation_tile.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/conversation_list_fab.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/header/cupertino_header.dart';
import 'package:bluebubbles/app/wrappers/stateful_boilerplate.dart';
import 'package:bluebubbles/app/wrappers/scrollbar_wrapper.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:crop_your_image/crop_your_image.dart';
import 'package:file_picker/file_picker.dart' as fp;
import 'package:flex_color_picker/flex_color_picker.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_acrylic/flutter_acrylic.dart';
import 'dart:ui' as ui;
import 'package:get/get.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:smooth_page_indicator/smooth_page_indicator.dart';
import 'package:flutter/material.dart';
import 'package:vector_math/vector_math_64.dart' as vector;
import 'dart:math' as math;

class UpperCaseTextFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(TextEditingValue oldValue, TextEditingValue newValue) {
    return TextEditingValue(
      text: newValue.text.toUpperCase(),
      selection: newValue.selection,
    );
  }
}

class AvailableFont {
  String timeIdentifier;
  TextStyle font;
  String Function(double weight) name;

  AvailableFont({
    required this.timeIdentifier,
    required this.font,
    required this.name,
  });
}

class PosterEdit extends StatefulWidget {
  final api.SimplifiedIncomingCallPoster? poster;
  final api.SimplifiedTranscriptPoster? transcriptPoster;
  final void Function(String newPath) posterEdited;
  final Handle? handle;
  final String? activePath;
  const PosterEdit(
      {Key? key, this.poster, this.transcriptPoster, required this.posterEdited, this.handle, this.activePath});

  @override
  State<StatefulWidget> createState() => PosterEditState();
}

class PosterEditState extends State<PosterEdit> with ThemeHelpers, SingleTickerProviderStateMixin {
  final ScrollController iosScrollController = ScrollController();
  final TransformationController cropController = TransformationController();

  Map<String, Image>? images;
  Map<String, double> opacities = {};
  api.SimplifiedIncomingCallPoster? currentPoster;
  api.SimplifiedTranscriptPoster? currentTranscriptPoster;

  api.PhotoPosterContentsFrame? backgroundFrame;

  api.SimplifiedIncomingCallPoster? get callPoster => currentPoster ?? widget.poster;
  api.SimplifiedTranscriptPoster? get transcriptPoster => currentTranscriptPoster ?? widget.transcriptPoster;

  api.SimplifiedPoster get poster => callPoster?.poster ?? transcriptPoster!.poster;

  bool deletingColors = false;
  late AnimationController _drawerAnimationController;
  late Animation<Offset> _animation;

  final TextEditingController monogramController = TextEditingController();

  String? ownedPosterPath; // a path this page "owns", when it closes, it should be deleted
  String get posterPath => ownedPosterPath ?? widget.activePath!;

  Future makeNewPoster() async {
    if (ownedPosterPath != null) {
      await PushSvc.deletePoster(ownedPosterPath!);
    }
    String appDocPath = FilesystemSvc.appDocDir.path;
    int number = Random().nextInt(9999999);
    ownedPosterPath = "$appDocPath/avatars/you/poster-$number";
    var f = Directory(ownedPosterPath!);
    if (!(await f.exists())) {
      await f.create(recursive: true);
    }
  }

  Color get backgroundColor => SettingsSvc.settings.windowEffect.value == WindowEffect.disabled
      ? context.theme.colorScheme.background
      : Colors.transparent;

  @override
  void dispose() {
    _drawerAnimationController.dispose();
    super.dispose();
    if (!SettingsSvc.settings.immersiveMode.value) {
      SystemChrome.setEnabledSystemUIMode(SystemUiMode.manual, overlays: [SystemUiOverlay.bottom, SystemUiOverlay.top]);
      EventDispatcherSvc.emit('theme-update', null);
    }
    stopEditing();
    if (ownedPosterPath != null) {
      PushSvc.deletePoster(ownedPosterPath!);
    }
  }

  // reverts current image to original state
  void stopEditing() {
    if (originalFrame != null && poster.type is api.PosterType_Photo) {
      var asset = (poster.type as api.PosterType_Photo).assets[0];
      var background = asset.contents.layers.firstWhere((l) => l.identifier == "background");
      background.filename = "portrait-layer_background.HEIC";
      background.frame = originalFrame!;
      originalFrame = null;
    }
  }

  api.PhotoPosterContentsFrame? originalFrame;

  @override
  void initState() {
    super.initState();
    // update widget when background color changes
    if (kIsDesktop) {
      SettingsSvc.settings.windowEffect.listen((WindowEffect effect) {
        setState(() {});
      });
    }

    _drawerAnimationController = AnimationController(
      duration: const Duration(milliseconds: 250),
      vsync: this,
    );

    _animation = Tween<Offset>(
      begin: const Offset(0, 1), // start below the screen
      end: Offset.zero, // end at normal position
    ).animate(CurvedAnimation(
      parent: _drawerAnimationController,
      curve: Curves.easeOut,
    ));

    if (!SettingsSvc.settings.immersiveMode.value) {
      SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
      EventDispatcherSvc.emit('theme-update', null);
    }

    if (widget.activePath == null) {
      makeNewPoster();
    }

    if (poster.type is api.PosterType_Monogram) {
      var data = poster.type as api.PosterType_Monogram;
      monogramController.text = data.data.initials;
    }

    if (poster.type is api.PosterType_Photo) {
      var asset = (poster.type as api.PosterType_Photo).assets[0];

      var original = PushSvc.fileForAsset(posterPath, asset, "original-background.jpg");
      if (original.existsSync()) {
        var background = asset.contents.layers.firstWhere((l) => l.identifier == "background");
        background.filename = "original-background.jpg";
        double topPadding = getPosterPadding(asset);
        originalFrame = background.frame;
        background.frame = api.PhotoPosterContentsFrame(
            width: asset.contents.properties.portraitLayout.imageSize.width.toDouble(),
            height: asset.contents.properties.portraitLayout.imageSize.height.toDouble() + topPadding,
            x: 0,
            y: -topPadding);
        backgroundFrame = api.PhotoPosterContentsFrame(
          width: asset.contents.properties.portraitLayout.imageSize.width.toDouble(),
          height: asset.contents.properties.portraitLayout.imageSize.height.toDouble(),
          x: 0,
          y: 0,
        );

        updateOpacities(asset);
      }

      var layout = asset.contents.properties.portraitLayout;
      var matrix = Matrix4.identity();
      matrix.scale(MediaQuery.sizeOf(Get.context!).width / layout.visibleFrame.width);
      matrix.translate(-layout.visibleFrame.x,
          -(layout.imageSize.height - layout.visibleFrame.y - layout.visibleFrame.height + getPosterPadding(asset)), 0);
      cropController.value = matrix;
    }

    if (poster.type is! api.PosterType_Monogram && poster.type is! api.PosterType_Photo) {
      changePhoto();
    }
  }

  Rect getVisibleRect() {
    var translation = -cropController.value.getTranslation() / cropController.value.getMaxScaleOnAxis();
    var otherEdge = (-cropController.value.getTranslation() +
            vector.Vector3(MediaQuery.sizeOf(Get.context!).width, MediaQuery.sizeOf(Get.context!).height, 0)) /
        cropController.value.getMaxScaleOnAxis();
    return Rect.fromLTRB(translation.x, translation.y, otherEdge.x, otherEdge.y);
  }

  api.PhotoPosterContentsFrame centeredAspectCrop({
    required double imageWidth,
    required double imageHeight,
    required double targetAspectRatio, // e.g. 16 / 9
  }) {
    final imageAspect = imageWidth / imageHeight;

    double cropWidth, cropHeight;

    if (imageAspect > targetAspectRatio) {
      // Image is wider than target: match height, crop width
      cropHeight = imageHeight;
      cropWidth = cropHeight * targetAspectRatio;
    } else {
      // Image is taller or equal: match width, crop height
      cropWidth = imageWidth;
      cropHeight = cropWidth / targetAspectRatio;
    }

    final left = (imageWidth - cropWidth) / 2;
    final top = (imageHeight - cropHeight) / 2;

    return api.PhotoPosterContentsFrame(x: left, y: top, width: cropWidth, height: cropHeight);
  }

  void applyCenterCropTransform({
    required Canvas canvas,
    required Size inputSize,
    required Size outputSize,
  }) {
    final inputAspect = inputSize.width / inputSize.height;
    final outputAspect = outputSize.width / outputSize.height;

    double scale;
    Offset translate;

    if (inputAspect > outputAspect) {
      // Input is wider than output → scale to match height, crop width
      scale = outputSize.height / inputSize.height;
      final scaledWidth = inputSize.width * scale;
      translate = Offset((outputSize.width - scaledWidth) / 2.0, 0.0);
    } else {
      // Input is taller → scale to match width, crop height
      scale = outputSize.width / inputSize.width;
      final scaledHeight = inputSize.height * scale;
      translate = Offset(0.0, (outputSize.height - scaledHeight) / 2.0);
    }

    // Apply transform to canvas
    canvas.translate(translate.dx, translate.dy);
    canvas.scale(scale);
  }

  // simulate apple's saturatioon
  static const List<double> darkMatrix = <double>[
    1.385, -0.56, -0.112, 0.0, 0.3, //
    -0.315, 1.14, -0.112, 0.0, 0.3, //
    -0.315, -0.56, 1.588, 0.0, 0.3, //
    0.0, 0.0, 0.0, 1.0, 0.0
  ];

  static const List<double> lightMatrix = <double>[
    1.74, -0.4, -0.17, 0.0, 0.0, //
    -0.26, 1.6, -0.17, 0.0, 0.0, //
    -0.26, -0.4, 1.83, 0.0, 0.0, //
    0.0, 0.0, 0.0, 1.0, 0.0
  ];

  void updateOpacities(api.PosterAsset asset) {
    var visible = getVisibleRect();
    double topPadding = getPosterPadding(asset); // constant?
    var innerRect = backgroundFrame ?? asset.contents.layers.firstWhere((l) => l.identifier == "background").frame;
    var innerActualRect = Rect.fromLTWH(innerRect.x, innerRect.y + topPadding, innerRect.width, innerRect.height);
    var contains = innerActualRect.contains(visible.topLeft) &&
        innerActualRect.contains(visible.topRight) &&
        innerActualRect.contains(visible.bottomLeft) &&
        innerActualRect.contains(visible.bottomRight);

    print(contains);
    double value = contains ? 1 : 0;

    if (value != (opacities["background"] ?? 1) && asset.contents.layers.length > 1) {
      opacities["background"] = value;
      setState(() {});
    }
  }

  Widget buildViewer(api.PosterAsset asset, bool Function(api.PhotoPosterLayer) predicate) {
    double topPadding = getPosterPadding(asset); // constant?
    return InteractiveViewer(
      minScale: 0.01,
      constrained: false,
      maxScale: 1.6,
      onInteractionUpdate: (c) {
        updateOpacities(asset);
      },
      child: SizedBox.fromSize(
        child: Stack(
          children: asset.contents.layers.where(predicate).map((layer) {
            return Positioned(
              child: AnimatedOpacity(
                opacity: opacities[layer.identifier] ?? 1,
                duration: const Duration(milliseconds: 200),
                child: Image.file(
                  PushSvc.fileForAsset(posterPath, asset, layer.filename, friendly: true),
                  width: layer.frame.width,
                  height: layer.frame.height,
                  fit: BoxFit.cover,
                ),
              ),
              left: layer.frame.x,
              top: layer.frame.y + topPadding,
            );
          }).toList(),
        ),
        size: Size(asset.contents.properties.portraitLayout.imageSize.width,
            asset.contents.properties.portraitLayout.imageSize.height + topPadding),
      ),
      transformationController: cropController,
    );
  }

  Future<bool> showPickerDialog(Color color, void Function(Color color) onColorChanged, {bool opacity = true}) async {
    return await ColorPicker(
      color: color,
      onColorChanged: (Color newColor) {
        onColorChanged(newColor);
      },
      title: Text(
        "Select Color",
        style: context.theme.textTheme.titleLarge,
      ),
      width: 40,
      height: 40,
      spacing: 0,
      runSpacing: 0,
      borderRadius: 0,
      wheelDiameter: 165,
      enableOpacity: opacity,
      showColorCode: true,
      colorCodeHasColor: true,
      pickersEnabled: <ColorPickerType, bool>{
        ColorPickerType.wheel: true,
      },
      copyPasteBehavior: const ColorPickerCopyPasteBehavior(
        parseShortHexCode: true,
      ),
      actionButtons: const ColorPickerActionButtons(
        dialogActionButtons: true,
      ),
    ).showPickerDialog(
      context,
      barrierDismissible: false,
      constraints: BoxConstraints(
          minHeight: 480, minWidth: NavigationSvc.width(context) - 70, maxWidth: NavigationSvc.width(context) - 70),
    );
  }

  Widget colorBuilder(Color color, void Function(Color?) update) {
    if (deletingColors) {
      color = Color.fromRGBO(color.red, color.green, color.blue, color.opacity * 0.20);
    }

    return GestureDetector(
      child: Container(
        width: 40,
        height: 40,
        margin: const EdgeInsets.only(right: 10),
        decoration: BoxDecoration(
            color: color, shape: BoxShape.circle, border: Border.all(color: Colors.white.darkenPercent(10), width: 4)),
        child: deletingColors ? Icon(CupertinoIcons.delete, color: Colors.red[700]) : const SizedBox.shrink(),
      ),
      onTap: () async {
        if (deletingColors) {
          update(null);
          return;
        }
        if (!await showPickerDialog(color, update)) {
          update(color); // reset
        }
      },
    );
  }

  void updateFontName() {
    callPoster?.textMetadata.fontNameKey =
        availableFonts[poster.titleConfiguration.timeFontConfiguration.timeFontIdentifier]
                ?.name(poster.titleConfiguration.timeFontConfiguration.weight) ??
            ".SFUI-Medium";
  }

  void changePhoto() async {
    final res = await fp.FilePicker
        .pickFiles(withData: true, type: fp.FileType.custom, allowedExtensions: ['png', 'jpg', 'jpeg']);
    if (res == null) return;

    showDialog(
        context: Get.context!,
        useRootNavigator: false,
        builder: (BuildContext context) {
          return AlertDialog(
            backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
            title: Text(
              "Preparing poster...",
              style: context.theme.textTheme.titleLarge,
            ),
            content: Container(
              height: 70,
              child: Center(
                child: CircularProgressIndicator(
                  backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                  valueColor: AlwaysStoppedAnimation<Color>(context.theme.colorScheme.primary),
                ),
              ),
            ),
          );
        });
    try {
      fp.PlatformFile platformFile = res.files.first;

      var decodedImage = await decodeImageFromBytes(platformFile.bytes!);

      var vibrant = await getVibrantColor(decodedImage);

      var padding = (decodedImage.height.toDouble() * 0.35).ceil();

      var recorder = ui.PictureRecorder();
      var canvas = Canvas(recorder);
      canvas.drawImage(decodedImage, Offset(0, padding.toDouble()), Paint());

      var program = await FragmentProgram.fromAsset('shaders/vertical_blur_gradient.frag');
      var shader = program.fragmentShader();

      print(decodedImage.width.toDouble() / 800);
      shader.setFloat(0, decodedImage.width.toDouble());
      shader.setFloat(1, decodedImage.height.toDouble());
      shader.setFloat(2, padding.toDouble());
      shader.setFloat(3, decodedImage.height.toDouble() * 0.23);

      shader.setFloat(4, 0);
      shader.setFloat(5, 0);
      shader.setFloat(6, 0);

      shader.setImageSampler(0, decodedImage);
      canvas.drawRect(
          Rect.fromLTWH(0, 0, decodedImage.width.toDouble(), padding + (decodedImage.height.toDouble() * 0.23)),
          Paint()..shader = shader);

      var picture = recorder.endRecording();

      var paddedImage = await picture.toImage(decodedImage.width, decodedImage.height + padding);
      var paddedJpgBytes = await imageToJpeg(paddedImage);

      var assetUuid = uuid.v4().toUpperCase();

      var asset = api.PosterAsset(
          contents: api.PhotoPosterContents(
              version: 10,
              layers: [
                api.PhotoPosterLayer(
                    frame: api.PhotoPosterContentsFrame(
                        width: paddedImage.width.toDouble(),
                        height: paddedImage.height.toDouble(),
                        x: 0,
                        y: -padding.toDouble()),
                    filename: "portrait-layer_background-backfill.HEIC",
                    zPosition: 4.5,
                    identifier: "background-backfill"),
                api.PhotoPosterLayer(
                    frame: api.PhotoPosterContentsFrame(
                        width: paddedImage.width.toDouble(),
                        height: paddedImage.height.toDouble(),
                        x: 0,
                        y: -padding.toDouble()),
                    filename: "portrait-layer_background.HEIC",
                    zPosition: 5,
                    identifier: "background"),
              ],
              properties: api.PhotoPosterProperties(
                  portraitLayout: api.PhotoPosterLayout(
                    clockIntersection: 2,
                    deviceResolution: const api.PhotoPosterContentsSize(width: 1170, height: 2532),
                    visibleFrame: centeredAspectCrop(
                        imageWidth: decodedImage.width.toDouble(),
                        imageHeight: decodedImage.height.toDouble(),
                        targetAspectRatio: MediaQuery.sizeOf(Get.context!).aspectRatio),
                    timeFrame: const api.PhotoPosterContentsFrame(height: 0, width: 0, x: 0, y: 0),
                    clockLayerOrder: "ClockAboveForeground",
                    hasTopEdgeContact: false, // TODO
                    inactiveFrame: const api.PhotoPosterContentsFrame(height: 0, width: 0, x: 0, y: 0),
                    imageSize: api.PhotoPosterContentsSize(
                        width: decodedImage.width.toDouble(), height: decodedImage.height.toDouble()),
                    parallaxPadding: const api.PhotoPosterContentsSize(width: 0, height: 0),
                  ),
                  settlingEffectEnabled: false,
                  depthEnabled: false,
                  clockAreaLuminance: 0,
                  parallaxDisabled: false)),
          files: {
            "portrait-layer_background-backfill.HEIC": Uint8List(0),
            "portrait-layer_background.HEIC": Uint8List(0)
          },
          uuid: assetUuid);

      var newPoster = api.SimplifiedPoster(
        titleConfiguration: api.PRPosterTitleStyleConfiguration(
            alternateDateEnabled: false,
            contentsLuminence: 0,
            groupName: "PREditingLook",
            preferredTitleAlignment: 0,
            preferredTitleLayout: 0,
            timeFontConfiguration: poster.titleConfiguration.timeFontConfiguration,
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
                colors: [colorToUIColor(vibrant ?? Colors.black)],
                vibrant: true,
                supportsVariation: true,
                needsToResolveVariation: false)),
        type: api.PosterType.photo(assets: [asset]),
        role: poster.role,
      );

      var recorder2 = ui.PictureRecorder();
      var canvas2 = Canvas(recorder2);
      canvas2.drawImage(decodedImage, Offset(0, padding.toDouble()), Paint());

      canvas2.translate(0, padding.toDouble());
      canvas2.scale(1, -1); // flip
      canvas2.drawImage(decodedImage, Offset.zero, Paint());

      var picture2 = recorder2.endRecording();

      var paddedImage2 = await picture2.toImage(decodedImage.width, decodedImage.height + padding);
      var data = await paddedImage2.toByteData(format: ui.ImageByteFormat.png);

      await makeNewPoster();

      var file = PushSvc.fileForAsset(posterPath, asset, "portrait-layer_background-backfill.HEIC");
      await file.writeAsBytes(paddedJpgBytes);

      await MethodChannelSvc.invokeMethod("encode-heif", {"file": file.path, "output": file.path});

      var file2 = PushSvc.fileForAsset(posterPath, asset, "portrait-layer_background.HEIC");
      await file2.writeAsBytes(data!.buffer.asUint8List()); // format doesn't matter because we re-crop before upload

      var file3 = PushSvc.fileForAsset(posterPath, asset, "original-background.jpg");
      await file3.writeAsBytes(data.buffer.asUint8List());

      stopEditing();

      var layout = asset.contents.properties.portraitLayout;
      var matrix = Matrix4.identity();
      matrix.scale(MediaQuery.sizeOf(Get.context!).width / layout.visibleFrame.width);
      matrix.translate(-layout.visibleFrame.x,
          -(layout.imageSize.height - layout.visibleFrame.y - layout.visibleFrame.height + getPosterPadding(asset)), 0);
      cropController.value = matrix;

      setState(() {
        // we want the mirror to render all over, but we still want it to fade away when we get out of it
        backgroundFrame = api.PhotoPosterContentsFrame(
          width: decodedImage.width.toDouble(),
          height: decodedImage.height.toDouble(),
          x: 0,
          y: 0,
        );
        updatePoster(newPoster);
      });
    } catch (e, s) {
      Get.back();
      showSnackbar("Error", "Failed to update profile! $e");
      rethrow;
    }

    Get.back();
  }

  void updatePoster(api.SimplifiedPoster newPoster) {
    if (callPoster != null) {
      currentPoster = api.SimplifiedIncomingCallPoster(
        poster: newPoster,
        textMetadata: callPoster!.textMetadata,
        lowRes: Uint8List(0),
      );
    }
    if (transcriptPoster != null) {
      currentTranscriptPoster = api.SimplifiedTranscriptPoster(watch: transcriptPoster!.watch, poster: newPoster);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AnnotatedRegion<SystemUiOverlayStyle>(
        value: SystemUiOverlayStyle(
          systemNavigationBarColor: Colors.transparent, // navigation bar color
          systemNavigationBarIconBrightness: context.theme.colorScheme.brightness.opposite,
          statusBarColor: Colors.transparent, // status bar color
          statusBarIconBrightness: context.theme.colorScheme.brightness.opposite,
        ),
        child: Scaffold(
          backgroundColor: SettingsSvc.settings.windowEffect.value != WindowEffect.disabled
              ? Colors.transparent
              : context.theme.colorScheme.background,
          extendBodyBehindAppBar: true,
          body: Stack(
            fit: StackFit.expand,
            children: [
              if (poster.type is api.PosterType_Monogram)
                Container(
                    decoration: BoxDecoration(
                        gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        posterColorToColor((poster.type as api.PosterType_Monogram).data.topBackgroundColorDescription),
                        posterColorToColor((poster.type as api.PosterType_Monogram).data.backgroundColorDescription),
                      ],
                    )),
                    child: Center(
                      child: ShaderMask(
                        shaderCallback: (size) => LinearGradient(
                          begin: Alignment.topCenter,
                          end: Alignment.bottomCenter,
                          colors: [
                            saturateColor(posterColorToColor(
                                (poster.type as api.PosterType_Monogram).data.topBackgroundColorDescription)),
                            saturateColor(posterColorToColor(
                                (poster.type as api.PosterType_Monogram).data.backgroundColorDescription)),
                          ],
                        ).createShader(size),
                        blendMode: BlendMode.srcIn,
                        child: TextField(
                          style: const TextStyle(
                              color: Colors.white, fontSize: 200, fontWeight: FontWeight.w500, decorationThickness: 0),
                          controller: monogramController,
                          maxLength: 2,
                          textAlign: TextAlign.center,
                          decoration: const InputDecoration.collapsed(hintText: ""),
                          maxLines: 1,
                          buildCounter: (context,
                                  {required int currentLength, required bool isFocused, required int? maxLength}) =>
                              const SizedBox.shrink(),
                          inputFormatters: [UpperCaseTextFormatter()],
                          onChanged: (edit) {
                            var data = poster.type as api.PosterType_Monogram;
                            data.data.initials = edit;
                          },
                        ),
                      ),
                    )),
              if (poster.type is api.PosterType_Photo)
                buildViewer(
                    (poster.type as api.PosterType_Photo).assets[0],
                    (layer) =>
                        (poster.type as api.PosterType_Photo)
                                .assets[0]
                                .contents
                                .properties
                                .portraitLayout
                                .clockLayerOrder !=
                            "ClockAboveBackground" ||
                        !layer.identifier.startsWith("foreground")),
              if (callPoster != null)
                Positioned(
                    left: 16,
                    right: 16,
                    top: MediaQuery.of(context).viewPadding.top + 65,
                    child: GestureDetector(
                      onTap: () {
                        _drawerAnimationController.forward();
                      },
                      child: Container(
                        decoration: BoxDecoration(
                          border: Border.all(color: Colors.white.withAlpha(96), width: 4),
                          borderRadius: BorderRadius.circular(16.0),
                        ),
                        padding: const EdgeInsets.all(3),
                        child: posterText(
                            poster,
                            80,
                            widget.handle != null
                                ? widget.handle!.displayName.split(" ").first
                                : SettingsSvc.settings.firstName.value ?? "You"),
                      ),
                    )),
              if (poster.type is api.PosterType_Photo &&
                  (poster.type as api.PosterType_Photo).assets[0].contents.properties.portraitLayout.clockLayerOrder ==
                      "ClockAboveBackground")
                IgnorePointer(
                  child: buildViewer((poster.type as api.PosterType_Photo).assets[0],
                      (layer) => layer.identifier.startsWith("foreground")),
                ), // ignore because bottom layer will get them, and need to click on title
              Positioned(
                left: 15,
                top: MediaQuery.of(context).viewPadding.top,
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: context.theme.colorScheme.outline.withAlpha(96),
                    padding: const EdgeInsets.symmetric(vertical: 5, horizontal: 5),
                    tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                    elevation: 0.0,
                    minimumSize: Size.zero,
                  ),
                  onPressed: () async {
                    Get.back();
                  },
                  child: const Icon(CupertinoIcons.back, color: Colors.white),
                ),
              ),
              Positioned(
                  bottom: 12 + MediaQuery.of(context).padding.bottom,
                  left: 27,
                  child: Column(
                    children: [
                      if (poster.type is! api.PosterType_Monogram && transcriptPoster == null)
                        ElevatedButton(
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.transparent,
                            padding: const EdgeInsets.symmetric(vertical: 5, horizontal: 10),
                            tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                            elevation: 0.0,
                            minimumSize: Size.zero,
                          ),
                          onPressed: () async {
                            showDialog(
                                context: Get.context!,
                                useRootNavigator: false,
                                builder: (BuildContext context) {
                                  return AlertDialog(
                                    title: Text(
                                      "Change to Monogram?",
                                      style: context.theme.textTheme.titleLarge,
                                    ),
                                    backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                                    actions: <Widget>[
                                      TextButton(
                                        child: Text("Cancel",
                                            style: context.theme.textTheme.bodyLarge!
                                                .copyWith(color: context.theme.colorScheme.primary)),
                                        onPressed: () {
                                          Navigator.of(context).pop();
                                        },
                                      ),
                                      TextButton(
                                        child: Text("Yes",
                                            style: context.theme.textTheme.bodyLarge!
                                                .copyWith(color: context.theme.colorScheme.primary)),
                                        onPressed: () async {
                                          Navigator.of(context).pop();

                                          if (poster.type is api.PosterType_Photo) {
                                            stopEditing();
                                          }

                                          await makeNewPoster();

                                          var randomColor =
                                              Color((math.Random().nextDouble() * 0xFFFFFF).toInt()).withOpacity(1.0);
                                          var initials = widget.handle != null
                                              ? widget.handle!.initials ?? 'A'
                                              : "${SettingsSvc.settings.firstName.value?.substring(0, 1) ?? 'A'}${SettingsSvc.settings.lastName.value?.substring(0, 1) ?? ''}"
                                                  .toUpperCase();
                                          var newPoster = api.SimplifiedPoster(
                                            titleConfiguration: api.PRPosterTitleStyleConfiguration(
                                                alternateDateEnabled: false,
                                                contentsLuminence: 0,
                                                groupName: "PREditingLook",
                                                preferredTitleAlignment: 0,
                                                preferredTitleLayout: 0,
                                                timeFontConfiguration: poster.titleConfiguration.timeFontConfiguration,
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
                                                titleStyle:
                                                    api.PRPosterContentMaterialStyle.prPosterContentDiscreteColorsStyle(
                                                        variation: 0,
                                                        colors: [colorToUIColor(saturateColor(randomColor))],
                                                        vibrant: true,
                                                        supportsVariation: true,
                                                        needsToResolveVariation: false)),
                                            type: api.PosterType.monogram(
                                              data: api.MonogramData(
                                                  topBackgroundColorDescription: colorToPosterColor(randomColor),
                                                  backgroundColorDescription: colorToPosterColor(randomColor),
                                                  initials: initials,
                                                  monogramSupportedForName: true),
                                              background: colorToPosterColor(randomColor),
                                            ),
                                            role: poster.role,
                                          );
                                          monogramController.text = initials;
                                          setState(() {
                                            updatePoster(newPoster);
                                          });
                                        },
                                      ),
                                    ],
                                  );
                                });
                          },
                          child: const Icon(
                            CupertinoIcons.textformat,
                            color: Colors.white,
                            size: 32,
                          ),
                        ),
                      const SizedBox(
                        height: 15,
                      ),
                      ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.transparent,
                          padding: const EdgeInsets.symmetric(vertical: 5, horizontal: 10),
                          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                          elevation: 0.0,
                          minimumSize: Size.zero,
                        ),
                        onPressed: () async {
                          changePhoto();
                        },
                        child: const Icon(
                          CupertinoIcons.photo_on_rectangle,
                          color: Colors.white,
                          size: 32,
                        ),
                      ),
                    ],
                  )),
              if (poster.type is api.PosterType_Photo || poster.type is api.PosterType_Monogram)
                Positioned(
                  right: 15,
                  bottom: 12 + MediaQuery.of(context).padding.bottom,
                  child: ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: context.theme.colorScheme.bubble(context, true),
                      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 10),
                      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      elevation: 0.0,
                      minimumSize: Size.zero,
                    ),
                    onPressed: () async {
                      showDialog(
                          context: Get.context!,
                          useRootNavigator: false,
                          builder: (BuildContext context) {
                            return AlertDialog(
                              backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                              title: Text(
                                "Saving poster...",
                                style: context.theme.textTheme.titleLarge,
                              ),
                              content: Container(
                                height: 70,
                                child: Center(
                                  child: CircularProgressIndicator(
                                    backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                                    valueColor: AlwaysStoppedAnimation<Color>(context.theme.colorScheme.primary),
                                  ),
                                ),
                              ),
                            );
                          });
                      try {
                        if (poster.type is api.PosterType_Photo) {
                          var asset = (poster.type as api.PosterType_Photo).assets[0];
                          double topPadding = getPosterPadding(asset); // constant?

                          stopEditing();

                          var visible = getVisibleRect();
                          var padded = visible.inflate(10);

                          var images = await loadAssetImages(posterPath, asset);

                          var recorder = ui.PictureRecorder();
                          var canvas = Canvas(recorder);
                          canvas.translate(-padded.topLeft.dx, -(padded.topLeft.dy - topPadding));

                          drawAsset(images, canvas, asset, opacities);

                          var picture = recorder.endRecording();

                          var image = await picture.toImage(padded.width.round(), padded.height.round());
                          var jpgBytes = await imageToJpeg(image);

                          if (callPoster != null) {
                            var recorder = ui.PictureRecorder();
                            var canvas = Canvas(recorder);
                            var outputSize = const Size(410, 667);
                            applyCenterCropTransform(canvas: canvas, inputSize: visible.size, outputSize: outputSize);
                            canvas.translate(-visible.topLeft.dx, -(visible.topLeft.dy - topPadding));

                            drawAsset(images, canvas, asset, opacities);

                            var picture = recorder.endRecording();

                            var image = await picture.toImage(outputSize.width.round(), outputSize.height.round());
                            callPoster!.lowRes = await imageToJpeg(image);
                          }

                          if (transcriptPoster != null) {
                            var recorder = ui.PictureRecorder();
                            var canvas = Canvas(recorder);
                            var outputSize = const Size(820, 1003);
                            applyCenterCropTransform(canvas: canvas, inputSize: visible.size, outputSize: outputSize);
                            canvas.translate(-visible.topLeft.dx, -(visible.topLeft.dy - topPadding));

                            drawAsset(images, canvas, asset, opacities);

                            var picture = recorder.endRecording();

                            var image = await picture.toImage(outputSize.width.round(), outputSize.height.round());
                            transcriptPoster!.watch = api.WatchBackground(
                                isHighKey: false,
                                luminance: 0,
                                backgroundImageData:
                                    (await image.toByteData(format: ImageByteFormat.png))!.buffer.asUint8List(),
                                extensionIdentifier: "com.apple.PhotosUIPrivate.PhotosPosterProvider");
                          }

                          var layout = asset.contents.properties.portraitLayout;
                          layout.visibleFrame = api.PhotoPosterContentsFrame(
                              width: visible.width,
                              height: visible.height,
                              x: visible.topLeft.dx,
                              y: layout.imageSize.height - (visible.bottomLeft.dy - topPadding /* unpadded */));

                          var layer = asset.contents.layers.firstWhere((l) => l.identifier == "background");
                          layer.frame = api.PhotoPosterContentsFrame(
                              width: padded.width,
                              height: padded.height,
                              x: padded.topLeft.dx,
                              y: padded.topLeft.dy - topPadding);

                          var file = PushSvc.fileForAsset(posterPath, asset, layer.filename);
                          await file.writeAsBytes(jpgBytes);
                          if (File("${file.path}.png").existsSync()) {
                            File("${file.path}.png").deleteSync();
                          }

                          await MethodChannelSvc.invokeMethod("encode-heif", {
                            "file": file.path,
                            "output": file.path,
                          });

                          await FileImage(file).evict();
                        } else if (poster.type is api.PosterType_Monogram) {
                          var type = poster.type as api.PosterType_Monogram;

                          if (callPoster != null) {
                            var recorder = ui.PictureRecorder();
                            var canvas = Canvas(recorder);
                            var outputSize = const Size(410, 667);
                            drawMonogram(poster.type as api.PosterType_Monogram, outputSize, canvas);

                            var picture = recorder.endRecording();

                            var image = await picture.toImage(outputSize.width.round(), outputSize.height.round());
                            Uint8List thumbnail = await imageToJpeg(image);
                            callPoster!.lowRes = thumbnail;
                          }

                          if (transcriptPoster != null) {
                            var recorder = ui.PictureRecorder();
                            var canvas = Canvas(recorder);
                            var outputSize = const Size(820, 1003);
                            drawMonogram(poster.type as api.PosterType_Monogram, outputSize, canvas);

                            var picture = recorder.endRecording();

                            var image = await picture.toImage(outputSize.width.round(), outputSize.height.round());
                            transcriptPoster!.watch = api.WatchBackground(
                                isHighKey: false,
                                luminance: 0,
                                backgroundImageData:
                                    (await image.toByteData(format: ImageByteFormat.png))!.buffer.asUint8List(),
                                extensionIdentifier: "com.apple.ContactsUI.MonogramPosterExtension");
                          }

                          var profile = await drawMonogramProfile(type);
                          if ((widget.handle == null || widget.handle!.contactsV2.firstOrNull != null) &&
                              transcriptPoster == null) {
                            await showDialog(
                                context: Get.context!,
                                useRootNavigator: false,
                                builder: (BuildContext context) {
                                  return AlertDialog(
                                    title: Text(
                                      "Change profile photo?",
                                      style: context.theme.textTheme.titleLarge,
                                    ),
                                    content: Container(
                                      child: ClipRRect(
                                        child: Image.memory(profile),
                                        borderRadius: BorderRadius.circular(300),
                                      ),
                                      padding: const EdgeInsets.only(left: 16, top: 16, right: 16),
                                    ),
                                    backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                                    actions: <Widget>[
                                      TextButton(
                                        child: Text("Skip",
                                            style: context.theme.textTheme.bodyLarge!
                                                .copyWith(color: context.theme.colorScheme.primary)),
                                        onPressed: () {
                                          Navigator.of(context).pop();
                                        },
                                      ),
                                      TextButton(
                                        child: Text("Yes",
                                            style: context.theme.textTheme.bodyLarge!
                                                .copyWith(color: context.theme.colorScheme.primary)),
                                        onPressed: () async {
                                          Navigator.of(context).pop();

                                          if (widget.handle != null) {
                                            final contact = widget.handle!.contactsV2.firstOrNull;
                                            if (contact == null) {
                                              return;
                                            }
                                            final avatarPath = contact.avatarPath ??
                                                "${FilesystemSvc.contactAvatarsPath}/${contact.nativeContactId}-${profile.length}.jpg";
                                            final file = File(avatarPath);
                                            if (!(await file.exists())) {
                                              await file.create(recursive: true);
                                            }
                                            await file.writeAsBytes(profile);
                                            contact.avatarPath = file.path;
                                            Database.contactsV2.put(contact);
                                            if (widget.handle!.id != null) {
                                              ContactsSvcV2.notifyHandlesUpdated([widget.handle!.id!]);
                                            }
                                          } else {
                                            String appDocPath = FilesystemSvc.appDocDir.path;
                                            File file = File("$appDocPath/avatars/you/avatar-${profile.length}.jpg");
                                            if (!(await file.exists())) {
                                              await file.create(recursive: true);
                                            }
                                            await file.writeAsBytes(profile);
                                            SettingsSvc.settings.userAvatarPath.value = file.path;
                                            await SettingsSvc.settings.saveOneAsync("userAvatarPath");
                                          }
                                        },
                                      ),
                                    ],
                                  );
                                });
                          }
                        }

                        var save = callPoster != null
                            ? await api.parsePosterSave(poster: callPoster!)
                            : await api.transcriptPosterSave(poster: transcriptPoster!);
                        await File("$posterPath.jpg").writeAsBytes(save);

                        widget.posterEdited(posterPath);
                        ownedPosterPath = null;
                      } catch (e, s) {
                        Get.back();
                        showSnackbar("Error", "Failed to update profile! $e");
                        rethrow;
                      }

                      Get.back();

                      Get.back();
                    },
                    child: const Icon(
                      Icons.check,
                      color: Colors.white,
                    ),
                  ),
                ),
              if (poster.type is api.PosterType_Monogram)
                Positioned(
                  right: 0,
                  left: 0,
                  bottom: 12 + MediaQuery.of(context).padding.bottom,
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      GestureDetector(
                        child: Container(
                          width: 40,
                          height: 40,
                          decoration: BoxDecoration(
                              color: posterColorToColor(
                                  (poster.type as api.PosterType_Monogram).data.backgroundColorDescription),
                              shape: BoxShape.circle,
                              border: Border.all(color: Colors.white, width: 4)),
                        ),
                        onTap: () async {
                          var data = (poster.type as api.PosterType_Monogram).data;
                          var color = data.backgroundColorDescription;
                          var original = (poster.type as api.PosterType_Monogram).background;
                          if (!await showPickerDialog(
                              posterColorToColor(color),
                              (u) => setState(() {
                                    data.backgroundColorDescription = colorToPosterColor(
                                        u); // it is bugged; apple does not support gradients when receiving
                                    poster.type =
                                        api.PosterType.monogram(data: data, background: colorToPosterColor(u));
                                  }),
                              opacity: false)) {
                            data.backgroundColorDescription = color;
                            poster.type = api.PosterType.monogram(data: data, background: original);
                          }
                        },
                      ),
                      const SizedBox(
                        width: 10,
                      ),
                      GestureDetector(
                        child: Container(
                          width: 40,
                          height: 40,
                          decoration: BoxDecoration(
                              color: posterColorToColor(
                                  (poster.type as api.PosterType_Monogram).data.topBackgroundColorDescription),
                              shape: BoxShape.circle,
                              border: Border.all(color: Colors.white, width: 4)),
                        ),
                        onTap: () async {
                          var data = (poster.type as api.PosterType_Monogram).data;
                          var topColor = data.topBackgroundColorDescription;
                          var original = (poster.type as api.PosterType_Monogram).background;
                          if (!await showPickerDialog(
                              posterColorToColor(topColor),
                              (u) => setState(() {
                                    data.topBackgroundColorDescription = colorToPosterColor(u);
                                    poster.type =
                                        api.PosterType.monogram(data: data, background: colorToPosterColor(u));
                                  }),
                              opacity: false)) {
                            data.topBackgroundColorDescription = topColor;
                            poster.type = api.PosterType.monogram(data: data, background: original);
                          }
                        },
                      ),
                    ],
                  ),
                ),
              Positioned(
                bottom: 0,
                left: 0,
                right: 0,
                child: SlideTransition(
                    position: _animation,
                    child: ClipRRect(
                        borderRadius:
                            const BorderRadius.only(topLeft: Radius.circular(32), topRight: Radius.circular(32)),
                        child: BackdropFilter(
                          filter: ImageFilter.compose(
                              outer: ImageFilter.blur(sigmaX: 30, sigmaY: 30),
                              inner: ColorFilter.matrix(
                                CupertinoTheme.maybeBrightnessOf(context) == Brightness.dark ? darkMatrix : lightMatrix,
                              )),
                          child: Container(
                              decoration: BoxDecoration(
                                color: context.theme.colorScheme.surfaceContainerHighest.withOpacity(0.5),
                                borderRadius: const BorderRadius.only(
                                    topLeft: Radius.circular(32), topRight: Radius.circular(32)),
                              ),
                              clipBehavior: Clip.hardEdge,
                              child: Stack(
                                children: [
                                  Column(
                                    crossAxisAlignment: CrossAxisAlignment.stretch,
                                    children: [
                                      const SizedBox(
                                        height: 16,
                                      ),
                                      Text(
                                        "Font & Style",
                                        style: context.theme.textTheme.titleMedium?.copyWith(fontSize: 18),
                                        textAlign: TextAlign.center,
                                      ),
                                      const SizedBox(
                                        height: 30,
                                      ),
                                      SingleChildScrollView(
                                          scrollDirection: Axis.horizontal,
                                          physics: const CustomBouncingScrollPhysics(),
                                          padding: const EdgeInsets.symmetric(horizontal: 25),
                                          child: Row(
                                            children: availableFonts.values
                                                .map((font) => GestureDetector(
                                                      child: Container(
                                                        decoration: BoxDecoration(
                                                          border: Border.all(
                                                              color: poster.titleConfiguration.timeFontConfiguration
                                                                          .timeFontIdentifier ==
                                                                      font.timeIdentifier
                                                                  ? context.theme.colorScheme.bubble(context, true)
                                                                  : Colors.transparent,
                                                              width: 4),
                                                          borderRadius: BorderRadius.circular(16.0),
                                                        ),
                                                        width: 85,
                                                        padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 8),
                                                        margin: const EdgeInsets.symmetric(horizontal: 15),
                                                        child: Text(
                                                          "Aa",
                                                          style: font.font.copyWith(
                                                              fontSize: 45,
                                                              height: 1,
                                                              fontVariations: [const FontVariation.weight(600)]),
                                                          textAlign: TextAlign.center,
                                                        ),
                                                      ),
                                                      onTap: () {
                                                        setState(() {
                                                          poster.titleConfiguration.timeFontConfiguration
                                                              .timeFontIdentifier = font.timeIdentifier;
                                                          updateFontName();
                                                        });
                                                      },
                                                    ))
                                                .toList(),
                                          )),
                                      const SizedBox(
                                        height: 15,
                                      ),
                                      Slider(
                                        activeColor: context.theme.colorScheme.outline.oppositeLightenOrDarken(20),
                                        secondaryActiveColor: context.theme.colorScheme.outline.withOpacity(0.6),
                                        thumbColor: context.theme.colorScheme.outline,
                                        inactiveColor: context.theme.colorScheme.outline.withOpacity(0.2),
                                        value: poster.titleConfiguration.timeFontConfiguration.weight,
                                        onChanged: (val) {
                                          setState(() {
                                            poster.titleConfiguration.timeFontConfiguration.weight = val;
                                            callPoster?.textMetadata.fontWeightKey = val.roundToDouble();
                                            updateFontName();
                                          });
                                        },
                                        min: 100,
                                        max: 900,
                                        mouseCursor: SystemMouseCursors.click,
                                      ),
                                      const SizedBox(
                                        height: 10,
                                      ),
                                      SingleChildScrollView(
                                        scrollDirection: Axis.horizontal,
                                        physics: const CustomBouncingScrollPhysics(),
                                        padding: const EdgeInsets.symmetric(horizontal: 25),
                                        child: Row(children: (() {
                                          List<api.UIColor> colors = [];
                                          var config = poster.titleConfiguration.titleStyle!;
                                          Widget editColors = GestureDetector(
                                            child: Container(
                                              width: 40,
                                              height: 40,
                                              margin: const EdgeInsets.only(right: 10),
                                              decoration: BoxDecoration(
                                                color: context.theme.colorScheme.outline.withAlpha(96),
                                                shape: BoxShape.circle,
                                              ),
                                              child: const Icon(
                                                CupertinoIcons.add,
                                              ),
                                            ),
                                            onTap: () async {
                                              var color = Colors.black;
                                              if (!await showPickerDialog(color, (c) => color = c)) return;
                                              colors.add(colorToUIColor(color));
                                              List<double> locations = [];
                                              for (int i = 0; i < colors.length; i++) {
                                                locations.add(i / (colors.length - 1));
                                              }
                                              setState(() {
                                                poster.titleConfiguration.titleStyle =
                                                    api.PRPosterContentMaterialStyle.prPosterContentGradientStyle(
                                                  colors: colors,
                                                  gradientType: 0,
                                                  startPoint: "{0, 0.5}",
                                                  endPoint: "{1, 0.5}",
                                                  locations: Float64List.fromList(locations),
                                                );
                                              });
                                            },
                                          );
                                          if (config
                                              is api.PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyle) {
                                            colors = [...config.colors];
                                            return [
                                              colorBuilder(uiColorToColor(colors.first), (newColor) {
                                                setState(() {
                                                  poster.titleConfiguration.titleStyle =
                                                      api.PRPosterContentMaterialStyle
                                                          .prPosterContentDiscreteColorsStyle(
                                                              variation: 0,
                                                              colors: [colorToUIColor(newColor!)],
                                                              vibrant: true,
                                                              supportsVariation: true,
                                                              needsToResolveVariation: false);
                                                });
                                              }),
                                              editColors
                                            ];
                                          }
                                          if (config is api.PRPosterContentMaterialStyle_PRPosterContentGradientStyle) {
                                            colors = [...config.colors];
                                            var myList = colors.asMap().entries.map<Widget>((entry) {
                                              return colorBuilder(uiColorToColor(entry.value), (newColor) {
                                                setState(() {
                                                  var currentLocations = config.locations;
                                                  if (newColor == null) {
                                                    colors.removeAt(entry.key);
                                                    List<double> locations = [];
                                                    for (int i = 0; i < colors.length; i++) {
                                                      locations.add(i / (colors.length - 1));
                                                    }
                                                    currentLocations = Float64List.fromList(locations);
                                                    if (colors.length == 1) {
                                                      deletingColors = false;
                                                      // convert to discrte
                                                      poster.titleConfiguration.titleStyle =
                                                          api.PRPosterContentMaterialStyle
                                                              .prPosterContentDiscreteColorsStyle(
                                                                  variation: 0,
                                                                  colors: colors,
                                                                  vibrant: true,
                                                                  supportsVariation: true,
                                                                  needsToResolveVariation: false);
                                                      return;
                                                    }
                                                  } else {
                                                    // set specific color
                                                    colors[entry.key] = colorToUIColor(newColor);
                                                  }
                                                  poster.titleConfiguration.titleStyle =
                                                      api.PRPosterContentMaterialStyle.prPosterContentGradientStyle(
                                                          colors: colors,
                                                          gradientType: config.gradientType,
                                                          startPoint: config.startPoint,
                                                          endPoint: config.endPoint,
                                                          locations: currentLocations);
                                                });
                                              });
                                            }).toList();
                                            myList.add(editColors);
                                            if (colors.length > 1) {
                                              myList.add(GestureDetector(
                                                child: Container(
                                                  width: 40,
                                                  height: 40,
                                                  margin: const EdgeInsets.only(right: 10),
                                                  decoration: BoxDecoration(
                                                    color: deletingColors
                                                        ? Colors.white
                                                        : context.theme.colorScheme.outline.withAlpha(96),
                                                    shape: BoxShape.circle,
                                                  ),
                                                  child: Icon(
                                                    Icons.clear,
                                                    color: deletingColors ? Colors.red[700] : null,
                                                  ),
                                                ),
                                                onTap: () async {
                                                  setState(() {
                                                    deletingColors = !deletingColors;
                                                  });
                                                },
                                              ));
                                              myList.add(GestureDetector(
                                                child: Container(
                                                  width: 40,
                                                  height: 40,
                                                  margin: const EdgeInsets.only(right: 10),
                                                  decoration: BoxDecoration(
                                                    color: deletingColors
                                                        ? Colors.white
                                                        : context.theme.colorScheme.outline.withAlpha(96),
                                                    shape: BoxShape.circle,
                                                  ),
                                                  child: Icon(
                                                      config.startPoint == "{0, 0.5}" && config.endPoint == "{1, 0.5}"
                                                          ? CupertinoIcons.arrow_right
                                                          : config.startPoint == "{0, 0}" && config.endPoint == "{1, 1}"
                                                              ? CupertinoIcons.arrow_down_right
                                                              : config.startPoint == "{0.5, 0}" &&
                                                                      config.endPoint == "{0.5, 1}"
                                                                  ? CupertinoIcons.arrow_down
                                                                  : config.startPoint == "{0, 1}" &&
                                                                          config.endPoint == "{1, 0}"
                                                                      ? CupertinoIcons.arrow_up_right
                                                                      : CupertinoIcons.question),
                                                ),
                                                onTap: () async {
                                                  String startPoint = "{0, 0.5}";
                                                  String endPoint = "{1, 0.5}";

                                                  if (config.startPoint == "{0, 0.5}" &&
                                                      config.endPoint == "{1, 0.5}") {
                                                    startPoint = "{0, 0}";
                                                    endPoint = "{1, 1}";
                                                  } else if (config.startPoint == "{0, 0}" &&
                                                      config.endPoint == "{1, 1}") {
                                                    startPoint = "{0.5, 0}";
                                                    endPoint = "{0.5, 1}";
                                                  } else if (config.startPoint == "{0.5, 0}" &&
                                                      config.endPoint == "{0.5, 1}") {
                                                    startPoint = "{0, 1}";
                                                    endPoint = "{1, 0}";
                                                  } // last case is initial

                                                  setState(() {
                                                    poster.titleConfiguration.titleStyle =
                                                        api.PRPosterContentMaterialStyle.prPosterContentGradientStyle(
                                                      colors: config.colors,
                                                      gradientType: config.gradientType,
                                                      startPoint: startPoint,
                                                      endPoint: endPoint,
                                                      locations: config.locations,
                                                    );
                                                  });
                                                },
                                              ));
                                            }
                                            return myList;
                                          }
                                          if (config
                                              is api.PRPosterContentMaterialStyle_PRPosterContentVibrantMaterialStyle) {
                                            colors = [poster.titleConfiguration.titleColor.color];
                                            return [
                                              colorBuilder(uiColorToColor(poster.titleConfiguration.titleColor.color),
                                                  (newColor) {
                                                setState(() {
                                                  poster.titleConfiguration.titleStyle =
                                                      api.PRPosterContentMaterialStyle
                                                          .prPosterContentDiscreteColorsStyle(
                                                              variation: 0,
                                                              colors: [colorToUIColor(newColor!)],
                                                              vibrant: true,
                                                              supportsVariation: true,
                                                              needsToResolveVariation: false);
                                                });
                                              }),
                                              editColors
                                            ];
                                          }
                                          return <Widget>[];
                                        })()),
                                      ),
                                      SizedBox(
                                        height: 20 + MediaQuery.of(context).padding.bottom,
                                      ),
                                    ],
                                  ),
                                  Positioned(
                                    right: 16,
                                    top: 16,
                                    child: ElevatedButton(
                                      style: ElevatedButton.styleFrom(
                                        backgroundColor: context.theme.colorScheme.outline.withAlpha(48),
                                        padding: const EdgeInsets.all(5),
                                        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                                        elevation: 0.0,
                                        minimumSize: Size.zero,
                                      ),
                                      onPressed: () async {
                                        print("here");
                                        _drawerAnimationController.reverse();
                                      },
                                      child: Icon(
                                        Icons.clear,
                                        color: context.theme.colorScheme.outline,
                                        size: 22,
                                      ),
                                    ),
                                  ),
                                ],
                              )),
                        ))),
              ),
            ],
          ),
        ));
  }
}
