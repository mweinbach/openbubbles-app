package app.openbubbles.nativeapp.ui.adaptive

import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

/**
 * Messaging list-detail directive.
 *
 * Default Material keeps medium width (600–839dp) on a single pane. An
 * unfolded foldable inner display and a portrait tablet live in that
 * bucket, and a conversation list is the documented exception — two panes
 * from medium, three from large (1200dp) so chat info can sit beside the
 * transcript. Hinge policy stays the library default ([AvoidSeparating]):
 * panes split around a separating vertical hinge and stay seamless when
 * the fold is flat.
 *
 * The 24dp list|detail gutter is zeroed so the panes read as one
 * connected surface (Nav3 recipe, b/418201867). Tonal layering still
 * separates list (`surfaceContainerLow`) from conversation (`surface`).
 */
fun messagingListDetailDirective(info: WindowAdaptiveInfo): PaneScaffoldDirective =
    calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(info)
        .copy(horizontalPartitionSpacerSize = 0.dp)

/**
 * Horizontal pane count this client wants for a messaging window.
 *
 * Mirrors [calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth]: two
 * panes from medium, three from large / extra-large.
 */
internal fun messagingHorizontalPartitions(minWidthDp: Int): Int = when {
    minWidthDp >= WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND -> 3
    minWidthDp >= WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND -> 2
    else -> 1
}

/**
 * Vertical pane count for tabletop / tall single-pane windows.
 *
 * Matches `calculatePaneScaffoldDirective`: tabletop, or a single
 * horizontal partition with expanded height, becomes two vertical
 * partitions so content can sit above the fold and controls below.
 */
internal fun messagingVerticalPartitions(
    isTabletop: Boolean,
    horizontalPartitions: Int,
    minHeightDp: Int,
): Int = if (
    isTabletop ||
        (horizontalPartitions == 1 &&
            minHeightDp >= WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND)
) {
    2
} else {
    1
}

/** Category rail + preference column on a foldable / tablet settings page. */
internal data class SettingsTwoPaneSplit(
    val listWidthDp: Float,
    val gutterDp: Float,
)

/**
 * Book-posture settings split: the category rail ends at the vertical
 * hinge and the preference column resumes after it. Without a separating
 * hinge the Messages-style 300dp rail + 16dp gutter is used.
 *
 * [hingeLeftDp] / [hingeRightDp] are window-coordinate dp. Settings is a
 * full-window overlay, so those numbers are layout-local.
 */
internal fun settingsTwoPaneSplit(
    hingeLeftDp: Float?,
    hingeRightDp: Float?,
    defaultListWidthDp: Float = 300f,
    defaultGutterDp: Float = 16f,
    minListWidthDp: Float = 200f,
): SettingsTwoPaneSplit {
    if (
        hingeLeftDp == null ||
        hingeRightDp == null ||
        hingeRightDp <= hingeLeftDp ||
        hingeLeftDp < minListWidthDp
    ) {
        return SettingsTwoPaneSplit(defaultListWidthDp, defaultGutterDp)
    }
    return SettingsTwoPaneSplit(
        listWidthDp = hingeLeftDp,
        gutterDp = (hingeRightDp - hingeLeftDp).coerceAtLeast(defaultGutterDp),
    )
}

/**
 * Incoming-call splash split for tabletop: identity/poster above the
 * horizontal hinge, accept/decline below it. Null means "use the phone
 * layout" — including the first WindowLayoutInfo frame, which has no
 * folding features yet.
 */
internal data class FaceTimeTabletopInsets(
    val contentHeightPx: Int,
    val controlsTopMarginPx: Int,
)

internal fun faceTimeTabletopInsets(
    windowHeightPx: Int,
    hingeTopPx: Int,
    hingeBottomPx: Int,
): FaceTimeTabletopInsets? {
    if (
        windowHeightPx <= 0 ||
        hingeTopPx <= 0 ||
        hingeBottomPx >= windowHeightPx ||
        hingeTopPx >= hingeBottomPx
    ) {
        return null
    }
    return FaceTimeTabletopInsets(
        contentHeightPx = hingeTopPx,
        controlsTopMarginPx = hingeBottomPx,
    )
}

/**
 * QR pairing scanner split for tabletop: viewfinder above the horizontal
 * hinge, close / torch / caption below it. Null means "use the phone
 * layout" — including the first WindowLayoutInfo frame, which has no
 * folding features yet.
 */
internal data class QrTabletopSplit(
    val viewfinderHeightPx: Int,
    val hingeHeightPx: Int,
)

internal fun qrTabletopSplit(
    windowHeightPx: Int,
    hingeTopPx: Int,
    hingeBottomPx: Int,
): QrTabletopSplit? {
    if (
        windowHeightPx <= 0 ||
        hingeTopPx <= 0 ||
        hingeBottomPx >= windowHeightPx ||
        hingeTopPx >= hingeBottomPx
    ) {
        return null
    }
    return QrTabletopSplit(
        viewfinderHeightPx = hingeTopPx,
        hingeHeightPx = hingeBottomPx - hingeTopPx,
    )
}

/** Horizontal half-opened hinge — tabletop / laptop posture. */
internal fun isTabletopFold(horizontalHinge: Boolean, halfOpened: Boolean): Boolean =
    horizontalHinge && halfOpened

/** Vertical half-opened hinge — book posture. */
internal fun isBookFold(verticalHinge: Boolean, halfOpened: Boolean): Boolean =
    verticalHinge && halfOpened
