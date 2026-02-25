// NavGraph.kt
package com.example.graphicaltimeplanner

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onRegisterClick = {
                    navController.navigate("register")
                }
            )
        }

        composable("register") {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate("login") {
                        popUpTo("register") { inclusive = true }
                    }
                },
                onLoginClick = {
                    navController.popBackStack()
                }
            )
        }

        composable("home") {
            HomeScreen(
                onNavigateToTimetable = {
                    navController.navigate("timetable")
                },
                onNavigateToAssistant = {
                    navController.navigate("assistant")
                },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable("timetable") {
            PlannerScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("assistant") {
            GenerateScreen(
                onBack = {
                    navController.popBackStack()
                },
                onNavigateToTimetable = {
                    navController.navigate("timetable") {
                        popUpTo("home")
                    }
                }
            )
        }
    }
}