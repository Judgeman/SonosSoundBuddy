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
