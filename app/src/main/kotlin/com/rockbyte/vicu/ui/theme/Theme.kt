package com.rockbyte.vicu.ui.theme

import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.style.StyleScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 自定义设计系统主题（完全不依赖 MaterialTheme）。
 * 按 DESIGN.md（Auralis System）以 Styles API 组织：token 经 CompositionLocal
 * 暴露给 Composable 与 Style 定义，组件样式集中在 [VicuStyles]。
 */
@Immutable
class VicuTheme(
    val colors: AuralisColors = LightAuralisColors,
    val typography: VicuTypography = LightTypography,
    val shapes: VicuShapes = VicuShapes,
) {
    companion object {
        val colors: AuralisColors
            @Composable @ReadOnlyComposable
            get() = LocalVicuTheme.current.colors

        val typography: VicuTypography
            @Composable @ReadOnlyComposable
            get() = LocalVicuTheme.current.typography

        val shapes: VicuShapes
            @Composable @ReadOnlyComposable
            get() = LocalVicuTheme.current.shapes

        val styles: VicuStyles = VicuStyles
    }
}

internal val LocalVicuTheme = staticCompositionLocalOf { VicuTheme() }

@Composable
fun VicuTheme(content: @Composable () -> Unit) {
    // 开启 Style 的文本属性继承（contentColor/textStyle 沿 styleable 容器传播给 BasicText），
    // 避免每个文案节点显式传色。
    ComposeFoundationFlags.isInheritedTextStyleEnabled = true
    CompositionLocalProvider(LocalVicuTheme provides VicuTheme()) {
        content()
    }
}

/** Style 定义内访问 token（StyleScope 中用 currentValue，而非 .current）。 */
val StyleScope.colors: AuralisColors
    get() = LocalVicuTheme.currentValue.colors

val StyleScope.typography: VicuTypography
    get() = LocalVicuTheme.currentValue.typography

val StyleScope.shapes: VicuShapes
    get() = LocalVicuTheme.currentValue.shapes
