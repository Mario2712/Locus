package io.github.mario2712.locus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import io.github.mario2712.locus.places.LocalPlaceSearch
import io.github.mario2712.locus.places.PlaceSearchController
import io.github.mario2712.locus.ui.LocusApp
import io.github.mario2712.locus.ui.theme.LocusTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var placeSearch: PlaceSearchController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocusTheme {
                CompositionLocalProvider(LocalPlaceSearch provides placeSearch) {
                    LocusApp()
                }
            }
        }
    }
}
