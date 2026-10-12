package com.filmera.app.core.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.filmera.app.core.filter.FilterDefinition
import com.filmera.app.core.ui.theme.FilmeraColors

/** A filter's round chip: its gradient swatch, a ring when selected, and its name. */
@Composable
fun FilterChip(
    filter: FilterDefinition,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(if (selected) 1.08f else 1f, spring(dampingRatio = 0.55f), label = "scale")
    val ring by animateDpAsState(if (selected) 2.5.dp else 0.dp, label = "ring")
    Column(
        modifier
            .width(68.dp)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .scale(scale)
                .size(58.dp)
                .border(ring, FilmeraColors.Emerald, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(filter.swatch.first), Color(filter.swatch.second)))),
                contentAlignment = Alignment.Center,
            ) {
                if (filter.requiresFaceTracking) {
                    Icon(Icons.Rounded.Face, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            filter.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) FilmeraColors.Emerald else Color.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}
