package com.example.ui.scores

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.GameMode
import com.example.data.PlayerProfile
import com.example.data.ScoreRecord
import com.example.data.UiState
import com.example.ui.game.formatNumber
import com.example.ui.theme.BerryPlum
import com.example.ui.theme.BerryPlumDeep
import com.example.ui.theme.HoneyGold
import com.example.ui.theme.MintSpark
import com.example.ui.theme.StrawberryPop
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

private enum class ScoreFilter {
    ALL,
    CLEANUP,
    SCORE_RUSH
}

@Composable
fun HighScoresScreen(
    profileState: UiState<PlayerProfile?>,
    scoresState: UiState<List<ScoreRecord>>,
    onDeleteScore: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(ScoreFilter.ALL) }
    val dateFormatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.scores_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Personal Best Summary Hero Card
            item {
                val profile = (profileState as? UiState.Success)?.data
                PersonalStatsHeroCard(profile = profile)
            }

            // Mode Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == ScoreFilter.ALL,
                        onClick = { selectedFilter = ScoreFilter.ALL },
                        label = { Text(stringResource(R.string.scores_filter_all)) },
                        modifier = Modifier.testTag("filter_all_chip")
                    )
                    FilterChip(
                        selected = selectedFilter == ScoreFilter.CLEANUP,
                        onClick = { selectedFilter = ScoreFilter.CLEANUP },
                        label = { Text(stringResource(R.string.mode_cleanup)) },
                        modifier = Modifier.testTag("filter_cleanup_chip")
                    )
                    FilterChip(
                        selected = selectedFilter == ScoreFilter.SCORE_RUSH,
                        onClick = { selectedFilter = ScoreFilter.SCORE_RUSH },
                        label = { Text(stringResource(R.string.mode_score_rush)) },
                        modifier = Modifier.testTag("filter_rush_chip")
                    )
                }
            }

            when (scoresState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = StrawberryPop)
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = scoresState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    val filtered = scoresState.data.filter { record ->
                        when (selectedFilter) {
                            ScoreFilter.ALL -> true
                            ScoreFilter.CLEANUP -> record.parsedMode == GameMode.CLEANUP
                            ScoreFilter.SCORE_RUSH -> record.parsedMode == GameMode.SCORE_RUSH
                        }
                    }

                    if (filtered.isEmpty()) {
                        item {
                            EmptyScoresCard()
                        }
                    } else {
                        itemsIndexed(
                            items = filtered,
                            key = { index, item -> item.id.ifEmpty { "score_$index" } }
                        ) { index, record ->
                            ScoreHistoryCard(
                                rank = index + 1,
                                record = record,
                                dateFormatter = dateFormatter,
                                onDelete = {
                                    if (record.id.isNotBlank()) {
                                        onDeleteScore(record.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonalStatsHeroCard(profile: PlayerProfile?) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(BerryPlumDeep, BerryPlum, StrawberryPop)
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.stat_best_score),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = formatNumber(profile?.bestScore ?: 0L),
                            style = MaterialTheme.typography.displayMedium,
                            color = Color.White,
                            modifier = Modifier.testTag("best_score_hero")
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = HoneyGold
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EmojiEvents,
                            contentDescription = null,
                            tint = BerryPlumDeep,
                            modifier = Modifier
                                .padding(12.dp)
                                .size(28.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HeroStatPill(
                        label = stringResource(R.string.stat_highest_stage),
                        value = "Stage ${profile?.highestStage ?: 1L}"
                    )
                    HeroStatPill(
                        label = stringResource(R.string.stat_candies_swept),
                        value = formatNumber(profile?.totalCandiesCleared ?: 0L)
                    )
                    HeroStatPill(
                        label = stringResource(R.string.stat_best_combo),
                        value = "x${profile?.bestCombo ?: 0L}"
                    )
                    HeroStatPill(
                        label = stringResource(R.string.stat_games_played),
                        value = "${profile?.gamesPlayed ?: 0L}"
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStatPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.78f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ScoreHistoryCard(
    rank: Int,
    record: ScoreRecord,
    dateFormatter: SimpleDateFormat,
    onDelete: () -> Unit
) {
    val medalColor = when (rank) {
        1 -> HoneyGold
        2 -> Color(0xFFC8D2E3)
        3 -> Color(0xFFE59866)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val medalTextColor = if (rank <= 3) BerryPlumDeep else MaterialTheme.colorScheme.onSurfaceVariant
    val displayTime = record.createdAt ?: record.updatedAt ?: Timestamp.now()
    val formattedDate = dateFormatter.format(displayTime.toDate())

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("score_record_card_$rank")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = medalColor,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "#$rank",
                        style = MaterialTheme.typography.titleSmall,
                        color = medalTextColor
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatNumber(record.score),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (record.parsedMode == GameMode.CLEANUP) {
                            MintSpark.copy(alpha = 0.16f)
                        } else {
                            StrawberryPop.copy(alpha = 0.14f)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (record.parsedMode == GameMode.CLEANUP) {
                                    Icons.Rounded.AutoAwesome
                                } else {
                                    Icons.Rounded.Timer
                                },
                                contentDescription = null,
                                tint = if (record.parsedMode == GameMode.CLEANUP) MintSpark else StrawberryPop,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (record.parsedMode == GameMode.CLEANUP) {
                                    "Stage ${record.stage}"
                                } else {
                                    stringResource(R.string.mode_score_rush)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (record.parsedMode == GameMode.CLEANUP) MintSpark else StrawberryPop
                            )
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${record.candiesCleared} swept • x${record.maxCombo} combo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (record.stars > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            repeat(record.stars.toInt().coerceIn(0, 3)) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = HoneyGold,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("delete_score_button_$rank")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.scores_delete_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyScoresCard() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = StrawberryPop.copy(alpha = 0.12f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = StrawberryPop,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.scores_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.scores_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
