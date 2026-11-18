package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.GameMission
import com.example.model.LibraryGame
import com.example.model.LibrarySortOrder
import com.example.model.LiveEventItem
import com.example.model.NewsArticle
import com.example.model.UserNotification
import com.example.ui.components.EmptyVaultState
import com.example.ui.components.GameCoverImage
import com.example.ui.components.MissionCard
import com.example.ui.components.NewsArticleCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatRemainingTime
import com.example.ui.components.openExternalUrl
import com.example.viewmodel.UiState

@Composable
fun HomeDashboardScreen(
    displayName: String,
    libraryState: UiState<List<LibraryGame>>,
    sortOrder: LibrarySortOrder,
    missionsState: UiState<List<GameMission>>,
    newsState: UiState<List<NewsArticle>>,
    liveEventsState: UiState<List<LiveEventItem>>,
    notificationsState: UiState<List<UserNotification>>,
    onSortChanged: (LibrarySortOrder) -> Unit,
    onOpenGameDetail: (String) -> Unit,
    onToggleFavorite: (LibraryGame) -> Unit,
    onOpenExplorer: () -> Unit,
    onOpenMyGames: () -> Unit,
    onOpenMissions: () -> Unit,
    onOpenNews: () -> Unit,
    onIncrementMission: (GameMission) -> Unit,
    onToggleMission: (GameMission) -> Unit
) {
    val context = LocalContext.current
    val games = (libraryState as? UiState.Success)?.data.orEmpty()
    val missions = (missionsState as? UiState.Success)?.data.orEmpty()
    val pendingMissions = missions.filter { !it.completed }
    val unreadAlerts = (notificationsState as? UiState.Success)?.data?.count { !it.read } ?: 0
    val updateNeededGames = games.filter { it.needsUpdate }
    val endingSeasonsGames = games.filter {
        val end = it.seasonEndsAtMillis ?: 0L
        end > System.currentTimeMillis()
    }.sortedBy { it.seasonEndsAtMillis }

    val favoriteTitles = games.filter { it.favorite }.map { it.title.lowercase() }.toSet()
    val allNews = (newsState as? UiState.Success)?.data.orEmpty()
    val favoriteNews = if (favoriteTitles.isNotEmpty()) {
        allNews.filter { article -> favoriteTitles.any { article.gameTitle.lowercase().contains(it) || it.contains(article.gameTitle.lowercase()) } }
            .ifEmpty { allNews }
    } else {
        allNews
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Cyber Command Hero Banner
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                            )
                        ),
                        MaterialTheme.shapes.large
                    )
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_nemox_hero),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.35f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(168.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(168.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.45f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.dashboard_welcome).uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable { onOpenExplorer() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = stringResource(R.string.add_game),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatPill(
                                count = games.size.toString(),
                                label = stringResource(R.string.dashboard_games_count),
                                accent = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onOpenMyGames() }
                            )
                            StatPill(
                                count = pendingMissions.size.toString(),
                                label = stringResource(R.string.dashboard_missions_left),
                                accent = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onOpenMissions() }
                            )
                            StatPill(
                                count = unreadAlerts.toString(),
                                label = stringResource(R.string.dashboard_alerts_count),
                                accent = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 2. User Library + Sort Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(
                    title = stringResource(R.string.section_your_library),
                    actionLabel = stringResource(R.string.view_all),
                    onActionClick = onOpenMyGames
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = sortOrder == LibrarySortOrder.RECENTLY_UPDATED,
                            onClick = { onSortChanged(LibrarySortOrder.RECENTLY_UPDATED) },
                            label = { Text(stringResource(R.string.sort_recent_update)) },
                            modifier = Modifier.testTag("sort_recent")
                        )
                    }
                    item {
                        FilterChip(
                            selected = sortOrder == LibrarySortOrder.MOST_PLAYED,
                            onClick = { onSortChanged(LibrarySortOrder.MOST_PLAYED) },
                            label = { Text(stringResource(R.string.sort_most_played)) },
                            modifier = Modifier.testTag("sort_most_played")
                        )
                    }
                    item {
                        FilterChip(
                            selected = sortOrder == LibrarySortOrder.FAVORITES,
                            onClick = { onSortChanged(LibrarySortOrder.FAVORITES) },
                            label = { Text(stringResource(R.string.sort_favorites)) },
                            modifier = Modifier.testTag("sort_favorites")
                        )
                    }
                }

                if (games.isEmpty()) {
                    EmptyVaultState(
                        title = stringResource(R.string.empty_library_title),
                        description = stringResource(R.string.empty_library_desc),
                        actionText = stringResource(R.string.explorer_title),
                        onAction = onOpenExplorer
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(games, key = { it.id }) { game ->
                            DashboardGameCard(
                                game = game,
                                onClick = { onOpenGameDetail(game.id) },
                                onToggleFavorite = { onToggleFavorite(game) }
                            )
                        }
                    }
                }
            }
        }

        // 3. Games Needing Updates
        if (updateNeededGames.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(title = stringResource(R.string.section_needs_update))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(updateNeededGames, key = { "upd_${it.id}" }) { game ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .width(230.dp)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f),
                                        MaterialTheme.shapes.medium
                                    )
                                    .clickable { onOpenGameDetail(game.id) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SystemUpdate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = game.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = game.updateNotes ?: stringResource(R.string.badge_needs_update),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Ending Soon Seasons & Live Events
        item {
            val liveEvents = (liveEventsState as? UiState.Success)?.data.orEmpty()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(title = stringResource(R.string.section_ending_soon))

                if (endingSeasonsGames.isNotEmpty()) {
                    endingSeasonsGames.take(3).forEach { game ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
                                .clickable { onOpenGameDetail(game.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(game.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = game.currentSeasonName ?: "Active Season",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                StatusBadge(
                                    text = formatRemainingTime(game.seasonEndsAtMillis ?: 0L),
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }

                if (liveEvents.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(liveEvents.take(6), key = { it.id }) { ev ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .width(260.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), MaterialTheme.shapes.medium)
                                    .clickable { openExternalUrl(context, ev.openUrl) }
                            ) {
                                Column {
                                    GameCoverImage(
                                        url = ev.imageUrl,
                                        title = ev.title,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                    )
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Bolt,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = ev.type,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = ev.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Rounded.Schedule,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = ev.endDateText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Incomplete Daily & Weekly Missions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(
                    title = stringResource(R.string.section_active_missions),
                    actionLabel = stringResource(R.string.view_all),
                    onActionClick = onOpenMissions
                )
                if (pendingMissions.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenMissions() }
                    ) {
                        Text(
                            text = stringResource(R.string.empty_missions_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    pendingMissions.take(3).forEach { mission ->
                        MissionCard(
                            mission = mission,
                            onIncrement = { onIncrementMission(mission) },
                            onToggleComplete = { onToggleMission(mission) }
                        )
                    }
                }
            }
        }

        // 6. Favorite Games News
        item {
            SectionHeader(
                title = stringResource(R.string.section_favorite_news),
                actionLabel = stringResource(R.string.view_all),
                onActionClick = onOpenNews
            )
        }

        when (newsState) {
            is UiState.Loading -> item {
                Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> item {
                Text(
                    text = newsState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            is UiState.Success -> {
                items(favoriteNews.take(4), key = { "dash_${it.id}" }) { article ->
                    NewsArticleCard(article = article)
                }
            }
        }
    }
}

@Composable
private fun StatPill(
    count: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DashboardGameCard(
    game: LibraryGame,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .width(190.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("dashboard_game_${game.id}")
    ) {
        Column {
            Box {
                GameCoverImage(
                    url = game.coverUrl ?: game.iconUrl,
                    title = game.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(106.dp)
                )
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = if (game.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = game.title,
                        tint = if (game.favorite) MaterialTheme.colorScheme.error else Color.White
                    )
                }
                if (game.needsUpdate) {
                    StatusBadge(
                        text = "UPDATE",
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${game.playHours}h • Lv.${game.playerLevel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
