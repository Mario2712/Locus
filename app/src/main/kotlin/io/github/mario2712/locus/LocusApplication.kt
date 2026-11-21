package io.github.mario2712.locus

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.mario2712.locus.data.seed.DatabaseSeeder
import io.github.mario2712.locus.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LocusApplication : Application() {

    @Inject lateinit var seeder: DatabaseSeeder

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Standarddaten anlegen; die UI beobachtet Flows und aktualisiert sich danach automatisch.
        appScope.launch { seeder.seed() }
    }
}
