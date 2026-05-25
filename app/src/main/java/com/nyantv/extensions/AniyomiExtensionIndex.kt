package com.nyantv.extensions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExtensionIndexEntry(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int = 0,
    val sources: List<ExtensionSourceEntry> = emptyList(),
)

@Serializable
data class ExtensionSourceEntry(
    val id: Long,
    val lang: String,
    val name: String,
    @SerialName("baseUrl") val baseUrl: String = "",
)