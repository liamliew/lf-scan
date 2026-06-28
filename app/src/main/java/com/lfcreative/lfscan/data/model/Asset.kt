package com.lfcreative.lfscan.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Asset(
    @SerialName("asset_id") val assetId: String,
    val name: String,
    val type: String,
    val status: String,
    @SerialName("current_location_id") val currentLocationId: String? = null,
    @SerialName("current_user_id") val currentUserId: String? = null,
    @SerialName("current_user_name") val currentUserName: String? = null
)
