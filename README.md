# Locus

Persönliche Android-App, die festhält, **wo ich wann war**: im Alltag (Homeoffice, Büro, Freizeit)
genauso wie auf Reisen. Daraus entstehen Karte, Länderpunkte mit Flaggen und Statistiken.

- Anforderungen und Konzept: [`docs/requirements.md`](docs/requirements.md)
- Zielgerät: Google Pixel 7, Android 17 (minSdk 31)

## Projektstruktur

| Modul | Inhalt |
|---|---|
| `:app` | Android-App: Jetpack Compose, Material 3, Hilt, Navigation |
| `:core:domain` | Reines Kotlin: fachliche Regeln (Länderpunkte, Tagesklassifizierung, Flaggen) mit Unit-Tests |

## Bauen

```bash
./gradlew :core:domain:test        # Domain-Tests
./gradlew :app:assembleDebug       # Debug-APK → app/build/outputs/apk/debug/
```

Voraussetzungen: JDK 17+ und Android SDK (compileSdk 37).

## Varianten und APKs

| | Debug | Release |
|---|---|---|
| Wofür | Entwicklung, jeder Branch | reguläre App aus `main` bzw. Tags |
| App-Name / Icon | „Locus Debug“, orange | „Locus“, grün |
| Paket-ID | `io.github.mario2712.locus.debug` | `io.github.mario2712.locus` |
| Signatur | Debug-Schlüssel `app/debug.keystore` (im Repo, kein Geheimnis) | Release-Schlüssel (nur in GitHub Secrets) |
| Download | Artefakt `locus-debug-apk` an jedem Workflow-Lauf | Artefakt `locus-release-apk` (main), GitHub-Release (Tag `vX.Y.Z`) |

Weil sich Paket-ID und Signatur unterscheiden, sind beide Varianten für Android **zwei getrennte Apps**
mit eigener Datenbank. Die Debug-APK lässt sich parallel installieren und kann die reguläre App
weder überschreiben noch ihre Daten verändern. Debug-APKs aus verschiedenen CI-Läufen haben dieselbe
Signatur und lassen sich daher direkt übereinander aktualisieren.

Versionen: Basis in `gradle.properties` (`locus.version`). main-Builds heißen `<basis>-main.<run>`,
Tags `vX.Y.Z` ergeben genau `X.Y.Z`. Der `versionCode` ist die fortlaufende Workflow-Run-Nummer.

## Release-Signatur einrichten (einmalig)

1. Keystore lokal erzeugen (nie committen):
   ```bash
   keytool -genkeypair -v -keystore locus-release.jks -alias locus \
     -keyalg RSA -keysize 4096 -validity 10000
   base64 -w0 locus-release.jks > locus-release.jks.b64
   ```
2. Im Repository unter *Settings → Secrets and variables → Actions* anlegen:
   `LOCUS_KEYSTORE_BASE64` (Inhalt der .b64-Datei), `LOCUS_KEYSTORE_PASSWORD`, `LOCUS_KEY_ALIAS`, `LOCUS_KEY_PASSWORD`.
3. Ab dann baut jeder Push auf `main` eine signierte Release-APK. Für ein GitHub-Release: `git tag v0.1.0 && git push origin v0.1.0`.

Den Keystore gut sichern: Ohne ihn lassen sich spätere Updates nicht über eine bestehende Installation einspielen.
