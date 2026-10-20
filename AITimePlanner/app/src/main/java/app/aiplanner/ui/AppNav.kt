package app.aiplanner.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.aiplanner.R
import app.aiplanner.ui.planner.PlannerScreen
import app.aiplanner.ui.profile.ProfileScreen
import app.aiplanner.ui.schedule.ScheduleScreen
import app.aiplanner.ui.tasks.TasksScreen

private data class NavItem(val route: String, val icon: ImageVector, val labelRes: Int)

@Composable
fun AiPlannerApp() {
    val navController = rememberNavController()
    val items = listOf(
        NavItem("schedule", Icons.Filled.Schedule, R.string.nav_schedule),
        NavItem("tasks", Icons.Filled.Checklist, R.string.nav_tasks),
        NavItem("planner", Icons.Filled.AutoAwesome, R.string.nav_planner),
        NavItem("profile", Icons.Filled.Person, R.string.nav_profile),
    )
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "schedule",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("schedule") { ScheduleScreen() }
            composable("tasks") { TasksScreen() }
            composable("planner") { PlannerScreen() }
            composable("profile") { ProfileScreen() }
        }
    }
}
