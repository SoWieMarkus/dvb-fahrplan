package markus.wieland.dvbfahrplan.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import markus.wieland.dvbfahrplan.core.model.Mode

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = FahrplanTheme.type.body,
    color: Color = FahrplanTheme.colors.ink,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
    strike: Boolean = false,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(
            color = color,
            textAlign = align ?: style.textAlign,
            textDecoration = if (strike) TextDecoration.LineThrough else null,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Icon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = FahrplanTheme.colors.ink,
    size: Dp = 22.dp,
) {
    Image(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/** A round, quiet icon button. */
@Composable
fun IconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = FahrplanTheme.colors.ink,
    background: Color = Color.Transparent,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint)
    }
}

// ---- Line colors and badges ----

data class ModeStyle(val color: Color, val onColor: Color, val shape: Shape)

private val SquareShape = RoundedCornerShape(3.dp)
private val PillShape = RoundedCornerShape(50)

fun Mode.style(): ModeStyle = when (this) {
    Mode.TRAM -> ModeStyle(Color(0xFFDD0B30), Color.White, SquareShape)
    Mode.BUS -> ModeStyle(Color(0xFF005D79), Color.White, PillShape)
    Mode.REGIONAL_BUS -> ModeStyle(Color(0xFF6A2C70), Color.White, PillShape)
    Mode.SUBURBAN -> ModeStyle(Color(0xFF00884A), Color.White, PillShape)
    Mode.TRAIN -> ModeStyle(Color(0xFF3E4650), Color.White, SquareShape)
    Mode.FERRY -> ModeStyle(Color(0xFF0090C8), Color.White, PillShape)
    Mode.CABLEWAY -> ModeStyle(Color(0xFF5F8C1F), Color.White, SquareShape)
    Mode.ON_DEMAND -> ModeStyle(Color(0xFFFFD000), Color(0xFF17191B), PillShape)
    else -> ModeStyle(Color(0xFF8C8880), Color.White, PillShape)
}

/** Line number on its line color. Trams and trains get square badges, buses round ones – as on the stop signs. */
@Composable
fun LineBadge(line: String, mode: Mode, modifier: Modifier = Modifier, compact: Boolean = false) {
    val style = mode.style()
    Box(
        modifier = modifier
            .widthIn(min = if (compact) 30.dp else 40.dp)
            .clip(style.shape)
            .background(style.color)
            .padding(horizontal = if (compact) 5.dp else 7.dp, vertical = if (compact) 2.dp else 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(
            text = line,
            style = if (compact) FahrplanTheme.type.badge.copy(fontSize = FahrplanTheme.type.caption.fontSize) else FahrplanTheme.type.badge,
            color = style.onColor,
            maxLines = 1,
            align = TextAlign.Center,
        )
    }
}

// ---- Layout pieces ----

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(text.uppercase(), style = FahrplanTheme.type.label, color = FahrplanTheme.colors.inkMuted, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier, start: Dp = 20.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = start)
            .height(1.dp)
            .background(FahrplanTheme.colors.line),
    )
}

/** Selectable chip used for filters and the time picker. */
@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
) {
    val colors = FahrplanTheme.colors
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(if (selected) colors.ink else Color.Transparent)
            .border(1.dp, if (selected) colors.ink else colors.line, PillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Txt(text, style = FahrplanTheme.type.caption, color = if (selected) colors.background else colors.ink, maxLines = 1)
    }
}

/** Big flat button, e.g. "Earlier connections". While [loading] it shows pulsing dots instead of its text. */
@Composable
fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp)
            .alpha(if (enabled || loading) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        // Keep the text laid out (invisible) while loading so the button does not change its height.
        Txt(text, style = FahrplanTheme.type.bodyStrong, color = FahrplanTheme.colors.ink, modifier = Modifier.alpha(if (loading) 0f else 1f))
        if (loading) LoadingDots()
    }
}

/** Three dots pulsing one after another. */
@Composable
fun LoadingDots(modifier: Modifier = Modifier, color: Color = FahrplanTheme.colors.ink) {
    val transition = rememberInfiniteTransition(label = "dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1050, easing = LinearEasing)),
        label = "dotsPhase",
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            // Each dot is brightest when the phase passes its index.
            val distance = kotlin.math.abs(((phase - index + 1.5f).mod(3f)) - 1.5f)
            val strength = (1f - distance).coerceIn(0f, 1f)
            Box(
                Modifier
                    .size(7.dp)
                    .alpha(0.25f + 0.75f * strength)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** Pulsing dot that marks live (real time) data. */
@Composable
fun LiveDot(modifier: Modifier = Modifier, color: Color = FahrplanTheme.colors.onTime) {
    val transition = rememberInfiniteTransition(label = "live")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "liveAlpha",
    )
    Box(
        modifier
            .size(8.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color),
    )
}

/** Placeholder while loading: a soft sweep over a sunken block. */
@Composable
fun SkeletonBlock(width: Dp, height: Dp, modifier: Modifier = Modifier) {
    val colors = FahrplanTheme.colors
    val transition = rememberInfiniteTransition(label = "skeleton")
    val offset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "skeletonOffset",
    )
    Box(
        modifier
            .size(width, height)
            .clip(RoundedCornerShape(4.dp))
            .background(colors.surfaceSunken)
            .drawWithContent {
                val w = size.width
                drawRect(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, colors.surface.copy(alpha = 0.55f), Color.Transparent),
                        startX = w * offset - w * 0.5f,
                        endX = w * offset + w * 0.5f,
                    ),
                )
            },
    )
}

@Composable
fun MessageBlock(
    title: String,
    body: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Txt(title, style = FahrplanTheme.type.heading)
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Txt(body, style = FahrplanTheme.type.body, color = FahrplanTheme.colors.inkMuted)
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .clip(PillShape)
                    .background(FahrplanTheme.colors.ink)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            ) {
                Txt(action, style = FahrplanTheme.type.bodyStrong, color = FahrplanTheme.colors.background)
            }
        }
    }
}
