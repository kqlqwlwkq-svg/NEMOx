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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.LibraryGame
import com.example.model.LibrarySortOrder
import com.example.ui.components.AddManualGameDialog
import com.example.ui.components.EmptyVaultState
import com.example.ui.components.GameCoverImage
import com.example.ui.components.StatusBadge
import com.example.viewmodel.UiState

@Composable
fun MyGamesScreen(
    libraryState: UiState<List<LibraryGame>>,
    sortOrder: LibrarySortOrder,
    onSortChanged: (LibrarySortOrder) -> Unit,
    onOpenGameDetail: (String) -> Unit,
    onToggleFavorite: (LibraryGame) -> Unit,
    onOpenExplorer: () -> Unit,
    onAddManualGame: (String, String, String, String?, String?, Int?) -> Unit
) {
    var showAddMenu by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (libraryState) {
            is UiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is UiState.Error -> {
                Text(
                    text = libraryState.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(20.dp)
                )
            }
            is UiState.Success -> {
                val games = libraryState.data
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = sortOrder == LibrarySortOrder.RECENTLY_UPDATED,
                                    onClick = { onSortChanged(LibrarySortOrder.RECENTLY_UPDATED) },
                                    label = { Text(stringResource(R.string.sort_recent_update)) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = sortOrder == LibrarySortOrder.MOST_PLAYED,
                                    onClick = { onSortChanged(LibrarySortOrder.MOST_PLAYED) },
                                    label = { Text(stringResource(R.string.sort_most_played)) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = sortOrder == LibrarySortOrder.FAVORITES,
                                    onClick = { onSortChanged(LibrarySortOrder.FAVORITES) },
                                    label = { Text(stringResource(R.string.sort_favorites)) }
                                )
                            }
                        }
                    }

                    if (games.isEmpty()) {
                        item {
                            EmptyVaultState(
                                title = stringResource(R.string.empty_library_title),
                                description = stringResource(R.string.empty_library_desc),
                                actionText = stringResource(R.string.explorer_title),
                                onAction = onOpenExplorer
                            )
                        }
                    } else {
                        items(games, key = { it.id }) { game ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        MaterialTheme.shapes.medium
                                    )
                                    .clickable { onOpenGameDetail(game.id) }
                                    .testTag("library_card_${game.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    GameCoverImage(
                                        url = game.coverUrl ?: game.iconUrl,
                                        title = game.title,
                                        modifier = Modifier
                                            .size(width = 96.dp, height = 68.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = game.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = game.platforms.joinToString(" • ").ifBlank { "PC" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            StatusBadge(
                                                text = "Lv.${game.playerLevel} • ${game.playHours}h",
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            if (game.needsUpdate) {
                                                StatusBadge(
                                                    text = stringResource(R.string.badge_needs_update),
                                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                                )
                                            }
                                        }
                                    }
                                    IconButton(onClick = { onToggleFavorite(game) }) {
                                        Icon(
                                            imageVector = if (game.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                            contentDescription = game.title,
                                            tint = if (game.favorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            FloatingActionButton(
                onClick = { showAddMenu = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_game_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_game))
            }
            DropdownMenu(
                expanded = showAddMenu,
                onDismissRequest = { showAddMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.explorer_title)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    onClick = {
                        showAddMenu = false
                        onOpenExplorer()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.add_manual_game)) },
                    leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    onClick = {
                        showAddMenu = false
                        showManualDialog = true
                    }
                )
            }
        }
    }

    if (showManualDialog) {
        AddManualGameDialog(
            onDismiss = { showManualDialog = false },
            onSave = onAddManualGame
        )
    }
}
