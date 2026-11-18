package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.CatalogGameItem
import com.example.model.GameCatalogPresets
import com.example.model.GenreSpec
import com.example.model.LibraryGame
import com.example.ui.components.GameCoverImage
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(
    isArabic: Boolean,
    trendingState: UiState<List<CatalogGameItem>>,
    recommendedState: UiState<List<CatalogGameItem>>,
    preferredGenres: List<String>,
    hasCompletedOnboarding: Boolean,
    selectedGenre: GenreSpec?,
    genreGamesState: UiState<List<CatalogGameItem>>,
    libraryGames: List<LibraryGame>,
    onSaveInterests: (List<String>) -> Unit,
    onSelectGenre: (GenreSpec) -> Unit,
    onDismissGenre: () -> Unit,
    onOpenGamePreview: (CatalogGameItem) -> Unit,
    onAddToLibrary: (CatalogGameItem) -> Unit,
    onOpenAllTrending: () -> Unit,
    onRefreshRecommended: () -> Unit
) {
    val libraryIds = libraryGames.map { it.externalId }.toSet()
    var showInterestEditor by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // First-use genre onboarding OR quick interest editor
        if (!hasCompletedOnboarding || showInterestEditor) {
            item {
                GenreInterestsPickerCard(
                    isArabic = isArabic,
                    initialSelected = preferredGenres,
                    isFirstTime = !hasCompletedOnboarding,
                    onSave = { picked ->
                        onSaveInterests(picked)
                        showInterestEditor = false
                    },
                    onCancel = if (hasCompletedOnboarding) {
                        { showInterestEditor = false }
                    } else null
                )
            }
        }

        // 1. Trending Games Preview Strip ("الأكثر رواجاً")
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(
                    title = stringResource(R.string.trending_title),
                    actionLabel = stringResource(R.string.view_all),
                    onActionClick = onOpenAllTrending
                )
                when (trendingState) {
                    is UiState.Loading -> Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    is UiState.Error -> Text(
                        text = trendingState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    is UiState.Success -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(trendingState.data.take(8), key = { "trend_${it.externalId}" }) { game ->
                                TrendingHorizontalCard(
                                    game = game,
                                    isArabic = isArabic,
                                    inLibrary = libraryIds.contains(game.externalId),
                                    onOpen = { onOpenGamePreview(game) },
                                    onAdd = { onAddToLibrary(game) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Recommended For You ("الألعاب المقترحة لك")
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader(
                        title = stringResource(R.string.recommended_title),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { showInterestEditor = !showInterestEditor },
                        modifier = Modifier.testTag("change_interests_button")
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.change_interests))
                    }
                    IconButton(onClick = onRefreshRecommended) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }

                when (recommendedState) {
                    is UiState.Loading -> Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    is UiState.Error -> Text(
                        text = recommendedState.message,
                        color = MaterialTheme.colorScheme.error
                    )

                    is UiState.Success -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(recommendedState.data, key = { "rec_${it.externalId}" }) { game ->
                                RecommendedGameCard(
                                    game = game,
                                    inLibrary = libraryIds.contains(game.externalId),
                                    onLearnMore = { onOpenGamePreview(game) },
                                    onAddToLibrary = { onAddToLibrary(game) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Game Genres Grid (14 Required Categories)
        item {
            SectionHeader(title = stringResource(R.string.genres_title))
        }

        val genreRows = GameCatalogPresets.discoverGenres.chunked(2)
        items(genreRows, key = { row -> row.joinToString("_") { it.id } }) { rowGenres ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowGenres.forEach { genre ->
                    GenreVisualCard(
                        genre = genre,
                        isArabic = isArabic,
                        onClick = { onSelectGenre(genre) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("genre_card_${genre.id}")
                    )
                }
                if (rowGenres.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }

    // Genre Games Browser Bottom Sheet
    if (selectedGenre != null) {
        ModalBottomSheet(onDismissRequest = onDismissGenre) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (isArabic) selectedGenre.nameAr else selectedGenre.nameEn,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isArabic) selectedGenre.subtitleAr else selectedGenre.subtitleEn,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                when (genreGamesState) {
                    is UiState.Loading -> Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    is UiState.Error -> Text(
                        text = genreGamesState.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )

                    is UiState.Success -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().height(420.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(genreGamesState.data, key = { "g_${it.externalId}" }) { game ->
                                val inLib = libraryIds.contains(game.externalId)
                                DiscoverListRowCard(
                                    game = game,
                                    inLibrary = inLib,
                                    onOpen = { onOpenGamePreview(game) },
                                    onAdd = { onAddToLibrary(game) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrendingScreen(
    isArabic: Boolean,
    trendingState: UiState<List<CatalogGameItem>>,
    libraryGames: List<LibraryGame>,
    onRefresh: () -> Unit,
    onOpenGamePreview: (CatalogGameItem) -> Unit,
    onAddToLibrary: (CatalogGameItem) -> Unit
) {
    val libraryIds = libraryGames.map { it.externalId }.toSet()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), MaterialTheme.shapes.medium)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocalFireDepartment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.trending_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(R.string.trending_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            }
        }

        when (trendingState) {
            is UiState.Loading -> item {
                Box(modifier = Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(trendingState.message, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
                }
            }

            is UiState.Success -> {
                items(trendingState.data, key = { "tr_full_${it.externalId}" }) { game ->
                    val inLib = libraryIds.contains(game.externalId)
                    val reason = if (isArabic) game.trendingReasonAr else game.trendingReasonEn
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
                            .clickable { onOpenGamePreview(game) }
                    ) {
                        Column {
                            Box {
                                GameCoverImage(
                                    url = game.coverUrl ?: game.iconUrl,
                                    title = game.title,
                                    modifier = Modifier.fillMaxWidth().height(165.dp)
                                )
                                game.trendingRank?.let { rank ->
                                    StatusBadge(
                                        text = "#$rank",
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(game.title, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = (game.genres.take(2) + game.platforms.take(3)).joinToString(" • "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                if (!reason.isNullOrBlank()) {
                                    StatusBadge(
                                        text = reason,
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                if (game.description.isNotBlank()) {
                                    Text(
                                        text = game.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { onOpenGamePreview(game) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.learn_more))
                                    }
                                    Button(
                                        onClick = { if (!inLib) onAddToLibrary(game) },
                                        enabled = !inLib,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (inLib) Icons.Rounded.Check else Icons.Rounded.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (inLib) stringResource(R.string.in_library) else stringResource(R.string.add_to_library))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreInterestsPickerCard(
    isArabic: Boolean,
    initialSelected: List<String>,
    isFirstTime: Boolean,
    onSave: (List<String>) -> Unit,
    onCancel: (() -> Unit)?
) {
    var selected by remember(initialSelected) { mutableStateOf(initialSelected.toSet()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), MaterialTheme.shapes.large)
            .testTag("genre_onboarding_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isFirstTime) stringResource(R.string.choose_interests_title)
                    else stringResource(R.string.change_interests),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = stringResource(R.string.choose_interests_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GameCatalogPresets.discoverGenres.forEach { genre ->
                    val isPicked = selected.contains(genre.id)
                    FilterChip(
                        selected = isPicked,
                        onClick = {
                            selected = if (isPicked) selected - genre.id else selected + genre.id
                        },
                        label = { Text(if (isArabic) genre.nameAr else genre.nameEn) },
                        modifier = Modifier.testTag("interest_chip_${genre.id}")
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (onCancel != null) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.cancel))
                    }
                }
                Button(
                    onClick = { onSave(selected.toList()) },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.weight(1f).testTag("save_interests_button")
                ) {
                    Text(stringResource(R.string.save_interests))
                }
            }
        }
    }
}

@Composable
private fun TrendingHorizontalCard(
    game: CatalogGameItem,
    isArabic: Boolean,
    inLibrary: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit
) {
    val reason = if (isArabic) game.trendingReasonAr else game.trendingReasonEn
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .width(245.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen)
    ) {
        Column {
            Box {
                GameCoverImage(
                    url = game.coverUrl ?: game.iconUrl,
                    title = game.title,
                    modifier = Modifier.fillMaxWidth().height(126.dp)
                )
                game.trendingRank?.let { r ->
                    StatusBadge(
                        text = "#$r",
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(game.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = (game.genres.take(2) + game.platforms.take(2)).joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!reason.isNullOrBlank()) {
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.learn_more), maxLines = 1)
                    }
                    IconButton(onClick = { if (!inLibrary) onAdd() }) {
                        Icon(
                            imageVector = if (inLibrary) Icons.Rounded.Check else Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.add_to_library),
                            tint = if (inLibrary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendedGameCard(
    game: CatalogGameItem,
    inLibrary: Boolean,
    onLearnMore: () -> Unit,
    onAddToLibrary: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .width(236.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            .clickable(onClick = onLearnMore)
    ) {
        Column {
            Box {
                GameCoverImage(
                    url = game.coverUrl ?: game.iconUrl,
                    title = game.title,
                    modifier = Modifier.fillMaxWidth().height(122.dp)
                )
                if (game.metacriticScore != null) {
                    StatusBadge(
                        text = "${game.metacriticScore}",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(game.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = game.genres.take(2).joinToString(" • ").ifBlank { game.sourceType.name },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Text(
                    text = game.platforms.take(3).joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onLearnMore, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.learn_more), maxLines = 1)
                    }
                    Button(
                        onClick = { if (!inLibrary) onAddToLibrary() },
                        enabled = !inLibrary,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (inLibrary) stringResource(R.string.in_library) else stringResource(R.string.add_to_library), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreVisualCard(
    genre: GenreSpec,
    isArabic: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .height(116.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GameCoverImage(
                url = genre.coverImageUrl,
                title = if (isArabic) genre.nameAr else genre.nameEn,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = if (isArabic) genre.nameAr else genre.nameEn,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isArabic) genre.subtitleAr else genre.subtitleEn,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DiscoverListRowCard(
    game: CatalogGameItem,
    inLibrary: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GameCoverImage(
                url = game.coverUrl ?: game.iconUrl,
                title = game.title,
                modifier = Modifier.size(width = 96.dp, height = 64.dp).clip(RoundedCornerShape(10.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(game.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = (game.genres.take(2) + game.platforms.take(2)).joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1
                )
                if (game.metacriticScore != null || game.priceDisplay != null) {
                    Text(
                        text = listOfNotNull(
                            game.priceDisplay,
                            game.metacriticScore?.let { "Score: $it" }
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = { if (!inLibrary) onAdd() }) {
                Icon(
                    imageVector = if (inLibrary) Icons.Rounded.Check else Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.add_to_library),
                    tint = if (inLibrary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
