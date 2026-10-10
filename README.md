# SoundBuddy

Native Android-App (Kotlin, Jetpack Compose) zum kindgerechten Steuern von
Sonos-Speakern über die offizielle Sonos Control API. Gedacht für ein
Tablet, das von Kindern bedient wird: große Knöpfe, bunte Icons, eine
Lautstärke-Leiste mit Obergrenze und passwortgeschützte Einstellungen für
die Eltern.

> **Datenschutz-Hinweis zum Abgleich über die Cloud:** Wird der automatische
> Abgleich zwischen Tablets genutzt, liegen die Einstellungen der App —
> Namen der Kinder, eigene Fotos, Musikauswahl, welches Kind welche Musik
> schon gespielt hat, Speaker-Einstellungen und der Passwort-Hash — im Cloudflare-KV-Speicher des Relay-Workers, also **beim
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
  (braucht ein KV-Binding `SYNC_KV`, siehe README des Relay-Projekts),
  auch für die schon gespielte Musik (`/sync/played`)

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
  - **Neue Musik:** Was das gewählte Kind noch nie gespielt hat, trägt oben
    rechts am Cover einen kleinen pinken Stern mit Funkeln — ebenso die
    Kategorie, in der so etwas steckt. Das gilt je Kinder-Profil: Hört Mia
    etwas, bleibt es für Paul neu. Sobald Sonos die Musik angenommen hat, ist
    sie für das Kind nicht mehr neu, auch nicht in anderen Kategorien und —
    mit dem Abgleich über die Cloud — auf den anderen Tablets (siehe unten).
    Die Eltern können Musik in den Einstellungen wieder als neu markieren
    (siehe „Neu-Markierung“). Was ein Profil schon vor dem Update auf diese
    Version in seiner Auswahl hatte, gilt als gespielt; neu ist erst, was
    danach dazukommt — auch eine Kategorie, die ein Kind später zu sehen
    bekommt, und für ein neu angelegtes Profil alles. Musik, die in der
    Sonos-App gestartet wurde, zählt nicht.
  - Senkrechte **Lautstärke-Leiste** rechts neben dem Cover mit 10 großen,
    nach oben breiter werdenden Stufen: Tippen oder Ziehen setzt die
    Lautstärke, + / − gehen eine Stufe weiter. Die oberste Stufe ist die
    Maximal-Lautstärke des Speakers.
  - Der **Hintergrund passt sich dem Cover an** (à la Apple Music): Farben
    werden per `androidx.palette` aus dem Cover extrahiert, daraus entsteht
    ein dunkler Verlauf mit weißer Schrift und heller Akzentfarbe. Beim
    Track-Wechsel blenden die Farben sanft über. Die App zeichnet dafür bis
    unter die Statusleiste (Edge-to-Edge).
  - **Tiere zur Musik** (`NowPlayingAnimals.kt`): Das Tier des gewählten
    Profils sitzt mit kleinem gezeichneten Körper auf der unteren rechten
    Ecke des Covers. Solange Musik läuft, tanzt es im Takt und bunte Noten
    steigen auf — oder es liest vor: Es hält ein aufgeschlagenes Bilderbuch
    zu den Kindern hin, blättert um (links jedes Mal ein neues Bild), und
    Buchstaben steigen auf. Was es tut, ist pro Kategorie eingestellt
    („Tier am Cover“); maßgeblich ist die Kategorie, aus der auf dem
    gewählten Speaker zuletzt etwas gestartet wurde (je Speaker gemerkt).
    Ist das unbekannt — z. B. in der Sonos-App gestartet, Kategorie
    gelöscht oder nach einem Abgleich neu angelegt —, tanzt es. Bei Pause
    schläft es mit geschlossenen Augen und „Zzz“ (das Buch zugeklappt),
    beim Laden steht es wach da. Antippen lässt es hüpfen. Ein kleineres
    steht auf dem Fortschrittsbalken an der Stelle des Fortschritts und
    läuft mit, solange die Musik spielt (bei Radio in der Mitte auf der
    Stelle). Ohne Profil fehlen beide.
  - **Tierbesuch:** Alle 10–20 Minuten schaut eins der Profil-Tiere mit
    kleinem gezeichneten Körper vorbei (`AnimalVisitor.kt`): Es läuft unten
    über den Bildschirm und bleibt meist stehen, um zu winken, schaut
    seitlich herein oder taucht unten auf und winkt. Antippen lässt es hüpfen,
    und ein Herz steigt auf; alle anderen Berührungen gehen an den
    Homescreen. Die Pause läuft nur, solange der Homescreen frei zu sehen ist
    (App im Vordergrund, kein Musik-Popup, kein Start-Popup, keine
    Fehlermeldung). Lange auf den Titel „SoundBuddy“ drücken schickt sofort
    ein Tier vorbei.
  - **Fehlermeldung:** Geht bei der Wiedergabe etwas schief, liegt ein großer
    Hinweis über dem Homescreen, bis jemand „Okay“ drückt. Darauf ist statt
    eines traurigen Smileys der SoundBuddy zu sehen (`ErrorBuddy.kt`): ein
    kleiner lila Lautsprecher, der seinen ausgesteckten Stecker in der Hand
    hält, abwechselnd darauf schaut und sich ratlos am Kopf kratzt, über ihm
    ein wippendes Fragezeichen. Antippen lässt ihn hüpfen und lachen, und
    ein Herz steigt auf. Dieselbe Figur zeigt auch die Fehlerseite, wenn die
    Speaker nicht geladen werden können.
  - **Akku-Anzeige** klein oben rechts in der Kopfzeile, neben dem Menü
    (`BatteryIndicator.kt`): Prozentzahl und eine gezeichnete Batterie mit
    Füllstand — gedacht für die Eltern, wenn das Tablet fixiert läuft. Am
    Strom wird die Füllung grün mit Blitz, ohne Strom bei 20 % und weniger
    rot. Der Stand kommt über das System-Ereignis `ACTION_BATTERY_CHANGED`,
    also ohne Berechtigung und ohne ständiges Nachfragen. Geräte ohne Akku
    zeigen nichts an. Links daneben steht klein die **App-Version**
    (z. B. „v1.0.0“), damit man auf einen Blick sieht, was auf einem Tablet
    läuft.
- **Einstellungen** (Drei-Punkte-Menü oben rechts)
  - **Passwortschutz:** Ist ein Passwort hinterlegt und „Einstellungen nur
    mit Passwort öffnen“ aktiv, wird beim Öffnen danach gefragt. Ohne
    Passwort sind die Einstellungen frei erreichbar und es kann dort eins
    gesetzt werden (aktiviert den Schutz automatisch). Gespeichert wird nur
    ein PBKDF2-Hash mit Salt.
  - Die Einstellungs-Seite zeigt ganz oben die **App-Version**
    (`versionName` aus `app/build.gradle.kts`), dann die **Musikauswahl**,
    darunter Speaker, Kinder-Profile, Abgleich, Passwort, Konto und Release
    Notes. Speaker und Kategorien haben eigene Unterseiten, damit die
    Hauptseite kurz bleibt.
  - **Speaker** (eigene Unterseite „Speaker verwalten“; die Hauptseite
    zeigt nur, wie viele es gibt, wie viele auswählbar und ob neue dazu
    gekommen sind): pro Speaker ein Schalter „auf dem Homescreen
    auswählbar“, ein Regler für die **maximale Lautstärke** (5–100 % in
    5er-Schritten) und ein frei wählbares **Icon**. Beim ersten Abgleich
    sind alle Speaker freigegeben, später neu gefundene müssen erst
    freigegeben werden.
  - **Musikauswahl:** Kategorien und Musik werden einmal zentral angelegt
    und dann den Profilen zugewiesen — dieselbe Kategorie kann mehrere
    Kinder-Profile bedienen, ohne doppelt gepflegt zu werden. Die
    Übersicht zeigt pro Kategorie nur Bild, Name (mit Stift zum
    Umbenennen), wie viele Einträge sie hat und wer sie sieht, und lässt
    die Reihenfolge ändern: am Griff ziehen (Drag & Drop) oder mit den
    Pfeilen — langes Drücken auf einen Pfeil setzt die Kategorie ganz nach
    oben bzw. unten. Neue Kategorien sind erst einmal für alle Profile
    sichtbar. Ein Tipp auf eine Kategorie öffnet ihre eigene Seite (dort
    auch Bild und Name änderbar und „Kategorie löschen“ im Menü). Pro
    Kategorie:
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
      „Manuell“ (am Griff ziehen oder mit den Pfeilen verschieben; langes
      Drücken auf einen Pfeil setzt den Eintrag ganz nach oben bzw. unten),
      „Alphabetisch“ (A–Z oder Z–A,
      Zahlen nach ihrem Wert: „Folge 2“ vor „Folge 10“) oder
      „Hinzugefügt“ (älteste oder neueste zuerst).
    - die **Abspielreihenfolge**: „Der Reihe nach“, „Zufällig“ oder „Kinder
      entscheiden“. Gilt für Playlisten und Alben; Songs, Radio und
      Sonos-Playlisten mit nur einem Titel laufen einfach los.
    - **Tier am Cover**: „Tanzen“ oder „Vorlesen“. Kategorien mit „Hörbuch“,
      „Hörspiel“, „Geschichte“ oder „Märchen“ im Namen (oder dem
      Hörbuch-Bild) lesen von Anfang an vor, alle anderen tanzen.
    - **Neu-Markierung:** Der Stern-Knopf an jedem Eintrag zeigt, für welche
      Kinder (unter denen, die die Kategorie sehen) er neu ist, und stellt das
      je Kind um; unter dem Namen steht „Neu für …“. Im Menü der Kategorie
      markieren „Alle als neu markieren“ und „Alle als gespielt markieren“ die
      ganze Kategorie für alle diese Kinder auf einmal.
    - **Neue Musik einfügen:** „Am Anfang“ oder „Am Ende“ der Kategorie
      (wird gemerkt, gilt für die manuelle Sortierung). Mehrere Einträge,
      die in einem Durchgang an den Anfang kommen, stehen dort in der
      Reihenfolge des Antippens.

    „Musik hinzufügen“ öffnet den **Sonos-Katalog** (alle Sonos-Favoriten und
    Sonos-Playlisten) mit Suche (auch nach Künstler) und Filter nach Songs,
    Playlisten, Alben und Radio; ein Tipp nimmt einen Eintrag in die
    Kategorie auf oder wieder heraus. Bei Sonos-Playlisten lässt sich die
    Titelliste ansehen. Jeder Eintrag zeigt den **Künstler** (soweit Sonos
    ihn nennt) und, in welchen Kategorien er schon steckt — was noch in
    keiner steckt, trägt das Schild „Noch in keiner Kategorie“. Der Filter
    **„Ohne Kategorie“** zeigt nur diese Einträge; was man dort antippt,
    bleibt stehen, bis man den Filter wechselt.
  - **Kinder-Profile:** Profile anlegen, pro Profil ein Schalter „auf diesem
    Tablet aktiv“. Ein Tipp öffnet die Profil-Seite mit Name, **Icon**
    (18 gezeichnete Tiere, Einhorn, Pikachu) und einem Schalter pro
    Kategorie der Musikauswahl, ob das Profil sie sieht. Wird ein Profil
    gelöscht, bleiben seine Kategorien für die anderen erhalten.
  - **Tablets abgleichen** (siehe unten): Stand eines Tablets automatisch
    über die Cloud, im WLAN oder als Datei auf andere übertragen.
  - **Abmelden** vom Sonos-Konto.
  - **Release Notes** (eigene Unterseite): alle Versionen mit ihren
    Neuerungen, die neueste oben; die installierte trägt das Schild
    „Installiert“.
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

Für die Kinder-Profile gibt es eigene Icons (`ProfileIcons.kt`): 18 selbst
gezeichnete Tiergesichter in `AnimalIcons.kt` (Katze, Hund, Bär, Panda,
Fuchs, Frosch, Löwe, Schwein, Maus, Hase, Eule, Pinguin, Affe, Koala,
Küken, Marienkäfer, Kuh, Hund Sam) sowie Einhorn und Pikachu. In der
Bildauswahl stehen Einhorn und Pikachu nur unter „Tiere“, nicht zusätzlich
unter „Icons“.

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

**Künstler:** Die Cloud-API nennt ihn bei Favoriten selten. Die App sucht
im Favoriten nach `artist`, `artists`, `artistName`, `albumArtist` oder
`creator` (als Text oder Objekt mit `name`, nicht beim Musikdienst) und
fragt sonst den Speaker im Heimnetz: `dc:creator` bzw. `upnp:artist` am
Favoriten oder in seinem eingebetteten DIDL (`r:resMD`). Bei
Sonos-Playlisten stammen die Künstler aus allen Titeln. Die Liste zeigt die
häufigsten drei (bei mehr mit „u. a.“), die Suche findet alle — ein
gesuchter Künstler rückt dabei nach vorne. Die Details zeigen alle.
Künstler werden nicht gespeichert, nur im Katalog angezeigt.

**Fehlersuche:** Der ⓘ-Knopf an jedem Eintrag im Katalog zeigt Künstler,
Kategorien, Quelle, Id, woher das Cover kommt, jede bekannte Cover-URL mit Vorschau und ob sie
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
| Kinder-Profile und Musikauswahl | Profile (Name, Icon), alle Kategorien mit Musik, Reihenfolge, Tier am Cover, eigenen Bildern und gespeicherten Covern, welches Profil welche Kategorie sieht und welches Kind welche Musik schon gespielt hat | an |
| Speaker-Einstellungen | Icon und maximale Lautstärke | an |
| Speaker-Freigabe | „auf dem Homescreen auswählbar“ | aus |
| Passwort | Hash, Salt und „nur mit Passwort öffnen“ | aus |

Die zuletzt gewählten Bereiche merkt sich das Tablet als Vorauswahl.
Übernommenes ersetzt den Stand auf dem Tablet: Die Musikauswahl wird
komplett ersetzt, Profile, die es auf dem sendenden Tablet nicht gibt,
werden gelöscht. Nur die schon gespielte Musik wird zusammengeführt: Je Kind
und Musik gewinnt die neuere Änderung, ob gespielt oder wieder als neu
markiert. Je Tablet erhalten bleiben,
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

**Schon gespielte Musik** gleichen alle Tablets mit „Haupt-Tablet“ oder
„Stand übernehmen“ untereinander ab, in beide Richtungen: Jedes Tablet lädt
hoch, was die Kinder dort gespielt und die Eltern dort als neu oder gespielt
markiert haben, und holt sich, was auf den anderen passiert ist — beim Start,
alle 5 Minuten, beim Öffnen der Musikauswahl (höchstens alle 30 Sekunden),
gleich nachdem ein Kind hier etwas zum ersten Mal gespielt hat und beim
Schließen der Einstellungen. Jeder Eintrag sagt je Kinder-Profil
(`syncId`) und Musik, ob sie gespielt ist, und wann sich das zuletzt
geändert hat; beim Zusammenführen gewinnt die neuere Änderung, bei gleichem
Zeitpunkt „gespielt“. So kommt auch „wieder neu“ auf allen Tablets an. Im
Worker hat jedes Tablet einen eigenen Eintrag unter einer zufälligen Kennung
(`cloud_sync_tablet_id`), so überschreiben sich zwei Tablets nie
gegenseitig. Hochgeladen wird nur, wenn ein Tablet etwas kennt, das in der
Cloud noch fehlt. Erkannt wird Musik an Quelle, Sonos-Id und Namen,
unabhängig von der Kategorie. Bekommt ein Profil beim allerersten Abgleich
die Kennung vom anderen Tablet, zieht das dort Gespielte mit. Unter „Tablets abgleichen“ steht, wann
zuletzt abgeglichen wurde; ein älterer Worker ohne `/sync/played` meldet sich
dort mit dem Hinweis, ihn neu zu deployen. Ohne Cloud reist die gespielte
Musik beim Übertragen als Datei oder im WLAN mit.

Bilder werden nach ihrem SHA-256 benannt und nur hochgeladen bzw.
heruntergeladen, wenn sie fehlen. „Daten aus der Cloud löschen“ entfernt den
Stand des Haushalts und die gespielte Musik aller Tablets aus dem Speicher.
Sobald danach auf einem Tablet etwas zum ersten Mal gespielt wird, lädt es
seine gespielte Musik wieder hoch, das Haupt-Tablet seinen Stand beim
nächsten Schließen der Einstellungen — wer die Cloud nicht mehr nutzen will,
stellt vorher alle Tablets auf „Aus“.

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

Lokale Room-Datenbank `sound_buddy.db` (`AppDatabase.kt`, Version 12):

- `speaker_config` — playerId, Name, freigegeben, Icon-Schlüssel,
  maximale Lautstärke (Spalte seit Version 2, Migration `MIGRATION_1_2`)
- `app_setting` — Key-Value: Passwort-Hash/-Salt, Passwortschutz an/aus,
  zuletzt gewählter Speaker und zuletzt gewähltes Profil, je Speaker die
  Kategorie der zuletzt gestarteten Musik (`last_category_<playerId>`),
  ob neue Musik an den Anfang kommt (`add_music_at_start`), die Kennung
  des Tablets für die gespielte Musik in der Cloud (`cloud_sync_tablet_id`)
- `child_profile` — Name, Icon-Schlüssel, auf diesem Tablet aktiv
  (seit Version 4, Migration `MIGRATION_3_4`, ebenso die beiden folgenden),
  Kennung für den Abgleich zwischen Tablets (`syncId`, seit Version 9,
  Migration `MIGRATION_8_9`; vergibt jedem vorhandenen Profil eine)
- `music_category` — Name, Position, Bild (`scene:…`, `icon:…`,
  `animal:…`, `file:…` oder leer = Standard-Bild), Abspielreihenfolge
  (Bild und Reihenfolge seit Version 5, Migration `MIGRATION_4_5`). Bis
  Version 7 gehörte jede Kategorie genau einem Profil. Sortierung der
  Musik (`itemSort`, `itemSortDescending`, seit Version 10, Migration
  `MIGRATION_9_10`). Tier am Cover (`coverAnimation`, seit Version 11,
  Migration `MIGRATION_10_11`; Hörbücher & Co. lesen gleich vor).
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
- `played_music` — je Kinder-Profil (`profileSyncId`) und Musik (`musicKey`,
  Text aus Quelle, Sonos-Id und Name, z. B. `FAVORITE:12:Bibi Blocksberg`),
  ob das Kind sie schon gespielt hat (`played`; false = wieder als neu
  markiert) und wann sich das geändert hat (`changedAt`) — auf diesem oder
  einem anderen Tablet. Ohne Eintrag ist die Musik neu (seit Version 12,
  Migration `MIGRATION_11_12`; trägt je Profil alles als gespielt ein, was
  es schon sieht, mit Zeitpunkt 0)

## Neue Version veröffentlichen

1. In `app/build.gradle.kts` `versionName` (z. B. `1.1.0`) und `versionCode`
   (um 1 höher) setzen — Android installiert ein Update nur, wenn der
   `versionCode` nicht kleiner ist als der installierte.
2. In `ReleaseNotes.kt` oben in `ReleaseHistory` einen Eintrag mit Version,
   Datum und den Neuerungen ergänzen.
3. Auf GitHub ein Release mit dem Tag `v<versionName>` und denselben
   Neuerungen anlegen.

## Projektstruktur

Alle Quellen liegen in `app/src/main/java/de/paul/sonoscontrol/`:

| Datei | Inhalt |
|---|---|
| `MainActivity.kt` | Einstieg, Navigation Home/Settings/Profil/Musikauswahl/Katalog/Abgleich/Release Notes, Theme |
| `MainViewModel.kt` | Zustand, Speaker- und Profil-Auswahl, Polling, Befehle, Katalog, Passwort |
| `HomeScreen.kt` | Homescreen: Dropdown, Cover, Fortschritt, Knöpfe |
| `ErrorBuddy.kt` | Animierter SoundBuddy für Fehlermeldungen |
| `MusicPicker.kt` | Profil-Dropdown, „Musik aussuchen“-Knopf und -Popup mit Stern für neue Musik |
| `VolumeBar.kt` | Stufen-Lautstärke-Leiste |
| `BatteryIndicator.kt` | Kleine Akku-Anzeige in der Kopfzeile |
| `AnimalFigure.kt` | Profil-Tiere als Figur mit Körper, geschlossene Augen zum Schlafen |
| `NowPlayingAnimals.kt`, `AnimalVisitor.kt` | Tier am Cover und auf dem Fortschrittsbalken, Tierbesuch |
| `CoverColors.kt` | Farben aus dem Cover, Cover-Theme, Statusleiste |
| `SettingsScreen.kt` | Einstellungen, Icon-Auswahl, Passwort-Dialog |
| `ReleaseNotes.kt` | Versionsliste mit Neuerungen und die Seite „Release Notes“ |
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
| `CloudSync.kt`, `CloudSyncClient.kt` | Abgleich über den Relay-Worker: hochladen, nachsehen, abholen; gespielte Musik |

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
