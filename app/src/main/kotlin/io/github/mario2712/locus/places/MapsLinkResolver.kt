package io.github.mario2712.locus.places

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/**
 * Löst Google-Maps-Kurzlinks (maps.app.goo.gl) auf: liest nur die Weiterleitungsadresse, lädt keine Seite.
 * Eine kurze Anfrage pro geteiltem Ort, nichts wird gespeichert.
 */
class MapsLinkResolver @Inject constructor() {
    suspend fun expand(url: String): String? = withContext(Dispatchers.IO) {
        var current = url
        repeat(5) {
            val connection = URL(current).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.setRequestProperty("User-Agent", "Locus")
                val code = connection.responseCode
                val location = connection.getHeaderField("Location")
                if (code !in 300..399 || location == null) return@withContext current.takeIf { it != url }
                current = URL(URL(current), location).toString()
                // Sobald die eigentliche Maps-Adresse erreicht ist, nicht weiter (z. B. zur Zustimmungsseite) folgen.
                if (!current.contains("goo.gl")) return@withContext current
            } finally {
                connection.disconnect()
            }
        }
        current.takeIf { it != url }
    }
}
