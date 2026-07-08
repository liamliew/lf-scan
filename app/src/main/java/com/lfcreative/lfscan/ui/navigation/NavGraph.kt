package com.lfcreative.lfscan.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lfcreative.lfscan.MainActivity
import com.lfcreative.lfscan.ui.screen.AssetDetailScreen
import com.lfcreative.lfscan.ui.screen.AssetsScreen
import com.lfcreative.lfscan.ui.screen.CommitResultScreen
import com.lfcreative.lfscan.ui.screen.ContainerDetailScreen
import com.lfcreative.lfscan.ui.screen.ContainerDetailViewModel
import com.lfcreative.lfscan.ui.screen.ContainersScreen
import com.lfcreative.lfscan.ui.screen.ContinueSessionScreen
import com.lfcreative.lfscan.ui.screen.CreateAssetScreen
import com.lfcreative.lfscan.ui.screen.HomeScreen
import com.lfcreative.lfscan.ui.screen.HomeViewModel
import com.lfcreative.lfscan.ui.screen.KioskAdminUnlockDialog
import com.lfcreative.lfscan.ui.screen.LocationDetailScreen
import com.lfcreative.lfscan.ui.screen.LocationScanScreen
import com.lfcreative.lfscan.ui.screen.LocationsScreen
import com.lfcreative.lfscan.ui.screen.ModeSelectScreen
import com.lfcreative.lfscan.ui.screen.PinScreen
import com.lfcreative.lfscan.ui.screen.RentDetailsScreen
import com.lfcreative.lfscan.ui.screen.ScanViewModel
import com.lfcreative.lfscan.ui.screen.ScannerScreen
import com.lfcreative.lfscan.ui.screen.ScannerTypeSelectScreen

sealed class Screen(val route: String) {
    object Pin : Screen("pin")
    object ContinueSession : Screen("continue_session")
    object Home : Screen("home")
    object ModeSelect : Screen("mode_select")
    object Locations : Screen("locations")
    object LocationDetail : Screen("location/{locationId}") {
        fun createRoute(locationId: String) = "location/$locationId"
    }

    object Containers : Screen("containers")

    // Nested graph — ContainerDetail and ContainerLocationScan share one ContainerDetailViewModel
    object ContainerFlow : Screen("container_flow/{containerId}") {
        fun createRoute(containerId: String) = "container_flow/$containerId"
    }
    object ContainerDetail : Screen("container/{containerId}")
    object ContainerLocationScan : Screen("container/{containerId}/location_scan/{mode}") {
        fun createRoute(containerId: String, mode: String) = "container/$containerId/location_scan/$mode"
    }

    // Nested graph — ScannerTypeSelect, LocationScan, Scanner, and CommitResult all share one ScanViewModel
    object Assets : Screen("assets")
    object AssetCreate : Screen("asset/create?locationId={locationId}") {
        fun createRoute(locationId: String? = null) =
            "asset/create" + (locationId?.let { "?locationId=$it" } ?: "")
    }
    object ScanFlow : Screen("scan_flow/{mode}") {
        fun createRoute(mode: String) = "scan_flow/$mode"
    }
    object ScannerTypeSelect : Screen("scanner_type_select/{mode}") {
        fun createRoute(mode: String) = "scanner_type_select/$mode"
    }
    object LocationScan : Screen("location_scan/{mode}/{scannerType}") {
        fun createRoute(mode: String, scannerType: String) = "location_scan/$mode/$scannerType"
    }
    object RentDetails : Screen("rent_details/{mode}/{scannerType}") {
        fun createRoute(mode: String, scannerType: String) = "rent_details/$mode/$scannerType"
    }
    object Scanner : Screen("scanner/{mode}/{scannerType}?locationId={locationId}") {
        fun createRoute(mode: String, scannerType: String, locationId: String? = null) =
            "scanner/$mode/$scannerType" + (locationId?.let { "?locationId=$it" } ?: "")
    }
    object CommitResult : Screen("commit_result/{mode}/{count}") {
        fun createRoute(mode: String, count: Int) = "commit_result/$mode/$count"
    }
    object AssetDetail : Screen("asset/{assetId}?created={created}") {
        fun createRoute(assetId: String, created: Boolean = false) = "asset/$assetId?created=$created"
    }
}

@Composable
fun LFScanNavGraph() {
    val navController = rememberNavController()
    val activity = LocalContext.current as MainActivity
    val isKioskModeActive by MainActivity.isKioskModeActive.collectAsState()
    val showAdminUnlockDialog by MainActivity.showAdminUnlockDialog.collectAsState()

    // Wired once, centrally, rather than per-screen: popping the whole back stack to the PIN
    // route also tears down every nested-graph ViewModel (ScanViewModel, ContainerDetailViewModel)
    // along the way, which is what actually satisfies "clear all state" — doing this per-screen
    // would silently miss logout on any screen someone forgot to wire it into.
    DisposableEffect(navController) {
        MainActivity.onSessionTimeout = {
            navController.navigate(Screen.Pin.route) {
                popUpTo(0) { inclusive = true }
            }
        }
        // Auto-lock (inactivity/screen-off): push the lock screen on top without popping
        // anything, so whatever screen — and its ViewModel state — is still there underneath
        // once the password is re-entered.
        MainActivity.onAutoLock = {
            navController.navigate(Screen.ContinueSession.route) {
                launchSingleTop = true
            }
        }
        onDispose {
            MainActivity.onSessionTimeout = null
            MainActivity.onAutoLock = null
        }
    }

    Box(Modifier) {
        NavHost(navController = navController, startDestination = Screen.Pin.route) {

        composable(Screen.Pin.route) {
            PinScreen(
                onSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Pin.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ContinueSession.route) {
            ContinueSessionScreen(
                onContinue = { navController.popBackStack() },
                onReturnToLogin = {
                    navController.navigate(Screen.Pin.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            val homeViewModel: HomeViewModel = hiltViewModel()
            HomeScreen(
                onNavigateToModeSelect = { navController.navigate(Screen.ModeSelect.route) },
                onNavigateToAssets = { navController.navigate(Screen.Assets.route) },
                onNavigateToLocations = { navController.navigate(Screen.Locations.route) },
                onNavigateToContainers = { navController.navigate(Screen.Containers.route) },
                onNavigateToActivity = { /* No Activity screen exists yet */ },
                onSignOut = {
                    homeViewModel.signOut {
                        navController.navigate(Screen.Pin.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                viewModel = homeViewModel
            )
        }

        composable(Screen.ModeSelect.route) {
            ModeSelectScreen(
                onModeSelected = { mode ->
                    navController.navigate(Screen.ScanFlow.createRoute(mode)) {
                        launchSingleTop = true
                    }
                },
                onSignOut = {
                    navController.navigate(Screen.Pin.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Assets.route) {
            AssetsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToAsset = { assetId ->
                    navController.navigate(Screen.AssetDetail.createRoute(assetId))
                },
                onNavigateToCreateAsset = {
                    navController.navigate(Screen.AssetCreate.createRoute())
                }
            )
        }

        composable(
            route = Screen.AssetCreate.route,
            arguments = listOf(navArgument("locationId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val locationId = backStackEntry.arguments?.getString("locationId")
            CreateAssetScreen(
                preselectedLocationId = locationId,
                onBack = { navController.popBackStack() },
                onAssetCreated = { assetId ->
                    navController.navigate(Screen.AssetDetail.createRoute(assetId, created = true)) {
                        popUpTo(Screen.AssetCreate.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Locations.route) {
            LocationsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLocation = { locationId ->
                    navController.navigate(Screen.LocationDetail.createRoute(locationId))
                }
            )
        }

        composable(
            route = Screen.LocationDetail.route,
            arguments = listOf(navArgument("locationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val locationId = backStackEntry.arguments?.getString("locationId") ?: ""
            LocationDetailScreen(
                locationId = locationId,
                onBack = { navController.popBackStack() },
                onNavigateToAsset = { assetId ->
                    navController.navigate(Screen.AssetDetail.createRoute(assetId))
                },
                onNavigateToCreateAsset = {
                    navController.navigate(Screen.AssetCreate.createRoute(locationId))
                }
            )
        }

        composable(Screen.Containers.route) {
            ContainersScreen(
                onBack = { navController.popBackStack() },
                onNavigateToContainer = { containerId ->
                    navController.navigate(Screen.ContainerFlow.createRoute(containerId))
                }
            )
        }

        // Nested graph — ContainerDetail's location-scan step for Check In/Update shares one
        // ContainerDetailViewModel so committing the operation reloads the same screen instance.
        navigation(
            startDestination = Screen.ContainerDetail.route,
            route = Screen.ContainerFlow.route
        ) {
            composable(
                route = Screen.ContainerDetail.route,
                arguments = listOf(navArgument("containerId") { type = NavType.StringType })
            ) { backStackEntry ->
                val containerId = backStackEntry.arguments?.getString("containerId") ?: ""
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Screen.ContainerFlow.createRoute(containerId))
                }
                val viewModel: ContainerDetailViewModel = hiltViewModel(parentEntry)
                ContainerDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToAsset = { assetId ->
                        navController.navigate(Screen.AssetDetail.createRoute(assetId))
                    },
                    onNavigateToLocationScan = { mode ->
                        navController.navigate(Screen.ContainerLocationScan.createRoute(containerId, mode))
                    }
                )
            }

            composable(
                route = Screen.ContainerLocationScan.route,
                arguments = listOf(
                    navArgument("containerId") { type = NavType.StringType },
                    navArgument("mode") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val containerId = backStackEntry.arguments?.getString("containerId") ?: ""
                val mode = backStackEntry.arguments?.getString("mode") ?: "check_in"
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Screen.ContainerFlow.createRoute(containerId))
                }
                val viewModel: ContainerDetailViewModel = hiltViewModel(parentEntry)
                // "update_location" is the container-operation mode; LocationScanScreen only
                // uses "mode" for its title/accent color, so map it to "update" for display.
                val displayMode = if (mode == "update_location") "update" else mode
                LocationScanScreen(
                    mode = displayMode,
                    scannerType = "camera",
                    onBack = { navController.popBackStack() },
                    onLocationConfirmed = { locationId ->
                        viewModel.commitOperation(mode, locationId) {
                            navController.popBackStack(Screen.ContainerDetail.route, inclusive = false)
                        }
                    }
                )
            }
        }

        // Nested graph — start dest is now ScannerTypeSelect
        navigation(
            startDestination = Screen.ScannerTypeSelect.route,
            route = Screen.ScanFlow.route
        ) {

            composable(
                route = Screen.ScannerTypeSelect.route,
                arguments = listOf(navArgument("mode") { type = NavType.StringType })
            ) { backStackEntry ->
                val mode = backStackEntry.arguments?.getString("mode") ?: "inquiry"
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Screen.ScanFlow.createRoute(mode))
                }
                val viewModel: ScanViewModel = hiltViewModel(parentEntry)
                ScannerTypeSelectScreen(
                    mode = mode,
                    onBack = {
                        viewModel.clearModeAndScannerType()
                        navController.popBackStack(Screen.ModeSelect.route, inclusive = false)
                    },
                    onScannerTypeSelected = { scannerType ->
                        when (mode) {
                            "check_in" -> navController.navigate(Screen.LocationScan.createRoute(mode, scannerType))
                            "rent_out" -> navController.navigate(Screen.RentDetails.createRoute(mode, scannerType))
                            else -> navController.navigate(Screen.Scanner.createRoute(mode, scannerType))
                        }
                    },
                    viewModel = viewModel
                )
            }

            composable(
                route = Screen.LocationScan.route,
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType },
                    navArgument("scannerType") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val mode = backStackEntry.arguments?.getString("mode") ?: "check_in"
                val scannerType = backStackEntry.arguments?.getString("scannerType") ?: "camera"
                LocationScanScreen(
                    mode = mode,
                    scannerType = scannerType,
                    onBack = { navController.popBackStack() },
                    onLocationConfirmed = { locationId ->
                        navController.navigate(Screen.Scanner.createRoute(mode, scannerType, locationId))
                    }
                )
            }

            composable(
                route = Screen.RentDetails.route,
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType },
                    navArgument("scannerType") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val mode = backStackEntry.arguments?.getString("mode") ?: "rent_out"
                val scannerType = backStackEntry.arguments?.getString("scannerType") ?: "camera"
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Screen.ScanFlow.createRoute(mode))
                }
                val viewModel: ScanViewModel = hiltViewModel(parentEntry)
                RentDetailsScreen(
                    onBack = { navController.popBackStack() },
                    onConfirmed = {
                        navController.navigate(Screen.Scanner.createRoute(mode, scannerType))
                    },
                    viewModel = viewModel
                )
            }

            composable(
                route = Screen.Scanner.route,
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType },
                    navArgument("scannerType") { type = NavType.StringType },
                    navArgument("locationId") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { backStackEntry ->
                val mode = backStackEntry.arguments?.getString("mode") ?: "inquiry"
                val scannerType = backStackEntry.arguments?.getString("scannerType") ?: "camera"
                val locationId = backStackEntry.arguments?.getString("locationId")
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Screen.ScanFlow.createRoute(mode))
                }
                val viewModel: ScanViewModel = hiltViewModel(parentEntry)
                ScannerScreen(
                    mode = mode,
                    scannerType = scannerType,
                    locationId = locationId,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onCommitSuccess = { count ->
                        navController.navigate(Screen.CommitResult.createRoute(mode, count)) {
                            popUpTo(Screen.Scanner.createRoute(mode, scannerType)) { inclusive = true }
                        }
                    },
                    onNavigateToAsset = { assetId ->
                        navController.navigate(Screen.AssetDetail.createRoute(assetId))
                    }
                )
            }

            composable(
                route = Screen.CommitResult.route,
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType },
                    navArgument("count") { type = NavType.IntType }
                )
            ) { backStackEntry ->
                val mode = backStackEntry.arguments?.getString("mode") ?: ""
                val count = backStackEntry.arguments?.getInt("count") ?: 0
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Screen.ScanFlow.createRoute(mode))
                }
                val viewModel: ScanViewModel = hiltViewModel(parentEntry)
                CommitResultScreen(
                    mode = mode,
                    count = count,
                    viewModel = viewModel,
                    onBackToMenu = {
                        navController.popBackStack(Screen.ModeSelect.route, inclusive = false)
                    }
                )
            }
        }

        composable(
            route = Screen.AssetDetail.route,
            arguments = listOf(
                navArgument("assetId") { type = NavType.StringType },
                navArgument("created") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val created = backStackEntry.arguments?.getBoolean("created") ?: false
            AssetDetailScreen(
                onBack = { navController.popBackStack() },
                showCreatedMessage = created
            )
        }
        }

        // Declared after NavHost so it sits on top of the back-press dispatcher stack and
        // intercepts before Navigation-Compose's own back handling — while Kiosk Mode is active,
        // back presses open the admin unlock gate instead of popping the back stack.
        BackHandler(enabled = isKioskModeActive) {
            MainActivity.showAdminUnlockDialog.value = true
        }

        if (showAdminUnlockDialog) {
            KioskAdminUnlockDialog(
                onCancel = { MainActivity.showAdminUnlockDialog.value = false },
                onUnlocked = {
                    MainActivity.showAdminUnlockDialog.value = false
                    activity.unlockKiosk()
                }
            )
        }
    }
}
