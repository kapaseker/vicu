package com.rockbyte.vicu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.rockbyte.vicu.ui.theme.VicuTheme

/**
 * 单选选项组卡片：标题 + 竖排单选项（card 视觉），
 * 选项行带单选语义（Role.RadioButton）与选中态指示环。
 */
@Composable
fun <T> RadioOptionGroup(
    label: String,
    options: List<T>,
    selected: T,
    enabled: Boolean,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    val cardState = remember { MutableStyleState(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .styleable(cardState, VicuTheme.styles.card),
        verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
    ) {
        BasicText(label, style = VicuTheme.typography.caption)
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit / 2),
        ) {
            options.forEach { option ->
                RadioOption(
                    label = optionLabel(option),
                    selected = option == selected,
                    enabled = enabled,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun RadioOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val indicatorColor = if (selected) VicuTheme.colors.primary else VicuTheme.colors.outline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VicuTheme.shapes.base)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .padding(
                horizontal = VicuTheme.dimensions.spacingUnit,
                vertical = VicuTheme.dimensions.radioItemVerticalPadding,
            )
            .alpha(if (enabled) VicuTheme.alpha.full else VicuTheme.alpha.disabled),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
    ) {
        Box(
            modifier = Modifier
                .size(VicuTheme.dimensions.radioOuterSize)
                .border(VicuTheme.dimensions.radioBorderWidth, indicatorColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(VicuTheme.dimensions.radioInnerSize)
                        .clip(CircleShape)
                        .background(VicuTheme.colors.primary)
                )
            }
        }
        BasicText(label, style = VicuTheme.typography.bodyLg)
    }
}
