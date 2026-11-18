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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.GameMission
import com.example.model.LibraryGame
import com.example.model.MissionCategory
import com.example.ui.components.AddMissionDialog
import com.example.ui.components.EmptyVaultState
import com.example.ui.components.MissionCard
import com.example.viewmodel.UiState

@Composable
fun MissionsScreen(
    missionsState: UiState<List<GameMission>>,
    libraryGames: List<LibraryGame>,
    onAddMission: (String?, String, String, String?, MissionCategory, Long, Long, String?) -> Unit,
    onIncrementMission: (GameMission) -> Unit,
    onToggleMission: (GameMission) -> Unit,
    onDeleteMission: (String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<MissionCategory?>(null) }
    var showRemainingOnly by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text(stringResource(R.string.filter_all)) }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedCategory == MissionCategory.DAILY,
                        onClick = { selectedCategory = MissionCategory.DAILY },
                        label = { Text(stringResource(R.string.filter_daily)) }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedCategory == MissionCategory.WEEKLY,
                        onClick = { selectedCategory = MissionCategory.WEEKLY },
                        label = { Text(stringResource(R.string.filter_weekly)) }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedCategory == MissionCategory.SEASONAL,
                        onClick = { selectedCategory = MissionCategory.SEASONAL },
                        label = { Text(stringResource(R.string.filter_seasonal)) }
                    )
                }
                item {
                    FilterChip(
                        selected = showRemainingOnly,
                        onClick = { showRemainingOnly = !showRemainingOnly },
                        label = { Text(stringResource(R.string.remaining_label)) }
                    )
                }
            }

            when (missionsState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Text(
                        text = missionsState.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(20.dp)
                    )
                }
                is UiState.Success -> {
                    val filtered = missionsState.data.filter { m ->
                        (selectedCategory == null || m.resolvedCategory == selectedCategory) &&
                            (!showRemainingOnly || !m.completed)
                    }
                    if (filtered.isEmpty()) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            EmptyVaultState(
                                title = stringResource(R.string.empty_missions_title),
                                description = stringResource(R.string.empty_missions_desc),
                                actionText = stringResource(R.string.add_mission),
                                onAction = { showAddDialog = true }
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                val doneCount = filtered.count { it.completed }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${stringResource(R.string.remaining_label)}: ${filtered.size - doneCount}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = "${stringResource(R.string.completed_label)}: $doneCount",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            items(filtered, key = { it.id }) { mission ->
                                MissionCard(
                                    mission = mission,
                                    onIncrement = { onIncrementMission(mission) },
                                    onToggleComplete = { onToggleMission(mission) },
                                    onDelete = { onDeleteMission(mission.id) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(72.dp)) }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_mission_fab")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_mission))
        }
    }

    if (showAddDialog) {
        AddMissionDialog(
            libraryGames = libraryGames,
            onDismiss = { showAddDialog = false },
            onSave = onAddMission
        )
    }
}
