package com.rockbyte.vicu.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.unit.Dp
import com.rockbyte.vicu.R

/** 从 Android 资源加载、由 [VicuTheme] 暴露的视觉尺寸 token。 */
@Immutable
data class VicuDimensions(
    val spacingUnit: Dp,
    val screenGutter: Dp,
    val topAppBarHeight: Dp,
    val topAppBarHorizontalPadding: Dp,
    val topAppBarTitlePadding: Dp,
    val topAppBarTitlePaddingWithoutNavigation: Dp,
    val navigationTouchSize: Dp,
    val iconSmall: Dp,
    val iconMedium: Dp,
    val iconLarge: Dp,
    val cardPadding: Dp,
    val cardBorderWidth: Dp,
    val cardShadowRadius: Dp,
    val buttonHorizontalPadding: Dp,
    val buttonMinHeight: Dp,
    val functionTilePadding: Dp,
    val statusDotSize: Dp,
    val typeBadgeSize: Dp,
    val homeMediaGridMinSize: Dp,
    val mediaFunctionsGridMinSize: Dp,
    val radioItemVerticalPadding: Dp,
    val radioOuterSize: Dp,
    val radioBorderWidth: Dp,
    val radioInnerSize: Dp,
)

/** 无量纲的透明度 token。 */
@Immutable
data class VicuAlpha(
    val full: Float = 1f,
    val disabled: Float = 0.38f,
    val ambientShadow: Float = 0.02f,
    val statusPulseMinimum: Float = 0.3f,
)

/** 动效 token。 */
@Immutable
data class VicuMotion(val statusPulseDurationMillis: Int)

@Composable
internal fun resourceDimensions() = VicuDimensions(
    spacingUnit = dimensionResource(R.dimen.vicu_spacing_unit),
    screenGutter = dimensionResource(R.dimen.vicu_screen_gutter),
    topAppBarHeight = dimensionResource(R.dimen.vicu_top_app_bar_height),
    topAppBarHorizontalPadding = dimensionResource(R.dimen.vicu_top_app_bar_horizontal_padding),
    topAppBarTitlePadding = dimensionResource(R.dimen.vicu_top_app_bar_title_padding),
    topAppBarTitlePaddingWithoutNavigation = dimensionResource(
        R.dimen.vicu_top_app_bar_title_padding_without_navigation
    ),
    navigationTouchSize = dimensionResource(R.dimen.vicu_navigation_touch_size),
    iconSmall = dimensionResource(R.dimen.vicu_icon_size_small),
    iconMedium = dimensionResource(R.dimen.vicu_icon_size_medium),
    iconLarge = dimensionResource(R.dimen.vicu_icon_size_large),
    cardPadding = dimensionResource(R.dimen.vicu_card_padding),
    cardBorderWidth = dimensionResource(R.dimen.vicu_card_border_width),
    cardShadowRadius = dimensionResource(R.dimen.vicu_card_shadow_radius),
    buttonHorizontalPadding = dimensionResource(R.dimen.vicu_button_horizontal_padding),
    buttonMinHeight = dimensionResource(R.dimen.vicu_button_min_height),
    functionTilePadding = dimensionResource(R.dimen.vicu_function_tile_padding),
    statusDotSize = dimensionResource(R.dimen.vicu_status_dot_size),
    typeBadgeSize = dimensionResource(R.dimen.vicu_type_badge_size),
    homeMediaGridMinSize = dimensionResource(R.dimen.vicu_home_media_grid_min_size),
    mediaFunctionsGridMinSize = dimensionResource(R.dimen.vicu_media_functions_grid_min_size),
    radioItemVerticalPadding = dimensionResource(R.dimen.vicu_radio_item_vertical_padding),
    radioOuterSize = dimensionResource(R.dimen.vicu_radio_outer_size),
    radioBorderWidth = dimensionResource(R.dimen.vicu_radio_border_width),
    radioInnerSize = dimensionResource(R.dimen.vicu_radio_inner_size),
)

@Composable
internal fun resourceMotion() = VicuMotion(
    statusPulseDurationMillis = integerResource(R.integer.vicu_status_pulse_duration_millis),
)
