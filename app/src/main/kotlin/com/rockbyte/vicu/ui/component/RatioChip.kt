package com.rockbyte.vicu.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.style.then
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.rockbyte.vicu.ui.theme.VicuTheme
import com.rockbyte.vicu.ui.theme.colors

/** 预制比例 chip 行：FlowRow 自动换行，单选语义。 */
@Composable
internal fun RatioChipRow(
    selected: ScaleRatioPreset,
    flipped: Boolean,
    enabled: Boolean,
    onSelect: (ScaleRatioPreset) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup()
            .alpha(if (enabled) VicuTheme.alpha.full else VicuTheme.alpha.disabled),
        horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
        verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
    ) {
        ScaleRatioPreset.entries.forEach { preset ->
            RatioChip(
                label = stringResource(preset.labelRes(preset == selected && flipped)),
                selected = preset == selected,
                enabled = enabled,
                onClick = { onSelect(preset) },
            )
        }
    }
}

/** 比例选项 chip：pill 底 + 单选语义，选中黑底白字、未选中 panel 底。 */
@Composable
internal fun RatioChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val chipState = remember { MutableStyleState(null) }
    Box(
        modifier = Modifier
            .clip(VicuTheme.shapes.full)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .styleable(chipState, VicuTheme.styles.chip then Style {
                background(if (selected) colors.primary else colors.secondaryContainer)
                contentColor(if (selected) colors.onPrimary else colors.onSecondaryContainer)
            }),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label)
    }
}
