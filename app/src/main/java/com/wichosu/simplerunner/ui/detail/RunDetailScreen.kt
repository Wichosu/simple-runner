package com.wichosu.simplerunner.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wichosu.simplerunner.R
import com.wichosu.simplerunner.SimpleRunnerApplication
import com.wichosu.simplerunner.data.local.RunEntity
import com.wichosu.simplerunner.domain.Formatters
import com.wichosu.simplerunner.ui.map.RouteMap
import com.wichosu.simplerunner.ui.map.RouteMapMode
import com.wichosu.simplerunner.ui.map.RoutePoint
import com.wichosu.simplerunner.ui.map.toRoutePoint
import com.wichosu.simplerunner.ui.util.RunFormatting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDetailScreen(
    runId: String,
    onBack: () -> Unit,
) {
    val repository = (LocalContext.current.applicationContext as SimpleRunnerApplication)
        .container.runRepository
    val run by repository.run(runId).collectAsStateWithLifecycle(initialValue = null)
    val points by repository.points(runId).collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.run_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val current = run
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            RunDetailContent(
                run = current,
                points = points.map { it.toRoutePoint() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
            )
        }
    }
}

@Composable
private fun RunDetailContent(
    run: RunEntity,
    points: List<RoutePoint>,
    modifier: Modifier = Modifier,
) {
    val pace = Formatters.pace(
        Formatters.paceSecondsPerKm(run.movingMillis, run.distanceM),
    )

    Column(modifier = modifier) {
        Text(
            text = RunFormatting.startedAt(run.startedAt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        Row {
            Text(
                text = Formatters.distanceKm(run.distanceM),
                style = MaterialTheme.typography.displayLarge,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.unit_km),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 36.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))

        Row {
            DetailStat(
                label = stringResource(R.string.label_time),
                value = Formatters.duration(run.movingMillis),
            )
            Spacer(Modifier.width(40.dp))
            DetailStat(
                label = stringResource(R.string.label_pace),
                value = pace,
                suffix = stringResource(R.string.unit_pace),
            )
        }

        Spacer(Modifier.height(20.dp))

        // The map takes whatever space is left, so a tall screen gets a big map and a
        // short one still keeps the stats and the map visible together.
        if (points.size < 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.detail_no_route),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            RouteMap(
                points = points,
                mode = RouteMapMode.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun DetailStat(
    label: String,
    value: String,
    suffix: String? = null,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
            )
            if (suffix != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = suffix,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 3.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
