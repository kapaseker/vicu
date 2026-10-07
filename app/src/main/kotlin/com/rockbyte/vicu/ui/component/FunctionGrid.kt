package com.rockbyte.vicu.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.rockbyte.vicu.ui.theme.VicuTheme

/** 功能按钮 grid：自适应列（tile 最小 96dp，宽屏自动多列）。 */
@Composable
fun FunctionGrid(functions: List<FunctionEntry>) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(VicuTheme.dimensions.mediaFunctionsGridMinSize),
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(VicuTheme.dimensions.screenGutter),
        horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
        verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
    ) {
        items(functions, key = { it.labelRes }) { entry ->
            FunctionTile(
                iconRes = entry.iconRes,
                label = stringResource(entry.labelRes),
                onClick = entry.onClick,
            )
        }
    }
}

class FunctionEntry(
    val iconRes: Int,
    val labelRes: Int,
    val onClick: () -> Unit,
)

/** 单个功能按钮：竖向 icon + 文字，卡片视觉直接落在页面上（不套外层容器）。 */
@Composable
private fun FunctionTile(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val styleState = remember { MutableStyleState(null) }
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(VicuTheme.shapes.xl)
            .clickable(
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .styleable(styleState, VicuTheme.styles.functionTile),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            VicuTheme.dimensions.spacingUnit,
            Alignment.CenterVertically,
        ),
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(VicuTheme.dimensions.iconLarge),
            colorFilter = ColorFilter.tint(VicuTheme.colors.onSurfaceVariant),
        )
        BasicText(
            text = label,
            style = VicuTheme.typography.bodySm.copy(color = VicuTheme.colors.onSurface),
        )
    }
}
