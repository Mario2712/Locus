package io.github.mario2712.locus.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.mario2712.locus.R
import kotlinx.serialization.Serializable

@Serializable data object TodayRoute
@Serializable data object CalendarRoute
@Serializable data object MapRoute
@Serializable data object CountriesRoute
@Serializable data object MoreRoute

/** Die fünf Ziele der unteren Navigationsleiste. */
enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    TODAY(TodayRoute, R.string.nav_today, R.drawable.ic_nav_today),
    CALENDAR(CalendarRoute, R.string.nav_calendar, R.drawable.ic_nav_calendar),
    MAP(MapRoute, R.string.nav_map, R.drawable.ic_nav_map),
    COUNTRIES(CountriesRoute, R.string.nav_countries, R.drawable.ic_nav_countries),
    MORE(MoreRoute, R.string.nav_more, R.drawable.ic_nav_more),
}
