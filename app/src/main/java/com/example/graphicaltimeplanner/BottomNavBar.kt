// BottomNavBar.kt
package com.example.graphicaltimeplanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding

enum class BottomNavItem {
    COURSES, AI, SCHEDULE, CHATBOT, ADVISOR
}

@Composable
fun BottomNavBar(
    selectedItem: BottomNavItem,
    onCoursesClick: () -> Unit = {},
    onAiClick: () -> Unit = {},
    onScheduleClick: () -> Unit = {},
    onChatbotClick: () -> Unit = {},
    onAdvisorClick: () -> Unit = {},
    showImportExport: Boolean = false,
    onImportClick: () -> Unit = {},
    onExportClick: () -> Unit = {}
) {
    val primaryYellow = colorResource(R.color.uw_gold_lvl4)
    val lightYellow = primaryYellow.copy(alpha = 0.25f)
    val dividerColor = Color(0xFFE0E0E0)

    Column {
        if (showImportExport) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = onImportClick) {
                    Text("↑ Import", fontSize = 15.sp)
                }

                Box(
                    Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(Color(0xFFDDDDDD))
                )

                TextButton(onClick = onExportClick) {
                    Text("↓ Export", fontSize = 15.sp)
                }
            }
        }

        HorizontalDivider(
            color = dividerColor,
            thickness = 1.dp,
            modifier = Modifier.fillMaxWidth()
        )

        NavigationBar(
            containerColor = Color.White,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
        ) {
            NavigationBarItem(
                selected = selectedItem == BottomNavItem.COURSES,
                onClick = onCoursesClick,
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = "Courses",
                        tint = if (selectedItem == BottomNavItem.COURSES) lightYellow else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                },
                label = {
                    Text(
                        "Courses",
                        color = if (selectedItem == BottomNavItem.COURSES) lightYellow else Color.Gray,
                        fontSize = 12.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
            )

            NavigationBarItem(
                selected = selectedItem == BottomNavItem.AI,
                onClick = onAiClick,
                icon = {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "AI",
                        tint = if (selectedItem == BottomNavItem.AI) lightYellow else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                },
                label = {
                    Text(
                        "AI",
                        color = if (selectedItem == BottomNavItem.AI) lightYellow else Color.Gray,
                        fontSize = 12.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
            )

            NavigationBarItem(
                selected = selectedItem == BottomNavItem.SCHEDULE,
                onClick = onScheduleClick,
                icon = {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (selectedItem == BottomNavItem.SCHEDULE) primaryYellow else Color.LightGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = "Schedule",
                            tint = if (selectedItem == BottomNavItem.SCHEDULE) Color.Black else Color.DarkGray,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                label = {
                    Text(
                        "Schedule",
                        color = if (selectedItem == BottomNavItem.SCHEDULE) lightYellow else Color.Gray,
                        fontSize = 12.sp
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
            )

            NavigationBarItem(
                selected = selectedItem == BottomNavItem.CHATBOT,
                onClick = onChatbotClick,
                icon = {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Chatbot",
                        tint = if (selectedItem == BottomNavItem.CHATBOT) lightYellow else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                },
                label = {
                    Text(
                        "Chatbot",
                        color = if (selectedItem == BottomNavItem.CHATBOT) lightYellow else Color.Gray,
                        fontSize = 12.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
            )

            NavigationBarItem(
                selected = selectedItem == BottomNavItem.ADVISOR,
                onClick = onAdvisorClick,
                icon = {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Advisor",
                        tint = if (selectedItem == BottomNavItem.ADVISOR) lightYellow else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                },
                label = {
                    Text(
                        "Advisor",
                        color = if (selectedItem == BottomNavItem.ADVISOR) lightYellow else Color.Gray,
                        fontSize = 12.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}