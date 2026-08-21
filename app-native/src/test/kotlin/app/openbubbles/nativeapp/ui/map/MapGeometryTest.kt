package app.openbubbles.nativeapp.ui.map

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Projection and camera maths. These are the only tests that can prove the map
 * puts a coordinate in the right place: on a device the same bug looks like
 * "the pin is a bit off".
 */
class WebMercatorTest {
    @Test
    fun `zoom zero puts the null island at the centre of one tile`() {
        assertEquals(128.0, WebMercator.projectX(0.0, 0.0), 1e-9)
        assertEquals(128.0, WebMercator.projectY(0.0, 0.0), 1e-9)
    }

    @Test
    fun `the antimeridian is the world's edge`() {
        assertEquals(0.0, WebMercator.projectX(-180.0, 0.0), 1e-9)
        assertEquals(256.0, WebMercator.projectX(180.0 - 1e-12, 0.0), 1e-6)
    }

    @Test
    fun `projection round-trips at every zoom`() {
        val points = listOf(
            GeoPoint(0.0, 0.0),
            GeoPoint(37.7749, -122.4194),
            GeoPoint(-33.8688, 151.2093),
            GeoPoint(51.5072, -0.1276),
            GeoPoint(-84.0, 179.0),
        )
        for (zoom in 2..19) {
            points.forEach { point ->
                val x = WebMercator.projectX(point.longitude, zoom.toDouble())
                val y = WebMercator.projectY(point.latitude, zoom.toDouble())
                assertEquals(
                    point.latitude,
                    WebMercator.unprojectLatitude(y, zoom.toDouble()),
                    1e-6,
                    "latitude at z$zoom",
                )
                assertEquals(
                    point.longitude,
                    WebMercator.unprojectLongitude(x, zoom.toDouble()),
                    1e-6,
                    "longitude at z$zoom",
                )
            }
        }
    }

    @Test
    fun `latitude is clamped to the projection's usable band`() {
        assertEquals(WebMercator.MAX_LATITUDE, WebMercator.clampLatitude(90.0))
        assertEquals(-WebMercator.MAX_LATITUDE, WebMercator.clampLatitude(-90.0))
        assertTrue(WebMercator.projectY(90.0, 4.0).isFinite())
    }

    @Test
    fun `longitude wraps instead of running off`() {
        assertEquals(-170.0, WebMercator.wrapLongitude(190.0), 1e-9)
        assertEquals(170.0, WebMercator.wrapLongitude(-190.0), 1e-9)
        assertEquals(0.0, WebMercator.wrapLongitude(720.0), 1e-9)
    }

    @Test
    fun `each zoom step halves the ground covered by a pixel`() {
        val z10 = WebMercator.metersPerPixel(0.0, 10.0)
        val z11 = WebMercator.metersPerPixel(0.0, 11.0)
        assertEquals(z10 / 2, z11, 1e-9)
        // Mercator stretches away from the equator, so a pixel covers less ground.
        assertTrue(WebMercator.metersPerPixel(60.0, 10.0) < z10)
    }

    @Test
    fun `distance matches known great-circle spans`() {
        val sf = GeoPoint(37.7749, -122.4194)
        val la = GeoPoint(34.0522, -118.2437)
        val meters = WebMercator.distanceMeters(sf, la)
        // ~559 km; a tolerance wide enough for the earth-radius choice only.
        assertTrue(abs(meters - 559_000) < 5_000, "got $meters")
        assertEquals(0.0, WebMercator.distanceMeters(sf, sf), 1e-6)
    }

    @Test
    fun `tile zoom and count follow the raster convention`() {
        assertEquals(14, WebMercator.tileZoom(14.7))
        assertEquals(2, WebMercator.tileZoom(-3.0))
        assertEquals(19, WebMercator.tileZoom(25.0))
        assertEquals(1024, WebMercator.tileCount(10))
    }
}

class MapViewportTest {
    private val viewport = MapViewport(
        camera = MapCamera(GeoPoint(37.7749, -122.4194), 14.0),
        widthPx = 1080f,
        heightPx = 1920f,
    )

    @Test
    fun `the camera centre lands in the middle of the viewport`() {
        assertEquals(540f, viewport.projectX(-122.4194), 0.01f)
        assertEquals(960f, viewport.projectY(37.7749), 0.01f)
    }

    @Test
    fun `projection and hit testing agree`() {
        val point = GeoPoint(37.79, -122.40)
        val x = viewport.projectX(point.longitude)
        val y = viewport.projectY(point.latitude)
        val back = viewport.unproject(x, y)
        assertEquals(point.latitude, back.latitude, 1e-6)
        assertEquals(point.longitude, back.longitude, 1e-6)
    }

    @Test
    fun `east is right and north is up`() {
        assertTrue(viewport.projectX(-122.40) > viewport.projectX(-122.44))
        assertTrue(viewport.projectY(37.80) < viewport.projectY(37.75))
    }

    @Test
    fun `a marker just across the antimeridian stays beside the camera`() {
        val wrapped = MapViewport(
            camera = MapCamera(GeoPoint(0.0, 179.9), 8.0),
            widthPx = 1080f,
            heightPx = 1080f,
        )
        val x = wrapped.projectX(-179.9)
        // The near copy of the world is a few hundred pixels to the east, not a
        // whole world width away to the west.
        assertTrue(x > 540f, "expected east of centre, got $x")
        assertTrue(x < 1600f, "expected the near copy, got $x")
    }

    @Test
    fun `visible tiles cover the viewport at the right zoom`() {
        val tiles = viewport.visibleTiles()
        assertTrue(tiles.isNotEmpty())
        assertTrue(tiles.all { it.id.zoom == 14 })
        assertTrue(tiles.all { it.sizePx == 256f }, "integer zoom draws tiles unscaled")
        // The whole viewport is painted: every pixel belongs to some tile rect.
        val covers = { x: Float, y: Float ->
            tiles.any {
                x >= it.leftPx && x <= it.leftPx + it.sizePx &&
                    y >= it.topPx && y <= it.topPx + it.sizePx
            }
        }
        assertTrue(covers(0f, 0f) && covers(1079f, 1919f) && covers(540f, 960f))
    }

    @Test
    fun `fractional zoom scales tiles instead of fetching a zoom that has none`() {
        val fractional = viewport.copy(camera = viewport.camera.copy(zoom = 14.5))
        val tiles = fractional.visibleTiles()
        assertTrue(tiles.all { it.id.zoom == 14 })
        assertTrue(tiles.all { it.sizePx > 256f && it.sizePx < 512f }, "got ${tiles.first().sizePx}")
    }

    @Test
    fun `tiles wrap in x and are dropped past the poles`() {
        val world = MapViewport(
            camera = MapCamera(GeoPoint(0.0, 180.0), 2.0),
            widthPx = 2000f,
            heightPx = 2000f,
        )
        val tiles = world.visibleTiles()
        assertTrue(tiles.all { it.id.x in 0..3 }, "x must wrap into range")
        assertTrue(tiles.all { it.id.y in 0..3 }, "y must stay inside the projection")
    }

    @Test
    fun `an unmeasured viewport asks for no tiles`() {
        assertTrue(viewport.copy(widthPx = 0f, heightPx = 0f).visibleTiles().isEmpty())
    }
}

class MapCameraTest {
    private val viewport = MapViewport(
        camera = MapCamera(GeoPoint(37.7749, -122.4194), 12.0),
        widthPx = 1000f,
        heightPx = 1000f,
    )

    @Test
    fun `panning moves the world with the finger`() {
        // Dragging right (positive dx) shows what was to the west.
        val panned = viewport.camera.panBy(200f, 0f, viewport.heightPx)
        assertTrue(panned.center.longitude < viewport.camera.center.longitude)
        val down = viewport.camera.panBy(0f, 200f, viewport.heightPx)
        assertTrue(down.center.latitude > viewport.camera.center.latitude)
    }

    @Test
    fun `panning cannot drag the world off the top or bottom`() {
        var camera = viewport.camera
        repeat(50) { camera = camera.panBy(0f, 5_000f, viewport.heightPx) }
        assertTrue(camera.center.latitude < WebMercator.MAX_LATITUDE)
        assertTrue(camera.center.latitude.isFinite())
        repeat(100) { camera = camera.panBy(0f, -5_000f, viewport.heightPx) }
        assertTrue(camera.center.latitude > -WebMercator.MAX_LATITUDE)
    }

    @Test
    fun `panning east across the antimeridian keeps going`() {
        var camera = MapCamera(GeoPoint(0.0, 179.0), 6.0)
        repeat(20) { camera = camera.panBy(-200f, 0f, 1000f) }
        assertTrue(camera.center.longitude in -180.0..180.0)
        assertTrue(camera.center.longitude < 0, "expected to have crossed into the west")
    }

    @Test
    fun `pinching keeps the ground under the fingers`() {
        val focusX = 250f
        val focusY = 750f
        val anchor = viewport.unproject(focusX, focusY)
        val zoomed = viewport.camera.zoomBy(2f, focusX, focusY, viewport)
        assertEquals(13.0, zoomed.zoom, 1e-9)
        val after = MapViewport(zoomed, viewport.widthPx, viewport.heightPx)
        assertEquals(focusX, after.projectX(anchor.longitude), 0.5f)
        assertEquals(focusY, after.projectY(anchor.latitude), 0.5f)
    }

    @Test
    fun `zoom is clamped and degenerate gestures are ignored`() {
        val far = viewport.camera.copy(zoom = WebMercator.MAX_ZOOM)
        assertEquals(WebMercator.MAX_ZOOM, far.zoomBy(4f, 0f, 0f, viewport).zoom)
        val near = viewport.camera.copy(zoom = WebMercator.MIN_ZOOM)
        assertEquals(WebMercator.MIN_ZOOM, near.zoomBy(0.1f, 0f, 0f, viewport).zoom)
        assertEquals(viewport.camera, viewport.camera.zoomBy(0f, 0f, 0f, viewport))
        assertEquals(viewport.camera, viewport.camera.zoomBy(Float.NaN, 0f, 0f, viewport))
    }
}

class MapFitTest {
    @Test
    fun `no points shows the world rather than nowhere`() {
        val camera = cameraFor(emptyList(), 1000f, 1000f)
        assertEquals(WebMercator.MIN_ZOOM, camera.zoom)
    }

    @Test
    fun `one point is centred at a street-level zoom`() {
        val point = GeoPoint(37.7749, -122.4194)
        val camera = cameraFor(listOf(point), 1000f, 1000f)
        assertEquals(point, camera.center)
        assertEquals(15.0, camera.zoom, 1e-9)
    }

    @Test
    fun `several points all land inside the viewport`() {
        val points = listOf(
            GeoPoint(37.7749, -122.4194),
            GeoPoint(37.3349, -122.0090),
            GeoPoint(37.8716, -122.2727),
        )
        val camera = cameraFor(points, 1080f, 1600f, paddingPx = 96f)
        val viewport = MapViewport(camera, 1080f, 1600f)
        points.forEach { point ->
            val x = viewport.projectX(point.longitude)
            val y = viewport.projectY(point.latitude)
            assertTrue(x in 0f..1080f, "x=$x for $point")
            assertTrue(y in 0f..1600f, "y=$y for $point")
        }
    }

    @Test
    fun `a continent-wide spread still fits`() {
        val points = listOf(GeoPoint(60.0, -150.0), GeoPoint(-40.0, 150.0))
        val camera = cameraFor(points, 1080f, 1080f)
        val viewport = MapViewport(camera, 1080f, 1080f)
        points.forEach { point ->
            assertTrue(viewport.projectY(point.latitude) in -1f..1081f)
        }
        assertTrue(camera.zoom >= WebMercator.MIN_ZOOM)
    }
}

class MapScaleBarTest {
    @Test
    fun `the bar reports a round distance that fits the space`() {
        val bar = scaleBarFor(metersPerPixel = 2.0, maxWidthPx = 300f)
        assertNotNull(bar)
        assertEquals("500 m", bar.label)
        assertEquals(250f, bar.widthPx, 0.01f)
    }

    @Test
    fun `kilometres are labelled as kilometres`() {
        val bar = scaleBarFor(metersPerPixel = 40.0, maxWidthPx = 300f)
        assertNotNull(bar)
        assertEquals("10 km", bar.label)
    }

    @Test
    fun `an impossible scale has no bar instead of a wrong one`() {
        assertNull(scaleBarFor(metersPerPixel = 0.0, maxWidthPx = 300f))
        assertNull(scaleBarFor(metersPerPixel = 2.0, maxWidthPx = 0f))
        assertNull(scaleBarFor(metersPerPixel = 0.0001, maxWidthPx = 10f))
    }
}

class MapTileSourceTest {
    @Test
    fun `tile urls substitute the whole address`() {
        assertEquals(
            "https://tile.openstreetmap.org/14/2621/6333.png",
            MapTileSource.OpenStreetMap.url(TileId(14, 2621, 6333)),
        )
    }

    @Test
    fun `the default source carries the attribution its licence requires`() {
        assertTrue(MapTileSource.OpenStreetMap.attribution.contains("OpenStreetMap"))
    }
}
