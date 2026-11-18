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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.UserNotification
import com.example.model.UserProfile
import com.example.ui.components.EmptyVaultState
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatPublishDate
import com.example.viewmodel.UiState

@Composable
fun ProfileSettingsScreen(
    displayName: String,
    email: String?,
    photoUrl: String?,
    profileState: UiState<UserProfile?>,
    currentLanguage: String,
    darkMode: Boolean,
    accountsCount: Int,
    missionsCount: Int,
    unreadNotifCount: Int,
    onOpenAccounts: () -> Unit,
    onOpenMissions: () -> Unit,
    onOpenNotifications: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onDarkModeChanged: (Boolean) -> Unit,
    onUpdateGlobalNotifs: (Boolean, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val profile = (profileState as? UiState.Success)?.data
    val masterNotif = profile?.notificationsEnabled ?: true
    val notifUpdates = profile?.notifyOnUpdates ?: true
    val notifSeasons = profile?.notifyOnSeasons ?: true
    val notifMissions = profile?.notifyOnMissions ?: true
    val notifUpcoming = profile?.notifyOnUpcoming ?: true
    val notifNews = profile?.notifyOnNews ?: true

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Profile Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.shapes.large)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = displayName,
                            modifier = Modifier.size(60.dp).clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(displayName, style = MaterialTheme.typography.titleLarge)
                        if (!email.isNullOrBlank()) {
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Hub Shortcuts (Accounts, Missions, Notifications)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HubShortcutCard(
                    icon = Icons.Rounded.AccountTree,
                    title = stringResource(R.string.accounts_title),
                    badge = accountsCount.toString(),
                    onClick = onOpenAccounts,
                    modifier = Modifier.weight(1f).testTag("shortcut_accounts")
                )
                HubShortcutCard(
                    icon = Icons.Rounded.Flag,
                    title = stringResource(R.string.missions_title),
                    badge = missionsCount.toString(),
                    onClick = onOpenMissions,
                    modifier = Modifier.weight(1f).testTag("shortcut_missions")
                )
                HubShortcutCard(
                    icon = Icons.Rounded.Notifications,
                    title = stringResource(R.string.notifications_title),
                    badge = unreadNotifCount.toString(),
                    onClick = onOpenNotifications,
                    modifier = Modifier.weight(1f).testTag("shortcut_notifications")
                )
            }
        }

        // Language & Appearance Settings
        item {
            SectionHeader(title = stringResource(R.string.settings_title))
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.language_label), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = currentLanguage == "system",
                            onClick = { onLanguageSelected("system") },
                            label = { Text(stringResource(R.string.lang_system)) },
                            modifier = Modifier.testTag("settings_lang_system")
                        )
                        FilterChip(
                            selected = currentLanguage == "en",
                            onClick = { onLanguageSelected("en") },
                            label = { Text(stringResource(R.string.lang_en)) },
                            modifier = Modifier.testTag("settings_lang_en")
                        )
                        FilterChip(
                            selected = currentLanguage == "ar",
                            onClick = { onLanguageSelected("ar") },
                            label = { Text(stringResource(R.string.lang_ar)) },
                            modifier = Modifier.testTag("settings_lang_ar")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.dark_mode_label), style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = darkMode,
                            onCheckedChange = onDarkModeChanged,
                            modifier = Modifier.testTag("settings_dark_mode_switch")
                        )
                    }
                }
            }
        }

        // Global Notification Preferences
        item {
            SectionHeader(title = stringResource(R.string.notifications_title))
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingSwitchRow(stringResource(R.string.notifications_master), masterNotif) {
                        onUpdateGlobalNotifs(it, notifUpdates, notifSeasons, notifMissions, notifUpcoming, notifNews)
                    }
                    SettingSwitchRow(stringResource(R.string.notif_updates), notifUpdates, enabled = masterNotif) {
                        onUpdateGlobalNotifs(masterNotif, it, notifSeasons, notifMissions, notifUpcoming, notifNews)
                    }
                    SettingSwitchRow(stringResource(R.string.notif_seasons), notifSeasons, enabled = masterNotif) {
                        onUpdateGlobalNotifs(masterNotif, notifUpdates, it, notifMissions, notifUpcoming, notifNews)
                    }
                    SettingSwitchRow(stringResource(R.string.notif_missions), notifMissions, enabled = masterNotif) {
                        onUpdateGlobalNotifs(masterNotif, notifUpdates, notifSeasons, it, notifUpcoming, notifNews)
                    }
                    SettingSwitchRow(stringResource(R.string.notif_upcoming), notifUpcoming, enabled = masterNotif) {
                        onUpdateGlobalNotifs(masterNotif, notifUpdates, notifSeasons, notifMissions, it, notifNews)
                    }
                    SettingSwitchRow(stringResource(R.string.notif_news), notifNews, enabled = masterNotif) {
                        onUpdateGlobalNotifs(masterNotif, notifUpdates, notifSeasons, notifMissions, notifUpcoming, it)
                    }
                }
            }
        }

        // Sign Out
        item {
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.auth_sign_out))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HubShortcutCard(
    icon: ImageVector,
    title: String,
    badge: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
            Text(badge, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
fun NotificationsScreen(
    notificationsState: UiState<List<UserNotification>>,
    onScanNow: () -> Unit,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onDelete: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onScanNow,
                modifier = Modifier.weight(1f).testTag("scan_alerts_button")
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.scan_alerts_now), maxLines = 1)
            }
            OutlinedButton(
                onClick = onMarkAllRead,
                modifier = Modifier.testTag("mark_all_read_button")
            ) {
                Icon(Icons.Rounded.DoneAll, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.mark_all_read), maxLines = 1)
            }
        }

        when (notificationsState) {
            is UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UiState.Error -> Text(
                text = notificationsState.message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
            is UiState.Success -> {
                val list = notificationsState.data
                if (list.isEmpty()) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        EmptyVaultState(
                            title = stringResource(R.string.empty_notifications_title),
                            description = stringResource(R.string.empty_notifications_desc),
                            actionText = stringResource(R.string.scan_alerts_now),
                            onAction = onScanNow
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(list, key = { it.id }) { notif ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (notif.read) MaterialTheme.colorScheme.surface
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        if (notif.read) MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        MaterialTheme.shapes.medium
                                    )
                                    .clickable { if (!notif.read) onMarkRead(notif.id) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.NotificationsActive,
                                        contentDescription = null,
                                        tint = if (notif.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            StatusBadge(
                                                text = notif.resolvedType.name.replace("_", " "),
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Text(
                                                text = formatPublishDate(notif.createdAtMillis),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(notif.title, style = MaterialTheme.typography.titleSmall)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = notif.body,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onDelete(notif.id) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.delete))
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
