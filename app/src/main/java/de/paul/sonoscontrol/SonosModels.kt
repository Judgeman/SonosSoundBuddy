package de.paul.sonoscontrol

import kotlinx.serialization.Serializable

@Serializable
data class Household(
    val id: String,
    val name: String? = null
)

@Serializable
data class HouseholdsResponse(
    val households: List<Household> = emptyList()
)

@Serializable
data class SonosPlayer(
    val id: String,
    val name: String,
    val icon: String? = null,
    val capabilities: List<String>? = null
)

@Serializable
data class SonosGroup(
    val id: String,
    val name: String,
    val coordinatorId: String,
    val playbackState: String? = null,
    val playerIds: List<String> = emptyList()
)

/** Antwort von GET /households/{householdId}/groups — enthält Groups UND Players. */
@Serializable
data class GroupsResponse(
    val groups: List<SonosGroup> = emptyList(),
    val players: List<SonosPlayer> = emptyList()
)

/** Antwort von GET /groups/{groupId}/playback */
@Serializable
data class PlaybackStatus(
    val playbackState: String? = null,
    val positionMillis: Long = 0,
    val availablePlaybackActions: PlaybackActions? = null
) {
    val isPlaying: Boolean get() = playbackState == STATE_PLAYING
    val isBuffering: Boolean get() = playbackState == STATE_BUFFERING
    val isPaused: Boolean get() = playbackState == STATE_PAUSED

    companion object {
        const val STATE_PLAYING = "PLAYBACK_STATE_PLAYING"
        const val STATE_BUFFERING = "PLAYBACK_STATE_BUFFERING"
        const val STATE_PAUSED = "PLAYBACK_STATE_PAUSED"
    }
}

/** Welche Steuerbefehle die aktuelle Quelle erlaubt (z. B. kein Skip bei Radio). */
@Serializable
data class PlaybackActions(
    val canSkip: Boolean = true,
    val canSkipBack: Boolean = true,
    val canPlay: Boolean = true,
    val canPause: Boolean = true
)

/** Antwort von GET /groups/{groupId}/playbackMetadata */
@Serializable
data class PlaybackMetadata(
    val container: MetadataContainer? = null,
    val currentItem: MetadataItem? = null,
    val streamInfo: String? = null
)

@Serializable
data class MetadataContainer(
    val name: String? = null,
    val type: String? = null,
    val imageUrl: String? = null
)

@Serializable
data class MetadataItem(
    val track: Track? = null
)

@Serializable
data class Track(
    val name: String? = null,
    val imageUrl: String? = null,
    val durationMillis: Long? = null,
    val artist: NamedItem? = null,
    val album: NamedItem? = null
)

@Serializable
data class NamedItem(
    val name: String? = null
)

/** Antwort von GET /players/{playerId}/playerVolume */
@Serializable
data class PlayerVolume(
    val volume: Int = 0,
    val muted: Boolean = false,
    val fixed: Boolean = false
)
