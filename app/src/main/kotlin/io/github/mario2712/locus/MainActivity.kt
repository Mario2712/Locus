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
    @Inject lateinit var sharedInbox: io.github.mario2712.locus.share.SharedInbox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShare(intent)
        setContent {
            LocusTheme {
                CompositionLocalProvider(LocalPlaceSearch provides placeSearch) {
                    LocusApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Geteilter Text (z. B. aus Google Maps) wird an die Oberfläche weitergereicht. */
    private fun handleShare(intent: android.content.Intent?) {
        if (intent?.action != android.content.Intent.ACTION_SEND) return
        intent.getStringExtra(android.content.Intent.EXTRA_TEXT)?.let(sharedInbox::offer)
    }
}
