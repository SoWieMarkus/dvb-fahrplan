package markus.wieland.dvbfahrplan.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import markus.wieland.dvbfahrplan.R
import markus.wieland.dvbfahrplan.core.designsystem.FahrplanTheme
import markus.wieland.dvbfahrplan.core.designsystem.Hairline
import markus.wieland.dvbfahrplan.core.designsystem.IconButton
import markus.wieland.dvbfahrplan.core.designsystem.Txt

/**
 * The two stacked inputs "From" / "To" with a route rail on the left – the single entry point of the app.
 * Only "From" filled → departure board; both → connections.
 */
@Composable
fun SearchInput(
    state: SearchUiState,
    onFocus: (Field) -> Unit,
    onTextChange: (Field, String) -> Unit,
    onClear: (Field) -> Unit,
    onSubmit: () -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FahrplanTheme.colors
    val originFocus = remember { FocusRequester() }
    val destinationFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // The ViewModel owns the focus; mirror it into Compose.
    LaunchedEffect(state.focused) {
        when (state.focused) {
            Field.ORIGIN -> originFocus.requestFocus()
            Field.DESTINATION -> destinationFocus.requestFocus()
            null -> focusManager.clearFocus()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(18.dp))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RouteRail(Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            StopField(
                text = state.originText,
                placeholder = stringResource(R.string.field_origin),
                focused = state.focused == Field.ORIGIN,
                focusRequester = originFocus,
                onFocus = { onFocus(Field.ORIGIN) },
                onTextChange = { onTextChange(Field.ORIGIN, it) },
                onClear = { onClear(Field.ORIGIN) },
                onSubmit = onSubmit,
            )
            Hairline(start = 0.dp)
            StopField(
                text = state.destinationText,
                placeholder = stringResource(if (state.origin != null) R.string.field_destination_hint else R.string.field_destination),
                focused = state.focused == Field.DESTINATION,
                focusRequester = destinationFocus,
                onFocus = { onFocus(Field.DESTINATION) },
                onTextChange = { onTextChange(Field.DESTINATION, it) },
                onClear = { onClear(Field.DESTINATION) },
                onSubmit = onSubmit,
                clearAlways = state.destination != null,
            )
        }
        IconButton(
            icon = R.drawable.ic_switch,
            contentDescription = stringResource(R.string.action_swap),
            onClick = onSwap,
            tint = colors.inkMuted,
        )
    }
}

/** Hollow ring (start) – dotted line – solid square (destination). */
@Composable
private fun RouteRail(modifier: Modifier = Modifier) {
    val colors = FahrplanTheme.colors
    Column(modifier.width(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(11.dp)
                .border(2.5.dp, colors.ink, CircleShape),
        )
        Box(
            Modifier
                .width(2.dp)
                .height(36.dp)
                .drawBehind {
                    drawLine(
                        color = colors.inkFaint,
                        start = Offset(size.width / 2, 4f),
                        end = Offset(size.width / 2, size.height - 4f),
                        strokeWidth = size.width,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 7f)),
                    )
                },
        )
        Box(
            Modifier
                .size(11.dp)
                .background(colors.ink, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun StopField(
    text: String,
    placeholder: String,
    focused: Boolean,
    focusRequester: FocusRequester,
    onFocus: () -> Unit,
    onTextChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    clearAlways: Boolean = false,
) {
    val colors = FahrplanTheme.colors
    val type = FahrplanTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            textStyle = type.bodyStrong.copy(color = colors.ink, fontSize = type.heading.fontSize),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused && !focused) onFocus() },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Txt(placeholder, style = type.body.copy(fontSize = type.heading.fontSize), color = colors.inkFaint, maxLines = 1)
                    }
                    inner()
                }
            },
        )
        if (text.isNotEmpty() && (focused || clearAlways)) {
            IconButton(
                icon = R.drawable.ic_close,
                contentDescription = stringResource(R.string.action_clear),
                onClick = onClear,
                tint = colors.inkFaint,
            )
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}
