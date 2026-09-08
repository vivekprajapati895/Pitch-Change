package com.pitchplayer.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.pitchplayer.app.player.PlayerViewModel
import com.pitchplayer.app.player.Screen
import com.pitchplayer.app.ui.HomeScreen
import com.pitchplayer.app.ui.PlayerScreen
import com.pitchplayer.app.ui.theme.PitchPlayerTheme

@UnstableApi
class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    private val pickFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.loadLocal(it) } }

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Export still runs either way; the notification is a convenience. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Nobody wants the screen dimming mid-verse.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        viewModel.handleIntent(intent)

        setContent {
            PitchPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val state by viewModel.ui.collectAsStateWithLifecycle()
                    val export by viewModel.exportState.collectAsStateWithLifecycle()

                    when (state.screen) {
                        Screen.Home -> HomeScreen(
                            state = state,
                            onUrlChange = viewModel::onUrlChange,
                            onLoad = { viewModel.loadUrl() },
                            onPickFile = {
                                pickFile.launch(arrayOf("audio/*", "video/*"))
                            },
                            onDismissError = viewModel::dismissError
                        )

                        Screen.Player -> PlayerScreen(
                            state = state,
                            exportState = export,
                            player = viewModel.player,
                            onBack = viewModel::backToHome,
                            onTogglePlay = viewModel::togglePlay,
                            onSeek = viewModel::seekTo,
                            onSkip = viewModel::skip,
                            onSemitoneChange = viewModel::setSemitones,
                            onNudge = viewModel::nudgeSemitones,
                            onResetPitch = viewModel::resetPitch,
                            onSpeedChange = viewModel::setSpeedPercent,
                            onSelectQuality = viewModel::selectQuality,
                            onOpenExport = viewModel::openExportSheet,
                            onCloseExport = viewModel::closeExportSheet,
                            onExport = viewModel::startExport,
                            onDismissExportState = viewModel::dismissExportState,
                            onDismissError = viewModel::dismissError
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        // Video without a foreground surface wastes battery; audio focus is
        // handed back cleanly too.
        viewModel.player.pause()
    }
}
