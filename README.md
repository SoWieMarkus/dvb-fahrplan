# DVB-Fahrplan

Disclaimer! The app is NOT related to the Dresdner Verkehrsbetriebe (DVB) or the Verkehrsverbund Oberelbe (VVO).

<img height="150px" src="https://github.com/SoWieMarkus/DVB-Fahrplan/blob/main/app/src/main/ic_app_logo-playstore.png"></a><a href="https://play.google.com/store/apps/details?id=markus.wieland.dvbfahrplan"><img src="https://upload.wikimedia.org/wikipedia/commons/7/78/Google_Play_Store_badge_EN.svg" alt="Get it on Google Play" height="150px"></a>

## Features

* One screen for everything: enter only a start stop to get its **live departure board**, add a destination to get **connections**
* Live data: departures and trips refresh every 30 seconds while visible, delays and cancellations included
* Earlier / later connections (continues the server-side search session)
* Trip view: the whole run of a tram/bus with its current position on a map
* Route details on a map (MapLibre, vector tiles), share a connection as text
* Favorite stops, recently used stops
* Current service changes (Verkehrsmeldungen)

## Tech

* Kotlin, Jetpack Compose with an own small design system (no Material), Navigation 3
* Hilt, Coroutines/Flow, Room
* OkHttp + kotlinx.serialization against the <a href="https://webapi.vvo-online.de">VVO WebAPI</a> (<a href="https://github.com/kiliankoe/vvo/blob/main/documentation/webapi.md">documentation</a>)
* <a href="https://maplibre.org/">MapLibre Native</a> with <a href="https://openfreemap.org/">OpenFreeMap</a> tiles, © OpenStreetMap contributors

### How the VVO API works (short)

* Every endpoint is a JSON `POST`. Send `Accept: application/json` and `"format":"json"`, otherwise some endpoints answer with an HTML page.
* Coordinates are Gauss-Krüger (zone 4); `core/geo/GaussKrueger.kt` converts them to WGS84.
* `tr/trips` returns a `SessionId`: the server keeps the search, and `tr/prevnext` with that id returns all routes so far plus earlier/later ones.
* There is no push channel: "live" simply means asking `dm` / `dm/trip` again.

## Build

```
./gradlew assembleDebug testDebugUnitTest
```

## Screenshots

<img width="25%" src="https://github.com/SoWieMarkus/DVB-Fahrplan/blob/main/screenshots/englisch/Screenshot_20210425-161238_DVB%20Fahrplan.jpg"><img width="25%" src="https://github.com/SoWieMarkus/DVB-Fahrplan/blob/main/screenshots/englisch/Screenshot_20210425-161301_DVB%20Fahrplan.jpg">
