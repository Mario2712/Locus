package io.github.mario2712.locus.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import io.github.mario2712.locus.ui.countries.CountryDetailScreen
import io.github.mario2712.locus.ui.navigation.CountryRoute
import io.github.mario2712.locus.ui.map.MapScreen
import io.github.mario2712.locus.ui.profile.ProfileScreen
import io.github.mario2712.locus.ui.settings.SettingsScreen
import io.github.mario2712.locus.ui.today.TodayScreen
import java.time.LocalDate

@Composable
fun LocusApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    // Aktiver Reiter = zuletzt besuchter Hauptbereich; bleibt in Untermenüs (z. B. „Mehr → Orte“) markiert.
    var activeTab by rememberSaveable { mutableStateOf(TopLevelDestination.TODAY) }
    val currentTab = TopLevelDestination.entries.firstOrNull { backStackEntry?.destination?.hasRoute(it.route::class) == true }
    LaunchedEffect(currentTab) {
        if (currentTab != null) activeTab = currentTab
    }
    // Aus Google Maps geteilter Ort
    val share: io.github.mario2712.locus.share.ShareViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
    val sharedText by share.text.collectAsStateWithLifecycle()
    val shareCities by share.cities.collectAsStateWithLifecycle()
    val reviewYear by share.reviewYear.collectAsStateWithLifecycle()
    LaunchedEffect(reviewYear) {
        reviewYear?.let {
            navController.navigate(io.github.mario2712.locus.ui.navigation.YearReviewRoute(it))
            share.reviewOpened()
        }
    }
    sharedText?.let { text ->
        io.github.mario2712.locus.share.SharedPlaceDialog(
            text = text,
            cities = shareCities,
            onDismiss = share::done,
            onSaved = { place ->
                share.done()
                navController.navigate(PlaceRoute(place.id))
            },
        )
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
                            if (selected) {
                                // Erneut antippen → oberste Ebene des Reiters (z. B. vom Tag zurück zum Monat)
                                if (!navController.popBackStack(dest.route, inclusive = false)) navController.navigateTopLevel(dest.route)
                            } else {
                                // Beim Wechsel wird der Unterbildschirm wiederhergestellt (z. B. ein offener Tag) –
                                // der Reiter gilt trotzdem sofort als aktiv, damit ein zweiter Tipp nach oben führt.
                                activeTab = dest
                                navController.navigateTopLevel(dest.route)
                            }
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
                TodayScreen(
                    onOpenCalendar = {
                        activeTab = TopLevelDestination.CALENDAR
                        navController.navigateTopLevel(CalendarRoute)
                    },
                    onOpenDay = openDay,
                )
            }
            composable<CalendarRoute> { CalendarScreen(onOpenDay = openDay) }
            composable<MapRoute> { MapScreen() }
            composable<CountriesRoute> { CountriesScreen(onOpenCountry = { navController.navigate(CountryRoute(it)) }) }
            composable<CountryRoute> { entry ->
                CountryDetailScreen(
                    iso2 = entry.toRoute<CountryRoute>().iso2,
                    onBack = { navController.popBackStack() },
                    onOpenPlace = { navController.navigate(PlaceRoute(it)) },
                    onOpenTrip = { navController.navigate(TripRoute(it)) },
                )
            }
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
            composable<StatsRoute> {
                StatsScreen(
                    onBack = { navController.popBackStack() }, onOpenDay = openDay,
                    onOpenPlace = { navController.navigate(PlaceRoute(it)) },
                    onOpenReview = { navController.navigate(io.github.mario2712.locus.ui.navigation.YearReviewRoute(it)) },
                )
            }
            composable<io.github.mario2712.locus.ui.navigation.YearReviewRoute> {
                io.github.mario2712.locus.ui.review.YearReviewScreen(onClose = { navController.popBackStack() })
            }
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
