# Locus – Requirements (v0.3)

Stand: 2026-11-20 · Status: abgestimmt, Umsetzung läuft (M0)

Locus ist eine persönliche Android-App, die festhält, **wo ich wann war** – im Alltag
(Homeoffice, Büro, Freizeit) ebenso wie auf Reisen – und daraus Karte, Länderpunkte
(mit Flagge) und Statistiken ableitet. Gepflegt wird die App ausschließlich von mir;
Johanna wird als mitreisende Person erfasst.

---

## 1. Rahmenbedingungen

| Thema | Entscheidung |
|---|---|
| Plattform | Android, Zielgerät Google Pixel 7 mit Android 17 |
| Sprache | Nur Deutsch (Strings trotzdem in `strings.xml`) |
| Nutzer | Eine pflegende Person (ich); Personen „Ich“ und „Johanna“ |
| Sichtbarkeit | Keine privaten Einträge, alles für alle sichtbar |
| Datenhaltung | Lokal, offline-first |
| Sync | v1: kein Sync, manueller JSON-Export/-Import. Später: Dropbox, manuell ausgelöst |
| Backup | v1: automatisches lokales JSON-Backup |
| App-Sperre | Optional (Fingerabdruck), nicht im MVP |
| Auslieferung | APK über GitHub Releases (öffentliches Repo), manuelle Installation |
| Qualität | Saubere Architektur und automatisierte Tests haben hohe Priorität |

---

## 2. Fachliches Kernmodell

### 2.1 Aufenthalt (Kernobjekt)
Ein **Aufenthalt** beschreibt: *an Ort X, Art Y, von – bis, mit Personen Z.*

- Ein Tag kann **mehrere Aufenthalte** haben, z. B. 08:00–12:00 Büro Salzburg, 12:00–17:00 Homeoffice Hallein.
- Ein Aufenthalt kann **mehrere Tage** umfassen, z. B. Fr 13:00 bis So: Urlaub Ljubljana.
- **Uhrzeiten sind optional.** Ohne Uhrzeit gilt der Aufenthalt als ganztägig. Bei mehreren Aufenthalten ohne Uhrzeit bestimmt die Reihenfolge den Ablauf.
- **Standardfall Alltag:** ein ganztägiger Aufenthalt pro Tag, per Muster vorgeschlagen und mit einem Tipp bestätigt.
- **„Wechsel ab …“**: Ein laufender Aufenthalt wird per Aktion zu einer Uhrzeit beendet und ein neuer begonnen.
- Der **Übernachtungsort** ergibt sich aus dem letzten Aufenthalt des Tages. Es gibt dafür kein eigenes Feld.
- **Unscharfe Datumsangaben** sind erlaubt, Genauigkeit TAG / MONAT / JAHR, z. B. „Italien, Juli 2014“. Unscharfe Aufenthalte zählen für Länderpunkte und Karte, nicht aber für Tagesstatistiken.
- Aufenthalte derselben Person dürfen sich nicht überlappen. Bei einer Überlappung warnt die App, blockiert aber nicht.

### 2.2 Aufenthaltsart (konfigurierbar)
Jede Art gehört zu einer Gruppe: ARBEIT / FREI / URLAUB / SONSTIGES.

| Art | Gruppe | Zählt für Länderpunkt |
|---|---|---|
| Homeoffice | ARBEIT | ja |
| Büro | ARBEIT | ja |
| Dienstreise | ARBEIT | ja |
| Frei / Zuhause | FREI | ja |
| Ausflug | FREI | ja |
| Urlaub | URLAUB | ja |
| Anreise / Unterwegs | SONSTIGES | **nein** |
| Transit (z. B. Umstieg am Flughafen) | SONSTIGES | **nein** |
| Krank | SONSTIGES | ja |

Name, Icon, Farbe und Gruppe lassen sich ändern. Eigene Arten können angelegt werden.

### 2.3 Besuch
Ein **Besuch** ist ein konkreter, bemerkenswerter Ort an einem Datum (optional mit Uhrzeit):
Sehenswürdigkeit, Restaurant, Gipfel, Konzert, Einkauf … Er hat eine Kategorie, eine Notiz,
optional eine Bewertung und optional einen Foto-Verweis.
Kategorien: Sehenswürdigkeit, Restaurant/Café, Bar, Natur/Wandern, Gipfel, Konzert/Event,
Museum, Einkaufen, Sport, Unterkunft, **Rast/Tankstopp**, Sonstiges.
Kategorien sind konfigurierbar. Jede Kategorie legt fest, ob sie für Länderpunkte zählt;
„Rast/Tankstopp“ zählt standardmäßig nicht. Ein einzelner Besuch kann diese Regel überschreiben.
Alltägliches wie „Einkaufen Oberalm“ ist erlaubt, wird auf der Karte aber standardmäßig ausgeblendet.

### 2.4 Reise
Eine **Reise** ist eine Klammer mit Titel und Zeitraum, optional mit Uhrzeit für Start und Ende
(„Fr ab 13:00 Zug nach Ljubljana“). Für den Zeitraum werden Urlaubs-Aufenthalte vorgeschlagen.
Davor liegende Aufenthalte am selben Tag (Fr vormittags Büro) bleiben erhalten.

### 2.5 Personen
„Ich“ und „Johanna“. Jeder Aufenthalt und jeder Besuch hat eine Teilnehmermenge: nur ich, nur Johanna oder beide.
Standard für neue Einträge: nur ich. Für Reisen ist „beide“ vorbelegt.
Auch Johannas Alltag kann erfasst werden (Aufenthalte mit Teilnehmerin „nur Johanna“).

### 2.6 Länderpunkte
- Ein Land zählt für eine Person, sobald es mindestens einen Aufenthalt oder Besuch gibt, an dem die Person teilnimmt, der im Land liegt und dessen Art für Länderpunkte zählt.
  - Umstieg in Toronto als „Transit“ erfasst: **kein** Punkt für Kanada.
  - Einreise in Peking und Besuch der Chinesischen Mauer während einer 10-stündigen Zwischenlandung: **Punkt** für China.
  - Durchfahrt durch Slowenien als „Anreise/Unterwegs“ mit Tankstopp: **kein** Punkt. Isst man dort richtig in einem Restaurant oder besichtigt etwas: **Punkt**.
- **Gemeinsam** = beide waren zur selben Zeit im Land. Zusätzlich wird angezeigt, welche Länder beide schon besucht haben, aber zu unterschiedlichen Zeiten.
- Länderliste: 193 UN-Staaten plus Kosovo, Vatikan und Taiwan. Gebiete werden dem Mutterland zugeordnet.
- **Wunschländer** werden auf der Karte separat (z. B. schraffiert) dargestellt.
- **Regionen sammeln** (z. B. Bundesländer): pro Land in den Einstellungen aktivierbar. Zuerst für Österreich und Deutschland.

---

## 3. Features und Priorisierung

### MVP (v1)
1. **Heute** (Startbildschirm):
   - Vorschlag laut Muster, mit einem Tipp bestätigen
   - Favoriten (Zuhause, Büro …)
   - „Wechsel ab …“
   - Besuch hinzufügen
   - Mini-Statistik
2. **Standort beim Check-in**: einmalige Abfrage nach Zustimmung, sonst manuelle Suche.
3. **Abend-Erinnerung**: täglich, Uhrzeit einstellbar (Standard 20:30), mit Aktion „Wie vorgeschlagen“ direkt in der Benachrichtigung.
4. **Wochentag-Muster**: fest definiert, z. B. „Di + Do Büro Salzburg“.
5. **Kalender**:
   - Monatsansicht in den Farben der Aufenthaltsarten, Lücken markiert
   - Nachtragen, auch für mehrere Tage gleichzeitig
6. **Timeline**: chronologische Liste mit Tagen, Reisen und Besuchen.
7. **Feiertage**: österreichische Feiertage werden berücksichtigt, Vorschläge entfallen an diesen Tagen.
8. **Orte**:
   - Suche über OSM (Photon/Nominatim), einmal abgerufen und lokal gespeichert
   - Pin auf der Karte setzen
   - Favoriten und Aliase
9. **Reisen** inklusive Nacherfassung ab 2014 mit unscharfen Daten.
10. **Länder**: Liste mit Flagge, gruppiert nach Kontinent. Vergleich Ich / Johanna / Gemeinsam, Fortschritt und Wunschländer.
11. **Karte**:
    - Länder eingefärbt (offline)
    - Zähler-Marker für Städte, Pins für Besuche
    - Filter nach Ebene, Kategorie, Person und Zeitraum
    - Reise-Route
12. **Orts-Akte**: Anzahl Besuche nach Art, erster und letzter Besuch, Notizen.
13. **Statistik (Basis)**: siehe Abschnitt 4, Kennzahlen 1–4.
14. **Daten**: JSON-Export/-Import und automatisches lokales Backup.

### Später
- „Neues Land!“-Moment
- Wunschliste mit Nähe-Hinweis
- Regionen-Sammlungen
- Jahresrückblick, auch als teilbares Bild
- Homeoffice-/Büro-Report als CSV/PDF
- Dropbox-Sync (manuell ausgelöst)
- Share-Intent für Google-Maps-Links
- Foto-Verweise
- Foto-Scanner
- Gelernte Muster
- Geofence-Vorschläge
- App-Sperre

---

## 4. Statistiken (Vorschläge)

| # | Kennzahl | MVP |
|---|---|---|
| 1 | Länderpunkte Ich / Johanna / Gemeinsam, neue Länder pro Jahr | ✔ |
| 2 | Tage pro Aufenthaltsart pro Monat/Jahr (Kalender-Heatmap) | ✔ |
| 3 | Homeoffice- vs. Bürotage pro Monat | ✔ |
| 4 | Urlaubstage gesamt, davon Inland/Ausland | ✔ |
| 5 | Top-Orte nach Tagen; „zuletzt in …“ | später |
| 6 | Neue Städte/Orte pro Jahr | später |
| 7 | Nächte außer Haus | später |
| 8 | Kontinent-Fortschritt | später |

**Zählregel bei geteilten Tagen** (bestätigt):
- Ein Tag zählt in jeder Art, die an ihm vorkommt („Tage mit Büro“).
- Zusätzlich wird eine Hauptart ermittelt: der längste Aufenthalt, bei fehlenden Uhrzeiten der erste.
- Der Homeoffice-Report zählt nur Tage, die **ausschließlich** Homeoffice sind.

---

## 5. Bildschirme / Navigation

Untere Navigationsleiste: **Heute · Kalender · Karte · Länder · Mehr**.
Unter „Mehr“: Timeline, Reisen, Statistik, Orte, Wunschliste, Einstellungen, Daten.

---

## 6. Datenmodell (Entwurf)

```
person         id, name, color, isMe
country        iso2 PK, iso3, nameDe, continent, flagEmoji            (statisch)
region         code PK (ISO 3166-2), countryIso2, nameDe              (statisch/nachladbar)
region_config  countryIso2 PK, collectRegions
place          id, kind[CITY|POI|ADDRESS], name, lat, lon, countryIso2,
               regionCode?, cityName?, parentPlaceId?, osmId?, poiCategory?,
               alias?, isFavorite, note?
stay_type      id, name, icon, color, group[WORK|FREE|VACATION|OTHER],
               countsForCountryPoints, sortOrder, isArchived
stay           id, placeId, stayTypeId, startDate, startTime?, endDate, endTime?,
               datePrecision[DAY|MONTH|YEAR], sortIndex, tripId?, note?, source
stay_person    stayId, personId
visit          id, placeId, date, time?, datePrecision, category, note?, rating?,
               tripId?, photoUri?, countsForCountryPoints, source
visit_person   visitId, personId
trip           id, title, startDate, startTime?, endDate, endTime?, datePrecision, note?
trip_person    tripId, personId
wish           id, placeId? | countryIso2?, note?, priority, createdAt, fulfilledAt?
pattern_rule   id, weekday, stayTypeId, placeId, validFrom?, validTo?
```

Alle veränderbaren Tabellen haben zusätzlich `uuid`, `createdAt`, `updatedAt`,
`deleted` (Soft-Delete) und `originDevice`. Damit ist der spätere Sync vorbereitet.
Länderpunkte, Übernachtungsort und Statistiken werden berechnet, nicht gespeichert.

---

## 7. Architektur

- **UI**: Kotlin, Jetpack Compose, Material 3 mit Dynamic Color und Darkmode. MVVM mit unidirektionalem Datenfluss (StateFlow).
- **Domain**: reines Kotlin mit UseCases. Die Regeln für Länderpunkte, Statistik, Musterlogik und Feiertage sind hier gekapselt und unit-getestet.
- **Data**:
  - Room als Single Source of Truth
  - Repositories
  - kotlinx.serialization für Export/Import
- **Geo**:
  - Land offline per Point-in-Polygon auf gebündeltem Natural-Earth-GeoJSON
  - Orte online über Photon/Nominatim (mit User-Agent und Rate-Limit)
- **Karte**: MapLibre Native, OSM-basierte Kacheln, Länder-Layer offline.
- **System**: WorkManager für Erinnerung und Backup, Notifications, FusedLocation nur bei Bedarf.
- **DI**: Hilt. **Build**: Gradle Kotlin DSL, Version Catalog, KSP.
- **Versionen (Stand M0)**: AGP 9.5.1 (eingebautes Kotlin), Gradle 9.6.0, Kotlin 2.4.21, Compose BOM 2026.11.01,
  minSdk 31, compileSdk/targetSdk 37 (Android 17).
- **Module**: `:app` (Android, UI) und `:core:domain` (reines Kotlin, Regeln und Tests). Ab M1 kommt `:core:data` (Room) dazu.
- **Tests**:
  - JUnit für die Domain
  - Room-Tests (instrumentiert oder Robolectric)
  - Compose-UI-Tests für Kernflows
- **CI (GitHub Actions)**:
  - Push/PR: Lint, Unit-Tests, Debug-APK als Artefakt
  - Tag `v*`: signierte Release-APK als GitHub Release (Keystore in Secrets, nie im Repo)

---

## 8. Meilensteine

| M | Inhalt |
|---|---|
| M0 | Projektgerüst, CI-Workflow, Debug-APK aus GitHub Actions |
| M1 | Datenmodell, Room, Stammdaten (Länder, Arten, Personen), Domain-Regeln mit Tests |
| M2 | Heute-Screen, Muster, Erinnerung, Kalender mit Nachtragen, Feiertage |
| M3 | Ortssuche, Favoriten, Besuche, Reisen, Timeline |
| M4 | Länderliste mit Flaggen, Weltkarte |
| M5 | Basis-Statistik, Export/Import, Backup → Release v1.0 |

---

## 9. Entscheidungen aus der Abstimmung

| Thema | Entscheidung |
|---|---|
| Zählregel geteilter Tage | „Tage mit X“ zählt jede vorkommende Art; Hauptart = längster Aufenthalt; Homeoffice-Report zählt nur reine Homeoffice-Tage |
| Regionen | Zuerst AT und DE |
| Johanna | Alltag und Reisen erfassbar |
| Durchfahrt/Anreise | Kein Länderpunkt; Punkt erst durch Restaurant, Sehenswürdigkeit o. Ä. |
