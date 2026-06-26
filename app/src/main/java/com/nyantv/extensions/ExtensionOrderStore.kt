package com.nyantv.extensions

import android.content.Context
import androidx.core.content.edit
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension

/**
 * Persists a user-defined ordering of installed extensions (by package name). The order decides
 * which extension is probed/selected first when opening an anime, and the order shown everywhere
 * extensions are listed for browsing.
 */
class ExtensionOrderStore(context: Context) {

    private val prefs = context.getSharedPreferences("extension_order", Context.MODE_PRIVATE)

    fun loadOrder(): List<String> =
        (prefs.getString(KEY, "") ?: "")
            .split("\n")
            .filter { it.isNotBlank() }

    fun saveOrder(pkgNames: List<String>) {
        prefs.edit { putString(KEY, pkgNames.joinToString("\n")) }
    }

    /**
     * Returns [installed] sorted by the saved order. Extensions not yet in the saved order (newly
     * installed) keep their natural relative position at the end.
     */
    fun sort(installed: List<AnimeExtension.Installed>): List<AnimeExtension.Installed> {
        val order = loadOrder()
        if (order.isEmpty()) return installed
        val rank = order.withIndex().associate { (i, pkg) -> pkg to i }
        return installed
            .withIndex()
            .sortedWith(compareBy({ rank[it.value.pkgName] ?: Int.MAX_VALUE }, { it.index }))
            .map { it.value }
    }

    private companion object {
        const val KEY = "order"
    }
}
