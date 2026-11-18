package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface NemoxRoute {
    @Serializable data object Home : NemoxRoute
    @Serializable data object MyGames : NemoxRoute
    @Serializable data class News(val filterGameTitle: String? = null) : NemoxRoute
    @Serializable data object Upcoming : NemoxRoute
    @Serializable data object Profile : NemoxRoute

    // Secondary / Detail routes
    @Serializable data object Explorer : NemoxRoute
    @Serializable data class GameDetail(val gameDocId: String) : NemoxRoute
    @Serializable data object Missions : NemoxRoute
    @Serializable data object Accounts : NemoxRoute
    @Serializable data object Notifications : NemoxRoute
}
