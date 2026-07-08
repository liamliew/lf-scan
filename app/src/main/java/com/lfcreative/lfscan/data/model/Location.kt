package com.lfcreative.lfscan.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Location(
    val id: String,
    val name: String,
    @SerialName("location_code") val locationCode: String? = null,
    val description: String? = null
)

// description is NOT NULL DEFAULT '' in inventory_locations — send "" rather than null
@Serializable
data class LocationInsert(
    val name: String,
    @SerialName("location_code") val locationCode: String,
    val description: String = ""
)

@Serializable
data class LocationIdOnly(val id: String)

// Location joined with a client-computed asset count — not a DB row shape.
data class LocationWithCount(
    val location: Location,
    val assetCount: Int
)
