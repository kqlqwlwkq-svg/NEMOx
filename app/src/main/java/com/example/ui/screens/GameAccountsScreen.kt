package com.example.ui.screens

import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.AccountApiMode
import com.example.model.GameAccount
import com.example.model.GameCatalogPresets
import com.example.ui.components.EmptyVaultState
import com.example.ui.components.StatusBadge
import com.example.viewmodel.UiState

@Composable
fun GameAccountsScreen(
    accountsState: UiState<List<GameAccount>>,
    isSyncing: Boolean,
    isArabic: Boolean,
    isGuestMode: Boolean = false,
    onSignInWithGoogle: () -> Unit = {},
    onLinkOrSyncAccount: (
        gameKey: String,
        customGameName: String,
        playerIdentifier: String,
        platform: String,
        serverRegion: String,
        manualLevel: Long,
        manualRank: String,
        manualStats: String,
        existingId: String
    ) -> Unit,
    onRefreshLive: (GameAccount) -> Unit,
    onDeleteAccount: (String) -> Unit
) {
    var showLinkDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isGuestMode) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f), MaterialTheme.shapes.large)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = stringResource(R.string.guest_signin_sync_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = stringResource(R.string.guest_signin_accounts_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onSignInWithGoogle,
                                modifier = Modifier.fillMaxWidth().testTag("guest_prompt_google_signin")
                            ) {
                                Text(stringResource(R.string.auth_sign_in_google))
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.no_passwords_banner),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                if (isSyncing) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing live account data…", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                when (accountsState) {
                    is UiState.Loading -> item {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> item {
                        Text(accountsState.message, color = MaterialTheme.colorScheme.error)
                    }
                    is UiState.Success -> {
                        val accounts = accountsState.data
                        if (accounts.isEmpty()) {
                            item {
                                EmptyVaultState(
                                    title = stringResource(R.string.empty_accounts_title),
                                    description = stringResource(R.string.empty_accounts_desc),
                                    actionText = stringResource(R.string.link_account),
                                    onAction = { showLinkDialog = true }
                                )
                            }
                        } else {
                            items(accounts, key = { it.id }) { account ->
                                AccountCard(
                                    account = account,
                                    onRefreshLive = { onRefreshLive(account) },
                                    onDelete = { onDeleteAccount(account.id) }
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }

        if (!isGuestMode) {
            FloatingActionButton(
                onClick = { showLinkDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("link_account_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.link_account))
            }
        }
    }

    if (showLinkDialog) {
        LinkGameAccountDialog(
            isArabic = isArabic,
            onDismiss = { showLinkDialog = false },
            onSave = { key, name, id, plat, reg, lvl, rank, stats ->
                onLinkOrSyncAccount(key, name, id, plat, reg, lvl, rank, stats, "")
            }
        )
    }
}

@Composable
private fun AccountCard(
    account: GameAccount,
    onRefreshLive: () -> Unit,
    onDelete: () -> Unit
) {
    val (modeText, modeBg, modeFg) = when (account.resolvedApiMode) {
        AccountApiMode.LIVE_OFFICIAL_API -> Triple(
            stringResource(R.string.api_live_official),
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        AccountApiMode.LIVE_COMMUNITY_XML -> Triple(
            stringResource(R.string.api_live_xml),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        AccountApiMode.MANUAL_NO_PUBLIC_API -> Triple(
            stringResource(R.string.api_manual_only),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!account.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = account.avatarUrl,
                        contentDescription = account.playerIdentifier,
                        modifier = Modifier.size(46.dp).clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(account.gameName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "ID: ${account.playerIdentifier} • ${account.platform}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (account.resolvedApiMode != AccountApiMode.MANUAL_NO_PUBLIC_API) {
                    IconButton(onClick = onRefreshLive) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.delete))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(text = modeText, containerColor = modeBg, contentColor = modeFg)
                if (account.level > 0) {
                    StatusBadge(
                        text = "Lv.${account.level}",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                if (!account.rankTier.isNullOrBlank()) {
                    StatusBadge(
                        text = account.rankTier,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            if (!account.verifiedPlayerId.isNullOrBlank()) {
                Text(
                    text = "Verified ID: ${account.verifiedPlayerId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (!account.statsSummary.isNullOrBlank()) {
                Text(account.statsSummary, style = MaterialTheme.typography.bodyMedium)
            }
            if (!account.apiAvailabilityNote.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp).padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = account.apiAvailabilityNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkGameAccountDialog(
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, Long, String, String) -> Unit
) {
    var selectedSpec by remember { mutableStateOf(GameCatalogPresets.supportedAccountGames.first()) }
    var customName by remember { mutableStateOf("") }
    var playerId by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf(selectedSpec.defaultPlatform) }
    var region by remember { mutableStateOf("") }
    var levelText by remember { mutableStateOf("1") }
    var rankText by remember { mutableStateOf("") }
    var statsText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.link_account)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(GameCatalogPresets.supportedAccountGames) { spec ->
                        FilterChip(
                            selected = selectedSpec.gameKey == spec.gameKey,
                            onClick = {
                                selectedSpec = spec
                                platform = spec.defaultPlatform
                            },
                            label = { Text(spec.gameName) }
                        )
                    }
                }

                // Clear API transparency box
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (selectedSpec.apiMode) {
                                    AccountApiMode.LIVE_OFFICIAL_API -> stringResource(R.string.api_live_official)
                                    AccountApiMode.LIVE_COMMUNITY_XML -> stringResource(R.string.api_live_xml)
                                    AccountApiMode.MANUAL_NO_PUBLIC_API -> stringResource(R.string.api_manual_only)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Text(
                            text = if (isArabic) selectedSpec.availabilitySummaryAr else selectedSpec.availabilitySummaryEn,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (selectedSpec.gameKey == "custom") {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Game Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = playerId,
                    onValueChange = { playerId = it },
                    label = { Text(if (isArabic) selectedSpec.identifierHintAr else selectedSpec.identifierHintEn) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = platform,
                        onValueChange = { platform = it },
                        label = { Text("Platform") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = region,
                        onValueChange = { region = it },
                        label = { Text("Region") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = levelText,
                        onValueChange = { levelText = it.filter { c -> c.isDigit() } },
                        label = { Text("Level") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                    OutlinedTextField(
                        value = rankText,
                        onValueChange = { rankText = it },
                        label = { Text("Rank / Tier") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                }
                OutlinedTextField(
                    value = statsText,
                    onValueChange = { statsText = it },
                    label = { Text("Stats Notes (e.g. K/D, Winrate)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        selectedSpec.gameKey,
                        customName,
                        playerId,
                        platform,
                        region,
                        levelText.toLongOrNull() ?: 1L,
                        rankText,
                        statsText
                    )
                    onDismiss()
                },
                enabled = playerId.isNotBlank() && (selectedSpec.gameKey != "custom" || customName.isNotBlank())
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
