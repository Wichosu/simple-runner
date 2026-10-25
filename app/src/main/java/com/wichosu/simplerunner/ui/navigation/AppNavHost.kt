package com.wichosu.simplerunner.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wichosu.simplerunner.ui.detail.RunDetailScreen
import com.wichosu.simplerunner.ui.home.HomeScreen
import com.wichosu.simplerunner.ui.run.ActiveRunScreen

object Routes {
    const val HOME = "home"
    const val ACTIVE_RUN = "active"
    const val RUN_DETAIL = "run/{runId}"
    const val RUN_ID_ARG = "runId"

    fun runDetail(runId: String): String = "run/$runId"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onStartRun = { navController.navigate(Routes.ACTIVE_RUN) },
                onOpenRun = { runId -> navController.navigate(Routes.runDetail(runId)) },
            )
        }
        composable(Routes.ACTIVE_RUN) {
            ActiveRunScreen(
                onFinished = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }
        composable(
            route = Routes.RUN_DETAIL,
            arguments = listOf(navArgument(Routes.RUN_ID_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            RunDetailScreen(
                runId = backStackEntry.arguments?.getString(Routes.RUN_ID_ARG).orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
