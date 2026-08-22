package app.openbubbles.nativeapp.ui.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlin.math.abs

/**
 * Optional Google renderer for the same targets, trails and camera as [OpenMap].
 *
 * GoogleMap starts requesting imagery as soon as it enters the composition, so
 * callers must never compose this function before explicit location-sharing
 * consent. Phone GPS and Google's My Location layer are intentionally unused.
 */
@Composable
fun GoogleMapsView(
    camera: MapCamera,
    onCameraChange: (MapCamera) -> Unit,
    markers: List<MapMarker>,
    onMarkerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onMapClick: () -> Unit = {},
) {
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            camera.center.toLatLng(),
            camera.zoom.toFloat(),
        )
    }
    val currentCamera by rememberUpdatedState(camera)
    val currentOnCameraChange by rememberUpdatedState(onCameraChange)

    LaunchedEffect(camera.center, camera.zoom) {
        val position = cameraState.position
        val requestedCenter = camera.center.toLatLng()
        if (position.target != requestedCenter || abs(position.zoom - camera.zoom) > 0.0001) {
            cameraState.move(
                CameraUpdateFactory.newLatLngZoom(requestedCenter, camera.zoom.toFloat()),
            )
        }
    }

    LaunchedEffect(cameraState) {
        snapshotFlow {
            Triple(cameraState.isMoving, cameraState.cameraMoveStartedReason, cameraState.position)
        }.collect { (moving, reason, position) ->
            if (!moving && reason == CameraMoveStartedReason.GESTURE) {
                val updatedCamera = MapCamera(
                    center = GeoPoint(position.target.latitude, position.target.longitude),
                    zoom = WebMercator.clampZoom(position.zoom.toDouble()),
                )
                if (updatedCamera != currentCamera) currentOnCameraChange(updatedCamera)
            }
        }
    }

    val density = LocalDensity.current
    val accuracyFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    val accuracyStroke = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val staleFill = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    val staleStroke = MaterialTheme.colorScheme.outline.copy(alpha = 0.40f)
    val trailColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
    val trailWidthPx = with(density) { 3.dp.toPx() }
    val accuracyStrokePx = with(density) { 1.5.dp.toPx() }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraState,
        properties = MapProperties(
            isMyLocationEnabled = false,
            minZoomPreference = WebMercator.MIN_ZOOM.toFloat(),
            maxZoomPreference = WebMercator.MAX_ZOOM.toFloat(),
        ),
        uiSettings = MapUiSettings(
            compassEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            rotationGesturesEnabled = false,
            tiltGesturesEnabled = false,
            zoomControlsEnabled = true,
        ),
        mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
        onMapClick = { onMapClick() },
    ) {
        markers.forEach { marker ->
            if (marker.trail.size >= 2) {
                Polyline(
                    points = marker.trail.map(GeoPoint::toLatLng),
                    color = trailColor,
                    width = trailWidthPx,
                )
            }
            marker.accuracyMeters?.takeIf { it > 0 }?.let { radiusMeters ->
                Circle(
                    center = marker.point.toLatLng(),
                    radius = radiusMeters,
                    fillColor = if (marker.stale) staleFill else accuracyFill,
                    strokeColor = if (marker.stale) staleStroke else accuracyStroke,
                    strokeWidth = accuracyStrokePx,
                )
            }
            MarkerComposable(
                marker.id,
                marker.label,
                marker.selected,
                marker.stale,
                state = rememberUpdatedMarkerState(position = marker.point.toLatLng()),
                contentDescription = marker.label,
                anchor = Offset(0.5f, 0.5f),
                title = marker.label,
                zIndex = if (marker.selected) 1f else 0f,
                onClick = {
                    onMarkerClick(marker.id)
                    true
                },
            ) {
                MapMarkerPin(marker = marker, onClick = {})
            }
        }
    }
}

private fun GeoPoint.toLatLng(): LatLng = LatLng(latitude, longitude)
