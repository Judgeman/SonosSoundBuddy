# SoundBuddy

Native Android-App (Kotlin, Jetpack Compose) zum kindgerechten Steuern von
Sonos-Speakern über die offizielle Sonos Control API. Gedacht für ein
Tablet, das von Kindern bedient wird: große Knöpfe, bunte Icons, eine
Lautstärke-Leiste mit Obergrenze und passwortgeschützte Einstellungen für
die Eltern.

> **Datenschutz-Hinweis zum Abgleich über die Cloud:** Wird der automatische
> Abgleich zwischen Tablets genutzt, liegen die Einstellungen der App —
> Namen der Kinder, eigene Fotos, Musikauswahl, Speaker-Einstellungen und der
> Passwort-Hash — im Cloudflare-KV-Speicher des Relay-Workers, also **beim
> Besitzer des Cloudflare-Accounts, in dem der Worker läuft**. Wer dieses
> Repo kopiert, sollte einen eigenen Worker deployen (`SonosConfig.kt`) und
> nicht den eines anderen verwenden. Ohne KV-Speicher im Worker bleibt der
> Abgleich über die Cloud aus; Export/Import und WLAN-Übertragung brauchen
> keine Cloud.

## Zugehöriges Projekt: Relay

Die App braucht den Cloudflare-Worker
**[SonosSoundBuddyRelay](https://github.com/Judgeman/SonosSoundBuddyRelay)**.
Er hält das Sonos-Client-Secret, das nicht in die App gehört, und übernimmt
alle Token-Anfragen bei Sonos:

- `GET /callback` — tauscht beim Login den Authorization-Code gegen Tokens
  und leitet zurück in die App (`sonoscontrol://callback?…`)
- `POST /refresh` — erneuert abgelaufene Access-Tokens
- `/sync/…` — optionaler Speicher für den Abgleich zwischen Tablets
  (braucht ein KV-Binding `SYNC_KV`, siehe README des Relay-Projekts)

Setup, Deploy (auch ohne Wrangler über das Cloudflare-Dashboard) und Tests
sind im README des Relay-Projekts beschrieben. Die Worker-URL wird in der
App in `SonosConfig.kt` eingetragen (siehe unten).

## Funktionen

- **Homescreen**
  - Großes Dropdown oben mit Icon und Namen des gewählten Speakers.
  - Ist in den Settings nur **ein** Speaker freigegeben, ist er immer
    automatisch gewählt (ohne Dropdown-Pfeil). Ansonsten wird beim Start
    der zuletzt gewählte Speaker wiederhergestellt.
  - Rechts daneben die **Profil-Auswahl**, die nur das Tier-Icon des
    gewählten Kinder-Profils zeigt. Gleiche Logik wie bei den Speakern: ein
    aktives Profil ist fest gewählt, bei mehreren wird das zuletzt gewählte
    wiederhergestellt.
  - Aktuelle Wiedergabe über den ganzen Bildschirm, ohne Scrollen: Das Cover
    wächst mit dem freien Platz, darunter Titel und Interpret,
    Fortschrittsbalken mit Position und Dauer („Live“ bei Radio) und klein
    die Lautstärke in Prozent.
  - Große Knöpfe für vorherigen Track, Play/Pause und nächsten Track.
    Skip-Knöpfe sind ausgegraut, wenn die Quelle es nicht erlaubt.
  - Ganz unten der Knopf **„Musik aussuchen“**: öffnet ein großes Popup mit
    der Musikauswahl des Profils — erst die Kategorien als große
    Bild-Kacheln (bei nur einer Kategorie direkt deren Musik), dann die
    Cover-Kacheln. Ein Tipp spielt die Musik sofort auf dem gewählten
    Speaker (die Warteschlange wird ersetzt). Solange die Musik startet,
    liegt ein Popup mit Cover, Namen und Ladekreis über dem Homescreen und
    fängt alle Berührungen und die Zurück-Taste ab. Steht die Kategorie auf
    „Kinder entscheiden“, fragen zwei große Bild-Knöpfe: „Der Reihe nach“
    (Entenmama mit Küken in einer Reihe) oder „Durcheinander“ (rollende
    Würfel) — gezeichnet in `OrderIcons.kt`, damit auch Kinder ohne Lesen
    wählen können. Playlisten mit nur einem Titel laufen ohne Frage der
    Reihe nach.
  - Senkrechte **Lautstärke-Leiste** rechts neben dem Cover mit 10 großen,
    nach oben breiter werdenden Stufen: Tippen oder Ziehen setzt die
    Lautstärke, + / − gehen eine Stufe weiter. Die oberste Stufe ist die
    Maximal-Lautstärke des Speakers.
  - Der **Hintergrund passt sich dem Cover an** (à la Apple Music): Farben
    werden per `androidx.palette` aus dem Cover extrahiert, daraus entsteht
    ein dunkler Verlauf mit weißer Schrift und heller Akzentfarbe. Beim
    Track-Wechsel blenden die Farben sanft über. Die App zeichnet dafür bis
    unter die Statusleiste (Edge-to-Edge).
  - **Tierbesuch:** Alle 10–20 Minuten schaut eins der Profil-Tiere mit
    kleinem gezeichneten Körper vorbei (`AnimalVisitor.kt`): Es läuft unten
    über den Bildschirm und bleibt meist stehen, um zu winken, schaut
    seitlich herein oder taucht unten auf und winkt. Antippen lässt es hüpfen,
    und ein Herz steigt auf; alle anderen Berührungen gehen an den
    Homescreen. Die Pause läuft nur, solange der Homescreen frei zu sehen ist
    (App im Vordergrund, kein Musik-Popup, kein Start-Popup, keine
    Fehlermeldung). Lange auf den Titel „SoundBuddy“ drücken schickt sofort
    ein Tier vorbei.
- **Einstellungen** (Drei-Punkte-Menü oben rechts)
  - **Passwortschutz:** Ist ein Passwort hinterlegt und „Einstellungen nur
    mit Passwort öffnen“ aktiv, wird beim Öffnen danach gefragt. Ohne
    Passwort sind die Einstellungen frei erreichbar und es kann dort eins
    gesetzt werden (aktiviert den Schutz automatisch). Gespeichert wird nur
    ein PBKDF2-Hash mit Salt.
  - **Speaker:** pro Speaker ein Schalter „auf dem Homescreen auswählbar“,
    ein Regler für die **maximale Lautstärke** (5–100 % in 5er-Schritten)
    und ein frei wählbares **Icon**. Beim ersten Abgleich sind alle Speaker
    freigegeben, später neu gefundene müssen erst freigegeben werden.
  - **Musikauswahl:** Kategorien und Musik werden einmal zentral angelegt
    und dann den Profilen zugewiesen — dieselbe Kategorie kann mehrere
    Kinder-Profile bedienen, ohne doppelt gepflegt zu werden. Kategorien
    anlegen (neue sind erst einmal für alle Profile sichtbar), umbenennen,
    sortieren und löschen. Pro Kategorie:
    - **Sichtbar für:** ein Chip pro Profil zum An- und Abwählen.
    - ein **Bild**: eine bunte Szene (**Tanzparty** mit tanzendem Hasen und
      Bär, **Hörbuch** mit Pop-up-Schloss, **Schlaflieder** mit schlafendem
      Mond; `CategoryIcons.kt`), eins der Speaker-Icons oder Tiere oder ein
      **eigenes Foto** (Android-Fotoauswahl, ohne Berechtigung; wird
      verkleinert in den App-Speicher kopiert). Ohne Auswahl die Tanzparty.
    - pro Musik-Eintrag ebenfalls ein eigenes Bild (Tipp auf das Cover) —
      gedacht vor allem für Sonos-Playlisten, die von Sonos kein Cover
      bekommen. „Cover von Sonos“ stellt das Original wieder her.
    - die **Sortierung** der Musik, so wie die Kinder sie sehen:
      „Manuell“ (mit Pfeilen verschieben), „Alphabetisch“ (A–Z oder Z–A,
      Zahlen nach ihrem Wert: „Folge 2“ vor „Folge 10“) oder
      „Hinzugefügt“ (älteste oder neueste zuerst).
    - die **Abspielreihenfolge**: „Der Reihe nach“, „Zufällig“ oder „Kinder
      entscheiden“. Gilt für Playlisten und Alben; Songs, Radio und
      Sonos-Playlisten mit nur einem Titel laufen einfach los. „Musik
    hinzufügen“ öffnet den **Sonos-Katalog** (alle Sonos-Favoriten und
    Sonos-Playlisten) mit Suche und Filter nach Songs, Playlisten, Alben und
    Radio; ein Tipp nimmt einen Eintrag in die Kategorie auf oder wieder
    heraus. Bei Sonos-Playlisten lässt sich die Titelliste ansehen.
  - **Kinder-Profile:** Profile anlegen, pro Profil ein Schalter „auf diesem
    Tablet aktiv“. Ein Tipp öffnet die Profil-Seite mit Name, **Icon**
    (17 gezeichnete Tiere, Einhorn, Pikachu) und einem Schalter pro
    Kategorie der Musikauswahl, ob das Profil sie sieht. Wird ein Profil
    gelöscht, bleiben seine Kategorien für die anderen erhalten.
  - **Tablets abgleichen** (siehe unten): Stand eines Tablets automatisch
    über die Cloud, im WLAN oder als Datei auf andere übertragen.
  - **Abmelden** vom Sonos-Konto.
- **Lautstärke-Obergrenze:** Wird ein Speaker woanders (Sonos-App, Tasten am
  Gerät) über sein Maximum gestellt, regelt die App ihn bei der nächsten
  Abfrage wieder herunter — solange die App im Vordergrund läuft.
- **Anmeldung bleibt erhalten:** Abgelaufene Access-Tokens werden
  automatisch über den Relay-Worker erneuert (siehe unten).

### Icons

32 Material Symbols (Rounded) für Räume und verspielte Motive (Küche,
Wohnzimmer, Kinderzimmer, Rakete, Roboter, Stern, Eis …) sowie selbst
gezeichnete, mehrfarbige Figuren in `CharacterIcons.kt`: **Einhorn** und
**Pikachu**. Beim ersten Abgleich schlägt die App anhand des Raumnamens ein
passendes Icon vor.

Hat ein Titel kein Cover, zeigt die App ein buntes Platzhalter-Bild
(Regenbogen mit lachenden Noten auf rosa-blauem Verlauf), ebenfalls in
`CharacterIcons.kt` gezeichnet. Musik-Einträge ohne Cover bekommen denselben
Verlauf mit einem weißen Symbol.

Für die Kinder-Profile gibt es eigene Icons (`ProfileIcons.kt`): 17 selbst
gezeichnete Tiergesichter in `AnimalIcons.kt` (Katze, Hund, Bär, Panda,
Fuchs, Frosch, Löwe, Schwein, Maus, Hase, Eule, Pinguin, Affe, Koala,
Küken, Marienkäfer, Kuh) sowie Einhorn und Pikachu. In der Bildauswahl
stehen Einhorn und Pikachu nur unter „Tiere“, nicht zusätzlich unter „Icons“.

> Pikachu ist eine geschützte Figur von Nintendo/The Pokémon Company. Für
> den privaten Gebrauch ok — vor einer Veröffentlichung im Play Store das
> Icon entfernen.

## Voraussetzungen

- Android Studio (aktuelle Version), Android SDK 35
- Der deployte Relay-Worker aus
  [SonosSoundBuddyRelay](https://github.com/Judgeman/SonosSoundBuddyRelay)
  mit fester HTTPS-URL, inkl. Endpunkt `POST /refresh`
- Eine Integration auf https://developer.sonos.com mit Client-ID + Secret

## Konfiguration vor dem ersten Build

In `app/src/main/java/de/paul/sonoscontrol/SonosConfig.kt`:

```kotlin
const val CLIENT_ID = "..."                 // aus dem Sonos Developer Portal
const val WORKER_CALLBACK_URL = "https://sonos-relay.<dein-subdomain>.workers.dev/callback"
```

Die Refresh-URL (`…/refresh`) wird automatisch aus `WORKER_CALLBACK_URL`
abgeleitet. Bei Sonos muss als Redirect-URI exakt `WORKER_CALLBACK_URL`
hinterlegt sein.

## Anmeldung

1. „Mit Sonos anmelden“ öffnet einen Custom Tab mit der Sonos-Login-Seite.
2. Nach dem Login redirectet Sonos zum Worker, der den Code gegen Tokens
   tauscht und zu
   `sonoscontrol://callback?access_token=…&refresh_token=…&expires_in=…`
   weiterleitet.
3. Android fängt das über den Intent-Filter in `AndroidManifest.xml` ab,
   `MainActivity.handleIntent()` reicht die Uri an `MainViewModel` weiter
   (inkl. Prüfung des `state`-Parameters gegen CSRF).
4. Tokens und Ablaufzeit liegen verschlüsselt in `TokenStore`
   (EncryptedSharedPreferences).

### Anmeldung erneuern

Sonos-Access-Tokens laufen nach 24 h ab. Zum Erneuern braucht Sonos das
Client-Secret, das nicht in die App gehört — deshalb übernimmt das der
Worker (`POST /refresh`). `SonosApiClient` erneuert selbstständig:

- vorab, wenn die gespeicherte Ablaufzeit in weniger als 5 min erreicht ist,
- sonst bei einer 401-Antwort — danach wird die Anfrage einmal wiederholt,
- bei parallelen Anfragen nur einmal.

Lehnt Sonos den Refresh-Token ab (Zugriff widerrufen o. ä.), zeigt die App
den Login mit dem Hinweis „Die Anmeldung bei Sonos ist abgelaufen“. Bei
Netz- oder Worker-Fehlern bleibt die Anmeldung erhalten; beim nächsten
Öffnen versucht die App es still erneut. Im Logcat (Tag `SonosApi`) steht
nach einer erfolgreichen Erneuerung „Access-Token erneuert“.

## Verwendete Sonos-API-Aufrufe

| Zweck | Aufruf |
|---|---|
| Household | `GET /households` |
| Speaker und Gruppen | `GET /households/{id}/groups` |
| Status / Position | `GET /groups/{id}/playback` |
| Titel, Interpret, Cover | `GET /groups/{id}/playbackMetadata` |
| Lautstärke lesen / setzen | `GET` / `POST /players/{id}/playerVolume` |
| Play/Pause, Skip | `POST /groups/{id}/playback/togglePlayPause`, `skipToNextTrack`, `skipToPreviousTrack` |
| Sonos-Favoriten / -Playlisten | `GET /households/{id}/favorites`, `GET /households/{id}/playlists` |
| Titel einer Playlist | `POST /households/{id}/playlists/getPlaylist` |
| Musik abspielen | `POST /groups/{id}/favorites`, `POST /groups/{id}/playlists` (`action: REPLACE`) |
| Zufall an/aus, Start | `POST /groups/{id}/playback/playMode` (`shuffle`), `POST /groups/{id}/playback/play` |

Die Wiedergabe wird alle 5 s abgefragt, solange die App im Vordergrund ist;
dazwischen läuft die Position lokal weiter. Das Cover wird aus `imageUrl`
oder der `images`-Liste des Tracks bzw. Containers gelesen.

### Musik-Katalog

Die Control API kann keine Musikdienste durchsuchen, sondern nur
**Sonos-Favoriten** und **Sonos-Playlisten** abspielen. Das ist der Katalog
für die Musikauswahl. Einzelne Songs, Alben, Spotify-Playlisten oder
Radiosender kommen also über „Zu Sonos-Favoriten hinzufügen“ in der
Sonos-App in die Auswahl. Einzelne Titel *aus* einer Sonos-Playlist lassen
sich nicht gezielt starten.

**Cover aus dem Heimnetz:** Die Cloud-API liefert für Sonos-Playlisten nie
ein Bild und für manche Favoriten keins, das sich laden lässt. Deshalb
fragt die App beim Laden des Katalogs (und still beim App-Start) direkt
einen Speaker im WLAN — so wie die Sonos-App (`LocalSonosClient.kt`):
Speaker per mDNS (`_sonos._tcp`) finden, dann UPnP-„Browse“ auf
`http://<ip>:1400/MediaServer/ContentDirectory/Control` für `FV:2`
(Favoriten) und `SQ:` (Sonos-Playlisten; ohne eigenes Bild das Cover des
ersten Titels). Zugeordnet wird über die Id (`13` ↔ `FV:2/13` bzw.
`SQ:13`), sonst über den Namen. Diese Cover haben Vorrang; das Cover aus
der Cloud bleibt als Ersatz gespeichert (mehrere Adressen, durch
Zeilenumbruch getrennt) — lädt die erste nicht, probiert die App die
nächste. Apple-Music-Vorlagen wie `{w}x{h}{c}.{f}` werden zu
`600x600bb.jpg` ausgefüllt. Logcat-Tag:
`SonosLocal`.

**Abgelaufene Cover (Apple Music):** Apple liefert Cover als signierte
Adressen, die nur 24 h gültig sind (`X-Amz-Date` + `X-Amz-Expires`). Sonos
speichert die Adresse beim Anlegen des Favoriten und gibt danach immer
dieselbe, längst abgelaufene aus (Antwort: 400). Deshalb:
- abgelaufene Adressen werden erkannt und übersprungen,
- der Speaker liefert über `/getaa?u=<Abspiel-Adresse>` ein frisches Cover
  (die Abspiel-Adresse steht im `res` des lokalen Favoriten),
- Cover werden beim Hinzufügen zur Auswahl **auf dem Tablet gespeichert**
  (`custom_images/cover-item-…jpg`),
- beim Abspielen übernimmt die App das Cover der Playlist bzw. des Albums
  aus der Wiedergabe (`container` in `playbackMetadata`, frische Adresse)
  und ersetzt damit ein vorher gespeichertes Cover — der Speaker liefert
  über `/getaa` bei Apple-Music-Playlisten nur das Cover eines Titels.
  Solche Dateien tragen `-container` im Namen und werden nicht mehr
  überschrieben. Gibt es kein Playlist-Cover, dient das des Titels als
  Ersatz, solange noch gar keins gespeichert ist.
- auch im Katalog sichert die App jede noch gültige signierte Adresse
  sofort (`custom_images/catalog-<hash>-…jpg`, werden beim Aufräumen nicht
  gelöscht). Speichert man einen Favoriten in der Sonos-App neu, ist dessen
  Adresse wieder 24 h gültig — einmal den Katalog öffnen, dann bleibt das
  Cover. Für Apple-Music-Bibliotheks-Playlisten gibt Sonos sonst kein
  Playlist-Cover heraus (auch nicht in der Wiedergabe).
Nicht mehr benutzte Bilder räumt die App beim Start auf.

**Fehlersuche:** Der ⓘ-Knopf an jedem Eintrag im Katalog zeigt Quelle,
Id, woher das Cover kommt, jede bekannte Cover-URL mit Vorschau und ob sie
sich laden lässt (sonst mit Fehlermeldung) sowie die Rohdaten von Sonos.
„Kopieren“ legt alles als Text in die Zwischenablage.

Darüber hinaus sucht die App im ganzen Favoriten (`imageUrl`, `images`, auch an
`resource` oder verschachtelt, nicht aber das Logo des Musikdienstes) und
ersetzt Größen-Platzhalter wie `{w}x{h}`. Findet sie keins, steht im Logcat
(Tag `SonosApi`) „Kein Cover im Favoriten …“ mit dem Roh-JSON; Ladefehler
stehen unter Tag `Cover`. Gespeicherte Einträge bekommen ihr Cover beim
Öffnen des Katalogs und beim App-Start nachgetragen; hat ein Eintrag gar
keins, übernimmt die App beim ersten Abspielen das Cover der Wiedergabe.

Gespeichert werden Id, Name und Cover. Sonos vergibt die Ids selbst. Hat
sich die Id inzwischen geändert (Favorit gelöscht und neu angelegt), sucht
die App beim Abspielen über den Namen.

Für die Reihenfolge lädt die App die Musik erst, ohne sie zu starten, setzt
dann den Zufallsmodus ausdrücklich an oder aus (sonst bliebe er vom letzten
Mal an) und startet die Wiedergabe. Im Zufallsmodus springt sie vorher
einen Titel weiter, damit nicht immer der erste Titel zuerst läuft. Radio
wird ohne diese Schritte direkt gestartet.

## Mehrere Tablets abgleichen

Ein Tablet wird fertig eingestellt, die anderen übernehmen seinen Stand
(Einstellungen → „Tablets abgleichen“) — automatisch über die Cloud, im
WLAN oder als Datei. Das empfangende Tablet sucht sich aus, was es
übernimmt:

| Bereich | Inhalt | Vorauswahl |
|---|---|---|
| Kinder-Profile und Musikauswahl | Profile (Name, Icon), alle Kategorien mit Musik, Reihenfolge, eigenen Bildern und gespeicherten Covern sowie welches Profil welche Kategorie sieht | an |
| Speaker-Einstellungen | Icon und maximale Lautstärke | an |
| Speaker-Freigabe | „auf dem Homescreen auswählbar“ | aus |
| Passwort | Hash, Salt und „nur mit Passwort öffnen“ | aus |

Die zuletzt gewählten Bereiche merkt sich das Tablet als Vorauswahl.
Übernommenes ersetzt den Stand auf dem Tablet: Die Musikauswahl wird
komplett ersetzt, Profile, die es auf dem sendenden Tablet nicht gibt,
werden gelöscht. Je Tablet erhalten bleiben,
welche Profile dort aktiv sind und welcher Speaker und welches Profil
zuletzt gewählt waren. Speaker werden nur ergänzt, nie gelöscht; noch
unbekannte kommen ohne übernommene Freigabe gesperrt und als „Neu“ dazu.

Profile finden sich über eine Kennung (`syncId`) wieder, die auf allen
Tablets gleich ist. Beim allerersten Abgleich, wenn auf beiden Tablets
schon von Hand Profile angelegt wurden, werden sie über den Namen
zugeordnet. Speaker und Musik passen ohne Umrechnung zusammen, weil die
Ids von Sonos im ganzen Haushalt gleich sind.

**Automatisch über die Cloud:** Pro Tablet wird eine Rolle gewählt:

- **Haupt-Tablet** — lädt seinen Stand beim App-Start und beim Schließen
  der Einstellungen in den KV-Speicher des Relay-Workers, aber nur, wenn
  sich etwas geändert hat (Prüfsumme). Das kann auch ein Handy mit der App
  sein. Es sollte nur ein Haupt-Tablet geben, sonst gewinnt der letzte Upload.
- **Stand übernehmen** — sieht beim Start und alle 5 Minuten nach (solange
  die App im Vordergrund ist), ob es eine neue Version gibt. Mit
  „Automatisch übernehmen“ werden die gewählten Bereiche ohne Nachfrage
  übernommen — nicht, solange die Einstellungen offen sind, dann beim
  Schließen. Ohne wird der neue Stand auf der Einstellungs-Seite und unter
  „Tablets abgleichen“ angezeigt und erst auf „Ansehen“ → „Übernehmen“
  übernommen; „Ignorieren“ wartet auf den nächsten Stand. Die Kinder sehen
  auf dem Homescreen nichts davon.
- **Aus** — Standard.

Bilder werden nach ihrem SHA-256 benannt und nur hochgeladen bzw.
heruntergeladen, wenn sie fehlen. „Daten aus der Cloud löschen“ entfernt den
Stand des Haushalts aus dem Speicher.

**Getrennte Haushalte:** Alles läuft je Sonos-Haushalt. Der Worker prüft
bei jeder Anfrage bei Sonos, ob der Access-Token der App zu dem Haushalt
gehört, und legt die Daten unter dem Haushalt ab — andere Konten oder
Haushalte kommen nicht heran. Die App merkt sich gesehene Versionen und den
letzten Upload ebenfalls je Haushalt; wird ein Tablet bei einem anderen
Sonos-Konto angemeldet, beginnt der Abgleich dort neu. Jeder Stand trägt
die Haushalts-Id: Stände aus einem anderen Haushalt werden auch beim Import
einer Datei oder im WLAN abgelehnt.

**Im WLAN:** Auf dem einen Tablet „Dieses Tablet freigeben“ tippen. Es
meldet sich per mDNS (`_soundbuddy._tcp`) im Heimnetz an und zeigt einen
vierstelligen Code. Auf dem anderen Tablet „Daten von einem anderen Tablet
holen“ tippen, das Tablet wählen und den Code eingeben. Mehrere Tablets
können nacheinander abholen. Nach 5 falschen Codes endet die Freigabe,
ebenso beim Verlassen der Seite oder wenn die App in den Hintergrund geht.
Die Daten gehen unverschlüsselt durchs Heimnetz (`LocalTransfer.kt`, eigenes
kleines TCP-Protokoll); das Passwort wird nur als Hash übertragen.

**Als Datei:** „Exportieren“ speichert eine ZIP-Datei (`soundbuddy.json`
plus `images/`) über die Android-Dateiauswahl, z. B. in Google Drive. Das
Passwort kommt nur mit, wenn es beim Export angehakt wird. „Importieren“
liest die Datei auf dem anderen Tablet ein.

## Datenbank

Lokale Room-Datenbank `sound_buddy.db` (`AppDatabase.kt`, Version 9):

- `speaker_config` — playerId, Name, freigegeben, Icon-Schlüssel,
  maximale Lautstärke (Spalte seit Version 2, Migration `MIGRATION_1_2`)
- `app_setting` — Key-Value: Passwort-Hash/-Salt, Passwortschutz an/aus,
  zuletzt gewählter Speaker und zuletzt gewähltes Profil
- `child_profile` — Name, Icon-Schlüssel, auf diesem Tablet aktiv
  (seit Version 4, Migration `MIGRATION_3_4`, ebenso die beiden folgenden),
  Kennung für den Abgleich zwischen Tablets (`syncId`, seit Version 9,
  Migration `MIGRATION_8_9`; vergibt jedem vorhandenen Profil eine)
- `music_category` — Name, Position, Bild (`scene:…`, `icon:…`,
  `animal:…`, `file:…` oder leer = Standard-Bild), Abspielreihenfolge
  (Bild und Reihenfolge seit Version 5, Migration `MIGRATION_4_5`). Bis
  Version 7 gehörte jede Kategorie genau einem Profil. Sortierung der
  Musik (`itemSort`, `itemSortDescending`, seit Version 10, Migration
  `MIGRATION_9_10`).
- `profile_category` — welches Profil welche Kategorie sieht (seit
  Version 8, Migration `MIGRATION_7_8`: übernimmt die bisherige Zuordnung,
  entfernt die Profil-Spalte aus `music_category` und nummeriert die
  Reihenfolge profilweise hintereinander durch)
- `music_item` — Kategorie, Quelle (Favorit/Playlist), Sonos-Id, Name,
  Beschreibung, Cover-URL, Art (Song, Album, …), Position, eigenes Bild
  (seit Version 6, Migration `MIGRATION_5_6`; sie setzt außerdem früher als
  Kategorie-Bild gewählte Cover zurück), Anzahl der Titel (seit Version 7,
  Migration `MIGRATION_6_7`; übernimmt sie aus der Beschreibung, danach
  aktualisiert beim Laden des Katalogs), Zeitpunkt des Hinzufügens
  (`addedAt`, seit Version 10; bei älteren Einträgen 0 — sie zählen als die
  ältesten, untereinander in der Reihenfolge ihrer Id)

## Projektstruktur

Alle Quellen liegen in `app/src/main/java/de/paul/sonoscontrol/`:

| Datei | Inhalt |
|---|---|
| `MainActivity.kt` | Einstieg, Navigation Home/Settings/Profil/Musikauswahl/Katalog/Abgleich, Theme |
| `MainViewModel.kt` | Zustand, Speaker- und Profil-Auswahl, Polling, Befehle, Katalog, Passwort |
| `HomeScreen.kt` | Homescreen: Dropdown, Cover, Fortschritt, Knöpfe |
| `MusicPicker.kt` | Profil-Dropdown, „Musik aussuchen“-Knopf und -Popup |
| `VolumeBar.kt` | Stufen-Lautstärke-Leiste |
| `AnimalVisitor.kt` | Tierbesuch auf dem Homescreen |
| `CoverColors.kt` | Farben aus dem Cover, Cover-Theme, Statusleiste |
| `SettingsScreen.kt` | Einstellungen, Icon-Auswahl, Passwort-Dialog |
| `ProfileSettingsScreens.kt` | Profil-Liste, Profil-Seite, zentrale Musikauswahl mit Kategorien, Katalog-Auswahl |
| `MusicCatalog.kt`, `MusicCover.kt` | Katalog-Einträge, Musik-Typen, Abspielreihenfolge, eigene Bilder, Cover-Kachel |
| `CustomImageStore.kt` | Eigene Bilder für Kategorien und Einträge importieren (verkleinern, drehen) und löschen |
| `SpeakerIcons.kt`, `CharacterIcons.kt` | Icon-Katalog, Einhorn und Pikachu |
| `ProfileIcons.kt`, `AnimalIcons.kt` | Profil-Icons, gezeichnete Tiere |
| `SonosApiClient.kt`, `SonosModels.kt` | Sonos-API inkl. Token-Erneuerung |
| `LocalSonosClient.kt` | Cover direkt vom Speaker im Heimnetz (mDNS + UPnP) |
| `SonosAuthManager.kt`, `TokenStore.kt`, `SonosConfig.kt` | Login und Tokens |
| `AppDatabase.kt`, `SettingsRepository.kt`, `PasswordHasher.kt` | Datenbank und Einstellungen |
| `SyncScreen.kt`, `SyncViewModel.kt` | Seite „Tablets abgleichen“ |
| `SyncPackage.kt`, `SyncRepository.kt` | Datenformat (ZIP) für den Abgleich, Einpacken und Übernehmen |
| `LocalTransfer.kt` | Übertragung zwischen Tablets im WLAN (mDNS + TCP) |
| `CloudSync.kt`, `CloudSyncClient.kt` | Abgleich über den Relay-Worker: hochladen, nachsehen, abholen |

Wichtige Bibliotheken: Compose Material 3 und `material-icons-extended`,
Room 2.6 (über KSP), Coil 2 für Cover, `androidx.palette`, OkHttp,
kotlinx.serialization.

## Bekannte Stolpersteine

- `minSdk = 26` — Custom Tabs + EncryptedSharedPreferences setzen das
  faktisch voraus.
- Klartext-HTTP ist erlaubt (`res/xml/network_security_config.xml`), weil
  Sonos Cover je nach Quelle (z. B. AirPlay) direkt vom Speaker im Heimnetz
  liefert (`http://<ip>:1400/getaa?…`). Die Sonos-API selbst läuft weiter
  über HTTPS.
- Die App nutzt ein eigenes Theme ohne ActionBar (`res/values/themes.xml`);
  Titel und Menü kommen aus der Compose-TopAppBar.
- Der Worker MUSS exakt dieselbe `redirect_uri` verwenden wie die App beim
  Auth-Request — sonst lehnt Sonos den Token-Exchange ab.
