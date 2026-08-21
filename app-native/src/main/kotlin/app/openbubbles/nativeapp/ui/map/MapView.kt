package app.openbubbles.nativeapp.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * One thing shown on the map.
 *
 * [content] draws the pin itself, so a device icon, a friend's avatar, and an
 * item's emoji can all be markers without the map knowing what any of them are.
 */
data class MapMarker(
    val id: String,
    val point: GeoPoint,
    /** Reported fix radius, drawn as a circle in real ground units. */
    val accuracyMeters: Double? = null,
    /** Spoken name for the pin; a pin is a real button, not a painted dot. */
    val label: String,
    val selected: Boolean = false,
    /** A fix old enough that the pin should read as approximate. */
    val stale: Boolean = false,
    /** Earlier fixes from this session, oldest first, drawn as a track. */
    val trail: List<GeoPoint> = emptyList(),
    val content: @Composable (selected: Boolean) -> Unit,
)

/** Marker touch target; also the anchor size the projection centres on. */
private val MarkerSize = 44.dp

/** How far off screen a marker may be before it stops being composed. */
private const val MarkerCullSlopPx = 120f

/**
 * The in-app slippy map: raster tiles, accuracy circles, session tracks, and
 * accessible markers, all driven by [MapViewport].
 *
 * Imagery is optional. With [tiles] null — or simply offline — the map paints a
 * tile graticule instead, so a located device still shows in the right place
 * relative to everything else rather than the screen going empty.
 */
@Composable
fun OpenMap(
    camera: MapCamera,
    onCameraChange: (MapCamera) -> Unit,
    markers: List<MapMarker>,
    onMarkerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    tiles: MapTileStore? = null,
    onMapClick: () -> Unit = {},
) {
    BoxWithConstraints(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val viewport = MapViewport(camera, widthPx, heightPx)
        val placed = viewport.visibleTiles()
        val loaded = remember(tiles) { mutableStateMapOf<TileId, ImageBitmap>() }
        val markerHalfPx = with(density) { MarkerSize.toPx() / 2f }

        if (tiles != null) {
            val ids = placed.map { it.id }
            LaunchedEffect(tiles, ids) {
                // Sequential on purpose: the store bounds its own network
                // concurrency, and a pan that scrolls a tile away cancels it.
                ids.forEach { id ->
                    if (loaded[id] == null) tiles.load(id)?.let { loaded[id] = it }
                }
            }
        }

        val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        val accuracyFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        val accuracyStroke = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        val staleFill = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        val staleStroke = MaterialTheme.colorScheme.outline.copy(alpha = 0.40f)
        val trailColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
        // Raster imagery is daylight-coloured; in dark theme it is dimmed rather
        // than inverted, which would make roads and labels unreadable.
        val dimImagery = MaterialTheme.colorScheme.surface.isDark()
        val imageryScrim = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
        val trailWidthPx = with(density) { 3.dp.toPx() }
        val accuracyStrokePx = with(density) { 1.5.dp.toPx() }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(camera, widthPx, heightPx) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        var next = camera.panBy(pan.x, pan.y, heightPx)
                        if (zoom != 1f) {
                            next = next.zoomBy(
                                factor = zoom,
                                focusXPx = centroid.x,
                                focusYPx = centroid.y,
                                viewport = MapViewport(next, widthPx, heightPx),
                            )
                        }
                        onCameraChange(next)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onMapClick() })
                },
        ) {
            var paintedImagery = false
            placed.forEach { tile ->
                val image = loaded[tile.id]
                if (image != null) {
                    paintedImagery = true
                    val size = max(1, tile.sizePx.roundToInt())
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(tile.leftPx.roundToInt(), tile.topPx.roundToInt()),
                        dstSize = IntSize(size, size),
                    )
                } else {
                    drawRect(
                        color = gridColor,
                        topLeft = Offset(tile.leftPx, tile.topPx),
                        size = Size(tile.sizePx, tile.sizePx),
                        style = Stroke(width = 1f),
                    )
                }
            }
            if (paintedImagery && dimImagery) drawRect(color = imageryScrim)

            markers.forEach { marker ->
                if (marker.trail.size >= 2) {
                    val path = Path()
                    marker.trail.forEachIndexed { index, point ->
                        val x = viewport.projectX(point.longitude)
                        val y = viewport.projectY(point.latitude)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path = path, color = trailColor, style = Stroke(width = trailWidthPx))
                }
                val radiusMeters = marker.accuracyMeters?.takeIf { it > 0 } ?: return@forEach
                val radiusPx = (radiusMeters / viewport.metersPerPixel()).toFloat()
                // Below a few pixels the circle says nothing the pin does not.
                if (radiusPx < 4f) return@forEach
                val center = Offset(
                    viewport.projectX(marker.point.longitude),
                    viewport.projectY(marker.point.latitude),
                )
                drawCircle(
                    color = if (marker.stale) staleFill else accuracyFill,
                    radius = radiusPx,
                    center = center,
                )
                drawCircle(
                    color = if (marker.stale) staleStroke else accuracyStroke,
                    radius = radiusPx,
                    center = center,
                    style = Stroke(width = accuracyStrokePx),
                )
            }
        }

        markers.forEach { marker ->
            val x = viewport.projectX(marker.point.longitude)
            val y = viewport.projectY(marker.point.latitude)
            // Pins outside the viewport are not composed at all, so a library of
            // accessories costs layout only for what is on screen.
            if (x < -MarkerCullSlopPx || y < -MarkerCullSlopPx ||
                x > widthPx + MarkerCullSlopPx || y > heightPx + MarkerCullSlopPx
            ) {
                return@forEach
            }
            MapMarkerPin(
                marker = marker,
                onClick = { onMarkerClick(marker.id) },
                modifier = Modifier.offset {
                    IntOffset((x - markerHalfPx).roundToInt(), (y - markerHalfPx).roundToInt())
                },
            )
        }

        MapScale(
            attribution = tiles?.source?.attribution,
            metersPerPixel = viewport.metersPerPixel(),
            maxWidthPx = widthPx * 0.35f,
            modifier = Modifier.align(Alignment.BottomStart),
        )

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // A pinch is not reachable with a keyboard, a switch, or one hand,
            // so zooming never depends on one.
            FilledTonalIconButton(
                onClick = { onCameraChange(camera.zoomBy(2f, widthPx / 2, heightPx / 2, viewport)) },
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Zoom in")
            }
            FilledTonalIconButton(
                onClick = { onCameraChange(camera.zoomBy(0.5f, widthPx / 2, heightPx / 2, viewport)) },
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Zoom out")
            }
        }
    }
}

@Composable
private fun MapMarkerPin(
    marker: MapMarker,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = when {
            marker.selected -> MaterialTheme.colorScheme.primary
            marker.stale -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (marker.selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = BorderStroke(
            width = 2.dp,
            color = if (marker.selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        modifier = modifier.size(MarkerSize),
    ) {
        Box(contentAlignment = Alignment.Center) { marker.content(marker.selected) }
    }
}

/**
 * Scale bar and licence attribution.
 *
 * Attribution is a condition of using the imagery, so it lives inside the map
 * where a caller cannot forget it.
 */
@Composable
private fun MapScale(
    attribution: String?,
    metersPerPixel: Double,
    maxWidthPx: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val bar = scaleBarFor(metersPerPixel, maxWidthPx)
    val barColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (bar != null) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = bar.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Canvas(
                        modifier = Modifier
                            .width(with(density) { bar.widthPx.toDp() })
                            .height(3.dp),
                    ) {
                        drawRect(color = barColor)
                    }
                }
            }
        }
        if (attribution != null) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                Text(
                    text = attribution,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

private fun Color.isDark(): Boolean = (0.299 * red + 0.587 * green + 0.114 * blue) < 0.5
