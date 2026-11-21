package markus.wieland.dvbfahrplan.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import markus.wieland.dvbfahrplan.R
import markus.wieland.dvbfahrplan.core.designsystem.FahrplanTheme
import markus.wieland.dvbfahrplan.core.designsystem.IconButton
import markus.wieland.dvbfahrplan.core.model.LatLng
import markus.wieland.dvbfahrplan.core.model.MapSegment
import markus.wieland.dvbfahrplan.feature.map.MapStop
import markus.wieland.dvbfahrplan.feature.map.TransitMap

/** Map on top, scrolling content below, floating back (and optional action) button. */
@Composable
fun DetailScaffold(
    segments: List<MapSegment>,
    stops: List<MapStop>,
    focus: LatLng?,
    onBack: () -> Unit,
    action: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val colors = FahrplanTheme.colors
    var mapTouched by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        LazyColumn(Modifier.fillMaxSize(), userScrollEnabled = !mapTouched) {
            item(key = "map") {
                TransitMap(
                    segments = segments,
                    stops = stops,
                    focus = focus,
                    onTouch = { mapTouched = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                )
            }
            content()
            item(key = "bottom") { Spacer(Modifier.navigationBarsPadding().height(32.dp)) }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingButton { IconButton(R.drawable.ic_back, stringResource(R.string.action_back), onBack) }
            Spacer(Modifier.weight(1f))
            if (action != null) FloatingButton { action() }
        }
    }
}

@Composable
private fun FloatingButton(content: @Composable () -> Unit) {
    Box(
        Modifier
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(FahrplanTheme.colors.surface),
    ) { content() }
}

