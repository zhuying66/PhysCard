package com.physcard.physics.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.physcard.physics.ui.components.ThinDivider
import com.physcard.physics.ui.home.HomeScreen
import com.physcard.physics.ui.home.HomeViewModel
import com.physcard.physics.ui.library.FormulaDetailRoute
import com.physcard.physics.ui.library.FormulaDetailViewModel
import com.physcard.physics.ui.library.LibraryRoute
import com.physcard.physics.ui.library.LibraryViewModel
import com.physcard.physics.ui.settings.SettingsRoute
import com.physcard.physics.ui.stats.StatsRoute
import com.physcard.physics.ui.study.StudyRoute
import com.physcard.physics.ui.study.StudyViewModel

private enum class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "首页", Icons.Rounded.Home),
    LIBRARY("library", "公式库", Icons.Rounded.MenuBook),
    STATS("stats", "统计", Icons.Rounded.BarChart),
    SETTINGS("settings", "设置", Icons.Rounded.Settings),
}

@Composable
fun PhysicsNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val routePattern = backStackEntry?.destination?.route
    val rootRoute = routePattern?.substringBefore("?")
    val showBottomBar = rootRoute in setOf("home", "library", "stats", "settings")

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize(),
        ) {
            composable("home") {
                val homeViewModel: HomeViewModel = viewModel()
                val home by homeViewModel.home.collectAsStateWithLifecycle()
                HomeScreen(
                    snapshot = home,
                    onStartStudy = {
                        if (home.hasStudyWork) {
                            navController.navigate("study")
                        } else {
                            navController.navigate("library")
                        }
                    },
                    onOpenStats = { navController.navigateRoot("stats") },
                )
            }
            composable(
                route = "library?module={module}",
                arguments = listOf(
                    navArgument("module") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = true
                    },
                ),
            ) { entry ->
                val libraryViewModel: LibraryViewModel = viewModel()
                LibraryRoute(
                    viewModel = libraryViewModel,
                    initialModule = entry.arguments?.getString("module"),
                    onOpenFormula = { id -> navController.navigate("formula/$id") },
                )
            }
            composable("stats") {
                val statsViewModel: com.physcard.physics.ui.stats.StatsViewModel = viewModel()
                StatsRoute(
                    viewModel = statsViewModel,
                    onStartStudy = { navController.navigate("study") },
                )
            }
            composable("settings") {
                SettingsRoute(
                    viewModel = viewModel(),
                )
            }
            composable(
                route = "formula/{formulaId}",
                arguments = listOf(navArgument("formulaId") { type = NavType.StringType }),
            ) { entry ->
                val formulaId = entry.arguments?.getString("formulaId").orEmpty()
                val application = LocalContext.current.applicationContext as Application
                val detailViewModel: FormulaDetailViewModel = viewModel(
                    key = "formula-$formulaId",
                    factory = FormulaDetailViewModel.factory(application, formulaId),
                )
                FormulaDetailRoute(
                    viewModel = detailViewModel,
                    onBack = { navController.popBackStack() },
                    onPractice = { id, ability ->
                        if (ability == null) {
                            navController.navigate("study?formulaId=$id")
                        } else {
                            navController.navigate("study?formulaId=$id&ability=${ability.wireName}")
                        }
                    },
                    onOpenFormula = { id -> navController.navigate("formula/$id") },
                )
            }
            composable(
                route = "study?formulaId={formulaId}&ability={ability}",
                arguments = listOf(
                    navArgument("formulaId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("ability") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                val studyViewModel: StudyViewModel = viewModel()
                StudyRoute(
                    viewModel = studyViewModel,
                    onExit = { navController.popBackStack() },
                    onFinish = {
                        val popped = navController.popBackStack("home", inclusive = false)
                        if (!popped) navController.navigateRoot("home")
                    },
                )
            }
        }
        if (showBottomBar) {
            AppBottomBar(
                currentRoute = rootRoute,
                onSelect = { tab -> navController.navigateRoot(tab.route) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun AppBottomBar(
    currentRoute: String?,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 0.dp,
    ) {
        Column {
            ThinDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomTab.entries.forEach { tab ->
                    val selected = currentRoute == tab.route
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .testTag("tab-${tab.route}")
                            .clickable { onSelect(tab) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .padding(horizontal = 16.dp, vertical = 3.dp),
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(21.dp),
                            )
                        }
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bottom-bar navigation for the four top-level destinations.
 *
 * We deliberately do NOT use `popUpTo(startDestination) { saveState = true }` together with
 * `restoreState = true`: the start destination is "home", so saveState would snapshot the whole
 * home stack (e.g. home -> library) and restore that stack whenever "首页" is tapped again,
 * leaving the user on the library screen. Instead every top-level tab is a single destination in
 * one stack, so tapping any tab always lands exactly on that tab.
 */
private fun NavHostController.navigateRoot(route: String) {
    navigate(route) {
        popUpTo(graph.id) {
            saveState = false
            inclusive = false
        }
        launchSingleTop = true
        restoreState = false
    }
}
