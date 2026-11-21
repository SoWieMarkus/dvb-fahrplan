package markus.wieland.dvbfahrplan.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import markus.wieland.dvbfahrplan.R
import markus.wieland.dvbfahrplan.core.model.Platform
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val Zone: ZoneId = ZoneId.of("Europe/Berlin")
private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dayFormat = DateTimeFormatter.ofPattern("EE dd.MM.")

fun Instant.clock(): String = atZone(Zone).format(clockFormat)

/** "14:05", or "Sa 22.11. 14:05" when not today. */
fun Instant.clockWithDay(today: LocalDate = LocalDate.now(Zone)): String {
    val zoned = atZone(Zone)
    return if (zoned.toLocalDate() == today) zoned.format(clockFormat) else zoned.format(dayFormat) + " " + zoned.format(clockFormat)
}

/** Whole minutes from [now] until this instant, never negative. */
fun Instant.minutesFrom(now: Instant): Long = Duration.between(now, this).toMinutes().coerceAtLeast(0)

@Composable
fun durationText(minutes: Int): String =
    if (minutes < 60) stringResource(R.string.duration_minutes, minutes)
    else stringResource(R.string.duration_hours, minutes / 60, minutes % 60)

@Composable
fun platformText(platform: Platform): String =
    stringResource(if (platform.isTrack) R.string.track else R.string.platform, platform.name)

@Composable
fun interchangesText(count: Int): String = when (count) {
    0 -> stringResource(R.string.interchanges_none)
    1 -> stringResource(R.string.interchanges_one)
    else -> stringResource(R.string.interchanges_many, count)
}

/** "+3", "-1" or null when on time / unknown. */
fun delayLabel(minutes: Int?): String? = when {
    minutes == null || minutes == 0 -> null
    minutes > 0 -> "+$minutes"
    else -> "$minutes"
}
