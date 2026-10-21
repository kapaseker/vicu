package com.rockbyte.vicu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import com.rockbyte.vicu.R
import com.rockbyte.vicu.ui.theme.VicuTheme

/**
 * 重命名对话框：Level-2 卡片容器（24dp 圆角 + 1px 边框）+ 单行输入（surfaceContainerLow
 * 填充、聚焦 1px 主色边框，对齐 DESIGN.md Input Fields），确认/取消按钮行。
 * 输入为空时确认不可用；键盘 Done 等同确认。
 */
@Composable
fun RenameDialog(
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val confirmEnabled = value.trim().isNotEmpty()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(VicuTheme.colors.surfaceContainerLowest, VicuTheme.shapes.xl)
                .border(
                    VicuTheme.dimensions.cardBorderWidth,
                    VicuTheme.colors.outlineVariant,
                    VicuTheme.shapes.xl,
                )
                .padding(VicuTheme.dimensions.cardPadding),
            verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit * 2),
        ) {
            BasicText(
                text = stringResource(R.string.rename),
                style = VicuTheme.typography.h3,
            )
            VicuTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (confirmEnabled) onConfirm(value.trim())
                    focusManager.clearFocus()
                }),
                focusRequester = focusRequester,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit)) {
                OutlineButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = stringResource(R.string.confirm),
                    onClick = { onConfirm(value.trim()) },
                    enabled = confirmEnabled,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Preview(showBackground = true)
@Composable
private fun RenameDialogPreview() {
    VicuTheme {
        RenameDialog(initialValue = "sample.mp4", onConfirm = {}, onDismiss = {})
    }
}
