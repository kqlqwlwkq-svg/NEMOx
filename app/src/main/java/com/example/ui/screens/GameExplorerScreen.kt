package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.model.GameCatalogPresets
import com.example.model.GameReleaseStatus
import com.example.model.LibraryGame
import com.example.model.NewsArticle
import com.example.model.SearchFilterState
import com.example.model.SearchPricingFilter
import com.example.model.SearchReleaseFilter
import com.example.model.WaitlistGame
import com.example.ui.components.EmptyVaultState
import com.example.ui.components.GameCoverImage
import com.example.ui.components.NewsArticleCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.openExternalUrl
import com.example.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GameExplorerScreen(
    isArabic: Boolean,
    query: String,
    explorerState: UiState<List<CatalogGameItem>>,
    filters: SearchFilterState,
    recentSearches: List<String>,
    suggestionsProvider: (String) -> List<String>,
    libraryGames: List<LibraryGame>,
    waitlistGames: List<WaitlistGame>,
    onSearch: (String) -> Unit,
    onUpdateFilters: (SearchFilterState) -> Unit,
    onResetFilters: () -> Unit,
    onRemoveRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onSelectGame: (CatalogGameItem) -> Unit,
    onAddToLibrary: (CatalogGameItem) -> Unit,
    onAddToWaitlist: (CatalogGameItem) -> Unit
) {
    var localQuery by remember(query) { mutableStateOf(query) }
    var showFilters by remember { mutableStateOf(false) }
    val libraryIds = libraryGames.map { it.externalId }.toSet()
    val waitlistIds = waitlistGames.map { it.externalId }.toSet()
    val liveSuggestions = remember(localQuery, recentSearches) {
        if (localQuery.isNotBlank()) suggestionsProvider(localQuery) else emptyList()
    }
    val activeFilterCount = listOfNotNull(
        filters.genreId,
        filters.platform,
        filters.statusFilter,
        filters.pricingFilter.takeIf { it != SearchPricingFilter.ALL },
        filters.releaseFilter.takeIf { it != SearchReleaseFilter.ALL }
    ).size

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Box Row
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
            IconButton(
                onClick = { showFilters = !showFilters },
                modifier = Modifier.testTag("search_filter_toggle")
            ) {
                Icon(
                    imageVector = Icons.Rounded.FilterList,
                    contentDescription = stringResource(R.string.filter_genre),
                    tint = if (activeFilterCount > 0 || showFilters) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { onSearch(localQuery) },
                modifier = Modifier.height(52.dp).testTag("explorer_search_submit")
            ) {
                Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.search_title))
            }
        }

        // Autocomplete Suggestions while typing
        if (liveSuggestions.isNotEmpty() && localQuery != query) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                items(liveSuggestions) { suggestion ->
                    SuggestionChip(
                        onClick = {
                            localQuery = suggestion
                            onSearch(suggestion)
                        },
                        icon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(suggestion, maxLines = 1) }
                    )
                }
            }
        }

        // Recent Search History
        if (recentSearches.isNotEmpty() && localQuery.isBlank()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.recent_searches), style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(onClick = onClearRecentSearches) {
                        Text(stringResource(R.string.clear_history))
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recentSearches) { item ->
                        InputChip(
                            selected = false,
                            onClick = {
                                localQuery = item
                                onSearch(item)
                            },
                            label = { Text(item) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.delete),
                                    modifier = Modifier.size(16.dp).clickable { onRemoveRecentSearch(item) }
                                )
                            }
                        )
                    }
                }
            }
        }

        // Expandable Comprehensive Filter Panel
        AnimatedVisibility(visible = showFilters) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.filter_genre), style = MaterialTheme.typography.labelLarge)
                        if (activeFilterCount > 0) {
                            TextButton(onClick = onResetFilters) { Text(stringResource(R.string.clear_history)) }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = filters.genreId == null,
                                onClick = { onUpdateFilters(filters.copy(genreId = null)) },
                                label = { Text(stringResource(R.string.filter_all)) }
                            )
                        }
                        items(GameCatalogPresets.discoverGenres) { g ->
                            FilterChip(
                                selected = filters.genreId == g.id,
                                onClick = {
                                    onUpdateFilters(filters.copy(genreId = if (filters.genreId == g.id) null else g.id))
                                },
                                label = { Text(if (isArabic) g.nameAr else g.nameEn) }
                            )
                        }
                    }

                    Text(stringResource(R.string.filter_platform), style = MaterialTheme.typography.labelLarge)
                    val plats = listOf(
                        null to stringResource(R.string.filter_all),
                        "PC" to "PC",
                        "Mobile" to "Mobile",
                        "Console" to "Console",
                        "Mac" to "Mac"
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(plats) { (key, label) ->
                            FilterChip(
                                selected = filters.platform == key,
                                onClick = { onUpdateFilters(filters.copy(platform = key)) },
                                label = { Text(label) }
                            )
                        }
                    }

                    Text(stringResource(R.string.filter_pricing), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = filters.pricingFilter == SearchPricingFilter.ALL,
                            onClick = { onUpdateFilters(filters.copy(pricingFilter = SearchPricingFilter.ALL)) },
                            label = { Text(stringResource(R.string.filter_all)) }
                        )
                        FilterChip(
                            selected = filters.pricingFilter == SearchPricingFilter.FREE,
                            onClick = { onUpdateFilters(filters.copy(pricingFilter = SearchPricingFilter.FREE)) },
                            label = { Text(stringResource(R.string.price_free)) },
                            modifier = Modifier.testTag("filter_price_free")
                        )
                        FilterChip(
                            selected = filters.pricingFilter == SearchPricingFilter.PAID,
                            onClick = { onUpdateFilters(filters.copy(pricingFilter = SearchPricingFilter.PAID)) },
                            label = { Text(stringResource(R.string.price_paid)) }
                        )
                    }

                    Text(stringResource(R.string.filter_status), style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = filters.statusFilter == null,
                                onClick = { onUpdateFilters(filters.copy(statusFilter = null)) },
                                label = { Text(stringResource(R.string.filter_all)) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = filters.statusFilter == GameReleaseStatus.AVAILABLE,
                                onClick = { onUpdateFilters(filters.copy(statusFilter = GameReleaseStatus.AVAILABLE)) },
                                label = { Text(stringResource(R.string.status_available)) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = filters.statusFilter == GameReleaseStatus.UPCOMING,
                                onClick = { onUpdateFilters(filters.copy(statusFilter = GameReleaseStatus.UPCOMING)) },
                                label = { Text(stringResource(R.string.status_upcoming)) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = filters.statusFilter == GameReleaseStatus.CANCELLED,
                                onClick = { onUpdateFilters(filters.copy(statusFilter = GameReleaseStatus.CANCELLED)) },
                                label = { Text(stringResource(R.string.status_cancelled)) }
                            )
                        }
                    }

                    Text(stringResource(R.string.filter_release_date), style = MaterialTheme.typography.labelLarge)
                    val eras = listOf(
                        SearchReleaseFilter.ALL to stringResource(R.string.filter_all),
                        SearchReleaseFilter.UPCOMING to stringResource(R.string.era_upcoming),
                        SearchReleaseFilter.RECENT_2024_2026 to stringResource(R.string.era_recent),
                        SearchReleaseFilter.MODERN_2020_2023 to stringResource(R.string.era_modern),
                        SearchReleaseFilter.CLASSIC to stringResource(R.string.era_classic)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(eras) { (era, label) ->
                            FilterChip(
                                selected = filters.releaseFilter == era,
                                onClick = { onUpdateFilters(filters.copy(releaseFilter = era)) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        // Results List
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
                if (results.isEmpty()) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        EmptyVaultState(
                            title = stringResource(R.string.empty_search_title),
                            description = stringResource(R.string.empty_search_desc),
                            actionText = if (activeFilterCount > 0) stringResource(R.string.clear_history) else null,
                            onAction = if (activeFilterCount > 0) onResetFilters else null
                        )
                    }
                } else {
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
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        GameCoverImage(
                                            url = game.coverUrl ?: game.iconUrl,
                                            title = game.title,
                                            modifier = Modifier.size(width = 104.dp, height = 70.dp).clip(RoundedCornerShape(10.dp))
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(
                                                text = game.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (game.genres.isNotEmpty()) {
                                                Text(
                                                    text = game.genres.take(3).joinToString(" • "),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = "${game.platforms.joinToString(", ")} • ${game.releaseDateText}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            ReleaseStatusBadge(game.status)
                                            game.priceDisplay?.let { price ->
                                                StatusBadge(
                                                    text = price,
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            OutlinedButton(onClick = { onSelectGame(game) }) {
                                                Text(stringResource(R.string.view_details))
                                            }
                                            IconButton(onClick = { if (!inLib) onAddToLibrary(game) }) {
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogGamePreviewSheet(
    selectedPreview: CatalogGameItem,
    selectedGameNews: List<NewsArticle>,
    libraryGames: List<LibraryGame>,
    waitlistGames: List<WaitlistGame>,
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onAddToLibrary: (CatalogGameItem) -> Unit,
    onAddToWaitlist: (CatalogGameItem) -> Unit
) {
    val context = LocalContext.current
    val inLib = libraryGames.any { it.externalId == selectedPreview.externalId }
    val inWait = waitlistGames.any { it.externalId == selectedPreview.externalId }
    val trendingReason = if (isArabic) selectedPreview.trendingReasonAr else selectedPreview.trendingReasonEn

    ModalBottomSheet(onDismissRequest = onDismiss) {
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
                modifier = Modifier.fillMaxWidth().height(185.dp).clip(RoundedCornerShape(16.dp))
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selectedPreview.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                ReleaseStatusBadge(selectedPreview.status)
            }
            if (!trendingReason.isNullOrBlank()) {
                StatusBadge(
                    text = trendingReason,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Text(
                text = listOfNotNull(
                    selectedPreview.releaseDateText,
                    selectedPreview.platforms.joinToString(", ").takeIf { it.isNotBlank() },
                    selectedPreview.priceDisplay,
                    selectedPreview.metacriticScore?.let { "Metacritic: $it" }
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            if (selectedPreview.genres.isNotEmpty()) {
                Text(
                    text = selectedPreview.genres.joinToString(" • "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
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
