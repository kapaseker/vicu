package com.rockbyte.vicu.ui.component.trim

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.rockbyte.vicu.R
import com.rockbyte.vicu.ui.theme.VicuTheme

@Composable
internal fun TrimTimeInput(
    label: String, value: String, enabled: Boolean, alignment: TextAlign, modifier: Modifier,
    onChange: (String) -> Unit, onFinish: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit)) {
        BasicText(label, Modifier.fillMaxWidth(), style = VicuTheme.typography.caption.copy(
            color = VicuTheme.colors.onSurfaceVariant, textAlign = alignment))
        BasicTextField(
            value, onChange, enabled = enabled, singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }.onFocusChanged {
                if (focused && !it.isFocused) onFinish()
                focused = it.isFocused
            }.background(VicuTheme.colors.surfaceContainerLow, VicuTheme.shapes.base)
                .border(VicuTheme.dimensions.cardBorderWidth,
                    if (focused) VicuTheme.colors.primary else VicuTheme.colors.outlineVariant, VicuTheme.shapes.base)
                .padding(VicuTheme.dimensions.spacingUnit),
            textStyle = VicuTheme.typography.bodyLg.copy(color = VicuTheme.colors.onSurface, textAlign = alignment),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onFinish(); focusManager.clearFocus() }),
        )
    }
}

internal val TrimInputError.messageRes: Int get() = when (this) {
    TrimInputError.EMPTY -> R.string.trim_error_empty
    TrimInputError.FORMAT -> R.string.trim_error_format
    TrimInputError.OUT_OF_BOUNDS -> R.string.trim_error_bounds
    TrimInputError.ORDER -> R.string.trim_error_order
}
