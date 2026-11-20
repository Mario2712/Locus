package io.github.mario2712.locus.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.mario2712.locus.ui.navigation.CalendarRoute
import io.github.mario2712.locus.ui.navigation.CountriesRoute
import io.github.mario2712.locus.ui.navigation.MapRoute
import io.github.mario2712.locus.ui.navigation.MoreRoute
import io.github.mario2712.locus.ui.navigation.TodayRoute
import io.github.mario2712.locus.ui.navigation.TopLevelDestination
import io.github.mario2712.locus.ui.screens.CalendarScreen
import io.github.mario2712.locus.ui.screens.CountriesScreen
import io.github.mario2712.locus.ui.screens.MapScreen
import io.github.mario2712.locus.ui.screens.MoreScreen
import io.github.mario2712.locus.ui.screens.TodayScreen

@Composable
fun LocusApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.hasRoute(dest.route::class) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(painterResource(dest.icon), contentDescription = null) },
                        label = { Text(stringResource(dest.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<TodayRoute> { TodayScreen() }
            composable<CalendarRoute> { CalendarScreen() }
            composable<MapRoute> { MapScreen() }
            composable<CountriesRoute> { CountriesScreen() }
            composable<MoreRoute> { MoreScreen() }
        }
    }
}
