package me.pj.jqb.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.pj.jqb.model.ActionType
import me.pj.jqb.ui.isEnglishLocale

private val ALL_ACTIONS = listOf(
    ActionType.DIRECT,
    ActionType.SMART,
    ActionType.SPOOF,
    ActionType.BLOCK
)

@Composable
fun ActionPillSelector(
    selectedAction: ActionType,
    onActionSelected: (ActionType) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ALL_ACTIONS.forEach { action ->
            val isSelected = action == selectedAction

            val targetBgColor = when {
                !isSelected -> Color.Transparent
                action == ActionType.BLOCK -> MaterialTheme.colorScheme.errorContainer
                action == ActionType.SPOOF -> MaterialTheme.colorScheme.tertiaryContainer
                action == ActionType.SMART -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            }

            val targetTextColor = when {
                !isSelected -> MaterialTheme.colorScheme.onSurfaceVariant
                action == ActionType.BLOCK -> MaterialTheme.colorScheme.onErrorContainer
                action == ActionType.SPOOF -> MaterialTheme.colorScheme.onTertiaryContainer
                action == ActionType.SMART -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSecondaryContainer
            }

            val bgColor by animateColorAsState(
                targetValue = targetBgColor,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "PillBgColor"
            )
            val textColor by animateColorAsState(
                targetValue = targetTextColor,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "PillTextColor"
            )
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.96f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "PillScale"
            )

            val icon = when (action) {
                ActionType.DIRECT -> Icons.Outlined.LockOpen
                ActionType.SMART -> Icons.Outlined.AutoAwesome
                ActionType.SPOOF -> Icons.Outlined.Shuffle
                ActionType.BLOCK -> Icons.Outlined.Block
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(7.dp))
                    .background(bgColor)
                    .clickable {
                        if (!isSelected) {
                            view.tickHaptic()
                            onActionSelected(action)
                        }
                    }
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = textColor
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    val isEn = isEnglishLocale()
                    Text(
                        text = action.label(isEn),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
