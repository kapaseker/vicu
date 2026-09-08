package com.rockbyte.vicu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private val viewModel: AudioExportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val videoPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
                onResult = { uri -> uri?.let(viewModel::selectVideo) }
            )

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AudioExportScreen(
                        state = uiState,
                        onChooseVideo = { videoPicker.launch(arrayOf("video/*")) },
                        onExport = viewModel::exportSelectedVideo
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioExportScreen(
    state: AudioExportUiState,
    onChooseVideo: () -> Unit,
    onExport: () -> Unit
) {
    val exporting = state.phase is ExportPhase.Exporting
    val canExport = state.selectedFileName != null && !exporting

    Scaffold(topBar = { TopAppBar(title = { Text("视频转 MP3") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("选择一个视频，提取第一个音频流并导出为固定 192 kb/s MP3。")

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !exporting,
                onClick = onChooseVideo
            ) {
                Text("选择视频")
            }

            state.selectedFileName?.let { Text("已选择：$it") }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = canExport,
                onClick = onExport
            ) {
                Text("导出 MP3（192 kb/s）")
            }

            when (val phase = state.phase) {
                ExportPhase.Exporting -> CircularProgressIndicator()
                is ExportPhase.Complete -> Text("已保存到相册的 Music/FFmpegKitNext 文件夹。")
                is ExportPhase.Failed -> Text(
                    text = "导出失败：${phase.reason}",
                    color = MaterialTheme.colors.error
                )
                else -> Unit
            }
        }
    }
}
