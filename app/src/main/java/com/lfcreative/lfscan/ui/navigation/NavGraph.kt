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
import com.lfcreative.lfscan.ui.screen.CommitResultScreen
import com.lfcreative.lfscan.ui.screen.ModeSelectScreen
import com.lfcreative.lfscan.ui.screen.PinScreen
import com.lfcreative.lfscan.ui.screen.ScanViewModel
import com.lfcreative.lfscan.ui.screen.ScannerScreen

sealed class Screen(val route: String) {
    object Pin : Screen("pin")
    object ModeSelect : Screen("mode_select")

    // Nested graph so ScannerScreen and CommitResultScreen share a ScanViewModel instance
    object ScanFlow : Screen("scan_flow/{mode}") {
        fun createRoute(mode: String) = "scan_flow/$mode"
    }
    object Scanner : Screen("scanner/{mode}") {
        fun createRoute(mode: String) = "scanner/$mode"
    }
    object CommitResult : Screen("commit_result/{mode}/{count}") {
        fun createRoute(mode: String, count: Int) = "commit_result/$mode/$count"
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
                }
            )
        }

        // Nested graph: both Scanner and CommitResult share the same ScanViewModel
        navigation(
            startDestination = Screen.Scanner.route,
            route = Screen.ScanFlow.route
        ) {
            composable(
                route = Screen.Scanner.route,
                arguments = listOf(navArgument("mode") { type = NavType.StringType })
            ) { backStackEntry ->
                val mode = backStackEntry.arguments?.getString("mode") ?: "inquiry"
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Screen.ScanFlow.createRoute(mode))
                }
                val viewModel: ScanViewModel = hiltViewModel(parentEntry)
                ScannerScreen(
                    mode = mode,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onCommitSuccess = { count ->
                        navController.navigate(Screen.CommitResult.createRoute(mode, count)) {
                            popUpTo(Screen.Scanner.createRoute(mode)) { inclusive = false }
                        }
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
                        navController.navigate(Screen.ModeSelect.route) {
                            popUpTo(Screen.ModeSelect.route) { inclusive = false }
                        }
                    }
                )
            }
        }
    }
}
