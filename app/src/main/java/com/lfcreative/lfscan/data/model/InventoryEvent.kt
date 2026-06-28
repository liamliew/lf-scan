package com.lfcreative.lfscan.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InventoryEvent(
    val id: String? = null,
    @SerialName("asset_id") val assetId: String,
    @SerialName("event_type") val eventType: String,
    @SerialName("performed_by") val performedBy: String? = null,
    @SerialName("performed_by_name") val performedByName: String? = null,
    val note: String? = null,
    @SerialName("location_id") val locationId: String? = null,
    @SerialName("gps_lat") val gpsLat: Double? = null,
    @SerialName("gps_lng") val gpsLng: Double? = null,
    @SerialName("gps_address") val gpsAddress: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

// Separate insert-only class to avoid sending null id/created_at to the DB
@Serializable
data class InventoryEventInsert(
    @SerialName("asset_id") val assetId: String,
    @SerialName("event_type") val eventType: String,
    @SerialName("performed_by") val performedBy: String,
    @SerialName("performed_by_name") val performedByName: String,
    @SerialName("location_id") val locationId: String? = null,
    @SerialName("gps_lat") val gpsLat: Double? = null,
    @SerialName("gps_lng") val gpsLng: Double? = null,
    @SerialName("gps_address") val gpsAddress: String? = null
)
