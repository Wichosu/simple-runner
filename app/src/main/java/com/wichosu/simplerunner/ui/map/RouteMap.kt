package com.wichosu.simplerunner.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wichosu.simplerunner.R
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val ROUTE_SOURCE_ID = "run-route"
private const val ROUTE_CASING_LAYER_ID = "run-route-casing"
private const val ROUTE_LAYER_ID = "run-route-line"

private const val CASING_WIDTH = 7.5f
private const val ROUTE_WIDTH = 4.5f

/** How long a style may take before the UI admits the map is not coming. */
private const val STYLE_TIMEOUT_MS = 8_000L

enum class RouteMapMode {
    /** Frame the whole track: the finished-run view. */
    Fit,

    /** Keep the newest point centred: the live view. */
    Follow,
}

/**
 * Draws a recorded track over an OpenFreeMap basemap.
 *
 * Segments are separate line strings, so a pause shows up as a gap instead of a straight
 * line across wherever the runner went while the run was paused.
 *
 * This composable is the only place that knows MapLibre exists. If the library stops
 * working or the tile provider disappears, [MapConfig] and this file are what change.
 */
@Composable
fun RouteMap(
    points: List<RoutePoint>,
    mode: RouteMapMode,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    /** Bumped every time a style finishes loading; 0 means nothing is drawable yet. */
    var styleGeneration by remember { mutableIntStateOf(0) }
    var styleFailed by remember { mutableStateOf(false) }
    var following by remember { mutableStateOf(true) }

    fun loadStyle(ready: MapLibreMap) {
        ready.setStyle(Style.Builder().fromUri(MapConfig.STYLE_URL)) { loaded ->
            loaded.addSource(GeoJsonSource(ROUTE_SOURCE_ID))
            // A light casing under the line keeps the route readable over busy streets.
            loaded.addLayer(
                LineLayer(ROUTE_CASING_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                    PropertyFactory.lineColor(MapConfig.ROUTE_CASING_COLOR),
                    PropertyFactory.lineWidth(CASING_WIDTH),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
            loaded.addLayer(
                LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                    PropertyFactory.lineColor(MapConfig.ROUTE_COLOR),
                    PropertyFactory.lineWidth(ROUTE_WIDTH),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
            style = loaded
            styleGeneration++
        }
    }

    // A map that cannot load (airplane mode, provider outage) must say so rather than sit
    // there as a grey rectangle. The run itself is unaffected, so the message says that.
    LaunchedEffect(styleGeneration) {
        if (styleGeneration > 0) {
            styleFailed = false
            return@LaunchedEffect
        }
        delay(STYLE_TIMEOUT_MS)
        if (styleGeneration == 0) styleFailed = true
    }

    LaunchedEffect(map, style, points, following) {
        val ready = map ?: return@LaunchedEffect
        val loaded = style ?: return@LaunchedEffect

        loaded.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)
            ?.setGeoJson(points.toFeatureCollection())

        when {
            points.isEmpty() -> Unit
            mode == RouteMapMode.Fit -> fitCamera(ready, points)
            following -> followCamera(ready, points.last())
        }
    }

    // MapView owns a GL surface and has to be told about lifecycle changes itself.
    DisposableEffect(lifecycleOwner, mapView) {
        val view = mapView ?: return@DisposableEffect onDispose { }
        var started = false
        var resumed = false

        fun sync() {
            val state = lifecycleOwner.lifecycle.currentState
            if (!started && state.isAtLeast(Lifecycle.State.STARTED)) {
                view.onStart()
                started = true
            }
            if (!resumed && state.isAtLeast(Lifecycle.State.RESUMED)) {
                view.onResume()
                resumed = true
            }
            if (resumed && !state.isAtLeast(Lifecycle.State.RESUMED)) {
                view.onPause()
                resumed = false
            }
            if (started && !state.isAtLeast(Lifecycle.State.STARTED)) {
                view.onStop()
                started = false
            }
        }

        val observer = LifecycleEventObserver { _, _ -> sync() }
        lifecycleOwner.lifecycle.addObserver(observer)
        // Compose can attach while the activity is already resumed, in which case no
        // further lifecycle event will arrive; seed from the current state.
        sync()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (resumed) view.onPause()
            if (started) view.onStop()
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val options = MapLibreMapOptions.createFromAttributes(context)
                    .logoEnabled(false)
                    .attributionEnabled(false)

                MapView(context, options).also { view ->
                    view.onCreate(null)
                    mapView = view
                    view.getMapAsync { ready ->
                        map = ready
                        ready.uiSettings.isRotateGesturesEnabled = false
                        ready.setMinZoomPreference(2.0)
                        ready.addOnCameraMoveStartedListener { reason ->
                            // Panning means the user wants to look somewhere; stop moving
                            // the map under their finger until they ask for the run again.
                            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                following = false
                            }
                        }
                        loadStyle(ready)
                    }
                }
            },
            onRelease = { view ->
                view.onDestroy()
                style = null
                map = null
                mapView = null
            },
        )

        Text(
            text = stringResource(R.string.map_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF2B2B2B),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.82f))
                .clickable { runCatching { uriHandler.openUri(MapConfig.ATTRIBUTION_URL) } }
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )

        if (mode == RouteMapMode.Follow && !following && points.isNotEmpty()) {
            FilledTonalIconButton(
                onClick = { following = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = stringResource(R.string.map_recenter),
                )
            }
        }

        if (styleFailed) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.map_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(
                        onClick = {
                            val ready = map ?: return@TextButton
                            styleFailed = false
                            styleGeneration = 0
                            loadStyle(ready)
                        },
                    ) {
                        Text(stringResource(R.string.map_retry))
                    }
                }
            }
        }
    }
}

private fun followCamera(map: MapLibreMap, point: RoutePoint) {
    map.animateCamera(
        CameraUpdateFactory.newLatLngZoom(LatLng(point.lat, point.lon), MapConfig.FOLLOW_ZOOM),
        800,
    )
}

private fun fitCamera(map: MapLibreMap, points: List<RoutePoint>) {
    val builder = LatLngBounds.Builder()
    points.forEach { builder.include(LatLng(it.lat, it.lon)) }
    val bounds = runCatching { builder.build() }.getOrNull() ?: return

    val southWest = bounds.southWest
    val northEast = bounds.northEast
    val degenerate = kotlin.math.abs(northEast.latitude - southWest.latitude) < 1e-6 &&
        kotlin.math.abs(northEast.longitude - southWest.longitude) < 1e-6
    if (degenerate) {
        followCamera(map, points.last())
        return
    }

    map.animateCamera(
        CameraUpdateFactory.newLatLngBounds(bounds, MapConfig.FIT_PADDING_PX),
        500,
    )
}

private fun List<RoutePoint>.toFeatureCollection(): FeatureCollection {
    val features = groupBy { it.segment }
        .values
        .filter { it.size >= 2 }
        .map { segmentPoints ->
            Feature.fromGeometry(
                LineString.fromLngLats(
                    segmentPoints.map { Point.fromLngLat(it.lon, it.lat) },
                ),
            )
        }
    return FeatureCollection.fromFeatures(features)
}
