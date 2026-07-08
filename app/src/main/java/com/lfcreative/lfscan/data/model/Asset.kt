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
    @SerialName("serial_number") val serialNumber: String? = null,
    @SerialName("photo_urls") val photoUrls: List<String> = emptyList(),
    @SerialName("current_location_id") val currentLocationId: String? = null,
    @SerialName("current_user_id") val currentUserId: String? = null,
    // Also doubles as the renter's display name when status == "rented" (external renters have
    // no team-member id, so current_user_id stays null in that case).
    @SerialName("current_user_name") val currentUserName: String? = null,
    @SerialName("container_id") val containerId: String? = null,
    @SerialName("container_locked") val containerLocked: Boolean = false,
    @SerialName("renter_contact") val renterContact: String? = null,
    @SerialName("rental_due_date") val rentalDueDate: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

// Separate insert-only class to avoid sending a null updated_at to the DB,
// which would override its default (and clash with server-generated timestamps).
@Serializable
data class AssetInsert(
    @SerialName("asset_id") val assetId: String,
    val name: String,
    val type: String? = null,
    val size: String = "M",
    val cost: String = "Med",
    val status: String,
    val notes: String? = null,
    @SerialName("serial_number") val serialNumber: String? = null,
    @SerialName("photo_urls") val photoUrls: List<String> = emptyList(),
    @SerialName("current_location_id") val currentLocationId: String? = null,
    @SerialName("current_user_id") val currentUserId: String? = null,
    @SerialName("current_user_name") val currentUserName: String? = null
)

@Serializable
data class AssetIdOnly(
    @SerialName("asset_id") val assetId: String
)

@Serializable
data class AssetTypeOnly(
    val type: String? = null
)
