# LF Scan

Inventory scanning app for LF Creative's crew — check gear in/out, track kit-bag "containers",
rent gear to clients, and run stock audits at a location. Shares the same Supabase project as
`lfcrea-frontend`.

## Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navigation | Jetpack Navigation Compose |
| Networking | Supabase Kotlin client (`io.github.jan-tennert.supabase`) via Ktor |
| Camera | CameraX |
| Barcode scanning | ML Kit Barcode Scanning (camera) + Zebra DataWedge (internal/external hardware scanners) |
| DI | Hilt |
| Local storage | DataStore Preferences (session/settings) + Room (offline cache/queue) |
| Background sync | WorkManager |
| Async | Coroutines + StateFlow |

---

## Project setup

### 1. Prerequisites

- Android Studio Hedgehog or later
- Android SDK 26+ installed
- A physical Android device (barcode scanning doesn't work well in emulator; hardware-scanner
  features require a Zebra TC15 or similar DataWedge-capable device)

### 2. Clone and open

```bash
cd lf-inventory/lf-scan
# Open this folder in Android Studio
```

### 3. Add Supabase credentials to `local.properties`

`local.properties` is gitignored. Open or create it in the project root and fill in:

```properties
sdk.dir=/Users/your-username/Library/Android/sdk

SUPABASE_URL=https://vxgtywdqijtfnkgyubkh.supabase.co
SUPABASE_ANON_KEY=<your-anon-key>
```

You can find these values in the Supabase dashboard under **Project Settings → API**.

These are injected at build time as `BuildConfig.SUPABASE_URL` and `BuildConfig.SUPABASE_ANON_KEY`.

### 4. Gradle wrapper

If the `gradle/wrapper/gradle-wrapper.jar` file is missing (it is gitignored by default in some
setups), generate it:

```bash
gradle wrapper --gradle-version 8.9
```

Or simply open the project in Android Studio — it will offer to download the wrapper automatically.

### 5. Build and run

Connect a physical device with USB debugging enabled, then:

```bash
./gradlew installDebug
```

Or press **Run** in Android Studio.

---

## PIN system

Each team member has a 4-digit PIN stored in the `inventory_team_members` table in Supabase, plus
an optional password for a second login step. The app queries this table (via the anon key + RLS
policy) to authenticate on the PIN screen.

### Add a team member

Run this SQL in the Supabase SQL editor:

```sql
INSERT INTO inventory_team_members (name, pin, role)
VALUES ('Name Here', '1234', 'Member');
```

Roles: `Admin` or `Member`. Set `password` too if you want the second login step for that member.

### Security note

The anon key can read/write every `inventory_*` table — RLS policies on these tables are
permissive (`USING (true)`), and PINs/passwords are stored as plain text. This is intentional for
a low-stakes internal tool used only by trusted crew on managed devices, not a gap to "fix"
casually — if requirements change, hash credentials and tighten RLS deliberately.

---

## Screens

| Screen | Description |
|---|---|
| **PIN** | Entry point — 4-digit ID, optional password step. Hardware scanner and NFC can fill either field. |
| **Continue Session** | Shown after a 3-minute inactivity timeout or screen-off; re-enter password to resume exactly where you left off. |
| **Home** | Stats (my items, checked out, containers out, rented out) + action grid (Scan, Inventory, Locations, Activity, Containers, Audit, Audit History). |
| **Mode Select** | Choose Bulk Check-Out / Bulk Check-In / Check-In (single-item) / Update / Inquiry, plus a "Scan" FAB for a quick camera-only lookup. |
| **Scanner Type Select** | Camera / External / Internal scanner, or auto-skipped per the employee's default-scanner setting. |
| **Location Scan** | Scan/select a location code, with a searchable dropdown fallback (Bulk Check-In, single-item Check-In, and Container check-in/update). |
| **Scanner** | Live scan session — camera preview or hardware-scanner circle/trigger, running item list, GPS capture, commit. |
| **Commit Result** | Confirmation after a commit, with the list of processed items. |
| **Assets** | Searchable/filterable asset catalogue. |
| **Asset Detail** | Full asset record, event history, photos. |
| **Create Asset** | New asset form. |
| **Locations** / **Location Detail** / **Location Form** | Location list shown as an indented parent/child tree, per-location asset list, and an Add/Edit form (name, code, parent, optional linked container asset). |
| **Containers** / **Container Detail** | Kit-bag grouping of assets — check-out/check-in/update applies to the container and every asset inside it. |
| **Audit** (Setup → Scan → Results) / **Audit History** | Snapshot what's expected at a location, scan to reconcile matched/missing/unexpected, export a text report. |
| **Activity** | Global event feed across every asset. |
| **Pending Sync** | Lists offline-queued operations (pending/failed), manual retry, "Sync Now". |
| **Settings** | Illumination/scan-trigger defaults, default scanner type, Kiosk Mode toggle. |

---

## Offline mode

The app works with no internet connection for the core scan operations (check-in, check-out,
update, and their container equivalents): writes are queued locally in a Room database and synced
to Supabase automatically via WorkManager once connectivity returns. A status chip appears in the
scanner bottom bar and on Home when offline or when a sync is still catching up, and **Pending
Sync** (reachable by tapping that chip, or from Home) lists queued/failed operations with a manual
retry.

Reads (asset/container lookup, the assets list) fall back to a locally cached copy when offline.
Audit creation/scanning/completion also work offline, queuing the same way.

Location creation/editing (the Locations admin screen) is online-only — it isn't part of the
offline queue.

---

## Supabase tables used

| Table | Purpose |
|---|---|
| `inventory_team_members` | PIN/password login |
| `inventory_employee_settings` | Per-employee preferences (scanner defaults, Kiosk Mode, etc.) |
| `inventory_assets` | Asset catalogue (read + update status/location/holder) |
| `inventory_locations` | Locations for the Check In / Audit pickers — free-form parent/child hierarchy (`parent_id`), a required unique scannable `code`, and an optional `linked_asset_id` for locations that are also physical containers tracked in `inventory_assets` |
| `inventory_events` | Per-asset audit log — one row inserted per asset per commit |
| `inventory_containers` | Kit-bag containers grouping multiple assets |
| `inventory_container_events` | Per-container audit log |
| `inventory_audits` | Stock-audit sessions (location, totals, status) |
| `inventory_audit_items` | Per-asset result within an audit (matched/missing/unexpected) |

`inventory_sessions` / `inventory_session_items` also exist in the database but are not used by
the current app — leftover from an earlier design.

---

## Adding locations

Locations for the Check In / Audit pickers come from `inventory_locations`. The primary way to
add or edit one is now in-app: **Home → Locations → the "+" FAB** (or tap a location → the edit
icon), which opens a plain form for `name`, `code`, an optional parent location (any location can
parent any other — no fixed "Room > Shelf" levels, and the picker excludes a location's own
descendants so it can't become its own ancestor), and an optional linked container asset.

Locations can still be added directly via SQL when that's more convenient (seeding a batch of
them, for example):

```sql
INSERT INTO inventory_locations (id, name, code, parent_id) VALUES
  (gen_random_uuid(), 'Studio A', 'LOC-STA1', NULL),
  (gen_random_uuid(), 'Storage Room', 'LOC-STOR', NULL),
  (gen_random_uuid(), 'Edit Suite', 'LOC-EDIT', NULL);
```

`code` is what a printed/scanned location tag encodes — required and unique (new rows default to
a generated `LOC-`-prefixed value if omitted, but the app always sets one explicitly). Set
`parent_id` to another location's `id` to nest it under that location; leave it `NULL` for a
root-level location. `linked_asset_id` (an `inventory_assets.asset_id`) marks a location that's
also a physical container tracked in the asset catalogue — a drybox, a case, a bin.
