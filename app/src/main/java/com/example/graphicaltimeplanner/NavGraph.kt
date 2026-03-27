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
                onNavigateToTimetable = { navController.navigate("home") },
                onNavigateToAssistant = { navController.navigate("ai") },
                onNavigateToCourses   = { navController.navigate("courses") },
                onNavigateToChatbot   = { navController.navigate("chatbot") },
                onViewProfile         = { navController.navigate("profile") },
                onNavigateToAdvisor   = { navController.navigate("advisor") },
                onLogout = {
                    AppState.logout()
                    navController.navigate("login") { popUpTo(0) { inclusive = true } }
                }
            )
        }

        // New AI screen
        composable("ai") {
            AIScreen(
                onViewProfile   = { navController.navigate("profile") },
                onLogout = {
                    AppState.logout()
                    navController.navigate("login") { popUpTo(0) { inclusive = true } }
                },
                onNavigateToHome     = { navController.navigate("home") { popUpTo("ai") { inclusive = true } } },
                onNavigateToCourses  = { navController.navigate("courses") },
                onNavigateToChatbot  = { navController.navigate("chatbot") },
                onNavigateToAdvisor  = { navController.navigate("advisor") }
            )
        }

        composable("courses") {
            CourseScreen(
                onViewProfile = { navController.navigate("profile") },
                onLogout = {
                    AppState.logout()
                    navController.navigate("login") { popUpTo(0) { inclusive = true } }
                },
                onBackToHome = {
                    navController.navigate("home") { popUpTo("courses") { inclusive = true } }
                },
                onNavigateToChatbot = { navController.navigate("chatbot") },
                onNavigateToAi = { navController.navigate("ai") },
                onNavigateToAdvisor = { navController.navigate("advisor") }
            )
        }

        composable("chatbot") {
            ChatbotScreen(
                onHistoryClick = { navController.navigate("chat_history") },
                onNavigateToTimetable = { navController.navigate("home") { popUpTo("home") } },
                onLogout = {
                    AppState.logout()
                    navController.navigate("login") { popUpTo(0) { inclusive = true } }
                },
                onViewProfile     = { navController.navigate("profile") },
                onNavigateToHome  = {
                    navController.navigate("home") { popUpTo("chatbot") { inclusive = true } }
                },
                onNavigateToCourses = { navController.navigate("courses") },
                onNavigateToAi = { navController.navigate("ai") },
                onNavigateToAdvisor = { navController.navigate("advisor") }
            )
        }

        composable("profile") {
            ProfileScreen(
                onBack   = { navController.popBackStack() },
                onLogout = {
                    AppState.logout()
                    navController.navigate("login") { popUpTo(0) { inclusive = true } }
                }
            )
        }

        composable("chat_history") {
            ChatHistoryScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable("advisor") {
            AdvisorScreen(
                onViewProfile      = { navController.navigate("profile") },
                onLogout = {
                    AppState.logout()
                    navController.navigate("login") { popUpTo(0) { inclusive = true } }
                },
                onNavigateToHome    = { navController.navigate("home") { popUpTo("advisor") { inclusive = true } } },
                onNavigateToCourses = { navController.navigate("courses") },
                onNavigateToChatbot = { navController.navigate("chatbot") },
                onNavigateToAi      = { navController.navigate("ai") }
            )
        }
    }
}
