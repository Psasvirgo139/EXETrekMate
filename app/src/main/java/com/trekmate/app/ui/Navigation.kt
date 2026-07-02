package com.trekmate.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.trekmate.app.core.model.TourRole
import com.trekmate.app.feature.auth.AuthState
import com.trekmate.app.feature.qr.QrCodeRenderer
import com.trekmate.app.feature.tour.TourViewModel
import com.trekmate.app.ui.screens.*

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object CreateTour : Screen("create_tour")
    data object JoinByCode : Screen("join_code")
    data object JoinByQr : Screen("join_qr")
    data object LeaderDashboard : Screen("leader_dashboard")
    data object MemberTracking : Screen("member_tracking")
}

@Composable
fun TrekMateNavHost(
    navController: NavHostController = rememberNavController(),
    mainViewModel: MainViewModel = hiltViewModel(),
    qrRenderer: QrCodeRenderer,
    modifier: Modifier = Modifier
) {
    val authState by mainViewModel.authState.collectAsState()
    val currentTour by mainViewModel.currentTour.collectAsState()
    val currentUser by mainViewModel.currentUser.collectAsState()

    // Auto-navigate when tour state changes
    LaunchedEffect(currentTour) {
        currentTour?.let { tour ->
            val targetRoute = if (tour.role == TourRole.LEADER) Screen.LeaderDashboard.route
            else Screen.MemberTracking.route
            navController.navigate(targetRoute) {
                popUpTo(Screen.Home.route) { inclusive = false }
                launchSingleTop = true
            }
        } ?: run {
            navController.navigate(Screen.Home.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                currentUser = currentUser,
                onCreateTour = { navController.navigate(Screen.CreateTour.route) },
                onJoinByCode = { navController.navigate(Screen.JoinByCode.route) },
                onJoinByQr = { navController.navigate(Screen.JoinByQr.route) }
            )
        }

        composable(Screen.CreateTour.route) {
            CreateTourScreen(
                onBack = { navController.popBackStack() },
                onTourCreated = {
                    navController.navigate(Screen.LeaderDashboard.route) {
                        popUpTo(Screen.Home.route)
                    }
                },
                qrRenderer = qrRenderer
            )
        }

        composable(Screen.JoinByCode.route) {
            JoinTourScreen(
                onBack = { navController.popBackStack() },
                onTourJoined = {
                    navController.navigate(Screen.MemberTracking.route) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        composable(Screen.JoinByQr.route) {
            QrScanScreen(
                onBack = { navController.popBackStack() },
                onTourJoined = {
                    navController.navigate(Screen.MemberTracking.route) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        composable(Screen.LeaderDashboard.route) {
            val tour = currentTour ?: return@composable
            LeaderDashboardScreen(
                tour = tour,
                onEndTour = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.MemberTracking.route) {
            val tour = currentTour ?: return@composable
            MemberTrackingScreen(tour = tour)
        }
    }
}
