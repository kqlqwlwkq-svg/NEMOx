package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.GameMission
import com.example.model.LibraryGame
import com.example.model.MissionCategory
import com.example.model.NewsArticle
import com.example.ui.components.AddMissionDialog
import com.example.ui.components.GameCoverImage
import com.example.ui.components.MissionCard
import com.example.ui.components.NewsArticleCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatRemainingTime
import com.example.ui.components.openExternalUrl

@Composable
fun GameDetailScreen(
    game: LibraryGame?,
    gameNews: List<NewsArticle>,
    missions: List<GameMission>,
    onLoadNews: (LibraryGame) -> Unit,
    onToggleFavorite: (LibraryGame) -> Unit,
    onToggleNeedsUpdate: (LibraryGame) -> Unit,
    onUpdateStatsAndSeason: (LibraryGame, Long, Long, Long, Long, String, String, Int) -> Unit,
    onUpdateNotificationPrefs: (LibraryGame, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onAddMission: (String?, String, String, String?, MissionCategory, Long, Long, String?) -> Unit,
    onIncrementMission: (GameMission) -> Unit,
    onToggleMission: (GameMission) -> Unit,
    onDeleteMission: (String) -> Unit,
    onRemoveGame: (String) -> Unit,
    onBack: () -> Unit
) {
    if (game == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Game not found", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onBack) { Text(stringResource(R.string.close)) }
        }
        return
    }

    val context = LocalContext.current
    var showEditStats by remember { mutableStateOf(false) }
    var showAddMission by remember { mutableStateOf(false) }

    LaunchedEffect(game.id) {
        onLoadNews(game)
    }

    val gameMissions = missions.filter {
        it.gameDocId == game.id || it.gameName.equals(game.title, ignoreCase = true)
    }
    val remainingMissions = gameMissions.filter { !it.completed }
    val completedMissions = gameMissions.filter { it.completed }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Cover & Title
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    GameCoverImage(
                        url = game.coverUrl ?: game.iconUrl,
                        title = game.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                    )
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(game.title, style = MaterialTheme.typography.headlineMedium)
                                Text(
                                    text = (game.platforms + game.genres).distinct().joinToString(" • "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                IconButton(onClick = { onToggleFavorite(game) }) {
                                    Icon(
                                        imageVector = if (game.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (game.favorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onToggleNeedsUpdate(game) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.SystemUpdate,
                                        contentDescription = "Needs Update",
                                        tint = if (game.needsUpdate) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    onRemoveGame(game.id)
                                    onBack()
                                }) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Player Stats & Achievements
        item {
            SectionHeader(
                title = stringResource(R.string.detail_player_stats),
                actionLabel = stringResource(R.string.edit),
                onActionClick = { showEditStats = true }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${game.playerLevel}", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Level", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${game.playHours}h", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.secondary)
                        Text("Playtime", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${game.achievementsUnlocked}/${game.achievementsTotal}", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.tertiary)
                        Text("Achievements", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Updates & Seasons
        item {
            SectionHeader(
                title = stringResource(R.string.detail_updates_seasons),
                actionLabel = stringResource(R.string.edit),
                onActionClick = { showEditStats = true }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(
                            text = if (game.needsUpdate) stringResource(R.string.badge_needs_update) else "Up to Date",
                            containerColor = if (game.needsUpdate) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (game.needsUpdate) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (!game.nextUpdateDateText.isNullOrBlank()) {
                            Text("Next Update: ${game.nextUpdateDateText}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (!game.currentSeasonName.isNullOrBlank()) {
                        val rem = game.seasonEndsAtMillis?.let { formatRemainingTime(it) } ?: ""
                        Text(
                            text = "Season: ${game.currentSeasonName}" + if (rem.isNotBlank()) " ($rem)" else "",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        // Official Website & Community Links
        if (!game.officialUrl.isNullOrBlank() || !game.communityUrl.isNullOrBlank()) {
            item {
                SectionHeader(title = stringResource(R.string.detail_links))
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!game.officialUrl.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { openExternalUrl(context, game.officialUrl) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.official_site))
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                        }
                    }
                    if (!game.communityUrl.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { openExternalUrl(context, game.communityUrl) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.community_hub))
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                        }
                    }
                }
            }
        }

        // Missions (Remaining & Completed)
        item {
            SectionHeader(
                title = "${stringResource(R.string.detail_missions)} (${remainingMissions.size} / ${completedMissions.size})",
                actionLabel = stringResource(R.string.add_mission),
                onActionClick = { showAddMission = true }
            )
        }
        if (remainingMissions.isNotEmpty()) {
            item { Text(stringResource(R.string.remaining_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary) }
            items(remainingMissions, key = { "rem_${it.id}" }) { m ->
                MissionCard(
                    mission = m,
                    onIncrement = { onIncrementMission(m) },
                    onToggleComplete = { onToggleMission(m) },
                    onDelete = { onDeleteMission(m.id) }
                )
            }
        }
        if (completedMissions.isNotEmpty()) {
            item { Text(stringResource(R.string.completed_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            items(completedMissions, key = { "done_${it.id}" }) { m ->
                MissionCard(
                    mission = m,
                    onIncrement = { onIncrementMission(m) },
                    onToggleComplete = { onToggleMission(m) },
                    onDelete = { onDeleteMission(m.id) }
                )
            }
        }

        // Per-Game Notification Settings
        item {
            SectionHeader(title = stringResource(R.string.detail_notifications))
            Spacer(modifier = Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    GameNotifRow(stringResource(R.string.notif_updates), game.notifyUpdates) {
                        onUpdateNotificationPrefs(game, it, game.notifySeasons, game.notifyMissions, game.notifyNews)
                    }
                    GameNotifRow(stringResource(R.string.notif_seasons), game.notifySeasons) {
                        onUpdateNotificationPrefs(game, game.notifyUpdates, it, game.notifyMissions, game.notifyNews)
                    }
                    GameNotifRow(stringResource(R.string.notif_missions), game.notifyMissions) {
                        onUpdateNotificationPrefs(game, game.notifyUpdates, game.notifySeasons, it, game.notifyNews)
                    }
                    GameNotifRow(stringResource(R.string.notif_news), game.notifyNews) {
                        onUpdateNotificationPrefs(game, game.notifyUpdates, game.notifySeasons, game.notifyMissions, it)
                    }
                }
            }
        }

        // Latest News & Patch Notes
        item {
            SectionHeader(title = stringResource(R.string.detail_latest_news))
        }
        if (gameNews.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.empty_news_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(gameNews, key = { "gd_${it.id}" }) { article ->
                NewsArticleCard(article = article)
            }
        }
    }

    if (showAddMission) {
        AddMissionDialog(
            libraryGames = listOf(game),
            preselectedGame = game,
            onDismiss = { showAddMission = false },
            onSave = onAddMission
        )
    }

    if (showEditStats) {
        EditGameStatsDialog(
            game = game,
            onDismiss = { showEditStats = false },
            onSave = { hrs, lvl, achU, achT, nextUpd, season, days ->
                onUpdateStatsAndSeason(game, hrs, lvl, achU, achT, nextUpd, season, days)
            }
        )
    }
}

@Composable
private fun GameNotifRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun EditGameStatsDialog(
    game: LibraryGame,
    onDismiss: () -> Unit,
    onSave: (Long, Long, Long, Long, String, String, Int) -> Unit
) {
    var hours by remember { mutableStateOf(game.playHours.toString()) }
    var level by remember { mutableStateOf(game.playerLevel.toString()) }
    var achUnlocked by remember { mutableStateOf(game.achievementsUnlocked.toString()) }
    var achTotal by remember { mutableStateOf(game.achievementsTotal.toString()) }
    var nextUpdate by remember { mutableStateOf(game.nextUpdateDateText.orEmpty()) }
    var seasonName by remember { mutableStateOf(game.currentSeasonName.orEmpty()) }
    var seasonDays by remember { mutableStateOf("14") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${game.title} — ${stringResource(R.string.edit)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = level,
                        onValueChange = { level = it.filter { c -> c.isDigit() } },
                        label = { Text("Level") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it.filter { c -> c.isDigit() } },
                        label = { Text("Hours") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = achUnlocked,
                        onValueChange = { achUnlocked = it.filter { c -> c.isDigit() } },
                        label = { Text("Ach. Done") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = achTotal,
                        onValueChange = { achTotal = it.filter { c -> c.isDigit() } },
                        label = { Text("Ach. Total") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = nextUpdate,
                    onValueChange = { nextUpdate = it },
                    label = { Text("Next Update Date (if announced)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = seasonName,
                        onValueChange = { seasonName = it },
                        label = { Text("Season Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1.3f)
                    )
                    OutlinedTextField(
                        value = seasonDays,
                        onValueChange = { seasonDays = it.filter { c -> c.isDigit() } },
                        label = { Text("Days") },
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    hours.toLongOrNull() ?: 0L,
                    level.toLongOrNull() ?: 1L,
                    achUnlocked.toLongOrNull() ?: 0L,
                    achTotal.toLongOrNull() ?: 0L,
                    nextUpdate,
                    seasonName,
                    seasonDays.toIntOrNull() ?: 0
                )
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
