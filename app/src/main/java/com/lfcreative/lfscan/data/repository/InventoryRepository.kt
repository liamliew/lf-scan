package com.lfcreative.lfscan.data.repository

import com.lfcreative.lfscan.data.model.Asset
import com.lfcreative.lfscan.data.model.AssetIdOnly
import com.lfcreative.lfscan.data.model.AssetInsert
import com.lfcreative.lfscan.data.model.AssetTypeOnly
import com.lfcreative.lfscan.data.model.Container
import com.lfcreative.lfscan.data.model.ContainerEvent
import com.lfcreative.lfscan.data.model.ContainerEventInsert
import com.lfcreative.lfscan.data.model.ContainerIdOnly
import com.lfcreative.lfscan.data.model.ContainerInsert
import com.lfcreative.lfscan.data.model.ContainerWithCount
import com.lfcreative.lfscan.data.model.ContainerWithDetails
import com.lfcreative.lfscan.data.model.EmployeeSetting
import com.lfcreative.lfscan.data.model.EmployeeSettingUpsert
import com.lfcreative.lfscan.data.model.InventoryEvent
import com.lfcreative.lfscan.data.model.InventoryEventInsert
import com.lfcreative.lfscan.data.model.Location
import com.lfcreative.lfscan.data.model.LocationIdOnly
import com.lfcreative.lfscan.data.model.LocationInsert
import com.lfcreative.lfscan.data.model.LocationWithCount
import com.lfcreative.lfscan.data.model.TeamMember
import com.lfcreative.lfscan.data.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.PostgrestUpdate
import io.github.jan.supabase.storage.storage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepository @Inject constructor() {

    suspend fun getTeamMemberByPin(pin: String): TeamMember? =
        supabase.from("inventory_team_members")
            .select { filter { eq("pin", pin) } }
            .decodeSingleOrNull<TeamMember>()

    suspend fun getTeamMemberByPinAndPassword(pin: String, password: String): TeamMember? =
        supabase.from("inventory_team_members")
            .select { filter { eq("pin", pin); eq("password", password) } }
            .decodeSingleOrNull<TeamMember>()

    suspend fun getAssetByCode(assetId: String): Asset? =
        supabase.from("inventory_assets")
            .select { filter { eq("asset_id", assetId) } }
            .decodeSingleOrNull<Asset>()

    suspend fun getAllAssets(): List<Asset> =
        supabase.from("inventory_assets")
            .select()
            .decodeList<Asset>()

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

    // Only sets last_known_* when a GPS fix was actually captured this session (lat/lng both
    // non-null) — otherwise the columns are left untouched entirely (no set() call at all), so a
    // missed/denied GPS fix never overwrites a previously known location with null.
    private fun PostgrestUpdate.setLastKnownLocation(lat: Double?, lng: Double?, address: String?) {
        if (lat != null && lng != null) {
            set("last_known_lat", lat)
            set("last_known_lng", lng)
            set("last_known_address", address)
            set("last_known_at", java.time.Instant.now().toString())
        }
    }

    suspend fun updateAssetStatus(
        assetId: String,
        status: String,
        locationId: String? = null,
        userId: String? = null,
        userName: String? = null,
        renterContact: String? = null,
        rentalDueDate: String? = null,
        gpsLat: Double? = null,
        gpsLng: Double? = null,
        gpsAddress: String? = null
    ) {
        supabase.from("inventory_assets")
            .update({
                set("status", status)
                set("current_location_id", locationId)
                set("current_user_id", userId)
                set("current_user_name", userName)
                set("renter_contact", renterContact)
                set("rental_due_date", rentalDueDate)
                setLastKnownLocation(gpsLat, gpsLng, gpsAddress)
            }) {
                filter { eq("asset_id", assetId) }
            }
    }

    suspend fun insertEvent(event: InventoryEventInsert) {
        supabase.from("inventory_events").insert(event)
    }

    suspend fun getLocationById(locationId: String): Location? =
        supabase.from("inventory_locations")
            .select { filter { eq("id", locationId) } }
            .decodeSingleOrNull<Location>()

    suspend fun getLocationByCode(code: String): LocationWithCount? {
        val location = supabase.from("inventory_locations")
            .select { filter { eq("location_code", code) } }
            .decodeSingleOrNull<Location>() ?: return null
        return LocationWithCount(location, countAssetsForLocation(location.id))
    }

    suspend fun getLocationsWithCounts(): List<LocationWithCount> =
        supabase.from("inventory_locations")
            .select()
            .decodeList<Location>()
            .map { LocationWithCount(it, countAssetsForLocation(it.id)) }

    suspend fun countAssetsForLocation(locationId: String): Int =
        supabase.from("inventory_assets")
            .select(columns = Columns.list("asset_id")) {
                head = true
                count(Count.EXACT)
                filter { eq("current_location_id", locationId) }
            }
            .countOrNull()?.toInt() ?: 0

    suspend fun locationCodeExists(code: String): Boolean =
        supabase.from("inventory_locations")
            .select(columns = Columns.list("id")) { filter { eq("location_code", code) } }
            .decodeList<LocationIdOnly>()
            .isNotEmpty()

    suspend fun createLocation(name: String, code: String, description: String?): Location =
        supabase.from("inventory_locations")
            .insert(LocationInsert(name = name, locationCode = code, description = description ?: "")) { select() }
            .decodeSingle<Location>()

    suspend fun getAssetsByLocation(locationId: String): List<Asset> =
        supabase.from("inventory_assets")
            .select { filter { eq("current_location_id", locationId) } }
            .decodeList<Asset>()

    // "My items": assets currently held by this employee, matched by either their team-member id
    // (used when checked out via the app before Clerk linking) or their Clerk user id.
    suspend fun countAssetsHeldBy(employeeId: String, clerkUserId: String?): Int {
        val userIds = listOfNotNull(employeeId, clerkUserId).distinct()
        return supabase.from("inventory_assets")
            .select(columns = Columns.list("asset_id")) {
                head = true
                count(Count.EXACT)
                filter { isIn("current_user_id", userIds) }
            }
            .countOrNull()?.toInt() ?: 0
    }

    suspend fun countAssetsByStatus(status: String): Int =
        supabase.from("inventory_assets")
            .select(columns = Columns.list("asset_id")) {
                head = true
                count(Count.EXACT)
                filter { eq("status", status) }
            }
            .countOrNull()?.toInt() ?: 0

    suspend fun countOverdueRentals(): Int =
        supabase.from("inventory_assets")
            .select(columns = Columns.list("asset_id")) {
                head = true
                count(Count.EXACT)
                filter {
                    eq("status", "rented")
                    lt("rental_due_date", java.time.Instant.now().toString())
                }
            }
            .countOrNull()?.toInt() ?: 0

    suspend fun getEventsByAssetId(assetId: String): List<InventoryEvent> =
        supabase.from("inventory_events")
            .select {
                filter { eq("asset_id", assetId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<InventoryEvent>()

    // Global activity feed — most recent events across every asset, newest first.
    suspend fun getRecentEvents(limit: Long = 200): List<InventoryEvent> =
        supabase.from("inventory_events")
            .select {
                order("created_at", Order.DESCENDING)
                limit(limit)
            }
            .decodeList<InventoryEvent>()

    suspend fun updateAssetFull(asset: Asset) {
        supabase.from("inventory_assets")
            .update({
                set("name", asset.name)
                set("type", asset.type)
                set("size", asset.size)
                set("cost", asset.cost)
                set("status", asset.status)
                set("notes", asset.notes)
                set("current_location_id", asset.currentLocationId)
            }) {
                filter { eq("asset_id", asset.assetId) }
            }
    }

    suspend fun getNextAssetIdSuggestion(): String {
        val ids = supabase.from("inventory_assets")
            .select(columns = Columns.list("asset_id"))
            .decodeList<AssetIdOnly>()
            .map { it.assetId }

        val highest = ids.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 0
        return (highest + 1).toString().padStart(4, '0')
    }

    suspend fun getDistinctAssetTypes(): List<String> =
        supabase.from("inventory_assets")
            .select(columns = Columns.list("type"))
            .decodeList<AssetTypeOnly>()
            .mapNotNull { it.type?.takeIf { type -> type.isNotBlank() } }
            .distinct()
            .sorted()

    suspend fun assetIdExists(assetId: String): Boolean =
        supabase.from("inventory_assets")
            .select(columns = Columns.list("asset_id")) { filter { eq("asset_id", assetId) } }
            .decodeList<AssetIdOnly>()
            .isNotEmpty()

    suspend fun insertAsset(asset: AssetInsert) {
        supabase.from("inventory_assets").insert(asset)
    }

    suspend fun updateAssetPhotoUrls(assetId: String, photoUrls: List<String>) {
        supabase.from("inventory_assets")
            .update({ set("photo_urls", photoUrls) }) {
                filter { eq("asset_id", assetId) }
            }
    }

    suspend fun uploadAssetPhoto(assetId: String, fileName: String, bytes: ByteArray): String {
        val path = "assets/$assetId/${System.currentTimeMillis()}_$fileName"
        supabase.storage.from("inventory").upload(path, bytes)
        return supabase.storage.from("inventory").publicUrl(path)
    }

    suspend fun getEmployeeSetting(employeeId: String, key: String): String? =
        supabase.from("inventory_employee_settings")
            .select { filter { eq("employee_id", employeeId); eq("key", key) } }
            .decodeSingleOrNull<EmployeeSetting>()
            ?.value

    suspend fun upsertEmployeeSetting(employeeId: String, key: String, value: String) {
        supabase.from("inventory_employee_settings")
            .upsert(EmployeeSettingUpsert(employeeId = employeeId, key = key, value = value)) {
                onConflict = "employee_id,key"
            }
    }

    // ── Containers ───────────────────────────────────────────────────────────

    suspend fun getContainers(): List<Container> =
        supabase.from("inventory_containers")
            .select { order("name", Order.ASCENDING) }
            .decodeList<Container>()

    suspend fun getContainersWithCounts(): List<ContainerWithCount> =
        getContainers().map { ContainerWithCount(it, countAssetsForContainer(it.id)) }

    suspend fun getContainerById(id: String): ContainerWithDetails? {
        val container = supabase.from("inventory_containers")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull<Container>() ?: return null
        return buildContainerWithDetails(container)
    }

    suspend fun getContainerByCode(code: String): ContainerWithDetails? {
        val container = supabase.from("inventory_containers")
            .select { filter { eq("container_id", code) } }
            .decodeSingleOrNull<Container>() ?: return null
        return buildContainerWithDetails(container)
    }

    private suspend fun buildContainerWithDetails(container: Container): ContainerWithDetails {
        val locationName = container.currentLocationId?.let { getLocationById(it)?.name }
        val assets = getAssetsByContainer(container.id)
        return ContainerWithDetails(
            container = container,
            locationName = locationName,
            assetCount = assets.size,
            assets = assets
        )
    }

    suspend fun getAssetsByContainer(containerId: String): List<Asset> =
        supabase.from("inventory_assets")
            .select { filter { eq("container_id", containerId) } }
            .decodeList<Asset>()

    suspend fun countAssetsForContainer(containerId: String): Int =
        supabase.from("inventory_assets")
            .select(columns = Columns.list("asset_id")) {
                head = true
                count(Count.EXACT)
                filter { eq("container_id", containerId) }
            }
            .countOrNull()?.toInt() ?: 0

    suspend fun containerCodeExists(code: String): Boolean =
        supabase.from("inventory_containers")
            .select(columns = Columns.list("container_id")) { filter { eq("container_id", code) } }
            .decodeList<ContainerIdOnly>()
            .isNotEmpty()

    suspend fun getNextContainerIdSuggestion(): String {
        val ids = supabase.from("inventory_containers")
            .select(columns = Columns.list("container_id"))
            .decodeList<ContainerIdOnly>()
            .map { it.containerId }

        val highest = ids.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 0
        return (highest + 1).toString().padStart(4, '0')
    }

    suspend fun countContainersByStatus(status: String): Int =
        supabase.from("inventory_containers")
            .select(columns = Columns.list("id")) {
                head = true
                count(Count.EXACT)
                filter { eq("status", status) }
            }
            .countOrNull()?.toInt() ?: 0

    suspend fun createContainer(
        containerId: String,
        name: String,
        description: String?,
        performedBy: TeamMember
    ): Container {
        val container = supabase.from("inventory_containers")
            .insert(ContainerInsert(containerId = containerId, name = name, description = description)) { select() }
            .decodeSingle<Container>()
        insertContainerEvent(
            ContainerEventInsert(
                containerId = container.id,
                eventType = "created",
                performedBy = performedBy.clerkUserId ?: performedBy.id,
                performedByName = performedBy.name
            )
        )
        return container
    }

    suspend fun updateContainer(id: String, name: String, description: String?) {
        supabase.from("inventory_containers")
            .update({
                set("name", name)
                set("description", description)
            }) {
                filter { eq("id", id) }
            }
    }

    suspend fun addAssetToContainer(assetId: String, containerId: String, performedBy: TeamMember) {
        supabase.from("inventory_assets")
            .update({
                set("container_id", containerId)
                set("container_locked", true)
            }) {
                filter { eq("asset_id", assetId) }
            }
        insertContainerEvent(
            ContainerEventInsert(
                containerId = containerId,
                eventType = "asset_added",
                performedBy = performedBy.clerkUserId ?: performedBy.id,
                performedByName = performedBy.name,
                notes = "Added asset $assetId"
            )
        )
    }

    suspend fun removeAssetFromContainer(
        assetId: String,
        containerId: String,
        performedBy: TeamMember,
        forced: Boolean
    ) {
        val clearedContainerId: String? = null
        supabase.from("inventory_assets")
            .update({
                set("container_id", clearedContainerId)
                set("container_locked", false)
            }) {
                filter { eq("asset_id", assetId) }
            }
        insertContainerEvent(
            ContainerEventInsert(
                containerId = containerId,
                eventType = "asset_removed",
                performedBy = performedBy.clerkUserId ?: performedBy.id,
                performedByName = performedBy.name,
                notes = if (forced) "Force removed asset $assetId" else "Removed asset $assetId"
            )
        )
    }

    suspend fun getContainerEvents(containerId: String): List<ContainerEvent> =
        supabase.from("inventory_container_events")
            .select {
                filter { eq("container_id", containerId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<ContainerEvent>()

    private suspend fun insertContainerEvent(event: ContainerEventInsert) {
        supabase.from("inventory_container_events").insert(event)
    }

    // Applies check_out / check_in / update_location to a container AND every asset inside it
    // in one operation. Per-asset inventory_events use the EXISTING event_type values
    // ("checkout"/"checkin"/"location") — NOT the container_events vocabulary
    // ("check_out"/"check_in"/"location_update"), since inventory_events has its own,
    // already-established CHECK constraint.
    // userId/userName are always the employee who performed the action (used for event-log
    // attribution). holderName is who the asset ends up "with" — normally the same employee
    // (check_out), but an external renter's name for rent_out; defaults to userName so
    // check_out/check_in behavior is unchanged when not specified.
    suspend fun commitContainerOperation(
        containerId: String,
        mode: String,
        locationId: String?,
        userId: String,
        userName: String,
        gpsLat: Double? = null,
        gpsLng: Double? = null,
        gpsAddress: String? = null,
        holderName: String? = null,
        renterContact: String? = null,
        rentalDueDate: String? = null
    ) {
        val holder = holderName ?: userName
        val noLocation: String? = null
        val noUser: String? = null
        val containerAssets = getAssetsByContainer(containerId)
        val locationName = locationId?.let { getLocationById(it)?.name }
        // containerId (param) is the UUID primary key; the note should show the human-readable
        // 4-digit code shown to users elsewhere in the app.
        val containerCode = supabase.from("inventory_containers")
            .select(columns = Columns.list("container_id")) { filter { eq("id", containerId) } }
            .decodeSingleOrNull<ContainerIdOnly>()
            ?.containerId ?: containerId

        when (mode) {
            "check_out" -> {
                supabase.from("inventory_containers")
                    .update({
                        set("status", "checked_out")
                        set("current_user_id", userId)
                        set("current_user_name", holder)
                        set("current_location_id", noLocation)
                        set("renter_contact", noUser)
                        set("rental_due_date", noUser)
                    }) { filter { eq("id", containerId) } }

                supabase.from("inventory_assets")
                    .update({
                        set("status", "checked_out")
                        set("current_user_id", userId)
                        set("current_user_name", holder)
                        set("current_location_id", noLocation)
                        set("renter_contact", noUser)
                        set("rental_due_date", noUser)
                        setLastKnownLocation(gpsLat, gpsLng, gpsAddress)
                    }) { filter { eq("container_id", containerId) } }

                containerAssets.forEach { asset ->
                    insertEvent(
                        InventoryEventInsert(
                            assetId = asset.assetId,
                            eventType = "checkout",
                            performedBy = userId,
                            performedByName = userName,
                            note = "Via container $containerCode"
                        )
                    )
                }
                insertContainerEvent(
                    ContainerEventInsert(
                        containerId = containerId,
                        eventType = "check_out",
                        performedBy = userId,
                        performedByName = userName,
                        gpsLat = gpsLat,
                        gpsLng = gpsLng,
                        gpsAddress = gpsAddress
                    )
                )
            }

            "rent_out" -> {
                supabase.from("inventory_containers")
                    .update({
                        set("status", "rented")
                        set("current_user_id", noUser)
                        set("current_user_name", holder)
                        set("current_location_id", noLocation)
                        set("renter_contact", renterContact)
                        set("rental_due_date", rentalDueDate)
                    }) { filter { eq("id", containerId) } }

                supabase.from("inventory_assets")
                    .update({
                        set("status", "rented")
                        set("current_user_id", noUser)
                        set("current_user_name", holder)
                        set("current_location_id", noLocation)
                        set("renter_contact", renterContact)
                        set("rental_due_date", rentalDueDate)
                        setLastKnownLocation(gpsLat, gpsLng, gpsAddress)
                    }) { filter { eq("container_id", containerId) } }

                containerAssets.forEach { asset ->
                    insertEvent(
                        InventoryEventInsert(
                            assetId = asset.assetId,
                            eventType = "rented",
                            performedBy = userId,
                            performedByName = userName,
                            note = "Via container $containerCode"
                        )
                    )
                }
                insertContainerEvent(
                    ContainerEventInsert(
                        containerId = containerId,
                        eventType = "rented",
                        performedBy = userId,
                        performedByName = userName,
                        gpsLat = gpsLat,
                        gpsLng = gpsLng,
                        gpsAddress = gpsAddress
                    )
                )
            }

            "check_in" -> {
                supabase.from("inventory_containers")
                    .update({
                        set("status", "available")
                        set("current_user_id", noUser)
                        set("current_user_name", noUser)
                        set("current_location_id", locationId)
                        set("renter_contact", noUser)
                        set("rental_due_date", noUser)
                    }) { filter { eq("id", containerId) } }

                supabase.from("inventory_assets")
                    .update({
                        set("status", "available")
                        set("current_user_id", noUser)
                        set("current_user_name", noUser)
                        set("current_location_id", locationId)
                        set("renter_contact", noUser)
                        set("rental_due_date", noUser)
                        setLastKnownLocation(gpsLat, gpsLng, gpsAddress)
                    }) { filter { eq("container_id", containerId) } }

                containerAssets.forEach { asset ->
                    insertEvent(
                        InventoryEventInsert(
                            assetId = asset.assetId,
                            eventType = "checkin",
                            performedBy = userId,
                            performedByName = userName,
                            locationId = locationId,
                            note = "Via container $containerCode"
                        )
                    )
                }
                insertContainerEvent(
                    ContainerEventInsert(
                        containerId = containerId,
                        eventType = "check_in",
                        performedBy = userId,
                        performedByName = userName,
                        locationId = locationId,
                        locationName = locationName,
                        gpsLat = gpsLat,
                        gpsLng = gpsLng,
                        gpsAddress = gpsAddress
                    )
                )
            }

            "update_location" -> {
                supabase.from("inventory_containers")
                    .update({ set("current_location_id", locationId) }) {
                        filter { eq("id", containerId) }
                    }

                supabase.from("inventory_assets")
                    .update({
                        set("current_location_id", locationId)
                        setLastKnownLocation(gpsLat, gpsLng, gpsAddress)
                    }) {
                        filter { eq("container_id", containerId) }
                    }

                containerAssets.forEach { asset ->
                    insertEvent(
                        InventoryEventInsert(
                            assetId = asset.assetId,
                            eventType = "location",
                            performedBy = userId,
                            performedByName = userName,
                            locationId = locationId,
                            note = "Via container $containerCode"
                        )
                    )
                }
                insertContainerEvent(
                    ContainerEventInsert(
                        containerId = containerId,
                        eventType = "location_update",
                        performedBy = userId,
                        performedByName = userName,
                        locationId = locationId,
                        locationName = locationName,
                        gpsLat = gpsLat,
                        gpsLng = gpsLng,
                        gpsAddress = gpsAddress
                    )
                )
            }
        }
    }
}
