package com.nyantv.ui.settings.sub_settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.nyantv.ui.utils.SubScreenHeader
import com.nyantv.ui.utils.focusBorder
import com.nyantv.viewmodel.AppViewModel

/**
 * Manage the per-series "Always ask" tracking choices. Each remembered anime can be flipped
 * Yes/No, or removed so it prompts again next time. (Only relevant with "Ask once per series" on.)
 */
@Composable
fun TrackingConsentsScreen(vm: AppViewModel, navController: NavController) {
    var items        by remember { mutableStateOf(vm.listSeriesConsents()) }
    var confirmClear by remember { mutableStateOf(false) }

    fun reload() { items = vm.listSeriesConsents() }

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SubScreenHeader(title = "Remembered choices", navController = navController) }
        item {
            Text(
                "Your remembered “track this series?” answers. Flip one Yes/No, or remove it so " +
                    "it asks again next time you watch.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }

        if (items.isEmpty()) {
            item {
                Text(
                    "No remembered choices yet.",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        } else {
            item {
                OutlinedButton(
                    onClick  = { confirmClear = true },
                    modifier = Modifier.focusBorder(MaterialTheme.shapes.small),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Reset all") }
            }
            items(items, key = { it.id }) { item ->
                ConsentRow(
                    item     = item,
                    onToggle = { granted -> vm.setSeriesConsent(item.id, granted); reload() },
                    onRemove = { vm.removeSeriesConsent(item.id); reload() },
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title            = { Text("Reset all choices?") },
            text             = { Text("Every remembered answer is forgotten, so each series asks again next time.") },
            confirmButton    = {
                TextButton(onClick = { vm.clearSeriesConsents(); reload(); confirmClear = false }) {
                    Text("Reset all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton    = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ConsentRow(
    item:     AppViewModel.ConsentItem,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model              = item.poster,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
                    .size(40.dp, 56.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                Text(
                    if (item.granted) "Tracking: On" else "Tracking: Off",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Switch(
                    checked         = item.granted,
                    onCheckedChange = onToggle,
                    modifier        = Modifier.focusBorder(androidx.compose.foundation.shape.RoundedCornerShape(50)),
                )
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                IconButton(
                    onClick  = onRemove,
                    modifier = Modifier.focusBorder(CircleShape, inset = true),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Forget ${item.title}",
                        tint               = MaterialTheme.colorScheme.error,
                        modifier           = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
