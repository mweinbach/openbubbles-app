package app.openbubbles.nativeapp.ui.map

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * Web Mercator projection maths for the in-app slippy map.
 *
 * Everything here is pure: no Android types, no Compose, no I/O. The map's
 * camera, hit testing, tile selection, and scale bar are all decided by these
 * functions, so they can be proven on the host instead of only on a device.
 *
 * The convention is the standard raster one. A tile is [TILE_SIZE] pixels
 * square, zoom `z` covers the world in `2^z` tiles per axis, x grows east from
 * longitude -180, and y grows south from the projection's north edge.
 */
const val TILE_SIZE: Int = 256

/** One geographic position. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** One raster tile address. */
data class TileId(val zoom: Int, val x: Int, val y: Int)

/**
 * A tile placed on screen: [id] plus the pixel rect it occupies. [sizePx] is
 * fractional because the camera zoom is continuous while tiles are not.
 */
data class PlacedTile(val id: TileId, val leftPx: Float, val topPx: Float, val sizePx: Float)

/**
 * Camera state: what the map is centred on and how far in it is zoomed.
 *
 * [zoom] is continuous so a pinch is smooth; tiles are fetched at
 * [WebMercator.tileZoom] and scaled to fill the difference.
 */
data class MapCamera(val center: GeoPoint, val zoom: Double)

object WebMercator {
    /**
     * Zoom floor. Below this the world is smaller than a phone screen and the
     * map reads as a decoration rather than a map.
     */
    const val MIN_ZOOM: Double = 2.0

    /** Zoom ceiling; standard raster tile sets stop having detail past this. */
    const val MAX_ZOOM: Double = 19.0

    /** The projection is undefined at the poles; clamp to its usable band. */
    const val MAX_LATITUDE: Double = 85.05112878

    /** Equatorial metres per pixel at zoom 0 for a 256px tile. */
    const val EQUATOR_METERS_PER_PIXEL: Double = 156_543.033928041

    private const val EARTH_RADIUS_METERS: Double = 6_371_008.8

    fun clampZoom(zoom: Double): Double = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

    fun clampLatitude(latitude: Double): Double =
        latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)

    /** Wraps a longitude into [-180, 180). */
    fun wrapLongitude(longitude: Double): Double {
        var value = (longitude + 180.0) % 360.0
        if (value < 0) value += 360.0
        return value - 180.0
    }

    /** World width and height in pixels at [zoom]. */
    fun worldSizePx(zoom: Double): Double = TILE_SIZE * 2.0.pow(zoom)

    /** Integer tile zoom used to fetch imagery for a continuous [zoom]. */
    fun tileZoom(zoom: Double): Int =
        floor(clampZoom(zoom)).toInt().coerceIn(MIN_ZOOM.toInt(), MAX_ZOOM.toInt())

    /** Tile count per axis at an integer tile zoom. */
    fun tileCount(tileZoom: Int): Int = 1 shl tileZoom

    fun projectX(longitude: Double, zoom: Double): Double =
        (wrapLongitude(longitude) + 180.0) / 360.0 * worldSizePx(zoom)

    fun projectY(latitude: Double, zoom: Double): Double {
        val sinLat = sin(clampLatitude(latitude) * PI / 180.0)
        val y = 0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * PI)
        return y * worldSizePx(zoom)
    }

    fun unprojectLongitude(x: Double, zoom: Double): Double =
        wrapLongitude(x / worldSizePx(zoom) * 360.0 - 180.0)

    fun unprojectLatitude(y: Double, zoom: Double): Double {
        val normalized = 0.5 - y / worldSizePx(zoom)
        return atan(sinh(2 * PI * normalized)) * 180.0 / PI
    }

    /** Ground resolution at a latitude, used for the scale bar and radii. */
    fun metersPerPixel(latitude: Double, zoom: Double): Double =
        EQUATOR_METERS_PER_PIXEL * cos(clampLatitude(latitude) * PI / 180.0) / 2.0.pow(zoom)

    /** Great-circle distance in metres. */
    fun distanceMeters(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = from.latitude * PI / 180.0
        val lat2 = to.latitude * PI / 180.0
        val dLat = lat2 - lat1
        val dLon = (wrapLongitude(to.longitude) - wrapLongitude(from.longitude)) * PI / 180.0
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(h)))
    }
}

/**
 * A camera plus the pixel size it is drawn into. This is the object the map
 * projects and hit-tests through, so panning, marker placement, and tile
 * selection can never disagree about where a coordinate lands.
 */
data class MapViewport(
    val camera: MapCamera,
    val widthPx: Float,
    val heightPx: Float,
) {
    private val centerX: Double get() = WebMercator.projectX(camera.center.longitude, camera.zoom)
    private val centerY: Double get() = WebMercator.projectY(camera.center.latitude, camera.zoom)

    /** Screen pixel offset of a geographic point, relative to the viewport. */
    fun projectX(longitude: Double): Float {
        val world = WebMercator.projectX(longitude, camera.zoom)
        val size = WebMercator.worldSizePx(camera.zoom)
        // Longitude wraps, so pick the copy of the world nearest the camera:
        // a marker just past the antimeridian must not fly off the far edge.
        var delta = world - centerX
        if (delta > size / 2) delta -= size
        if (delta < -size / 2) delta += size
        return (widthPx / 2 + delta).toFloat()
    }

    fun projectY(latitude: Double): Float =
        (heightPx / 2 + (WebMercator.projectY(latitude, camera.zoom) - centerY)).toFloat()

    fun unproject(xPx: Float, yPx: Float): GeoPoint = GeoPoint(
        latitude = WebMercator.unprojectLatitude(centerY + (yPx - heightPx / 2), camera.zoom),
        longitude = WebMercator.unprojectLongitude(centerX + (xPx - widthPx / 2), camera.zoom),
    )

    /** Metres per screen pixel at the camera centre. */
    fun metersPerPixel(): Double =
        WebMercator.metersPerPixel(camera.center.latitude, camera.zoom)

    /**
     * Every tile needed to paint this viewport, in draw order, already placed in
     * screen pixels. Tiles above or below the projection are dropped; tiles east
     * or west wrap, so panning across the antimeridian keeps painting.
     */
    fun visibleTiles(): List<PlacedTile> {
        if (widthPx <= 0f || heightPx <= 0f) return emptyList()
        val tileZoom = WebMercator.tileZoom(camera.zoom)
        val count = WebMercator.tileCount(tileZoom)
        // Tiles are fetched at an integer zoom and drawn larger or smaller to
        // cover the fractional remainder of the camera zoom.
        val scale = 2.0.pow(camera.zoom - tileZoom)
        val sizePx = TILE_SIZE * scale
        val centerTileX = WebMercator.projectX(camera.center.longitude, tileZoom.toDouble())
        val centerTileY = WebMercator.projectY(camera.center.latitude, tileZoom.toDouble())
        val originX = widthPx / 2 - centerTileX * scale
        val originY = heightPx / 2 - centerTileY * scale
        val firstX = floor(-originX / sizePx).toInt()
        val lastX = floor((widthPx - originX) / sizePx).toInt()
        val firstY = max(0, floor(-originY / sizePx).toInt())
        val lastY = min(count - 1, floor((heightPx - originY) / sizePx).toInt())
        if (lastY < firstY) return emptyList()
        val tiles = ArrayList<PlacedTile>((lastX - firstX + 1) * (lastY - firstY + 1))
        for (y in firstY..lastY) {
            for (x in firstX..lastX) {
                val wrappedX = ((x % count) + count) % count
                tiles += PlacedTile(
                    id = TileId(tileZoom, wrappedX, y),
                    leftPx = (originX + x * sizePx).toFloat(),
                    topPx = (originY + y * sizePx).toFloat(),
                    sizePx = sizePx.toFloat(),
                )
            }
        }
        return tiles
    }
}

/** Pans the camera by a screen-pixel delta, keeping it inside the projection. */
fun MapCamera.panBy(dxPx: Float, dyPx: Float, viewportHeightPx: Float): MapCamera {
    val worldSize = WebMercator.worldSizePx(zoom)
    val x = WebMercator.projectX(center.longitude, zoom) - dxPx
    val rawY = WebMercator.projectY(center.latitude, zoom) - dyPx
    // Vertical panning stops where the world edge reaches the viewport edge, so
    // the map cannot be dragged off into empty space.
    val halfHeight = (viewportHeightPx / 2).toDouble()
    val y = rawY.coerceIn(min(halfHeight, worldSize / 2), max(worldSize - halfHeight, worldSize / 2))
    return copy(
        center = GeoPoint(
            latitude = WebMercator.unprojectLatitude(y, zoom),
            longitude = WebMercator.unprojectLongitude(x, zoom),
        ),
    )
}

/**
 * Zooms by [factor] about a screen point, so a pinch keeps the ground under the
 * fingers where it was.
 */
fun MapCamera.zoomBy(
    factor: Float,
    focusXPx: Float,
    focusYPx: Float,
    viewport: MapViewport,
): MapCamera {
    if (factor <= 0f || !factor.isFinite()) return this
    val target = WebMercator.clampZoom(zoom + ln(factor.toDouble()) / ln(2.0))
    if (target == zoom) return this
    val anchor = viewport.unproject(focusXPx, focusYPx)
    val zoomed = copy(zoom = target)
    val after = MapViewport(zoomed, viewport.widthPx, viewport.heightPx)
    val dx = after.projectX(anchor.longitude) - focusXPx
    val dy = after.projectY(anchor.latitude) - focusYPx
    return zoomed.panBy(-dx, -dy, viewport.heightPx)
}

/**
 * A camera that shows every point with [paddingPx] of breathing room.
 *
 * One point gets [singlePointZoom] because a zero-size bounding box has no
 * scale of its own. An empty list gets a whole-world view rather than a
 * meaningless centre.
 */
fun cameraFor(
    points: List<GeoPoint>,
    widthPx: Float,
    heightPx: Float,
    paddingPx: Float = 64f,
    singlePointZoom: Double = 15.0,
): MapCamera {
    if (points.isEmpty()) return MapCamera(GeoPoint(20.0, 0.0), WebMercator.MIN_ZOOM)
    if (points.size == 1) {
        return MapCamera(points.single(), WebMercator.clampZoom(singlePointZoom))
    }
    val south = points.minOf { WebMercator.clampLatitude(it.latitude) }
    val north = points.maxOf { WebMercator.clampLatitude(it.latitude) }
    val west = points.minOf { WebMercator.wrapLongitude(it.longitude) }
    val east = points.maxOf { WebMercator.wrapLongitude(it.longitude) }
    val center = GeoPoint((south + north) / 2, (west + east) / 2)
    val usableWidth = max(1f, widthPx - paddingPx)
    val usableHeight = max(1f, heightPx - paddingPx)
    // Find the largest zoom at which the whole span still fits both axes.
    var zoom = WebMercator.MAX_ZOOM
    while (zoom > WebMercator.MIN_ZOOM) {
        val spanX = abs(
            WebMercator.projectX(east, zoom) - WebMercator.projectX(west, zoom),
        )
        val spanY = abs(
            WebMercator.projectY(south, zoom) - WebMercator.projectY(north, zoom),
        )
        if (spanX <= usableWidth && spanY <= usableHeight) break
        zoom -= 0.25
    }
    return MapCamera(center, WebMercator.clampZoom(zoom))
}

/**
 * A round distance for the scale bar plus the pixel width that represents it,
 * so the bar always reads 50 m / 200 m / 1 km rather than "173 m".
 */
data class MapScaleBar(val label: String, val widthPx: Float)

fun scaleBarFor(metersPerPixel: Double, maxWidthPx: Float): MapScaleBar? {
    if (metersPerPixel <= 0 || maxWidthPx <= 0) return null
    val maxMeters = metersPerPixel * maxWidthPx
    val steps = listOf(
        10.0, 20.0, 50.0, 100.0, 200.0, 500.0,
        1_000.0, 2_000.0, 5_000.0, 10_000.0, 20_000.0, 50_000.0,
        100_000.0, 200_000.0, 500_000.0, 1_000_000.0, 2_000_000.0,
    )
    val meters = steps.lastOrNull { it <= maxMeters } ?: return null
    val label = if (meters >= 1_000) "${(meters / 1_000).toInt()} km" else "${meters.toInt()} m"
    return MapScaleBar(label = label, widthPx = (meters / metersPerPixel).toFloat())
}
