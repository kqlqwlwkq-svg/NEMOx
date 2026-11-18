package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface NemoxRoute {
    // 5 Primary Bottom Navigation Tabs
    @Serializable data object Home : NemoxRoute
    @Serializable data object Discover : NemoxRoute
    @Serializable data object Search : NemoxRoute
    @Serializable data object MyGames : NemoxRoute
    @Serializable data object Profile : NemoxRoute

    // Secondary / Feature & Detail routes (all existing screens preserved + Trending)
    @Serializable data object Trending : NemoxRoute
    @Serializable data class News(val filterGameTitle: String? = null) : NemoxRoute
    @Serializable data object Upcoming : NemoxRoute
    @Serializable data object Explorer : NemoxRoute
    @Serializable data class GameDetail(val gameDocId: String) : NemoxRoute
    @Serializable data object Missions : NemoxRoute
    @Serializable data object Accounts : NemoxRoute
    @Serializable data object Notifications : NemoxRoute
}
