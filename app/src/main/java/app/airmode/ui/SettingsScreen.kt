@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.airmode.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.airmode.BuildConfig
import app.airmode.R
import app.airmode.data.Settings
import app.airmode.domain.Mode

@Composable
fun SettingsScreen(settings: Settings, hasAnc: Boolean, onBack: () -> Unit,
                   onPopup: (Boolean) -> Unit, onPersistent: (Boolean) -> Unit,
                   onAutoStart: (Boolean) -> Unit, onModes: (Set<Mode>) -> Unit,
                   onLanguage: (String) -> Unit, onAddTile: () -> Unit, onAddWidget: () -> Unit, onRepository: () -> Unit,
                   onDiagnostics: () -> Unit = {}) {
    var versionTaps by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TextButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.back))
        }
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            SettingToggle(R.string.popup_setting, settings.popup, onPopup, 0)
            SettingToggle(R.string.persistent_setting, settings.persistent, onPersistent, 1, R.string.persistent_detail)
            SettingToggle(R.string.autostart_setting, settings.autoStart, onAutoStart, 2)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.tile_modes), style = MaterialTheme.typography.titleMedium)
            if (hasAnc) {
                Text(stringResource(R.string.tile_modes_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    Mode.entries.forEachIndexed { index, mode ->
                        val checked = mode in settings.tileModes
                        val canChange = !checked || settings.tileModes.size > 2
                        SegmentedListItem(checked = checked, enabled = canChange,
                            onCheckedChange = { onModes(if (checked) settings.tileModes - mode else settings.tileModes + mode) },
                            shapes = ListItemDefaults.segmentedShapes(index, Mode.entries.size),
                            trailingContent = { Checkbox(checked = checked, enabled = canChange, onCheckedChange = null) }) {
                            Text(stringResource(mode.label()))
                        }
                    }
                }
            } else Text(stringResource(R.string.no_anc))
            FilledTonalButton(onClick = onAddTile, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(painterResource(R.drawable.ic_noise_anc), contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_tile))
            }
            FilledTonalButton(onClick = onAddWidget, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(painterResource(R.drawable.ic_airmode), contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_widget))
            }
            Text(stringResource(R.string.widget_detail), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                listOf("system" to R.string.language_system, "ru" to R.string.language_ru, "en" to R.string.language_en)
                    .forEachIndexed { index, (language, label) ->
                        SegmentedListItem(selected = settings.language == language, onClick = { onLanguage(language) },
                            shapes = ListItemDefaults.segmentedShapes(index, 3),
                            trailingContent = { RadioButton(selected = settings.language == language, onClick = null) }) {
                            Text(stringResource(label))
                        }
                    }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.about), style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                Surface(shape = ListItemDefaults.segmentedShapes(0, 3).shape,
                    modifier = if (BuildConfig.DEBUG) Modifier.clickable {
                        versionTaps++
                        if (versionTaps == 7) { versionTaps = 0; onDiagnostics() }
                    } else Modifier) {
                    ListItem(headlineContent = { Text(stringResource(R.string.version, BuildConfig.VERSION_NAME)) },
                        supportingContent = { Text(stringResource(R.string.license)) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer))
                }
                SegmentedListItem(onClick = onRepository, shapes = ListItemDefaults.segmentedShapes(1, 3)) {
                    Text(stringResource(R.string.repository))
                }
                Surface(shape = ListItemDefaults.segmentedShapes(2, 3).shape) {
                    ListItem(headlineContent = { Text(stringResource(R.string.not_apple), style = MaterialTheme.typography.bodySmall) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SettingToggle(label: Int, checked: Boolean, onChange: (Boolean) -> Unit, index: Int, detail: Int? = null) {
    SegmentedListItem(checked = checked, onCheckedChange = onChange,
        shapes = ListItemDefaults.segmentedShapes(index, 3),
        supportingContent = detail?.let { { Text(stringResource(it)) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) }) {
        Text(stringResource(label))
    }
}
