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

## APK herunterladen

- **Debug-APK**: Jeder Push baut über GitHub Actions eine Debug-APK. Sie hängt als Artefakt
  `locus-debug-apk` am jeweiligen Workflow-Lauf (Tab *Actions*).
- **Release-APK**: Ein Tag `vX.Y.Z` erzeugt eine signierte APK und ein GitHub-Release.

Die Debug-Variante hat die Paket-ID `io.github.mario2712.locus.debug` und lässt sich parallel zur Release-Variante installieren.

## Release-Signatur einrichten (einmalig)

1. Keystore lokal erzeugen (nie committen):
   ```bash
   keytool -genkeypair -v -keystore locus-release.jks -alias locus \
     -keyalg RSA -keysize 4096 -validity 10000
   base64 -w0 locus-release.jks > locus-release.jks.b64
   ```
2. Im Repository unter *Settings → Secrets and variables → Actions* anlegen:
   `LOCUS_KEYSTORE_BASE64` (Inhalt der .b64-Datei), `LOCUS_KEYSTORE_PASSWORD`, `LOCUS_KEY_ALIAS`, `LOCUS_KEY_PASSWORD`.
3. Release auslösen: `git tag v0.1.0 && git push origin v0.1.0`.

Den Keystore gut sichern: Ohne ihn lassen sich spätere Updates nicht über eine bestehende Installation einspielen.
