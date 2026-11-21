package markus.wieland.dvbfahrplan.feature.detail

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import markus.wieland.dvbfahrplan.R
import markus.wieland.dvbfahrplan.core.designsystem.FahrplanTheme
import markus.wieland.dvbfahrplan.core.designsystem.IconButton
import markus.wieland.dvbfahrplan.core.designsystem.LineBadge
import markus.wieland.dvbfahrplan.core.designsystem.Txt
import markus.wieland.dvbfahrplan.core.designsystem.clock
import markus.wieland.dvbfahrplan.core.designsystem.clockWithDay
import markus.wieland.dvbfahrplan.core.designsystem.delayLabel
import markus.wieland.dvbfahrplan.core.designsystem.durationText
import markus.wieland.dvbfahrplan.core.designsystem.interchangesText
import markus.wieland.dvbfahrplan.core.designsystem.platformText
import markus.wieland.dvbfahrplan.core.designsystem.style
import markus.wieland.dvbfahrplan.core.model.LatLng
import markus.wieland.dvbfahrplan.core.model.Leg
import markus.wieland.dvbfahrplan.core.model.Mode
import markus.wieland.dvbfahrplan.core.model.Route
import markus.wieland.dvbfahrplan.core.model.RouteStop
import markus.wieland.dvbfahrplan.core.model.Stop
import markus.wieland.dvbfahrplan.core.model.TimePoint
import markus.wieland.dvbfahrplan.feature.map.MapStop

@Composable
fun RouteDetailScreen(route: Route, origin: Stop, destination: Stop, onBack: () -> Unit) {
    var focus by remember { mutableStateOf<LatLng?>(null) }
    val context = LocalContext.current
    val transitLegs = route.legs.filter { it.mode.isTransit }
    val mapStops = buildList {
        transitLegs.forEachIndexed { i, leg ->
            leg.intermediateStops.forEach { s -> s.location?.let { add(MapStop(it, 0)) } }
            leg.origin?.location?.let { add(MapStop(it, if (i == 0) 2 else 1)) }
            leg.destination?.location?.let { add(MapStop(it, if (i == transitLegs.lastIndex) 2 else 1)) }
        }
    }

    DetailScaffold(
        segments = route.path,
        stops = mapStops,
        focus = focus,
        onBack = onBack,
        action = {
            IconButton(R.drawable.ic_share, stringResource(R.string.action_share), onClick = { share(context, shareText(context, route, origin, destination)) })
        },
    ) {
        item(key = "summary") { RouteSummary(route, origin, destination) }
        legs(route.legs) { focus = it }
    }
}

@Composable
private fun RouteSummary(route: Route, origin: Stop, destination: Stop) {
    val colors = FahrplanTheme.colors
    val type = FahrplanTheme.type
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)) {
        Txt("${origin.name} → ${destination.name}", style = type.title, maxLines = 2)
        Spacer(Modifier.height(4.dp))
        val departure = route.departure
        Txt(
            listOfNotNull(
                departure?.scheduled?.clockWithDay(),
                durationText(route.durationMinutes),
                interchangesText(route.interchanges),
            ).joinToString(" · "),
            style = type.body,
            color = colors.inkMuted,
        )
        if (route.price != null && route.fareZones != null) {
            Txt(stringResource(R.string.price, route.price, route.fareZones), style = type.caption, color = colors.inkFaint)
        }
    }
}

private fun LazyListScope.legs(legs: List<Leg>, onFocus: (LatLng) -> Unit) {
    // Render the legs as the API sends them. The only derived row is "change" between two rides that
    // directly follow each other (same platform or same stop), which the API does not list separately.
    var previousRide: Leg? = null
    var gapSincePreviousRide = false
    legs.forEachIndexed { index, leg ->
        when {
            leg.mode.isTransit -> {
                val before = previousRide
                if (before != null && !gapSincePreviousRide) {
                    val minutes = transferMinutes(before, leg)
                    item(key = "change-$index") { GapRow(stringResource(R.string.transfer_minutes, minutes), dashed = false) }
                }
                item(key = "leg-$index") { TransitLeg(leg, onFocus) }
                previousRide = leg
                gapSincePreviousRide = false
            }
            leg.mode == Mode.FOOTPATH -> {
                item(key = "leg-$index") { GapRow(stringResource(R.string.footpath_minutes, leg.durationMinutes), dashed = true) }
                gapSincePreviousRide = true
            }
            leg.mode == Mode.WAIT && leg.durationMinutes > 0 -> {
                item(key = "leg-$index") { GapRow(stringResource(R.string.wait_minutes, leg.durationMinutes), dashed = false) }
                gapSincePreviousRide = true
            }
            leg.mode == Mode.STAY_IN_VEHICLE -> {
                item(key = "leg-$index") { GapRow(stringResource(R.string.stay_in_vehicle), dashed = false) }
                gapSincePreviousRide = true
            }
            else -> Unit // stairs/elevators: implied by the footpath around them
        }
    }
}

/** Minutes between arriving with [from] and leaving with [to], live times preferred. */
private fun transferMinutes(from: Leg, to: Leg): Int {
    val arrival = from.destination?.arrival?.best ?: return 0
    val departure = to.origin?.departure?.best ?: return 0
    return java.time.Duration.between(arrival, departure).toMinutes().toInt().coerceAtLeast(0)
}

private val TimeColumn = 88.dp
private val RailColumn = 28.dp

/** One ride: origin stop, line + direction, intermediate stops (collapsible), destination stop. */
@Composable
private fun TransitLeg(leg: Leg, onFocus: (LatLng) -> Unit) {
    val colors = FahrplanTheme.colors
    val type = FahrplanTheme.type
    val lineColor = leg.mode.style().color
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(top = 8.dp)) {
        leg.origin?.let { stop -> StopLine(stop, stop.departure, lineColor, Edge.TOP, onFocus) }
        Rail(lineColor) {
            Column(Modifier.padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineBadge(leg.line ?: "?", leg.mode)
                    Spacer(Modifier.width(10.dp))
                    leg.direction?.let { Txt(it, style = type.bodyStrong, maxLines = 1) }
                }
                val between = leg.intermediateStops
                if (between.isNotEmpty()) {
                    Txt(
                        text = (if (between.size == 1) stringResource(R.string.stops_count_one) else stringResource(R.string.stops_count_many, between.size)) +
                            " · " + durationText(leg.durationMinutes) + if (expanded) "  ▴" else "  ▾",
                        style = type.caption,
                        color = colors.inkMuted,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(CircleShape)
                            .clickable { expanded = !expanded }
                            .padding(vertical = 4.dp),
                    )
                }
                leg.infos.forEach { info ->
                    Txt("• $info", style = type.caption, color = colors.inkFaint, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        if (expanded) {
            leg.intermediateStops.forEach { stop -> StopLine(stop, stop.departure ?: stop.arrival, lineColor, Edge.MIDDLE, onFocus, small = true) }
        }
        leg.destination?.let { stop -> StopLine(stop, stop.arrival, lineColor, Edge.BOTTOM, onFocus) }
    }
}

private enum class Edge { TOP, MIDDLE, BOTTOM }

@Composable
private fun StopLine(
    stop: RouteStop,
    time: TimePoint?,
    lineColor: Color,
    edge: Edge,
    onFocus: (LatLng) -> Unit,
    small: Boolean = false,
) {
    val colors = FahrplanTheme.colors
    val type = FahrplanTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(enabled = stop.location != null) { stop.location?.let(onFocus) }
            .padding(end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(TimeColumn).padding(start = 20.dp), verticalAlignment = Alignment.Bottom) {
            if (time != null) {
                Txt(time.scheduled.clock(), style = if (small) type.timeSmall else type.time, color = if (small) colors.inkMuted else colors.ink)
                delayLabel(time.delayMinutes)?.let {
                    Spacer(Modifier.width(3.dp))
                    Txt(it, style = type.timeSmall, color = if ((time.delayMinutes ?: 0) > 0) colors.delayed else colors.onTime)
                }
            }
        }
        Box(
            Modifier
                .width(RailColumn)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2
                    val top = if (edge == Edge.TOP) size.height / 2 else 0f
                    val bottom = if (edge == Edge.BOTTOM) size.height / 2 else size.height
                    drawLine(lineColor, Offset(x, top), Offset(x, bottom), strokeWidth = 5.dp.toPx())
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(if (small) 7.dp else 13.dp)
                    .clip(CircleShape)
                    .background(if (small) colors.background else colors.surface)
                    .drawBehind { drawCircle(colors.ink, radius = size.minDimension / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx())) },
            )
        }
        Column(Modifier.weight(1f).padding(vertical = if (small) 5.dp else 8.dp, horizontal = 8.dp)) {
            Txt(stop.name, style = if (small) type.caption else type.bodyStrong, maxLines = 1, color = if (small) colors.inkMuted else colors.ink)
            if (!small) {
                Txt(
                    listOfNotNull(stop.place.takeIf { it.isNotBlank() && it != "Dresden" }, stop.platform?.let { platformText(it) }).joinToString(" · "),
                    style = type.caption,
                    color = colors.inkMuted,
                )
            }
        }
    }
}

@Composable
private fun Rail(color: Color, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Spacer(Modifier.width(TimeColumn))
        Box(
            Modifier
                .width(RailColumn)
                .fillMaxHeight()
                .drawBehind { drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 5.dp.toPx()) },
        )
        Box(Modifier.weight(1f).padding(horizontal = 8.dp)) { content() }
    }
}

/** Walk / wait between two rides: a dotted rail with a short label. */
@Composable
private fun GapRow(text: String, dashed: Boolean) {
    val colors = FahrplanTheme.colors
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Spacer(Modifier.width(TimeColumn))
        Box(
            Modifier
                .width(RailColumn)
                .fillMaxHeight()
                .drawBehind {
                    drawLine(
                        colors.inkFaint,
                        Offset(size.width / 2, 0f),
                        Offset(size.width / 2, size.height),
                        strokeWidth = 3.dp.toPx(),
                        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(2f, 10f)) else null,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                },
        )
        Txt(text, style = FahrplanTheme.type.caption, color = colors.inkMuted, modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp))
    }
}

private fun shareText(context: Context, route: Route, origin: Stop, destination: Stop): String {
    val title = context.getString(R.string.share_title)
    return buildString {
        appendLine("$title: ${origin.name} → ${destination.name}")
        route.departure?.let { appendLine(it.scheduled.clockWithDay()) }
        appendLine()
        route.legs.forEach { leg ->
            when {
                leg.mode.isTransit -> {
                    val from = leg.origin
                    val to = leg.destination
                    appendLine("${from?.departure?.scheduled?.clock().orEmpty()}  ${from?.name.orEmpty()}")
                    appendLine("        ${leg.line.orEmpty()} → ${leg.direction.orEmpty()}")
                    appendLine("${to?.arrival?.scheduled?.clock().orEmpty()}  ${to?.name.orEmpty()}")
                }
                leg.mode == Mode.FOOTPATH -> appendLine("        " + context.getString(R.string.footpath_minutes, leg.durationMinutes))
                else -> Unit
            }
        }
    }.trim()
}

private fun share(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, null))
}
