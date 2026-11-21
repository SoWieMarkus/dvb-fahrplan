package markus.wieland.dvbfahrplan.feature.disruptions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import markus.wieland.dvbfahrplan.R
import markus.wieland.dvbfahrplan.core.designsystem.FahrplanTheme
import markus.wieland.dvbfahrplan.core.designsystem.Hairline
import markus.wieland.dvbfahrplan.core.designsystem.IconButton
import markus.wieland.dvbfahrplan.core.designsystem.LineBadge
import markus.wieland.dvbfahrplan.core.designsystem.MessageBlock
import markus.wieland.dvbfahrplan.core.designsystem.Txt
import markus.wieland.dvbfahrplan.core.model.Disruption
import markus.wieland.dvbfahrplan.core.model.Load
import markus.wieland.dvbfahrplan.data.TransitRepository
import markus.wieland.dvbfahrplan.data.poll
import markus.wieland.dvbfahrplan.feature.search.ErrorBlock
import markus.wieland.dvbfahrplan.feature.search.SkeletonRows
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

@HiltViewModel
class DisruptionsViewModel @Inject constructor(transit: TransitRepository, clock: Clock) : ViewModel() {
    /** All service changes the API knows, including ones not active right now (a departure may point to those). */
    val disruptions: StateFlow<Load<List<Disruption>>> = poll(5.minutes, clock) { transit.disruptions() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)
}

/**
 * @param ids show only these service changes (e.g. the ones referenced by the departures of a stop);
 *   null = all that are active now.
 * @param subtitle what the filtered list is about, e.g. the stop name.
 */

@Composable
fun DisruptionsScreen(viewModel: DisruptionsViewModel, ids: List<String>?, subtitle: String?, onBack: () -> Unit) {
    val load by viewModel.disruptions.collectAsStateWithLifecycle()
    val colors = FahrplanTheme.colors
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        item {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(R.drawable.ic_back, stringResource(R.string.action_back), onBack)
                Spacer(Modifier.width(4.dp))
                Column {
                    if (subtitle != null) {
                        Txt(subtitle.uppercase(), style = FahrplanTheme.type.label, color = colors.inkMuted)
                    }
                    Txt(stringResource(R.string.section_disruptions), style = FahrplanTheme.type.title)
                }
            }
        }
        when (val current = load) {
            is Load.Data -> {
                val now = Instant.now()
                val shown = if (ids == null) current.value.filter { it.isActive(now) } else current.value.filter { it.id in ids }
                if (shown.isEmpty()) {
                    item { MessageBlock(stringResource(R.string.disruptions_none)) }
                }
                // A single message (tapped warning sign) opens right away.
                items(shown, key = { it.id }) { DisruptionItem(it, initiallyExpanded = shown.size == 1) }
            }
            is Load.Failed -> item { ErrorBlock(current.kind, onRetry = {}) }
            else -> item { SkeletonRows(6) }
        }
        item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
    }
}

@Composable
private fun DisruptionItem(disruption: Disruption, initiallyExpanded: Boolean) {
    val colors = FahrplanTheme.colors
    var expanded by rememberSaveable(disruption.id) { mutableStateOf(initiallyExpanded) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (disruption.lines.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                disruption.lines.take(12).forEach { line -> LineBadge(line.name, line.mode, compact = true) }
            }
            Spacer(Modifier.height(8.dp))
        }
        Txt(disruption.title, style = FahrplanTheme.type.bodyStrong, maxLines = if (expanded) Int.MAX_VALUE else 2)
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            BasicText(
                AnnotatedString.fromHtml(disruption.descriptionHtml),
                style = FahrplanTheme.type.caption.copy(color = colors.inkMuted),
            )
        }
    }
    Hairline()
}

