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

Die Release-APK (aus `main` bzw. Tags `vX.Y.Z`) wird mit einem eigenen Schlüssel signiert, der nur
in den GitHub-Secrets liegt – nie im Repo. **Keystore und Passwort gut sichern:** Ohne sie lässt
sich die installierte App nie mehr aktualisieren.

Benötigte Secrets (Repository → *Settings → Secrets and variables → Actions*):

| Secret | Inhalt |
|---|---|
| `LOCUS_KEYSTORE_BASE64` | Keystore-Datei als Base64-Text |
| `LOCUS_KEYSTORE_PASSWORD` | Passwort des Keystores |
| `LOCUS_KEY_ALIAS` | Alias des Schlüssels, hier `locus` |
| `LOCUS_KEY_PASSWORD` | Passwort des Schlüssels – bei PKCS12 **dasselbe** wie das Keystore-Passwort |

### Windows (PowerShell)

1. **keytool finden** (kommt mit jedem JDK). Mit Android Studio ist es schon da:
   ```powershell
   $keytool = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
   & $keytool -help | Select-Object -First 1
   ```
   Ohne Android Studio: `winget install EclipseAdoptium.Temurin.21.JDK`, neues Terminal öffnen, dann `$keytool = "keytool"`.

2. **Keystore erzeugen** – in einem Ordner **außerhalb** des Repos; fragt zweimal nach dem Passwort:
   ```powershell
   $dir = "$HOME\locus-signing"
   New-Item -ItemType Directory -Force $dir | Out-Null
   Set-Location $dir
   & $keytool -genkeypair -v -keystore locus-release.jks -storetype PKCS12 -alias locus `
       -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Locus, O=Privat, C=AT"
   ```

3. **Fingerabdruck notieren** (zum späteren Prüfen der APK):
   ```powershell
   & $keytool -list -v -keystore locus-release.jks -alias locus | Select-String "SHA256"
   ```

4. **Secrets setzen** – entweder im Browser (Base64 liegt danach in der Zwischenablage) …
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("$dir\locus-release.jks")) | Set-Clipboard
   ```
   … und unter *New repository secret* die vier Secrets anlegen,

   **oder** mit der GitHub-CLI (`winget install GitHub.cli`, einmal `gh auth login`):
   ```powershell
   $repo = "Mario2712/Locus"
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("$dir\locus-release.jks")) |
       gh secret set LOCUS_KEYSTORE_BASE64 --repo $repo
   gh secret set LOCUS_KEY_ALIAS --repo $repo --body "locus"
   gh secret set LOCUS_KEYSTORE_PASSWORD --repo $repo   # fragt das Passwort ab (nicht sichtbar)
   gh secret set LOCUS_KEY_PASSWORD --repo $repo        # dasselbe Passwort noch einmal
   gh secret list --repo $repo
   ```

5. **Sichern:** `locus-release.jks` und das Passwort in den Passwortmanager bzw. auf einen
   USB-Stick. Base64-Text nicht als Datei liegen lassen.

### Linux/macOS

```bash
keytool -genkeypair -v -keystore locus-release.jks -storetype PKCS12 -alias locus \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Locus, O=Privat, C=AT"
base64 -w0 locus-release.jks   # macOS: base64 -i locus-release.jks
```

### Release auslösen

Sobald die Secrets gesetzt sind und der Stand in `main` liegt, baut jeder Push auf `main` eine
signierte Release-APK (Artefakt `locus-release-apk`). Für ein GitHub-Release mit APK:
`git tag v1.0.0; git push origin v1.0.0` – oder im Browser unter *Releases → Draft a new release*
einen neuen Tag `v1.0.0` auf `main` anlegen.

## Lizenzen von Drittinhalten

- Ländergrenzen: Natural Earth (gemeinfrei).
- Kartenkacheln der Detailkarte: OpenFreeMap / OpenStreetMap-Mitwirkende (ODbL).
- Ortssuche: Photon (komoot) auf Basis von OpenStreetMap (ODbL).
- Schrift „Instrument Serif“ (Jahresrückblick): SIL Open Font License 1.1, siehe `app/src/main/assets/licenses/instrument-serif-OFL.txt`.
