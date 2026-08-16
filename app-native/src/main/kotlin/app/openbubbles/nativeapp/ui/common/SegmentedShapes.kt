package app.openbubbles.nativeapp.ui.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Vertical gap between segmented rows (matches ListItemDefaults.SegmentedGap). */
val SegmentedRowGap = 2.dp

/**
 * Connected-group row shapes: rounded outer corners, near-square inner
 * corners, so one logical group reads as a single object. Values mirror the
 * expressive tokens (largeIncreased outer / extraSmall inner); hand-built
 * because these rows are custom Surfaces rather than ListItems.
 */
fun segmentedRowShape(index: Int, count: Int): RoundedCornerShape {
    val outer = 20.dp
    val inner = 4.dp
    return when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(
            topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner,
        )
        index == count - 1 -> RoundedCornerShape(
            topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer,
        )
        else -> RoundedCornerShape(inner)
    }
}
