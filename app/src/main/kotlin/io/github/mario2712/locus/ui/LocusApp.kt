package io.github.mario2712.locus.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.mario2712.locus.ui.calendar.CalendarScreen
import io.github.mario2712.locus.ui.day.DayScreen
import io.github.mario2712.locus.ui.more.MoreScreen
import io.github.mario2712.locus.ui.navigation.CalendarRoute
import io.github.mario2712.locus.ui.navigation.CountriesRoute
import io.github.mario2712.locus.ui.navigation.DayRoute
import io.github.mario2712.locus.ui.navigation.MapRoute
import io.github.mario2712.locus.ui.navigation.MoreRoute
import io.github.mario2712.locus.ui.data.DataScreen
import io.github.mario2712.locus.ui.navigation.DataRoute
import io.github.mario2712.locus.ui.navigation.PlaceRoute
import io.github.mario2712.locus.ui.navigation.StatsRoute
import io.github.mario2712.locus.ui.stats.StatsScreen
import io.github.mario2712.locus.ui.navigation.PlacesRoute
import io.github.mario2712.locus.ui.navigation.ProfileRoute
import io.github.mario2712.locus.ui.navigation.TimelineRoute
import io.github.mario2712.locus.ui.navigation.TripRoute
import io.github.mario2712.locus.ui.navigation.TripsRoute
import io.github.mario2712.locus.ui.places.PlaceDetailScreen
import io.github.mario2712.locus.ui.places.PlacesScreen
import io.github.mario2712.locus.ui.timeline.TimelineScreen
import io.github.mario2712.locus.ui.trips.TripDetailScreen
import io.github.mario2712.locus.ui.trips.TripsScreen
import io.github.mario2712.locus.ui.navigation.SettingsRoute
import io.github.mario2712.locus.ui.navigation.TodayRoute
import io.github.mario2712.locus.ui.navigation.TopLevelDestination
import io.github.mario2712.locus.ui.countries.CountriesScreen
import io.github.mario2712.locus.ui.map.MapScreen
import io.github.mario2712.locus.ui.profile.ProfileScreen
import io.github.mario2712.locus.ui.settings.SettingsScreen
import io.github.mario2712.locus.ui.today.TodayScreen
import java.time.LocalDate

@Composable
fun LocusApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    // Aktiver Reiter = der zuletzt geöffnete Hauptbereich im Back-Stack (auch in Untermenüs wie „Mehr → Orte“).
    val activeTab = remember(backStackEntry) {
        navController.currentBackStack.value.asReversed().firstNotNullOfOrNull { entry ->
            TopLevelDestination.entries.firstOrNull { entry.destination.hasRoute(it.route::class) }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { dest ->
                    val selected = dest == activeTab
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            // Erneut antippen → zurück zur obersten Ebene des Reiters
                            if (selected) navController.popBackStack(dest.route, inclusive = false)
                            else navController.navigateTopLevel(dest.route)
                        },
                        icon = { Icon(painterResource(dest.icon), contentDescription = null) },
                        label = { Text(stringResource(dest.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val openDay: (LocalDate) -> Unit = { navController.navigate(DayRoute(it.toString())) }
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<TodayRoute> {
                TodayScreen(onOpenCalendar = { navController.navigateTopLevel(CalendarRoute) })
            }
            composable<CalendarRoute> { CalendarScreen(onOpenDay = openDay) }
            composable<MapRoute> { MapScreen() }
            composable<CountriesRoute> { CountriesScreen() }
            composable<MoreRoute> {
                MoreScreen(
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onOpenProfile = { navController.navigate(ProfileRoute) },
                    onOpenTimeline = { navController.navigate(TimelineRoute) },
                    onOpenTrips = { navController.navigate(TripsRoute) },
                    onOpenPlaces = { navController.navigate(PlacesRoute) },
                    onOpenStats = { navController.navigate(StatsRoute) },
                    onOpenData = { navController.navigate(DataRoute) },
                )
            }
            composable<DayRoute> { DayScreen(onBack = { navController.popBackStack() }) }
            composable<SettingsRoute> { SettingsScreen(onBack = { navController.popBackStack() }) }
            composable<StatsRoute> { StatsScreen(onBack = { navController.popBackStack() }, onOpenDay = openDay) }
            composable<DataRoute> { DataScreen(onBack = { navController.popBackStack() }) }
            composable<ProfileRoute> { ProfileScreen(onBack = { navController.popBackStack() }) }
            composable<TimelineRoute> { TimelineScreen(onBack = { navController.popBackStack() }, onOpenDay = openDay) }
            composable<TripsRoute> {
                TripsScreen(onBack = { navController.popBackStack() }, onOpenTrip = { navController.navigate(TripRoute(it)) })
            }
            composable<TripRoute> { entry ->
                TripDetailScreen(entry.toRoute<TripRoute>().id, onBack = { navController.popBackStack() }, onOpenDay = openDay)
            }
            composable<PlacesRoute> {
                PlacesScreen(onBack = { navController.popBackStack() }, onOpenPlace = { navController.navigate(PlaceRoute(it)) })
            }
            composable<PlaceRoute> {
                PlaceDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDay = openDay,
                    onOpenPlace = { navController.navigate(PlaceRoute(it)) },
                )
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
