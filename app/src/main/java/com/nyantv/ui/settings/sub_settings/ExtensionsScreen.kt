package com.nyantv.ui.settings.sub_settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.nyantv.extensions.ExtensionOrderStore
import com.nyantv.ui.utils.SubScreenHeader
import com.nyantv.ui.utils.focusBorder
import com.nyantv.viewmodel.ExtensionViewModel
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import java.io.File

import android.content.SharedPreferences
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Settings
import com.nyantv.ui.utils.SectionCardDialog
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.data.preference.SharedPreferencesDataStore
import androidx.core.content.edit

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

private fun AnimeExtension.Installed.isConfigurable(): Boolean =
    sources.any { it is ConfigurableAnimeSource }

private fun AnimeExtension.Installed.getPrefs(context: Context): SharedPreferences? =
    try {
        val sourceId = sources.firstOrNull()?.id ?: return null
        context.getSharedPreferences("source_$sourceId", Context.MODE_PRIVATE)
    } catch (e: Exception) { null }

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
    var settingsTarget by remember { mutableStateOf<AnimeExtension.Installed?>(null) }
    // Which language group is expanded in the Available list (null = all collapsed, one at a time).
    var expandedLang by remember { mutableStateOf<String?>(null) }

    // User-defined order (decides which extension is probed first). Re-derived whenever the
    // installed set changes (install / uninstall).
    val orderStore = remember(context) { ExtensionOrderStore(context) }
    var ordered by remember(installed) { mutableStateOf(orderStore.sort(installed)) }
    fun moveExtension(from: Int, to: Int) {
        if (to < 0 || to > ordered.lastIndex) return
        val list = ordered.toMutableList()
        list.add(to, list.removeAt(from))
        ordered = list
        orderStore.saveOrder(list.map { it.pkgName })
    }

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
    if (settingsTarget != null) {
        val ext = settingsTarget!!
        val prefs: SharedPreferences? = remember(ext) { ext.getPrefs(context) }
        val source: ConfigurableAnimeSource? = remember(ext) {
            ext.sources.firstOrNull { it is ConfigurableAnimeSource } as? ConfigurableAnimeSource
        }

        SectionCardDialog(title = ext.name, onDismiss = { settingsTarget = null }) {
            if (prefs == null || source == null) {
                Text(
                    text     = "No settings available.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                ExtensionSettingsContent(source = source, prefs = prefs, context = context)
            }
        }
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

        item {
            val gPrefs = remember { context.getSharedPreferences("nyantv_prefs", Context.MODE_PRIVATE) }
            var globalSearch by remember { mutableStateOf(gPrefs.getBoolean("global_search_extensions", false)) }
            Column {
                SectionHeader("Search")
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Search all extensions", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Include results from every installed extension in the Search tab",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    Switch(
                        checked         = globalSearch,
                        onCheckedChange = { globalSearch = it; gPrefs.edit { putBoolean("global_search_extensions", it) } },
                    )
                }
            }
        }

        if (ordered.isNotEmpty()) {
            item { SectionHeader("Installed") }
            if (ordered.size > 1) {
                item {
                    Text(
                        text     = "Order decides which extension is checked first when opening an anime.",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            itemsIndexed(ordered, key = { _, it -> "installed_${it.pkgName}" }) { index, ext ->
                val hasUpdate = remember(ext.pkgName, ext.versionCode, available) {
                    viewModel.hasUpdate(ext)
                }
                InstalledExtensionItem(
                    extension = ext,
                    hasUpdate = hasUpdate,
                    onUpdate = {
                        available.firstOrNull { it.pkgName == ext.pkgName }
                            ?.let { viewModel.installExtension(it) }
                    },
                    onDelete = { deleteTarget = ext },
                    onSettings = if (ext.isConfigurable()) ({ settingsTarget = ext }) else null,
                    reorderable = ordered.size > 1,
                    canMoveUp   = index > 0,
                    canMoveDown = index < ordered.lastIndex,
                    onMoveUp    = { moveExtension(index, index - 1) },
                    onMoveDown  = { moveExtension(index, index + 1) },
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
                    LanguageAccordionHeader(
                        label    = langDisplayName(lang),
                        count    = exts.size,
                        expanded = expandedLang == lang,
                        onClick  = { expandedLang = if (expandedLang == lang) null else lang },
                    )
                }
                if (expandedLang == lang) {
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
}

@Composable
private fun LanguageAccordionHeader(
    label:    String,
    count:    Int,
    expanded: Boolean,
    onClick:  () -> Unit,
) {
    Surface(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth().focusBorder(MaterialTheme.shapes.medium),
        shape    = MaterialTheme.shapes.medium,
        color    = if (expanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                   else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text     = label,
                style    = MaterialTheme.typography.labelLarge,
                color    = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text  = "$count",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier           = Modifier.rotate(if (expanded) 180f else 0f),
                tint               = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
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
    onSettings: (() -> Unit)?,
    reorderable: Boolean = false,
    canMoveUp:   Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp:    () -> Unit = {},
    onMoveDown:  () -> Unit = {},
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier            = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment   = Alignment.CenterVertically
        ) {
            if (reorderable) {
                Column {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        IconButton(
                            onClick  = onMoveUp,
                            enabled  = canMoveUp,
                            modifier = Modifier.size(28.dp).focusBorder(CircleShape, inset = true)
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Move up",
                                modifier = Modifier.size(20.dp),
                                tint = if (canMoveUp) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                        }
                        IconButton(
                            onClick  = onMoveDown,
                            enabled  = canMoveDown,
                            modifier = Modifier.size(28.dp).focusBorder(CircleShape, inset = true)
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Move down",
                                modifier = Modifier.size(20.dp),
                                tint = if (canMoveDown) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.width(4.dp))
            }
            ExtensionIcon(iconUrl = extension.iconUrl)
            Column(modifier = Modifier.weight(1f)) {
                Text(extension.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "v${extension.versionName} | ${langDisplayName(extension.lang)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (onSettings != null) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    IconButton(
                        onClick  = onSettings,
                        modifier = Modifier.focusBorder(CircleShape, inset = true)
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Extension settings",
                            modifier = Modifier.size(20.dp),
                            tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
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
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            IconButton(
                                onClick  = { onDeleteRepo(url) },
                                modifier = Modifier.focusBorder(CircleShape, inset = true)
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Remove repo",
                                    tint     = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
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
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    IconButton(
                        onClick  = {
                            if (input.isNotBlank()) {
                                onAddRepo(input.trim())
                                input = ""
                            }
                        },
                        modifier = Modifier.focusBorder(CircleShape, inset = true),
                        enabled  = input.isNotBlank()
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
}

@Composable
private fun ExtensionSettingsContent(
    source:  ConfigurableAnimeSource,
    prefs:   SharedPreferences,
    context: Context,
) {
    val prefItems = remember(source) {
        try {
            val prefManager = androidx.preference.PreferenceManager::class.java
                .getDeclaredConstructor(Context::class.java)
                .also { it.isAccessible = true }
                .newInstance(context)

            prefManager.preferenceDataStore = SharedPreferencesDataStore(prefs)
            val screen = prefManager.createPreferenceScreen(context)
            source.setupPreferenceScreen(screen)
            collectPreferences(screen)
        } catch (e: Exception) {
            emptyList()
        }
    }

    if (prefItems.isEmpty()) {
        Text(
            text     = "No configurable settings found.",
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(16.dp)
        )
        return
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .heightIn(max = 400.dp)
            .verticalScroll(scrollState)
    ) {
        prefItems.forEachIndexed { index, pref ->
            PreferenceItem(pref = pref, prefs = prefs)
            if (index < prefItems.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            }
        }
    }
}

private fun collectPreferences(
    group: androidx.preference.PreferenceGroup
): List<androidx.preference.Preference> {
    val result = mutableListOf<androidx.preference.Preference>()
    for (i in 0 until group.preferenceCount) {
        val pref = group.getPreference(i)
        if (pref is androidx.preference.PreferenceGroup) {
            result += collectPreferences(pref)
        } else {
            result += pref
        }
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreferenceItem(
    pref:  androidx.preference.Preference,
    prefs: SharedPreferences,
) {
    when (pref) {
        is androidx.preference.CheckBoxPreference,
        is androidx.preference.SwitchPreference,
        is androidx.preference.SwitchPreferenceCompat -> {
            val key     = pref.key ?: return
            var checked by remember { mutableStateOf(prefs.getBoolean(key, false)) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        checked = !checked
                        prefs.edit { putBoolean(key, checked) }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(pref.title?.toString() ?: key, style = MaterialTheme.typography.bodyMedium)
                    if (!pref.summary.isNullOrBlank()) {
                        Text(
                            pref.summary.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Switch(
                    checked         = checked,
                    onCheckedChange = { checked = it; prefs.edit { putBoolean(key, it) } },
                    modifier        = Modifier.focusable()
                )
            }
        }

        is androidx.preference.EditTextPreference -> {
            val key  = pref.key ?: return
            var text by remember { mutableStateOf(prefs.getString(key, "") ?: "") }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    pref.title?.toString() ?: key,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (!pref.summary.isNullOrBlank()) {
                    Text(
                        pref.summary.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value         = text,
                    onValueChange = { text = it; prefs.edit { putString(key, it) } },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
            }
        }

        is androidx.preference.ListPreference -> {
            val key          = pref.key ?: return
            val entries      = pref.entries?.map { it.toString() } ?: emptyList()
            val entryValues  = pref.entryValues?.map { it.toString() } ?: emptyList()
            var selected     by remember { mutableStateOf(prefs.getString(key, "") ?: "") }
            var expanded     by remember { mutableStateOf(false) }
            val selectedLabel = entries.getOrNull(entryValues.indexOf(selected)) ?: selected

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    pref.title?.toString() ?: key,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(4.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value         = selectedLabel,
                        onValueChange = {},
                        readOnly      = true,
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier      = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        entries.forEachIndexed { i, label ->
                            DropdownMenuItem(
                                text    = { Text(label) },
                                onClick = {
                                    selected = entryValues[i]
                                    prefs.edit { putString(key, entryValues[i]) }
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        is androidx.preference.MultiSelectListPreference -> {
            val key         = pref.key ?: return
            val entries     = pref.entries?.map { it.toString() } ?: emptyList()
            val entryValues = pref.entryValues?.map { it.toString() } ?: emptyList()
            var selected    by remember { mutableStateOf(prefs.getStringSet(key, emptySet()) ?: emptySet()) }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    pref.title?.toString() ?: key,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                entries.forEachIndexed { i, label ->
                    val value   = entryValues[i]
                    val checked = value in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newSet = if (checked) selected - value else selected + value
                                selected = newSet
                                prefs.edit { putStringSet(key, newSet) }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked         = checked,
                            onCheckedChange = {
                                val newSet = if (checked) selected - value else selected + value
                                selected = newSet
                                prefs.edit { putStringSet(key, newSet) }
                            },
                            modifier = Modifier.focusable()
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}