package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.GpxTrack
import com.example.domain.SourceCategory
import com.example.domain.SourceSummary
import com.example.ui.theme.TelemetryMonoStyle
import java.util.Locale

@Composable
fun TrackManagerSidebar(
    summaries: List<SourceSummary>,
    tracks: List<GpxTrack>,
    allSources: List<SourceCategory>,
    visibleTrackCount: Int,
    isDarkTheme: Boolean,
    onToggleUiTheme: () -> Unit,
    showCloseButton: Boolean,
    onClose: () -> Unit,
    onImportClick: () -> Unit,
    onLoadDemoClick: () -> Unit,
    onCreateSourceClick: () -> Unit,
    onToggleAllVisibility: (Boolean) -> Unit,
    onToggleSourceVisibility: (String) -> Unit,
    onToggleTrackVisibility: (String) -> Unit,
    onOpenColorPicker: (String) -> Unit,
    onReassignTrackSource: (String, String) -> Unit,
    onDeleteTrack: (String) -> Unit,
    onDeleteSourceTracks: (String) -> Unit,
    onClearAllTracks: () -> Unit,
    onOpenExportDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tracksBySource = remember(tracks) { tracks.groupBy { it.sourceId } }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    val allItemsVisible = tracks.isNotEmpty() && visibleTrackCount == tracks.size
    val anyItemsVisible = visibleTrackCount > 0
    val masterState = when {
        allItemsVisible -> ToggleableState.On
        anyItemsVisible -> ToggleableState.Indeterminate
        else -> ToggleableState.Off
    }

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sidebar Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TRACK MANAGER",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${tracks.size} file(s) • ${summaries.count { it.trackCount > 0 }} active source(s)",
                        style = TelemetryMonoStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                IconButton(
                    onClick = onToggleUiTheme,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_ui_theme_button")
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = if (isDarkTheme) "Switch to light UI" else "Switch to dark UI",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (showCloseButton) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_sidebar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close track manager",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

            // Quick Batch Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onImportClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("sidebar_import_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add GPX", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                OutlinedButton(
                    onClick = onLoadDemoClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("sidebar_demo_button")
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Demo",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                IconButton(
                    onClick = onCreateSourceClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .testTag("create_source_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CreateNewFolder,
                        contentDescription = "Create custom source",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (tracks.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggleAllVisibility(!allItemsVisible) }
                        .testTag("sidebar_toggle_all_row")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TriStateCheckbox(
                                state = masterState,
                                onClick = { onToggleAllVisibility(!allItemsVisible) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = MaterialTheme.colorScheme.onSurface,
                                    checkmarkColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("sidebar_toggle_all_checkbox")
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (allItemsVisible) "Hide All Exported Items" else "Show All Exported Items",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "$visibleTrackCount/${tracks.size}",
                            style = TelemetryMonoStyle,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }

            // Grouped Source & File List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(summaries, key = { it.source.id }) { summary ->
                    val source = summary.source
                    val sourceTracks = tracksBySource[source.id].orEmpty()
                    val isExpanded = expandedGroups[source.id] ?: sourceTracks.isNotEmpty()

                    SourceGroupCard(
                        summary = summary,
                        tracks = sourceTracks,
                        allSources = allSources,
                        isExpanded = isExpanded,
                        onToggleExpand = { expandedGroups[source.id] = !isExpanded },
                        onToggleVisibility = { onToggleSourceVisibility(source.id) },
                        onToggleTrackVisibility = onToggleTrackVisibility,
                        onPickColor = { onOpenColorPicker(source.id) },
                        onDeleteSourceTracks = { onDeleteSourceTracks(source.id) },
                        onReassignTrackSource = onReassignTrackSource,
                        onDeleteTrack = onDeleteTrack
                    )
                }

                if (tracks.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 12.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            TextButton(
                                onClick = onClearAllTracks,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("clear_all_tracks_button")
                            ) {
                                Icon(
                                    Icons.Filled.DeleteSweep,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Clear All Tracks",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

            // Export Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenExportDialog,
                    enabled = visibleTrackCount > 0,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("sidebar_export_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.FileDownload,
                        contentDescription = stringResource(R.string.cd_export_gpx),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (visibleTrackCount > 0) {
                            "Export Aggregated ($visibleTrackCount)"
                        } else {
                            "No Visible Tracks to Export"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceGroupCard(
    summary: SourceSummary,
    tracks: List<GpxTrack>,
    allSources: List<SourceCategory>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleVisibility: () -> Unit,
    onToggleTrackVisibility: (String) -> Unit,
    onPickColor: () -> Unit,
    onDeleteSourceTracks: () -> Unit,
    onReassignTrackSource: (String, String) -> Unit,
    onDeleteTrack: (String) -> Unit
) {
    val src = summary.source
    val accent = parseHexColor(src.colorHex)
    val readableAccent = readableOnSurface(accent, MaterialTheme.colorScheme.surfaceVariant)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = if (src.isVisible && tracks.isNotEmpty()) 1.5.dp else 1.dp,
            color = if (src.isVisible && tracks.isNotEmpty()) readableAccent else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = src.isVisible,
                    onCheckedChange = { onToggleVisibility() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = accent,
                        uncheckedColor = MaterialTheme.colorScheme.onSurface,
                        checkmarkColor = if (accent.luminance() > 0.5f) Color.Black else Color.White
                    ),
                    modifier = Modifier.testTag("sidebar_source_checkbox_${src.id}")
                )

                // Color swatch button
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .border(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f), CircleShape)
                        .clickable(onClick = onPickColor)
                        .testTag("sidebar_color_picker_${src.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Palette,
                        contentDescription = stringResource(R.string.cd_color_picker),
                        tint = if (accent.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = src.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "%d track(s) • %.2f km",
                            summary.trackCount,
                            summary.totalDistanceKm
                        ),
                        style = TelemetryMonoStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (tracks.isNotEmpty() || !src.isPreset) {
                    IconButton(
                        onClick = onDeleteSourceTracks,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_source_${src.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.cd_delete_source),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded && tracks.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    tracks.forEach { track ->
                        TrackItemRow(
                            track = track,
                            sourceVisible = src.isVisible,
                            accentColor = accent,
                            allSources = allSources,
                            onToggleVisibility = { onToggleTrackVisibility(track.id) },
                            onReassignSource = { newSourceId -> onReassignTrackSource(track.id, newSourceId) },
                            onDelete = { onDeleteTrack(track.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackItemRow(
    track: GpxTrack,
    sourceVisible: Boolean,
    accentColor: Color,
    allSources: List<SourceCategory>,
    onToggleVisibility: () -> Unit,
    onReassignSource: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showSourceMenu by remember { mutableStateOf(false) }
    val readableAccent = readableOnSurface(accentColor, MaterialTheme.colorScheme.surface)
    val effectiveVisible = track.isVisible && sourceVisible

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (effectiveVisible) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleVisibility)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = effectiveVisible,
                onCheckedChange = { onToggleVisibility() },
                colors = CheckboxDefaults.colors(
                    checkedColor = accentColor,
                    uncheckedColor = MaterialTheme.colorScheme.onSurface,
                    checkmarkColor = if (accentColor.luminance() > 0.5f) Color.Black else Color.White
                ),
                modifier = Modifier.testTag("sidebar_track_checkbox_${track.id}")
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (effectiveVisible) accentColor else accentColor.copy(alpha = 0.35f))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.trackName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (effectiveVisible) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(track.fileName)
                        if (!track.recordedAtIso.isNullOrBlank()) {
                            append(" • ")
                            append(track.recordedAtIso.take(10))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = String.format(
                        Locale.US,
                        "%.2f km • %d pts",
                        track.distanceKm,
                        track.rawPointCount
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = readableAccent,
                    fontWeight = FontWeight.Bold
                )
            }

            Box {
                IconButton(
                    onClick = { showSourceMenu = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("reassign_track_${track.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = "Change track source",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = showSourceMenu,
                    onDismissRequest = { showSourceMenu = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
                ) {
                    allSources.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(parseHexColor(option.colorHex))
                                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), CircleShape)
                                    )
                                    Text(
                                        option.name,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            },
                            onClick = {
                                showSourceMenu = false
                                onReassignSource(option.id)
                            }
                        )
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("delete_track_${track.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_delete_track),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
