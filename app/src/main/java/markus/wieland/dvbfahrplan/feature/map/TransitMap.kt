package markus.wieland.dvbfahrplan.feature.map

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import markus.wieland.dvbfahrplan.core.designsystem.FahrplanTheme
import markus.wieland.dvbfahrplan.core.designsystem.style
import markus.wieland.dvbfahrplan.core.model.LatLng
import markus.wieland.dvbfahrplan.core.model.MapSegment
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.android.geometry.LatLng as MlLatLng

/** A stop drawn on the map. [emphasis]: 0 = small dot, 1 = interchange / current position, 2 = start or destination. */
@Immutable
data class MapStop(val position: LatLng, val emphasis: Int = 0)

private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/positron"
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"

private const val LINE_SOURCE = "route-lines"
private const val STOP_SOURCE = "route-stops"
private const val CASING_LAYER = "route-casing"
private const val LINE_LAYER = "route-line"
private const val WALK_LAYER = "route-walk"
private const val STOP_LAYER = "route-stop"

/**
 * Native MapLibre map (no WebView) showing a route or trip: one line per segment in its line color,
 * walks dashed, and stops as dots. Vector tiles come from OpenFreeMap (no API key needed).
 */
@Composable
fun TransitMap(
    segments: List<MapSegment>,
    stops: List<MapStop>,
    modifier: Modifier = Modifier,
    focus: LatLng? = null,
    onTouch: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val dark = FahrplanTheme.colors.isDark
    val inkColor = FahrplanTheme.colors.ink
    val surfaceColor = FahrplanTheme.colors.surface
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    val mapView = remember {
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true).attributionEnabled(true).logoEnabled(false)).apply {
            onCreate(null)
            getMapAsync { map = it }
        }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(map, dark) {
        val m = map ?: return@LaunchedEffect
        m.uiSettings.isRotateGesturesEnabled = false
        m.uiSettings.isTiltGesturesEnabled = false
        // MapLibre leaves room for its logo left of the "i"; the logo is off, so pull it into the corner.
        val inset = (12 * context.resources.displayMetrics.density).toInt()
        m.uiSettings.setAttributionMargins(inset, 0, 0, inset)
        m.setStyle(Style.Builder().fromUri(if (dark) STYLE_DARK else STYLE_LIGHT)) { loaded ->
            addRouteLayers(loaded, inkColor, surfaceColor)
            style = loaded
        }
    }

    LaunchedEffect(style, segments, stops) {
        val s = style ?: return@LaunchedEffect
        (s.getSource(LINE_SOURCE) as? GeoJsonSource)?.setGeoJson(segmentsToGeoJson(segments))
        (s.getSource(STOP_SOURCE) as? GeoJsonSource)?.setGeoJson(stopsToGeoJson(stops))
    }

    // Fit the whole route once, then follow explicit focus requests.
    var fitted by remember { mutableStateOf(false) }
    LaunchedEffect(map, segments, stops) {
        val m = map ?: return@LaunchedEffect
        if (fitted) return@LaunchedEffect
        val points = segments.flatMap { it.points } + stops.map { it.position }
        if (points.isEmpty()) return@LaunchedEffect
        val padding = (48 * context.resources.displayMetrics.density).toInt()
        if (points.size == 1) {
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(points.first().toMl(), 15.0))
        } else {
            val bounds = LatLngBounds.Builder().includes(points.map { it.toMl() }).build()
            m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
        }
        fitted = true
    }
    LaunchedEffect(map, focus) {
        val m = map ?: return@LaunchedEffect
        focus ?: return@LaunchedEffect
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(focus.toMl(), 15.5), 600)
    }

    AndroidView(
        factory = {
            mapView.apply {
                // Let the map own gestures while touched (it sits inside a scrollable screen).
                setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            v.parent?.requestDisallowInterceptTouchEvent(true); onTouch(true)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            v.parent?.requestDisallowInterceptTouchEvent(false); onTouch(false)
                        }
                    }
                    false
                }
            }
        },
        modifier = modifier,
    )
}

private fun addRouteLayers(style: Style, ink: Color, surface: Color) {
    style.addSource(GeoJsonSource(LINE_SOURCE))
    style.addSource(GeoJsonSource(STOP_SOURCE))

    val transitOnly = Expression.eq(Expression.get("walk"), Expression.literal(false))
    val walkOnly = Expression.eq(Expression.get("walk"), Expression.literal(true))

    style.addLayer(
        LineLayer(CASING_LAYER, LINE_SOURCE).withFilter(transitOnly).withProperties(
            PropertyFactory.lineColor(surface.toArgb()),
            PropertyFactory.lineWidth(9f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        LineLayer(LINE_LAYER, LINE_SOURCE).withFilter(transitOnly).withProperties(
            PropertyFactory.lineColor(Expression.get("color")),
            PropertyFactory.lineWidth(5.5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        LineLayer(WALK_LAYER, LINE_SOURCE).withFilter(walkOnly).withProperties(
            PropertyFactory.lineColor(ink.copy(alpha = 0.7f).toArgb()),
            PropertyFactory.lineWidth(3.5f),
            PropertyFactory.lineDasharray(arrayOf(0.1f, 1.8f)),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        ),
    )
    style.addLayer(
        CircleLayer(STOP_LAYER, STOP_SOURCE).withProperties(
            PropertyFactory.circleRadius(
                Expression.match(Expression.get("emphasis"), Expression.literal(3.5f), Expression.stop(1, 5.5f), Expression.stop(2, 7.5f)),
            ),
            PropertyFactory.circleColor(
                Expression.match(Expression.get("emphasis"), Expression.color(surface.toArgb()), Expression.stop(2, Expression.color(ink.toArgb()))),
            ),
            PropertyFactory.circleStrokeColor(ink.toArgb()),
            PropertyFactory.circleStrokeWidth(
                Expression.match(Expression.get("emphasis"), Expression.literal(1.5f), Expression.stop(2, 3f)),
            ),
        ),
    )
}

private fun segmentsToGeoJson(segments: List<MapSegment>): String = FeatureCollection.fromFeatures(
    segments.filter { it.points.size >= 2 }.map { segment ->
        val walk = !segment.mode.isTransit
        Feature.fromGeometry(LineString.fromLngLats(segment.points.map { Point.fromLngLat(it.lng, it.lat) })).apply {
            addBooleanProperty("walk", walk)
            addStringProperty("color", String.format("#%06X", 0xFFFFFF and segment.mode.style().color.toArgb()))
        }
    },
).toJson()

private fun stopsToGeoJson(stops: List<MapStop>): String = FeatureCollection.fromFeatures(
    stops.sortedBy { it.emphasis }.map { stop ->
        Feature.fromGeometry(Point.fromLngLat(stop.position.lng, stop.position.lat)).apply {
            addNumberProperty("emphasis", stop.emphasis)
        }
    },
).toJson()

private fun LatLng.toMl() = MlLatLng(lat, lng)
