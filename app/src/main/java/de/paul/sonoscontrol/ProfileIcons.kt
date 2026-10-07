package de.paul.sonoscontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Icons für die Kinder-Profile: Tiergesichter (AnimalIcons.kt) sowie Einhorn
 * und Pikachu (CharacterIcons.kt). Der Enum-Name wird als Schlüssel in der
 * Datenbank gespeichert — Einträge also nicht umbenennen.
 */
enum class ProfileIcon(val label: String, private val vectorProvider: () -> ImageVector, val color: Color) {
    CAT("Katze", { CatIcon }, Color(0xFFB3E5FC)),
    DOG("Hund", { DogIcon }, Color(0xFFC8E6C9)),
    BEAR("Bär", { BearIcon }, Color(0xFFFFE0B2)),
    PANDA("Panda", { PandaIcon }, Color(0xFFA5D6A7)),
    FOX("Fuchs", { FoxIcon }, Color(0xFFB2EBF2)),
    FROG("Frosch", { FrogIcon }, Color(0xFFFFF59D)),
    LION("Löwe", { LionIcon }, Color(0xFFE1BEE7)),
    PIG("Schwein", { PigIcon }, Color(0xFFB2DFDB)),
    MOUSE("Maus", { MouseIcon }, Color(0xFFFFCCBC)),
    BUNNY("Hase", { BunnyIcon }, Color(0xFF90CAF9)),
    OWL("Eule", { OwlIcon }, Color(0xFFC5E1A5)),
    PENGUIN("Pinguin", { PenguinIcon }, Color(0xFF81D4FA)),
    MONKEY("Affe", { MonkeyIcon }, Color(0xFFFFF176)),
    KOALA("Koala", { KoalaIcon }, Color(0xFFF8BBD0)),
    CHICK("Küken", { ChickIcon }, Color(0xFF80CBC4)),
    LADYBUG("Marienkäfer", { LadybugIcon }, Color(0xFFDCEDC8)),
    COW("Kuh", { CowIcon }, Color(0xFFAED581)),
    SAM("Sam", { SamIcon }, Color(0xFF9FA8DA)),
    UNICORN("Einhorn", { UnicornIcon }, Color(0xFFB39DDB)),
    PIKACHU("Pikachu", { PikachuIcon }, Color(0xFF4FC3F7));

    val vector: ImageVector get() = vectorProvider()

    companion object {
        fun fromKey(key: String?): ProfileIcon = entries.firstOrNull { it.name == key } ?: CAT

        /** Für ein neues Profil ein Icon vorschlagen, das noch kein anderes Profil hat. */
        fun suggestion(used: Collection<ProfileIcon>): ProfileIcon =
            entries.firstOrNull { it !in used } ?: CAT
    }
}

/** Rundes Badge mit dem Profil-Icon — im Profil-Dropdown, Musik-Popup und in den Settings. */
@Composable
fun ProfileIconBadge(icon: ProfileIcon, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(icon.color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon.vector,
            contentDescription = icon.label,
            tint = Color.Unspecified,
            modifier = Modifier.size(size * 0.82f)
        )
    }
}
