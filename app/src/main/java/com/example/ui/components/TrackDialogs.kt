package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.GeoMath
import com.example.domain.GpxTrack
import com.example.domain.PendingImportTrack
import com.example.domain.SourceCategory
import com.example.ui.theme.SourceSwatchPalette
import com.example.ui.theme.TelemetryMonoStyle
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PendingImportStagingDialog(
    pendingTracks: List<PendingImportTrack>,
    sources: List<SourceCategory>,
    onUpdateItemSource: (tempId: String, sourceId: String) -> Unit,
    onSetAllSources: (sourceId: String) -> Unit,
    onConfirmImport: () -> Unit,
    onCancel: () -> Unit
) {
    val sourcesById = remember(sources) { sources.associateBy { it.id } }

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Column {
                Text(
                    text = "Tag Imported GPX Sources",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${pendingTracks.size} track(s) parsed — review auto-detected source categories",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Set all tracks to:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sources.forEach { src ->
                        val chipColor = parseHexColor(src.colorHex)
                        FilterChip(
                            selected = pendingTracks.all { it.selectedSourceId == src.id },
                            onClick = { onSetAllSources(src.id) },
                            label = { Text(src.name, maxLines = 1) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(chipColor)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = chipColor.copy(alpha = 0.22f),
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.testTag("staging_set_all_${src.id}")
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pendingTracks, key = { it.tempId }) { item ->
                        val assignedSource = sourcesById[item.selectedSourceId]
                        val dotColor = parseHexColor(assignedSource?.colorHex ?: "#E879F9")
                        var dropdownOpen by remember { mutableStateOf(false) }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.trackName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = buildString {
                                            append(String.format(Locale.US, "%.2f km • %d pts", item.distanceKm, item.points.size))
                                            if (!item.creatorRaw.isNullOrBlank()) {
                                                append(" • ")
                                                append(item.creatorRaw)
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = dotColor.copy(alpha = 0.16f),
                                        border = BorderStroke(1.dp, dotColor),
                                        modifier = Modifier
                                            .clickable { dropdownOpen = true }
                                            .heightIn(min = 40.dp)
                                            .testTag("staging_dropdown_${item.tempId}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(dotColor)
                                            )
                                            Text(
                                                text = assignedSource?.name ?: "Custom",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = dropdownOpen,
                                        onDismissRequest = { dropdownOpen = false },
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        sources.forEach { option ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        option.name,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                },
                                                onClick = {
                                                    dropdownOpen = false
                                                    onUpdateItemSource(item.tempId, option.id)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmImport,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_import_button")
            ) {
                Text("Add to Map")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourceColorPickerDialog(
    source: SourceCategory,
    onSelectColor: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var hexInput by remember(source.id) { mutableStateOf(source.colorHex) }
    val isValid = GeoMath.isValidHexColor(hexInput)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Text(
                text = "Customize ${source.name} Color",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SourceSwatchPalette.forEach { hex ->
                        val color = parseHexColor(hex)
                        val selected = hexInput.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { hexInput = hex }
                                .testTag("swatch_$hex"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected color",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { hexInput = it.take(7) },
                    label = { Text("Custom Hex (#RRGGBB)") },
                    singleLine = true,
                    isError = !isValid,
                    textStyle = TelemetryMonoStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isValid) parseHexColor(hexInput)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_hex_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelectColor(hexInput) },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("apply_color_button")
            ) {
                Text("Apply Color")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateCustomSourceDialog(
    onCreate: (name: String, colorHex: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedHex by remember { mutableStateOf(SourceSwatchPalette[5]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Add Custom Source", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Source Name (e.g. Komoot, AllTrails)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_source_name_input")
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SourceSwatchPalette.take(8).forEach { hex ->
                        val color = parseHexColor(hex)
                        val selected = hex == selectedHex
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedHex = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, selectedHex) },
                enabled = name.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_create_source_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun ExportOptionsDialog(
    allTracks: List<GpxTrack>,
    sources: List<SourceCategory>,
    visibleTrackCount: Int,
    visibleDistanceKm: Double,
    renderedPoints: Int,
    rdpEnabled: Boolean,
    mergeExportItems: Boolean,
    onToggleMergeExportItems: (Boolean) -> Unit,
    onToggleTrackVisibility: (String) -> Unit,
    onToggleAllVisibility: (Boolean) -> Unit,
    onSaveKmz: () -> Unit,
    onSaveKml: () -> Unit,
    onSaveGpx: () -> Unit,
    onSaveGeoJson: () -> Unit,
    onShareKmz: () -> Unit,
    onShareGpx: () -> Unit,
    onDismiss: () -> Unit
) {
    val estimatedKmzKb = ((renderedPoints * 7) / 1024).coerceAtLeast(1)
    val sourcesById = remember(sources) { sources.associateBy { it.id } }
    val allChecked = allTracks.isNotEmpty() && visibleTrackCount == allTracks.size
    val anyChecked = visibleTrackCount > 0
    val masterState = when {
        allChecked -> ToggleableState.On
        anyChecked -> ToggleableState.Indeterminate
        else -> ToggleableState.Off
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Text(
                text = "Export Heatmap & Tracks",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = String.format(
                                Locale.US,
                                "%d/%d tracks • %.2f km • %d pts",
                                visibleTrackCount,
                                allTracks.size,
                                visibleDistanceKm,
                                renderedPoints
                            ),
                            style = TelemetryMonoStyle,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (visibleTrackCount == 0) {
                                "Select at least one track below to export."
                            } else {
                                "Est. KMZ size: ~$estimatedKmzKb KB (5 MB Google My Maps limit)" +
                                    if (rdpEnabled) " • RDP active" else ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (visibleTrackCount == 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // Exported Items Tick-Box Selector
                if (allTracks.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleAllVisibility(!allChecked) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("export_toggle_all_row"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TriStateCheckbox(
                                        state = masterState,
                                        onClick = { onToggleAllVisibility(!allChecked) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.primary,
                                            uncheckedColor = MaterialTheme.colorScheme.onSurface,
                                            checkmarkColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        modifier = Modifier.testTag("export_toggle_all_checkbox")
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (allChecked) "Hide All Exported Items" else "Show All Exported Items",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "$visibleTrackCount/${allTracks.size}",
                                    style = TelemetryMonoStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                            allTracks.forEach { track ->
                                val src = sourcesById[track.sourceId]
                                val dotColor = parseHexColor(src?.colorHex ?: "#E879F9")
                                val isChecked = track.isVisible && (src?.isVisible ?: true)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleTrackVisibility(track.id) }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        .testTag("export_item_row_${track.id}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { onToggleTrackVisibility(track.id) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = dotColor,
                                            uncheckedColor = MaterialTheme.colorScheme.onSurface,
                                            checkmarkColor = if (dotColor.luminance() > 0.5f) Color.Black else Color.White
                                        ),
                                        modifier = Modifier.testTag("export_item_checkbox_${track.id}")
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = track.trackName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isChecked) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = String.format(Locale.US, "%.1f km", track.distanceKm),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Merge All Exported Items Option
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (mergeExportItems) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = BorderStroke(
                        width = if (mergeExportItems) 1.5.dp else 1.dp,
                        color = if (mergeExportItems) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggleMergeExportItems(!mergeExportItems) }
                        .testTag("merge_export_items_row")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CallMerge,
                            contentDescription = null,
                            tint = if (mergeExportItems) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Merge all exported items",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (mergeExportItems) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Combine selected tracks into 1 single layer / track",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (mergeExportItems) {
                                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Switch(
                            checked = mergeExportItems,
                            onCheckedChange = onToggleMergeExportItems,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                                checkedTrackColor = MaterialTheme.colorScheme.secondary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.testTag("merge_export_items_switch")
                        )
                    }
                }

                Button(
                    onClick = onSaveKmz,
                    enabled = visibleTrackCount > 0,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("save_kmz_button")
                ) {
                    Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Google My Maps (.kmz • Compressed)", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onSaveKml,
                    enabled = visibleTrackCount > 0,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("save_kml_button")
                ) {
                    Icon(
                        Icons.Filled.Map,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Google My Maps KML (.kml)", color = MaterialTheme.colorScheme.tertiary)
                }

                Button(
                    onClick = onSaveGpx,
                    enabled = visibleTrackCount > 0,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("save_aggregated_gpx_button")
                ) {
                    Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aggregated GPX (.gpx v1.1)")
                }

                OutlinedButton(
                    onClick = onSaveGeoJson,
                    enabled = visibleTrackCount > 0,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("save_geojson_button")
                ) {
                    Icon(
                        Icons.Filled.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GeoJSON (.geojson)", color = MaterialTheme.colorScheme.secondary)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onShareKmz,
                        enabled = visibleTrackCount > 0,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("share_kmz_button")
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share KMZ", color = MaterialTheme.colorScheme.onSurface)
                    }
                    OutlinedButton(
                        onClick = onShareGpx,
                        enabled = visibleTrackCount > 0,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("share_gpx_button")
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share GPX", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
