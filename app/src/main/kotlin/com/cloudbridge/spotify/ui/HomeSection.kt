package com.cloudbridge.spotify.ui

enum class HomeSection(
    val storageKey: String,
    val title: String
) {
    JumpBackIn("jump_back_in", "Jump Back In"),
    Podcasts("podcasts", "Your Podcasts"),
    CustomMixes("custom_mixes", "Your Custom Mixes"),
    PinnedFavorites("pinned_favorites", "Pinned Favorites"),
    SuggestedForYou("suggested_for_you", "Suggested For You"),
    NewReleases("new_releases", "New Releases");

    companion object {
        val defaultOrder: List<HomeSection> = listOf(
            JumpBackIn,
            Podcasts,
            CustomMixes,
            PinnedFavorites,
            SuggestedForYou,
            NewReleases
        )

        fun fromStorageKeys(keys: List<String>): List<HomeSection> {
            val resolved = keys.mapNotNull { key ->
                entries.firstOrNull { it.storageKey == key.trim() }
            }
            val seen = linkedSetOf<HomeSection>()
            resolved.forEach { seen += it }
            defaultOrder.forEach { seen += it }
            return seen.toList()
        }
    }
}
