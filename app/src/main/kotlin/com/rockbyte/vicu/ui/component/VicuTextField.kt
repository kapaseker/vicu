package com.rockbyte.vicu.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.style.then
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import com.rockbyte.vicu.R
import com.rockbyte.vicu.ui.theme.VicuTheme

/**
 * 通用单行输入控件：文本框 + 仅有输入文字时显示的清除图标（ic_close，单击清空）。
 * 清除图标位于输入框内部尾部（Material 式 trailing icon）：48dp 触控区落在输入框
 * 内边距区域，不额外撑高容器；文字区在图标出现时自动让出尾部 48dp。
 * [inputStyle]/[clearIconStyle] 按 then 语义覆盖主题 textField/clearIconButton 样式，
 * 可分别自定义输入框与图标按钮（容器几何/底色/边框；图标 tint 走项目惯例，在调用点固定）。
 * 光标管理：内部持有 TextFieldValue，首次组合与外部 [value] 变化时均将光标置于文字末尾；
 * 清除只改内部状态并回调，不抢占焦点。
 */
@Composable
fun VicuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    inputStyle: Style = Style,
    clearIconStyle: Style = Style,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { }
    var textValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) {
        if (value != textValue.text) {
            textValue = TextFieldValue(value, TextRange(value.length))
        }
    }
    val hasClear = textValue.text.isNotEmpty()
    Box(
        modifier = modifier.styleable(styleState, VicuTheme.styles.textField then inputStyle),
        contentAlignment = Alignment.CenterStart,
    ) {
        var fieldModifier = Modifier
            .fillMaxWidth()
            .padding(VicuTheme.dimensions.spacingUnit * 2)
            .then(if (hasClear) Modifier.padding(end = VicuTheme.dimensions.navigationTouchSize) else Modifier)
        if (focusRequester != null) fieldModifier = fieldModifier.focusRequester(focusRequester)
        BasicTextField(
            value = textValue,
            onValueChange = {
                textValue = it
                onValueChange(it.text)
            },
            singleLine = singleLine,
            interactionSource = interactionSource,
            modifier = fieldModifier,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
        )
        if (hasClear) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(VicuTheme.shapes.full) // 裁掉 ripple，保持圆形触控区
                    .clickable {
                        textValue = TextFieldValue("", TextRange(0))
                        onValueChange("")
                    }
                    .styleable(style = VicuTheme.styles.clearIconButton then clearIconStyle),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.clear),
                    modifier = Modifier.size(VicuTheme.dimensions.iconMedium),
                    colorFilter = ColorFilter.tint(VicuTheme.colors.onSurfaceVariant),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VicuTextFieldPreview() {
    VicuTheme {
        VicuTextField(value = "sample.mp4", onValueChange = {})
    }
}
