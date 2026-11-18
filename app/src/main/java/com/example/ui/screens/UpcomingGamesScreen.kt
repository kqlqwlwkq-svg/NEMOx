package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.CatalogGameItem
import com.example.model.WaitlistGame
import com.example.ui.components.GameCoverImage
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.components.openExternalUrl
import com.example.viewmodel.UiState

@Composable
fun UpcomingGamesScreen(
    upcomingState: UiState<List<CatalogGameItem>>,
    waitlistState: UiState<List<WaitlistGame>>,
    onOpenExplorerForUpcoming: (String) -> Unit,
    onAddToWaitlist: (CatalogGameItem) -> Unit,
    onToggleWaitlistAlerts: (WaitlistGame, Boolean, Boolean) -> Unit,
    onRemoveFromWaitlist: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    var searchFilter by remember { mutableStateOf("") }
    val waitlist = (waitlistState as? UiState.Success)?.data.orEmpty()
    val waitlistExternalIds = waitlist.map { it.externalId }.toSet()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            OutlinedTextField(
                value = searchFilter,
                onValueChange = { searchFilter = it },
                label = { Text(stringResource(R.string.search_games)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchFilter.isNotBlank()) {
                        OutlinedButton(
                            onClick = { onOpenExplorerForUpcoming(searchFilter) },
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text("Explore")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("upcoming_search_input")
            )
        }

        // User Waitlist
        if (waitlist.isNotEmpty()) {
            item {
                SectionHeader(title = "${stringResource(R.string.in_waitlist)} (${waitlist.size})")
            }
            items(waitlist, key = { "wl_${it.id}" }) { item ->
                if (searchFilter.isBlank() || item.title.contains(searchFilter, ignoreCase = true)) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                MaterialTheme.shapes.medium
                            )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GameCoverImage(
                                    url = item.coverUrl,
                                    title = item.title,
                                    modifier = Modifier.width(88.dp).height(58.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = "${item.releaseDateText} • ${item.platforms.joinToString(", ")}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                IconButton(onClick = { onRemoveFromWaitlist(item.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.delete))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Before Release", style = MaterialTheme.typography.labelMedium)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Switch(
                                        checked = item.notifyBeforeRelease,
                                        onCheckedChange = { onToggleWaitlistAlerts(item, it, item.notifyOnRelease) }
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("On Launch", style = MaterialTheme.typography.labelMedium)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Switch(
                                        checked = item.notifyOnRelease,
                                        onCheckedChange = { onToggleWaitlistAlerts(item, item.notifyBeforeRelease, it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Live Steam Coming Soon Releases
        item {
            SectionHeader(
                title = stringResource(R.string.nav_upcoming),
                actionLabel = stringResource(R.string.refresh),
                onActionClick = onRefresh
            )
        }

        when (upcomingState) {
            is UiState.Loading -> item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(upcomingState.message, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
                }
            }
            is UiState.Success -> {
                val filtered = upcomingState.data.filter {
                    searchFilter.isBlank() || it.title.contains(searchFilter, ignoreCase = true) ||
                        it.genres.any { g -> g.contains(searchFilter, ignoreCase = true) }
                }
                items(filtered, key = { it.externalId }) { game ->
                    val isWaitlisted = waitlistExternalIds.contains(game.externalId)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
                            .clickable { onOpenExplorerForUpcoming(game.title) }
                    ) {
                        Column {
                            GameCoverImage(
                                url = game.coverUrl,
                                title = game.title,
                                modifier = Modifier.fillMaxWidth().height(170.dp)
                            )
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusBadge(
                                        text = game.releaseDateText,
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = game.platforms.joinToString(" • "),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(game.title, style = MaterialTheme.typography.titleLarge)
                                if (game.genres.isNotEmpty()) {
                                    Text(
                                        text = game.genres.joinToString(" • "),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (game.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = game.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { if (!isWaitlisted) onAddToWaitlist(game) },
                                        enabled = !isWaitlisted,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (isWaitlisted) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                                            contentDescription = null
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if (isWaitlisted) stringResource(R.string.in_waitlist)
                                            else stringResource(R.string.add_to_waitlist)
                                        )
                                    }
                                    if (!game.trailerUrl.isNullOrBlank()) {
                                        OutlinedButton(onClick = { openExternalUrl(context, game.trailerUrl) }) {
                                            Icon(Icons.Rounded.PlayCircleOutline, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.watch_trailer))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
