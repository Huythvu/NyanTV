package com.nyantv.ui.settings.sub_settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.nyantv.ui.utils.SubScreenHeader
import com.nyantv.ui.utils.focusBorder
import com.nyantv.viewmodel.ExtensionViewModel
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import java.io.File

private val LANG_NAMES = mapOf(
    "all"   to "All",
    "en"    to "English",
    "de"    to "Deutsch",
    "fr"    to "Français",
    "es"    to "Español",
    "pt-BR" to "Português (BR)",
    "ar"    to "العربية",
    "it"    to "Italiano",
    "pl"    to "Polski",
    "ru"    to "Русский",
    "tr"    to "Türkçe",
    "zh"    to "中文",
    "zh-hant" to "中文 (繁體)",
    "ko"    to "한국어",
    "id"    to "Indonesia",
    "hi"    to "हिन्दी",
    "sr"    to "Srpski",
    "uk"    to "Українська",
)

private fun langDisplayName(code: String) =
    LANG_NAMES[code] ?: code.uppercase()

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun ExtensionsScreen(
    navController: NavController,
    viewModel: ExtensionViewModel = viewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onAppResumed()
        }
    }

    val installed by viewModel.installedExtensions.collectAsStateWithLifecycle()
    val available by viewModel.availableExtensions.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val repos     by viewModel.repos.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) {
        var ctx = context
        while (ctx is ContextWrapper && ctx !is Activity) {
            ctx = ctx.baseContext
        }
        ctx as? Activity
    }

    var deleteTarget by remember { mutableStateOf<AnimeExtension.Installed?>(null) }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Extension") },
            text = { Text("Are you sure you want to uninstall ${deleteTarget!!.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget?.let {
                        activity?.let { act -> viewModel.deleteExtension(it, act) }
                    }
                    deleteTarget = null
                }) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("No") }
            }
        )
    }

    val availableByLang = remember(available, installed) {
        val installedPkgs = installed.map { it.pkgName }.toSet()
        available
            .filter { it.pkgName !in installedPkgs }
            .groupBy { it.lang }
            .toSortedMap(compareBy { langDisplayName(it) })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SubScreenHeader(title = "Extensions", navController = navController)
        }
        item {
            RepoManagerSection(
                repos        = repos,
                onAddRepo    = { viewModel.addRepo(it) },
                onDeleteRepo = { viewModel.deleteRepo(it) }
            )
        }

        if (installed.isNotEmpty()) {
            item { SectionHeader("Installed") }
            items(installed, key = { "installed_${it.pkgName}" }) { ext ->
                val hasUpdate = remember(ext.pkgName, available) {
                    viewModel.hasUpdate(ext)
                }
                InstalledExtensionItem(
                    extension = ext,
                    hasUpdate = hasUpdate,
                    onUpdate = {
                        available.firstOrNull { it.pkgName == ext.pkgName }
                            ?.let { viewModel.installExtension(it) }
                    },
                    onDelete = { deleteTarget = ext }
                )
            }
        }

        item { SectionHeader("Available") }

        if (isLoading) {
            item { CircularProgressIndicator(modifier = Modifier.padding(16.dp)) }
        } else if (repos.isEmpty()) {
            item {
                Text(
                    text     = "Add a repository above to browse extensions.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            availableByLang.forEach { (lang, exts) ->
                item(key = "header_$lang") {
                    Text(
                        text = langDisplayName(lang),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                items(exts, key = { "available_${it.pkgName}" }) { ext ->
                    AvailableExtensionItem(
                        extension = ext,
                        onInstall = { viewModel.installExtension(ext) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.titleMedium,
        color    = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun InstalledExtensionItem(
    extension: AnimeExtension.Installed,
    hasUpdate: Boolean,
    onUpdate:  () -> Unit,
    onDelete:  () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment   = Alignment.CenterVertically
        ) {
            ExtensionIcon(iconUrl = extension.iconUrl)
            Column(modifier = Modifier.weight(1f)) {
                Text(extension.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "v${extension.versionName} | ${langDisplayName(extension.lang)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (hasUpdate) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    OutlinedButton(
                        onClick = onUpdate,
                        modifier = Modifier.focusBorder(CircleShape, inset = true)
                    ) { Text("Update") }
                }
                Spacer(Modifier.width(4.dp))
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.focusBorder(CircleShape, inset = true)
                ) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun AvailableExtensionItem(
    extension: AnimeExtension.Available,
    onInstall: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            ExtensionIcon(iconUrl = extension.iconUrl)
            Column(modifier = Modifier.weight(1f)) {
                Text(extension.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "v${extension.versionName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Button(
                    onClick = onInstall,
                    modifier = Modifier.focusBorder(CircleShape, color = MaterialTheme.colorScheme.onPrimary)
                ) { Text("Install") }
            }
        }
    }
}

@Composable
private fun ExtensionIcon(iconUrl: String?) {
    val model = remember(iconUrl) {
        when {
            iconUrl == null          -> null
            iconUrl.startsWith("/") -> File(iconUrl)
            else                    -> iconUrl
        }
    }
    AsyncImage(
        model            = model,
        contentDescription = null,
        modifier         = Modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        fallback = null,
        error    = null,
    )
}

@Composable
private fun RepoManagerSection(
    repos: List<String>,
    onAddRepo: (String) -> Unit,
    onDeleteRepo: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column {
            Text(
                text       = "Repositories",
                style      = MaterialTheme.typography.labelLarge,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            if (repos.isEmpty()) {
                Text(
                    text     = "No repositories added yet.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            } else {
                repos.forEach { url ->
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            text     = url,
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.weight(1f),
                            maxLines = 2
                        )
                        IconButton(onClick = { onDeleteRepo(url) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Remove repo",
                                tint     = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                }
            }

            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value         = input,
                    onValueChange = { input = it },
                    placeholder   = { Text("https://.../index.min.json", style = MaterialTheme.typography.bodySmall) },
                    singleLine    = true,
                    modifier      = Modifier.weight(1f),
                    textStyle     = MaterialTheme.typography.bodySmall,
                )
                IconButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            onAddRepo(input.trim())
                            input = ""
                        }
                    },
                    enabled = input.isNotBlank()
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Add repo",
                        tint = if (input.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}