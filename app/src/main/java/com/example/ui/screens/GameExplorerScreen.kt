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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.CatalogGameItem
import com.example.model.GameReleaseStatus
import com.example.model.LibraryGame
import com.example.model.NewsArticle
import com.example.model.WaitlistGame
import com.example.ui.components.GameCoverImage
import com.example.ui.components.NewsArticleCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.openExternalUrl
import com.example.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameExplorerScreen(
    query: String,
    explorerState: UiState<List<CatalogGameItem>>,
    selectedPreview: CatalogGameItem?,
    selectedGameNews: List<NewsArticle>,
    libraryGames: List<LibraryGame>,
    waitlistGames: List<WaitlistGame>,
    onSearch: (String) -> Unit,
    onSelectGame: (CatalogGameItem) -> Unit,
    onDismissPreview: () -> Unit,
    onAddToLibrary: (CatalogGameItem) -> Unit,
    onAddToWaitlist: (CatalogGameItem) -> Unit
) {
    val context = LocalContext.current
    var localQuery by remember(query) { mutableStateOf(query) }
    val libraryIds = libraryGames.map { it.externalId }.toSet()
    val waitlistIds = waitlistGames.map { it.externalId }.toSet()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = localQuery,
                onValueChange = { localQuery = it },
                placeholder = { Text(stringResource(R.string.search_games)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (localQuery.isNotBlank()) {
                        IconButton(onClick = {
                            localQuery = ""
                            onSearch("")
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(localQuery) }),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("explorer_search_field")
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onSearch(localQuery) },
                modifier = Modifier.height(54.dp).testTag("explorer_search_submit")
            ) {
                Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.explorer_title))
            }
        }

        when (explorerState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(explorerState.message, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(onClick = { onSearch(localQuery) }) { Text(stringResource(R.string.retry)) }
                }
            }
            is UiState.Success -> {
                val results = explorerState.data
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(results, key = { it.externalId }) { game ->
                        val inLib = libraryIds.contains(game.externalId)
                        val inWait = waitlistIds.contains(game.externalId)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
                                .clickable { onSelectGame(game) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GameCoverImage(
                                    url = game.coverUrl ?: game.iconUrl,
                                    title = game.title,
                                    modifier = Modifier.size(width = 100.dp, height = 68.dp).clip(RoundedCornerShape(10.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(game.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = "${game.platforms.joinToString(", ")} • ${game.releaseDateText}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    ReleaseStatusBadge(game.status)
                                }
                                IconButton(
                                    onClick = { if (!inLib) onAddToLibrary(game) }
                                ) {
                                    Icon(
                                        imageVector = if (inLib) Icons.Rounded.Check else Icons.Rounded.Add,
                                        contentDescription = stringResource(R.string.add_to_library),
                                        tint = if (inLib) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                    )
                                }
                                if (game.status == GameReleaseStatus.UPCOMING || inWait) {
                                    IconButton(onClick = { if (!inWait) onAddToWaitlist(game) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.BookmarkAdd,
                                            contentDescription = stringResource(R.string.add_to_waitlist),
                                            tint = if (inWait) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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

    if (selectedPreview != null) {
        val inLib = libraryIds.contains(selectedPreview.externalId)
        val inWait = waitlistIds.contains(selectedPreview.externalId)
        ModalBottomSheet(onDismissRequest = onDismissPreview) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GameCoverImage(
                    url = selectedPreview.coverUrl,
                    title = selectedPreview.title,
                    modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp))
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(selectedPreview.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    ReleaseStatusBadge(selectedPreview.status)
                }
                Text(
                    text = "Release: ${selectedPreview.releaseDateText} • ${selectedPreview.platforms.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (!selectedPreview.developer.isNullOrBlank()) {
                    Text(
                        text = "Dev: ${selectedPreview.developer} • Pub: ${selectedPreview.publisher ?: "-"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selectedPreview.description.isNotBlank()) {
                    Text(selectedPreview.description, style = MaterialTheme.typography.bodyLarge)
                }

                if (selectedPreview.screenshots.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(selectedPreview.screenshots) { shot ->
                            GameCoverImage(
                                url = shot,
                                title = selectedPreview.title,
                                modifier = Modifier.size(width = 180.dp, height = 100.dp).clip(RoundedCornerShape(10.dp))
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onAddToLibrary(selectedPreview) },
                        enabled = !inLib,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (inLib) stringResource(R.string.in_library) else stringResource(R.string.add_to_library))
                    }
                    OutlinedButton(
                        onClick = { onAddToWaitlist(selectedPreview) },
                        enabled = !inWait,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (inWait) stringResource(R.string.in_waitlist) else stringResource(R.string.add_to_waitlist))
                    }
                }

                if (!selectedPreview.trailerUrl.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { openExternalUrl(context, selectedPreview.trailerUrl) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.PlayCircleOutline, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.watch_trailer))
                    }
                }

                if (selectedGameNews.isNotEmpty()) {
                    Text(stringResource(R.string.detail_latest_news), style = MaterialTheme.typography.titleMedium)
                    selectedGameNews.take(3).forEach { article ->
                        NewsArticleCard(article = article)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ReleaseStatusBadge(status: GameReleaseStatus) {
    val (labelRes, bg, fg) = when (status) {
        GameReleaseStatus.AVAILABLE -> Triple(
            R.string.status_available,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        GameReleaseStatus.UPCOMING -> Triple(
            R.string.status_upcoming,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        GameReleaseStatus.DELAYED -> Triple(
            R.string.status_delayed,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        GameReleaseStatus.CANCELLED -> Triple(
            R.string.status_cancelled,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }
    StatusBadge(text = stringResource(labelRes), containerColor = bg, contentColor = fg)
}
