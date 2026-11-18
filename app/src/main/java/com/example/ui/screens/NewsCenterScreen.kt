package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.LibraryGame
import com.example.model.NewsArticle
import com.example.ui.components.EmptyVaultState
import com.example.ui.components.NewsArticleCard
import com.example.viewmodel.UiState

@Composable
fun NewsCenterScreen(
    newsState: UiState<List<NewsArticle>>,
    libraryGames: List<LibraryGame>,
    selectedGameFilter: String?,
    officialOnly: Boolean,
    onSelectGameFilter: (String?) -> Unit,
    onToggleOfficialOnly: (Boolean) -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.nav_news),
                style = MaterialTheme.typography.headlineMedium
            )
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.testTag("news_refresh_button")
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.refresh))
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = officialOnly,
                    onClick = { onToggleOfficialOnly(!officialOnly) },
                    leadingIcon = { Icon(Icons.Rounded.Verified, contentDescription = null) },
                    label = { Text(stringResource(R.string.filter_official)) },
                    modifier = Modifier.testTag("news_official_chip")
                )
            }
            item {
                FilterChip(
                    selected = selectedGameFilter == null,
                    onClick = { onSelectGameFilter(null) },
                    label = { Text(stringResource(R.string.filter_all)) }
                )
            }
            items(libraryGames, key = { "nf_${it.id}" }) { game ->
                FilterChip(
                    selected = selectedGameFilter == game.title,
                    onClick = {
                        onSelectGameFilter(if (selectedGameFilter == game.title) null else game.title)
                    },
                    label = { Text(game.title) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (newsState) {
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
                    Text(newsState.message, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
                }
            }
            is UiState.Success -> {
                val filtered = newsState.data.filter { article ->
                    val matchOfficial = !officialOnly || article.isOfficial
                    val matchGame = selectedGameFilter == null ||
                        article.gameTitle.contains(selectedGameFilter, ignoreCase = true) ||
                        selectedGameFilter.contains(article.gameTitle, ignoreCase = true)
                    matchOfficial && matchGame
                }

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        EmptyVaultState(
                            title = stringResource(R.string.empty_news_title),
                            description = stringResource(R.string.empty_news_desc),
                            actionText = stringResource(R.string.refresh),
                            onAction = onRefresh
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(filtered, key = { it.id }) { article ->
                            NewsArticleCard(article = article)
                        }
                    }
                }
            }
        }
    }
}
