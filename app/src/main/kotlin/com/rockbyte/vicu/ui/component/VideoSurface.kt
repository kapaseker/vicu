package com.rockbyte.vicu.ui.component

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 播放预览 Surface：surfaceChanged 上报 Surface 供引擎绑定，surfaceDestroyed 上报 null。
 * 播放页与裁剪页共用，宿主负责把 Surface 交给引擎。
 */
@Composable
fun VideoSurface(
    modifier: Modifier = Modifier,
    onSurfaceAvailable: (android.view.Surface?) -> Unit,
) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) = Unit

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int,
                    ) {
                        onSurfaceAvailable(holder.surface)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        onSurfaceAvailable(null)
                    }
                })
            }
        },
        modifier = modifier,
    )
}
