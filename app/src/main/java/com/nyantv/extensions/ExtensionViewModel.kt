package com.nyantv.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nyantv.extensions.AniyomiExtensions
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExtensionViewModel(application: Application) : AndroidViewModel(application) {

    private val aniyomi = AniyomiExtensions(application)

    // ── Repos ──────────────────────────────────────────────────────────────────

    private val repoPrefs = application
        .getSharedPreferences("extension_repos", Context.MODE_PRIVATE)

    private val _repos = MutableStateFlow(loadRepos())
    val repos: StateFlow<List<String>> = _repos

    private fun loadRepos(): List<String> {
        val count = repoPrefs.getInt("repo_count", 0)
        return (0 until count)
            .map { repoPrefs.getString("repo_$it", "") ?: "" }
            .filter { it.isNotBlank() }
    }

    private fun saveRepos(list: List<String>) {
        repoPrefs.edit {
            putInt("repo_count", list.size)
            list.forEachIndexed { i, url -> putString("repo_$i", url) }
        }
    }

    fun addRepo(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank() || trimmed in _repos.value) return
        val updated = _repos.value + trimmed
        _repos.value = updated
        saveRepos(updated)
        android.util.Log.d("NyanExt", "addRepo: '$trimmed' (now ${updated.size} repo(s))")
        refreshAvailable()
    }

    fun deleteRepo(url: String) {
        val updated = _repos.value - url
        _repos.value = updated
        saveRepos(updated)
        refreshAvailable()
    }

    // ── Extensions ─────────────────────────────────────────────────────────────

    val installedExtensions: StateFlow<List<AnimeExtension.Installed>> =
        aniyomi.installedExtensions
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _availableExtensions = MutableStateFlow<List<AnimeExtension.Available>>(emptyList())
    val availableExtensions: StateFlow<List<AnimeExtension.Available>> = _availableExtensions

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadAvailableExtensions()
    }

    fun onAppResumed() {
        viewModelScope.launch(Dispatchers.IO) {
            aniyomi.extensionManager.refresh()
        }
    }

    private fun loadAvailableExtensions() = refreshAvailable()

    fun refreshAvailable() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            android.util.Log.d("NyanExt", "refreshAvailable: ${_repos.value.size} repo(s): ${_repos.value}")
            runCatching {
                _availableExtensions.value = aniyomi.fetchAvailableExtensions(_repos.value)
            }.onFailure { android.util.Log.e("NyanExt", "refreshAvailable failed", it) }
            _isLoading.value = false
        }
    }

    fun installExtension(extension: AnimeExtension.Available) {
        viewModelScope.launch {
            runCatching { aniyomi.installExtension(extension) }
        }
    }

    fun deleteExtension(extension: AnimeExtension.Installed, activity: Activity) {
        aniyomi.uninstallExtension(extension, activity)
    }

    fun hasUpdate(installed: AnimeExtension.Installed): Boolean {
        return availableExtensions.value.any { available ->
            available.pkgName == installed.pkgName &&
                    available.versionCode > installed.versionCode
        }
    }

    fun getAniyomi() = aniyomi
}