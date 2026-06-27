# LF Scan

Minimal inventory scanning app for LF Creative. Shares the same Supabase project as `lfcrea-frontend`.

## Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navigation | Jetpack Navigation Compose |
| Networking | Supabase Kotlin client (`io.github.jan-tennert.supabase`) via Ktor |
| Camera | CameraX |
| Barcode scanning | ML Kit Barcode Scanning |
| DI | Hilt |
| Local storage | DataStore Preferences |
| Async | Coroutines + StateFlow |

---

## Project setup

### 1. Prerequisites

- Android Studio Hedgehog or later
- Android SDK 26+ installed
- A physical Android device (barcode scanning doesn't work well in emulator)

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

If the `gradle/wrapper/gradle-wrapper.jar` file is missing (it is gitignored by default in some setups), generate it:

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

Each team member has a 4-digit PIN stored in the `inventory_team_members` table in Supabase. The app queries this table (via the anon key + RLS policy) to authenticate on the PIN screen.

### Add a team member

Run this SQL in the Supabase SQL editor:

```sql
INSERT INTO inventory_team_members (name, pin, role)
VALUES ('Name Here', '1234', 'Member');
```

Roles: `Admin` or `Member`.

### Seeded members

| Name | PIN | Role |
|---|---|---|
| Liam Liew | 1234 | Admin |
| Chew Yu Fong | 5678 | Member |

### Security note

The anon key can read `inventory_team_members` because of the RLS policy:

```sql
CREATE POLICY "Anon can read team members for PIN auth"
  ON inventory_team_members FOR SELECT USING (true);
```

PINs are stored as plain text — sufficient for a low-stakes internal tool. If security requirements increase, hash the PINs and compare server-side.

---

## Screens

| Screen | Description |
|---|---|
| **PIN** | Entry point. Enter a 4-digit PIN to log in. |
| **Mode Select** | Choose Check Out / Check In / Update / Inquiry. |
| **Scanner** | Live camera feed. Scan barcodes. Manage scanned list. Commit. |
| **Commit Result** | Confirmation after a commit with a list of processed items. |

---

## Supabase tables used

| Table | Purpose |
|---|---|
| `inventory_team_members` | PIN-based login |
| `inventory_assets` | Asset catalogue (read + update status) |
| `inventory_locations` | Location list for Check In picker |
| `inventory_events` | Audit log — one row inserted per asset per commit |

---

## Adding locations

Locations for the Check In picker come from `inventory_locations`. Add rows:

```sql
INSERT INTO inventory_locations (id, name) VALUES
  (gen_random_uuid(), 'Studio A'),
  (gen_random_uuid(), 'Storage Room'),
  (gen_random_uuid(), 'Edit Suite');
```
