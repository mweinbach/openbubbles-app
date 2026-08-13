// GENERATED CODE - DO NOT MODIFY BY HAND
// coverage:ignore-file
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'api.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

// dart format off
T _$identity<T>(T value) => value;

/// @nodoc
mixin _$AttachmentType {
  Object get field0;

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is AttachmentType &&
            const DeepCollectionEquality().equals(other.field0, field0));
  }

  @override
  int get hashCode =>
      Object.hash(runtimeType, const DeepCollectionEquality().hash(field0));

  @override
  String toString() {
    return 'AttachmentType(field0: $field0)';
  }
}

/// @nodoc
class $AttachmentTypeCopyWith<$Res> {
  $AttachmentTypeCopyWith(AttachmentType _, $Res Function(AttachmentType) __);
}

/// Adds pattern-matching-related methods to [AttachmentType].
extension AttachmentTypePatterns on AttachmentType {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(AttachmentType_Inline value)? inline,
    TResult Function(AttachmentType_MMCS value)? mmcs,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case AttachmentType_Inline() when inline != null:
        return inline(_that);
      case AttachmentType_MMCS() when mmcs != null:
        return mmcs(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(AttachmentType_Inline value) inline,
    required TResult Function(AttachmentType_MMCS value) mmcs,
  }) {
    final _that = this;
    switch (_that) {
      case AttachmentType_Inline():
        return inline(_that);
      case AttachmentType_MMCS():
        return mmcs(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(AttachmentType_Inline value)? inline,
    TResult? Function(AttachmentType_MMCS value)? mmcs,
  }) {
    final _that = this;
    switch (_that) {
      case AttachmentType_Inline() when inline != null:
        return inline(_that);
      case AttachmentType_MMCS() when mmcs != null:
        return mmcs(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function(Uint8List field0)? inline,
    TResult Function(MMCSFile field0)? mmcs,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case AttachmentType_Inline() when inline != null:
        return inline(_that.field0);
      case AttachmentType_MMCS() when mmcs != null:
        return mmcs(_that.field0);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function(Uint8List field0) inline,
    required TResult Function(MMCSFile field0) mmcs,
  }) {
    final _that = this;
    switch (_that) {
      case AttachmentType_Inline():
        return inline(_that.field0);
      case AttachmentType_MMCS():
        return mmcs(_that.field0);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function(Uint8List field0)? inline,
    TResult? Function(MMCSFile field0)? mmcs,
  }) {
    final _that = this;
    switch (_that) {
      case AttachmentType_Inline() when inline != null:
        return inline(_that.field0);
      case AttachmentType_MMCS() when mmcs != null:
        return mmcs(_that.field0);
      case _:
        return null;
    }
  }
}

/// @nodoc

class AttachmentType_Inline extends AttachmentType {
  const AttachmentType_Inline(this.field0) : super._();

  @override
  final Uint8List field0;

  /// Create a copy of AttachmentType
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $AttachmentType_InlineCopyWith<AttachmentType_Inline> get copyWith =>
      _$AttachmentType_InlineCopyWithImpl<AttachmentType_Inline>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is AttachmentType_Inline &&
            const DeepCollectionEquality().equals(other.field0, field0));
  }

  @override
  int get hashCode =>
      Object.hash(runtimeType, const DeepCollectionEquality().hash(field0));

  @override
  String toString() {
    return 'AttachmentType.inline(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $AttachmentType_InlineCopyWith<$Res>
    implements $AttachmentTypeCopyWith<$Res> {
  factory $AttachmentType_InlineCopyWith(AttachmentType_Inline value,
          $Res Function(AttachmentType_Inline) _then) =
      _$AttachmentType_InlineCopyWithImpl;
  @useResult
  $Res call({Uint8List field0});
}

/// @nodoc
class _$AttachmentType_InlineCopyWithImpl<$Res>
    implements $AttachmentType_InlineCopyWith<$Res> {
  _$AttachmentType_InlineCopyWithImpl(this._self, this._then);

  final AttachmentType_Inline _self;
  final $Res Function(AttachmentType_Inline) _then;

  /// Create a copy of AttachmentType
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(AttachmentType_Inline(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as Uint8List,
    ));
  }
}

/// @nodoc

class AttachmentType_MMCS extends AttachmentType {
  const AttachmentType_MMCS(this.field0) : super._();

  @override
  final MMCSFile field0;

  /// Create a copy of AttachmentType
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $AttachmentType_MMCSCopyWith<AttachmentType_MMCS> get copyWith =>
      _$AttachmentType_MMCSCopyWithImpl<AttachmentType_MMCS>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is AttachmentType_MMCS &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'AttachmentType.mmcs(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $AttachmentType_MMCSCopyWith<$Res>
    implements $AttachmentTypeCopyWith<$Res> {
  factory $AttachmentType_MMCSCopyWith(
          AttachmentType_MMCS value, $Res Function(AttachmentType_MMCS) _then) =
      _$AttachmentType_MMCSCopyWithImpl;
  @useResult
  $Res call({MMCSFile field0});
}

/// @nodoc
class _$AttachmentType_MMCSCopyWithImpl<$Res>
    implements $AttachmentType_MMCSCopyWith<$Res> {
  _$AttachmentType_MMCSCopyWithImpl(this._self, this._then);

  final AttachmentType_MMCS _self;
  final $Res Function(AttachmentType_MMCS) _then;

  /// Create a copy of AttachmentType
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(AttachmentType_MMCS(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as MMCSFile,
    ));
  }
}

/// @nodoc
mixin _$BalloonLayout {
  String get imageSubtitle;
  String get imageTitle;
  String get caption;
  String get secondarySubcaption;
  String get tertiarySubcaption;
  String get subcaption;
  NSDictionaryClass get class_;

  /// Create a copy of BalloonLayout
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $BalloonLayoutCopyWith<BalloonLayout> get copyWith =>
      _$BalloonLayoutCopyWithImpl<BalloonLayout>(
          this as BalloonLayout, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is BalloonLayout &&
            (identical(other.imageSubtitle, imageSubtitle) ||
                other.imageSubtitle == imageSubtitle) &&
            (identical(other.imageTitle, imageTitle) ||
                other.imageTitle == imageTitle) &&
            (identical(other.caption, caption) || other.caption == caption) &&
            (identical(other.secondarySubcaption, secondarySubcaption) ||
                other.secondarySubcaption == secondarySubcaption) &&
            (identical(other.tertiarySubcaption, tertiarySubcaption) ||
                other.tertiarySubcaption == tertiarySubcaption) &&
            (identical(other.subcaption, subcaption) ||
                other.subcaption == subcaption) &&
            (identical(other.class_, class_) || other.class_ == class_));
  }

  @override
  int get hashCode => Object.hash(runtimeType, imageSubtitle, imageTitle,
      caption, secondarySubcaption, tertiarySubcaption, subcaption, class_);

  @override
  String toString() {
    return 'BalloonLayout(imageSubtitle: $imageSubtitle, imageTitle: $imageTitle, caption: $caption, secondarySubcaption: $secondarySubcaption, tertiarySubcaption: $tertiarySubcaption, subcaption: $subcaption, class_: $class_)';
  }
}

/// @nodoc
abstract mixin class $BalloonLayoutCopyWith<$Res> {
  factory $BalloonLayoutCopyWith(
          BalloonLayout value, $Res Function(BalloonLayout) _then) =
      _$BalloonLayoutCopyWithImpl;
  @useResult
  $Res call(
      {String imageSubtitle,
      String imageTitle,
      String caption,
      String secondarySubcaption,
      String tertiarySubcaption,
      String subcaption,
      NSDictionaryClass class_});
}

/// @nodoc
class _$BalloonLayoutCopyWithImpl<$Res>
    implements $BalloonLayoutCopyWith<$Res> {
  _$BalloonLayoutCopyWithImpl(this._self, this._then);

  final BalloonLayout _self;
  final $Res Function(BalloonLayout) _then;

  /// Create a copy of BalloonLayout
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? imageSubtitle = null,
    Object? imageTitle = null,
    Object? caption = null,
    Object? secondarySubcaption = null,
    Object? tertiarySubcaption = null,
    Object? subcaption = null,
    Object? class_ = null,
  }) {
    return _then(_self.copyWith(
      imageSubtitle: null == imageSubtitle
          ? _self.imageSubtitle
          : imageSubtitle // ignore: cast_nullable_to_non_nullable
              as String,
      imageTitle: null == imageTitle
          ? _self.imageTitle
          : imageTitle // ignore: cast_nullable_to_non_nullable
              as String,
      caption: null == caption
          ? _self.caption
          : caption // ignore: cast_nullable_to_non_nullable
              as String,
      secondarySubcaption: null == secondarySubcaption
          ? _self.secondarySubcaption
          : secondarySubcaption // ignore: cast_nullable_to_non_nullable
              as String,
      tertiarySubcaption: null == tertiarySubcaption
          ? _self.tertiarySubcaption
          : tertiarySubcaption // ignore: cast_nullable_to_non_nullable
              as String,
      subcaption: null == subcaption
          ? _self.subcaption
          : subcaption // ignore: cast_nullable_to_non_nullable
              as String,
      class_: null == class_
          ? _self.class_
          : class_ // ignore: cast_nullable_to_non_nullable
              as NSDictionaryClass,
    ));
  }
}

/// Adds pattern-matching-related methods to [BalloonLayout].
extension BalloonLayoutPatterns on BalloonLayout {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(BalloonLayout_TemplateLayout value)? templateLayout,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case BalloonLayout_TemplateLayout() when templateLayout != null:
        return templateLayout(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(BalloonLayout_TemplateLayout value)
        templateLayout,
  }) {
    final _that = this;
    switch (_that) {
      case BalloonLayout_TemplateLayout():
        return templateLayout(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(BalloonLayout_TemplateLayout value)? templateLayout,
  }) {
    final _that = this;
    switch (_that) {
      case BalloonLayout_TemplateLayout() when templateLayout != null:
        return templateLayout(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function(
            String imageSubtitle,
            String imageTitle,
            String caption,
            String secondarySubcaption,
            String tertiarySubcaption,
            String subcaption,
            NSDictionaryClass class_)?
        templateLayout,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case BalloonLayout_TemplateLayout() when templateLayout != null:
        return templateLayout(
            _that.imageSubtitle,
            _that.imageTitle,
            _that.caption,
            _that.secondarySubcaption,
            _that.tertiarySubcaption,
            _that.subcaption,
            _that.class_);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function(
            String imageSubtitle,
            String imageTitle,
            String caption,
            String secondarySubcaption,
            String tertiarySubcaption,
            String subcaption,
            NSDictionaryClass class_)
        templateLayout,
  }) {
    final _that = this;
    switch (_that) {
      case BalloonLayout_TemplateLayout():
        return templateLayout(
            _that.imageSubtitle,
            _that.imageTitle,
            _that.caption,
            _that.secondarySubcaption,
            _that.tertiarySubcaption,
            _that.subcaption,
            _that.class_);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function(
            String imageSubtitle,
            String imageTitle,
            String caption,
            String secondarySubcaption,
            String tertiarySubcaption,
            String subcaption,
            NSDictionaryClass class_)?
        templateLayout,
  }) {
    final _that = this;
    switch (_that) {
      case BalloonLayout_TemplateLayout() when templateLayout != null:
        return templateLayout(
            _that.imageSubtitle,
            _that.imageTitle,
            _that.caption,
            _that.secondarySubcaption,
            _that.tertiarySubcaption,
            _that.subcaption,
            _that.class_);
      case _:
        return null;
    }
  }
}

/// @nodoc

class BalloonLayout_TemplateLayout extends BalloonLayout {
  const BalloonLayout_TemplateLayout(
      {required this.imageSubtitle,
      required this.imageTitle,
      required this.caption,
      required this.secondarySubcaption,
      required this.tertiarySubcaption,
      required this.subcaption,
      required this.class_})
      : super._();

  @override
  final String imageSubtitle;
  @override
  final String imageTitle;
  @override
  final String caption;
  @override
  final String secondarySubcaption;
  @override
  final String tertiarySubcaption;
  @override
  final String subcaption;
  @override
  final NSDictionaryClass class_;

  /// Create a copy of BalloonLayout
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $BalloonLayout_TemplateLayoutCopyWith<BalloonLayout_TemplateLayout>
      get copyWith => _$BalloonLayout_TemplateLayoutCopyWithImpl<
          BalloonLayout_TemplateLayout>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is BalloonLayout_TemplateLayout &&
            (identical(other.imageSubtitle, imageSubtitle) ||
                other.imageSubtitle == imageSubtitle) &&
            (identical(other.imageTitle, imageTitle) ||
                other.imageTitle == imageTitle) &&
            (identical(other.caption, caption) || other.caption == caption) &&
            (identical(other.secondarySubcaption, secondarySubcaption) ||
                other.secondarySubcaption == secondarySubcaption) &&
            (identical(other.tertiarySubcaption, tertiarySubcaption) ||
                other.tertiarySubcaption == tertiarySubcaption) &&
            (identical(other.subcaption, subcaption) ||
                other.subcaption == subcaption) &&
            (identical(other.class_, class_) || other.class_ == class_));
  }

  @override
  int get hashCode => Object.hash(runtimeType, imageSubtitle, imageTitle,
      caption, secondarySubcaption, tertiarySubcaption, subcaption, class_);

  @override
  String toString() {
    return 'BalloonLayout.templateLayout(imageSubtitle: $imageSubtitle, imageTitle: $imageTitle, caption: $caption, secondarySubcaption: $secondarySubcaption, tertiarySubcaption: $tertiarySubcaption, subcaption: $subcaption, class_: $class_)';
  }
}

/// @nodoc
abstract mixin class $BalloonLayout_TemplateLayoutCopyWith<$Res>
    implements $BalloonLayoutCopyWith<$Res> {
  factory $BalloonLayout_TemplateLayoutCopyWith(
          BalloonLayout_TemplateLayout value,
          $Res Function(BalloonLayout_TemplateLayout) _then) =
      _$BalloonLayout_TemplateLayoutCopyWithImpl;
  @override
  @useResult
  $Res call(
      {String imageSubtitle,
      String imageTitle,
      String caption,
      String secondarySubcaption,
      String tertiarySubcaption,
      String subcaption,
      NSDictionaryClass class_});
}

/// @nodoc
class _$BalloonLayout_TemplateLayoutCopyWithImpl<$Res>
    implements $BalloonLayout_TemplateLayoutCopyWith<$Res> {
  _$BalloonLayout_TemplateLayoutCopyWithImpl(this._self, this._then);

  final BalloonLayout_TemplateLayout _self;
  final $Res Function(BalloonLayout_TemplateLayout) _then;

  /// Create a copy of BalloonLayout
  /// with the given fields replaced by the non-null parameter values.
  @override
  @pragma('vm:prefer-inline')
  $Res call({
    Object? imageSubtitle = null,
    Object? imageTitle = null,
    Object? caption = null,
    Object? secondarySubcaption = null,
    Object? tertiarySubcaption = null,
    Object? subcaption = null,
    Object? class_ = null,
  }) {
    return _then(BalloonLayout_TemplateLayout(
      imageSubtitle: null == imageSubtitle
          ? _self.imageSubtitle
          : imageSubtitle // ignore: cast_nullable_to_non_nullable
              as String,
      imageTitle: null == imageTitle
          ? _self.imageTitle
          : imageTitle // ignore: cast_nullable_to_non_nullable
              as String,
      caption: null == caption
          ? _self.caption
          : caption // ignore: cast_nullable_to_non_nullable
              as String,
      secondarySubcaption: null == secondarySubcaption
          ? _self.secondarySubcaption
          : secondarySubcaption // ignore: cast_nullable_to_non_nullable
              as String,
      tertiarySubcaption: null == tertiarySubcaption
          ? _self.tertiarySubcaption
          : tertiarySubcaption // ignore: cast_nullable_to_non_nullable
              as String,
      subcaption: null == subcaption
          ? _self.subcaption
          : subcaption // ignore: cast_nullable_to_non_nullable
              as String,
      class_: null == class_
          ? _self.class_
          : class_ // ignore: cast_nullable_to_non_nullable
              as NSDictionaryClass,
    ));
  }
}

/// @nodoc
mixin _$DeleteTarget {
  Object get field0;

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is DeleteTarget &&
            const DeepCollectionEquality().equals(other.field0, field0));
  }

  @override
  int get hashCode =>
      Object.hash(runtimeType, const DeepCollectionEquality().hash(field0));

  @override
  String toString() {
    return 'DeleteTarget(field0: $field0)';
  }
}

/// @nodoc
class $DeleteTargetCopyWith<$Res> {
  $DeleteTargetCopyWith(DeleteTarget _, $Res Function(DeleteTarget) __);
}

/// Adds pattern-matching-related methods to [DeleteTarget].
extension DeleteTargetPatterns on DeleteTarget {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(DeleteTarget_Chat value)? chat,
    TResult Function(DeleteTarget_Messages value)? messages,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case DeleteTarget_Chat() when chat != null:
        return chat(_that);
      case DeleteTarget_Messages() when messages != null:
        return messages(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(DeleteTarget_Chat value) chat,
    required TResult Function(DeleteTarget_Messages value) messages,
  }) {
    final _that = this;
    switch (_that) {
      case DeleteTarget_Chat():
        return chat(_that);
      case DeleteTarget_Messages():
        return messages(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(DeleteTarget_Chat value)? chat,
    TResult? Function(DeleteTarget_Messages value)? messages,
  }) {
    final _that = this;
    switch (_that) {
      case DeleteTarget_Chat() when chat != null:
        return chat(_that);
      case DeleteTarget_Messages() when messages != null:
        return messages(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function(OperatedChat field0)? chat,
    TResult Function(List<String> field0)? messages,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case DeleteTarget_Chat() when chat != null:
        return chat(_that.field0);
      case DeleteTarget_Messages() when messages != null:
        return messages(_that.field0);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function(OperatedChat field0) chat,
    required TResult Function(List<String> field0) messages,
  }) {
    final _that = this;
    switch (_that) {
      case DeleteTarget_Chat():
        return chat(_that.field0);
      case DeleteTarget_Messages():
        return messages(_that.field0);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function(OperatedChat field0)? chat,
    TResult? Function(List<String> field0)? messages,
  }) {
    final _that = this;
    switch (_that) {
      case DeleteTarget_Chat() when chat != null:
        return chat(_that.field0);
      case DeleteTarget_Messages() when messages != null:
        return messages(_that.field0);
      case _:
        return null;
    }
  }
}

/// @nodoc

class DeleteTarget_Chat extends DeleteTarget {
  const DeleteTarget_Chat(this.field0) : super._();

  @override
  final OperatedChat field0;

  /// Create a copy of DeleteTarget
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $DeleteTarget_ChatCopyWith<DeleteTarget_Chat> get copyWith =>
      _$DeleteTarget_ChatCopyWithImpl<DeleteTarget_Chat>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is DeleteTarget_Chat &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'DeleteTarget.chat(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $DeleteTarget_ChatCopyWith<$Res>
    implements $DeleteTargetCopyWith<$Res> {
  factory $DeleteTarget_ChatCopyWith(
          DeleteTarget_Chat value, $Res Function(DeleteTarget_Chat) _then) =
      _$DeleteTarget_ChatCopyWithImpl;
  @useResult
  $Res call({OperatedChat field0});
}

/// @nodoc
class _$DeleteTarget_ChatCopyWithImpl<$Res>
    implements $DeleteTarget_ChatCopyWith<$Res> {
  _$DeleteTarget_ChatCopyWithImpl(this._self, this._then);

  final DeleteTarget_Chat _self;
  final $Res Function(DeleteTarget_Chat) _then;

  /// Create a copy of DeleteTarget
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(DeleteTarget_Chat(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as OperatedChat,
    ));
  }
}

/// @nodoc

class DeleteTarget_Messages extends DeleteTarget {
  const DeleteTarget_Messages(final List<String> field0)
      : _field0 = field0,
        super._();

  final List<String> _field0;
  @override
  List<String> get field0 {
    if (_field0 is EqualUnmodifiableListView) return _field0;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(_field0);
  }

  /// Create a copy of DeleteTarget
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $DeleteTarget_MessagesCopyWith<DeleteTarget_Messages> get copyWith =>
      _$DeleteTarget_MessagesCopyWithImpl<DeleteTarget_Messages>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is DeleteTarget_Messages &&
            const DeepCollectionEquality().equals(other._field0, _field0));
  }

  @override
  int get hashCode =>
      Object.hash(runtimeType, const DeepCollectionEquality().hash(_field0));

  @override
  String toString() {
    return 'DeleteTarget.messages(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $DeleteTarget_MessagesCopyWith<$Res>
    implements $DeleteTargetCopyWith<$Res> {
  factory $DeleteTarget_MessagesCopyWith(DeleteTarget_Messages value,
          $Res Function(DeleteTarget_Messages) _then) =
      _$DeleteTarget_MessagesCopyWithImpl;
  @useResult
  $Res call({List<String> field0});
}

/// @nodoc
class _$DeleteTarget_MessagesCopyWithImpl<$Res>
    implements $DeleteTarget_MessagesCopyWith<$Res> {
  _$DeleteTarget_MessagesCopyWithImpl(this._self, this._then);

  final DeleteTarget_Messages _self;
  final $Res Function(DeleteTarget_Messages) _then;

  /// Create a copy of DeleteTarget
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(DeleteTarget_Messages(
      null == field0
          ? _self._field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as List<String>,
    ));
  }
}

/// @nodoc
mixin _$FTMessage {
  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is FTMessage);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'FTMessage()';
  }
}

/// @nodoc
class $FTMessageCopyWith<$Res> {
  $FTMessageCopyWith(FTMessage _, $Res Function(FTMessage) __);
}

/// Adds pattern-matching-related methods to [FTMessage].
extension FTMessagePatterns on FTMessage {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(FTMessage_LetMeInRequest value)? letMeInRequest,
    TResult Function(FTMessage_LinkChanged value)? linkChanged,
    TResult Function(FTMessage_JoinEvent value)? joinEvent,
    TResult Function(FTMessage_AddMembers value)? addMembers,
    TResult Function(FTMessage_RemoveMembers value)? removeMembers,
    TResult Function(FTMessage_LeaveEvent value)? leaveEvent,
    TResult Function(FTMessage_Ring value)? ring,
    TResult Function(FTMessage_Decline value)? decline,
    TResult Function(FTMessage_RespondedElsewhere value)? respondedElsewhere,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case FTMessage_LetMeInRequest() when letMeInRequest != null:
        return letMeInRequest(_that);
      case FTMessage_LinkChanged() when linkChanged != null:
        return linkChanged(_that);
      case FTMessage_JoinEvent() when joinEvent != null:
        return joinEvent(_that);
      case FTMessage_AddMembers() when addMembers != null:
        return addMembers(_that);
      case FTMessage_RemoveMembers() when removeMembers != null:
        return removeMembers(_that);
      case FTMessage_LeaveEvent() when leaveEvent != null:
        return leaveEvent(_that);
      case FTMessage_Ring() when ring != null:
        return ring(_that);
      case FTMessage_Decline() when decline != null:
        return decline(_that);
      case FTMessage_RespondedElsewhere() when respondedElsewhere != null:
        return respondedElsewhere(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(FTMessage_LetMeInRequest value) letMeInRequest,
    required TResult Function(FTMessage_LinkChanged value) linkChanged,
    required TResult Function(FTMessage_JoinEvent value) joinEvent,
    required TResult Function(FTMessage_AddMembers value) addMembers,
    required TResult Function(FTMessage_RemoveMembers value) removeMembers,
    required TResult Function(FTMessage_LeaveEvent value) leaveEvent,
    required TResult Function(FTMessage_Ring value) ring,
    required TResult Function(FTMessage_Decline value) decline,
    required TResult Function(FTMessage_RespondedElsewhere value)
        respondedElsewhere,
  }) {
    final _that = this;
    switch (_that) {
      case FTMessage_LetMeInRequest():
        return letMeInRequest(_that);
      case FTMessage_LinkChanged():
        return linkChanged(_that);
      case FTMessage_JoinEvent():
        return joinEvent(_that);
      case FTMessage_AddMembers():
        return addMembers(_that);
      case FTMessage_RemoveMembers():
        return removeMembers(_that);
      case FTMessage_LeaveEvent():
        return leaveEvent(_that);
      case FTMessage_Ring():
        return ring(_that);
      case FTMessage_Decline():
        return decline(_that);
      case FTMessage_RespondedElsewhere():
        return respondedElsewhere(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(FTMessage_LetMeInRequest value)? letMeInRequest,
    TResult? Function(FTMessage_LinkChanged value)? linkChanged,
    TResult? Function(FTMessage_JoinEvent value)? joinEvent,
    TResult? Function(FTMessage_AddMembers value)? addMembers,
    TResult? Function(FTMessage_RemoveMembers value)? removeMembers,
    TResult? Function(FTMessage_LeaveEvent value)? leaveEvent,
    TResult? Function(FTMessage_Ring value)? ring,
    TResult? Function(FTMessage_Decline value)? decline,
    TResult? Function(FTMessage_RespondedElsewhere value)? respondedElsewhere,
  }) {
    final _that = this;
    switch (_that) {
      case FTMessage_LetMeInRequest() when letMeInRequest != null:
        return letMeInRequest(_that);
      case FTMessage_LinkChanged() when linkChanged != null:
        return linkChanged(_that);
      case FTMessage_JoinEvent() when joinEvent != null:
        return joinEvent(_that);
      case FTMessage_AddMembers() when addMembers != null:
        return addMembers(_that);
      case FTMessage_RemoveMembers() when removeMembers != null:
        return removeMembers(_that);
      case FTMessage_LeaveEvent() when leaveEvent != null:
        return leaveEvent(_that);
      case FTMessage_Ring() when ring != null:
        return ring(_that);
      case FTMessage_Decline() when decline != null:
        return decline(_that);
      case FTMessage_RespondedElsewhere() when respondedElsewhere != null:
        return respondedElsewhere(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function(LetMeInRequest field0)? letMeInRequest,
    TResult Function(String guid)? linkChanged,
    TResult Function(String guid, BigInt participant, String handle, bool ring)?
        joinEvent,
    TResult Function(String guid, Set<FTMember> members, bool ring)? addMembers,
    TResult Function(String guid, Set<FTMember> members)? removeMembers,
    TResult Function(String guid, BigInt participant, String handle)?
        leaveEvent,
    TResult Function(String guid)? ring,
    TResult Function(String guid)? decline,
    TResult Function(String guid)? respondedElsewhere,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case FTMessage_LetMeInRequest() when letMeInRequest != null:
        return letMeInRequest(_that.field0);
      case FTMessage_LinkChanged() when linkChanged != null:
        return linkChanged(_that.guid);
      case FTMessage_JoinEvent() when joinEvent != null:
        return joinEvent(
            _that.guid, _that.participant, _that.handle, _that.ring);
      case FTMessage_AddMembers() when addMembers != null:
        return addMembers(_that.guid, _that.members, _that.ring);
      case FTMessage_RemoveMembers() when removeMembers != null:
        return removeMembers(_that.guid, _that.members);
      case FTMessage_LeaveEvent() when leaveEvent != null:
        return leaveEvent(_that.guid, _that.participant, _that.handle);
      case FTMessage_Ring() when ring != null:
        return ring(_that.guid);
      case FTMessage_Decline() when decline != null:
        return decline(_that.guid);
      case FTMessage_RespondedElsewhere() when respondedElsewhere != null:
        return respondedElsewhere(_that.guid);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function(LetMeInRequest field0) letMeInRequest,
    required TResult Function(String guid) linkChanged,
    required TResult Function(
            String guid, BigInt participant, String handle, bool ring)
        joinEvent,
    required TResult Function(String guid, Set<FTMember> members, bool ring)
        addMembers,
    required TResult Function(String guid, Set<FTMember> members) removeMembers,
    required TResult Function(String guid, BigInt participant, String handle)
        leaveEvent,
    required TResult Function(String guid) ring,
    required TResult Function(String guid) decline,
    required TResult Function(String guid) respondedElsewhere,
  }) {
    final _that = this;
    switch (_that) {
      case FTMessage_LetMeInRequest():
        return letMeInRequest(_that.field0);
      case FTMessage_LinkChanged():
        return linkChanged(_that.guid);
      case FTMessage_JoinEvent():
        return joinEvent(
            _that.guid, _that.participant, _that.handle, _that.ring);
      case FTMessage_AddMembers():
        return addMembers(_that.guid, _that.members, _that.ring);
      case FTMessage_RemoveMembers():
        return removeMembers(_that.guid, _that.members);
      case FTMessage_LeaveEvent():
        return leaveEvent(_that.guid, _that.participant, _that.handle);
      case FTMessage_Ring():
        return ring(_that.guid);
      case FTMessage_Decline():
        return decline(_that.guid);
      case FTMessage_RespondedElsewhere():
        return respondedElsewhere(_that.guid);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function(LetMeInRequest field0)? letMeInRequest,
    TResult? Function(String guid)? linkChanged,
    TResult? Function(
            String guid, BigInt participant, String handle, bool ring)?
        joinEvent,
    TResult? Function(String guid, Set<FTMember> members, bool ring)?
        addMembers,
    TResult? Function(String guid, Set<FTMember> members)? removeMembers,
    TResult? Function(String guid, BigInt participant, String handle)?
        leaveEvent,
    TResult? Function(String guid)? ring,
    TResult? Function(String guid)? decline,
    TResult? Function(String guid)? respondedElsewhere,
  }) {
    final _that = this;
    switch (_that) {
      case FTMessage_LetMeInRequest() when letMeInRequest != null:
        return letMeInRequest(_that.field0);
      case FTMessage_LinkChanged() when linkChanged != null:
        return linkChanged(_that.guid);
      case FTMessage_JoinEvent() when joinEvent != null:
        return joinEvent(
            _that.guid, _that.participant, _that.handle, _that.ring);
      case FTMessage_AddMembers() when addMembers != null:
        return addMembers(_that.guid, _that.members, _that.ring);
      case FTMessage_RemoveMembers() when removeMembers != null:
        return removeMembers(_that.guid, _that.members);
      case FTMessage_LeaveEvent() when leaveEvent != null:
        return leaveEvent(_that.guid, _that.participant, _that.handle);
      case FTMessage_Ring() when ring != null:
        return ring(_that.guid);
      case FTMessage_Decline() when decline != null:
        return decline(_that.guid);
      case FTMessage_RespondedElsewhere() when respondedElsewhere != null:
        return respondedElsewhere(_that.guid);
      case _:
        return null;
    }
  }
}

/// @nodoc

class FTMessage_LetMeInRequest extends FTMessage {
  const FTMessage_LetMeInRequest(this.field0) : super._();

  final LetMeInRequest field0;

  /// Create a copy of FTMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $FTMessage_LetMeInRequestCopyWith<FTMessage_LetMeInRequest> get copyWith =>
      _$FTMessage_LetMeInRequestCopyWithImpl<FTMessage_LetMeInRequest>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is FTMessage_LetMeInRequest &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'FTMessage.letMeInRequest(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $FTMessage_LetMeInRequestCopyWith<$Res>
    implements $FTMessageCopyWith<$Res> {
  factory $FTMessage_LetMeInRequestCopyWith(FTMessage_LetMeInRequest value,
          $Res Function(FTMessage_LetMeInRequest) _then) =
      _$FTMessage_LetMeInRequestCopyWithImpl;
  @useResult
  $Res call({LetMeInRequest field0});
}

/// @nodoc
class _$FTMessage_LetMeInRequestCopyWithImpl<$Res>
    implements $FTMessage_LetMeInRequestCopyWith<$Res> {
  _$FTMessage_LetMeInRequestCopyWithImpl(this._self, this._then);

  final FTMessage_LetMeInRequest _self;
  final $Res Function(FTMessage_LetMeInRequest) _then;

  /// Create a copy of FTMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(FTMessage_LetMeInRequest(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as LetMeInRequest,
    ));
  }
}

/// @nodoc

class FTMessage_LinkChanged extends FTMessage {
  const FTMessage_LinkChanged({required this.guid}) : super._();

  final String guid;

  /// Create a copy of FTMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $FTMessage_LinkChangedCopyWith<FTMessage_LinkChanged> get copyWith =>
      _$FTMessage_LinkChangedCopyWithImpl<FTMessage_LinkChanged>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is FTMessage_LinkChanged &&
            (identical(other.guid, guid) || other.guid == guid));
  }

  @override
  int get hashCode => Object.hash(runtimeType, guid);

  @override
  String toString() {
    return 'FTMessage.linkChanged(guid: $guid)';
  }
}

/// @nodoc
abstract mixin class $FTMessage_LinkChangedCopyWith<$Res>
    implements $FTMessageCopyWith<$Res> {
  factory $FTMessage_LinkChangedCopyWith(FTMessage_LinkChanged value,
          $Res Function(FTMessage_LinkChanged) _then) =
      _$FTMessage_LinkChangedCopyWithImpl;
  @useResult
  $Res call({String guid});
}

/// @nodoc
class _$FTMessage_LinkChangedCopyWithImpl<$Res>
    implements $FTMessage_LinkChangedCopyWith<$Res> {
  _$FTMessage_LinkChangedCopyWithImpl(this._self, this._then);

  final FTMessage_LinkChanged _self;
  final $Res Function(FTMessage_LinkChanged) _then;

  /// Create a copy of FTMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? guid = null,
  }) {
    return _then(FTMessage_LinkChanged(
      guid: null == guid
          ? _self.guid
          : guid // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

/// @nodoc

class FTMessage_JoinEvent extends FTMessage {
  const FTMessage_JoinEvent(
      {required this.guid,
      required this.participant,
      required this.handle,
      required this.ring})
      : super._();

  final String guid;
  final BigInt participant;
  final String handle;
  final bool ring;

  /// Create a copy of FTMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $FTMessage_JoinEventCopyWith<FTMessage_JoinEvent> get copyWith =>
      _$FTMessage_JoinEventCopyWithImpl<FTMessage_JoinEvent>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is FTMessage_JoinEvent &&
            (identical(other.guid, guid) || other.guid == guid) &&
            (identical(other.participant, participant) ||
                other.participant == participant) &&
            (identical(other.handle, handle) || other.handle == handle) &&
            (identical(other.ring, ring) || other.ring == ring));
  }

  @override
  int get hashCode => Object.hash(runtimeType, guid, participant, handle, ring);

  @override
  String toString() {
    return 'FTMessage.joinEvent(guid: $guid, participant: $participant, handle: $handle, ring: $ring)';
  }
}

/// @nodoc
abstract mixin class $FTMessage_JoinEventCopyWith<$Res>
    implements $FTMessageCopyWith<$Res> {
  factory $FTMessage_JoinEventCopyWith(
          FTMessage_JoinEvent value, $Res Function(FTMessage_JoinEvent) _then) =
      _$FTMessage_JoinEventCopyWithImpl;
  @useResult
  $Res call({String guid, BigInt participant, String handle, bool ring});
}

/// @nodoc
class _$FTMessage_JoinEventCopyWithImpl<$Res>
    implements $FTMessage_JoinEventCopyWith<$Res> {
  _$FTMessage_JoinEventCopyWithImpl(this._self, this._then);

  final FTMessage_JoinEvent _self;
  final $Res Function(FTMessage_JoinEvent) _then;

  /// Create a copy of FTMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? guid = null,
    Object? participant = null,
    Object? handle = null,
    Object? ring = null,
  }) {
    return _then(FTMessage_JoinEvent(
      guid: null == guid
          ? _self.guid
          : guid // ignore: cast_nullable_to_non_nullable
              as String,
      participant: null == participant
          ? _self.participant
          : participant // ignore: cast_nullable_to_non_nullable
              as BigInt,
      handle: null == handle
          ? _self.handle
          : handle // ignore: cast_nullable_to_non_nullable
              as String,
      ring: null == ring
          ? _self.ring
          : ring // ignore: cast_nullable_to_non_nullable
              as bool,
    ));
  }
}

/// @nodoc

class FTMessage_AddMembers extends FTMessage {
  const FTMessage_AddMembers(
      {required this.guid,
      required final Set<FTMember> members,
      required this.ring})
      : _members = members,
        super._();

  final String guid;
  final Set<FTMember> _members;
  Set<FTMember> get members {
    if (_members is EqualUnmodifiableSetView) return _members;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableSetView(_members);
  }

  final bool ring;

  /// Create a copy of FTMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $FTMessage_AddMembersCopyWith<FTMessage_AddMembers> get copyWith =>
      _$FTMessage_AddMembersCopyWithImpl<FTMessage_AddMembers>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is FTMessage_AddMembers &&
            (identical(other.guid, guid) || other.guid == guid) &&
            const DeepCollectionEquality().equals(other._members, _members) &&
            (identical(other.ring, ring) || other.ring == ring));
  }

  @override
  int get hashCode => Object.hash(
      runtimeType, guid, const DeepCollectionEquality().hash(_members), ring);

  @override
  String toString() {
    return 'FTMessage.addMembers(guid: $guid, members: $members, ring: $ring)';
  }
}

/// @nodoc
abstract mixin class $FTMessage_AddMembersCopyWith<$Res>
    implements $FTMessageCopyWith<$Res> {
  factory $FTMessage_AddMembersCopyWith(FTMessage_AddMembers value,
          $Res Function(FTMessage_AddMembers) _then) =
      _$FTMessage_AddMembersCopyWithImpl;
  @useResult
  $Res call({String guid, Set<FTMember> members, bool ring});
}

/// @nodoc
class _$FTMessage_AddMembersCopyWithImpl<$Res>
    implements $FTMessage_AddMembersCopyWith<$Res> {
  _$FTMessage_AddMembersCopyWithImpl(this._self, this._then);

  final FTMessage_AddMembers _self;
  final $Res Function(FTMessage_AddMembers) _then;

  /// Create a copy of FTMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? guid = null,
    Object? members = null,
    Object? ring = null,
  }) {
    return _then(FTMessage_AddMembers(
      guid: null == guid
          ? _self.guid
          : guid // ignore: cast_nullable_to_non_nullable
              as String,
      members: null == members
          ? _self._members
          : members // ignore: cast_nullable_to_non_nullable
              as Set<FTMember>,
      ring: null == ring
          ? _self.ring
          : ring // ignore: cast_nullable_to_non_nullable
              as bool,
    ));
  }
}

/// @nodoc

class FTMessage_RemoveMembers extends FTMessage {
  const FTMessage_RemoveMembers(
      {required this.guid, required final Set<FTMember> members})
      : _members = members,
        super._();

  final String guid;
  final Set<FTMember> _members;
  Set<FTMember> get members {
    if (_members is EqualUnmodifiableSetView) return _members;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableSetView(_members);
  }

  /// Create a copy of FTMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $FTMessage_RemoveMembersCopyWith<FTMessage_RemoveMembers> get copyWith =>
      _$FTMessage_RemoveMembersCopyWithImpl<FTMessage_RemoveMembers>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is FTMessage_RemoveMembers &&
            (identical(other.guid, guid) || other.guid == guid) &&
            const DeepCollectionEquality().equals(other._members, _members));
  }

  @override
  int get hashCode => Object.hash(
      runtimeType, guid, const DeepCollectionEquality().hash(_members));

  @override
  String toString() {
    return 'FTMessage.removeMembers(guid: $guid, members: $members)';
  }
}

/// @nodoc
abstract mixin class $FTMessage_RemoveMembersCopyWith<$Res>
    implements $FTMessageCopyWith<$Res> {
  factory $FTMessage_RemoveMembersCopyWith(FTMessage_RemoveMembers value,
          $Res Function(FTMessage_RemoveMembers) _then) =
      _$FTMessage_RemoveMembersCopyWithImpl;
  @useResult
  $Res call({String guid, Set<FTMember> members});
}

/// @nodoc
class _$FTMessage_RemoveMembersCopyWithImpl<$Res>
    implements $FTMessage_RemoveMembersCopyWith<$Res> {
  _$FTMessage_RemoveMembersCopyWithImpl(this._self, this._then);

  final FTMessage_RemoveMembers _self;
  final $Res Function(FTMessage_RemoveMembers) _then;

  /// Create a copy of FTMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? guid = null,
    Object? members = null,
  }) {
    return _then(FTMessage_RemoveMembers(
      guid: null == guid
          ? _self.guid
          : guid // ignore: cast_nullable_to_non_nullable
              as String,
      members: null == members
          ? _self._members
          : members // ignore: cast_nullable_to_non_nullable
              as Set<FTMember>,
    ));
  }
}

/// @nodoc

class FTMessage_LeaveEvent extends FTMessage {
  const FTMessage_LeaveEvent(
      {required this.guid, required this.participant, required this.handle})
      : super._();

  final String guid;
  final BigInt participant;
  final String handle;

  /// Create a copy of FTMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $FTMessage_LeaveEventCopyWith<FTMessage_LeaveEvent> get copyWith =>
      _$FTMessage_LeaveEventCopyWithImpl<FTMessage_LeaveEvent>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is FTMessage_LeaveEvent &&
            (identical(other.guid, guid) || other.guid == guid) &&
            (identical(other.participant, participant) ||
                other.participant == participant) &&
            (identical(other.handle, handle) || other.handle == handle));
  }

  @override
  int get hashCode => Object.hash(runtimeType, guid, participant, handle);

  @override
  String toString() {
    return 'FTMessage.leaveEvent(guid: $guid, participant: $participant, handle: $handle)';
  }
}

/// @nodoc
abstract mixin class $FTMessage_LeaveEventCopyWith<$Res>
    implements $FTMessageCopyWith<$Res> {
  factory $FTMessage_LeaveEventCopyWith(FTMessage_LeaveEvent value,
          $Res Function(FTMessage_LeaveEvent) _then) =
      _$FTMessage_LeaveEventCopyWithImpl;
  @useResult
  $Res call({String guid, BigInt participant, String handle});
}

/// @nodoc
class _$FTMessage_LeaveEventCopyWithImpl<$Res>
    implements $FTMessage_LeaveEventCopyWith<$Res> {
  _$FTMessage_LeaveEventCopyWithImpl(this._self, this._then);

  final FTMessage_LeaveEvent _self;
  final $Res Function(FTMessage_LeaveEvent) _then;

  /// Create a copy of FTMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? guid = null,
    Object? participant = null,
    Object? handle = null,
  }) {
    return _then(FTMessage_LeaveEvent(
      guid: null == guid
          ? _self.guid
          : guid // ignore: cast_nullable_to_non_nullable
              as String,
      participant: null == participant
          ? _self.participant
          : participant // ignore: cast_nullable_to_non_nullable
              as BigInt,
      handle: null == handle
          ? _self.handle
          : handle // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

/// @nodoc

class FTMessage_Ring extends FTMessage {
  const FTMessage_Ring({required this.guid}) : super._();

  final String guid;

  /// Create a copy of FTMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $FTMessage_RingCopyWith<FTMessage_Ring> get copyWith =>
      _$FTMessage_RingCopyWithImpl<FTMessage_Ring>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is FTMessage_Ring &&
            (identical(other.guid, guid) || other.guid == guid));
  }

  @override
  int get hashCode => Object.hash(runtimeType, guid);

  @override
  String toString() {
    return 'FTMessage.ring(guid: $guid)';
  }
}

/// @nodoc
abstract mixin class $FTMessage_RingCopyWith<$Res>
    implements $FTMessageCopyWith<$Res> {
  factory $FTMessage_RingCopyWith(
          FTMessage_Ring value, $Res Function(FTMessage_Ring) _then) =
      _$FTMessage_RingCopyWithImpl;
  @useResult
  $Res call({String guid});
}

/// @nodoc
class _$FTMessage_RingCopyWithImpl<$Res>
    implements $FTMessage_RingCopyWith<$Res> {
  _$FTMessage_RingCopyWithImpl(this._self, this._then);

  final FTMessage_Ring _self;
  final $Res Function(FTMessage_Ring) _then;

  /// Create a copy of FTMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? guid = null,
  }) {
    return _then(FTMessage_Ring(
      guid: null == guid
          ? _self.guid
          : guid // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

/// @nodoc

class FTMessage_Decline extends FTMessage {
  const FTMessage_Decline({required this.guid}) : super._();

  final String guid;

  /// Create a copy of FTMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $FTMessage_DeclineCopyWith<FTMessage_Decline> get copyWith =>
      _$FTMessage_DeclineCopyWithImpl<FTMessage_Decline>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is FTMessage_Decline &&
            (identical(other.guid, guid) || other.guid == guid));
  }

  @override
  int get hashCode => Object.hash(runtimeType, guid);

  @override
  String toString() {
    return 'FTMessage.decline(guid: $guid)';
  }
}

/// @nodoc
abstract mixin class $FTMessage_DeclineCopyWith<$Res>
    implements $FTMessageCopyWith<$Res> {
  factory $FTMessage_DeclineCopyWith(
          FTMessage_Decline value, $Res Function(FTMessage_Decline) _then) =
      _$FTMessage_DeclineCopyWithImpl;
  @useResult
  $Res call({String guid});
}

/// @nodoc
class _$FTMessage_DeclineCopyWithImpl<$Res>
    implements $FTMessage_DeclineCopyWith<$Res> {
  _$FTMessage_DeclineCopyWithImpl(this._self, this._then);

  final FTMessage_Decline _self;
  final $Res Function(FTMessage_Decline) _then;

  /// Create a copy of FTMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? guid = null,
  }) {
    return _then(FTMessage_Decline(
      guid: null == guid
          ? _self.guid
          : guid // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

/// @nodoc

class FTMessage_RespondedElsewhere extends FTMessage {
  const FTMessage_RespondedElsewhere({required this.guid}) : super._();

  final String guid;

  /// Create a copy of FTMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $FTMessage_RespondedElsewhereCopyWith<FTMessage_RespondedElsewhere>
      get copyWith => _$FTMessage_RespondedElsewhereCopyWithImpl<
          FTMessage_RespondedElsewhere>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is FTMessage_RespondedElsewhere &&
            (identical(other.guid, guid) || other.guid == guid));
  }

  @override
  int get hashCode => Object.hash(runtimeType, guid);

  @override
  String toString() {
    return 'FTMessage.respondedElsewhere(guid: $guid)';
  }
}

/// @nodoc
abstract mixin class $FTMessage_RespondedElsewhereCopyWith<$Res>
    implements $FTMessageCopyWith<$Res> {
  factory $FTMessage_RespondedElsewhereCopyWith(
          FTMessage_RespondedElsewhere value,
          $Res Function(FTMessage_RespondedElsewhere) _then) =
      _$FTMessage_RespondedElsewhereCopyWithImpl;
  @useResult
  $Res call({String guid});
}

/// @nodoc
class _$FTMessage_RespondedElsewhereCopyWithImpl<$Res>
    implements $FTMessage_RespondedElsewhereCopyWith<$Res> {
  _$FTMessage_RespondedElsewhereCopyWithImpl(this._self, this._then);

  final FTMessage_RespondedElsewhere _self;
  final $Res Function(FTMessage_RespondedElsewhere) _then;

  /// Create a copy of FTMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? guid = null,
  }) {
    return _then(FTMessage_RespondedElsewhere(
      guid: null == guid
          ? _self.guid
          : guid // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

/// @nodoc
mixin _$IdmsMessage {
  Object get field0;

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is IdmsMessage &&
            const DeepCollectionEquality().equals(other.field0, field0));
  }

  @override
  int get hashCode =>
      Object.hash(runtimeType, const DeepCollectionEquality().hash(field0));

  @override
  String toString() {
    return 'IdmsMessage(field0: $field0)';
  }
}

/// @nodoc
class $IdmsMessageCopyWith<$Res> {
  $IdmsMessageCopyWith(IdmsMessage _, $Res Function(IdmsMessage) __);
}

/// Adds pattern-matching-related methods to [IdmsMessage].
extension IdmsMessagePatterns on IdmsMessage {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(IdmsMessage_RequestedSignIn value)? requestedSignIn,
    TResult Function(IdmsMessage_TeardownSignIn value)? teardownSignIn,
    TResult Function(IdmsMessage_CircleRequest value)? circleRequest,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case IdmsMessage_RequestedSignIn() when requestedSignIn != null:
        return requestedSignIn(_that);
      case IdmsMessage_TeardownSignIn() when teardownSignIn != null:
        return teardownSignIn(_that);
      case IdmsMessage_CircleRequest() when circleRequest != null:
        return circleRequest(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(IdmsMessage_RequestedSignIn value)
        requestedSignIn,
    required TResult Function(IdmsMessage_TeardownSignIn value) teardownSignIn,
    required TResult Function(IdmsMessage_CircleRequest value) circleRequest,
  }) {
    final _that = this;
    switch (_that) {
      case IdmsMessage_RequestedSignIn():
        return requestedSignIn(_that);
      case IdmsMessage_TeardownSignIn():
        return teardownSignIn(_that);
      case IdmsMessage_CircleRequest():
        return circleRequest(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(IdmsMessage_RequestedSignIn value)? requestedSignIn,
    TResult? Function(IdmsMessage_TeardownSignIn value)? teardownSignIn,
    TResult? Function(IdmsMessage_CircleRequest value)? circleRequest,
  }) {
    final _that = this;
    switch (_that) {
      case IdmsMessage_RequestedSignIn() when requestedSignIn != null:
        return requestedSignIn(_that);
      case IdmsMessage_TeardownSignIn() when teardownSignIn != null:
        return teardownSignIn(_that);
      case IdmsMessage_CircleRequest() when circleRequest != null:
        return circleRequest(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function(IdmsRequestedSignIn field0)? requestedSignIn,
    TResult Function(TeardownSignIn field0)? teardownSignIn,
    TResult Function(IdmsCircleMessage field0, IdmsRequestedSignIn? field1)?
        circleRequest,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case IdmsMessage_RequestedSignIn() when requestedSignIn != null:
        return requestedSignIn(_that.field0);
      case IdmsMessage_TeardownSignIn() when teardownSignIn != null:
        return teardownSignIn(_that.field0);
      case IdmsMessage_CircleRequest() when circleRequest != null:
        return circleRequest(_that.field0, _that.field1);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function(IdmsRequestedSignIn field0) requestedSignIn,
    required TResult Function(TeardownSignIn field0) teardownSignIn,
    required TResult Function(
            IdmsCircleMessage field0, IdmsRequestedSignIn? field1)
        circleRequest,
  }) {
    final _that = this;
    switch (_that) {
      case IdmsMessage_RequestedSignIn():
        return requestedSignIn(_that.field0);
      case IdmsMessage_TeardownSignIn():
        return teardownSignIn(_that.field0);
      case IdmsMessage_CircleRequest():
        return circleRequest(_that.field0, _that.field1);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function(IdmsRequestedSignIn field0)? requestedSignIn,
    TResult? Function(TeardownSignIn field0)? teardownSignIn,
    TResult? Function(IdmsCircleMessage field0, IdmsRequestedSignIn? field1)?
        circleRequest,
  }) {
    final _that = this;
    switch (_that) {
      case IdmsMessage_RequestedSignIn() when requestedSignIn != null:
        return requestedSignIn(_that.field0);
      case IdmsMessage_TeardownSignIn() when teardownSignIn != null:
        return teardownSignIn(_that.field0);
      case IdmsMessage_CircleRequest() when circleRequest != null:
        return circleRequest(_that.field0, _that.field1);
      case _:
        return null;
    }
  }
}

/// @nodoc

class IdmsMessage_RequestedSignIn extends IdmsMessage {
  const IdmsMessage_RequestedSignIn(this.field0) : super._();

  @override
  final IdmsRequestedSignIn field0;

  /// Create a copy of IdmsMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $IdmsMessage_RequestedSignInCopyWith<IdmsMessage_RequestedSignIn>
      get copyWith => _$IdmsMessage_RequestedSignInCopyWithImpl<
          IdmsMessage_RequestedSignIn>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is IdmsMessage_RequestedSignIn &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'IdmsMessage.requestedSignIn(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $IdmsMessage_RequestedSignInCopyWith<$Res>
    implements $IdmsMessageCopyWith<$Res> {
  factory $IdmsMessage_RequestedSignInCopyWith(
          IdmsMessage_RequestedSignIn value,
          $Res Function(IdmsMessage_RequestedSignIn) _then) =
      _$IdmsMessage_RequestedSignInCopyWithImpl;
  @useResult
  $Res call({IdmsRequestedSignIn field0});
}

/// @nodoc
class _$IdmsMessage_RequestedSignInCopyWithImpl<$Res>
    implements $IdmsMessage_RequestedSignInCopyWith<$Res> {
  _$IdmsMessage_RequestedSignInCopyWithImpl(this._self, this._then);

  final IdmsMessage_RequestedSignIn _self;
  final $Res Function(IdmsMessage_RequestedSignIn) _then;

  /// Create a copy of IdmsMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(IdmsMessage_RequestedSignIn(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as IdmsRequestedSignIn,
    ));
  }
}

/// @nodoc

class IdmsMessage_TeardownSignIn extends IdmsMessage {
  const IdmsMessage_TeardownSignIn(this.field0) : super._();

  @override
  final TeardownSignIn field0;

  /// Create a copy of IdmsMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $IdmsMessage_TeardownSignInCopyWith<IdmsMessage_TeardownSignIn>
      get copyWith =>
          _$IdmsMessage_TeardownSignInCopyWithImpl<IdmsMessage_TeardownSignIn>(
              this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is IdmsMessage_TeardownSignIn &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'IdmsMessage.teardownSignIn(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $IdmsMessage_TeardownSignInCopyWith<$Res>
    implements $IdmsMessageCopyWith<$Res> {
  factory $IdmsMessage_TeardownSignInCopyWith(IdmsMessage_TeardownSignIn value,
          $Res Function(IdmsMessage_TeardownSignIn) _then) =
      _$IdmsMessage_TeardownSignInCopyWithImpl;
  @useResult
  $Res call({TeardownSignIn field0});
}

/// @nodoc
class _$IdmsMessage_TeardownSignInCopyWithImpl<$Res>
    implements $IdmsMessage_TeardownSignInCopyWith<$Res> {
  _$IdmsMessage_TeardownSignInCopyWithImpl(this._self, this._then);

  final IdmsMessage_TeardownSignIn _self;
  final $Res Function(IdmsMessage_TeardownSignIn) _then;

  /// Create a copy of IdmsMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(IdmsMessage_TeardownSignIn(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as TeardownSignIn,
    ));
  }
}

/// @nodoc

class IdmsMessage_CircleRequest extends IdmsMessage {
  const IdmsMessage_CircleRequest(this.field0, [this.field1]) : super._();

  @override
  final IdmsCircleMessage field0;
  final IdmsRequestedSignIn? field1;

  /// Create a copy of IdmsMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $IdmsMessage_CircleRequestCopyWith<IdmsMessage_CircleRequest> get copyWith =>
      _$IdmsMessage_CircleRequestCopyWithImpl<IdmsMessage_CircleRequest>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is IdmsMessage_CircleRequest &&
            (identical(other.field0, field0) || other.field0 == field0) &&
            (identical(other.field1, field1) || other.field1 == field1));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0, field1);

  @override
  String toString() {
    return 'IdmsMessage.circleRequest(field0: $field0, field1: $field1)';
  }
}

/// @nodoc
abstract mixin class $IdmsMessage_CircleRequestCopyWith<$Res>
    implements $IdmsMessageCopyWith<$Res> {
  factory $IdmsMessage_CircleRequestCopyWith(IdmsMessage_CircleRequest value,
          $Res Function(IdmsMessage_CircleRequest) _then) =
      _$IdmsMessage_CircleRequestCopyWithImpl;
  @useResult
  $Res call({IdmsCircleMessage field0, IdmsRequestedSignIn? field1});
}

/// @nodoc
class _$IdmsMessage_CircleRequestCopyWithImpl<$Res>
    implements $IdmsMessage_CircleRequestCopyWith<$Res> {
  _$IdmsMessage_CircleRequestCopyWithImpl(this._self, this._then);

  final IdmsMessage_CircleRequest _self;
  final $Res Function(IdmsMessage_CircleRequest) _then;

  /// Create a copy of IdmsMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
    Object? field1 = freezed,
  }) {
    return _then(IdmsMessage_CircleRequest(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as IdmsCircleMessage,
      freezed == field1
          ? _self.field1
          : field1 // ignore: cast_nullable_to_non_nullable
              as IdmsRequestedSignIn?,
    ));
  }
}

/// @nodoc
mixin _$LoginState {
  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is LoginState);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'LoginState()';
  }
}

/// @nodoc
class $LoginStateCopyWith<$Res> {
  $LoginStateCopyWith(LoginState _, $Res Function(LoginState) __);
}

/// Adds pattern-matching-related methods to [LoginState].
extension LoginStatePatterns on LoginState {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(LoginState_LoggedIn value)? loggedIn,
    TResult Function(LoginState_NeedsDevice2FA value)? needsDevice2Fa,
    TResult Function(LoginState_Needs2FAVerification value)?
        needs2FaVerification,
    TResult Function(LoginState_NeedsSMS2FA value)? needsSms2Fa,
    TResult Function(LoginState_NeedsSMS2FAVerification value)?
        needsSms2FaVerification,
    TResult Function(LoginState_NeedsExtraStep value)? needsExtraStep,
    TResult Function(LoginState_NeedsLogin value)? needsLogin,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case LoginState_LoggedIn() when loggedIn != null:
        return loggedIn(_that);
      case LoginState_NeedsDevice2FA() when needsDevice2Fa != null:
        return needsDevice2Fa(_that);
      case LoginState_Needs2FAVerification() when needs2FaVerification != null:
        return needs2FaVerification(_that);
      case LoginState_NeedsSMS2FA() when needsSms2Fa != null:
        return needsSms2Fa(_that);
      case LoginState_NeedsSMS2FAVerification()
          when needsSms2FaVerification != null:
        return needsSms2FaVerification(_that);
      case LoginState_NeedsExtraStep() when needsExtraStep != null:
        return needsExtraStep(_that);
      case LoginState_NeedsLogin() when needsLogin != null:
        return needsLogin(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(LoginState_LoggedIn value) loggedIn,
    required TResult Function(LoginState_NeedsDevice2FA value) needsDevice2Fa,
    required TResult Function(LoginState_Needs2FAVerification value)
        needs2FaVerification,
    required TResult Function(LoginState_NeedsSMS2FA value) needsSms2Fa,
    required TResult Function(LoginState_NeedsSMS2FAVerification value)
        needsSms2FaVerification,
    required TResult Function(LoginState_NeedsExtraStep value) needsExtraStep,
    required TResult Function(LoginState_NeedsLogin value) needsLogin,
  }) {
    final _that = this;
    switch (_that) {
      case LoginState_LoggedIn():
        return loggedIn(_that);
      case LoginState_NeedsDevice2FA():
        return needsDevice2Fa(_that);
      case LoginState_Needs2FAVerification():
        return needs2FaVerification(_that);
      case LoginState_NeedsSMS2FA():
        return needsSms2Fa(_that);
      case LoginState_NeedsSMS2FAVerification():
        return needsSms2FaVerification(_that);
      case LoginState_NeedsExtraStep():
        return needsExtraStep(_that);
      case LoginState_NeedsLogin():
        return needsLogin(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(LoginState_LoggedIn value)? loggedIn,
    TResult? Function(LoginState_NeedsDevice2FA value)? needsDevice2Fa,
    TResult? Function(LoginState_Needs2FAVerification value)?
        needs2FaVerification,
    TResult? Function(LoginState_NeedsSMS2FA value)? needsSms2Fa,
    TResult? Function(LoginState_NeedsSMS2FAVerification value)?
        needsSms2FaVerification,
    TResult? Function(LoginState_NeedsExtraStep value)? needsExtraStep,
    TResult? Function(LoginState_NeedsLogin value)? needsLogin,
  }) {
    final _that = this;
    switch (_that) {
      case LoginState_LoggedIn() when loggedIn != null:
        return loggedIn(_that);
      case LoginState_NeedsDevice2FA() when needsDevice2Fa != null:
        return needsDevice2Fa(_that);
      case LoginState_Needs2FAVerification() when needs2FaVerification != null:
        return needs2FaVerification(_that);
      case LoginState_NeedsSMS2FA() when needsSms2Fa != null:
        return needsSms2Fa(_that);
      case LoginState_NeedsSMS2FAVerification()
          when needsSms2FaVerification != null:
        return needsSms2FaVerification(_that);
      case LoginState_NeedsExtraStep() when needsExtraStep != null:
        return needsExtraStep(_that);
      case LoginState_NeedsLogin() when needsLogin != null:
        return needsLogin(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function()? loggedIn,
    TResult Function()? needsDevice2Fa,
    TResult Function()? needs2FaVerification,
    TResult Function()? needsSms2Fa,
    TResult Function(VerifyBody field0)? needsSms2FaVerification,
    TResult Function(String field0)? needsExtraStep,
    TResult Function()? needsLogin,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case LoginState_LoggedIn() when loggedIn != null:
        return loggedIn();
      case LoginState_NeedsDevice2FA() when needsDevice2Fa != null:
        return needsDevice2Fa();
      case LoginState_Needs2FAVerification() when needs2FaVerification != null:
        return needs2FaVerification();
      case LoginState_NeedsSMS2FA() when needsSms2Fa != null:
        return needsSms2Fa();
      case LoginState_NeedsSMS2FAVerification()
          when needsSms2FaVerification != null:
        return needsSms2FaVerification(_that.field0);
      case LoginState_NeedsExtraStep() when needsExtraStep != null:
        return needsExtraStep(_that.field0);
      case LoginState_NeedsLogin() when needsLogin != null:
        return needsLogin();
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function() loggedIn,
    required TResult Function() needsDevice2Fa,
    required TResult Function() needs2FaVerification,
    required TResult Function() needsSms2Fa,
    required TResult Function(VerifyBody field0) needsSms2FaVerification,
    required TResult Function(String field0) needsExtraStep,
    required TResult Function() needsLogin,
  }) {
    final _that = this;
    switch (_that) {
      case LoginState_LoggedIn():
        return loggedIn();
      case LoginState_NeedsDevice2FA():
        return needsDevice2Fa();
      case LoginState_Needs2FAVerification():
        return needs2FaVerification();
      case LoginState_NeedsSMS2FA():
        return needsSms2Fa();
      case LoginState_NeedsSMS2FAVerification():
        return needsSms2FaVerification(_that.field0);
      case LoginState_NeedsExtraStep():
        return needsExtraStep(_that.field0);
      case LoginState_NeedsLogin():
        return needsLogin();
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function()? loggedIn,
    TResult? Function()? needsDevice2Fa,
    TResult? Function()? needs2FaVerification,
    TResult? Function()? needsSms2Fa,
    TResult? Function(VerifyBody field0)? needsSms2FaVerification,
    TResult? Function(String field0)? needsExtraStep,
    TResult? Function()? needsLogin,
  }) {
    final _that = this;
    switch (_that) {
      case LoginState_LoggedIn() when loggedIn != null:
        return loggedIn();
      case LoginState_NeedsDevice2FA() when needsDevice2Fa != null:
        return needsDevice2Fa();
      case LoginState_Needs2FAVerification() when needs2FaVerification != null:
        return needs2FaVerification();
      case LoginState_NeedsSMS2FA() when needsSms2Fa != null:
        return needsSms2Fa();
      case LoginState_NeedsSMS2FAVerification()
          when needsSms2FaVerification != null:
        return needsSms2FaVerification(_that.field0);
      case LoginState_NeedsExtraStep() when needsExtraStep != null:
        return needsExtraStep(_that.field0);
      case LoginState_NeedsLogin() when needsLogin != null:
        return needsLogin();
      case _:
        return null;
    }
  }
}

/// @nodoc

class LoginState_LoggedIn extends LoginState {
  const LoginState_LoggedIn() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is LoginState_LoggedIn);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'LoginState.loggedIn()';
  }
}

/// @nodoc

class LoginState_NeedsDevice2FA extends LoginState {
  const LoginState_NeedsDevice2FA() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is LoginState_NeedsDevice2FA);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'LoginState.needsDevice2Fa()';
  }
}

/// @nodoc

class LoginState_Needs2FAVerification extends LoginState {
  const LoginState_Needs2FAVerification() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is LoginState_Needs2FAVerification);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'LoginState.needs2FaVerification()';
  }
}

/// @nodoc

class LoginState_NeedsSMS2FA extends LoginState {
  const LoginState_NeedsSMS2FA() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is LoginState_NeedsSMS2FA);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'LoginState.needsSms2Fa()';
  }
}

/// @nodoc

class LoginState_NeedsSMS2FAVerification extends LoginState {
  const LoginState_NeedsSMS2FAVerification(this.field0) : super._();

  final VerifyBody field0;

  /// Create a copy of LoginState
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $LoginState_NeedsSMS2FAVerificationCopyWith<
          LoginState_NeedsSMS2FAVerification>
      get copyWith => _$LoginState_NeedsSMS2FAVerificationCopyWithImpl<
          LoginState_NeedsSMS2FAVerification>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is LoginState_NeedsSMS2FAVerification &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'LoginState.needsSms2FaVerification(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $LoginState_NeedsSMS2FAVerificationCopyWith<$Res>
    implements $LoginStateCopyWith<$Res> {
  factory $LoginState_NeedsSMS2FAVerificationCopyWith(
          LoginState_NeedsSMS2FAVerification value,
          $Res Function(LoginState_NeedsSMS2FAVerification) _then) =
      _$LoginState_NeedsSMS2FAVerificationCopyWithImpl;
  @useResult
  $Res call({VerifyBody field0});
}

/// @nodoc
class _$LoginState_NeedsSMS2FAVerificationCopyWithImpl<$Res>
    implements $LoginState_NeedsSMS2FAVerificationCopyWith<$Res> {
  _$LoginState_NeedsSMS2FAVerificationCopyWithImpl(this._self, this._then);

  final LoginState_NeedsSMS2FAVerification _self;
  final $Res Function(LoginState_NeedsSMS2FAVerification) _then;

  /// Create a copy of LoginState
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(LoginState_NeedsSMS2FAVerification(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as VerifyBody,
    ));
  }
}

/// @nodoc

class LoginState_NeedsExtraStep extends LoginState {
  const LoginState_NeedsExtraStep(this.field0) : super._();

  final String field0;

  /// Create a copy of LoginState
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $LoginState_NeedsExtraStepCopyWith<LoginState_NeedsExtraStep> get copyWith =>
      _$LoginState_NeedsExtraStepCopyWithImpl<LoginState_NeedsExtraStep>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is LoginState_NeedsExtraStep &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'LoginState.needsExtraStep(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $LoginState_NeedsExtraStepCopyWith<$Res>
    implements $LoginStateCopyWith<$Res> {
  factory $LoginState_NeedsExtraStepCopyWith(LoginState_NeedsExtraStep value,
          $Res Function(LoginState_NeedsExtraStep) _then) =
      _$LoginState_NeedsExtraStepCopyWithImpl;
  @useResult
  $Res call({String field0});
}

/// @nodoc
class _$LoginState_NeedsExtraStepCopyWithImpl<$Res>
    implements $LoginState_NeedsExtraStepCopyWith<$Res> {
  _$LoginState_NeedsExtraStepCopyWithImpl(this._self, this._then);

  final LoginState_NeedsExtraStep _self;
  final $Res Function(LoginState_NeedsExtraStep) _then;

  /// Create a copy of LoginState
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(LoginState_NeedsExtraStep(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

/// @nodoc

class LoginState_NeedsLogin extends LoginState {
  const LoginState_NeedsLogin() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is LoginState_NeedsLogin);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'LoginState.needsLogin()';
  }
}

/// @nodoc
mixin _$LPSpecializationMetadata {
  String get groupName;
  String get urlParameters;

  /// Create a copy of LPSpecializationMetadata
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $LPSpecializationMetadataCopyWith<LPSpecializationMetadata> get copyWith =>
      _$LPSpecializationMetadataCopyWithImpl<LPSpecializationMetadata>(
          this as LPSpecializationMetadata, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is LPSpecializationMetadata &&
            (identical(other.groupName, groupName) ||
                other.groupName == groupName) &&
            (identical(other.urlParameters, urlParameters) ||
                other.urlParameters == urlParameters));
  }

  @override
  int get hashCode => Object.hash(runtimeType, groupName, urlParameters);

  @override
  String toString() {
    return 'LPSpecializationMetadata(groupName: $groupName, urlParameters: $urlParameters)';
  }
}

/// @nodoc
abstract mixin class $LPSpecializationMetadataCopyWith<$Res> {
  factory $LPSpecializationMetadataCopyWith(LPSpecializationMetadata value,
          $Res Function(LPSpecializationMetadata) _then) =
      _$LPSpecializationMetadataCopyWithImpl;
  @useResult
  $Res call({String groupName, String urlParameters});
}

/// @nodoc
class _$LPSpecializationMetadataCopyWithImpl<$Res>
    implements $LPSpecializationMetadataCopyWith<$Res> {
  _$LPSpecializationMetadataCopyWithImpl(this._self, this._then);

  final LPSpecializationMetadata _self;
  final $Res Function(LPSpecializationMetadata) _then;

  /// Create a copy of LPSpecializationMetadata
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? groupName = null,
    Object? urlParameters = null,
  }) {
    return _then(_self.copyWith(
      groupName: null == groupName
          ? _self.groupName
          : groupName // ignore: cast_nullable_to_non_nullable
              as String,
      urlParameters: null == urlParameters
          ? _self.urlParameters
          : urlParameters // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

/// Adds pattern-matching-related methods to [LPSpecializationMetadata].
extension LPSpecializationMetadataPatterns on LPSpecializationMetadata {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(LPSpecializationMetadata_LPPasswordsInviteMetadata value)?
        lpPasswordsInviteMetadata,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case LPSpecializationMetadata_LPPasswordsInviteMetadata()
          when lpPasswordsInviteMetadata != null:
        return lpPasswordsInviteMetadata(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(
            LPSpecializationMetadata_LPPasswordsInviteMetadata value)
        lpPasswordsInviteMetadata,
  }) {
    final _that = this;
    switch (_that) {
      case LPSpecializationMetadata_LPPasswordsInviteMetadata():
        return lpPasswordsInviteMetadata(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(LPSpecializationMetadata_LPPasswordsInviteMetadata value)?
        lpPasswordsInviteMetadata,
  }) {
    final _that = this;
    switch (_that) {
      case LPSpecializationMetadata_LPPasswordsInviteMetadata()
          when lpPasswordsInviteMetadata != null:
        return lpPasswordsInviteMetadata(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function(String groupName, String urlParameters)?
        lpPasswordsInviteMetadata,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case LPSpecializationMetadata_LPPasswordsInviteMetadata()
          when lpPasswordsInviteMetadata != null:
        return lpPasswordsInviteMetadata(_that.groupName, _that.urlParameters);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function(String groupName, String urlParameters)
        lpPasswordsInviteMetadata,
  }) {
    final _that = this;
    switch (_that) {
      case LPSpecializationMetadata_LPPasswordsInviteMetadata():
        return lpPasswordsInviteMetadata(_that.groupName, _that.urlParameters);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function(String groupName, String urlParameters)?
        lpPasswordsInviteMetadata,
  }) {
    final _that = this;
    switch (_that) {
      case LPSpecializationMetadata_LPPasswordsInviteMetadata()
          when lpPasswordsInviteMetadata != null:
        return lpPasswordsInviteMetadata(_that.groupName, _that.urlParameters);
      case _:
        return null;
    }
  }
}

/// @nodoc

class LPSpecializationMetadata_LPPasswordsInviteMetadata
    extends LPSpecializationMetadata {
  const LPSpecializationMetadata_LPPasswordsInviteMetadata(
      {required this.groupName, required this.urlParameters})
      : super._();

  @override
  final String groupName;
  @override
  final String urlParameters;

  /// Create a copy of LPSpecializationMetadata
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $LPSpecializationMetadata_LPPasswordsInviteMetadataCopyWith<
          LPSpecializationMetadata_LPPasswordsInviteMetadata>
      get copyWith =>
          _$LPSpecializationMetadata_LPPasswordsInviteMetadataCopyWithImpl<
                  LPSpecializationMetadata_LPPasswordsInviteMetadata>(
              this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is LPSpecializationMetadata_LPPasswordsInviteMetadata &&
            (identical(other.groupName, groupName) ||
                other.groupName == groupName) &&
            (identical(other.urlParameters, urlParameters) ||
                other.urlParameters == urlParameters));
  }

  @override
  int get hashCode => Object.hash(runtimeType, groupName, urlParameters);

  @override
  String toString() {
    return 'LPSpecializationMetadata.lpPasswordsInviteMetadata(groupName: $groupName, urlParameters: $urlParameters)';
  }
}

/// @nodoc
abstract mixin class $LPSpecializationMetadata_LPPasswordsInviteMetadataCopyWith<
    $Res> implements $LPSpecializationMetadataCopyWith<$Res> {
  factory $LPSpecializationMetadata_LPPasswordsInviteMetadataCopyWith(
          LPSpecializationMetadata_LPPasswordsInviteMetadata value,
          $Res Function(LPSpecializationMetadata_LPPasswordsInviteMetadata)
              _then) =
      _$LPSpecializationMetadata_LPPasswordsInviteMetadataCopyWithImpl;
  @override
  @useResult
  $Res call({String groupName, String urlParameters});
}

/// @nodoc
class _$LPSpecializationMetadata_LPPasswordsInviteMetadataCopyWithImpl<$Res>
    implements
        $LPSpecializationMetadata_LPPasswordsInviteMetadataCopyWith<$Res> {
  _$LPSpecializationMetadata_LPPasswordsInviteMetadataCopyWithImpl(
      this._self, this._then);

  final LPSpecializationMetadata_LPPasswordsInviteMetadata _self;
  final $Res Function(LPSpecializationMetadata_LPPasswordsInviteMetadata) _then;

  /// Create a copy of LPSpecializationMetadata
  /// with the given fields replaced by the non-null parameter values.
  @override
  @pragma('vm:prefer-inline')
  $Res call({
    Object? groupName = null,
    Object? urlParameters = null,
  }) {
    return _then(LPSpecializationMetadata_LPPasswordsInviteMetadata(
      groupName: null == groupName
          ? _self.groupName
          : groupName // ignore: cast_nullable_to_non_nullable
              as String,
      urlParameters: null == urlParameters
          ? _self.urlParameters
          : urlParameters // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

/// @nodoc
mixin _$Message {
  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is Message);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'Message()';
  }
}

/// @nodoc
class $MessageCopyWith<$Res> {
  $MessageCopyWith(Message _, $Res Function(Message) __);
}

/// Adds pattern-matching-related methods to [Message].
extension MessagePatterns on Message {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(Message_Message value)? message,
    TResult Function(Message_RenameMessage value)? renameMessage,
    TResult Function(Message_ChangeParticipants value)? changeParticipants,
    TResult Function(Message_React value)? react,
    TResult Function(Message_Delivered value)? delivered,
    TResult Function(Message_Read value)? read,
    TResult Function(Message_Typing value)? typing,
    TResult Function(Message_Unsend value)? unsend,
    TResult Function(Message_Edit value)? edit,
    TResult Function(Message_IconChange value)? iconChange,
    TResult Function(Message_EnableSmsActivation value)? enableSmsActivation,
    TResult Function(Message_MessageReadOnDevice value)? messageReadOnDevice,
    TResult Function(Message_SmsConfirmSent value)? smsConfirmSent,
    TResult Function(Message_MarkUnread value)? markUnread,
    TResult Function(Message_PeerCacheInvalidate value)? peerCacheInvalidate,
    TResult Function(Message_UpdateExtension value)? updateExtension,
    TResult Function(Message_Error value)? error,
    TResult Function(Message_MoveToRecycleBin value)? moveToRecycleBin,
    TResult Function(Message_RecoverChat value)? recoverChat,
    TResult Function(Message_PermanentDelete value)? permanentDelete,
    TResult Function(Message_Unschedule value)? unschedule,
    TResult Function(Message_UpdateProfile value)? updateProfile,
    TResult Function(Message_UpdateProfileSharing value)? updateProfileSharing,
    TResult Function(Message_ShareProfile value)? shareProfile,
    TResult Function(Message_NotifyAnyways value)? notifyAnyways,
    TResult Function(Message_SetTranscriptBackground value)?
        setTranscriptBackground,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case Message_Message() when message != null:
        return message(_that);
      case Message_RenameMessage() when renameMessage != null:
        return renameMessage(_that);
      case Message_ChangeParticipants() when changeParticipants != null:
        return changeParticipants(_that);
      case Message_React() when react != null:
        return react(_that);
      case Message_Delivered() when delivered != null:
        return delivered(_that);
      case Message_Read() when read != null:
        return read(_that);
      case Message_Typing() when typing != null:
        return typing(_that);
      case Message_Unsend() when unsend != null:
        return unsend(_that);
      case Message_Edit() when edit != null:
        return edit(_that);
      case Message_IconChange() when iconChange != null:
        return iconChange(_that);
      case Message_EnableSmsActivation() when enableSmsActivation != null:
        return enableSmsActivation(_that);
      case Message_MessageReadOnDevice() when messageReadOnDevice != null:
        return messageReadOnDevice(_that);
      case Message_SmsConfirmSent() when smsConfirmSent != null:
        return smsConfirmSent(_that);
      case Message_MarkUnread() when markUnread != null:
        return markUnread(_that);
      case Message_PeerCacheInvalidate() when peerCacheInvalidate != null:
        return peerCacheInvalidate(_that);
      case Message_UpdateExtension() when updateExtension != null:
        return updateExtension(_that);
      case Message_Error() when error != null:
        return error(_that);
      case Message_MoveToRecycleBin() when moveToRecycleBin != null:
        return moveToRecycleBin(_that);
      case Message_RecoverChat() when recoverChat != null:
        return recoverChat(_that);
      case Message_PermanentDelete() when permanentDelete != null:
        return permanentDelete(_that);
      case Message_Unschedule() when unschedule != null:
        return unschedule(_that);
      case Message_UpdateProfile() when updateProfile != null:
        return updateProfile(_that);
      case Message_UpdateProfileSharing() when updateProfileSharing != null:
        return updateProfileSharing(_that);
      case Message_ShareProfile() when shareProfile != null:
        return shareProfile(_that);
      case Message_NotifyAnyways() when notifyAnyways != null:
        return notifyAnyways(_that);
      case Message_SetTranscriptBackground()
          when setTranscriptBackground != null:
        return setTranscriptBackground(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(Message_Message value) message,
    required TResult Function(Message_RenameMessage value) renameMessage,
    required TResult Function(Message_ChangeParticipants value)
        changeParticipants,
    required TResult Function(Message_React value) react,
    required TResult Function(Message_Delivered value) delivered,
    required TResult Function(Message_Read value) read,
    required TResult Function(Message_Typing value) typing,
    required TResult Function(Message_Unsend value) unsend,
    required TResult Function(Message_Edit value) edit,
    required TResult Function(Message_IconChange value) iconChange,
    required TResult Function(Message_EnableSmsActivation value)
        enableSmsActivation,
    required TResult Function(Message_MessageReadOnDevice value)
        messageReadOnDevice,
    required TResult Function(Message_SmsConfirmSent value) smsConfirmSent,
    required TResult Function(Message_MarkUnread value) markUnread,
    required TResult Function(Message_PeerCacheInvalidate value)
        peerCacheInvalidate,
    required TResult Function(Message_UpdateExtension value) updateExtension,
    required TResult Function(Message_Error value) error,
    required TResult Function(Message_MoveToRecycleBin value) moveToRecycleBin,
    required TResult Function(Message_RecoverChat value) recoverChat,
    required TResult Function(Message_PermanentDelete value) permanentDelete,
    required TResult Function(Message_Unschedule value) unschedule,
    required TResult Function(Message_UpdateProfile value) updateProfile,
    required TResult Function(Message_UpdateProfileSharing value)
        updateProfileSharing,
    required TResult Function(Message_ShareProfile value) shareProfile,
    required TResult Function(Message_NotifyAnyways value) notifyAnyways,
    required TResult Function(Message_SetTranscriptBackground value)
        setTranscriptBackground,
  }) {
    final _that = this;
    switch (_that) {
      case Message_Message():
        return message(_that);
      case Message_RenameMessage():
        return renameMessage(_that);
      case Message_ChangeParticipants():
        return changeParticipants(_that);
      case Message_React():
        return react(_that);
      case Message_Delivered():
        return delivered(_that);
      case Message_Read():
        return read(_that);
      case Message_Typing():
        return typing(_that);
      case Message_Unsend():
        return unsend(_that);
      case Message_Edit():
        return edit(_that);
      case Message_IconChange():
        return iconChange(_that);
      case Message_EnableSmsActivation():
        return enableSmsActivation(_that);
      case Message_MessageReadOnDevice():
        return messageReadOnDevice(_that);
      case Message_SmsConfirmSent():
        return smsConfirmSent(_that);
      case Message_MarkUnread():
        return markUnread(_that);
      case Message_PeerCacheInvalidate():
        return peerCacheInvalidate(_that);
      case Message_UpdateExtension():
        return updateExtension(_that);
      case Message_Error():
        return error(_that);
      case Message_MoveToRecycleBin():
        return moveToRecycleBin(_that);
      case Message_RecoverChat():
        return recoverChat(_that);
      case Message_PermanentDelete():
        return permanentDelete(_that);
      case Message_Unschedule():
        return unschedule(_that);
      case Message_UpdateProfile():
        return updateProfile(_that);
      case Message_UpdateProfileSharing():
        return updateProfileSharing(_that);
      case Message_ShareProfile():
        return shareProfile(_that);
      case Message_NotifyAnyways():
        return notifyAnyways(_that);
      case Message_SetTranscriptBackground():
        return setTranscriptBackground(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(Message_Message value)? message,
    TResult? Function(Message_RenameMessage value)? renameMessage,
    TResult? Function(Message_ChangeParticipants value)? changeParticipants,
    TResult? Function(Message_React value)? react,
    TResult? Function(Message_Delivered value)? delivered,
    TResult? Function(Message_Read value)? read,
    TResult? Function(Message_Typing value)? typing,
    TResult? Function(Message_Unsend value)? unsend,
    TResult? Function(Message_Edit value)? edit,
    TResult? Function(Message_IconChange value)? iconChange,
    TResult? Function(Message_EnableSmsActivation value)? enableSmsActivation,
    TResult? Function(Message_MessageReadOnDevice value)? messageReadOnDevice,
    TResult? Function(Message_SmsConfirmSent value)? smsConfirmSent,
    TResult? Function(Message_MarkUnread value)? markUnread,
    TResult? Function(Message_PeerCacheInvalidate value)? peerCacheInvalidate,
    TResult? Function(Message_UpdateExtension value)? updateExtension,
    TResult? Function(Message_Error value)? error,
    TResult? Function(Message_MoveToRecycleBin value)? moveToRecycleBin,
    TResult? Function(Message_RecoverChat value)? recoverChat,
    TResult? Function(Message_PermanentDelete value)? permanentDelete,
    TResult? Function(Message_Unschedule value)? unschedule,
    TResult? Function(Message_UpdateProfile value)? updateProfile,
    TResult? Function(Message_UpdateProfileSharing value)? updateProfileSharing,
    TResult? Function(Message_ShareProfile value)? shareProfile,
    TResult? Function(Message_NotifyAnyways value)? notifyAnyways,
    TResult? Function(Message_SetTranscriptBackground value)?
        setTranscriptBackground,
  }) {
    final _that = this;
    switch (_that) {
      case Message_Message() when message != null:
        return message(_that);
      case Message_RenameMessage() when renameMessage != null:
        return renameMessage(_that);
      case Message_ChangeParticipants() when changeParticipants != null:
        return changeParticipants(_that);
      case Message_React() when react != null:
        return react(_that);
      case Message_Delivered() when delivered != null:
        return delivered(_that);
      case Message_Read() when read != null:
        return read(_that);
      case Message_Typing() when typing != null:
        return typing(_that);
      case Message_Unsend() when unsend != null:
        return unsend(_that);
      case Message_Edit() when edit != null:
        return edit(_that);
      case Message_IconChange() when iconChange != null:
        return iconChange(_that);
      case Message_EnableSmsActivation() when enableSmsActivation != null:
        return enableSmsActivation(_that);
      case Message_MessageReadOnDevice() when messageReadOnDevice != null:
        return messageReadOnDevice(_that);
      case Message_SmsConfirmSent() when smsConfirmSent != null:
        return smsConfirmSent(_that);
      case Message_MarkUnread() when markUnread != null:
        return markUnread(_that);
      case Message_PeerCacheInvalidate() when peerCacheInvalidate != null:
        return peerCacheInvalidate(_that);
      case Message_UpdateExtension() when updateExtension != null:
        return updateExtension(_that);
      case Message_Error() when error != null:
        return error(_that);
      case Message_MoveToRecycleBin() when moveToRecycleBin != null:
        return moveToRecycleBin(_that);
      case Message_RecoverChat() when recoverChat != null:
        return recoverChat(_that);
      case Message_PermanentDelete() when permanentDelete != null:
        return permanentDelete(_that);
      case Message_Unschedule() when unschedule != null:
        return unschedule(_that);
      case Message_UpdateProfile() when updateProfile != null:
        return updateProfile(_that);
      case Message_UpdateProfileSharing() when updateProfileSharing != null:
        return updateProfileSharing(_that);
      case Message_ShareProfile() when shareProfile != null:
        return shareProfile(_that);
      case Message_NotifyAnyways() when notifyAnyways != null:
        return notifyAnyways(_that);
      case Message_SetTranscriptBackground()
          when setTranscriptBackground != null:
        return setTranscriptBackground(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function(NormalMessage field0)? message,
    TResult Function(RenameMessage field0)? renameMessage,
    TResult Function(ChangeParticipantMessage field0)? changeParticipants,
    TResult Function(ReactMessage field0)? react,
    TResult Function()? delivered,
    TResult Function()? read,
    TResult Function(bool field0, TypingApp? field1)? typing,
    TResult Function(UnsendMessage field0)? unsend,
    TResult Function(EditMessage field0)? edit,
    TResult Function(IconChangeMessage field0)? iconChange,
    TResult Function(bool field0)? enableSmsActivation,
    TResult Function()? messageReadOnDevice,
    TResult Function(bool field0)? smsConfirmSent,
    TResult Function()? markUnread,
    TResult Function()? peerCacheInvalidate,
    TResult Function(UpdateExtensionMessage field0)? updateExtension,
    TResult Function(ErrorMessage field0)? error,
    TResult Function(MoveToRecycleBinMessage field0)? moveToRecycleBin,
    TResult Function(OperatedChat field0)? recoverChat,
    TResult Function(PermanentDeleteMessage field0)? permanentDelete,
    TResult Function()? unschedule,
    TResult Function(UpdateProfileMessage field0)? updateProfile,
    TResult Function(UpdateProfileSharingMessage field0)? updateProfileSharing,
    TResult Function(ShareProfileMessage field0)? shareProfile,
    TResult Function()? notifyAnyways,
    TResult Function(SetTranscriptBackgroundMessage field0)?
        setTranscriptBackground,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case Message_Message() when message != null:
        return message(_that.field0);
      case Message_RenameMessage() when renameMessage != null:
        return renameMessage(_that.field0);
      case Message_ChangeParticipants() when changeParticipants != null:
        return changeParticipants(_that.field0);
      case Message_React() when react != null:
        return react(_that.field0);
      case Message_Delivered() when delivered != null:
        return delivered();
      case Message_Read() when read != null:
        return read();
      case Message_Typing() when typing != null:
        return typing(_that.field0, _that.field1);
      case Message_Unsend() when unsend != null:
        return unsend(_that.field0);
      case Message_Edit() when edit != null:
        return edit(_that.field0);
      case Message_IconChange() when iconChange != null:
        return iconChange(_that.field0);
      case Message_EnableSmsActivation() when enableSmsActivation != null:
        return enableSmsActivation(_that.field0);
      case Message_MessageReadOnDevice() when messageReadOnDevice != null:
        return messageReadOnDevice();
      case Message_SmsConfirmSent() when smsConfirmSent != null:
        return smsConfirmSent(_that.field0);
      case Message_MarkUnread() when markUnread != null:
        return markUnread();
      case Message_PeerCacheInvalidate() when peerCacheInvalidate != null:
        return peerCacheInvalidate();
      case Message_UpdateExtension() when updateExtension != null:
        return updateExtension(_that.field0);
      case Message_Error() when error != null:
        return error(_that.field0);
      case Message_MoveToRecycleBin() when moveToRecycleBin != null:
        return moveToRecycleBin(_that.field0);
      case Message_RecoverChat() when recoverChat != null:
        return recoverChat(_that.field0);
      case Message_PermanentDelete() when permanentDelete != null:
        return permanentDelete(_that.field0);
      case Message_Unschedule() when unschedule != null:
        return unschedule();
      case Message_UpdateProfile() when updateProfile != null:
        return updateProfile(_that.field0);
      case Message_UpdateProfileSharing() when updateProfileSharing != null:
        return updateProfileSharing(_that.field0);
      case Message_ShareProfile() when shareProfile != null:
        return shareProfile(_that.field0);
      case Message_NotifyAnyways() when notifyAnyways != null:
        return notifyAnyways();
      case Message_SetTranscriptBackground()
          when setTranscriptBackground != null:
        return setTranscriptBackground(_that.field0);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function(NormalMessage field0) message,
    required TResult Function(RenameMessage field0) renameMessage,
    required TResult Function(ChangeParticipantMessage field0)
        changeParticipants,
    required TResult Function(ReactMessage field0) react,
    required TResult Function() delivered,
    required TResult Function() read,
    required TResult Function(bool field0, TypingApp? field1) typing,
    required TResult Function(UnsendMessage field0) unsend,
    required TResult Function(EditMessage field0) edit,
    required TResult Function(IconChangeMessage field0) iconChange,
    required TResult Function(bool field0) enableSmsActivation,
    required TResult Function() messageReadOnDevice,
    required TResult Function(bool field0) smsConfirmSent,
    required TResult Function() markUnread,
    required TResult Function() peerCacheInvalidate,
    required TResult Function(UpdateExtensionMessage field0) updateExtension,
    required TResult Function(ErrorMessage field0) error,
    required TResult Function(MoveToRecycleBinMessage field0) moveToRecycleBin,
    required TResult Function(OperatedChat field0) recoverChat,
    required TResult Function(PermanentDeleteMessage field0) permanentDelete,
    required TResult Function() unschedule,
    required TResult Function(UpdateProfileMessage field0) updateProfile,
    required TResult Function(UpdateProfileSharingMessage field0)
        updateProfileSharing,
    required TResult Function(ShareProfileMessage field0) shareProfile,
    required TResult Function() notifyAnyways,
    required TResult Function(SetTranscriptBackgroundMessage field0)
        setTranscriptBackground,
  }) {
    final _that = this;
    switch (_that) {
      case Message_Message():
        return message(_that.field0);
      case Message_RenameMessage():
        return renameMessage(_that.field0);
      case Message_ChangeParticipants():
        return changeParticipants(_that.field0);
      case Message_React():
        return react(_that.field0);
      case Message_Delivered():
        return delivered();
      case Message_Read():
        return read();
      case Message_Typing():
        return typing(_that.field0, _that.field1);
      case Message_Unsend():
        return unsend(_that.field0);
      case Message_Edit():
        return edit(_that.field0);
      case Message_IconChange():
        return iconChange(_that.field0);
      case Message_EnableSmsActivation():
        return enableSmsActivation(_that.field0);
      case Message_MessageReadOnDevice():
        return messageReadOnDevice();
      case Message_SmsConfirmSent():
        return smsConfirmSent(_that.field0);
      case Message_MarkUnread():
        return markUnread();
      case Message_PeerCacheInvalidate():
        return peerCacheInvalidate();
      case Message_UpdateExtension():
        return updateExtension(_that.field0);
      case Message_Error():
        return error(_that.field0);
      case Message_MoveToRecycleBin():
        return moveToRecycleBin(_that.field0);
      case Message_RecoverChat():
        return recoverChat(_that.field0);
      case Message_PermanentDelete():
        return permanentDelete(_that.field0);
      case Message_Unschedule():
        return unschedule();
      case Message_UpdateProfile():
        return updateProfile(_that.field0);
      case Message_UpdateProfileSharing():
        return updateProfileSharing(_that.field0);
      case Message_ShareProfile():
        return shareProfile(_that.field0);
      case Message_NotifyAnyways():
        return notifyAnyways();
      case Message_SetTranscriptBackground():
        return setTranscriptBackground(_that.field0);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function(NormalMessage field0)? message,
    TResult? Function(RenameMessage field0)? renameMessage,
    TResult? Function(ChangeParticipantMessage field0)? changeParticipants,
    TResult? Function(ReactMessage field0)? react,
    TResult? Function()? delivered,
    TResult? Function()? read,
    TResult? Function(bool field0, TypingApp? field1)? typing,
    TResult? Function(UnsendMessage field0)? unsend,
    TResult? Function(EditMessage field0)? edit,
    TResult? Function(IconChangeMessage field0)? iconChange,
    TResult? Function(bool field0)? enableSmsActivation,
    TResult? Function()? messageReadOnDevice,
    TResult? Function(bool field0)? smsConfirmSent,
    TResult? Function()? markUnread,
    TResult? Function()? peerCacheInvalidate,
    TResult? Function(UpdateExtensionMessage field0)? updateExtension,
    TResult? Function(ErrorMessage field0)? error,
    TResult? Function(MoveToRecycleBinMessage field0)? moveToRecycleBin,
    TResult? Function(OperatedChat field0)? recoverChat,
    TResult? Function(PermanentDeleteMessage field0)? permanentDelete,
    TResult? Function()? unschedule,
    TResult? Function(UpdateProfileMessage field0)? updateProfile,
    TResult? Function(UpdateProfileSharingMessage field0)? updateProfileSharing,
    TResult? Function(ShareProfileMessage field0)? shareProfile,
    TResult? Function()? notifyAnyways,
    TResult? Function(SetTranscriptBackgroundMessage field0)?
        setTranscriptBackground,
  }) {
    final _that = this;
    switch (_that) {
      case Message_Message() when message != null:
        return message(_that.field0);
      case Message_RenameMessage() when renameMessage != null:
        return renameMessage(_that.field0);
      case Message_ChangeParticipants() when changeParticipants != null:
        return changeParticipants(_that.field0);
      case Message_React() when react != null:
        return react(_that.field0);
      case Message_Delivered() when delivered != null:
        return delivered();
      case Message_Read() when read != null:
        return read();
      case Message_Typing() when typing != null:
        return typing(_that.field0, _that.field1);
      case Message_Unsend() when unsend != null:
        return unsend(_that.field0);
      case Message_Edit() when edit != null:
        return edit(_that.field0);
      case Message_IconChange() when iconChange != null:
        return iconChange(_that.field0);
      case Message_EnableSmsActivation() when enableSmsActivation != null:
        return enableSmsActivation(_that.field0);
      case Message_MessageReadOnDevice() when messageReadOnDevice != null:
        return messageReadOnDevice();
      case Message_SmsConfirmSent() when smsConfirmSent != null:
        return smsConfirmSent(_that.field0);
      case Message_MarkUnread() when markUnread != null:
        return markUnread();
      case Message_PeerCacheInvalidate() when peerCacheInvalidate != null:
        return peerCacheInvalidate();
      case Message_UpdateExtension() when updateExtension != null:
        return updateExtension(_that.field0);
      case Message_Error() when error != null:
        return error(_that.field0);
      case Message_MoveToRecycleBin() when moveToRecycleBin != null:
        return moveToRecycleBin(_that.field0);
      case Message_RecoverChat() when recoverChat != null:
        return recoverChat(_that.field0);
      case Message_PermanentDelete() when permanentDelete != null:
        return permanentDelete(_that.field0);
      case Message_Unschedule() when unschedule != null:
        return unschedule();
      case Message_UpdateProfile() when updateProfile != null:
        return updateProfile(_that.field0);
      case Message_UpdateProfileSharing() when updateProfileSharing != null:
        return updateProfileSharing(_that.field0);
      case Message_ShareProfile() when shareProfile != null:
        return shareProfile(_that.field0);
      case Message_NotifyAnyways() when notifyAnyways != null:
        return notifyAnyways();
      case Message_SetTranscriptBackground()
          when setTranscriptBackground != null:
        return setTranscriptBackground(_that.field0);
      case _:
        return null;
    }
  }
}

/// @nodoc

class Message_Message extends Message {
  const Message_Message(this.field0) : super._();

  final NormalMessage field0;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $Message_MessageCopyWith<Message_Message> get copyWith =>
      _$Message_MessageCopyWithImpl<Message_Message>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Message_Message &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'Message.message(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $Message_MessageCopyWith<$Res>
    implements $MessageCopyWith<$Res> {
  factory $Message_MessageCopyWith(
          Message_Message value, $Res Function(Message_Message) _then) =
      _$Message_MessageCopyWithImpl;
  @useResult
  $Res call({NormalMessage field0});
}

/// @nodoc
class _$Message_MessageCopyWithImpl<$Res>
    implements $Message_MessageCopyWith<$Res> {
  _$Message_MessageCopyWithImpl(this._self, this._then);

  final Message_Message _self;
  final $Res Function(Message_Message) _then;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(Message_Message(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as NormalMessage,
    ));
  }
}

/// @nodoc

class Message_RenameMessage extends Message {
  const Message_RenameMessage(this.field0) : super._();

  final RenameMessage field0;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $Message_RenameMessageCopyWith<Message_RenameMessage> get copyWith =>
      _$Message_RenameMessageCopyWithImpl<Message_RenameMessage>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Message_RenameMessage &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'Message.renameMessage(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $Message_RenameMessageCopyWith<$Res>
    implements $MessageCopyWith<$Res> {
  factory $Message_RenameMessageCopyWith(Message_RenameMessage value,
          $Res Function(Message_RenameMessage) _then) =
      _$Message_RenameMessageCopyWithImpl;
  @useResult
  $Res call({RenameMessage field0});
}

/// @nodoc
class _$Message_RenameMessageCopyWithImpl<$Res>
    implements $Message_RenameMessageCopyWith<$Res> {
  _$Message_RenameMessageCopyWithImpl(this._self, this._then);

  final Message_RenameMessage _self;
  final $Res Function(Message_RenameMessage) _then;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(Message_RenameMessage(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as RenameMessage,
    ));
  }
}

/// @nodoc

class Message_ChangeParticipants extends Message {
  const Message_ChangeParticipants(this.field0) : super._();

  final ChangeParticipantMessage field0;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $Message_ChangeParticipantsCopyWith<Message_ChangeParticipants>
      get copyWith =>
          _$Message_ChangeParticipantsCopyWithImpl<Message_ChangeParticipants>(
              this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Message_ChangeParticipants &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'Message.changeParticipants(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $Message_ChangeParticipantsCopyWith<$Res>
    implements $MessageCopyWith<$Res> {
  factory $Message_ChangeParticipantsCopyWith(Message_ChangeParticipants value,
          $Res Function(Message_ChangeParticipants) _then) =
      _$Message_ChangeParticipantsCopyWithImpl;
  @useResult
  $Res call({ChangeParticipantMessage field0});
}

/// @nodoc
class _$Message_ChangeParticipantsCopyWithImpl<$Res>
    implements $Message_ChangeParticipantsCopyWith<$Res> {
  _$Message_ChangeParticipantsCopyWithImpl(this._self, this._then);

  final Message_ChangeParticipants _self;
  final $Res Function(Message_ChangeParticipants) _then;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(Message_ChangeParticipants(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as ChangeParticipantMessage,
    ));
  }
}

/// @nodoc

class Message_React extends Message {
  const Message_React(this.field0) : super._();

  final ReactMessage field0;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $Message_ReactCopyWith<Message_React> get copyWith =>
      _$Message_ReactCopyWithImpl<Message_React>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Message_React &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'Message.react(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $Message_ReactCopyWith<$Res>
    implements $MessageCopyWith<$Res> {
  factory $Message_ReactCopyWith(
          Message_React value, $Res Function(Message_React) _then) =
      _$Message_ReactCopyWithImpl;
  @useResult
  $Res call({ReactMessage field0});
}

/// @nodoc
class _$Message_ReactCopyWithImpl<$Res>
    implements $Message_ReactCopyWith<$Res> {
  _$Message_ReactCopyWithImpl(this._self, this._then);

  final Message_React _self;
  final $Res Function(Message_React) _then;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(Message_React(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as ReactMessage,
    ));
  }
}

/// @nodoc

class Message_Delivered extends Message {
  const Message_Delivered() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is Message_Delivered);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'Message.delivered()';
  }
}

/// @nodoc

class Message_Read extends Message {
  const Message_Read() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is Message_Read);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'Message.read()';
  }
}

/// @nodoc

class Message_Typing extends Message {
  const Message_Typing(this.field0, [this.field1]) : super._();

  final bool field0;
  final TypingApp? field1;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $Message_TypingCopyWith<Message_Typing> get copyWith =>
      _$Message_TypingCopyWithImpl<Message_Typing>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Message_Typing &&
            (identical(other.field0, field0) || other.field0 == field0) &&
            (identical(other.field1, field1) || other.field1 == field1));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0, field1);

  @override
  String toString() {
    return 'Message.typing(field0: $field0, field1: $field1)';
  }
}

/// @nodoc
abstract mixin class $Message_TypingCopyWith<$Res>
    implements $MessageCopyWith<$Res> {
  factory $Message_TypingCopyWith(
          Message_Typing value, $Res Function(Message_Typing) _then) =
      _$Message_TypingCopyWithImpl;
  @useResult
  $Res call({bool field0, TypingApp? field1});
}

/// @nodoc
class _$Message_TypingCopyWithImpl<$Res>
    implements $Message_TypingCopyWith<$Res> {
  _$Message_TypingCopyWithImpl(this._self, this._then);

  final Message_Typing _self;
  final $Res Function(Message_Typing) _then;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
    Object? field1 = freezed,
  }) {
    return _then(Message_Typing(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as bool,
      freezed == field1
          ? _self.field1
          : field1 // ignore: cast_nullable_to_non_nullable
              as TypingApp?,
    ));
  }
}

/// @nodoc

class Message_Unsend extends Message {
  const Message_Unsend(this.field0) : super._();

  final UnsendMessage field0;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $Message_UnsendCopyWith<Message_Unsend> get copyWith =>
      _$Message_UnsendCopyWithImpl<Message_Unsend>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Message_Unsend &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'Message.unsend(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $Message_UnsendCopyWith<$Res>
    implements $MessageCopyWith<$Res> {
  factory $Message_UnsendCopyWith(
          Message_Unsend value, $Res Function(Message_Unsend) _then) =
      _$Message_UnsendCopyWithImpl;
  @useResult
  $Res call({UnsendMessage field0});
}

/// @nodoc
class _$Message_UnsendCopyWithImpl<$Res>
    implements $Message_UnsendCopyWith<$Res> {
  _$Message_UnsendCopyWithImpl(this._self, this._then);

  final Message_Unsend _self;
  final $Res Function(Message_Unsend) _then;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(Message_Unsend(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as UnsendMessage,
    ));
  }
}

/// @nodoc

class Message_Edit extends Message {
  const Message_Edit(this.field0) : super._();

  final EditMessage field0;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $Message_EditCopyWith<Message_Edit> get copyWith =>
      _$Message_EditCopyWithImpl<Message_Edit>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Message_Edit &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'Message.edit(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $Message_EditCopyWith<$Res>
    implements $MessageCopyWith<$Res> {
  factory $Message_EditCopyWith(
          Message_Edit value, $Res Function(Message_Edit) _then) =
      _$Message_EditCopyWithImpl;
  @useResult
  $Res call({EditMessage field0});
}

/// @nodoc
class _$Message_EditCopyWithImpl<$Res> implements $Message_EditCopyWith<$Res> {
  _$Message_EditCopyWithImpl(this._self, this._then);

  final Message_Edit _self;
  final $Res Function(Message_Edit) _then;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(Message_Edit(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as EditMessage,
    ));
  }
}

/// @nodoc

class Message_IconChange extends Message {
  const Message_IconChange(this.field0) : super._();

  final IconChangeMessage field0;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $Message_IconChangeCopyWith<Message_IconChange> get copyWith =>
      _$Message_IconChangeCopyWithImpl<Message_IconChange>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Message_IconChange &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'Message.iconChange(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $Message_IconChangeCopyWith<$Res>
    implements $MessageCopyWith<$Res> {
  factory $Message_IconChangeCopyWith(
          Message_IconChange value, $Res Function(Message_IconChange) _then) =
      _$Message_IconChangeCopyWithImpl;
  @useResult
  $Res call({IconChangeMessage field0});
}

/// @nodoc
class _$Message_IconChangeCopyWithImpl<$Res>
    implements $Message_IconChangeCopyWith<$Res> {
  _$Message_IconChangeCopyWithImpl(this._self, this._then);

  final Message_IconChange _self;
  final $Res Function(Message_IconChange) _then;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(Message_IconChange(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as IconChangeMessage,
    ));
  }
}

/// @nodoc

class Message_EnableSmsActivation extends Message {
  const Message_EnableSmsActivation(this.field0) : super._();

  final bool field0;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $Message_EnableSmsActivationCopyWith<Message_EnableSmsActivation>
      get copyWith => _$Message_EnableSmsActivationCopyWithImpl<
          Message_EnableSmsActivation>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Message_EnableSmsActivation &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'Message.enableSmsActivation(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $Message_EnableSmsActivationCopyWith<$Res>
    implements $MessageCopyWith<$Res> {
  factory $Message_EnableSmsActivationCopyWith(
          Message_EnableSmsActivation value,
          $Res Function(Message_EnableSmsActivation) _then) =
      _$Message_EnableSmsActivationCopyWithImpl;
  @useResult
  $Res call({bool field0});
}

/// @nodoc
class _$Message_EnableSmsActivationCopyWithImpl<$Res>
    implements $Message_EnableSmsActivationCopyWith<$Res> {
  _$Message_EnableSmsActivationCopyWithImpl(this._self, this._then);

  final Message_EnableSmsActivation _self;
  final $Res Function(Message_EnableSmsActivation) _then;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(Message_EnableSmsActivation(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as bool,
    ));
  }
}

/// @nodoc

class Message_MessageReadOnDevice extends Message {
  const Message_MessageReadOnDevice() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Message_MessageReadOnDevice);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'Message.messageReadOnDevice()';
  }
}

/// @nodoc

class Message_SmsConfirmSent extends Message {
  const Message_SmsConfirmSent(this.field0) : super._();

  final bool field0;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $Message_SmsConfirmSentCopyWith<Message_SmsConfirmSent> get copyWith =>
      _$Message_SmsConfirmSentCopyWithImpl<Message_SmsConfirmSent>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Message_SmsConfirmSent &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'Message.smsConfirmSent(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $Message_SmsConfirmSentCopyWith<$Res>
    implements $MessageCopyWith<$Res> {
  factory $Message_SmsConfirmSentCopyWith(Message_SmsConfirmSent value,
          $Res Function(Message_SmsConfirmSent) _then) =
      _$Message_SmsConfirmSentCopyWithImpl;
  @useResult
  $Res call({bool field0});
}

/// @nodoc
class _$Message_SmsConfirmSentCopyWithImpl<$Res>
    implements $Message_SmsConfirmSentCopyWith<$Res> {
  _$Message_SmsConfirmSentCopyWithImpl(this._self, this._then);

  final Message_SmsConfirmSent _self;
  final $Res Function(Message_SmsConfirmSent) _then;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(Message_SmsConfirmSent(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as bool,
    ));
  }
}

/// @nodoc

class Message_MarkUnread extends Message {
  const Message_MarkUnread() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is Message_MarkUnread);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'Message.markUnread()';
  }
}

/// @nodoc

class Message_PeerCacheInvalidate extends Message {
  const Message_PeerCacheInvalidate() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Message_PeerCacheInvalidate);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'Message.peerCacheInvalidate()';
  }
}

/// @nodoc

class Message_UpdateExtension extends Message {
  const Message_UpdateExtension(this.field0) : super._();

  final UpdateExtensionMessage field0;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $Message_UpdateExtensionCopyWith<Message_UpdateExtension> get copyWith =>
      _$Message_UpdateExtensionCopyWithImpl<Message_UpdateExtension>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Message_UpdateExtension &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'Message.updateExtension(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $Message_UpdateExtensionCopyWith<$Res>
    implements $MessageCopyWith<$Res> {
  factory $Message_UpdateExtensionCopyWith(Message_UpdateExtension value,
          $Res Function(Message_UpdateExtension) _then) =
      _$Message_UpdateExtensionCopyWithImpl;
  @useResult
  $Res call({UpdateExtensionMessage field0});
}

/// @nodoc
class _$Message_UpdateExtensionCopyWithImpl<$Res>
    implements $Message_UpdateExtensionCopyWith<$Res> {
  _$Message_UpdateExtensionCopyWithImpl(this._self, this._then);

  final Message_UpdateExtension _self;
  final $Res Function(Message_UpdateExtension) _then;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(Message_UpdateExtension(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as UpdateExtensionMessage,
    ));
  }
}

/// @nodoc

class Message_Error extends Message {
  const Message_Error(this.field0) : super._();

  final ErrorMessage field0;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $Message_ErrorCopyWith<Message_Error> get copyWith =>
      _$Message_ErrorCopyWithImpl<Message_Error>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Message_Error &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'Message.error(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $Message_ErrorCopyWith<$Res>
    implements $MessageCopyWith<$Res> {
  factory $Message_ErrorCopyWith(
          Message_Error value, $Res Function(Message_Error) _then) =
      _$Message_ErrorCopyWithImpl;
  @useResult
  $Res call({ErrorMessage field0});
}

/// @nodoc
class _$Message_ErrorCopyWithImpl<$Res>
    implements $Message_ErrorCopyWith<$Res> {
  _$Message_ErrorCopyWithImpl(this._self, this._then);

  final Message_Error _self;
  final $Res Function(Message_Error) _then;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(Message_Error(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as ErrorMessage,
    ));
  }
}

/// @nodoc

class Message_MoveToRecycleBin extends Message {
  const Message_MoveToRecycleBin(this.field0) : super._();

  final MoveToRecycleBinMessage field0;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $Message_MoveToRecycleBinCopyWith<Message_MoveToRecycleBin> get copyWith =>
      _$Message_MoveToRecycleBinCopyWithImpl<Message_MoveToRecycleBin>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Message_MoveToRecycleBin &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'Message.moveToRecycleBin(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $Message_MoveToRecycleBinCopyWith<$Res>
    implements $MessageCopyWith<$Res> {
  factory $Message_MoveToRecycleBinCopyWith(Message_MoveToRecycleBin value,
          $Res Function(Message_MoveToRecycleBin) _then) =
      _$Message_MoveToRecycleBinCopyWithImpl;
  @useResult
  $Res call({MoveToRecycleBinMessage field0});
}

/// @nodoc
class _$Message_MoveToRecycleBinCopyWithImpl<$Res>
    implements $Message_MoveToRecycleBinCopyWith<$Res> {
  _$Message_MoveToRecycleBinCopyWithImpl(this._self, this._then);

  final Message_MoveToRecycleBin _self;
  final $Res Function(Message_MoveToRecycleBin) _then;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(Message_MoveToRecycleBin(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as MoveToRecycleBinMessage,
    ));
  }
}

/// @nodoc

class Message_RecoverChat extends Message {
  const Message_RecoverChat(this.field0) : super._();

  final OperatedChat field0;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $Message_RecoverChatCopyWith<Message_RecoverChat> get copyWith =>
      _$Message_RecoverChatCopyWithImpl<Message_RecoverChat>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Message_RecoverChat &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'Message.recoverChat(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $Message_RecoverChatCopyWith<$Res>
    implements $MessageCopyWith<$Res> {
  factory $Message_RecoverChatCopyWith(
          Message_RecoverChat value, $Res Function(Message_RecoverChat) _then) =
      _$Message_RecoverChatCopyWithImpl;
  @useResult
  $Res call({OperatedChat field0});
}

/// @nodoc
class _$Message_RecoverChatCopyWithImpl<$Res>
    implements $Message_RecoverChatCopyWith<$Res> {
  _$Message_RecoverChatCopyWithImpl(this._self, this._then);

  final Message_RecoverChat _self;
  final $Res Function(Message_RecoverChat) _then;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(Message_RecoverChat(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as OperatedChat,
    ));
  }
}

/// @nodoc

class Message_PermanentDelete extends Message {
  const Message_PermanentDelete(this.field0) : super._();

  final PermanentDeleteMessage field0;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $Message_PermanentDeleteCopyWith<Message_PermanentDelete> get copyWith =>
      _$Message_PermanentDeleteCopyWithImpl<Message_PermanentDelete>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Message_PermanentDelete &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'Message.permanentDelete(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $Message_PermanentDeleteCopyWith<$Res>
    implements $MessageCopyWith<$Res> {
  factory $Message_PermanentDeleteCopyWith(Message_PermanentDelete value,
          $Res Function(Message_PermanentDelete) _then) =
      _$Message_PermanentDeleteCopyWithImpl;
  @useResult
  $Res call({PermanentDeleteMessage field0});
}

/// @nodoc
class _$Message_PermanentDeleteCopyWithImpl<$Res>
    implements $Message_PermanentDeleteCopyWith<$Res> {
  _$Message_PermanentDeleteCopyWithImpl(this._self, this._then);

  final Message_PermanentDelete _self;
  final $Res Function(Message_PermanentDelete) _then;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(Message_PermanentDelete(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as PermanentDeleteMessage,
    ));
  }
}

/// @nodoc

class Message_Unschedule extends Message {
  const Message_Unschedule() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is Message_Unschedule);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'Message.unschedule()';
  }
}

/// @nodoc

class Message_UpdateProfile extends Message {
  const Message_UpdateProfile(this.field0) : super._();

  final UpdateProfileMessage field0;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $Message_UpdateProfileCopyWith<Message_UpdateProfile> get copyWith =>
      _$Message_UpdateProfileCopyWithImpl<Message_UpdateProfile>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Message_UpdateProfile &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'Message.updateProfile(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $Message_UpdateProfileCopyWith<$Res>
    implements $MessageCopyWith<$Res> {
  factory $Message_UpdateProfileCopyWith(Message_UpdateProfile value,
          $Res Function(Message_UpdateProfile) _then) =
      _$Message_UpdateProfileCopyWithImpl;
  @useResult
  $Res call({UpdateProfileMessage field0});
}

/// @nodoc
class _$Message_UpdateProfileCopyWithImpl<$Res>
    implements $Message_UpdateProfileCopyWith<$Res> {
  _$Message_UpdateProfileCopyWithImpl(this._self, this._then);

  final Message_UpdateProfile _self;
  final $Res Function(Message_UpdateProfile) _then;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(Message_UpdateProfile(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as UpdateProfileMessage,
    ));
  }
}

/// @nodoc

class Message_UpdateProfileSharing extends Message {
  const Message_UpdateProfileSharing(this.field0) : super._();

  final UpdateProfileSharingMessage field0;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $Message_UpdateProfileSharingCopyWith<Message_UpdateProfileSharing>
      get copyWith => _$Message_UpdateProfileSharingCopyWithImpl<
          Message_UpdateProfileSharing>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Message_UpdateProfileSharing &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'Message.updateProfileSharing(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $Message_UpdateProfileSharingCopyWith<$Res>
    implements $MessageCopyWith<$Res> {
  factory $Message_UpdateProfileSharingCopyWith(
          Message_UpdateProfileSharing value,
          $Res Function(Message_UpdateProfileSharing) _then) =
      _$Message_UpdateProfileSharingCopyWithImpl;
  @useResult
  $Res call({UpdateProfileSharingMessage field0});
}

/// @nodoc
class _$Message_UpdateProfileSharingCopyWithImpl<$Res>
    implements $Message_UpdateProfileSharingCopyWith<$Res> {
  _$Message_UpdateProfileSharingCopyWithImpl(this._self, this._then);

  final Message_UpdateProfileSharing _self;
  final $Res Function(Message_UpdateProfileSharing) _then;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(Message_UpdateProfileSharing(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as UpdateProfileSharingMessage,
    ));
  }
}

/// @nodoc

class Message_ShareProfile extends Message {
  const Message_ShareProfile(this.field0) : super._();

  final ShareProfileMessage field0;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $Message_ShareProfileCopyWith<Message_ShareProfile> get copyWith =>
      _$Message_ShareProfileCopyWithImpl<Message_ShareProfile>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Message_ShareProfile &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'Message.shareProfile(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $Message_ShareProfileCopyWith<$Res>
    implements $MessageCopyWith<$Res> {
  factory $Message_ShareProfileCopyWith(Message_ShareProfile value,
          $Res Function(Message_ShareProfile) _then) =
      _$Message_ShareProfileCopyWithImpl;
  @useResult
  $Res call({ShareProfileMessage field0});
}

/// @nodoc
class _$Message_ShareProfileCopyWithImpl<$Res>
    implements $Message_ShareProfileCopyWith<$Res> {
  _$Message_ShareProfileCopyWithImpl(this._self, this._then);

  final Message_ShareProfile _self;
  final $Res Function(Message_ShareProfile) _then;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(Message_ShareProfile(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as ShareProfileMessage,
    ));
  }
}

/// @nodoc

class Message_NotifyAnyways extends Message {
  const Message_NotifyAnyways() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is Message_NotifyAnyways);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'Message.notifyAnyways()';
  }
}

/// @nodoc

class Message_SetTranscriptBackground extends Message {
  const Message_SetTranscriptBackground(this.field0) : super._();

  final SetTranscriptBackgroundMessage field0;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $Message_SetTranscriptBackgroundCopyWith<Message_SetTranscriptBackground>
      get copyWith => _$Message_SetTranscriptBackgroundCopyWithImpl<
          Message_SetTranscriptBackground>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Message_SetTranscriptBackground &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'Message.setTranscriptBackground(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $Message_SetTranscriptBackgroundCopyWith<$Res>
    implements $MessageCopyWith<$Res> {
  factory $Message_SetTranscriptBackgroundCopyWith(
          Message_SetTranscriptBackground value,
          $Res Function(Message_SetTranscriptBackground) _then) =
      _$Message_SetTranscriptBackgroundCopyWithImpl;
  @useResult
  $Res call({SetTranscriptBackgroundMessage field0});

  $SetTranscriptBackgroundMessageCopyWith<$Res> get field0;
}

/// @nodoc
class _$Message_SetTranscriptBackgroundCopyWithImpl<$Res>
    implements $Message_SetTranscriptBackgroundCopyWith<$Res> {
  _$Message_SetTranscriptBackgroundCopyWithImpl(this._self, this._then);

  final Message_SetTranscriptBackground _self;
  final $Res Function(Message_SetTranscriptBackground) _then;

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(Message_SetTranscriptBackground(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as SetTranscriptBackgroundMessage,
    ));
  }

  /// Create a copy of Message
  /// with the given fields replaced by the non-null parameter values.
  @override
  @pragma('vm:prefer-inline')
  $SetTranscriptBackgroundMessageCopyWith<$Res> get field0 {
    return $SetTranscriptBackgroundMessageCopyWith<$Res>(_self.field0, (value) {
      return _then(_self.copyWith(field0: value));
    });
  }
}

/// @nodoc
mixin _$MessagePart {
  Object get field0;

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is MessagePart &&
            const DeepCollectionEquality().equals(other.field0, field0));
  }

  @override
  int get hashCode =>
      Object.hash(runtimeType, const DeepCollectionEquality().hash(field0));

  @override
  String toString() {
    return 'MessagePart(field0: $field0)';
  }
}

/// @nodoc
class $MessagePartCopyWith<$Res> {
  $MessagePartCopyWith(MessagePart _, $Res Function(MessagePart) __);
}

/// Adds pattern-matching-related methods to [MessagePart].
extension MessagePartPatterns on MessagePart {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(MessagePart_Text value)? text,
    TResult Function(MessagePart_Attachment value)? attachment,
    TResult Function(MessagePart_Mention value)? mention,
    TResult Function(MessagePart_Object value)? object,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case MessagePart_Text() when text != null:
        return text(_that);
      case MessagePart_Attachment() when attachment != null:
        return attachment(_that);
      case MessagePart_Mention() when mention != null:
        return mention(_that);
      case MessagePart_Object() when object != null:
        return object(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(MessagePart_Text value) text,
    required TResult Function(MessagePart_Attachment value) attachment,
    required TResult Function(MessagePart_Mention value) mention,
    required TResult Function(MessagePart_Object value) object,
  }) {
    final _that = this;
    switch (_that) {
      case MessagePart_Text():
        return text(_that);
      case MessagePart_Attachment():
        return attachment(_that);
      case MessagePart_Mention():
        return mention(_that);
      case MessagePart_Object():
        return object(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(MessagePart_Text value)? text,
    TResult? Function(MessagePart_Attachment value)? attachment,
    TResult? Function(MessagePart_Mention value)? mention,
    TResult? Function(MessagePart_Object value)? object,
  }) {
    final _that = this;
    switch (_that) {
      case MessagePart_Text() when text != null:
        return text(_that);
      case MessagePart_Attachment() when attachment != null:
        return attachment(_that);
      case MessagePart_Mention() when mention != null:
        return mention(_that);
      case MessagePart_Object() when object != null:
        return object(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function(String field0, TextFormat field1)? text,
    TResult Function(Attachment field0)? attachment,
    TResult Function(String field0, String field1)? mention,
    TResult Function(String field0)? object,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case MessagePart_Text() when text != null:
        return text(_that.field0, _that.field1);
      case MessagePart_Attachment() when attachment != null:
        return attachment(_that.field0);
      case MessagePart_Mention() when mention != null:
        return mention(_that.field0, _that.field1);
      case MessagePart_Object() when object != null:
        return object(_that.field0);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function(String field0, TextFormat field1) text,
    required TResult Function(Attachment field0) attachment,
    required TResult Function(String field0, String field1) mention,
    required TResult Function(String field0) object,
  }) {
    final _that = this;
    switch (_that) {
      case MessagePart_Text():
        return text(_that.field0, _that.field1);
      case MessagePart_Attachment():
        return attachment(_that.field0);
      case MessagePart_Mention():
        return mention(_that.field0, _that.field1);
      case MessagePart_Object():
        return object(_that.field0);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function(String field0, TextFormat field1)? text,
    TResult? Function(Attachment field0)? attachment,
    TResult? Function(String field0, String field1)? mention,
    TResult? Function(String field0)? object,
  }) {
    final _that = this;
    switch (_that) {
      case MessagePart_Text() when text != null:
        return text(_that.field0, _that.field1);
      case MessagePart_Attachment() when attachment != null:
        return attachment(_that.field0);
      case MessagePart_Mention() when mention != null:
        return mention(_that.field0, _that.field1);
      case MessagePart_Object() when object != null:
        return object(_that.field0);
      case _:
        return null;
    }
  }
}

/// @nodoc

class MessagePart_Text extends MessagePart {
  const MessagePart_Text(this.field0, this.field1) : super._();

  @override
  final String field0;
  final TextFormat field1;

  /// Create a copy of MessagePart
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $MessagePart_TextCopyWith<MessagePart_Text> get copyWith =>
      _$MessagePart_TextCopyWithImpl<MessagePart_Text>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is MessagePart_Text &&
            (identical(other.field0, field0) || other.field0 == field0) &&
            (identical(other.field1, field1) || other.field1 == field1));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0, field1);

  @override
  String toString() {
    return 'MessagePart.text(field0: $field0, field1: $field1)';
  }
}

/// @nodoc
abstract mixin class $MessagePart_TextCopyWith<$Res>
    implements $MessagePartCopyWith<$Res> {
  factory $MessagePart_TextCopyWith(
          MessagePart_Text value, $Res Function(MessagePart_Text) _then) =
      _$MessagePart_TextCopyWithImpl;
  @useResult
  $Res call({String field0, TextFormat field1});

  $TextFormatCopyWith<$Res> get field1;
}

/// @nodoc
class _$MessagePart_TextCopyWithImpl<$Res>
    implements $MessagePart_TextCopyWith<$Res> {
  _$MessagePart_TextCopyWithImpl(this._self, this._then);

  final MessagePart_Text _self;
  final $Res Function(MessagePart_Text) _then;

  /// Create a copy of MessagePart
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
    Object? field1 = null,
  }) {
    return _then(MessagePart_Text(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as String,
      null == field1
          ? _self.field1
          : field1 // ignore: cast_nullable_to_non_nullable
              as TextFormat,
    ));
  }

  /// Create a copy of MessagePart
  /// with the given fields replaced by the non-null parameter values.
  @override
  @pragma('vm:prefer-inline')
  $TextFormatCopyWith<$Res> get field1 {
    return $TextFormatCopyWith<$Res>(_self.field1, (value) {
      return _then(_self.copyWith(field1: value));
    });
  }
}

/// @nodoc

class MessagePart_Attachment extends MessagePart {
  const MessagePart_Attachment(this.field0) : super._();

  @override
  final Attachment field0;

  /// Create a copy of MessagePart
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $MessagePart_AttachmentCopyWith<MessagePart_Attachment> get copyWith =>
      _$MessagePart_AttachmentCopyWithImpl<MessagePart_Attachment>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is MessagePart_Attachment &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'MessagePart.attachment(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $MessagePart_AttachmentCopyWith<$Res>
    implements $MessagePartCopyWith<$Res> {
  factory $MessagePart_AttachmentCopyWith(MessagePart_Attachment value,
          $Res Function(MessagePart_Attachment) _then) =
      _$MessagePart_AttachmentCopyWithImpl;
  @useResult
  $Res call({Attachment field0});
}

/// @nodoc
class _$MessagePart_AttachmentCopyWithImpl<$Res>
    implements $MessagePart_AttachmentCopyWith<$Res> {
  _$MessagePart_AttachmentCopyWithImpl(this._self, this._then);

  final MessagePart_Attachment _self;
  final $Res Function(MessagePart_Attachment) _then;

  /// Create a copy of MessagePart
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(MessagePart_Attachment(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as Attachment,
    ));
  }
}

/// @nodoc

class MessagePart_Mention extends MessagePart {
  const MessagePart_Mention(this.field0, this.field1) : super._();

  @override
  final String field0;
  final String field1;

  /// Create a copy of MessagePart
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $MessagePart_MentionCopyWith<MessagePart_Mention> get copyWith =>
      _$MessagePart_MentionCopyWithImpl<MessagePart_Mention>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is MessagePart_Mention &&
            (identical(other.field0, field0) || other.field0 == field0) &&
            (identical(other.field1, field1) || other.field1 == field1));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0, field1);

  @override
  String toString() {
    return 'MessagePart.mention(field0: $field0, field1: $field1)';
  }
}

/// @nodoc
abstract mixin class $MessagePart_MentionCopyWith<$Res>
    implements $MessagePartCopyWith<$Res> {
  factory $MessagePart_MentionCopyWith(
          MessagePart_Mention value, $Res Function(MessagePart_Mention) _then) =
      _$MessagePart_MentionCopyWithImpl;
  @useResult
  $Res call({String field0, String field1});
}

/// @nodoc
class _$MessagePart_MentionCopyWithImpl<$Res>
    implements $MessagePart_MentionCopyWith<$Res> {
  _$MessagePart_MentionCopyWithImpl(this._self, this._then);

  final MessagePart_Mention _self;
  final $Res Function(MessagePart_Mention) _then;

  /// Create a copy of MessagePart
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
    Object? field1 = null,
  }) {
    return _then(MessagePart_Mention(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as String,
      null == field1
          ? _self.field1
          : field1 // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

/// @nodoc

class MessagePart_Object extends MessagePart {
  const MessagePart_Object(this.field0) : super._();

  @override
  final String field0;

  /// Create a copy of MessagePart
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $MessagePart_ObjectCopyWith<MessagePart_Object> get copyWith =>
      _$MessagePart_ObjectCopyWithImpl<MessagePart_Object>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is MessagePart_Object &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'MessagePart.object(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $MessagePart_ObjectCopyWith<$Res>
    implements $MessagePartCopyWith<$Res> {
  factory $MessagePart_ObjectCopyWith(
          MessagePart_Object value, $Res Function(MessagePart_Object) _then) =
      _$MessagePart_ObjectCopyWithImpl;
  @useResult
  $Res call({String field0});
}

/// @nodoc
class _$MessagePart_ObjectCopyWithImpl<$Res>
    implements $MessagePart_ObjectCopyWith<$Res> {
  _$MessagePart_ObjectCopyWithImpl(this._self, this._then);

  final MessagePart_Object _self;
  final $Res Function(MessagePart_Object) _then;

  /// Create a copy of MessagePart
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(MessagePart_Object(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

/// @nodoc
mixin _$MessageTarget {
  Object get field0;

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is MessageTarget &&
            const DeepCollectionEquality().equals(other.field0, field0));
  }

  @override
  int get hashCode =>
      Object.hash(runtimeType, const DeepCollectionEquality().hash(field0));

  @override
  String toString() {
    return 'MessageTarget(field0: $field0)';
  }
}

/// @nodoc
class $MessageTargetCopyWith<$Res> {
  $MessageTargetCopyWith(MessageTarget _, $Res Function(MessageTarget) __);
}

/// Adds pattern-matching-related methods to [MessageTarget].
extension MessageTargetPatterns on MessageTarget {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(MessageTarget_Token value)? token,
    TResult Function(MessageTarget_Uuid value)? uuid,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case MessageTarget_Token() when token != null:
        return token(_that);
      case MessageTarget_Uuid() when uuid != null:
        return uuid(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(MessageTarget_Token value) token,
    required TResult Function(MessageTarget_Uuid value) uuid,
  }) {
    final _that = this;
    switch (_that) {
      case MessageTarget_Token():
        return token(_that);
      case MessageTarget_Uuid():
        return uuid(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(MessageTarget_Token value)? token,
    TResult? Function(MessageTarget_Uuid value)? uuid,
  }) {
    final _that = this;
    switch (_that) {
      case MessageTarget_Token() when token != null:
        return token(_that);
      case MessageTarget_Uuid() when uuid != null:
        return uuid(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function(Uint8List field0)? token,
    TResult Function(String field0)? uuid,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case MessageTarget_Token() when token != null:
        return token(_that.field0);
      case MessageTarget_Uuid() when uuid != null:
        return uuid(_that.field0);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function(Uint8List field0) token,
    required TResult Function(String field0) uuid,
  }) {
    final _that = this;
    switch (_that) {
      case MessageTarget_Token():
        return token(_that.field0);
      case MessageTarget_Uuid():
        return uuid(_that.field0);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function(Uint8List field0)? token,
    TResult? Function(String field0)? uuid,
  }) {
    final _that = this;
    switch (_that) {
      case MessageTarget_Token() when token != null:
        return token(_that.field0);
      case MessageTarget_Uuid() when uuid != null:
        return uuid(_that.field0);
      case _:
        return null;
    }
  }
}

/// @nodoc

class MessageTarget_Token extends MessageTarget {
  const MessageTarget_Token(this.field0) : super._();

  @override
  final Uint8List field0;

  /// Create a copy of MessageTarget
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $MessageTarget_TokenCopyWith<MessageTarget_Token> get copyWith =>
      _$MessageTarget_TokenCopyWithImpl<MessageTarget_Token>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is MessageTarget_Token &&
            const DeepCollectionEquality().equals(other.field0, field0));
  }

  @override
  int get hashCode =>
      Object.hash(runtimeType, const DeepCollectionEquality().hash(field0));

  @override
  String toString() {
    return 'MessageTarget.token(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $MessageTarget_TokenCopyWith<$Res>
    implements $MessageTargetCopyWith<$Res> {
  factory $MessageTarget_TokenCopyWith(
          MessageTarget_Token value, $Res Function(MessageTarget_Token) _then) =
      _$MessageTarget_TokenCopyWithImpl;
  @useResult
  $Res call({Uint8List field0});
}

/// @nodoc
class _$MessageTarget_TokenCopyWithImpl<$Res>
    implements $MessageTarget_TokenCopyWith<$Res> {
  _$MessageTarget_TokenCopyWithImpl(this._self, this._then);

  final MessageTarget_Token _self;
  final $Res Function(MessageTarget_Token) _then;

  /// Create a copy of MessageTarget
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(MessageTarget_Token(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as Uint8List,
    ));
  }
}

/// @nodoc

class MessageTarget_Uuid extends MessageTarget {
  const MessageTarget_Uuid(this.field0) : super._();

  @override
  final String field0;

  /// Create a copy of MessageTarget
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $MessageTarget_UuidCopyWith<MessageTarget_Uuid> get copyWith =>
      _$MessageTarget_UuidCopyWithImpl<MessageTarget_Uuid>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is MessageTarget_Uuid &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'MessageTarget.uuid(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $MessageTarget_UuidCopyWith<$Res>
    implements $MessageTargetCopyWith<$Res> {
  factory $MessageTarget_UuidCopyWith(
          MessageTarget_Uuid value, $Res Function(MessageTarget_Uuid) _then) =
      _$MessageTarget_UuidCopyWithImpl;
  @useResult
  $Res call({String field0});
}

/// @nodoc
class _$MessageTarget_UuidCopyWithImpl<$Res>
    implements $MessageTarget_UuidCopyWith<$Res> {
  _$MessageTarget_UuidCopyWithImpl(this._self, this._then);

  final MessageTarget_Uuid _self;
  final $Res Function(MessageTarget_Uuid) _then;

  /// Create a copy of MessageTarget
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(MessageTarget_Uuid(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

/// @nodoc
mixin _$MessageType {
  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is MessageType);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'MessageType()';
  }
}

/// @nodoc
class $MessageTypeCopyWith<$Res> {
  $MessageTypeCopyWith(MessageType _, $Res Function(MessageType) __);
}

/// Adds pattern-matching-related methods to [MessageType].
extension MessageTypePatterns on MessageType {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(MessageType_IMessage value)? iMessage,
    TResult Function(MessageType_SMS value)? sms,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case MessageType_IMessage() when iMessage != null:
        return iMessage(_that);
      case MessageType_SMS() when sms != null:
        return sms(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(MessageType_IMessage value) iMessage,
    required TResult Function(MessageType_SMS value) sms,
  }) {
    final _that = this;
    switch (_that) {
      case MessageType_IMessage():
        return iMessage(_that);
      case MessageType_SMS():
        return sms(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(MessageType_IMessage value)? iMessage,
    TResult? Function(MessageType_SMS value)? sms,
  }) {
    final _that = this;
    switch (_that) {
      case MessageType_IMessage() when iMessage != null:
        return iMessage(_that);
      case MessageType_SMS() when sms != null:
        return sms(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function()? iMessage,
    TResult Function(bool isPhone, String usingNumber, String? fromHandle)? sms,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case MessageType_IMessage() when iMessage != null:
        return iMessage();
      case MessageType_SMS() when sms != null:
        return sms(_that.isPhone, _that.usingNumber, _that.fromHandle);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function() iMessage,
    required TResult Function(
            bool isPhone, String usingNumber, String? fromHandle)
        sms,
  }) {
    final _that = this;
    switch (_that) {
      case MessageType_IMessage():
        return iMessage();
      case MessageType_SMS():
        return sms(_that.isPhone, _that.usingNumber, _that.fromHandle);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function()? iMessage,
    TResult? Function(bool isPhone, String usingNumber, String? fromHandle)?
        sms,
  }) {
    final _that = this;
    switch (_that) {
      case MessageType_IMessage() when iMessage != null:
        return iMessage();
      case MessageType_SMS() when sms != null:
        return sms(_that.isPhone, _that.usingNumber, _that.fromHandle);
      case _:
        return null;
    }
  }
}

/// @nodoc

class MessageType_IMessage extends MessageType {
  const MessageType_IMessage() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is MessageType_IMessage);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'MessageType.iMessage()';
  }
}

/// @nodoc

class MessageType_SMS extends MessageType {
  const MessageType_SMS(
      {required this.isPhone, required this.usingNumber, this.fromHandle})
      : super._();

  final bool isPhone;
  final String usingNumber;
  final String? fromHandle;

  /// Create a copy of MessageType
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $MessageType_SMSCopyWith<MessageType_SMS> get copyWith =>
      _$MessageType_SMSCopyWithImpl<MessageType_SMS>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is MessageType_SMS &&
            (identical(other.isPhone, isPhone) || other.isPhone == isPhone) &&
            (identical(other.usingNumber, usingNumber) ||
                other.usingNumber == usingNumber) &&
            (identical(other.fromHandle, fromHandle) ||
                other.fromHandle == fromHandle));
  }

  @override
  int get hashCode =>
      Object.hash(runtimeType, isPhone, usingNumber, fromHandle);

  @override
  String toString() {
    return 'MessageType.sms(isPhone: $isPhone, usingNumber: $usingNumber, fromHandle: $fromHandle)';
  }
}

/// @nodoc
abstract mixin class $MessageType_SMSCopyWith<$Res>
    implements $MessageTypeCopyWith<$Res> {
  factory $MessageType_SMSCopyWith(
          MessageType_SMS value, $Res Function(MessageType_SMS) _then) =
      _$MessageType_SMSCopyWithImpl;
  @useResult
  $Res call({bool isPhone, String usingNumber, String? fromHandle});
}

/// @nodoc
class _$MessageType_SMSCopyWithImpl<$Res>
    implements $MessageType_SMSCopyWith<$Res> {
  _$MessageType_SMSCopyWithImpl(this._self, this._then);

  final MessageType_SMS _self;
  final $Res Function(MessageType_SMS) _then;

  /// Create a copy of MessageType
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? isPhone = null,
    Object? usingNumber = null,
    Object? fromHandle = freezed,
  }) {
    return _then(MessageType_SMS(
      isPhone: null == isPhone
          ? _self.isPhone
          : isPhone // ignore: cast_nullable_to_non_nullable
              as bool,
      usingNumber: null == usingNumber
          ? _self.usingNumber
          : usingNumber // ignore: cast_nullable_to_non_nullable
              as String,
      fromHandle: freezed == fromHandle
          ? _self.fromHandle
          : fromHandle // ignore: cast_nullable_to_non_nullable
              as String?,
    ));
  }
}

/// @nodoc
mixin _$NumOrString {
  Object get field0;

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is NumOrString &&
            const DeepCollectionEquality().equals(other.field0, field0));
  }

  @override
  int get hashCode =>
      Object.hash(runtimeType, const DeepCollectionEquality().hash(field0));

  @override
  String toString() {
    return 'NumOrString(field0: $field0)';
  }
}

/// @nodoc
class $NumOrStringCopyWith<$Res> {
  $NumOrStringCopyWith(NumOrString _, $Res Function(NumOrString) __);
}

/// Adds pattern-matching-related methods to [NumOrString].
extension NumOrStringPatterns on NumOrString {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(NumOrString_Num value)? num,
    TResult Function(NumOrString_String value)? string,
    TResult Function(NumOrString_Bool value)? bool,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case NumOrString_Num() when num != null:
        return num(_that);
      case NumOrString_String() when string != null:
        return string(_that);
      case NumOrString_Bool() when bool != null:
        return bool(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(NumOrString_Num value) num,
    required TResult Function(NumOrString_String value) string,
    required TResult Function(NumOrString_Bool value) bool,
  }) {
    final _that = this;
    switch (_that) {
      case NumOrString_Num():
        return num(_that);
      case NumOrString_String():
        return string(_that);
      case NumOrString_Bool():
        return bool(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(NumOrString_Num value)? num,
    TResult? Function(NumOrString_String value)? string,
    TResult? Function(NumOrString_Bool value)? bool,
  }) {
    final _that = this;
    switch (_that) {
      case NumOrString_Num() when num != null:
        return num(_that);
      case NumOrString_String() when string != null:
        return string(_that);
      case NumOrString_Bool() when bool != null:
        return bool(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function(int field0)? num,
    TResult Function(String field0)? string,
    TResult Function(bool field0)? bool,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case NumOrString_Num() when num != null:
        return num(_that.field0);
      case NumOrString_String() when string != null:
        return string(_that.field0);
      case NumOrString_Bool() when bool != null:
        return bool(_that.field0);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function(int field0) num,
    required TResult Function(String field0) string,
    required TResult Function(bool field0) bool,
  }) {
    final _that = this;
    switch (_that) {
      case NumOrString_Num():
        return num(_that.field0);
      case NumOrString_String():
        return string(_that.field0);
      case NumOrString_Bool():
        return bool(_that.field0);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function(int field0)? num,
    TResult? Function(String field0)? string,
    TResult? Function(bool field0)? bool,
  }) {
    final _that = this;
    switch (_that) {
      case NumOrString_Num() when num != null:
        return num(_that.field0);
      case NumOrString_String() when string != null:
        return string(_that.field0);
      case NumOrString_Bool() when bool != null:
        return bool(_that.field0);
      case _:
        return null;
    }
  }
}

/// @nodoc

class NumOrString_Num extends NumOrString {
  const NumOrString_Num(this.field0) : super._();

  @override
  final int field0;

  /// Create a copy of NumOrString
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $NumOrString_NumCopyWith<NumOrString_Num> get copyWith =>
      _$NumOrString_NumCopyWithImpl<NumOrString_Num>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is NumOrString_Num &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'NumOrString.num(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $NumOrString_NumCopyWith<$Res>
    implements $NumOrStringCopyWith<$Res> {
  factory $NumOrString_NumCopyWith(
          NumOrString_Num value, $Res Function(NumOrString_Num) _then) =
      _$NumOrString_NumCopyWithImpl;
  @useResult
  $Res call({int field0});
}

/// @nodoc
class _$NumOrString_NumCopyWithImpl<$Res>
    implements $NumOrString_NumCopyWith<$Res> {
  _$NumOrString_NumCopyWithImpl(this._self, this._then);

  final NumOrString_Num _self;
  final $Res Function(NumOrString_Num) _then;

  /// Create a copy of NumOrString
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(NumOrString_Num(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as int,
    ));
  }
}

/// @nodoc

class NumOrString_String extends NumOrString {
  const NumOrString_String(this.field0) : super._();

  @override
  final String field0;

  /// Create a copy of NumOrString
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $NumOrString_StringCopyWith<NumOrString_String> get copyWith =>
      _$NumOrString_StringCopyWithImpl<NumOrString_String>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is NumOrString_String &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'NumOrString.string(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $NumOrString_StringCopyWith<$Res>
    implements $NumOrStringCopyWith<$Res> {
  factory $NumOrString_StringCopyWith(
          NumOrString_String value, $Res Function(NumOrString_String) _then) =
      _$NumOrString_StringCopyWithImpl;
  @useResult
  $Res call({String field0});
}

/// @nodoc
class _$NumOrString_StringCopyWithImpl<$Res>
    implements $NumOrString_StringCopyWith<$Res> {
  _$NumOrString_StringCopyWithImpl(this._self, this._then);

  final NumOrString_String _self;
  final $Res Function(NumOrString_String) _then;

  /// Create a copy of NumOrString
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(NumOrString_String(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

/// @nodoc

class NumOrString_Bool extends NumOrString {
  const NumOrString_Bool(this.field0) : super._();

  @override
  final bool field0;

  /// Create a copy of NumOrString
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $NumOrString_BoolCopyWith<NumOrString_Bool> get copyWith =>
      _$NumOrString_BoolCopyWithImpl<NumOrString_Bool>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is NumOrString_Bool &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'NumOrString.bool(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $NumOrString_BoolCopyWith<$Res>
    implements $NumOrStringCopyWith<$Res> {
  factory $NumOrString_BoolCopyWith(
          NumOrString_Bool value, $Res Function(NumOrString_Bool) _then) =
      _$NumOrString_BoolCopyWithImpl;
  @useResult
  $Res call({bool field0});
}

/// @nodoc
class _$NumOrString_BoolCopyWithImpl<$Res>
    implements $NumOrString_BoolCopyWith<$Res> {
  _$NumOrString_BoolCopyWithImpl(this._self, this._then);

  final NumOrString_Bool _self;
  final $Res Function(NumOrString_Bool) _then;

  /// Create a copy of NumOrString
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(NumOrString_Bool(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as bool,
    ));
  }
}

/// @nodoc
mixin _$PartExtension {
  double get msgWidth;
  double get rotation;
  BigInt get sai;
  double get scale;
  bool? get update;
  BigInt get sli;
  double get normalizedX;
  double get normalizedY;
  BigInt get version;
  String get hash;
  BigInt get safi;
  PlatformInt64 get effectType;
  String get stickerId;

  /// Create a copy of PartExtension
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $PartExtensionCopyWith<PartExtension> get copyWith =>
      _$PartExtensionCopyWithImpl<PartExtension>(
          this as PartExtension, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is PartExtension &&
            (identical(other.msgWidth, msgWidth) ||
                other.msgWidth == msgWidth) &&
            (identical(other.rotation, rotation) ||
                other.rotation == rotation) &&
            (identical(other.sai, sai) || other.sai == sai) &&
            (identical(other.scale, scale) || other.scale == scale) &&
            (identical(other.update, update) || other.update == update) &&
            (identical(other.sli, sli) || other.sli == sli) &&
            (identical(other.normalizedX, normalizedX) ||
                other.normalizedX == normalizedX) &&
            (identical(other.normalizedY, normalizedY) ||
                other.normalizedY == normalizedY) &&
            (identical(other.version, version) || other.version == version) &&
            (identical(other.hash, hash) || other.hash == hash) &&
            (identical(other.safi, safi) || other.safi == safi) &&
            (identical(other.effectType, effectType) ||
                other.effectType == effectType) &&
            (identical(other.stickerId, stickerId) ||
                other.stickerId == stickerId));
  }

  @override
  int get hashCode => Object.hash(
      runtimeType,
      msgWidth,
      rotation,
      sai,
      scale,
      update,
      sli,
      normalizedX,
      normalizedY,
      version,
      hash,
      safi,
      effectType,
      stickerId);

  @override
  String toString() {
    return 'PartExtension(msgWidth: $msgWidth, rotation: $rotation, sai: $sai, scale: $scale, update: $update, sli: $sli, normalizedX: $normalizedX, normalizedY: $normalizedY, version: $version, hash: $hash, safi: $safi, effectType: $effectType, stickerId: $stickerId)';
  }
}

/// @nodoc
abstract mixin class $PartExtensionCopyWith<$Res> {
  factory $PartExtensionCopyWith(
          PartExtension value, $Res Function(PartExtension) _then) =
      _$PartExtensionCopyWithImpl;
  @useResult
  $Res call(
      {double msgWidth,
      double rotation,
      BigInt sai,
      double scale,
      bool? update,
      BigInt sli,
      double normalizedX,
      double normalizedY,
      BigInt version,
      String hash,
      BigInt safi,
      PlatformInt64 effectType,
      String stickerId});
}

/// @nodoc
class _$PartExtensionCopyWithImpl<$Res>
    implements $PartExtensionCopyWith<$Res> {
  _$PartExtensionCopyWithImpl(this._self, this._then);

  final PartExtension _self;
  final $Res Function(PartExtension) _then;

  /// Create a copy of PartExtension
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? msgWidth = null,
    Object? rotation = null,
    Object? sai = null,
    Object? scale = null,
    Object? update = freezed,
    Object? sli = null,
    Object? normalizedX = null,
    Object? normalizedY = null,
    Object? version = null,
    Object? hash = null,
    Object? safi = null,
    Object? effectType = null,
    Object? stickerId = null,
  }) {
    return _then(_self.copyWith(
      msgWidth: null == msgWidth
          ? _self.msgWidth
          : msgWidth // ignore: cast_nullable_to_non_nullable
              as double,
      rotation: null == rotation
          ? _self.rotation
          : rotation // ignore: cast_nullable_to_non_nullable
              as double,
      sai: null == sai
          ? _self.sai
          : sai // ignore: cast_nullable_to_non_nullable
              as BigInt,
      scale: null == scale
          ? _self.scale
          : scale // ignore: cast_nullable_to_non_nullable
              as double,
      update: freezed == update
          ? _self.update
          : update // ignore: cast_nullable_to_non_nullable
              as bool?,
      sli: null == sli
          ? _self.sli
          : sli // ignore: cast_nullable_to_non_nullable
              as BigInt,
      normalizedX: null == normalizedX
          ? _self.normalizedX
          : normalizedX // ignore: cast_nullable_to_non_nullable
              as double,
      normalizedY: null == normalizedY
          ? _self.normalizedY
          : normalizedY // ignore: cast_nullable_to_non_nullable
              as double,
      version: null == version
          ? _self.version
          : version // ignore: cast_nullable_to_non_nullable
              as BigInt,
      hash: null == hash
          ? _self.hash
          : hash // ignore: cast_nullable_to_non_nullable
              as String,
      safi: null == safi
          ? _self.safi
          : safi // ignore: cast_nullable_to_non_nullable
              as BigInt,
      effectType: null == effectType
          ? _self.effectType
          : effectType // ignore: cast_nullable_to_non_nullable
              as PlatformInt64,
      stickerId: null == stickerId
          ? _self.stickerId
          : stickerId // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

/// Adds pattern-matching-related methods to [PartExtension].
extension PartExtensionPatterns on PartExtension {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(PartExtension_Sticker value)? sticker,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case PartExtension_Sticker() when sticker != null:
        return sticker(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(PartExtension_Sticker value) sticker,
  }) {
    final _that = this;
    switch (_that) {
      case PartExtension_Sticker():
        return sticker(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(PartExtension_Sticker value)? sticker,
  }) {
    final _that = this;
    switch (_that) {
      case PartExtension_Sticker() when sticker != null:
        return sticker(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function(
            double msgWidth,
            double rotation,
            BigInt sai,
            double scale,
            bool? update,
            BigInt sli,
            double normalizedX,
            double normalizedY,
            BigInt version,
            String hash,
            BigInt safi,
            PlatformInt64 effectType,
            String stickerId)?
        sticker,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case PartExtension_Sticker() when sticker != null:
        return sticker(
            _that.msgWidth,
            _that.rotation,
            _that.sai,
            _that.scale,
            _that.update,
            _that.sli,
            _that.normalizedX,
            _that.normalizedY,
            _that.version,
            _that.hash,
            _that.safi,
            _that.effectType,
            _that.stickerId);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function(
            double msgWidth,
            double rotation,
            BigInt sai,
            double scale,
            bool? update,
            BigInt sli,
            double normalizedX,
            double normalizedY,
            BigInt version,
            String hash,
            BigInt safi,
            PlatformInt64 effectType,
            String stickerId)
        sticker,
  }) {
    final _that = this;
    switch (_that) {
      case PartExtension_Sticker():
        return sticker(
            _that.msgWidth,
            _that.rotation,
            _that.sai,
            _that.scale,
            _that.update,
            _that.sli,
            _that.normalizedX,
            _that.normalizedY,
            _that.version,
            _that.hash,
            _that.safi,
            _that.effectType,
            _that.stickerId);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function(
            double msgWidth,
            double rotation,
            BigInt sai,
            double scale,
            bool? update,
            BigInt sli,
            double normalizedX,
            double normalizedY,
            BigInt version,
            String hash,
            BigInt safi,
            PlatformInt64 effectType,
            String stickerId)?
        sticker,
  }) {
    final _that = this;
    switch (_that) {
      case PartExtension_Sticker() when sticker != null:
        return sticker(
            _that.msgWidth,
            _that.rotation,
            _that.sai,
            _that.scale,
            _that.update,
            _that.sli,
            _that.normalizedX,
            _that.normalizedY,
            _that.version,
            _that.hash,
            _that.safi,
            _that.effectType,
            _that.stickerId);
      case _:
        return null;
    }
  }
}

/// @nodoc

class PartExtension_Sticker extends PartExtension {
  const PartExtension_Sticker(
      {required this.msgWidth,
      required this.rotation,
      required this.sai,
      required this.scale,
      this.update,
      required this.sli,
      required this.normalizedX,
      required this.normalizedY,
      required this.version,
      required this.hash,
      required this.safi,
      required this.effectType,
      required this.stickerId})
      : super._();

  @override
  final double msgWidth;
  @override
  final double rotation;
  @override
  final BigInt sai;
  @override
  final double scale;
  @override
  final bool? update;
  @override
  final BigInt sli;
  @override
  final double normalizedX;
  @override
  final double normalizedY;
  @override
  final BigInt version;
  @override
  final String hash;
  @override
  final BigInt safi;
  @override
  final PlatformInt64 effectType;
  @override
  final String stickerId;

  /// Create a copy of PartExtension
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $PartExtension_StickerCopyWith<PartExtension_Sticker> get copyWith =>
      _$PartExtension_StickerCopyWithImpl<PartExtension_Sticker>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is PartExtension_Sticker &&
            (identical(other.msgWidth, msgWidth) ||
                other.msgWidth == msgWidth) &&
            (identical(other.rotation, rotation) ||
                other.rotation == rotation) &&
            (identical(other.sai, sai) || other.sai == sai) &&
            (identical(other.scale, scale) || other.scale == scale) &&
            (identical(other.update, update) || other.update == update) &&
            (identical(other.sli, sli) || other.sli == sli) &&
            (identical(other.normalizedX, normalizedX) ||
                other.normalizedX == normalizedX) &&
            (identical(other.normalizedY, normalizedY) ||
                other.normalizedY == normalizedY) &&
            (identical(other.version, version) || other.version == version) &&
            (identical(other.hash, hash) || other.hash == hash) &&
            (identical(other.safi, safi) || other.safi == safi) &&
            (identical(other.effectType, effectType) ||
                other.effectType == effectType) &&
            (identical(other.stickerId, stickerId) ||
                other.stickerId == stickerId));
  }

  @override
  int get hashCode => Object.hash(
      runtimeType,
      msgWidth,
      rotation,
      sai,
      scale,
      update,
      sli,
      normalizedX,
      normalizedY,
      version,
      hash,
      safi,
      effectType,
      stickerId);

  @override
  String toString() {
    return 'PartExtension.sticker(msgWidth: $msgWidth, rotation: $rotation, sai: $sai, scale: $scale, update: $update, sli: $sli, normalizedX: $normalizedX, normalizedY: $normalizedY, version: $version, hash: $hash, safi: $safi, effectType: $effectType, stickerId: $stickerId)';
  }
}

/// @nodoc
abstract mixin class $PartExtension_StickerCopyWith<$Res>
    implements $PartExtensionCopyWith<$Res> {
  factory $PartExtension_StickerCopyWith(PartExtension_Sticker value,
          $Res Function(PartExtension_Sticker) _then) =
      _$PartExtension_StickerCopyWithImpl;
  @override
  @useResult
  $Res call(
      {double msgWidth,
      double rotation,
      BigInt sai,
      double scale,
      bool? update,
      BigInt sli,
      double normalizedX,
      double normalizedY,
      BigInt version,
      String hash,
      BigInt safi,
      PlatformInt64 effectType,
      String stickerId});
}

/// @nodoc
class _$PartExtension_StickerCopyWithImpl<$Res>
    implements $PartExtension_StickerCopyWith<$Res> {
  _$PartExtension_StickerCopyWithImpl(this._self, this._then);

  final PartExtension_Sticker _self;
  final $Res Function(PartExtension_Sticker) _then;

  /// Create a copy of PartExtension
  /// with the given fields replaced by the non-null parameter values.
  @override
  @pragma('vm:prefer-inline')
  $Res call({
    Object? msgWidth = null,
    Object? rotation = null,
    Object? sai = null,
    Object? scale = null,
    Object? update = freezed,
    Object? sli = null,
    Object? normalizedX = null,
    Object? normalizedY = null,
    Object? version = null,
    Object? hash = null,
    Object? safi = null,
    Object? effectType = null,
    Object? stickerId = null,
  }) {
    return _then(PartExtension_Sticker(
      msgWidth: null == msgWidth
          ? _self.msgWidth
          : msgWidth // ignore: cast_nullable_to_non_nullable
              as double,
      rotation: null == rotation
          ? _self.rotation
          : rotation // ignore: cast_nullable_to_non_nullable
              as double,
      sai: null == sai
          ? _self.sai
          : sai // ignore: cast_nullable_to_non_nullable
              as BigInt,
      scale: null == scale
          ? _self.scale
          : scale // ignore: cast_nullable_to_non_nullable
              as double,
      update: freezed == update
          ? _self.update
          : update // ignore: cast_nullable_to_non_nullable
              as bool?,
      sli: null == sli
          ? _self.sli
          : sli // ignore: cast_nullable_to_non_nullable
              as BigInt,
      normalizedX: null == normalizedX
          ? _self.normalizedX
          : normalizedX // ignore: cast_nullable_to_non_nullable
              as double,
      normalizedY: null == normalizedY
          ? _self.normalizedY
          : normalizedY // ignore: cast_nullable_to_non_nullable
              as double,
      version: null == version
          ? _self.version
          : version // ignore: cast_nullable_to_non_nullable
              as BigInt,
      hash: null == hash
          ? _self.hash
          : hash // ignore: cast_nullable_to_non_nullable
              as String,
      safi: null == safi
          ? _self.safi
          : safi // ignore: cast_nullable_to_non_nullable
              as BigInt,
      effectType: null == effectType
          ? _self.effectType
          : effectType // ignore: cast_nullable_to_non_nullable
              as PlatformInt64,
      stickerId: null == stickerId
          ? _self.stickerId
          : stickerId // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

/// @nodoc
mixin _$PollResult {
  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is PollResult);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'PollResult()';
  }
}

/// @nodoc
class $PollResultCopyWith<$Res> {
  $PollResultCopyWith(PollResult _, $Res Function(PollResult) __);
}

/// Adds pattern-matching-related methods to [PollResult].
extension PollResultPatterns on PollResult {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(PollResult_Stop value)? stop,
    TResult Function(PollResult_Cont value)? cont,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case PollResult_Stop() when stop != null:
        return stop(_that);
      case PollResult_Cont() when cont != null:
        return cont(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(PollResult_Stop value) stop,
    required TResult Function(PollResult_Cont value) cont,
  }) {
    final _that = this;
    switch (_that) {
      case PollResult_Stop():
        return stop(_that);
      case PollResult_Cont():
        return cont(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(PollResult_Stop value)? stop,
    TResult? Function(PollResult_Cont value)? cont,
  }) {
    final _that = this;
    switch (_that) {
      case PollResult_Stop() when stop != null:
        return stop(_that);
      case PollResult_Cont() when cont != null:
        return cont(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function()? stop,
    TResult Function(PushMessage? field0)? cont,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case PollResult_Stop() when stop != null:
        return stop();
      case PollResult_Cont() when cont != null:
        return cont(_that.field0);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function() stop,
    required TResult Function(PushMessage? field0) cont,
  }) {
    final _that = this;
    switch (_that) {
      case PollResult_Stop():
        return stop();
      case PollResult_Cont():
        return cont(_that.field0);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function()? stop,
    TResult? Function(PushMessage? field0)? cont,
  }) {
    final _that = this;
    switch (_that) {
      case PollResult_Stop() when stop != null:
        return stop();
      case PollResult_Cont() when cont != null:
        return cont(_that.field0);
      case _:
        return null;
    }
  }
}

/// @nodoc

class PollResult_Stop extends PollResult {
  const PollResult_Stop() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is PollResult_Stop);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'PollResult.stop()';
  }
}

/// @nodoc

class PollResult_Cont extends PollResult {
  const PollResult_Cont([this.field0]) : super._();

  final PushMessage? field0;

  /// Create a copy of PollResult
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $PollResult_ContCopyWith<PollResult_Cont> get copyWith =>
      _$PollResult_ContCopyWithImpl<PollResult_Cont>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is PollResult_Cont &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'PollResult.cont(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $PollResult_ContCopyWith<$Res>
    implements $PollResultCopyWith<$Res> {
  factory $PollResult_ContCopyWith(
          PollResult_Cont value, $Res Function(PollResult_Cont) _then) =
      _$PollResult_ContCopyWithImpl;
  @useResult
  $Res call({PushMessage? field0});

  $PushMessageCopyWith<$Res>? get field0;
}

/// @nodoc
class _$PollResult_ContCopyWithImpl<$Res>
    implements $PollResult_ContCopyWith<$Res> {
  _$PollResult_ContCopyWithImpl(this._self, this._then);

  final PollResult_Cont _self;
  final $Res Function(PollResult_Cont) _then;

  /// Create a copy of PollResult
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = freezed,
  }) {
    return _then(PollResult_Cont(
      freezed == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as PushMessage?,
    ));
  }

  /// Create a copy of PollResult
  /// with the given fields replaced by the non-null parameter values.
  @override
  @pragma('vm:prefer-inline')
  $PushMessageCopyWith<$Res>? get field0 {
    if (_self.field0 == null) {
      return null;
    }

    return $PushMessageCopyWith<$Res>(_self.field0!, (value) {
      return _then(_self.copyWith(field0: value));
    });
  }
}

/// @nodoc
mixin _$PosterType {
  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is PosterType);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'PosterType()';
  }
}

/// @nodoc
class $PosterTypeCopyWith<$Res> {
  $PosterTypeCopyWith(PosterType _, $Res Function(PosterType) __);
}

/// Adds pattern-matching-related methods to [PosterType].
extension PosterTypePatterns on PosterType {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(PosterType_Photo value)? photo,
    TResult Function(PosterType_Monogram value)? monogram,
    TResult Function(PosterType_Memoji value)? memoji,
    TResult Function(PosterType_TranscriptDynamic value)? transcriptDynamic,
    TResult Function(PosterType_TranscriptGradient value)? transcriptGradient,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case PosterType_Photo() when photo != null:
        return photo(_that);
      case PosterType_Monogram() when monogram != null:
        return monogram(_that);
      case PosterType_Memoji() when memoji != null:
        return memoji(_that);
      case PosterType_TranscriptDynamic() when transcriptDynamic != null:
        return transcriptDynamic(_that);
      case PosterType_TranscriptGradient() when transcriptGradient != null:
        return transcriptGradient(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(PosterType_Photo value) photo,
    required TResult Function(PosterType_Monogram value) monogram,
    required TResult Function(PosterType_Memoji value) memoji,
    required TResult Function(PosterType_TranscriptDynamic value)
        transcriptDynamic,
    required TResult Function(PosterType_TranscriptGradient value)
        transcriptGradient,
  }) {
    final _that = this;
    switch (_that) {
      case PosterType_Photo():
        return photo(_that);
      case PosterType_Monogram():
        return monogram(_that);
      case PosterType_Memoji():
        return memoji(_that);
      case PosterType_TranscriptDynamic():
        return transcriptDynamic(_that);
      case PosterType_TranscriptGradient():
        return transcriptGradient(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(PosterType_Photo value)? photo,
    TResult? Function(PosterType_Monogram value)? monogram,
    TResult? Function(PosterType_Memoji value)? memoji,
    TResult? Function(PosterType_TranscriptDynamic value)? transcriptDynamic,
    TResult? Function(PosterType_TranscriptGradient value)? transcriptGradient,
  }) {
    final _that = this;
    switch (_that) {
      case PosterType_Photo() when photo != null:
        return photo(_that);
      case PosterType_Monogram() when monogram != null:
        return monogram(_that);
      case PosterType_Memoji() when memoji != null:
        return memoji(_that);
      case PosterType_TranscriptDynamic() when transcriptDynamic != null:
        return transcriptDynamic(_that);
      case PosterType_TranscriptGradient() when transcriptGradient != null:
        return transcriptGradient(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function(List<PosterAsset> assets)? photo,
    TResult Function(MonogramData data, PosterColor background)? monogram,
    TResult Function(MemojiData data, PosterColor background)? memoji,
    TResult Function(TranscriptDynamicUserData data)? transcriptDynamic,
    TResult Function(List<PosterColor> colors)? transcriptGradient,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case PosterType_Photo() when photo != null:
        return photo(_that.assets);
      case PosterType_Monogram() when monogram != null:
        return monogram(_that.data, _that.background);
      case PosterType_Memoji() when memoji != null:
        return memoji(_that.data, _that.background);
      case PosterType_TranscriptDynamic() when transcriptDynamic != null:
        return transcriptDynamic(_that.data);
      case PosterType_TranscriptGradient() when transcriptGradient != null:
        return transcriptGradient(_that.colors);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function(List<PosterAsset> assets) photo,
    required TResult Function(MonogramData data, PosterColor background)
        monogram,
    required TResult Function(MemojiData data, PosterColor background) memoji,
    required TResult Function(TranscriptDynamicUserData data) transcriptDynamic,
    required TResult Function(List<PosterColor> colors) transcriptGradient,
  }) {
    final _that = this;
    switch (_that) {
      case PosterType_Photo():
        return photo(_that.assets);
      case PosterType_Monogram():
        return monogram(_that.data, _that.background);
      case PosterType_Memoji():
        return memoji(_that.data, _that.background);
      case PosterType_TranscriptDynamic():
        return transcriptDynamic(_that.data);
      case PosterType_TranscriptGradient():
        return transcriptGradient(_that.colors);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function(List<PosterAsset> assets)? photo,
    TResult? Function(MonogramData data, PosterColor background)? monogram,
    TResult? Function(MemojiData data, PosterColor background)? memoji,
    TResult? Function(TranscriptDynamicUserData data)? transcriptDynamic,
    TResult? Function(List<PosterColor> colors)? transcriptGradient,
  }) {
    final _that = this;
    switch (_that) {
      case PosterType_Photo() when photo != null:
        return photo(_that.assets);
      case PosterType_Monogram() when monogram != null:
        return monogram(_that.data, _that.background);
      case PosterType_Memoji() when memoji != null:
        return memoji(_that.data, _that.background);
      case PosterType_TranscriptDynamic() when transcriptDynamic != null:
        return transcriptDynamic(_that.data);
      case PosterType_TranscriptGradient() when transcriptGradient != null:
        return transcriptGradient(_that.colors);
      case _:
        return null;
    }
  }
}

/// @nodoc

class PosterType_Photo extends PosterType {
  const PosterType_Photo({required final List<PosterAsset> assets})
      : _assets = assets,
        super._();

  final List<PosterAsset> _assets;
  List<PosterAsset> get assets {
    if (_assets is EqualUnmodifiableListView) return _assets;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(_assets);
  }

  /// Create a copy of PosterType
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $PosterType_PhotoCopyWith<PosterType_Photo> get copyWith =>
      _$PosterType_PhotoCopyWithImpl<PosterType_Photo>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is PosterType_Photo &&
            const DeepCollectionEquality().equals(other._assets, _assets));
  }

  @override
  int get hashCode =>
      Object.hash(runtimeType, const DeepCollectionEquality().hash(_assets));

  @override
  String toString() {
    return 'PosterType.photo(assets: $assets)';
  }
}

/// @nodoc
abstract mixin class $PosterType_PhotoCopyWith<$Res>
    implements $PosterTypeCopyWith<$Res> {
  factory $PosterType_PhotoCopyWith(
          PosterType_Photo value, $Res Function(PosterType_Photo) _then) =
      _$PosterType_PhotoCopyWithImpl;
  @useResult
  $Res call({List<PosterAsset> assets});
}

/// @nodoc
class _$PosterType_PhotoCopyWithImpl<$Res>
    implements $PosterType_PhotoCopyWith<$Res> {
  _$PosterType_PhotoCopyWithImpl(this._self, this._then);

  final PosterType_Photo _self;
  final $Res Function(PosterType_Photo) _then;

  /// Create a copy of PosterType
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? assets = null,
  }) {
    return _then(PosterType_Photo(
      assets: null == assets
          ? _self._assets
          : assets // ignore: cast_nullable_to_non_nullable
              as List<PosterAsset>,
    ));
  }
}

/// @nodoc

class PosterType_Monogram extends PosterType {
  const PosterType_Monogram({required this.data, required this.background})
      : super._();

  final MonogramData data;
  final PosterColor background;

  /// Create a copy of PosterType
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $PosterType_MonogramCopyWith<PosterType_Monogram> get copyWith =>
      _$PosterType_MonogramCopyWithImpl<PosterType_Monogram>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is PosterType_Monogram &&
            (identical(other.data, data) || other.data == data) &&
            (identical(other.background, background) ||
                other.background == background));
  }

  @override
  int get hashCode => Object.hash(runtimeType, data, background);

  @override
  String toString() {
    return 'PosterType.monogram(data: $data, background: $background)';
  }
}

/// @nodoc
abstract mixin class $PosterType_MonogramCopyWith<$Res>
    implements $PosterTypeCopyWith<$Res> {
  factory $PosterType_MonogramCopyWith(
          PosterType_Monogram value, $Res Function(PosterType_Monogram) _then) =
      _$PosterType_MonogramCopyWithImpl;
  @useResult
  $Res call({MonogramData data, PosterColor background});
}

/// @nodoc
class _$PosterType_MonogramCopyWithImpl<$Res>
    implements $PosterType_MonogramCopyWith<$Res> {
  _$PosterType_MonogramCopyWithImpl(this._self, this._then);

  final PosterType_Monogram _self;
  final $Res Function(PosterType_Monogram) _then;

  /// Create a copy of PosterType
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? data = null,
    Object? background = null,
  }) {
    return _then(PosterType_Monogram(
      data: null == data
          ? _self.data
          : data // ignore: cast_nullable_to_non_nullable
              as MonogramData,
      background: null == background
          ? _self.background
          : background // ignore: cast_nullable_to_non_nullable
              as PosterColor,
    ));
  }
}

/// @nodoc

class PosterType_Memoji extends PosterType {
  const PosterType_Memoji({required this.data, required this.background})
      : super._();

  final MemojiData data;
  final PosterColor background;

  /// Create a copy of PosterType
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $PosterType_MemojiCopyWith<PosterType_Memoji> get copyWith =>
      _$PosterType_MemojiCopyWithImpl<PosterType_Memoji>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is PosterType_Memoji &&
            (identical(other.data, data) || other.data == data) &&
            (identical(other.background, background) ||
                other.background == background));
  }

  @override
  int get hashCode => Object.hash(runtimeType, data, background);

  @override
  String toString() {
    return 'PosterType.memoji(data: $data, background: $background)';
  }
}

/// @nodoc
abstract mixin class $PosterType_MemojiCopyWith<$Res>
    implements $PosterTypeCopyWith<$Res> {
  factory $PosterType_MemojiCopyWith(
          PosterType_Memoji value, $Res Function(PosterType_Memoji) _then) =
      _$PosterType_MemojiCopyWithImpl;
  @useResult
  $Res call({MemojiData data, PosterColor background});
}

/// @nodoc
class _$PosterType_MemojiCopyWithImpl<$Res>
    implements $PosterType_MemojiCopyWith<$Res> {
  _$PosterType_MemojiCopyWithImpl(this._self, this._then);

  final PosterType_Memoji _self;
  final $Res Function(PosterType_Memoji) _then;

  /// Create a copy of PosterType
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? data = null,
    Object? background = null,
  }) {
    return _then(PosterType_Memoji(
      data: null == data
          ? _self.data
          : data // ignore: cast_nullable_to_non_nullable
              as MemojiData,
      background: null == background
          ? _self.background
          : background // ignore: cast_nullable_to_non_nullable
              as PosterColor,
    ));
  }
}

/// @nodoc

class PosterType_TranscriptDynamic extends PosterType {
  const PosterType_TranscriptDynamic({required this.data}) : super._();

  final TranscriptDynamicUserData data;

  /// Create a copy of PosterType
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $PosterType_TranscriptDynamicCopyWith<PosterType_TranscriptDynamic>
      get copyWith => _$PosterType_TranscriptDynamicCopyWithImpl<
          PosterType_TranscriptDynamic>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is PosterType_TranscriptDynamic &&
            (identical(other.data, data) || other.data == data));
  }

  @override
  int get hashCode => Object.hash(runtimeType, data);

  @override
  String toString() {
    return 'PosterType.transcriptDynamic(data: $data)';
  }
}

/// @nodoc
abstract mixin class $PosterType_TranscriptDynamicCopyWith<$Res>
    implements $PosterTypeCopyWith<$Res> {
  factory $PosterType_TranscriptDynamicCopyWith(
          PosterType_TranscriptDynamic value,
          $Res Function(PosterType_TranscriptDynamic) _then) =
      _$PosterType_TranscriptDynamicCopyWithImpl;
  @useResult
  $Res call({TranscriptDynamicUserData data});
}

/// @nodoc
class _$PosterType_TranscriptDynamicCopyWithImpl<$Res>
    implements $PosterType_TranscriptDynamicCopyWith<$Res> {
  _$PosterType_TranscriptDynamicCopyWithImpl(this._self, this._then);

  final PosterType_TranscriptDynamic _self;
  final $Res Function(PosterType_TranscriptDynamic) _then;

  /// Create a copy of PosterType
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? data = null,
  }) {
    return _then(PosterType_TranscriptDynamic(
      data: null == data
          ? _self.data
          : data // ignore: cast_nullable_to_non_nullable
              as TranscriptDynamicUserData,
    ));
  }
}

/// @nodoc

class PosterType_TranscriptGradient extends PosterType {
  const PosterType_TranscriptGradient({required final List<PosterColor> colors})
      : _colors = colors,
        super._();

  final List<PosterColor> _colors;
  List<PosterColor> get colors {
    if (_colors is EqualUnmodifiableListView) return _colors;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(_colors);
  }

  /// Create a copy of PosterType
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $PosterType_TranscriptGradientCopyWith<PosterType_TranscriptGradient>
      get copyWith => _$PosterType_TranscriptGradientCopyWithImpl<
          PosterType_TranscriptGradient>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is PosterType_TranscriptGradient &&
            const DeepCollectionEquality().equals(other._colors, _colors));
  }

  @override
  int get hashCode =>
      Object.hash(runtimeType, const DeepCollectionEquality().hash(_colors));

  @override
  String toString() {
    return 'PosterType.transcriptGradient(colors: $colors)';
  }
}

/// @nodoc
abstract mixin class $PosterType_TranscriptGradientCopyWith<$Res>
    implements $PosterTypeCopyWith<$Res> {
  factory $PosterType_TranscriptGradientCopyWith(
          PosterType_TranscriptGradient value,
          $Res Function(PosterType_TranscriptGradient) _then) =
      _$PosterType_TranscriptGradientCopyWithImpl;
  @useResult
  $Res call({List<PosterColor> colors});
}

/// @nodoc
class _$PosterType_TranscriptGradientCopyWithImpl<$Res>
    implements $PosterType_TranscriptGradientCopyWith<$Res> {
  _$PosterType_TranscriptGradientCopyWithImpl(this._self, this._then);

  final PosterType_TranscriptGradient _self;
  final $Res Function(PosterType_TranscriptGradient) _then;

  /// Create a copy of PosterType
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? colors = null,
  }) {
    return _then(PosterType_TranscriptGradient(
      colors: null == colors
          ? _self._colors
          : colors // ignore: cast_nullable_to_non_nullable
              as List<PosterColor>,
    ));
  }
}

/// @nodoc
mixin _$PRPosterContentMaterialStyle {
  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is PRPosterContentMaterialStyle);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'PRPosterContentMaterialStyle()';
  }
}

/// @nodoc
class $PRPosterContentMaterialStyleCopyWith<$Res> {
  $PRPosterContentMaterialStyleCopyWith(PRPosterContentMaterialStyle _,
      $Res Function(PRPosterContentMaterialStyle) __);
}

/// Adds pattern-matching-related methods to [PRPosterContentMaterialStyle].
extension PRPosterContentMaterialStylePatterns on PRPosterContentMaterialStyle {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(
            PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyle
                value)?
        prPosterContentDiscreteColorsStyle,
    TResult Function(
            PRPosterContentMaterialStyle_PRPosterContentVibrantMaterialStyle
                value)?
        prPosterContentVibrantMaterialStyle,
    TResult Function(
            PRPosterContentMaterialStyle_PRPosterContentGradientStyle value)?
        prPosterContentGradientStyle,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyle()
          when prPosterContentDiscreteColorsStyle != null:
        return prPosterContentDiscreteColorsStyle(_that);
      case PRPosterContentMaterialStyle_PRPosterContentVibrantMaterialStyle()
          when prPosterContentVibrantMaterialStyle != null:
        return prPosterContentVibrantMaterialStyle(_that);
      case PRPosterContentMaterialStyle_PRPosterContentGradientStyle()
          when prPosterContentGradientStyle != null:
        return prPosterContentGradientStyle(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(
            PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyle
                value)
        prPosterContentDiscreteColorsStyle,
    required TResult Function(
            PRPosterContentMaterialStyle_PRPosterContentVibrantMaterialStyle
                value)
        prPosterContentVibrantMaterialStyle,
    required TResult Function(
            PRPosterContentMaterialStyle_PRPosterContentGradientStyle value)
        prPosterContentGradientStyle,
  }) {
    final _that = this;
    switch (_that) {
      case PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyle():
        return prPosterContentDiscreteColorsStyle(_that);
      case PRPosterContentMaterialStyle_PRPosterContentVibrantMaterialStyle():
        return prPosterContentVibrantMaterialStyle(_that);
      case PRPosterContentMaterialStyle_PRPosterContentGradientStyle():
        return prPosterContentGradientStyle(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(
            PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyle
                value)?
        prPosterContentDiscreteColorsStyle,
    TResult? Function(
            PRPosterContentMaterialStyle_PRPosterContentVibrantMaterialStyle
                value)?
        prPosterContentVibrantMaterialStyle,
    TResult? Function(
            PRPosterContentMaterialStyle_PRPosterContentGradientStyle value)?
        prPosterContentGradientStyle,
  }) {
    final _that = this;
    switch (_that) {
      case PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyle()
          when prPosterContentDiscreteColorsStyle != null:
        return prPosterContentDiscreteColorsStyle(_that);
      case PRPosterContentMaterialStyle_PRPosterContentVibrantMaterialStyle()
          when prPosterContentVibrantMaterialStyle != null:
        return prPosterContentVibrantMaterialStyle(_that);
      case PRPosterContentMaterialStyle_PRPosterContentGradientStyle()
          when prPosterContentGradientStyle != null:
        return prPosterContentGradientStyle(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function(double variation, List<UIColor> colors, bool vibrant,
            bool supportsVariation, bool needsToResolveVariation)?
        prPosterContentDiscreteColorsStyle,
    TResult Function()? prPosterContentVibrantMaterialStyle,
    TResult Function(int gradientType, List<UIColor> colors, String startPoint,
            Float64List locations, String endPoint)?
        prPosterContentGradientStyle,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyle()
          when prPosterContentDiscreteColorsStyle != null:
        return prPosterContentDiscreteColorsStyle(
            _that.variation,
            _that.colors,
            _that.vibrant,
            _that.supportsVariation,
            _that.needsToResolveVariation);
      case PRPosterContentMaterialStyle_PRPosterContentVibrantMaterialStyle()
          when prPosterContentVibrantMaterialStyle != null:
        return prPosterContentVibrantMaterialStyle();
      case PRPosterContentMaterialStyle_PRPosterContentGradientStyle()
          when prPosterContentGradientStyle != null:
        return prPosterContentGradientStyle(_that.gradientType, _that.colors,
            _that.startPoint, _that.locations, _that.endPoint);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function(double variation, List<UIColor> colors,
            bool vibrant, bool supportsVariation, bool needsToResolveVariation)
        prPosterContentDiscreteColorsStyle,
    required TResult Function() prPosterContentVibrantMaterialStyle,
    required TResult Function(int gradientType, List<UIColor> colors,
            String startPoint, Float64List locations, String endPoint)
        prPosterContentGradientStyle,
  }) {
    final _that = this;
    switch (_that) {
      case PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyle():
        return prPosterContentDiscreteColorsStyle(
            _that.variation,
            _that.colors,
            _that.vibrant,
            _that.supportsVariation,
            _that.needsToResolveVariation);
      case PRPosterContentMaterialStyle_PRPosterContentVibrantMaterialStyle():
        return prPosterContentVibrantMaterialStyle();
      case PRPosterContentMaterialStyle_PRPosterContentGradientStyle():
        return prPosterContentGradientStyle(_that.gradientType, _that.colors,
            _that.startPoint, _that.locations, _that.endPoint);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function(double variation, List<UIColor> colors, bool vibrant,
            bool supportsVariation, bool needsToResolveVariation)?
        prPosterContentDiscreteColorsStyle,
    TResult? Function()? prPosterContentVibrantMaterialStyle,
    TResult? Function(int gradientType, List<UIColor> colors, String startPoint,
            Float64List locations, String endPoint)?
        prPosterContentGradientStyle,
  }) {
    final _that = this;
    switch (_that) {
      case PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyle()
          when prPosterContentDiscreteColorsStyle != null:
        return prPosterContentDiscreteColorsStyle(
            _that.variation,
            _that.colors,
            _that.vibrant,
            _that.supportsVariation,
            _that.needsToResolveVariation);
      case PRPosterContentMaterialStyle_PRPosterContentVibrantMaterialStyle()
          when prPosterContentVibrantMaterialStyle != null:
        return prPosterContentVibrantMaterialStyle();
      case PRPosterContentMaterialStyle_PRPosterContentGradientStyle()
          when prPosterContentGradientStyle != null:
        return prPosterContentGradientStyle(_that.gradientType, _that.colors,
            _that.startPoint, _that.locations, _that.endPoint);
      case _:
        return null;
    }
  }
}

/// @nodoc

class PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyle
    extends PRPosterContentMaterialStyle {
  const PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyle(
      {required this.variation,
      required final List<UIColor> colors,
      required this.vibrant,
      required this.supportsVariation,
      required this.needsToResolveVariation})
      : _colors = colors,
        super._();

  final double variation;
  final List<UIColor> _colors;
  List<UIColor> get colors {
    if (_colors is EqualUnmodifiableListView) return _colors;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(_colors);
  }

  final bool vibrant;
  final bool supportsVariation;
  final bool needsToResolveVariation;

  /// Create a copy of PRPosterContentMaterialStyle
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyleCopyWith<
          PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyle>
      get copyWith =>
          _$PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyleCopyWithImpl<
                  PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyle>(
              this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other
                is PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyle &&
            (identical(other.variation, variation) ||
                other.variation == variation) &&
            const DeepCollectionEquality().equals(other._colors, _colors) &&
            (identical(other.vibrant, vibrant) || other.vibrant == vibrant) &&
            (identical(other.supportsVariation, supportsVariation) ||
                other.supportsVariation == supportsVariation) &&
            (identical(
                    other.needsToResolveVariation, needsToResolveVariation) ||
                other.needsToResolveVariation == needsToResolveVariation));
  }

  @override
  int get hashCode => Object.hash(
      runtimeType,
      variation,
      const DeepCollectionEquality().hash(_colors),
      vibrant,
      supportsVariation,
      needsToResolveVariation);

  @override
  String toString() {
    return 'PRPosterContentMaterialStyle.prPosterContentDiscreteColorsStyle(variation: $variation, colors: $colors, vibrant: $vibrant, supportsVariation: $supportsVariation, needsToResolveVariation: $needsToResolveVariation)';
  }
}

/// @nodoc
abstract mixin class $PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyleCopyWith<
    $Res> implements $PRPosterContentMaterialStyleCopyWith<$Res> {
  factory $PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyleCopyWith(
          PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyle value,
          $Res Function(
                  PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyle)
              _then) =
      _$PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyleCopyWithImpl;
  @useResult
  $Res call(
      {double variation,
      List<UIColor> colors,
      bool vibrant,
      bool supportsVariation,
      bool needsToResolveVariation});
}

/// @nodoc
class _$PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyleCopyWithImpl<
        $Res>
    implements
        $PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyleCopyWith<
            $Res> {
  _$PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyleCopyWithImpl(
      this._self, this._then);

  final PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyle _self;
  final $Res Function(
      PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyle) _then;

  /// Create a copy of PRPosterContentMaterialStyle
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? variation = null,
    Object? colors = null,
    Object? vibrant = null,
    Object? supportsVariation = null,
    Object? needsToResolveVariation = null,
  }) {
    return _then(
        PRPosterContentMaterialStyle_PRPosterContentDiscreteColorsStyle(
      variation: null == variation
          ? _self.variation
          : variation // ignore: cast_nullable_to_non_nullable
              as double,
      colors: null == colors
          ? _self._colors
          : colors // ignore: cast_nullable_to_non_nullable
              as List<UIColor>,
      vibrant: null == vibrant
          ? _self.vibrant
          : vibrant // ignore: cast_nullable_to_non_nullable
              as bool,
      supportsVariation: null == supportsVariation
          ? _self.supportsVariation
          : supportsVariation // ignore: cast_nullable_to_non_nullable
              as bool,
      needsToResolveVariation: null == needsToResolveVariation
          ? _self.needsToResolveVariation
          : needsToResolveVariation // ignore: cast_nullable_to_non_nullable
              as bool,
    ));
  }
}

/// @nodoc

class PRPosterContentMaterialStyle_PRPosterContentVibrantMaterialStyle
    extends PRPosterContentMaterialStyle {
  const PRPosterContentMaterialStyle_PRPosterContentVibrantMaterialStyle()
      : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other
                is PRPosterContentMaterialStyle_PRPosterContentVibrantMaterialStyle);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'PRPosterContentMaterialStyle.prPosterContentVibrantMaterialStyle()';
  }
}

/// @nodoc

class PRPosterContentMaterialStyle_PRPosterContentGradientStyle
    extends PRPosterContentMaterialStyle {
  const PRPosterContentMaterialStyle_PRPosterContentGradientStyle(
      {required this.gradientType,
      required final List<UIColor> colors,
      required this.startPoint,
      required this.locations,
      required this.endPoint})
      : _colors = colors,
        super._();

  final int gradientType;
  final List<UIColor> _colors;
  List<UIColor> get colors {
    if (_colors is EqualUnmodifiableListView) return _colors;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(_colors);
  }

  final String startPoint;
  final Float64List locations;
  final String endPoint;

  /// Create a copy of PRPosterContentMaterialStyle
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $PRPosterContentMaterialStyle_PRPosterContentGradientStyleCopyWith<
          PRPosterContentMaterialStyle_PRPosterContentGradientStyle>
      get copyWith =>
          _$PRPosterContentMaterialStyle_PRPosterContentGradientStyleCopyWithImpl<
                  PRPosterContentMaterialStyle_PRPosterContentGradientStyle>(
              this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other
                is PRPosterContentMaterialStyle_PRPosterContentGradientStyle &&
            (identical(other.gradientType, gradientType) ||
                other.gradientType == gradientType) &&
            const DeepCollectionEquality().equals(other._colors, _colors) &&
            (identical(other.startPoint, startPoint) ||
                other.startPoint == startPoint) &&
            const DeepCollectionEquality().equals(other.locations, locations) &&
            (identical(other.endPoint, endPoint) ||
                other.endPoint == endPoint));
  }

  @override
  int get hashCode => Object.hash(
      runtimeType,
      gradientType,
      const DeepCollectionEquality().hash(_colors),
      startPoint,
      const DeepCollectionEquality().hash(locations),
      endPoint);

  @override
  String toString() {
    return 'PRPosterContentMaterialStyle.prPosterContentGradientStyle(gradientType: $gradientType, colors: $colors, startPoint: $startPoint, locations: $locations, endPoint: $endPoint)';
  }
}

/// @nodoc
abstract mixin class $PRPosterContentMaterialStyle_PRPosterContentGradientStyleCopyWith<
    $Res> implements $PRPosterContentMaterialStyleCopyWith<$Res> {
  factory $PRPosterContentMaterialStyle_PRPosterContentGradientStyleCopyWith(
          PRPosterContentMaterialStyle_PRPosterContentGradientStyle value,
          $Res Function(
                  PRPosterContentMaterialStyle_PRPosterContentGradientStyle)
              _then) =
      _$PRPosterContentMaterialStyle_PRPosterContentGradientStyleCopyWithImpl;
  @useResult
  $Res call(
      {int gradientType,
      List<UIColor> colors,
      String startPoint,
      Float64List locations,
      String endPoint});
}

/// @nodoc
class _$PRPosterContentMaterialStyle_PRPosterContentGradientStyleCopyWithImpl<
        $Res>
    implements
        $PRPosterContentMaterialStyle_PRPosterContentGradientStyleCopyWith<
            $Res> {
  _$PRPosterContentMaterialStyle_PRPosterContentGradientStyleCopyWithImpl(
      this._self, this._then);

  final PRPosterContentMaterialStyle_PRPosterContentGradientStyle _self;
  final $Res Function(PRPosterContentMaterialStyle_PRPosterContentGradientStyle)
      _then;

  /// Create a copy of PRPosterContentMaterialStyle
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? gradientType = null,
    Object? colors = null,
    Object? startPoint = null,
    Object? locations = null,
    Object? endPoint = null,
  }) {
    return _then(PRPosterContentMaterialStyle_PRPosterContentGradientStyle(
      gradientType: null == gradientType
          ? _self.gradientType
          : gradientType // ignore: cast_nullable_to_non_nullable
              as int,
      colors: null == colors
          ? _self._colors
          : colors // ignore: cast_nullable_to_non_nullable
              as List<UIColor>,
      startPoint: null == startPoint
          ? _self.startPoint
          : startPoint // ignore: cast_nullable_to_non_nullable
              as String,
      locations: null == locations
          ? _self.locations
          : locations // ignore: cast_nullable_to_non_nullable
              as Float64List,
      endPoint: null == endPoint
          ? _self.endPoint
          : endPoint // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

/// @nodoc
mixin _$PushMessage {
  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is PushMessage);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'PushMessage()';
  }
}

/// @nodoc
class $PushMessageCopyWith<$Res> {
  $PushMessageCopyWith(PushMessage _, $Res Function(PushMessage) __);
}

/// Adds pattern-matching-related methods to [PushMessage].
extension PushMessagePatterns on PushMessage {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(PushMessage_IMessage value)? iMessage,
    TResult Function(PushMessage_SendConfirm value)? sendConfirm,
    TResult Function(PushMessage_RegistrationState value)? registrationState,
    TResult Function(PushMessage_NewPhotostream value)? newPhotostream,
    TResult Function(PushMessage_FaceTime value)? faceTime,
    TResult Function(PushMessage_StatusUpdate value)? statusUpdate,
    TResult Function(PushMessage_Idms value)? idms,
    TResult Function(PushMessage_TwoFaAuthEvent value)? twoFaAuthEvent,
    TResult Function(PushMessage_CircleFinishEvent value)? circleFinishEvent,
    TResult Function(PushMessage_BeaconShared value)? beaconShared,
    TResult Function(PushMessage_ProcessQueue value)? processQueue,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case PushMessage_IMessage() when iMessage != null:
        return iMessage(_that);
      case PushMessage_SendConfirm() when sendConfirm != null:
        return sendConfirm(_that);
      case PushMessage_RegistrationState() when registrationState != null:
        return registrationState(_that);
      case PushMessage_NewPhotostream() when newPhotostream != null:
        return newPhotostream(_that);
      case PushMessage_FaceTime() when faceTime != null:
        return faceTime(_that);
      case PushMessage_StatusUpdate() when statusUpdate != null:
        return statusUpdate(_that);
      case PushMessage_Idms() when idms != null:
        return idms(_that);
      case PushMessage_TwoFaAuthEvent() when twoFaAuthEvent != null:
        return twoFaAuthEvent(_that);
      case PushMessage_CircleFinishEvent() when circleFinishEvent != null:
        return circleFinishEvent(_that);
      case PushMessage_BeaconShared() when beaconShared != null:
        return beaconShared(_that);
      case PushMessage_ProcessQueue() when processQueue != null:
        return processQueue(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(PushMessage_IMessage value) iMessage,
    required TResult Function(PushMessage_SendConfirm value) sendConfirm,
    required TResult Function(PushMessage_RegistrationState value)
        registrationState,
    required TResult Function(PushMessage_NewPhotostream value) newPhotostream,
    required TResult Function(PushMessage_FaceTime value) faceTime,
    required TResult Function(PushMessage_StatusUpdate value) statusUpdate,
    required TResult Function(PushMessage_Idms value) idms,
    required TResult Function(PushMessage_TwoFaAuthEvent value) twoFaAuthEvent,
    required TResult Function(PushMessage_CircleFinishEvent value)
        circleFinishEvent,
    required TResult Function(PushMessage_BeaconShared value) beaconShared,
    required TResult Function(PushMessage_ProcessQueue value) processQueue,
  }) {
    final _that = this;
    switch (_that) {
      case PushMessage_IMessage():
        return iMessage(_that);
      case PushMessage_SendConfirm():
        return sendConfirm(_that);
      case PushMessage_RegistrationState():
        return registrationState(_that);
      case PushMessage_NewPhotostream():
        return newPhotostream(_that);
      case PushMessage_FaceTime():
        return faceTime(_that);
      case PushMessage_StatusUpdate():
        return statusUpdate(_that);
      case PushMessage_Idms():
        return idms(_that);
      case PushMessage_TwoFaAuthEvent():
        return twoFaAuthEvent(_that);
      case PushMessage_CircleFinishEvent():
        return circleFinishEvent(_that);
      case PushMessage_BeaconShared():
        return beaconShared(_that);
      case PushMessage_ProcessQueue():
        return processQueue(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(PushMessage_IMessage value)? iMessage,
    TResult? Function(PushMessage_SendConfirm value)? sendConfirm,
    TResult? Function(PushMessage_RegistrationState value)? registrationState,
    TResult? Function(PushMessage_NewPhotostream value)? newPhotostream,
    TResult? Function(PushMessage_FaceTime value)? faceTime,
    TResult? Function(PushMessage_StatusUpdate value)? statusUpdate,
    TResult? Function(PushMessage_Idms value)? idms,
    TResult? Function(PushMessage_TwoFaAuthEvent value)? twoFaAuthEvent,
    TResult? Function(PushMessage_CircleFinishEvent value)? circleFinishEvent,
    TResult? Function(PushMessage_BeaconShared value)? beaconShared,
    TResult? Function(PushMessage_ProcessQueue value)? processQueue,
  }) {
    final _that = this;
    switch (_that) {
      case PushMessage_IMessage() when iMessage != null:
        return iMessage(_that);
      case PushMessage_SendConfirm() when sendConfirm != null:
        return sendConfirm(_that);
      case PushMessage_RegistrationState() when registrationState != null:
        return registrationState(_that);
      case PushMessage_NewPhotostream() when newPhotostream != null:
        return newPhotostream(_that);
      case PushMessage_FaceTime() when faceTime != null:
        return faceTime(_that);
      case PushMessage_StatusUpdate() when statusUpdate != null:
        return statusUpdate(_that);
      case PushMessage_Idms() when idms != null:
        return idms(_that);
      case PushMessage_TwoFaAuthEvent() when twoFaAuthEvent != null:
        return twoFaAuthEvent(_that);
      case PushMessage_CircleFinishEvent() when circleFinishEvent != null:
        return circleFinishEvent(_that);
      case PushMessage_BeaconShared() when beaconShared != null:
        return beaconShared(_that);
      case PushMessage_ProcessQueue() when processQueue != null:
        return processQueue(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function(MessageInst field0)? iMessage,
    TResult Function(String uuid, String? error)? sendConfirm,
    TResult Function(RegisterState field0)? registrationState,
    TResult Function(SharedAlbum field0)? newPhotostream,
    TResult Function(FTMessage field0)? faceTime,
    TResult Function(StatusKitMessage field0)? statusUpdate,
    TResult Function(IdmsMessage field0)? idms,
    TResult Function(bool field0)? twoFaAuthEvent,
    TResult Function()? circleFinishEvent,
    TResult Function(String sender, String beacon, BeaconAttributes attributes)?
        beaconShared,
    TResult Function()? processQueue,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case PushMessage_IMessage() when iMessage != null:
        return iMessage(_that.field0);
      case PushMessage_SendConfirm() when sendConfirm != null:
        return sendConfirm(_that.uuid, _that.error);
      case PushMessage_RegistrationState() when registrationState != null:
        return registrationState(_that.field0);
      case PushMessage_NewPhotostream() when newPhotostream != null:
        return newPhotostream(_that.field0);
      case PushMessage_FaceTime() when faceTime != null:
        return faceTime(_that.field0);
      case PushMessage_StatusUpdate() when statusUpdate != null:
        return statusUpdate(_that.field0);
      case PushMessage_Idms() when idms != null:
        return idms(_that.field0);
      case PushMessage_TwoFaAuthEvent() when twoFaAuthEvent != null:
        return twoFaAuthEvent(_that.field0);
      case PushMessage_CircleFinishEvent() when circleFinishEvent != null:
        return circleFinishEvent();
      case PushMessage_BeaconShared() when beaconShared != null:
        return beaconShared(_that.sender, _that.beacon, _that.attributes);
      case PushMessage_ProcessQueue() when processQueue != null:
        return processQueue();
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function(MessageInst field0) iMessage,
    required TResult Function(String uuid, String? error) sendConfirm,
    required TResult Function(RegisterState field0) registrationState,
    required TResult Function(SharedAlbum field0) newPhotostream,
    required TResult Function(FTMessage field0) faceTime,
    required TResult Function(StatusKitMessage field0) statusUpdate,
    required TResult Function(IdmsMessage field0) idms,
    required TResult Function(bool field0) twoFaAuthEvent,
    required TResult Function() circleFinishEvent,
    required TResult Function(
            String sender, String beacon, BeaconAttributes attributes)
        beaconShared,
    required TResult Function() processQueue,
  }) {
    final _that = this;
    switch (_that) {
      case PushMessage_IMessage():
        return iMessage(_that.field0);
      case PushMessage_SendConfirm():
        return sendConfirm(_that.uuid, _that.error);
      case PushMessage_RegistrationState():
        return registrationState(_that.field0);
      case PushMessage_NewPhotostream():
        return newPhotostream(_that.field0);
      case PushMessage_FaceTime():
        return faceTime(_that.field0);
      case PushMessage_StatusUpdate():
        return statusUpdate(_that.field0);
      case PushMessage_Idms():
        return idms(_that.field0);
      case PushMessage_TwoFaAuthEvent():
        return twoFaAuthEvent(_that.field0);
      case PushMessage_CircleFinishEvent():
        return circleFinishEvent();
      case PushMessage_BeaconShared():
        return beaconShared(_that.sender, _that.beacon, _that.attributes);
      case PushMessage_ProcessQueue():
        return processQueue();
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function(MessageInst field0)? iMessage,
    TResult? Function(String uuid, String? error)? sendConfirm,
    TResult? Function(RegisterState field0)? registrationState,
    TResult? Function(SharedAlbum field0)? newPhotostream,
    TResult? Function(FTMessage field0)? faceTime,
    TResult? Function(StatusKitMessage field0)? statusUpdate,
    TResult? Function(IdmsMessage field0)? idms,
    TResult? Function(bool field0)? twoFaAuthEvent,
    TResult? Function()? circleFinishEvent,
    TResult? Function(
            String sender, String beacon, BeaconAttributes attributes)?
        beaconShared,
    TResult? Function()? processQueue,
  }) {
    final _that = this;
    switch (_that) {
      case PushMessage_IMessage() when iMessage != null:
        return iMessage(_that.field0);
      case PushMessage_SendConfirm() when sendConfirm != null:
        return sendConfirm(_that.uuid, _that.error);
      case PushMessage_RegistrationState() when registrationState != null:
        return registrationState(_that.field0);
      case PushMessage_NewPhotostream() when newPhotostream != null:
        return newPhotostream(_that.field0);
      case PushMessage_FaceTime() when faceTime != null:
        return faceTime(_that.field0);
      case PushMessage_StatusUpdate() when statusUpdate != null:
        return statusUpdate(_that.field0);
      case PushMessage_Idms() when idms != null:
        return idms(_that.field0);
      case PushMessage_TwoFaAuthEvent() when twoFaAuthEvent != null:
        return twoFaAuthEvent(_that.field0);
      case PushMessage_CircleFinishEvent() when circleFinishEvent != null:
        return circleFinishEvent();
      case PushMessage_BeaconShared() when beaconShared != null:
        return beaconShared(_that.sender, _that.beacon, _that.attributes);
      case PushMessage_ProcessQueue() when processQueue != null:
        return processQueue();
      case _:
        return null;
    }
  }
}

/// @nodoc

class PushMessage_IMessage extends PushMessage {
  const PushMessage_IMessage(this.field0) : super._();

  final MessageInst field0;

  /// Create a copy of PushMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $PushMessage_IMessageCopyWith<PushMessage_IMessage> get copyWith =>
      _$PushMessage_IMessageCopyWithImpl<PushMessage_IMessage>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is PushMessage_IMessage &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'PushMessage.iMessage(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $PushMessage_IMessageCopyWith<$Res>
    implements $PushMessageCopyWith<$Res> {
  factory $PushMessage_IMessageCopyWith(PushMessage_IMessage value,
          $Res Function(PushMessage_IMessage) _then) =
      _$PushMessage_IMessageCopyWithImpl;
  @useResult
  $Res call({MessageInst field0});
}

/// @nodoc
class _$PushMessage_IMessageCopyWithImpl<$Res>
    implements $PushMessage_IMessageCopyWith<$Res> {
  _$PushMessage_IMessageCopyWithImpl(this._self, this._then);

  final PushMessage_IMessage _self;
  final $Res Function(PushMessage_IMessage) _then;

  /// Create a copy of PushMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(PushMessage_IMessage(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as MessageInst,
    ));
  }
}

/// @nodoc

class PushMessage_SendConfirm extends PushMessage {
  const PushMessage_SendConfirm({required this.uuid, this.error}) : super._();

  final String uuid;
  final String? error;

  /// Create a copy of PushMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $PushMessage_SendConfirmCopyWith<PushMessage_SendConfirm> get copyWith =>
      _$PushMessage_SendConfirmCopyWithImpl<PushMessage_SendConfirm>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is PushMessage_SendConfirm &&
            (identical(other.uuid, uuid) || other.uuid == uuid) &&
            (identical(other.error, error) || other.error == error));
  }

  @override
  int get hashCode => Object.hash(runtimeType, uuid, error);

  @override
  String toString() {
    return 'PushMessage.sendConfirm(uuid: $uuid, error: $error)';
  }
}

/// @nodoc
abstract mixin class $PushMessage_SendConfirmCopyWith<$Res>
    implements $PushMessageCopyWith<$Res> {
  factory $PushMessage_SendConfirmCopyWith(PushMessage_SendConfirm value,
          $Res Function(PushMessage_SendConfirm) _then) =
      _$PushMessage_SendConfirmCopyWithImpl;
  @useResult
  $Res call({String uuid, String? error});
}

/// @nodoc
class _$PushMessage_SendConfirmCopyWithImpl<$Res>
    implements $PushMessage_SendConfirmCopyWith<$Res> {
  _$PushMessage_SendConfirmCopyWithImpl(this._self, this._then);

  final PushMessage_SendConfirm _self;
  final $Res Function(PushMessage_SendConfirm) _then;

  /// Create a copy of PushMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? uuid = null,
    Object? error = freezed,
  }) {
    return _then(PushMessage_SendConfirm(
      uuid: null == uuid
          ? _self.uuid
          : uuid // ignore: cast_nullable_to_non_nullable
              as String,
      error: freezed == error
          ? _self.error
          : error // ignore: cast_nullable_to_non_nullable
              as String?,
    ));
  }
}

/// @nodoc

class PushMessage_RegistrationState extends PushMessage {
  const PushMessage_RegistrationState(this.field0) : super._();

  final RegisterState field0;

  /// Create a copy of PushMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $PushMessage_RegistrationStateCopyWith<PushMessage_RegistrationState>
      get copyWith => _$PushMessage_RegistrationStateCopyWithImpl<
          PushMessage_RegistrationState>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is PushMessage_RegistrationState &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'PushMessage.registrationState(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $PushMessage_RegistrationStateCopyWith<$Res>
    implements $PushMessageCopyWith<$Res> {
  factory $PushMessage_RegistrationStateCopyWith(
          PushMessage_RegistrationState value,
          $Res Function(PushMessage_RegistrationState) _then) =
      _$PushMessage_RegistrationStateCopyWithImpl;
  @useResult
  $Res call({RegisterState field0});

  $RegisterStateCopyWith<$Res> get field0;
}

/// @nodoc
class _$PushMessage_RegistrationStateCopyWithImpl<$Res>
    implements $PushMessage_RegistrationStateCopyWith<$Res> {
  _$PushMessage_RegistrationStateCopyWithImpl(this._self, this._then);

  final PushMessage_RegistrationState _self;
  final $Res Function(PushMessage_RegistrationState) _then;

  /// Create a copy of PushMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(PushMessage_RegistrationState(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as RegisterState,
    ));
  }

  /// Create a copy of PushMessage
  /// with the given fields replaced by the non-null parameter values.
  @override
  @pragma('vm:prefer-inline')
  $RegisterStateCopyWith<$Res> get field0 {
    return $RegisterStateCopyWith<$Res>(_self.field0, (value) {
      return _then(_self.copyWith(field0: value));
    });
  }
}

/// @nodoc

class PushMessage_NewPhotostream extends PushMessage {
  const PushMessage_NewPhotostream(this.field0) : super._();

  final SharedAlbum field0;

  /// Create a copy of PushMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $PushMessage_NewPhotostreamCopyWith<PushMessage_NewPhotostream>
      get copyWith =>
          _$PushMessage_NewPhotostreamCopyWithImpl<PushMessage_NewPhotostream>(
              this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is PushMessage_NewPhotostream &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'PushMessage.newPhotostream(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $PushMessage_NewPhotostreamCopyWith<$Res>
    implements $PushMessageCopyWith<$Res> {
  factory $PushMessage_NewPhotostreamCopyWith(PushMessage_NewPhotostream value,
          $Res Function(PushMessage_NewPhotostream) _then) =
      _$PushMessage_NewPhotostreamCopyWithImpl;
  @useResult
  $Res call({SharedAlbum field0});
}

/// @nodoc
class _$PushMessage_NewPhotostreamCopyWithImpl<$Res>
    implements $PushMessage_NewPhotostreamCopyWith<$Res> {
  _$PushMessage_NewPhotostreamCopyWithImpl(this._self, this._then);

  final PushMessage_NewPhotostream _self;
  final $Res Function(PushMessage_NewPhotostream) _then;

  /// Create a copy of PushMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(PushMessage_NewPhotostream(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as SharedAlbum,
    ));
  }
}

/// @nodoc

class PushMessage_FaceTime extends PushMessage {
  const PushMessage_FaceTime(this.field0) : super._();

  final FTMessage field0;

  /// Create a copy of PushMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $PushMessage_FaceTimeCopyWith<PushMessage_FaceTime> get copyWith =>
      _$PushMessage_FaceTimeCopyWithImpl<PushMessage_FaceTime>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is PushMessage_FaceTime &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'PushMessage.faceTime(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $PushMessage_FaceTimeCopyWith<$Res>
    implements $PushMessageCopyWith<$Res> {
  factory $PushMessage_FaceTimeCopyWith(PushMessage_FaceTime value,
          $Res Function(PushMessage_FaceTime) _then) =
      _$PushMessage_FaceTimeCopyWithImpl;
  @useResult
  $Res call({FTMessage field0});

  $FTMessageCopyWith<$Res> get field0;
}

/// @nodoc
class _$PushMessage_FaceTimeCopyWithImpl<$Res>
    implements $PushMessage_FaceTimeCopyWith<$Res> {
  _$PushMessage_FaceTimeCopyWithImpl(this._self, this._then);

  final PushMessage_FaceTime _self;
  final $Res Function(PushMessage_FaceTime) _then;

  /// Create a copy of PushMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(PushMessage_FaceTime(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as FTMessage,
    ));
  }

  /// Create a copy of PushMessage
  /// with the given fields replaced by the non-null parameter values.
  @override
  @pragma('vm:prefer-inline')
  $FTMessageCopyWith<$Res> get field0 {
    return $FTMessageCopyWith<$Res>(_self.field0, (value) {
      return _then(_self.copyWith(field0: value));
    });
  }
}

/// @nodoc

class PushMessage_StatusUpdate extends PushMessage {
  const PushMessage_StatusUpdate(this.field0) : super._();

  final StatusKitMessage field0;

  /// Create a copy of PushMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $PushMessage_StatusUpdateCopyWith<PushMessage_StatusUpdate> get copyWith =>
      _$PushMessage_StatusUpdateCopyWithImpl<PushMessage_StatusUpdate>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is PushMessage_StatusUpdate &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'PushMessage.statusUpdate(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $PushMessage_StatusUpdateCopyWith<$Res>
    implements $PushMessageCopyWith<$Res> {
  factory $PushMessage_StatusUpdateCopyWith(PushMessage_StatusUpdate value,
          $Res Function(PushMessage_StatusUpdate) _then) =
      _$PushMessage_StatusUpdateCopyWithImpl;
  @useResult
  $Res call({StatusKitMessage field0});

  $StatusKitMessageCopyWith<$Res> get field0;
}

/// @nodoc
class _$PushMessage_StatusUpdateCopyWithImpl<$Res>
    implements $PushMessage_StatusUpdateCopyWith<$Res> {
  _$PushMessage_StatusUpdateCopyWithImpl(this._self, this._then);

  final PushMessage_StatusUpdate _self;
  final $Res Function(PushMessage_StatusUpdate) _then;

  /// Create a copy of PushMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(PushMessage_StatusUpdate(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as StatusKitMessage,
    ));
  }

  /// Create a copy of PushMessage
  /// with the given fields replaced by the non-null parameter values.
  @override
  @pragma('vm:prefer-inline')
  $StatusKitMessageCopyWith<$Res> get field0 {
    return $StatusKitMessageCopyWith<$Res>(_self.field0, (value) {
      return _then(_self.copyWith(field0: value));
    });
  }
}

/// @nodoc

class PushMessage_Idms extends PushMessage {
  const PushMessage_Idms(this.field0) : super._();

  final IdmsMessage field0;

  /// Create a copy of PushMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $PushMessage_IdmsCopyWith<PushMessage_Idms> get copyWith =>
      _$PushMessage_IdmsCopyWithImpl<PushMessage_Idms>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is PushMessage_Idms &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'PushMessage.idms(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $PushMessage_IdmsCopyWith<$Res>
    implements $PushMessageCopyWith<$Res> {
  factory $PushMessage_IdmsCopyWith(
          PushMessage_Idms value, $Res Function(PushMessage_Idms) _then) =
      _$PushMessage_IdmsCopyWithImpl;
  @useResult
  $Res call({IdmsMessage field0});

  $IdmsMessageCopyWith<$Res> get field0;
}

/// @nodoc
class _$PushMessage_IdmsCopyWithImpl<$Res>
    implements $PushMessage_IdmsCopyWith<$Res> {
  _$PushMessage_IdmsCopyWithImpl(this._self, this._then);

  final PushMessage_Idms _self;
  final $Res Function(PushMessage_Idms) _then;

  /// Create a copy of PushMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(PushMessage_Idms(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as IdmsMessage,
    ));
  }

  /// Create a copy of PushMessage
  /// with the given fields replaced by the non-null parameter values.
  @override
  @pragma('vm:prefer-inline')
  $IdmsMessageCopyWith<$Res> get field0 {
    return $IdmsMessageCopyWith<$Res>(_self.field0, (value) {
      return _then(_self.copyWith(field0: value));
    });
  }
}

/// @nodoc

class PushMessage_TwoFaAuthEvent extends PushMessage {
  const PushMessage_TwoFaAuthEvent(this.field0) : super._();

  final bool field0;

  /// Create a copy of PushMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $PushMessage_TwoFaAuthEventCopyWith<PushMessage_TwoFaAuthEvent>
      get copyWith =>
          _$PushMessage_TwoFaAuthEventCopyWithImpl<PushMessage_TwoFaAuthEvent>(
              this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is PushMessage_TwoFaAuthEvent &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'PushMessage.twoFaAuthEvent(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $PushMessage_TwoFaAuthEventCopyWith<$Res>
    implements $PushMessageCopyWith<$Res> {
  factory $PushMessage_TwoFaAuthEventCopyWith(PushMessage_TwoFaAuthEvent value,
          $Res Function(PushMessage_TwoFaAuthEvent) _then) =
      _$PushMessage_TwoFaAuthEventCopyWithImpl;
  @useResult
  $Res call({bool field0});
}

/// @nodoc
class _$PushMessage_TwoFaAuthEventCopyWithImpl<$Res>
    implements $PushMessage_TwoFaAuthEventCopyWith<$Res> {
  _$PushMessage_TwoFaAuthEventCopyWithImpl(this._self, this._then);

  final PushMessage_TwoFaAuthEvent _self;
  final $Res Function(PushMessage_TwoFaAuthEvent) _then;

  /// Create a copy of PushMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(PushMessage_TwoFaAuthEvent(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as bool,
    ));
  }
}

/// @nodoc

class PushMessage_CircleFinishEvent extends PushMessage {
  const PushMessage_CircleFinishEvent() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is PushMessage_CircleFinishEvent);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'PushMessage.circleFinishEvent()';
  }
}

/// @nodoc

class PushMessage_BeaconShared extends PushMessage {
  const PushMessage_BeaconShared(
      {required this.sender, required this.beacon, required this.attributes})
      : super._();

  final String sender;
  final String beacon;
  final BeaconAttributes attributes;

  /// Create a copy of PushMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $PushMessage_BeaconSharedCopyWith<PushMessage_BeaconShared> get copyWith =>
      _$PushMessage_BeaconSharedCopyWithImpl<PushMessage_BeaconShared>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is PushMessage_BeaconShared &&
            (identical(other.sender, sender) || other.sender == sender) &&
            (identical(other.beacon, beacon) || other.beacon == beacon) &&
            (identical(other.attributes, attributes) ||
                other.attributes == attributes));
  }

  @override
  int get hashCode => Object.hash(runtimeType, sender, beacon, attributes);

  @override
  String toString() {
    return 'PushMessage.beaconShared(sender: $sender, beacon: $beacon, attributes: $attributes)';
  }
}

/// @nodoc
abstract mixin class $PushMessage_BeaconSharedCopyWith<$Res>
    implements $PushMessageCopyWith<$Res> {
  factory $PushMessage_BeaconSharedCopyWith(PushMessage_BeaconShared value,
          $Res Function(PushMessage_BeaconShared) _then) =
      _$PushMessage_BeaconSharedCopyWithImpl;
  @useResult
  $Res call({String sender, String beacon, BeaconAttributes attributes});
}

/// @nodoc
class _$PushMessage_BeaconSharedCopyWithImpl<$Res>
    implements $PushMessage_BeaconSharedCopyWith<$Res> {
  _$PushMessage_BeaconSharedCopyWithImpl(this._self, this._then);

  final PushMessage_BeaconShared _self;
  final $Res Function(PushMessage_BeaconShared) _then;

  /// Create a copy of PushMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? sender = null,
    Object? beacon = null,
    Object? attributes = null,
  }) {
    return _then(PushMessage_BeaconShared(
      sender: null == sender
          ? _self.sender
          : sender // ignore: cast_nullable_to_non_nullable
              as String,
      beacon: null == beacon
          ? _self.beacon
          : beacon // ignore: cast_nullable_to_non_nullable
              as String,
      attributes: null == attributes
          ? _self.attributes
          : attributes // ignore: cast_nullable_to_non_nullable
              as BeaconAttributes,
    ));
  }
}

/// @nodoc

class PushMessage_ProcessQueue extends PushMessage {
  const PushMessage_ProcessQueue() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is PushMessage_ProcessQueue);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'PushMessage.processQueue()';
  }
}

/// @nodoc
mixin _$ReactMessageType {
  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is ReactMessageType);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'ReactMessageType()';
  }
}

/// @nodoc
class $ReactMessageTypeCopyWith<$Res> {
  $ReactMessageTypeCopyWith(
      ReactMessageType _, $Res Function(ReactMessageType) __);
}

/// Adds pattern-matching-related methods to [ReactMessageType].
extension ReactMessageTypePatterns on ReactMessageType {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(ReactMessageType_React value)? react,
    TResult Function(ReactMessageType_Extension value)? extension_,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case ReactMessageType_React() when react != null:
        return react(_that);
      case ReactMessageType_Extension() when extension_ != null:
        return extension_(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(ReactMessageType_React value) react,
    required TResult Function(ReactMessageType_Extension value) extension_,
  }) {
    final _that = this;
    switch (_that) {
      case ReactMessageType_React():
        return react(_that);
      case ReactMessageType_Extension():
        return extension_(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(ReactMessageType_React value)? react,
    TResult? Function(ReactMessageType_Extension value)? extension_,
  }) {
    final _that = this;
    switch (_that) {
      case ReactMessageType_React() when react != null:
        return react(_that);
      case ReactMessageType_Extension() when extension_ != null:
        return extension_(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function(Reaction reaction, bool enable)? react,
    TResult Function(ExtensionApp spec, MessageParts body, bool isMeta)?
        extension_,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case ReactMessageType_React() when react != null:
        return react(_that.reaction, _that.enable);
      case ReactMessageType_Extension() when extension_ != null:
        return extension_(_that.spec, _that.body, _that.isMeta);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function(Reaction reaction, bool enable) react,
    required TResult Function(ExtensionApp spec, MessageParts body, bool isMeta)
        extension_,
  }) {
    final _that = this;
    switch (_that) {
      case ReactMessageType_React():
        return react(_that.reaction, _that.enable);
      case ReactMessageType_Extension():
        return extension_(_that.spec, _that.body, _that.isMeta);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function(Reaction reaction, bool enable)? react,
    TResult? Function(ExtensionApp spec, MessageParts body, bool isMeta)?
        extension_,
  }) {
    final _that = this;
    switch (_that) {
      case ReactMessageType_React() when react != null:
        return react(_that.reaction, _that.enable);
      case ReactMessageType_Extension() when extension_ != null:
        return extension_(_that.spec, _that.body, _that.isMeta);
      case _:
        return null;
    }
  }
}

/// @nodoc

class ReactMessageType_React extends ReactMessageType {
  const ReactMessageType_React({required this.reaction, required this.enable})
      : super._();

  final Reaction reaction;
  final bool enable;

  /// Create a copy of ReactMessageType
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $ReactMessageType_ReactCopyWith<ReactMessageType_React> get copyWith =>
      _$ReactMessageType_ReactCopyWithImpl<ReactMessageType_React>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is ReactMessageType_React &&
            (identical(other.reaction, reaction) ||
                other.reaction == reaction) &&
            (identical(other.enable, enable) || other.enable == enable));
  }

  @override
  int get hashCode => Object.hash(runtimeType, reaction, enable);

  @override
  String toString() {
    return 'ReactMessageType.react(reaction: $reaction, enable: $enable)';
  }
}

/// @nodoc
abstract mixin class $ReactMessageType_ReactCopyWith<$Res>
    implements $ReactMessageTypeCopyWith<$Res> {
  factory $ReactMessageType_ReactCopyWith(ReactMessageType_React value,
          $Res Function(ReactMessageType_React) _then) =
      _$ReactMessageType_ReactCopyWithImpl;
  @useResult
  $Res call({Reaction reaction, bool enable});

  $ReactionCopyWith<$Res> get reaction;
}

/// @nodoc
class _$ReactMessageType_ReactCopyWithImpl<$Res>
    implements $ReactMessageType_ReactCopyWith<$Res> {
  _$ReactMessageType_ReactCopyWithImpl(this._self, this._then);

  final ReactMessageType_React _self;
  final $Res Function(ReactMessageType_React) _then;

  /// Create a copy of ReactMessageType
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? reaction = null,
    Object? enable = null,
  }) {
    return _then(ReactMessageType_React(
      reaction: null == reaction
          ? _self.reaction
          : reaction // ignore: cast_nullable_to_non_nullable
              as Reaction,
      enable: null == enable
          ? _self.enable
          : enable // ignore: cast_nullable_to_non_nullable
              as bool,
    ));
  }

  /// Create a copy of ReactMessageType
  /// with the given fields replaced by the non-null parameter values.
  @override
  @pragma('vm:prefer-inline')
  $ReactionCopyWith<$Res> get reaction {
    return $ReactionCopyWith<$Res>(_self.reaction, (value) {
      return _then(_self.copyWith(reaction: value));
    });
  }
}

/// @nodoc

class ReactMessageType_Extension extends ReactMessageType {
  const ReactMessageType_Extension(
      {required this.spec, required this.body, required this.isMeta})
      : super._();

  final ExtensionApp spec;
  final MessageParts body;
  final bool isMeta;

  /// Create a copy of ReactMessageType
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $ReactMessageType_ExtensionCopyWith<ReactMessageType_Extension>
      get copyWith =>
          _$ReactMessageType_ExtensionCopyWithImpl<ReactMessageType_Extension>(
              this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is ReactMessageType_Extension &&
            (identical(other.spec, spec) || other.spec == spec) &&
            (identical(other.body, body) || other.body == body) &&
            (identical(other.isMeta, isMeta) || other.isMeta == isMeta));
  }

  @override
  int get hashCode => Object.hash(runtimeType, spec, body, isMeta);

  @override
  String toString() {
    return 'ReactMessageType.extension_(spec: $spec, body: $body, isMeta: $isMeta)';
  }
}

/// @nodoc
abstract mixin class $ReactMessageType_ExtensionCopyWith<$Res>
    implements $ReactMessageTypeCopyWith<$Res> {
  factory $ReactMessageType_ExtensionCopyWith(ReactMessageType_Extension value,
          $Res Function(ReactMessageType_Extension) _then) =
      _$ReactMessageType_ExtensionCopyWithImpl;
  @useResult
  $Res call({ExtensionApp spec, MessageParts body, bool isMeta});
}

/// @nodoc
class _$ReactMessageType_ExtensionCopyWithImpl<$Res>
    implements $ReactMessageType_ExtensionCopyWith<$Res> {
  _$ReactMessageType_ExtensionCopyWithImpl(this._self, this._then);

  final ReactMessageType_Extension _self;
  final $Res Function(ReactMessageType_Extension) _then;

  /// Create a copy of ReactMessageType
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? spec = null,
    Object? body = null,
    Object? isMeta = null,
  }) {
    return _then(ReactMessageType_Extension(
      spec: null == spec
          ? _self.spec
          : spec // ignore: cast_nullable_to_non_nullable
              as ExtensionApp,
      body: null == body
          ? _self.body
          : body // ignore: cast_nullable_to_non_nullable
              as MessageParts,
      isMeta: null == isMeta
          ? _self.isMeta
          : isMeta // ignore: cast_nullable_to_non_nullable
              as bool,
    ));
  }
}

/// @nodoc
mixin _$Reaction {
  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is Reaction);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'Reaction()';
  }
}

/// @nodoc
class $ReactionCopyWith<$Res> {
  $ReactionCopyWith(Reaction _, $Res Function(Reaction) __);
}

/// Adds pattern-matching-related methods to [Reaction].
extension ReactionPatterns on Reaction {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(Reaction_Heart value)? heart,
    TResult Function(Reaction_Like value)? like,
    TResult Function(Reaction_Dislike value)? dislike,
    TResult Function(Reaction_Laugh value)? laugh,
    TResult Function(Reaction_Emphasize value)? emphasize,
    TResult Function(Reaction_Question value)? question,
    TResult Function(Reaction_Emoji value)? emoji,
    TResult Function(Reaction_Sticker value)? sticker,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case Reaction_Heart() when heart != null:
        return heart(_that);
      case Reaction_Like() when like != null:
        return like(_that);
      case Reaction_Dislike() when dislike != null:
        return dislike(_that);
      case Reaction_Laugh() when laugh != null:
        return laugh(_that);
      case Reaction_Emphasize() when emphasize != null:
        return emphasize(_that);
      case Reaction_Question() when question != null:
        return question(_that);
      case Reaction_Emoji() when emoji != null:
        return emoji(_that);
      case Reaction_Sticker() when sticker != null:
        return sticker(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(Reaction_Heart value) heart,
    required TResult Function(Reaction_Like value) like,
    required TResult Function(Reaction_Dislike value) dislike,
    required TResult Function(Reaction_Laugh value) laugh,
    required TResult Function(Reaction_Emphasize value) emphasize,
    required TResult Function(Reaction_Question value) question,
    required TResult Function(Reaction_Emoji value) emoji,
    required TResult Function(Reaction_Sticker value) sticker,
  }) {
    final _that = this;
    switch (_that) {
      case Reaction_Heart():
        return heart(_that);
      case Reaction_Like():
        return like(_that);
      case Reaction_Dislike():
        return dislike(_that);
      case Reaction_Laugh():
        return laugh(_that);
      case Reaction_Emphasize():
        return emphasize(_that);
      case Reaction_Question():
        return question(_that);
      case Reaction_Emoji():
        return emoji(_that);
      case Reaction_Sticker():
        return sticker(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(Reaction_Heart value)? heart,
    TResult? Function(Reaction_Like value)? like,
    TResult? Function(Reaction_Dislike value)? dislike,
    TResult? Function(Reaction_Laugh value)? laugh,
    TResult? Function(Reaction_Emphasize value)? emphasize,
    TResult? Function(Reaction_Question value)? question,
    TResult? Function(Reaction_Emoji value)? emoji,
    TResult? Function(Reaction_Sticker value)? sticker,
  }) {
    final _that = this;
    switch (_that) {
      case Reaction_Heart() when heart != null:
        return heart(_that);
      case Reaction_Like() when like != null:
        return like(_that);
      case Reaction_Dislike() when dislike != null:
        return dislike(_that);
      case Reaction_Laugh() when laugh != null:
        return laugh(_that);
      case Reaction_Emphasize() when emphasize != null:
        return emphasize(_that);
      case Reaction_Question() when question != null:
        return question(_that);
      case Reaction_Emoji() when emoji != null:
        return emoji(_that);
      case Reaction_Sticker() when sticker != null:
        return sticker(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function()? heart,
    TResult Function()? like,
    TResult Function()? dislike,
    TResult Function()? laugh,
    TResult Function()? emphasize,
    TResult Function()? question,
    TResult Function(String field0)? emoji,
    TResult Function(ExtensionApp? spec, MessageParts body)? sticker,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case Reaction_Heart() when heart != null:
        return heart();
      case Reaction_Like() when like != null:
        return like();
      case Reaction_Dislike() when dislike != null:
        return dislike();
      case Reaction_Laugh() when laugh != null:
        return laugh();
      case Reaction_Emphasize() when emphasize != null:
        return emphasize();
      case Reaction_Question() when question != null:
        return question();
      case Reaction_Emoji() when emoji != null:
        return emoji(_that.field0);
      case Reaction_Sticker() when sticker != null:
        return sticker(_that.spec, _that.body);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function() heart,
    required TResult Function() like,
    required TResult Function() dislike,
    required TResult Function() laugh,
    required TResult Function() emphasize,
    required TResult Function() question,
    required TResult Function(String field0) emoji,
    required TResult Function(ExtensionApp? spec, MessageParts body) sticker,
  }) {
    final _that = this;
    switch (_that) {
      case Reaction_Heart():
        return heart();
      case Reaction_Like():
        return like();
      case Reaction_Dislike():
        return dislike();
      case Reaction_Laugh():
        return laugh();
      case Reaction_Emphasize():
        return emphasize();
      case Reaction_Question():
        return question();
      case Reaction_Emoji():
        return emoji(_that.field0);
      case Reaction_Sticker():
        return sticker(_that.spec, _that.body);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function()? heart,
    TResult? Function()? like,
    TResult? Function()? dislike,
    TResult? Function()? laugh,
    TResult? Function()? emphasize,
    TResult? Function()? question,
    TResult? Function(String field0)? emoji,
    TResult? Function(ExtensionApp? spec, MessageParts body)? sticker,
  }) {
    final _that = this;
    switch (_that) {
      case Reaction_Heart() when heart != null:
        return heart();
      case Reaction_Like() when like != null:
        return like();
      case Reaction_Dislike() when dislike != null:
        return dislike();
      case Reaction_Laugh() when laugh != null:
        return laugh();
      case Reaction_Emphasize() when emphasize != null:
        return emphasize();
      case Reaction_Question() when question != null:
        return question();
      case Reaction_Emoji() when emoji != null:
        return emoji(_that.field0);
      case Reaction_Sticker() when sticker != null:
        return sticker(_that.spec, _that.body);
      case _:
        return null;
    }
  }
}

/// @nodoc

class Reaction_Heart extends Reaction {
  const Reaction_Heart() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is Reaction_Heart);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'Reaction.heart()';
  }
}

/// @nodoc

class Reaction_Like extends Reaction {
  const Reaction_Like() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is Reaction_Like);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'Reaction.like()';
  }
}

/// @nodoc

class Reaction_Dislike extends Reaction {
  const Reaction_Dislike() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is Reaction_Dislike);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'Reaction.dislike()';
  }
}

/// @nodoc

class Reaction_Laugh extends Reaction {
  const Reaction_Laugh() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is Reaction_Laugh);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'Reaction.laugh()';
  }
}

/// @nodoc

class Reaction_Emphasize extends Reaction {
  const Reaction_Emphasize() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is Reaction_Emphasize);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'Reaction.emphasize()';
  }
}

/// @nodoc

class Reaction_Question extends Reaction {
  const Reaction_Question() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is Reaction_Question);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'Reaction.question()';
  }
}

/// @nodoc

class Reaction_Emoji extends Reaction {
  const Reaction_Emoji(this.field0) : super._();

  final String field0;

  /// Create a copy of Reaction
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $Reaction_EmojiCopyWith<Reaction_Emoji> get copyWith =>
      _$Reaction_EmojiCopyWithImpl<Reaction_Emoji>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Reaction_Emoji &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'Reaction.emoji(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $Reaction_EmojiCopyWith<$Res>
    implements $ReactionCopyWith<$Res> {
  factory $Reaction_EmojiCopyWith(
          Reaction_Emoji value, $Res Function(Reaction_Emoji) _then) =
      _$Reaction_EmojiCopyWithImpl;
  @useResult
  $Res call({String field0});
}

/// @nodoc
class _$Reaction_EmojiCopyWithImpl<$Res>
    implements $Reaction_EmojiCopyWith<$Res> {
  _$Reaction_EmojiCopyWithImpl(this._self, this._then);

  final Reaction_Emoji _self;
  final $Res Function(Reaction_Emoji) _then;

  /// Create a copy of Reaction
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(Reaction_Emoji(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

/// @nodoc

class Reaction_Sticker extends Reaction {
  const Reaction_Sticker({this.spec, required this.body}) : super._();

  final ExtensionApp? spec;
  final MessageParts body;

  /// Create a copy of Reaction
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $Reaction_StickerCopyWith<Reaction_Sticker> get copyWith =>
      _$Reaction_StickerCopyWithImpl<Reaction_Sticker>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is Reaction_Sticker &&
            (identical(other.spec, spec) || other.spec == spec) &&
            (identical(other.body, body) || other.body == body));
  }

  @override
  int get hashCode => Object.hash(runtimeType, spec, body);

  @override
  String toString() {
    return 'Reaction.sticker(spec: $spec, body: $body)';
  }
}

/// @nodoc
abstract mixin class $Reaction_StickerCopyWith<$Res>
    implements $ReactionCopyWith<$Res> {
  factory $Reaction_StickerCopyWith(
          Reaction_Sticker value, $Res Function(Reaction_Sticker) _then) =
      _$Reaction_StickerCopyWithImpl;
  @useResult
  $Res call({ExtensionApp? spec, MessageParts body});
}

/// @nodoc
class _$Reaction_StickerCopyWithImpl<$Res>
    implements $Reaction_StickerCopyWith<$Res> {
  _$Reaction_StickerCopyWithImpl(this._self, this._then);

  final Reaction_Sticker _self;
  final $Res Function(Reaction_Sticker) _then;

  /// Create a copy of Reaction
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? spec = freezed,
    Object? body = null,
  }) {
    return _then(Reaction_Sticker(
      spec: freezed == spec
          ? _self.spec
          : spec // ignore: cast_nullable_to_non_nullable
              as ExtensionApp?,
      body: null == body
          ? _self.body
          : body // ignore: cast_nullable_to_non_nullable
              as MessageParts,
    ));
  }
}

/// @nodoc
mixin _$RegisterState {
  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is RegisterState);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'RegisterState()';
  }
}

/// @nodoc
class $RegisterStateCopyWith<$Res> {
  $RegisterStateCopyWith(RegisterState _, $Res Function(RegisterState) __);
}

/// Adds pattern-matching-related methods to [RegisterState].
extension RegisterStatePatterns on RegisterState {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(RegisterState_Registered value)? registered,
    TResult Function(RegisterState_Registering value)? registering,
    TResult Function(RegisterState_Failed value)? failed,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case RegisterState_Registered() when registered != null:
        return registered(_that);
      case RegisterState_Registering() when registering != null:
        return registering(_that);
      case RegisterState_Failed() when failed != null:
        return failed(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(RegisterState_Registered value) registered,
    required TResult Function(RegisterState_Registering value) registering,
    required TResult Function(RegisterState_Failed value) failed,
  }) {
    final _that = this;
    switch (_that) {
      case RegisterState_Registered():
        return registered(_that);
      case RegisterState_Registering():
        return registering(_that);
      case RegisterState_Failed():
        return failed(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(RegisterState_Registered value)? registered,
    TResult? Function(RegisterState_Registering value)? registering,
    TResult? Function(RegisterState_Failed value)? failed,
  }) {
    final _that = this;
    switch (_that) {
      case RegisterState_Registered() when registered != null:
        return registered(_that);
      case RegisterState_Registering() when registering != null:
        return registering(_that);
      case RegisterState_Failed() when failed != null:
        return failed(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function(PlatformInt64 nextS)? registered,
    TResult Function()? registering,
    TResult Function(BigInt? retryWait, String error)? failed,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case RegisterState_Registered() when registered != null:
        return registered(_that.nextS);
      case RegisterState_Registering() when registering != null:
        return registering();
      case RegisterState_Failed() when failed != null:
        return failed(_that.retryWait, _that.error);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function(PlatformInt64 nextS) registered,
    required TResult Function() registering,
    required TResult Function(BigInt? retryWait, String error) failed,
  }) {
    final _that = this;
    switch (_that) {
      case RegisterState_Registered():
        return registered(_that.nextS);
      case RegisterState_Registering():
        return registering();
      case RegisterState_Failed():
        return failed(_that.retryWait, _that.error);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function(PlatformInt64 nextS)? registered,
    TResult? Function()? registering,
    TResult? Function(BigInt? retryWait, String error)? failed,
  }) {
    final _that = this;
    switch (_that) {
      case RegisterState_Registered() when registered != null:
        return registered(_that.nextS);
      case RegisterState_Registering() when registering != null:
        return registering();
      case RegisterState_Failed() when failed != null:
        return failed(_that.retryWait, _that.error);
      case _:
        return null;
    }
  }
}

/// @nodoc

class RegisterState_Registered extends RegisterState {
  const RegisterState_Registered({required this.nextS}) : super._();

  final PlatformInt64 nextS;

  /// Create a copy of RegisterState
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $RegisterState_RegisteredCopyWith<RegisterState_Registered> get copyWith =>
      _$RegisterState_RegisteredCopyWithImpl<RegisterState_Registered>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is RegisterState_Registered &&
            (identical(other.nextS, nextS) || other.nextS == nextS));
  }

  @override
  int get hashCode => Object.hash(runtimeType, nextS);

  @override
  String toString() {
    return 'RegisterState.registered(nextS: $nextS)';
  }
}

/// @nodoc
abstract mixin class $RegisterState_RegisteredCopyWith<$Res>
    implements $RegisterStateCopyWith<$Res> {
  factory $RegisterState_RegisteredCopyWith(RegisterState_Registered value,
          $Res Function(RegisterState_Registered) _then) =
      _$RegisterState_RegisteredCopyWithImpl;
  @useResult
  $Res call({PlatformInt64 nextS});
}

/// @nodoc
class _$RegisterState_RegisteredCopyWithImpl<$Res>
    implements $RegisterState_RegisteredCopyWith<$Res> {
  _$RegisterState_RegisteredCopyWithImpl(this._self, this._then);

  final RegisterState_Registered _self;
  final $Res Function(RegisterState_Registered) _then;

  /// Create a copy of RegisterState
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? nextS = null,
  }) {
    return _then(RegisterState_Registered(
      nextS: null == nextS
          ? _self.nextS
          : nextS // ignore: cast_nullable_to_non_nullable
              as PlatformInt64,
    ));
  }
}

/// @nodoc

class RegisterState_Registering extends RegisterState {
  const RegisterState_Registering() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is RegisterState_Registering);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'RegisterState.registering()';
  }
}

/// @nodoc

class RegisterState_Failed extends RegisterState {
  const RegisterState_Failed({this.retryWait, required this.error}) : super._();

  final BigInt? retryWait;
  final String error;

  /// Create a copy of RegisterState
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $RegisterState_FailedCopyWith<RegisterState_Failed> get copyWith =>
      _$RegisterState_FailedCopyWithImpl<RegisterState_Failed>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is RegisterState_Failed &&
            (identical(other.retryWait, retryWait) ||
                other.retryWait == retryWait) &&
            (identical(other.error, error) || other.error == error));
  }

  @override
  int get hashCode => Object.hash(runtimeType, retryWait, error);

  @override
  String toString() {
    return 'RegisterState.failed(retryWait: $retryWait, error: $error)';
  }
}

/// @nodoc
abstract mixin class $RegisterState_FailedCopyWith<$Res>
    implements $RegisterStateCopyWith<$Res> {
  factory $RegisterState_FailedCopyWith(RegisterState_Failed value,
          $Res Function(RegisterState_Failed) _then) =
      _$RegisterState_FailedCopyWithImpl;
  @useResult
  $Res call({BigInt? retryWait, String error});
}

/// @nodoc
class _$RegisterState_FailedCopyWithImpl<$Res>
    implements $RegisterState_FailedCopyWith<$Res> {
  _$RegisterState_FailedCopyWithImpl(this._self, this._then);

  final RegisterState_Failed _self;
  final $Res Function(RegisterState_Failed) _then;

  /// Create a copy of RegisterState
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? retryWait = freezed,
    Object? error = null,
  }) {
    return _then(RegisterState_Failed(
      retryWait: freezed == retryWait
          ? _self.retryWait
          : retryWait // ignore: cast_nullable_to_non_nullable
              as BigInt?,
      error: null == error
          ? _self.error
          : error // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

/// @nodoc
mixin _$SetTranscriptBackgroundMessage {
  int get aid;
  BigInt get bid;
  String? get chatId;

  /// Create a copy of SetTranscriptBackgroundMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $SetTranscriptBackgroundMessageCopyWith<SetTranscriptBackgroundMessage>
      get copyWith => _$SetTranscriptBackgroundMessageCopyWithImpl<
              SetTranscriptBackgroundMessage>(
          this as SetTranscriptBackgroundMessage, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is SetTranscriptBackgroundMessage &&
            (identical(other.aid, aid) || other.aid == aid) &&
            (identical(other.bid, bid) || other.bid == bid) &&
            (identical(other.chatId, chatId) || other.chatId == chatId));
  }

  @override
  int get hashCode => Object.hash(runtimeType, aid, bid, chatId);

  @override
  String toString() {
    return 'SetTranscriptBackgroundMessage(aid: $aid, bid: $bid, chatId: $chatId)';
  }
}

/// @nodoc
abstract mixin class $SetTranscriptBackgroundMessageCopyWith<$Res> {
  factory $SetTranscriptBackgroundMessageCopyWith(
          SetTranscriptBackgroundMessage value,
          $Res Function(SetTranscriptBackgroundMessage) _then) =
      _$SetTranscriptBackgroundMessageCopyWithImpl;
  @useResult
  $Res call({int aid, BigInt bid, String? chatId});
}

/// @nodoc
class _$SetTranscriptBackgroundMessageCopyWithImpl<$Res>
    implements $SetTranscriptBackgroundMessageCopyWith<$Res> {
  _$SetTranscriptBackgroundMessageCopyWithImpl(this._self, this._then);

  final SetTranscriptBackgroundMessage _self;
  final $Res Function(SetTranscriptBackgroundMessage) _then;

  /// Create a copy of SetTranscriptBackgroundMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? aid = null,
    Object? bid = null,
    Object? chatId = freezed,
  }) {
    return _then(_self.copyWith(
      aid: null == aid
          ? _self.aid
          : aid // ignore: cast_nullable_to_non_nullable
              as int,
      bid: null == bid
          ? _self.bid
          : bid // ignore: cast_nullable_to_non_nullable
              as BigInt,
      chatId: freezed == chatId
          ? _self.chatId
          : chatId // ignore: cast_nullable_to_non_nullable
              as String?,
    ));
  }
}

/// Adds pattern-matching-related methods to [SetTranscriptBackgroundMessage].
extension SetTranscriptBackgroundMessagePatterns
    on SetTranscriptBackgroundMessage {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(SetTranscriptBackgroundMessage_Remove value)? remove,
    TResult Function(SetTranscriptBackgroundMessage_Set value)? set_,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case SetTranscriptBackgroundMessage_Remove() when remove != null:
        return remove(_that);
      case SetTranscriptBackgroundMessage_Set() when set_ != null:
        return set_(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(SetTranscriptBackgroundMessage_Remove value)
        remove,
    required TResult Function(SetTranscriptBackgroundMessage_Set value) set_,
  }) {
    final _that = this;
    switch (_that) {
      case SetTranscriptBackgroundMessage_Remove():
        return remove(_that);
      case SetTranscriptBackgroundMessage_Set():
        return set_(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(SetTranscriptBackgroundMessage_Remove value)? remove,
    TResult? Function(SetTranscriptBackgroundMessage_Set value)? set_,
  }) {
    final _that = this;
    switch (_that) {
      case SetTranscriptBackgroundMessage_Remove() when remove != null:
        return remove(_that);
      case SetTranscriptBackgroundMessage_Set() when set_ != null:
        return set_(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function(int aid, BigInt bid, String? chatId, bool remove)? remove,
    TResult Function(
            int aid,
            BigInt bid,
            String? chatId,
            String objectId,
            int payloadVersion,
            String backgroundId,
            String url,
            String signature,
            String key,
            BigInt fileSize)?
        set_,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case SetTranscriptBackgroundMessage_Remove() when remove != null:
        return remove(_that.aid, _that.bid, _that.chatId, _that.remove);
      case SetTranscriptBackgroundMessage_Set() when set_ != null:
        return set_(
            _that.aid,
            _that.bid,
            _that.chatId,
            _that.objectId,
            _that.payloadVersion,
            _that.backgroundId,
            _that.url,
            _that.signature,
            _that.key,
            _that.fileSize);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function(int aid, BigInt bid, String? chatId, bool remove)
        remove,
    required TResult Function(
            int aid,
            BigInt bid,
            String? chatId,
            String objectId,
            int payloadVersion,
            String backgroundId,
            String url,
            String signature,
            String key,
            BigInt fileSize)
        set_,
  }) {
    final _that = this;
    switch (_that) {
      case SetTranscriptBackgroundMessage_Remove():
        return remove(_that.aid, _that.bid, _that.chatId, _that.remove);
      case SetTranscriptBackgroundMessage_Set():
        return set_(
            _that.aid,
            _that.bid,
            _that.chatId,
            _that.objectId,
            _that.payloadVersion,
            _that.backgroundId,
            _that.url,
            _that.signature,
            _that.key,
            _that.fileSize);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function(int aid, BigInt bid, String? chatId, bool remove)? remove,
    TResult? Function(
            int aid,
            BigInt bid,
            String? chatId,
            String objectId,
            int payloadVersion,
            String backgroundId,
            String url,
            String signature,
            String key,
            BigInt fileSize)?
        set_,
  }) {
    final _that = this;
    switch (_that) {
      case SetTranscriptBackgroundMessage_Remove() when remove != null:
        return remove(_that.aid, _that.bid, _that.chatId, _that.remove);
      case SetTranscriptBackgroundMessage_Set() when set_ != null:
        return set_(
            _that.aid,
            _that.bid,
            _that.chatId,
            _that.objectId,
            _that.payloadVersion,
            _that.backgroundId,
            _that.url,
            _that.signature,
            _that.key,
            _that.fileSize);
      case _:
        return null;
    }
  }
}

/// @nodoc

class SetTranscriptBackgroundMessage_Remove
    extends SetTranscriptBackgroundMessage {
  const SetTranscriptBackgroundMessage_Remove(
      {required this.aid, required this.bid, this.chatId, required this.remove})
      : super._();

  @override
  final int aid;
  @override
  final BigInt bid;
  @override
  final String? chatId;
  final bool remove;

  /// Create a copy of SetTranscriptBackgroundMessage
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $SetTranscriptBackgroundMessage_RemoveCopyWith<
          SetTranscriptBackgroundMessage_Remove>
      get copyWith => _$SetTranscriptBackgroundMessage_RemoveCopyWithImpl<
          SetTranscriptBackgroundMessage_Remove>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is SetTranscriptBackgroundMessage_Remove &&
            (identical(other.aid, aid) || other.aid == aid) &&
            (identical(other.bid, bid) || other.bid == bid) &&
            (identical(other.chatId, chatId) || other.chatId == chatId) &&
            (identical(other.remove, remove) || other.remove == remove));
  }

  @override
  int get hashCode => Object.hash(runtimeType, aid, bid, chatId, remove);

  @override
  String toString() {
    return 'SetTranscriptBackgroundMessage.remove(aid: $aid, bid: $bid, chatId: $chatId, remove: $remove)';
  }
}

/// @nodoc
abstract mixin class $SetTranscriptBackgroundMessage_RemoveCopyWith<$Res>
    implements $SetTranscriptBackgroundMessageCopyWith<$Res> {
  factory $SetTranscriptBackgroundMessage_RemoveCopyWith(
          SetTranscriptBackgroundMessage_Remove value,
          $Res Function(SetTranscriptBackgroundMessage_Remove) _then) =
      _$SetTranscriptBackgroundMessage_RemoveCopyWithImpl;
  @override
  @useResult
  $Res call({int aid, BigInt bid, String? chatId, bool remove});
}

/// @nodoc
class _$SetTranscriptBackgroundMessage_RemoveCopyWithImpl<$Res>
    implements $SetTranscriptBackgroundMessage_RemoveCopyWith<$Res> {
  _$SetTranscriptBackgroundMessage_RemoveCopyWithImpl(this._self, this._then);

  final SetTranscriptBackgroundMessage_Remove _self;
  final $Res Function(SetTranscriptBackgroundMessage_Remove) _then;

  /// Create a copy of SetTranscriptBackgroundMessage
  /// with the given fields replaced by the non-null parameter values.
  @override
  @pragma('vm:prefer-inline')
  $Res call({
    Object? aid = null,
    Object? bid = null,
    Object? chatId = freezed,
    Object? remove = null,
  }) {
    return _then(SetTranscriptBackgroundMessage_Remove(
      aid: null == aid
          ? _self.aid
          : aid // ignore: cast_nullable_to_non_nullable
              as int,
      bid: null == bid
          ? _self.bid
          : bid // ignore: cast_nullable_to_non_nullable
              as BigInt,
      chatId: freezed == chatId
          ? _self.chatId
          : chatId // ignore: cast_nullable_to_non_nullable
              as String?,
      remove: null == remove
          ? _self.remove
          : remove // ignore: cast_nullable_to_non_nullable
              as bool,
    ));
  }
}

/// @nodoc

class SetTranscriptBackgroundMessage_Set
    extends SetTranscriptBackgroundMessage {
  const SetTranscriptBackgroundMessage_Set(
      {required this.aid,
      required this.bid,
      this.chatId,
      required this.objectId,
      required this.payloadVersion,
      required this.backgroundId,
      required this.url,
      required this.signature,
      required this.key,
      required this.fileSize})
      : super._();

  @override
  final int aid;
  @override
  final BigInt bid;
  @override
  final String? chatId;
  final String objectId;
  final int payloadVersion;
  final String backgroundId;
  final String url;
  final String signature;
  final String key;
  final BigInt fileSize;

  /// Create a copy of SetTranscriptBackgroundMessage
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $SetTranscriptBackgroundMessage_SetCopyWith<
          SetTranscriptBackgroundMessage_Set>
      get copyWith => _$SetTranscriptBackgroundMessage_SetCopyWithImpl<
          SetTranscriptBackgroundMessage_Set>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is SetTranscriptBackgroundMessage_Set &&
            (identical(other.aid, aid) || other.aid == aid) &&
            (identical(other.bid, bid) || other.bid == bid) &&
            (identical(other.chatId, chatId) || other.chatId == chatId) &&
            (identical(other.objectId, objectId) ||
                other.objectId == objectId) &&
            (identical(other.payloadVersion, payloadVersion) ||
                other.payloadVersion == payloadVersion) &&
            (identical(other.backgroundId, backgroundId) ||
                other.backgroundId == backgroundId) &&
            (identical(other.url, url) || other.url == url) &&
            (identical(other.signature, signature) ||
                other.signature == signature) &&
            (identical(other.key, key) || other.key == key) &&
            (identical(other.fileSize, fileSize) ||
                other.fileSize == fileSize));
  }

  @override
  int get hashCode => Object.hash(runtimeType, aid, bid, chatId, objectId,
      payloadVersion, backgroundId, url, signature, key, fileSize);

  @override
  String toString() {
    return 'SetTranscriptBackgroundMessage.set_(aid: $aid, bid: $bid, chatId: $chatId, objectId: $objectId, payloadVersion: $payloadVersion, backgroundId: $backgroundId, url: $url, signature: $signature, key: $key, fileSize: $fileSize)';
  }
}

/// @nodoc
abstract mixin class $SetTranscriptBackgroundMessage_SetCopyWith<$Res>
    implements $SetTranscriptBackgroundMessageCopyWith<$Res> {
  factory $SetTranscriptBackgroundMessage_SetCopyWith(
          SetTranscriptBackgroundMessage_Set value,
          $Res Function(SetTranscriptBackgroundMessage_Set) _then) =
      _$SetTranscriptBackgroundMessage_SetCopyWithImpl;
  @override
  @useResult
  $Res call(
      {int aid,
      BigInt bid,
      String? chatId,
      String objectId,
      int payloadVersion,
      String backgroundId,
      String url,
      String signature,
      String key,
      BigInt fileSize});
}

/// @nodoc
class _$SetTranscriptBackgroundMessage_SetCopyWithImpl<$Res>
    implements $SetTranscriptBackgroundMessage_SetCopyWith<$Res> {
  _$SetTranscriptBackgroundMessage_SetCopyWithImpl(this._self, this._then);

  final SetTranscriptBackgroundMessage_Set _self;
  final $Res Function(SetTranscriptBackgroundMessage_Set) _then;

  /// Create a copy of SetTranscriptBackgroundMessage
  /// with the given fields replaced by the non-null parameter values.
  @override
  @pragma('vm:prefer-inline')
  $Res call({
    Object? aid = null,
    Object? bid = null,
    Object? chatId = freezed,
    Object? objectId = null,
    Object? payloadVersion = null,
    Object? backgroundId = null,
    Object? url = null,
    Object? signature = null,
    Object? key = null,
    Object? fileSize = null,
  }) {
    return _then(SetTranscriptBackgroundMessage_Set(
      aid: null == aid
          ? _self.aid
          : aid // ignore: cast_nullable_to_non_nullable
              as int,
      bid: null == bid
          ? _self.bid
          : bid // ignore: cast_nullable_to_non_nullable
              as BigInt,
      chatId: freezed == chatId
          ? _self.chatId
          : chatId // ignore: cast_nullable_to_non_nullable
              as String?,
      objectId: null == objectId
          ? _self.objectId
          : objectId // ignore: cast_nullable_to_non_nullable
              as String,
      payloadVersion: null == payloadVersion
          ? _self.payloadVersion
          : payloadVersion // ignore: cast_nullable_to_non_nullable
              as int,
      backgroundId: null == backgroundId
          ? _self.backgroundId
          : backgroundId // ignore: cast_nullable_to_non_nullable
              as String,
      url: null == url
          ? _self.url
          : url // ignore: cast_nullable_to_non_nullable
              as String,
      signature: null == signature
          ? _self.signature
          : signature // ignore: cast_nullable_to_non_nullable
              as String,
      key: null == key
          ? _self.key
          : key // ignore: cast_nullable_to_non_nullable
              as String,
      fileSize: null == fileSize
          ? _self.fileSize
          : fileSize // ignore: cast_nullable_to_non_nullable
              as BigInt,
    ));
  }
}

/// @nodoc
mixin _$StatusKitMessage {
  String get user;
  String? get mode;
  bool get allowed;

  /// Create a copy of StatusKitMessage
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $StatusKitMessageCopyWith<StatusKitMessage> get copyWith =>
      _$StatusKitMessageCopyWithImpl<StatusKitMessage>(
          this as StatusKitMessage, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is StatusKitMessage &&
            (identical(other.user, user) || other.user == user) &&
            (identical(other.mode, mode) || other.mode == mode) &&
            (identical(other.allowed, allowed) || other.allowed == allowed));
  }

  @override
  int get hashCode => Object.hash(runtimeType, user, mode, allowed);

  @override
  String toString() {
    return 'StatusKitMessage(user: $user, mode: $mode, allowed: $allowed)';
  }
}

/// @nodoc
abstract mixin class $StatusKitMessageCopyWith<$Res> {
  factory $StatusKitMessageCopyWith(
          StatusKitMessage value, $Res Function(StatusKitMessage) _then) =
      _$StatusKitMessageCopyWithImpl;
  @useResult
  $Res call({String user, String? mode, bool allowed});
}

/// @nodoc
class _$StatusKitMessageCopyWithImpl<$Res>
    implements $StatusKitMessageCopyWith<$Res> {
  _$StatusKitMessageCopyWithImpl(this._self, this._then);

  final StatusKitMessage _self;
  final $Res Function(StatusKitMessage) _then;

  /// Create a copy of StatusKitMessage
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? user = null,
    Object? mode = freezed,
    Object? allowed = null,
  }) {
    return _then(_self.copyWith(
      user: null == user
          ? _self.user
          : user // ignore: cast_nullable_to_non_nullable
              as String,
      mode: freezed == mode
          ? _self.mode
          : mode // ignore: cast_nullable_to_non_nullable
              as String?,
      allowed: null == allowed
          ? _self.allowed
          : allowed // ignore: cast_nullable_to_non_nullable
              as bool,
    ));
  }
}

/// Adds pattern-matching-related methods to [StatusKitMessage].
extension StatusKitMessagePatterns on StatusKitMessage {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(StatusKitMessage_StatusChanged value)? statusChanged,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case StatusKitMessage_StatusChanged() when statusChanged != null:
        return statusChanged(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(StatusKitMessage_StatusChanged value)
        statusChanged,
  }) {
    final _that = this;
    switch (_that) {
      case StatusKitMessage_StatusChanged():
        return statusChanged(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(StatusKitMessage_StatusChanged value)? statusChanged,
  }) {
    final _that = this;
    switch (_that) {
      case StatusKitMessage_StatusChanged() when statusChanged != null:
        return statusChanged(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function(String user, String? mode, bool allowed)? statusChanged,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case StatusKitMessage_StatusChanged() when statusChanged != null:
        return statusChanged(_that.user, _that.mode, _that.allowed);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function(String user, String? mode, bool allowed)
        statusChanged,
  }) {
    final _that = this;
    switch (_that) {
      case StatusKitMessage_StatusChanged():
        return statusChanged(_that.user, _that.mode, _that.allowed);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function(String user, String? mode, bool allowed)? statusChanged,
  }) {
    final _that = this;
    switch (_that) {
      case StatusKitMessage_StatusChanged() when statusChanged != null:
        return statusChanged(_that.user, _that.mode, _that.allowed);
      case _:
        return null;
    }
  }
}

/// @nodoc

class StatusKitMessage_StatusChanged extends StatusKitMessage {
  const StatusKitMessage_StatusChanged(
      {required this.user, this.mode, required this.allowed})
      : super._();

  @override
  final String user;
  @override
  final String? mode;
  @override
  final bool allowed;

  /// Create a copy of StatusKitMessage
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $StatusKitMessage_StatusChangedCopyWith<StatusKitMessage_StatusChanged>
      get copyWith => _$StatusKitMessage_StatusChangedCopyWithImpl<
          StatusKitMessage_StatusChanged>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is StatusKitMessage_StatusChanged &&
            (identical(other.user, user) || other.user == user) &&
            (identical(other.mode, mode) || other.mode == mode) &&
            (identical(other.allowed, allowed) || other.allowed == allowed));
  }

  @override
  int get hashCode => Object.hash(runtimeType, user, mode, allowed);

  @override
  String toString() {
    return 'StatusKitMessage.statusChanged(user: $user, mode: $mode, allowed: $allowed)';
  }
}

/// @nodoc
abstract mixin class $StatusKitMessage_StatusChangedCopyWith<$Res>
    implements $StatusKitMessageCopyWith<$Res> {
  factory $StatusKitMessage_StatusChangedCopyWith(
          StatusKitMessage_StatusChanged value,
          $Res Function(StatusKitMessage_StatusChanged) _then) =
      _$StatusKitMessage_StatusChangedCopyWithImpl;
  @override
  @useResult
  $Res call({String user, String? mode, bool allowed});
}

/// @nodoc
class _$StatusKitMessage_StatusChangedCopyWithImpl<$Res>
    implements $StatusKitMessage_StatusChangedCopyWith<$Res> {
  _$StatusKitMessage_StatusChangedCopyWithImpl(this._self, this._then);

  final StatusKitMessage_StatusChanged _self;
  final $Res Function(StatusKitMessage_StatusChanged) _then;

  /// Create a copy of StatusKitMessage
  /// with the given fields replaced by the non-null parameter values.
  @override
  @pragma('vm:prefer-inline')
  $Res call({
    Object? user = null,
    Object? mode = freezed,
    Object? allowed = null,
  }) {
    return _then(StatusKitMessage_StatusChanged(
      user: null == user
          ? _self.user
          : user // ignore: cast_nullable_to_non_nullable
              as String,
      mode: freezed == mode
          ? _self.mode
          : mode // ignore: cast_nullable_to_non_nullable
              as String?,
      allowed: null == allowed
          ? _self.allowed
          : allowed // ignore: cast_nullable_to_non_nullable
              as bool,
    ));
  }
}

/// @nodoc
mixin _$SyncStatus {
  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is SyncStatus);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'SyncStatus()';
  }
}

/// @nodoc
class $SyncStatusCopyWith<$Res> {
  $SyncStatusCopyWith(SyncStatus _, $Res Function(SyncStatus) __);
}

/// Adds pattern-matching-related methods to [SyncStatus].
extension SyncStatusPatterns on SyncStatus {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(SyncStatus_Synced value)? synced,
    TResult Function(SyncStatus_Downloading value)? downloading,
    TResult Function(SyncStatus_Uploading value)? uploading,
    TResult Function(SyncStatus_Syncing value)? syncing,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case SyncStatus_Synced() when synced != null:
        return synced(_that);
      case SyncStatus_Downloading() when downloading != null:
        return downloading(_that);
      case SyncStatus_Uploading() when uploading != null:
        return uploading(_that);
      case SyncStatus_Syncing() when syncing != null:
        return syncing(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(SyncStatus_Synced value) synced,
    required TResult Function(SyncStatus_Downloading value) downloading,
    required TResult Function(SyncStatus_Uploading value) uploading,
    required TResult Function(SyncStatus_Syncing value) syncing,
  }) {
    final _that = this;
    switch (_that) {
      case SyncStatus_Synced():
        return synced(_that);
      case SyncStatus_Downloading():
        return downloading(_that);
      case SyncStatus_Uploading():
        return uploading(_that);
      case SyncStatus_Syncing():
        return syncing(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(SyncStatus_Synced value)? synced,
    TResult? Function(SyncStatus_Downloading value)? downloading,
    TResult? Function(SyncStatus_Uploading value)? uploading,
    TResult? Function(SyncStatus_Syncing value)? syncing,
  }) {
    final _that = this;
    switch (_that) {
      case SyncStatus_Synced() when synced != null:
        return synced(_that);
      case SyncStatus_Downloading() when downloading != null:
        return downloading(_that);
      case SyncStatus_Uploading() when uploading != null:
        return uploading(_that);
      case SyncStatus_Syncing() when syncing != null:
        return syncing(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function()? synced,
    TResult Function(BigInt progress, BigInt total)? downloading,
    TResult Function(BigInt progress, BigInt total)? uploading,
    TResult Function()? syncing,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case SyncStatus_Synced() when synced != null:
        return synced();
      case SyncStatus_Downloading() when downloading != null:
        return downloading(_that.progress, _that.total);
      case SyncStatus_Uploading() when uploading != null:
        return uploading(_that.progress, _that.total);
      case SyncStatus_Syncing() when syncing != null:
        return syncing();
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function() synced,
    required TResult Function(BigInt progress, BigInt total) downloading,
    required TResult Function(BigInt progress, BigInt total) uploading,
    required TResult Function() syncing,
  }) {
    final _that = this;
    switch (_that) {
      case SyncStatus_Synced():
        return synced();
      case SyncStatus_Downloading():
        return downloading(_that.progress, _that.total);
      case SyncStatus_Uploading():
        return uploading(_that.progress, _that.total);
      case SyncStatus_Syncing():
        return syncing();
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function()? synced,
    TResult? Function(BigInt progress, BigInt total)? downloading,
    TResult? Function(BigInt progress, BigInt total)? uploading,
    TResult? Function()? syncing,
  }) {
    final _that = this;
    switch (_that) {
      case SyncStatus_Synced() when synced != null:
        return synced();
      case SyncStatus_Downloading() when downloading != null:
        return downloading(_that.progress, _that.total);
      case SyncStatus_Uploading() when uploading != null:
        return uploading(_that.progress, _that.total);
      case SyncStatus_Syncing() when syncing != null:
        return syncing();
      case _:
        return null;
    }
  }
}

/// @nodoc

class SyncStatus_Synced extends SyncStatus {
  const SyncStatus_Synced() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is SyncStatus_Synced);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'SyncStatus.synced()';
  }
}

/// @nodoc

class SyncStatus_Downloading extends SyncStatus {
  const SyncStatus_Downloading({required this.progress, required this.total})
      : super._();

  final BigInt progress;
  final BigInt total;

  /// Create a copy of SyncStatus
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $SyncStatus_DownloadingCopyWith<SyncStatus_Downloading> get copyWith =>
      _$SyncStatus_DownloadingCopyWithImpl<SyncStatus_Downloading>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is SyncStatus_Downloading &&
            (identical(other.progress, progress) ||
                other.progress == progress) &&
            (identical(other.total, total) || other.total == total));
  }

  @override
  int get hashCode => Object.hash(runtimeType, progress, total);

  @override
  String toString() {
    return 'SyncStatus.downloading(progress: $progress, total: $total)';
  }
}

/// @nodoc
abstract mixin class $SyncStatus_DownloadingCopyWith<$Res>
    implements $SyncStatusCopyWith<$Res> {
  factory $SyncStatus_DownloadingCopyWith(SyncStatus_Downloading value,
          $Res Function(SyncStatus_Downloading) _then) =
      _$SyncStatus_DownloadingCopyWithImpl;
  @useResult
  $Res call({BigInt progress, BigInt total});
}

/// @nodoc
class _$SyncStatus_DownloadingCopyWithImpl<$Res>
    implements $SyncStatus_DownloadingCopyWith<$Res> {
  _$SyncStatus_DownloadingCopyWithImpl(this._self, this._then);

  final SyncStatus_Downloading _self;
  final $Res Function(SyncStatus_Downloading) _then;

  /// Create a copy of SyncStatus
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? progress = null,
    Object? total = null,
  }) {
    return _then(SyncStatus_Downloading(
      progress: null == progress
          ? _self.progress
          : progress // ignore: cast_nullable_to_non_nullable
              as BigInt,
      total: null == total
          ? _self.total
          : total // ignore: cast_nullable_to_non_nullable
              as BigInt,
    ));
  }
}

/// @nodoc

class SyncStatus_Uploading extends SyncStatus {
  const SyncStatus_Uploading({required this.progress, required this.total})
      : super._();

  final BigInt progress;
  final BigInt total;

  /// Create a copy of SyncStatus
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $SyncStatus_UploadingCopyWith<SyncStatus_Uploading> get copyWith =>
      _$SyncStatus_UploadingCopyWithImpl<SyncStatus_Uploading>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is SyncStatus_Uploading &&
            (identical(other.progress, progress) ||
                other.progress == progress) &&
            (identical(other.total, total) || other.total == total));
  }

  @override
  int get hashCode => Object.hash(runtimeType, progress, total);

  @override
  String toString() {
    return 'SyncStatus.uploading(progress: $progress, total: $total)';
  }
}

/// @nodoc
abstract mixin class $SyncStatus_UploadingCopyWith<$Res>
    implements $SyncStatusCopyWith<$Res> {
  factory $SyncStatus_UploadingCopyWith(SyncStatus_Uploading value,
          $Res Function(SyncStatus_Uploading) _then) =
      _$SyncStatus_UploadingCopyWithImpl;
  @useResult
  $Res call({BigInt progress, BigInt total});
}

/// @nodoc
class _$SyncStatus_UploadingCopyWithImpl<$Res>
    implements $SyncStatus_UploadingCopyWith<$Res> {
  _$SyncStatus_UploadingCopyWithImpl(this._self, this._then);

  final SyncStatus_Uploading _self;
  final $Res Function(SyncStatus_Uploading) _then;

  /// Create a copy of SyncStatus
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? progress = null,
    Object? total = null,
  }) {
    return _then(SyncStatus_Uploading(
      progress: null == progress
          ? _self.progress
          : progress // ignore: cast_nullable_to_non_nullable
              as BigInt,
      total: null == total
          ? _self.total
          : total // ignore: cast_nullable_to_non_nullable
              as BigInt,
    ));
  }
}

/// @nodoc

class SyncStatus_Syncing extends SyncStatus {
  const SyncStatus_Syncing() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is SyncStatus_Syncing);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'SyncStatus.syncing()';
  }
}

/// @nodoc
mixin _$TextFormat {
  Object get field0;

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is TextFormat &&
            const DeepCollectionEquality().equals(other.field0, field0));
  }

  @override
  int get hashCode =>
      Object.hash(runtimeType, const DeepCollectionEquality().hash(field0));

  @override
  String toString() {
    return 'TextFormat(field0: $field0)';
  }
}

/// @nodoc
class $TextFormatCopyWith<$Res> {
  $TextFormatCopyWith(TextFormat _, $Res Function(TextFormat) __);
}

/// Adds pattern-matching-related methods to [TextFormat].
extension TextFormatPatterns on TextFormat {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(TextFormat_Flags value)? flags,
    TResult Function(TextFormat_Effect value)? effect,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case TextFormat_Flags() when flags != null:
        return flags(_that);
      case TextFormat_Effect() when effect != null:
        return effect(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(TextFormat_Flags value) flags,
    required TResult Function(TextFormat_Effect value) effect,
  }) {
    final _that = this;
    switch (_that) {
      case TextFormat_Flags():
        return flags(_that);
      case TextFormat_Effect():
        return effect(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(TextFormat_Flags value)? flags,
    TResult? Function(TextFormat_Effect value)? effect,
  }) {
    final _that = this;
    switch (_that) {
      case TextFormat_Flags() when flags != null:
        return flags(_that);
      case TextFormat_Effect() when effect != null:
        return effect(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function(TextFlags field0)? flags,
    TResult Function(TextEffect field0)? effect,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case TextFormat_Flags() when flags != null:
        return flags(_that.field0);
      case TextFormat_Effect() when effect != null:
        return effect(_that.field0);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function(TextFlags field0) flags,
    required TResult Function(TextEffect field0) effect,
  }) {
    final _that = this;
    switch (_that) {
      case TextFormat_Flags():
        return flags(_that.field0);
      case TextFormat_Effect():
        return effect(_that.field0);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function(TextFlags field0)? flags,
    TResult? Function(TextEffect field0)? effect,
  }) {
    final _that = this;
    switch (_that) {
      case TextFormat_Flags() when flags != null:
        return flags(_that.field0);
      case TextFormat_Effect() when effect != null:
        return effect(_that.field0);
      case _:
        return null;
    }
  }
}

/// @nodoc

class TextFormat_Flags extends TextFormat {
  const TextFormat_Flags(this.field0) : super._();

  @override
  final TextFlags field0;

  /// Create a copy of TextFormat
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $TextFormat_FlagsCopyWith<TextFormat_Flags> get copyWith =>
      _$TextFormat_FlagsCopyWithImpl<TextFormat_Flags>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is TextFormat_Flags &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'TextFormat.flags(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $TextFormat_FlagsCopyWith<$Res>
    implements $TextFormatCopyWith<$Res> {
  factory $TextFormat_FlagsCopyWith(
          TextFormat_Flags value, $Res Function(TextFormat_Flags) _then) =
      _$TextFormat_FlagsCopyWithImpl;
  @useResult
  $Res call({TextFlags field0});
}

/// @nodoc
class _$TextFormat_FlagsCopyWithImpl<$Res>
    implements $TextFormat_FlagsCopyWith<$Res> {
  _$TextFormat_FlagsCopyWithImpl(this._self, this._then);

  final TextFormat_Flags _self;
  final $Res Function(TextFormat_Flags) _then;

  /// Create a copy of TextFormat
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(TextFormat_Flags(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as TextFlags,
    ));
  }
}

/// @nodoc

class TextFormat_Effect extends TextFormat {
  const TextFormat_Effect(this.field0) : super._();

  @override
  final TextEffect field0;

  /// Create a copy of TextFormat
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $TextFormat_EffectCopyWith<TextFormat_Effect> get copyWith =>
      _$TextFormat_EffectCopyWithImpl<TextFormat_Effect>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is TextFormat_Effect &&
            (identical(other.field0, field0) || other.field0 == field0));
  }

  @override
  int get hashCode => Object.hash(runtimeType, field0);

  @override
  String toString() {
    return 'TextFormat.effect(field0: $field0)';
  }
}

/// @nodoc
abstract mixin class $TextFormat_EffectCopyWith<$Res>
    implements $TextFormatCopyWith<$Res> {
  factory $TextFormat_EffectCopyWith(
          TextFormat_Effect value, $Res Function(TextFormat_Effect) _then) =
      _$TextFormat_EffectCopyWithImpl;
  @useResult
  $Res call({TextEffect field0});
}

/// @nodoc
class _$TextFormat_EffectCopyWithImpl<$Res>
    implements $TextFormat_EffectCopyWith<$Res> {
  _$TextFormat_EffectCopyWithImpl(this._self, this._then);

  final TextFormat_Effect _self;
  final $Res Function(TextFormat_Effect) _then;

  /// Create a copy of TextFormat
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? field0 = null,
  }) {
    return _then(TextFormat_Effect(
      null == field0
          ? _self.field0
          : field0 // ignore: cast_nullable_to_non_nullable
              as TextEffect,
    ));
  }
}

/// @nodoc
mixin _$UIColor {
  int get colorComponents;
  double get alpha;
  int get colorSpace;
  String get class_;

  /// Create a copy of UIColor
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $UIColorCopyWith<UIColor> get copyWith =>
      _$UIColorCopyWithImpl<UIColor>(this as UIColor, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is UIColor &&
            (identical(other.colorComponents, colorComponents) ||
                other.colorComponents == colorComponents) &&
            (identical(other.alpha, alpha) || other.alpha == alpha) &&
            (identical(other.colorSpace, colorSpace) ||
                other.colorSpace == colorSpace) &&
            (identical(other.class_, class_) || other.class_ == class_));
  }

  @override
  int get hashCode =>
      Object.hash(runtimeType, colorComponents, alpha, colorSpace, class_);

  @override
  String toString() {
    return 'UIColor(colorComponents: $colorComponents, alpha: $alpha, colorSpace: $colorSpace, class_: $class_)';
  }
}

/// @nodoc
abstract mixin class $UIColorCopyWith<$Res> {
  factory $UIColorCopyWith(UIColor value, $Res Function(UIColor) _then) =
      _$UIColorCopyWithImpl;
  @useResult
  $Res call({int colorComponents, double alpha, int colorSpace, String class_});
}

/// @nodoc
class _$UIColorCopyWithImpl<$Res> implements $UIColorCopyWith<$Res> {
  _$UIColorCopyWithImpl(this._self, this._then);

  final UIColor _self;
  final $Res Function(UIColor) _then;

  /// Create a copy of UIColor
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? colorComponents = null,
    Object? alpha = null,
    Object? colorSpace = null,
    Object? class_ = null,
  }) {
    return _then(_self.copyWith(
      colorComponents: null == colorComponents
          ? _self.colorComponents
          : colorComponents // ignore: cast_nullable_to_non_nullable
              as int,
      alpha: null == alpha
          ? _self.alpha
          : alpha // ignore: cast_nullable_to_non_nullable
              as double,
      colorSpace: null == colorSpace
          ? _self.colorSpace
          : colorSpace // ignore: cast_nullable_to_non_nullable
              as int,
      class_: null == class_
          ? _self.class_
          : class_ // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

/// Adds pattern-matching-related methods to [UIColor].
extension UIColorPatterns on UIColor {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(UIColor_RGBAColorSpace value)? rgbaColorSpace,
    TResult Function(UIColor_GrayscaleAlphaColorSpace value)?
        grayscaleAlphaColorSpace,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case UIColor_RGBAColorSpace() when rgbaColorSpace != null:
        return rgbaColorSpace(_that);
      case UIColor_GrayscaleAlphaColorSpace()
          when grayscaleAlphaColorSpace != null:
        return grayscaleAlphaColorSpace(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(UIColor_RGBAColorSpace value) rgbaColorSpace,
    required TResult Function(UIColor_GrayscaleAlphaColorSpace value)
        grayscaleAlphaColorSpace,
  }) {
    final _that = this;
    switch (_that) {
      case UIColor_RGBAColorSpace():
        return rgbaColorSpace(_that);
      case UIColor_GrayscaleAlphaColorSpace():
        return grayscaleAlphaColorSpace(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(UIColor_RGBAColorSpace value)? rgbaColorSpace,
    TResult? Function(UIColor_GrayscaleAlphaColorSpace value)?
        grayscaleAlphaColorSpace,
  }) {
    final _that = this;
    switch (_that) {
      case UIColor_RGBAColorSpace() when rgbaColorSpace != null:
        return rgbaColorSpace(_that);
      case UIColor_GrayscaleAlphaColorSpace()
          when grayscaleAlphaColorSpace != null:
        return grayscaleAlphaColorSpace(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function(
            int colorComponents,
            double green,
            double blue,
            double red,
            double? greenDbl,
            double? blueDbl,
            double? redDbl,
            double? alphaDbl,
            double alpha,
            Uint8List rgb,
            int colorSpace,
            String class_)?
        rgbaColorSpace,
    TResult Function(int colorComponents, double white, double alpha,
            Uint8List bin, int colorSpace, String class_)?
        grayscaleAlphaColorSpace,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case UIColor_RGBAColorSpace() when rgbaColorSpace != null:
        return rgbaColorSpace(
            _that.colorComponents,
            _that.green,
            _that.blue,
            _that.red,
            _that.greenDbl,
            _that.blueDbl,
            _that.redDbl,
            _that.alphaDbl,
            _that.alpha,
            _that.rgb,
            _that.colorSpace,
            _that.class_);
      case UIColor_GrayscaleAlphaColorSpace()
          when grayscaleAlphaColorSpace != null:
        return grayscaleAlphaColorSpace(_that.colorComponents, _that.white,
            _that.alpha, _that.bin, _that.colorSpace, _that.class_);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function(
            int colorComponents,
            double green,
            double blue,
            double red,
            double? greenDbl,
            double? blueDbl,
            double? redDbl,
            double? alphaDbl,
            double alpha,
            Uint8List rgb,
            int colorSpace,
            String class_)
        rgbaColorSpace,
    required TResult Function(int colorComponents, double white, double alpha,
            Uint8List bin, int colorSpace, String class_)
        grayscaleAlphaColorSpace,
  }) {
    final _that = this;
    switch (_that) {
      case UIColor_RGBAColorSpace():
        return rgbaColorSpace(
            _that.colorComponents,
            _that.green,
            _that.blue,
            _that.red,
            _that.greenDbl,
            _that.blueDbl,
            _that.redDbl,
            _that.alphaDbl,
            _that.alpha,
            _that.rgb,
            _that.colorSpace,
            _that.class_);
      case UIColor_GrayscaleAlphaColorSpace():
        return grayscaleAlphaColorSpace(_that.colorComponents, _that.white,
            _that.alpha, _that.bin, _that.colorSpace, _that.class_);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function(
            int colorComponents,
            double green,
            double blue,
            double red,
            double? greenDbl,
            double? blueDbl,
            double? redDbl,
            double? alphaDbl,
            double alpha,
            Uint8List rgb,
            int colorSpace,
            String class_)?
        rgbaColorSpace,
    TResult? Function(int colorComponents, double white, double alpha,
            Uint8List bin, int colorSpace, String class_)?
        grayscaleAlphaColorSpace,
  }) {
    final _that = this;
    switch (_that) {
      case UIColor_RGBAColorSpace() when rgbaColorSpace != null:
        return rgbaColorSpace(
            _that.colorComponents,
            _that.green,
            _that.blue,
            _that.red,
            _that.greenDbl,
            _that.blueDbl,
            _that.redDbl,
            _that.alphaDbl,
            _that.alpha,
            _that.rgb,
            _that.colorSpace,
            _that.class_);
      case UIColor_GrayscaleAlphaColorSpace()
          when grayscaleAlphaColorSpace != null:
        return grayscaleAlphaColorSpace(_that.colorComponents, _that.white,
            _that.alpha, _that.bin, _that.colorSpace, _that.class_);
      case _:
        return null;
    }
  }
}

/// @nodoc

class UIColor_RGBAColorSpace extends UIColor {
  const UIColor_RGBAColorSpace(
      {required this.colorComponents,
      required this.green,
      required this.blue,
      required this.red,
      this.greenDbl,
      this.blueDbl,
      this.redDbl,
      this.alphaDbl,
      required this.alpha,
      required this.rgb,
      required this.colorSpace,
      required this.class_})
      : super._();

  @override
  final int colorComponents;
  final double green;
  final double blue;
  final double red;
  final double? greenDbl;
  final double? blueDbl;
  final double? redDbl;
  final double? alphaDbl;
  @override
  final double alpha;
  final Uint8List rgb;
  @override
  final int colorSpace;
  @override
  final String class_;

  /// Create a copy of UIColor
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $UIColor_RGBAColorSpaceCopyWith<UIColor_RGBAColorSpace> get copyWith =>
      _$UIColor_RGBAColorSpaceCopyWithImpl<UIColor_RGBAColorSpace>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is UIColor_RGBAColorSpace &&
            (identical(other.colorComponents, colorComponents) ||
                other.colorComponents == colorComponents) &&
            (identical(other.green, green) || other.green == green) &&
            (identical(other.blue, blue) || other.blue == blue) &&
            (identical(other.red, red) || other.red == red) &&
            (identical(other.greenDbl, greenDbl) ||
                other.greenDbl == greenDbl) &&
            (identical(other.blueDbl, blueDbl) || other.blueDbl == blueDbl) &&
            (identical(other.redDbl, redDbl) || other.redDbl == redDbl) &&
            (identical(other.alphaDbl, alphaDbl) ||
                other.alphaDbl == alphaDbl) &&
            (identical(other.alpha, alpha) || other.alpha == alpha) &&
            const DeepCollectionEquality().equals(other.rgb, rgb) &&
            (identical(other.colorSpace, colorSpace) ||
                other.colorSpace == colorSpace) &&
            (identical(other.class_, class_) || other.class_ == class_));
  }

  @override
  int get hashCode => Object.hash(
      runtimeType,
      colorComponents,
      green,
      blue,
      red,
      greenDbl,
      blueDbl,
      redDbl,
      alphaDbl,
      alpha,
      const DeepCollectionEquality().hash(rgb),
      colorSpace,
      class_);

  @override
  String toString() {
    return 'UIColor.rgbaColorSpace(colorComponents: $colorComponents, green: $green, blue: $blue, red: $red, greenDbl: $greenDbl, blueDbl: $blueDbl, redDbl: $redDbl, alphaDbl: $alphaDbl, alpha: $alpha, rgb: $rgb, colorSpace: $colorSpace, class_: $class_)';
  }
}

/// @nodoc
abstract mixin class $UIColor_RGBAColorSpaceCopyWith<$Res>
    implements $UIColorCopyWith<$Res> {
  factory $UIColor_RGBAColorSpaceCopyWith(UIColor_RGBAColorSpace value,
          $Res Function(UIColor_RGBAColorSpace) _then) =
      _$UIColor_RGBAColorSpaceCopyWithImpl;
  @override
  @useResult
  $Res call(
      {int colorComponents,
      double green,
      double blue,
      double red,
      double? greenDbl,
      double? blueDbl,
      double? redDbl,
      double? alphaDbl,
      double alpha,
      Uint8List rgb,
      int colorSpace,
      String class_});
}

/// @nodoc
class _$UIColor_RGBAColorSpaceCopyWithImpl<$Res>
    implements $UIColor_RGBAColorSpaceCopyWith<$Res> {
  _$UIColor_RGBAColorSpaceCopyWithImpl(this._self, this._then);

  final UIColor_RGBAColorSpace _self;
  final $Res Function(UIColor_RGBAColorSpace) _then;

  /// Create a copy of UIColor
  /// with the given fields replaced by the non-null parameter values.
  @override
  @pragma('vm:prefer-inline')
  $Res call({
    Object? colorComponents = null,
    Object? green = null,
    Object? blue = null,
    Object? red = null,
    Object? greenDbl = freezed,
    Object? blueDbl = freezed,
    Object? redDbl = freezed,
    Object? alphaDbl = freezed,
    Object? alpha = null,
    Object? rgb = null,
    Object? colorSpace = null,
    Object? class_ = null,
  }) {
    return _then(UIColor_RGBAColorSpace(
      colorComponents: null == colorComponents
          ? _self.colorComponents
          : colorComponents // ignore: cast_nullable_to_non_nullable
              as int,
      green: null == green
          ? _self.green
          : green // ignore: cast_nullable_to_non_nullable
              as double,
      blue: null == blue
          ? _self.blue
          : blue // ignore: cast_nullable_to_non_nullable
              as double,
      red: null == red
          ? _self.red
          : red // ignore: cast_nullable_to_non_nullable
              as double,
      greenDbl: freezed == greenDbl
          ? _self.greenDbl
          : greenDbl // ignore: cast_nullable_to_non_nullable
              as double?,
      blueDbl: freezed == blueDbl
          ? _self.blueDbl
          : blueDbl // ignore: cast_nullable_to_non_nullable
              as double?,
      redDbl: freezed == redDbl
          ? _self.redDbl
          : redDbl // ignore: cast_nullable_to_non_nullable
              as double?,
      alphaDbl: freezed == alphaDbl
          ? _self.alphaDbl
          : alphaDbl // ignore: cast_nullable_to_non_nullable
              as double?,
      alpha: null == alpha
          ? _self.alpha
          : alpha // ignore: cast_nullable_to_non_nullable
              as double,
      rgb: null == rgb
          ? _self.rgb
          : rgb // ignore: cast_nullable_to_non_nullable
              as Uint8List,
      colorSpace: null == colorSpace
          ? _self.colorSpace
          : colorSpace // ignore: cast_nullable_to_non_nullable
              as int,
      class_: null == class_
          ? _self.class_
          : class_ // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

/// @nodoc

class UIColor_GrayscaleAlphaColorSpace extends UIColor {
  const UIColor_GrayscaleAlphaColorSpace(
      {required this.colorComponents,
      required this.white,
      required this.alpha,
      required this.bin,
      required this.colorSpace,
      required this.class_})
      : super._();

  @override
  final int colorComponents;
  final double white;
  @override
  final double alpha;
  final Uint8List bin;
  @override
  final int colorSpace;
  @override
  final String class_;

  /// Create a copy of UIColor
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $UIColor_GrayscaleAlphaColorSpaceCopyWith<UIColor_GrayscaleAlphaColorSpace>
      get copyWith => _$UIColor_GrayscaleAlphaColorSpaceCopyWithImpl<
          UIColor_GrayscaleAlphaColorSpace>(this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is UIColor_GrayscaleAlphaColorSpace &&
            (identical(other.colorComponents, colorComponents) ||
                other.colorComponents == colorComponents) &&
            (identical(other.white, white) || other.white == white) &&
            (identical(other.alpha, alpha) || other.alpha == alpha) &&
            const DeepCollectionEquality().equals(other.bin, bin) &&
            (identical(other.colorSpace, colorSpace) ||
                other.colorSpace == colorSpace) &&
            (identical(other.class_, class_) || other.class_ == class_));
  }

  @override
  int get hashCode => Object.hash(runtimeType, colorComponents, white, alpha,
      const DeepCollectionEquality().hash(bin), colorSpace, class_);

  @override
  String toString() {
    return 'UIColor.grayscaleAlphaColorSpace(colorComponents: $colorComponents, white: $white, alpha: $alpha, bin: $bin, colorSpace: $colorSpace, class_: $class_)';
  }
}

/// @nodoc
abstract mixin class $UIColor_GrayscaleAlphaColorSpaceCopyWith<$Res>
    implements $UIColorCopyWith<$Res> {
  factory $UIColor_GrayscaleAlphaColorSpaceCopyWith(
          UIColor_GrayscaleAlphaColorSpace value,
          $Res Function(UIColor_GrayscaleAlphaColorSpace) _then) =
      _$UIColor_GrayscaleAlphaColorSpaceCopyWithImpl;
  @override
  @useResult
  $Res call(
      {int colorComponents,
      double white,
      double alpha,
      Uint8List bin,
      int colorSpace,
      String class_});
}

/// @nodoc
class _$UIColor_GrayscaleAlphaColorSpaceCopyWithImpl<$Res>
    implements $UIColor_GrayscaleAlphaColorSpaceCopyWith<$Res> {
  _$UIColor_GrayscaleAlphaColorSpaceCopyWithImpl(this._self, this._then);

  final UIColor_GrayscaleAlphaColorSpace _self;
  final $Res Function(UIColor_GrayscaleAlphaColorSpace) _then;

  /// Create a copy of UIColor
  /// with the given fields replaced by the non-null parameter values.
  @override
  @pragma('vm:prefer-inline')
  $Res call({
    Object? colorComponents = null,
    Object? white = null,
    Object? alpha = null,
    Object? bin = null,
    Object? colorSpace = null,
    Object? class_ = null,
  }) {
    return _then(UIColor_GrayscaleAlphaColorSpace(
      colorComponents: null == colorComponents
          ? _self.colorComponents
          : colorComponents // ignore: cast_nullable_to_non_nullable
              as int,
      white: null == white
          ? _self.white
          : white // ignore: cast_nullable_to_non_nullable
              as double,
      alpha: null == alpha
          ? _self.alpha
          : alpha // ignore: cast_nullable_to_non_nullable
              as double,
      bin: null == bin
          ? _self.bin
          : bin // ignore: cast_nullable_to_non_nullable
              as Uint8List,
      colorSpace: null == colorSpace
          ? _self.colorSpace
          : colorSpace // ignore: cast_nullable_to_non_nullable
              as int,
      class_: null == class_
          ? _self.class_
          : class_ // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

/// @nodoc
mixin _$UpdateAccountFinish {
  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType && other is UpdateAccountFinish);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'UpdateAccountFinish()';
  }
}

/// @nodoc
class $UpdateAccountFinishCopyWith<$Res> {
  $UpdateAccountFinishCopyWith(
      UpdateAccountFinish _, $Res Function(UpdateAccountFinish) __);
}

/// Adds pattern-matching-related methods to [UpdateAccountFinish].
extension UpdateAccountFinishPatterns on UpdateAccountFinish {
  /// A variant of `map` that fallback to returning `orElse`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeMap<TResult extends Object?>({
    TResult Function(UpdateAccountFinish_MacOS value)? macOs,
    TResult Function(UpdateAccountFinish_IOS value)? ios,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case UpdateAccountFinish_MacOS() when macOs != null:
        return macOs(_that);
      case UpdateAccountFinish_IOS() when ios != null:
        return ios(_that);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// Callbacks receives the raw object, upcasted.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case final Subclass2 value:
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult map<TResult extends Object?>({
    required TResult Function(UpdateAccountFinish_MacOS value) macOs,
    required TResult Function(UpdateAccountFinish_IOS value) ios,
  }) {
    final _that = this;
    switch (_that) {
      case UpdateAccountFinish_MacOS():
        return macOs(_that);
      case UpdateAccountFinish_IOS():
        return ios(_that);
    }
  }

  /// A variant of `map` that fallback to returning `null`.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case final Subclass value:
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? mapOrNull<TResult extends Object?>({
    TResult? Function(UpdateAccountFinish_MacOS value)? macOs,
    TResult? Function(UpdateAccountFinish_IOS value)? ios,
  }) {
    final _that = this;
    switch (_that) {
      case UpdateAccountFinish_MacOS() when macOs != null:
        return macOs(_that);
      case UpdateAccountFinish_IOS() when ios != null:
        return ios(_that);
      case _:
        return null;
    }
  }

  /// A variant of `when` that fallback to an `orElse` callback.
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return orElse();
  /// }
  /// ```

  @optionalTypeArgs
  TResult maybeWhen<TResult extends Object?>({
    TResult Function()? macOs,
    TResult Function(String url)? ios,
    required TResult orElse(),
  }) {
    final _that = this;
    switch (_that) {
      case UpdateAccountFinish_MacOS() when macOs != null:
        return macOs();
      case UpdateAccountFinish_IOS() when ios != null:
        return ios(_that.url);
      case _:
        return orElse();
    }
  }

  /// A `switch`-like method, using callbacks.
  ///
  /// As opposed to `map`, this offers destructuring.
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case Subclass2(:final field2):
  ///     return ...;
  /// }
  /// ```

  @optionalTypeArgs
  TResult when<TResult extends Object?>({
    required TResult Function() macOs,
    required TResult Function(String url) ios,
  }) {
    final _that = this;
    switch (_that) {
      case UpdateAccountFinish_MacOS():
        return macOs();
      case UpdateAccountFinish_IOS():
        return ios(_that.url);
    }
  }

  /// A variant of `when` that fallback to returning `null`
  ///
  /// It is equivalent to doing:
  /// ```dart
  /// switch (sealedClass) {
  ///   case Subclass(:final field):
  ///     return ...;
  ///   case _:
  ///     return null;
  /// }
  /// ```

  @optionalTypeArgs
  TResult? whenOrNull<TResult extends Object?>({
    TResult? Function()? macOs,
    TResult? Function(String url)? ios,
  }) {
    final _that = this;
    switch (_that) {
      case UpdateAccountFinish_MacOS() when macOs != null:
        return macOs();
      case UpdateAccountFinish_IOS() when ios != null:
        return ios(_that.url);
      case _:
        return null;
    }
  }
}

/// @nodoc

class UpdateAccountFinish_MacOS extends UpdateAccountFinish {
  const UpdateAccountFinish_MacOS() : super._();

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is UpdateAccountFinish_MacOS);
  }

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() {
    return 'UpdateAccountFinish.macOs()';
  }
}

/// @nodoc

class UpdateAccountFinish_IOS extends UpdateAccountFinish {
  const UpdateAccountFinish_IOS({required this.url}) : super._();

  final String url;

  /// Create a copy of UpdateAccountFinish
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @pragma('vm:prefer-inline')
  $UpdateAccountFinish_IOSCopyWith<UpdateAccountFinish_IOS> get copyWith =>
      _$UpdateAccountFinish_IOSCopyWithImpl<UpdateAccountFinish_IOS>(
          this, _$identity);

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is UpdateAccountFinish_IOS &&
            (identical(other.url, url) || other.url == url));
  }

  @override
  int get hashCode => Object.hash(runtimeType, url);

  @override
  String toString() {
    return 'UpdateAccountFinish.ios(url: $url)';
  }
}

/// @nodoc
abstract mixin class $UpdateAccountFinish_IOSCopyWith<$Res>
    implements $UpdateAccountFinishCopyWith<$Res> {
  factory $UpdateAccountFinish_IOSCopyWith(UpdateAccountFinish_IOS value,
          $Res Function(UpdateAccountFinish_IOS) _then) =
      _$UpdateAccountFinish_IOSCopyWithImpl;
  @useResult
  $Res call({String url});
}

/// @nodoc
class _$UpdateAccountFinish_IOSCopyWithImpl<$Res>
    implements $UpdateAccountFinish_IOSCopyWith<$Res> {
  _$UpdateAccountFinish_IOSCopyWithImpl(this._self, this._then);

  final UpdateAccountFinish_IOS _self;
  final $Res Function(UpdateAccountFinish_IOS) _then;

  /// Create a copy of UpdateAccountFinish
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  $Res call({
    Object? url = null,
  }) {
    return _then(UpdateAccountFinish_IOS(
      url: null == url
          ? _self.url
          : url // ignore: cast_nullable_to_non_nullable
              as String,
    ));
  }
}

// dart format on
