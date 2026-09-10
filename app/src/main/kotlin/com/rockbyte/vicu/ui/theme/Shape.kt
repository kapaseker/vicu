package com.rockbyte.vicu.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** DESIGN.md rounded 刻度（rem × 4 = px 换算；full 为 pill）。 */
object VicuShapes {
    val sm: Shape = RoundedCornerShape(4.dp)
    val base: Shape = RoundedCornerShape(8.dp)
    val md: Shape = RoundedCornerShape(12.dp)
    val lg: Shape = RoundedCornerShape(16.dp)
    val xl: Shape = RoundedCornerShape(24.dp)
    val full: Shape = RoundedCornerShape(50)
}
