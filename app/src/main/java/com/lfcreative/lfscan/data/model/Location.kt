package com.lfcreative.lfscan.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Location(
    val id: String,
    val name: String,
    // Unique, required, same code-space convention as inventory_assets.asset_id but visually
    // distinguishable (new locations default to a "LOC-" prefix) so a resolver can tell asset
    // codes and location codes apart before checking which table they're in.
    val code: String,
    val description: String? = null,
    // Free-form hierarchy — ANY location can parent any other (no fixed "Room > Shelf" levels).
    // Walk this up to the root via LocationHierarchy to get a display path or detect cycles.
    @SerialName("parent_id") val parentId: String? = null,
    // Set when this location is also a physical container tracked in the asset catalogue (a
    // drybox, a case, a bin) — the linked row's asset_id in inventory_assets.
    @SerialName("linked_asset_id") val linkedAssetId: String? = null
)

// description is NOT NULL DEFAULT '' in inventory_locations — send "" rather than null
@Serializable
data class LocationInsert(
    val name: String,
    val code: String,
    val description: String = "",
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("linked_asset_id") val linkedAssetId: String? = null
)

@Serializable
data class LocationIdOnly(val id: String)

// Location joined with a client-computed asset count — not a DB row shape.
data class LocationWithCount(
    val location: Location,
    val assetCount: Int
)
