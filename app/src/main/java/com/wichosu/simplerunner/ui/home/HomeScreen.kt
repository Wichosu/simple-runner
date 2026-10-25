package com.wichosu.simplerunner.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wichosu.simplerunner.R
import com.wichosu.simplerunner.SimpleRunnerApplication
import com.wichosu.simplerunner.data.local.RunEntity
import com.wichosu.simplerunner.domain.Formatters
import com.wichosu.simplerunner.domain.RunState
import com.wichosu.simplerunner.tracking.RunTrackingService
import com.wichosu.simplerunner.ui.util.RunFormatting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartRun: () -> Unit,
    onOpenRun: (String) -> Unit,
) {
    val application = LocalContext.current.applicationContext as SimpleRunnerApplication
    val context = LocalContext.current
    val repository = application.container.runRepository
    val sessionState by application.container.runSession.state.collectAsStateWithLifecycle()
    val runs by repository.runs.collectAsStateWithLifecycle(initialValue = emptyList())

    val openRun = runs.firstOrNull { it.endedAt == null }
    val finishedRuns = runs.filter { it.endedAt != null }

    var discardTarget by remember { mutableStateOf<RunEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
        floatingActionButton = {
            if (openRun == null) {
                ExtendedFloatingActionButton(
                    onClick = onStartRun,
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    text = { Text(stringResource(R.string.home_start_run)) },
                )
            }
        },
    ) { innerPadding ->
        when {
            openRun != null -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "open-run") {
                        OpenRunCard(
                            run = openRun,
                            state = sessionState.state,
                            // Both paths lead to the live run screen: it is the only place
                            // that can pause, resume, or finish, and entering it starts or
                            // adopts the run through the tracking service.
                            onOpen = onStartRun,
                            onDiscard = { discardTarget = openRun },
                        )
                    }
                    items(items = finishedRuns, key = { it.id }) { run ->
                        RunRow(run = run, onClick = { onOpenRun(run.id) })
                    }
                }
            }

            finishedRuns.isEmpty() -> {
                EmptyState(onStartRun = onStartRun, modifier = Modifier.padding(innerPadding))
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = finishedRuns, key = { it.id }) { run ->
                        RunRow(run = run, onClick = { onOpenRun(run.id) })
                    }
                }
            }
        }
    }

    discardTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { discardTarget = null },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        discardTarget = null
                        // Through the service: it is the only owner of the run row, so it
                        // can also drop a live session that still holds this run.
                        RunTrackingService.discard(context, target.id)
                    },
                ) {
                    Text(stringResource(R.string.action_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { discardTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun OpenRunCard(
    run: RunEntity,
    state: RunState,
    onOpen: () -> Unit,
    onDiscard: () -> Unit,
) {
    // An open row with an idle session is a run the process left behind; one with a
    // live session is being tracked right now, possibly paused.
    val live = state != RunState.IDLE
    val title = when (state) {
        RunState.PAUSED -> R.string.home_run_paused
        RunState.IDLE -> R.string.home_unfinished_run
        else -> R.string.home_run_in_progress
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (live) Modifier.clickable(onClick = onOpen) else Modifier),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${Formatters.distanceKm(run.distanceM)} km",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = RunFormatting.startedAt(run.startedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    text = Formatters.duration(run.movingMillis),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            if (!live) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.home_unfinished_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpen,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.action_resume_run))
                    }
                    OutlinedButton(
                        onClick = onDiscard,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.action_discard))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    onStartRun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onStartRun,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.home_start_run))
        }
    }
}

@Composable
private fun RunRow(
    run: RunEntity,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${Formatters.distanceKm(run.distanceM)} km",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = RunFormatting.startedAt(run.startedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = Formatters.duration(run.movingMillis),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
