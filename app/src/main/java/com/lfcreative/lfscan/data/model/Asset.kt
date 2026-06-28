package com.lfcreative.lfscan.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Asset(
    @SerialName("asset_id") val assetId: String,
    val name: String,
    val category: String = "Other",
    val type: String? = null,
    val size: String = "M",
    val cost: String = "Med",
    val status: String,
    val notes: String? = null,
    @SerialName("photo_urls") val photoUrls: List<String> = emptyList(),
    @SerialName("current_location_id") val currentLocationId: String? = null,
    @SerialName("current_user_id") val currentUserId: String? = null,
    @SerialName("current_user_name") val currentUserName: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
