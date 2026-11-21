package markus.wieland.dvbfahrplan.feature.detail

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import markus.wieland.dvbfahrplan.R
import markus.wieland.dvbfahrplan.core.designsystem.FahrplanTheme
import markus.wieland.dvbfahrplan.core.designsystem.LineBadge
import markus.wieland.dvbfahrplan.core.designsystem.LiveDot
import markus.wieland.dvbfahrplan.core.designsystem.Txt
import markus.wieland.dvbfahrplan.core.designsystem.clock
import markus.wieland.dvbfahrplan.core.designsystem.delayLabel
import markus.wieland.dvbfahrplan.core.designsystem.platformText
import markus.wieland.dvbfahrplan.core.designsystem.style
import markus.wieland.dvbfahrplan.core.model.Departure
import markus.wieland.dvbfahrplan.core.model.LatLng
import markus.wieland.dvbfahrplan.core.model.Load
import markus.wieland.dvbfahrplan.core.model.Mode
import markus.wieland.dvbfahrplan.core.model.Stop
import markus.wieland.dvbfahrplan.core.model.Trip
import markus.wieland.dvbfahrplan.core.model.TripPosition
import markus.wieland.dvbfahrplan.core.model.TripStop
import markus.wieland.dvbfahrplan.data.TransitRepository
import markus.wieland.dvbfahrplan.data.poll
import markus.wieland.dvbfahrplan.feature.map.MapStop
import markus.wieland.dvbfahrplan.feature.search.ErrorBlock
import markus.wieland.dvbfahrplan.feature.search.SkeletonRows
import java.time.Clock
import kotlin.time.Duration.Companion.seconds

@HiltViewModel(assistedFactory = TripViewModel.Factory::class)
class TripViewModel @AssistedInject constructor(
    @Assisted departure: Departure,
    @Assisted stop: Stop,
    transit: TransitRepository,
    clock: Clock,
) : ViewModel() {

    /** The run of this vehicle, refreshed every 30 s while visible (the live position moves along). */
    val trip: StateFlow<Load<Trip>> = poll(30.seconds, clock) { transit.trip(departure, stop.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)

    @AssistedFactory
    interface Factory {
        fun create(departure: Departure, stop: Stop): TripViewModel
    }
}

@Composable
fun TripScreen(viewModel: TripViewModel, departure: Departure, stop: Stop, onBack: () -> Unit) {
    val load by viewModel.trip.collectAsStateWithLifecycle()
    var focus by remember { mutableStateOf<LatLng?>(null) }
    val trip = load.valueOrNull
    // dm/trip labels its map data "Undefined" – color it like the line we are looking at.
    val segments = trip?.path.orEmpty().map { if (it.mode == Mode.UNKNOWN) it.copy(mode = departure.mode) else it }
    val mapStops = trip?.stops.orEmpty().mapNotNull { s ->
        s.location?.let { MapStop(it, if (s.id == stop.id || s.position == TripPosition.CURRENT) 2 else 0) }
    }

    DetailScaffold(segments = segments, stops = mapStops, focus = focus, onBack = onBack) {
        val current = load
        item(key = "header") { TripHeader(departure, live = current is Load.Data) }
        when (current) {
            is Load.Data -> itemsIndexed(current.value.stops, key = { _, it -> "${it.id}-${it.time.scheduled}" }) { index, tripStop ->
                TripStopRow(
                    stop = tripStop,
                    isSelected = tripStop.id == stop.id,
                    color = departure.mode.style().color,
                    isFirst = index == 0,
                    isLast = index == current.value.stops.lastIndex,
                ) {
                    tripStop.location?.let { focus = it }
                }
            }
            is Load.Failed -> item { ErrorBlock(current.kind, onRetry = {}) }
            else -> item { SkeletonRows(8) }
        }
    }
}

@Composable
private fun TripHeader(departure: Departure, live: Boolean) {
    val colors = FahrplanTheme.colors
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineBadge(departure.line, departure.mode)
            Spacer(Modifier.width(10.dp))
            Txt(stringResource(R.string.direction, departure.direction), style = FahrplanTheme.type.heading, maxLines = 2, modifier = Modifier.weight(1f))
        }
        if (live) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                LiveDot()
                Spacer(Modifier.width(6.dp))
                Txt(stringResource(R.string.live).uppercase(), style = FahrplanTheme.type.label, color = colors.onTime)
            }
        }
    }
}

@Composable
private fun TripStopRow(
    stop: TripStop,
    isSelected: Boolean,
    color: androidx.compose.ui.graphics.Color,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val colors = FahrplanTheme.colors
    val type = FahrplanTheme.type
    val passed = stop.position == TripPosition.PREVIOUS
    val current = stop.position == TripPosition.CURRENT
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .background(if (isSelected) colors.surface else androidx.compose.ui.graphics.Color.Transparent)
            .padding(end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(88.dp).padding(start = 20.dp), verticalAlignment = Alignment.Bottom) {
            Txt(stop.time.scheduled.clock(), style = type.time, color = if (passed) colors.inkFaint else colors.ink, strike = stop.time.isCancelled)
            delayLabel(stop.time.delayMinutes)?.let {
                Spacer(Modifier.width(3.dp))
                Txt(it, style = type.timeSmall, color = if ((stop.time.delayMinutes ?: 0) > 0) colors.delayed else colors.onTime)
            }
        }
        Box(
            Modifier
                .width(28.dp)
                .fillMaxHeight()
                .drawBehind {
                    // The rail starts at the first stop and ends at the last one. The half above a stop
                    // is already driven once the vehicle is here, the half below once it has left.
                    val x = size.width / 2
                    val mid = size.height / 2
                    val stroke = 5.dp.toPx()
                    if (!isFirst) {
                        drawLine(if (passed || current) colors.inkFaint else color, Offset(x, 0f), Offset(x, mid), stroke)
                    }
                    if (!isLast) {
                        drawLine(if (passed) colors.inkFaint else color, Offset(x, mid), Offset(x, size.height), stroke)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(if (current || isSelected) 14.dp else 9.dp)
                    .clip(CircleShape)
                    .background(if (current) colors.ink else colors.surface)
                    .drawBehind { drawCircle(colors.ink, radius = size.minDimension / 2, style = Stroke(2.dp.toPx())) },
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 9.dp)) {
            Txt(
                // dm/trip prefixes stops inside Dresden with the city name ("Dresden Albertplatz").
                stop.name.removePrefix("Dresden ").ifBlank { stop.name },
                style = if (isSelected || current) type.bodyStrong else type.body,
                color = if (passed) colors.inkFaint else colors.ink,
                maxLines = 1,
            )
            stop.platform?.takeIf { isSelected }?.let {
                Txt(platformText(it), style = type.caption, color = colors.inkMuted)
            }
        }
    }
}
