package com.lfcreative.lfscan.data.repository

import com.lfcreative.lfscan.data.model.Asset
import com.lfcreative.lfscan.data.model.InventoryEvent
import com.lfcreative.lfscan.data.model.InventoryEventInsert
import com.lfcreative.lfscan.data.model.Location
import com.lfcreative.lfscan.data.model.TeamMember
import com.lfcreative.lfscan.data.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepository @Inject constructor() {

    suspend fun getTeamMemberByPin(pin: String): TeamMember? =
        supabase.from("inventory_team_members")
            .select { filter { eq("pin", pin) } }
            .decodeSingleOrNull<TeamMember>()

    suspend fun getAssetByCode(assetId: String): Asset? =
        supabase.from("inventory_assets")
            .select { filter { eq("asset_id", assetId) } }
            .decodeSingleOrNull<Asset>()

    suspend fun getLocations(): List<Location> =
        supabase.from("inventory_locations")
            .select()
            .decodeList<Location>()

    suspend fun getLastEvent(assetUuid: String): InventoryEvent? =
        supabase.from("inventory_events")
            .select {
                filter { eq("asset_id", assetUuid) }
                order("created_at", Order.DESCENDING)
                limit(1)
            }
            .decodeSingleOrNull<InventoryEvent>()

    suspend fun updateAssetStatus(
        assetId: String,
        status: String,
        locationId: String? = null,
        userId: String? = null,
        userName: String? = null
    ) {
        supabase.from("inventory_assets")
            .update({
                set("status", status)
                set("current_location_id", locationId)
                set("current_user_id", userId)
                set("current_user_name", userName)
            }) {
                filter { eq("asset_id", assetId) }
            }
    }

    suspend fun insertEvent(event: InventoryEventInsert) {
        supabase.from("inventory_events").insert(event)
    }
}
