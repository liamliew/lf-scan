package com.lfcreative.lfscan.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lfcreative.lfscan.ui.screen.AssetDetailScreen
import com.lfcreative.lfscan.ui.screen.AssetsScreen
import com.lfcreative.lfscan.ui.screen.CommitResultScreen
import com.lfcreative.lfscan.ui.screen.ModeSelectScreen
import com.lfcreative.lfscan.ui.screen.PinScreen
import com.lfcreative.lfscan.ui.screen.ScanViewModel
import com.lfcreative.lfscan.ui.screen.ScannerScreen
import com.lfcreative.lfscan.ui.screen.ScannerTypeSelectScreen

sealed class Screen(val route: String) {
    object Pin : Screen("pin")
    object ModeSelect : Screen("mode_select")

    // Nested graph — ScannerTypeSelect, Scanner, and CommitResult all share one ScanViewModel
    object Assets : Screen("assets")
    object ScanFlow : Screen("scan_flow/{mode}") {
        fun createRoute(mode: String) = "scan_flow/$mode"
    }
    object ScannerTypeSelect : Screen("scanner_type_select/{mode}") {
        fun createRoute(mode: String) = "scanner_type_select/$mode"
    }
    object Scanner : Screen("scanner/{mode}/{scannerType}") {
        fun createRoute(mode: String, scannerType: String) = "scanner/$mode/$scannerType"
    }
    object CommitResult : Screen("commit_result/{mode}/{count}") {
        fun createRoute(mode: String, count: Int) = "commit_result/$mode/$count"
    }
    object AssetDetail : Screen("asset/{assetId}") {
        fun createRoute(assetId: String) = "asset/$assetId"
    }
}

@Composable
fun LFScanNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Pin.route) {

        composable(Screen.Pin.route) {
            PinScreen(
                onSuccess = {
                    navController.navigate(Screen.ModeSelect.route) {
                        popUpTo(Screen.Pin.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ModeSelect.route) {
            ModeSelectScreen(
                onModeSelected = { mode ->
                    navController.navigate(Screen.ScanFlow.createRoute(mode))
                },
                onSignOut = {
                    navController.navigate(Screen.Pin.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onViewAssets = {
                    navController.navigate(Screen.Assets.route)
                }
            )
        }

        composable(Screen.Assets.route) {
            AssetsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToAsset = { assetId ->
                    navController.navigate(Screen.AssetDetail.createRoute(assetId))
                }
            )
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
                ScannerTypeSelectScreen(
                    mode = mode,
                    onBack = { navController.popBackStack() },
                    onScannerTypeSelected = { scannerType ->
                        navController.navigate(Screen.Scanner.createRoute(mode, scannerType))
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
                            popUpTo(Screen.Scanner.createRoute(mode, scannerType)) { inclusive = false }
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
            arguments = listOf(navArgument("assetId") { type = NavType.StringType })
        ) {
            AssetDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}
