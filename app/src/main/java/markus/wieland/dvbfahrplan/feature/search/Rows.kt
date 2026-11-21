package markus.wieland.dvbfahrplan.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import markus.wieland.dvbfahrplan.R
import markus.wieland.dvbfahrplan.core.designsystem.FahrplanTheme
import markus.wieland.dvbfahrplan.core.designsystem.Icon
import markus.wieland.dvbfahrplan.core.designsystem.IconButton
import markus.wieland.dvbfahrplan.core.designsystem.LineBadge
import markus.wieland.dvbfahrplan.core.designsystem.SkeletonBlock
import markus.wieland.dvbfahrplan.core.designsystem.Txt
import markus.wieland.dvbfahrplan.core.designsystem.clock
import markus.wieland.dvbfahrplan.core.designsystem.delayLabel
import markus.wieland.dvbfahrplan.core.designsystem.durationText
import markus.wieland.dvbfahrplan.core.designsystem.interchangesText
import markus.wieland.dvbfahrplan.core.designsystem.minutesFrom
import markus.wieland.dvbfahrplan.core.designsystem.platformText
import markus.wieland.dvbfahrplan.core.model.Departure
import markus.wieland.dvbfahrplan.core.model.Leg
import markus.wieland.dvbfahrplan.core.model.Mode
import markus.wieland.dvbfahrplan.core.model.Route
import markus.wieland.dvbfahrplan.core.model.Stop
import markus.wieland.dvbfahrplan.core.model.TimePoint
import java.time.Instant

/** Current time that ticks every [periodMillis], for countdowns. */
@Composable
fun rememberNow(periodMillis: Long = 10_000): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(periodMillis)
            now = Instant.now()
        }
    }
    return now
}

@Composable
fun StopRow(
    stop: Stop,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val colors = FahrplanTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(stop.name, style = FahrplanTheme.type.bodyStrong, maxLines = 1)
            Txt(stop.displayPlace, style = FahrplanTheme.type.caption, color = colors.inkMuted, maxLines = 1)
        }
        IconButton(
            icon = if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_outline,
            contentDescription = stringResource(if (isFavorite) R.string.action_unfavorite else R.string.action_favorite),
            onClick = onToggleFavorite,
            tint = if (isFavorite) colors.accent else colors.inkFaint,
        )
    }
}

@Composable
fun DepartureRow(departure: Departure, now: Instant, onDisruptionClick: () -> Unit, onClick: () -> Unit) {
    val colors = FahrplanTheme.colors
    val type = FahrplanTheme.type
    val time = departure.time
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineBadge(departure.line, departure.mode, Modifier.widthIn(min = 52.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(
                    departure.direction,
                    style = type.bodyStrong,
                    maxLines = 1,
                    strike = time.isCancelled,
                    color = if (time.isCancelled) colors.inkMuted else colors.ink,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (departure.changeIds.isNotEmpty()) {
                    // Own (larger) touch target: opens the service change instead of the trip.
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onDisruptionClick)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Icon(R.drawable.ic_warning, stringResource(R.string.section_disruptions), tint = colors.inkFaint, size = 16.dp)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                departure.platform?.let { Txt(platformText(it), style = type.caption, color = colors.inkMuted) }
                ScheduledTime(time)
            }
        }
        Spacer(Modifier.width(10.dp))
        Countdown(time, now)
    }
}

/** "14:05" plus the delay ("+3") in the delay color. */
@Composable
private fun ScheduledTime(time: TimePoint) {
    val colors = FahrplanTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Txt(time.scheduled.clock(), style = FahrplanTheme.type.timeSmall, color = colors.inkMuted)
        delayLabel(time.delayMinutes)?.let { label ->
            Txt(label, style = FahrplanTheme.type.timeSmall, color = if ((time.delayMinutes ?: 0) > 0) colors.delayed else colors.onTime)
        }
    }
}

@Composable
private fun Countdown(time: TimePoint, now: Instant) {
    val colors = FahrplanTheme.colors
    val type = FahrplanTheme.type
    Column(Modifier.widthIn(min = 56.dp), horizontalAlignment = Alignment.End) {
        if (time.isCancelled) {
            Txt(stringResource(R.string.cancelled), style = type.caption, color = colors.cancelled, align = TextAlign.End)
            return@Column
        }
        val minutes = time.best.minutesFrom(now)
        // Calm board: the countdown is always ink; punctuality shows only in the small "+3" next to the time.
        val color = colors.ink
        if (minutes == 0L) {
            Txt(stringResource(R.string.now_short), style = type.timeLarge, color = color)
        } else if (minutes < 60) {
            Row(verticalAlignment = Alignment.Bottom) {
                Txt(minutes.toString(), style = type.timeLarge, color = color)
                Spacer(Modifier.width(2.dp))
                Txt("min", style = type.timeSmall, color = color, modifier = Modifier.padding(bottom = 3.dp))
            }
        } else {
            Txt(time.best.clock(), style = type.timeLarge, color = color)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteRow(route: Route, onClick: () -> Unit) {
    val colors = FahrplanTheme.colors
    val type = FahrplanTheme.type
    val departure = route.departure
    val arrival = route.arrival
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                departure?.let { ClockWithDelay(it, route.isCancelled) }
                Txt("  –  ", style = type.time, color = colors.inkFaint)
                arrival?.let { ClockWithDelay(it, route.isCancelled) }
            }
            Txt(durationText(route.durationMinutes), style = type.bodyStrong)
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            route.legs.filter { it.mode.isTransit || it.mode == Mode.FOOTPATH }.forEachIndexed { index, leg ->
                if (index > 0) Txt("›", style = type.caption, color = colors.inkFaint)
                LegChip(leg)
            }
        }
        Spacer(Modifier.height(8.dp))
        val firstPlatform = route.legs.firstOrNull { it.mode.isTransit }?.origin?.platform
        Txt(
            listOfNotNull(
                interchangesText(route.interchanges),
                firstPlatform?.let { platformText(it) },
                if (route.isCancelled) stringResource(R.string.cancelled) else null,
            ).joinToString(" · "),
            style = type.caption,
            color = if (route.isCancelled) colors.cancelled else colors.inkMuted,
        )
    }
}

@Composable
fun ClockWithDelay(time: TimePoint, cancelled: Boolean = false) {
    val colors = FahrplanTheme.colors
    val type = FahrplanTheme.type
    Row(verticalAlignment = Alignment.Top) {
        Txt(time.scheduled.clock(), style = type.timeLarge, strike = cancelled || time.isCancelled)
        delayLabel(time.delayMinutes)?.let {
            Txt(it, style = type.timeSmall, color = if ((time.delayMinutes ?: 0) > 0) colors.delayed else colors.onTime)
        }
    }
}

@Composable
private fun LegChip(leg: Leg) {
    val colors = FahrplanTheme.colors
    if (leg.mode == Mode.FOOTPATH) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(R.drawable.ic_transport_footpath, null, tint = colors.inkMuted, size = 16.dp)
            Txt("${leg.durationMinutes}", style = FahrplanTheme.type.timeSmall, color = colors.inkMuted)
        }
    } else {
        LineBadge(leg.line ?: "?", leg.mode, compact = true)
    }
}

@Composable
fun SkeletonRows(count: Int = 6) {
    Column {
        repeat(count) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBlock(52.dp, 24.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBlock(160.dp, 14.dp)
                    Spacer(Modifier.height(6.dp))
                    SkeletonBlock(90.dp, 10.dp)
                }
                SkeletonBlock(36.dp, 22.dp)
            }
        }
    }
}

/** Placeholder shaped like a [RouteRow]: two times, a duration, a few line badges. */
@Composable
fun SkeletonRouteRows(count: Int = 2, modifier: Modifier = Modifier) {
    Column(modifier) {
        repeat(count) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBlock(150.dp, 24.dp)
                    Spacer(Modifier.weight(1f))
                    SkeletonBlock(52.dp, 18.dp)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBlock(34.dp, 20.dp)
                    SkeletonBlock(34.dp, 20.dp)
                }
                Spacer(Modifier.height(10.dp))
                SkeletonBlock(120.dp, 12.dp)
            }
        }
    }
}
