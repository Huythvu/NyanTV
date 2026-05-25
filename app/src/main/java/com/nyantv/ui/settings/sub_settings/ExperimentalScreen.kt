package com.nyantv.ui.settings.sub_settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.nyantv.ui.player.PlayerArgs
import com.nyantv.ui.player.StreamTrack
import com.nyantv.ui.player.SubtitleTrack
import com.nyantv.ui.utils.SubScreenHeader
import com.nyantv.ui.utils.focusBorder
import java.io.File

const val TEST_STREAM = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
const val TEST_STREAM2 = "https://tv-trtworld.medya.trt.com.tr/master.m3u8"

const val TEST_STREAM3 = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"
const val TEST_SUBS   = "https://www.fileexamples.com/files/sample_web_video_captions.vtt"

@Composable
fun ExperimentalScreen(navController: NavController) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        SubScreenHeader(title = "Experimental stuff", navController = navController)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.PlayCircleOutline,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            "Example Stream",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Test the media player",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Button(
                        onClick = {
                            PlayerArgs.streams = listOf(
                                StreamTrack("HD 1080p DUB", TEST_STREAM),
                                StreamTrack("HD 720p DUB",  TEST_STREAM2),
                                StreamTrack("SD 480p DUB",  TEST_STREAM3),
                            )
                            PlayerArgs.subtitleTracks = listOf(
                                SubtitleTrack("English", TEST_SUBS),
                            )
                            PlayerArgs.initialStreamIndex = 0
                            PlayerArgs.title              = "Test Stream"
                            navController.navigate("player")
                        },
                        modifier = Modifier.fillMaxWidth().focusBorder(CircleShape)
                    ) {
                        Icon(Icons.Filled.Stream, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Start Stream")
                    }
                }
            }
        }
    }
}