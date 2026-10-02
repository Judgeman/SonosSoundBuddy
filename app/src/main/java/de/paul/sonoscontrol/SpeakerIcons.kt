package de.paul.sonoscontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bathtub
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Deck
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Icecream
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Toys
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.Yard
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Auswahl an Material-Symbols (Rounded) für die Speaker. Der Enum-Name wird
 * als Schlüssel in der Datenbank gespeichert — Einträge also nicht umbenennen.
 */
enum class SpeakerIcon(
    val label: String,
    val vector: ImageVector,
    val color: Color,
    /** Mehrfarbige Figur (siehe CharacterIcons.kt) — wird nicht weiß eingefärbt. */
    val multicolor: Boolean = false
) {
    SPEAKER("Lautsprecher", Icons.Rounded.Speaker, Color(0xFF5C6BC0)),
    KITCHEN("Küche", Icons.Rounded.Kitchen, Color(0xFFFF7043)),
    DINING("Esszimmer", Icons.Rounded.Restaurant, Color(0xFFFFA726)),
    CAFE("Kaffee", Icons.Rounded.LocalCafe, Color(0xFF8D6E63)),
    LIVING_ROOM("Wohnzimmer", Icons.Rounded.Weekend, Color(0xFF26A69A)),
    TV("Fernsehen", Icons.Rounded.Tv, Color(0xFF42A5F5)),
    KIDS_ROOM("Kinderzimmer", Icons.Rounded.ChildCare, Color(0xFFEC407A)),
    TOYS("Spielzeug", Icons.Rounded.Toys, Color(0xFFAB47BC)),
    UNICORN("Einhorn", UnicornIcon, Color(0xFFB39DDB), multicolor = true),
    PIKACHU("Pikachu", PikachuIcon, Color(0xFF4FC3F7), multicolor = true),
    ROBOT("Roboter", Icons.Rounded.SmartToy, Color(0xFF7E57C2)),
    GAMES("Spielen", Icons.Rounded.SportsEsports, Color(0xFF5E35B1)),
    ROCKET("Rakete", Icons.Rounded.RocketLaunch, Color(0xFFEF5350)),
    STAR("Stern", Icons.Rounded.Star, Color(0xFFFFB300)),
    MOON("Gute Nacht", Icons.Rounded.NightsStay, Color(0xFF3949AB)),
    SUN("Sonne", Icons.Rounded.WbSunny, Color(0xFFFF9800)),
    BEDROOM("Schlafzimmer", Icons.Rounded.Bed, Color(0xFF7986CB)),
    BATHROOM("Bad", Icons.Rounded.Bathtub, Color(0xFF29B6F6)),
    GARDEN("Garten", Icons.Rounded.Yard, Color(0xFF66BB6A)),
    TREE("Baum", Icons.Rounded.Park, Color(0xFF43A047)),
    TERRACE("Terrasse", Icons.Rounded.Deck, Color(0xFFA1887F)),
    OFFICE("Büro", Icons.Rounded.Computer, Color(0xFF78909C)),
    PETS("Tiere", Icons.Rounded.Pets, Color(0xFFFF8A65)),
    MUSIC("Musik", Icons.Rounded.MusicNote, Color(0xFFF06292)),
    HEADPHONES("Kopfhörer", Icons.Rounded.Headphones, Color(0xFF26C6DA)),
    PIANO("Klavier", Icons.Rounded.Piano, Color(0xFF6D4C41)),
    PARTY("Party", Icons.Rounded.Celebration, Color(0xFFFF4081)),
    SMILE("Lachen", Icons.Rounded.EmojiEmotions, Color(0xFFFFC107)),
    CAKE("Kuchen", Icons.Rounded.Cake, Color(0xFFF48FB1)),
    ICE_CREAM("Eis", Icons.Rounded.Icecream, Color(0xFFBA68C8)),
    STORIES("Geschichten", Icons.Rounded.AutoStories, Color(0xFF8E24AA)),
    PAINTING("Malen", Icons.Rounded.Palette, Color(0xFF00ACC1)),
    HEART("Herz", Icons.Rounded.Favorite, Color(0xFFE53935)),
    HOME("Zuhause", Icons.Rounded.Home, Color(0xFF7CB342));

    companion object {
        fun fromKey(key: String?): SpeakerIcon = entries.firstOrNull { it.name == key } ?: SPEAKER

        /** Schlägt anhand des Sonos-Raumnamens ein passendes Start-Icon vor. */
        fun guessFor(roomName: String): SpeakerIcon {
            val name = roomName.lowercase()
            fun matches(vararg words: String) = words.any { it in name }
            return when {
                matches("küche", "kueche", "kitchen") -> KITCHEN
                matches("esszimmer", "dining") -> DINING
                matches("kind", "kids", "baby", "spiel") -> KIDS_ROOM
                matches("wohn", "living") -> LIVING_ROOM
                matches("schlaf", "bed") -> BEDROOM
                matches("bad", "bath") -> BATHROOM
                matches("garten", "garden") -> GARDEN
                matches("terrasse", "balkon", "terrace") -> TERRACE
                matches("büro", "buero", "office", "arbeit") -> OFFICE
                matches("tv", "fernseh", "kino") -> TV
                else -> SPEAKER
            }
        }
    }
}

/** Rundes, farbiges Icon-Badge — wird im Dropdown und in den Settings verwendet. */
@Composable
fun SpeakerIconBadge(icon: SpeakerIcon, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(icon.color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon.vector,
            contentDescription = icon.label,
            tint = if (icon.multicolor) Color.Unspecified else Color.White,
            modifier = Modifier.size(size * if (icon.multicolor) 0.78f else 0.58f)
        )
    }
}
