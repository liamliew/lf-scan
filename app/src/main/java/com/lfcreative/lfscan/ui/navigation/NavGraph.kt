package com.lfcreative.lfscan.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.lfcreative.lfscan.ui.screen.ActivityScreen
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
import com.lfcreative.lfscan.ui.screen.LocationFormScreen
import com.lfcreative.lfscan.ui.screen.LocationScanScreen
import com.lfcreative.lfscan.ui.screen.LocationsScreen
import com.lfcreative.lfscan.ui.screen.AuditHistoryScreen
import com.lfcreative.lfscan.ui.screen.AuditResultsScreen
import com.lfcreative.lfscan.ui.screen.AuditScanScreen
import com.lfcreative.lfscan.ui.screen.AuditSetupScreen
import com.lfcreative.lfscan.ui.screen.ModeSelectScreen
import com.lfcreative.lfscan.ui.screen.PendingSyncScreen
import com.lfcreative.lfscan.ui.screen.FloatingResultViewModel
import com.lfcreative.lfscan.ui.screen.FloatingResultWindow
import com.lfcreative.lfscan.ui.screen.PinScreen
import com.lfcreative.lfscan.ui.screen.QuickInquiryScreen
import com.lfcreative.lfscan.ui.screen.RepeatCheckInScreen
import com.lfcreative.lfscan.ui.screen.ReturnDateSelectScreen
import com.lfcreative.lfscan.ui.screen.ScanViewModel
import com.lfcreative.lfscan.ui.screen.ScannerScreen
import com.lfcreative.lfscan.ui.screen.ScannerTypeSelectScreen

sealed class Screen(val route: String) {
    object Pin : Screen("pin")
    object ContinueSession : Screen("continue_session")
    object Home : Screen("home")
    object ModeSelect : Screen("mode_select")
    object Activity : Screen("activity")
    object Locations : Screen("locations")
    object LocationDetail : Screen("location/{locationId}") {
        fun createRoute(locationId: String) = "location/$locationId"
    }
    // "new" as the path segment means Add rather than Edit — see LocationFormScreen.
    object LocationForm : Screen("location_form/{locationId}") {
        fun createRoute(locationId: String? = null) = "location_form/${locationId ?: "new"}"
    }

    object Containers : Screen("containers")
    object PendingSync : Screen("pending_sync")

    object AuditSetup : Screen("audit")
    object AuditHistory : Screen("audit/history")
    object AuditScan : Screen("audit/{auditId}") {
        fun createRoute(auditId: String) = "audit/$auditId"
    }
    object AuditResults : Screen("audit/{auditId}/results") {
        fun createRoute(auditId: String) = "audit/$auditId/results"
    }

    // Nested graph — ContainerDetail and ContainerLocationScan share one ContainerDetailViewModel
    object ContainerFlow : Screen("container_flow/{containerId}") {
        fun createRoute(containerId: String) = "container_flow/$containerId"
    }
    object ContainerDetail : Screen("container/{containerId}")
    object ContainerScannerTypeSelect : Screen("container/{containerId}/scanner_type_select/{mode}") {
        fun createRoute(containerId: String, mode: String) = "container/$containerId/scanner_type_select/$mode"
    }
    object ContainerLocationScan : Screen("container/{containerId}/location_scan/{mode}/{scannerType}") {
        fun createRoute(containerId: String, mode: String, scannerType: String) =
            "container/$containerId/location_scan/$mode/$scannerType"
    }

    // FAB "quick scan" shortcut on Mode Select / Assets — camera-only asset lookup, skips
    // ScannerTypeSelect entirely. Not part of the scan_flow nested graph (see QuickInquiryScreen).
    object QuickInquiry : Screen("quick_inquiry")

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
    // Single-item, repeating "Check-In" mode (mode key "check_in_repeat") — its own screen,
    // distinct from Bulk Check-In's Scanner + LocationScan pair.
    object RepeatCheckIn : Screen("repeat_check_in/{mode}/{scannerType}") {
        fun createRoute(mode: String, scannerType: String) = "repeat_check_in/$mode/$scannerType"
    }
    // Bulk Check-Out's "estimated return date" step, reached from Scanner's "Next" button.
    object ReturnDateSelect : Screen("return_date_select/{mode}/{scannerType}") {
        fun createRoute(mode: String, scannerType: String) = "return_date_select/$mode/$scannerType"
    }
    object Scanner : Screen("scanner/{mode}/{scannerType}") {
        fun createRoute(mode: String, scannerType: String) = "scanner/$mode/$scannerType"
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

    // Activity-scoped (not per-destination) — the single source of truth for the shared
    // FloatingResultWindow, hosted below at the root so it can appear over any screen.
    val floatingResultViewModel: FloatingResultViewModel = hiltViewModel(activity)
    val floatingResultContent by floatingResultViewModel.content.collectAsState()
    val inquiryResults by floatingResultViewModel.inquiryResults.collectAsState()

    // Wired once, centrally, rather than per-screen: popping the whole back stack to the PIN
    // route also tears down every nested-graph ViewModel (ScanViewModel, ContainerDetailViewModel)
    // along the way, which is what actually satisfies "clear all state" — doing this per-screen
    // would silently miss logout on any screen someone forgot to wire it into.
    DisposableEffect(navController) {
        MainActivity.onSessionTimeout = {
            floatingResultViewModel.dismiss()
            floatingResultViewModel.clearInquiryResults()
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

    // fillMaxSize (rather than the previous bare Modifier) so FloatingResultWindow — a plain
    // composable, not a Dialog — has definite full-screen bounds to bottom-align its card within,
    // regardless of whatever the window/theme wrapper above this would otherwise imply.
    Box(Modifier.fillMaxSize()) {
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
                onNavigateToActivity = { navController.navigate(Screen.Activity.route) },
                onNavigateToPendingSync = { navController.navigate(Screen.PendingSync.route) },
                onNavigateToAudit = { navController.navigate(Screen.AuditSetup.route) },
                onNavigateToAuditHistory = { navController.navigate(Screen.AuditHistory.route) },
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

        composable(Screen.Activity.route) {
            ActivityScreen(
                onBack = { navController.popBackStack() },
                onNavigateToAsset = { assetId ->
                    navController.navigate(Screen.AssetDetail.createRoute(assetId))
                }
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
                },
                onQuickScan = { navController.navigate(Screen.QuickInquiry.route) }
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
                },
                onQuickScan = { navController.navigate(Screen.QuickInquiry.route) }
            )
        }

        composable(Screen.QuickInquiry.route) {
            QuickInquiryScreen(
                onBack = { navController.popBackStack() },
                onViewDetails = { assetId ->
                    navController.navigate(Screen.AssetDetail.createRoute(assetId)) {
                        popUpTo(Screen.QuickInquiry.route) { inclusive = true }
                    }
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
                },
                onAddLocation = {
                    navController.navigate(Screen.LocationForm.createRoute(null))
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
                },
                onEditLocation = {
                    navController.navigate(Screen.LocationForm.createRoute(locationId))
                }
            )
        }

        composable(
            route = Screen.LocationForm.route,
            arguments = listOf(navArgument("locationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getString("locationId") ?: "new"
            val locationId = rawId.takeUnless { it == "new" }
            LocationFormScreen(
                locationId = locationId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onViewAsset = { assetId -> navController.navigate(Screen.AssetDetail.createRoute(assetId)) }
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

        composable(Screen.PendingSync.route) {
            PendingSyncScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AuditSetup.route) {
            AuditSetupScreen(
                onBack = { navController.popBackStack() },
                onAuditStarted = { auditId ->
                    navController.navigate(Screen.AuditScan.createRoute(auditId)) {
                        popUpTo(Screen.AuditSetup.route) { inclusive = true }
                    }
                }
            )
        }

        // Registered before AuditScan's templated route so "audit/history" always resolves to
        // this exact destination rather than the {auditId} pattern.
        composable(Screen.AuditHistory.route) {
            AuditHistoryScreen(
                onBack = { navController.popBackStack() },
                onNavigateToResults = { auditId ->
                    navController.navigate(Screen.AuditResults.createRoute(auditId))
                },
                onResumeAudit = { auditId ->
                    navController.navigate(Screen.AuditScan.createRoute(auditId))
                }
            )
        }

        composable(
            route = Screen.AuditScan.route,
            arguments = listOf(navArgument("auditId") { type = NavType.StringType })
        ) {
            AuditScanScreen(
                onBack = { navController.popBackStack() },
                onFinished = { auditId ->
                    navController.navigate(Screen.AuditResults.createRoute(auditId)) {
                        popUpTo(Screen.AuditSetup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.AuditResults.route,
            arguments = listOf(navArgument("auditId") { type = NavType.StringType })
        ) {
            AuditResultsScreen(
                onDone = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
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
                        navController.navigate(Screen.ContainerScannerTypeSelect.createRoute(containerId, mode))
                    }
                )
            }

            composable(
                route = Screen.ContainerScannerTypeSelect.route,
                arguments = listOf(
                    navArgument("containerId") { type = NavType.StringType },
                    navArgument("mode") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val containerId = backStackEntry.arguments?.getString("containerId") ?: ""
                val mode = backStackEntry.arguments?.getString("mode") ?: "check_in"
                // "update_location" is the container-operation mode; ScannerTypeSelectScreen only
                // uses "mode" for its title/accent color, so map it to "update" for display.
                val displayMode = if (mode == "update_location") "update" else mode
                val goToLocationScan: (String, Boolean) -> Unit = { scannerType, popSelf ->
                    navController.navigate(Screen.ContainerLocationScan.createRoute(containerId, mode, scannerType)) {
                        if (popSelf) popUpTo(Screen.ContainerScannerTypeSelect.createRoute(containerId, mode)) { inclusive = true }
                    }
                }
                ScannerTypeSelectScreen(
                    mode = displayMode,
                    onBack = { navController.popBackStack() },
                    onScannerTypeSelected = { scannerType -> goToLocationScan(scannerType, false) },
                    onScannerTypeAutoSelected = { scannerType -> goToLocationScan(scannerType, true) }
                )
            }

            composable(
                route = Screen.ContainerLocationScan.route,
                arguments = listOf(
                    navArgument("containerId") { type = NavType.StringType },
                    navArgument("mode") { type = NavType.StringType },
                    navArgument("scannerType") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val containerId = backStackEntry.arguments?.getString("containerId") ?: ""
                val mode = backStackEntry.arguments?.getString("mode") ?: "check_in"
                val scannerType = backStackEntry.arguments?.getString("scannerType") ?: "camera"
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Screen.ContainerFlow.createRoute(containerId))
                }
                val viewModel: ContainerDetailViewModel = hiltViewModel(parentEntry)
                // "update_location" is the container-operation mode; LocationScanScreen only
                // uses "mode" for its title/accent color, so map it to "update" for display.
                val displayMode = if (mode == "update_location") "update" else mode
                LocationScanScreen(
                    mode = displayMode,
                    scannerType = scannerType,
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
                val goToNextScreen: (String, Boolean) -> Unit = { scannerType, popSelf ->
                    val route = when (mode) {
                        // Bulk Check-In now scans items first, then advances to the location step
                        // via ScannerScreen's "Next" button — same as every other bulk mode, it
                        // goes straight to Scanner.
                        "check_in_repeat" -> Screen.RepeatCheckIn.createRoute(mode, scannerType)
                        else -> Screen.Scanner.createRoute(mode, scannerType)
                    }
                    navController.navigate(route) {
                        if (popSelf) popUpTo(Screen.ScannerTypeSelect.createRoute(mode)) { inclusive = true }
                    }
                }
                ScannerTypeSelectScreen(
                    mode = mode,
                    onBack = {
                        viewModel.clearModeAndScannerType()
                        navController.popBackStack(Screen.ModeSelect.route, inclusive = false)
                    },
                    onScannerTypeSelected = { scannerType -> goToNextScreen(scannerType, false) },
                    onScannerTypeAutoSelected = { scannerType -> goToNextScreen(scannerType, true) },
                    viewModel = viewModel
                )
            }

            // Reached from Scanner's "Next" button, AFTER items are already scanned (Bulk
            // Check-In only — this is the only mode that routes here). Resolving a location here
            // now commits every scanned item to it directly, rather than navigating forward to
            // Scanner the way it used to when this step came before scanning.
            composable(
                route = Screen.LocationScan.route,
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType },
                    navArgument("scannerType") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val mode = backStackEntry.arguments?.getString("mode") ?: "check_in"
                val scannerType = backStackEntry.arguments?.getString("scannerType") ?: "camera"
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Screen.ScanFlow.createRoute(mode))
                }
                val viewModel: ScanViewModel = hiltViewModel(parentEntry)
                val currentMember by viewModel.currentMember.collectAsState(initial = null)
                LocationScanScreen(
                    mode = mode,
                    scannerType = scannerType,
                    onBack = { navController.popBackStack() },
                    onLocationConfirmed = { locationId ->
                        viewModel.setLocation(locationId)
                        currentMember?.let { member ->
                            viewModel.commitSession(mode, member) { count ->
                                navController.navigate(Screen.CommitResult.createRoute(mode, count)) {
                                    popUpTo(Screen.Scanner.createRoute(mode, scannerType)) { inclusive = true }
                                }
                            }
                        }
                    }
                )
            }

            // Bulk Check-Out's "estimated return date" step, reached from Scanner's "Next"
            // button after items are already scanned.
            composable(
                route = Screen.ReturnDateSelect.route,
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType },
                    navArgument("scannerType") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val mode = backStackEntry.arguments?.getString("mode") ?: "check_out"
                val scannerType = backStackEntry.arguments?.getString("scannerType") ?: "camera"
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Screen.ScanFlow.createRoute(mode))
                }
                val viewModel: ScanViewModel = hiltViewModel(parentEntry)
                val currentMember by viewModel.currentMember.collectAsState(initial = null)
                val state by viewModel.state.collectAsState()
                ReturnDateSelectScreen(
                    itemCount = state.scannedItems.size,
                    onBack = { navController.popBackStack() },
                    onConfirmed = {
                        currentMember?.let { member ->
                            viewModel.commitSession(mode, member) { count ->
                                navController.navigate(Screen.CommitResult.createRoute(mode, count)) {
                                    popUpTo(Screen.Scanner.createRoute(mode, scannerType)) { inclusive = true }
                                }
                            }
                        }
                    },
                    viewModel = viewModel
                )
            }

            // Single-item, repeating "Check-In" mode — its own screen (see RepeatCheckInScreen).
            composable(
                route = Screen.RepeatCheckIn.route,
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType },
                    navArgument("scannerType") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val mode = backStackEntry.arguments?.getString("mode") ?: "check_in_repeat"
                val scannerType = backStackEntry.arguments?.getString("scannerType") ?: "camera"
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Screen.ScanFlow.createRoute(mode))
                }
                val viewModel: ScanViewModel = hiltViewModel(parentEntry)
                RepeatCheckInScreen(
                    scannerType = scannerType,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onDone = { count ->
                        navController.navigate(Screen.CommitResult.createRoute(mode, count)) {
                            popUpTo(Screen.RepeatCheckIn.createRoute(mode, scannerType)) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Screen.Scanner.route,
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType },
                    navArgument("scannerType") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val mode = backStackEntry.arguments?.getString("mode") ?: "inquiry"
                val scannerType = backStackEntry.arguments?.getString("scannerType") ?: "camera"
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Screen.ScanFlow.createRoute(mode))
                }
                val viewModel: ScanViewModel = hiltViewModel(parentEntry)
                ScannerScreen(
                    mode = mode,
                    scannerType = scannerType,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onCommitSuccess = { count ->
                        navController.navigate(Screen.CommitResult.createRoute(mode, count)) {
                            popUpTo(Screen.Scanner.createRoute(mode, scannerType)) { inclusive = true }
                        }
                    },
                    onAdvance = {
                        when (mode) {
                            "check_in" -> navController.navigate(Screen.LocationScan.createRoute(mode, scannerType))
                            "check_out" -> navController.navigate(Screen.ReturnDateSelect.createRoute(mode, scannerType))
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
                showCreatedMessage = created,
                // Addendum — this screen's ViewModel is fixed to the OLD id for its whole
                // lifetime (SavedStateHandle-backed, "load fresh by id" convention — see
                // AssetDetailViewModel), so after a rename we navigate to the new id's own route
                // rather than trying to refresh in place. popUpTo...inclusive replaces the now-
                // dead old-id entry instead of stacking on top of it, so Back goes to wherever
                // this screen was originally opened from, not back to a screen for an id that no
                // longer exists.
                onRenamed = { newAssetId ->
                    navController.navigate(Screen.AssetDetail.createRoute(newAssetId)) {
                        popUpTo(Screen.AssetDetail.route) { inclusive = true }
                    }
                }
            )
        }
        }

        // Declared after NavHost so it sits on top of the back-press dispatcher stack and
        // intercepts before Navigation-Compose's own back handling. While Kiosk Mode is active,
        // normal in-app back navigation (e.g. backing out of a scan page to Mode Select) still
        // works as usual — only once there's nowhere left to pop (i.e. back would otherwise exit
        // the app) does the admin unlock gate take over, which is the only thing Kiosk Mode is
        // meant to block.
        BackHandler(enabled = isKioskModeActive) {
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            } else {
                MainActivity.showAdminUnlockDialog.value = true
            }
        }

        // The ONE shared floating result window, hosted here (not inside any single destination)
        // so it can appear over any screen — fed by Check-In's per-item card, the Inquiry FAB, and
        // the global hardware-scanner trigger alike. Declared after BackHandler/the admin dialog
        // so it draws on top of everything else in the stack.
        FloatingResultWindow(
            content = floatingResultContent,
            onDismiss = { floatingResultViewModel.dismiss() },
            onViewDetails = { assetId ->
                floatingResultViewModel.dismiss()
                navController.navigate(Screen.AssetDetail.createRoute(assetId))
            },
            inquiryResults = inquiryResults,
            onDismissInquiryResult = { id -> floatingResultViewModel.dismissInquiryResult(id) },
            onInquiryViewDetails = { assetId ->
                navController.navigate(Screen.AssetDetail.createRoute(assetId))
            }
        )

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
