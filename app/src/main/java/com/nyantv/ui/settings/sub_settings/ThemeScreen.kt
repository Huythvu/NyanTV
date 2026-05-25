package com.nyantv.ui.settings.sub_settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nyantv.ui.theme.ActiveTheme
import com.nyantv.ui.theme.AppTheme
import com.nyantv.ui.utils.SectionCard
import com.nyantv.ui.utils.SubScreenHeader
import com.nyantv.ui.utils.focusBorder
import com.nyantv.viewmodel.AppViewModel

private val THEME_COLORS = mapOf(
    AppTheme.SAKURA   to Color(0xFFE8A0BF),
    AppTheme.OCEAN    to Color(0xFF7FD1EE),
    AppTheme.MIDNIGHT to Color(0xFFBBB3FF),
    AppTheme.FOREST   to Color(0xFF8FD4A1),
    AppTheme.SUNSET   to Color(0xFFFFB075),
    AppTheme.OLED     to Color(0xFFCFBCFF),
    AppTheme.BLOOD to Color(0xFFA90432)
)

@Composable
private fun ThemeCircle(
    color:       Color,
    label:       String,
    selected:    Boolean,
    onClick:     () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .size(44.dp)
                    .background(color, CircleShape)
                    .then(
                        if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                        else Modifier
                    )
                    .focusBorder(CircleShape)
                    .onKeyEvent { keyEvent ->
                        val isDpadOk = keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                                || keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER
                        if (isDpadOk
                            && keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN
                            && keyEvent.nativeKeyEvent.repeatCount == 1
                        ) {
                            onLongClick?.invoke()
                            true
                        } else false
                    },
                interactionSource = remember { MutableInteractionSource() }
            ) {}
        }
        Text(
            text       = label,
            style      = MaterialTheme.typography.labelSmall,
            color      = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun ThemeScreen(vm: AppViewModel, navController: NavController) {
    val context      = LocalContext.current
    val activeTheme  by vm.activeTheme.collectAsStateWithLifecycle()
    val customThemes by vm.customThemes.collectAsStateWithLifecycle()
    var importError  by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val json = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()?.readText() ?: return@rememberLauncherForActivityResult
        vm.importThemeJson(json)
            .onSuccess { importError = null }
            .onFailure { importError = "Invalid theme format" }
    }

    val waitingForKeyUp = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .onPreviewKeyEvent { keyEvent ->
                if (!waitingForKeyUp.get()) return@onPreviewKeyEvent false
                if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP) {
                    waitingForKeyUp.set(false)
                }
                true
            },
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        SubScreenHeader(title = "Theme", navController = navController)

        // ── Built-in Themes ───────────────────────────────────────────────────
        SectionCard(title = "Built-in Themes") {
            LazyRow(
                contentPadding        = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(AppTheme.entries) { t ->
                    ThemeCircle(
                        color    = THEME_COLORS[t] ?: Color.Gray,
                        label    = t.label,
                        selected = activeTheme == ActiveTheme.BuiltIn(t),
                        onClick  = { vm.setTheme(t) }
                    )
                }
            }
        }

        // ── Custom Themes ─────────────────────────────────────────────────────
        SectionCard(title = "Custom Themes") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (customThemes.isEmpty()) {
                    Text(
                        text     = "No custom themes imported yet.",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                } else {
                    LazyRow(
                        contentPadding        = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(customThemes, key = { it.name }) { t ->
                            ThemeCircle(
                                color       = t.primary,
                                label       = t.name,
                                selected    = activeTheme == ActiveTheme.Custom(t),
                                onClick     = { vm.setCustomTheme(t) },
                                onLongClick = { vm.deleteCustomTheme(t)
                                                waitingForKeyUp.set(true) }
                            )
                        }
                    }
                }

                importError?.let {
                    Text(
                        text     = it,
                        color    = MaterialTheme.colorScheme.error,
                        style    = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }

                TextButton(
                    onClick  = { filePicker.launch("application/json") },
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector     = Icons.Default.FileUpload,
                        contentDescription = null,
                        modifier        = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Import theme from JSON")
                }
            }
        }
    }
}