package com.dramashield.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dramashield.app.ui.screen.about.AboutScreen
import com.dramashield.app.ui.screen.diagnostics.DiagnosticsScreen
import com.dramashield.app.ui.screen.home.HomeScreen
import com.dramashield.app.ui.screen.log.LogScreen
import com.dramashield.app.ui.screen.rule.RuleScreen
import com.dramashield.app.ui.screen.setting.SettingScreen

object Routes {
    const val HOME = "home"
    const val RULE = "rule"
    const val LOG = "log"
    const val SETTING = "setting"
    const val ABOUT = "about"
    const val DIAGNOSTICS = "diagnostics"
}

private data class BottomTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, "首页", Icons.Filled.Home, Icons.Outlined.Home),
    BottomTab(Routes.RULE, "规则", Icons.AutoMirrored.Filled.Rule, Icons.AutoMirrored.Outlined.Rule),
    BottomTab(Routes.LOG, "日志", Icons.AutoMirrored.Filled.Article, Icons.AutoMirrored.Outlined.Article),
    BottomTab(Routes.DIAGNOSTICS, "诊断", Icons.Filled.BugReport, Icons.Outlined.BugReport),
    BottomTab(Routes.SETTING, "设置", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun DramaShieldApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomTabs.any { it.route == currentRoute }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val navigateToRule = { navigateToTab(Routes.RULE) }
    val navigateToLog = { navigateToTab(Routes.LOG) }
    val navigateToSetting = { navigateToTab(Routes.SETTING) }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    bottomTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateToTab(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) {
                                        tab.selectedIcon
                                    } else {
                                        tab.unselectedIcon
                                    },
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(text = tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 16 }
            },
            exitTransition = {
                fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { -it / 16 }
            },
            popEnterTransition = {
                fadeIn(tween(200)) + slideInHorizontally(tween(200)) { -it / 16 }
            },
            popExitTransition = {
                fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { it / 16 }
            }
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenWhiteList = navigateToSetting,
                    onOpenLogs = navigateToLog,
                    onOpenAbout = { navController.navigate(Routes.ABOUT) }
                )
            }
            composable(Routes.RULE) {
                RuleScreen()
            }
            composable(Routes.LOG) {
                LogScreen()
            }
            composable(Routes.DIAGNOSTICS) {
                DiagnosticsScreen()
            }
            composable(Routes.SETTING) {
                SettingScreen(
                    onOpenAbout = { navController.navigate(Routes.ABOUT) }
                )
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
