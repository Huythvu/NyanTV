package com.nyantv.ui.utils

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

enum class NetworkState { LOADING, ERROR, SUCCESS }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NetworkStatusContent(
    state: NetworkState,
    serviceName: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    when (state) {
        NetworkState.LOADING -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator(color = MaterialTheme.colorScheme.primary)
        }

        NetworkState.ERROR -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = "$serviceName API is not responding",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Check your connection",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Button(onClick = onRetry, modifier = Modifier.focusBorder(CircleShape)) {
                        Text("Try Again")
                    }
                }
            }
        }

        NetworkState.SUCCESS -> content()
    }
}