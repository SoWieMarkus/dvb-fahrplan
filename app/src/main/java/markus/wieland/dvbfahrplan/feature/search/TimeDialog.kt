package markus.wieland.dvbfahrplan.feature.search

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import markus.wieland.dvbfahrplan.R
import markus.wieland.dvbfahrplan.core.designsystem.Chip
import markus.wieland.dvbfahrplan.core.designsystem.FahrplanTheme
import markus.wieland.dvbfahrplan.core.designsystem.Txt
import markus.wieland.dvbfahrplan.core.designsystem.Zone
import markus.wieland.dvbfahrplan.core.designsystem.clock
import markus.wieland.dvbfahrplan.core.designsystem.clockWithDay
import markus.wieland.dvbfahrplan.data.SearchTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
fun timeChipText(time: SearchTime): String = when {
    time.instant == null -> stringResource(R.string.time_now)
    time.isArrival -> stringResource(R.string.time_arrive_by, time.instant.clockWithDay())
    else -> stringResource(R.string.time_depart_at, time.instant.clockWithDay())
}

/** Small dialog: depart/arrive, day, and a time (system time picker). */
@Composable
fun TimeDialog(initial: SearchTime, onDismiss: () -> Unit, onApply: (SearchTime) -> Unit) {
    val colors = FahrplanTheme.colors
    val type = FahrplanTheme.type
    val context = LocalContext.current
    val start = (initial.instant ?: java.time.Instant.now()).atZone(Zone)
    var isArrival by remember { mutableStateOf(initial.isArrival) }
    var date by remember { mutableStateOf(start.toLocalDate()) }
    var time by remember { mutableStateOf(start.toLocalTime().withSecond(0).withNano(0)) }
    val today = LocalDate.now(Zone)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(colors.surface)
                .padding(20.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.time_mode_depart), selected = !isArrival, onClick = { isArrival = false })
                Chip(stringResource(R.string.time_mode_arrive), selected = isArrival, onClick = { isArrival = true })
            }
            Spacer(Modifier.height(18.dp))
            Txt(stringResource(R.string.time_pick_date).uppercase(), style = type.label, color = colors.inkMuted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0L, 1L, 2L).forEach { offset ->
                    val day = today.plusDays(offset)
                    val label = when (offset) {
                        0L -> stringResource(R.string.time_today)
                        1L -> stringResource(R.string.time_tomorrow)
                        else -> day.format(DateTimeFormatter.ofPattern("EE dd.MM."))
                    }
                    Chip(label, selected = date == day, onClick = { date = day })
                }
            }
            Spacer(Modifier.height(18.dp))
            Txt(stringResource(R.string.time_pick_time).uppercase(), style = type.label, color = colors.inkMuted)
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) {
                        TimePickerDialog(context, { _, h, m -> time = LocalTime.of(h, m) }, time.hour, time.minute, true).show()
                    }
                    .padding(vertical = 6.dp),
            ) {
                Txt(ZonedDateTime.of(date, time, Zone).toInstant().clock(), style = type.timeLarge.copy(fontSize = type.title.fontSize))
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Txt(
                    stringResource(R.string.time_now),
                    style = type.bodyStrong,
                    color = colors.inkMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { onApply(SearchTime()) }
                        .padding(8.dp),
                )
                Txt(
                    stringResource(R.string.time_apply),
                    style = type.bodyStrong,
                    color = colors.background,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.ink)
                        .clickable(role = Role.Button) {
                            onApply(SearchTime(ZonedDateTime.of(date, time, Zone).toInstant(), isArrival))
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}
