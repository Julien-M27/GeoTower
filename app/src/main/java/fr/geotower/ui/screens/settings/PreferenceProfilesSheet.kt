package fr.geotower.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.geotower.R
import fr.geotower.ui.components.settingsPopupFadingEdge
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.PreferenceProfile
import fr.geotower.utils.PreferenceProfileChange
import fr.geotower.utils.PreferenceProfileImportPreview
import fr.geotower.utils.PreferenceProfileImportResolution
import fr.geotower.utils.PreferenceProfileManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferenceProfilesSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState,
    useOneUi: Boolean
) {
    val context = LocalContext.current
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val scrollState = rememberScrollState()

    var profiles by remember { mutableStateOf(PreferenceProfileManager.profiles(context)) }
    var activeProfileId by remember { mutableStateOf(PreferenceProfileManager.activeProfileId(context)) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var profileToDelete by remember { mutableStateOf<PreferenceProfile?>(null) }
    var profileToApply by remember { mutableStateOf<PreferenceProfile?>(null) }
    var importPreview by remember { mutableStateOf<PreferenceProfileImportPreview?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var exportIds by remember { mutableStateOf<Set<String>?>(null) }
    var showExportSelection by remember { mutableStateOf(false) }
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    var pendingImageProfileId by remember { mutableStateOf<String?>(null) }

    fun refreshProfiles() {
        profiles = PreferenceProfileManager.profiles(context)
        activeProfileId = PreferenceProfileManager.activeProfileId(context)
    }

    fun selectedProfiles(ids: Set<String>): List<PreferenceProfile> {
        return profiles.filter { it.id in ids }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val json = pendingExportJson ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                Toast.makeText(context, R.string.preference_profiles_export_saved, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, R.string.preference_profiles_export_failed, Toast.LENGTH_SHORT).show()
            }
        }
        pendingExportJson = null
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: error("empty")
        }.onSuccess { json ->
            runCatching {
                PreferenceProfileManager.parseImport(context, json)
            }.onSuccess { preview ->
                importPreview = preview
            }.onFailure {
                importError = context.getString(R.string.preference_profiles_import_failed)
            }
        }.onFailure {
            importError = context.getString(R.string.preference_profiles_import_failed)
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val profileId = pendingImageProfileId
        pendingImageProfileId = null
        if (uri == null || profileId == null) return@rememberLauncherForActivityResult
        val updated = PreferenceProfileManager.updateProfileImage(context, profileId, uri)
        if (updated) {
            refreshProfiles()
            Toast.makeText(context, R.string.preference_profiles_image_updated, Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .settingsPopupFadingEdge(scrollState)
                .verticalScroll(scrollState)
                .padding(
                    start = sizing.spacing(20.dp),
                    end = sizing.spacing(20.dp),
                    bottom = sizing.spacing(40.dp)
                )
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Text(
                    text = stringResource(R.string.preference_profiles_title),
                    style = sizing.textStyle(MaterialTheme.typography.titleLarge),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(sizing.spacing(48.dp)))
            }

            Text(
                text = stringResource(R.string.preference_profiles_desc),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(20.dp))
            )

            profiles.forEach { profile ->
                PreferenceProfileRow(
                    profile = profile,
                    active = profile.id == activeProfileId,
                    useOneUi = useOneUi,
                    onClick = {
                        if (profile.id != activeProfileId) profileToApply = profile
                    },
                    onImageClick = {
                        pendingImageProfileId = profile.id
                        imageLauncher.launch(arrayOf("image/*"))
                    },
                    onDelete = {
                        if (!profile.isDefault) profileToDelete = profile
                    }
                )
                Spacer(Modifier.height(sizing.spacing(10.dp)))
            }

            Spacer(Modifier.height(sizing.spacing(8.dp)))
            ProfileActionButton(
                title = stringResource(R.string.preference_profiles_create),
                desc = stringResource(R.string.preference_profiles_create_desc),
                icon = Icons.Default.Add,
                useOneUi = useOneUi,
                onClick = { showCreateDialog = true }
            )
            Spacer(Modifier.height(sizing.spacing(10.dp)))
            ProfileActionButton(
                title = stringResource(R.string.preference_profiles_import),
                desc = stringResource(R.string.preference_profiles_import_desc),
                icon = Icons.Default.FileUpload,
                useOneUi = useOneUi,
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
            )
            Spacer(Modifier.height(sizing.spacing(10.dp)))
            ProfileActionButton(
                title = stringResource(R.string.preference_profiles_export_active),
                desc = stringResource(R.string.preference_profiles_export_active_desc),
                icon = Icons.Default.Share,
                useOneUi = useOneUi,
                onClick = { exportIds = setOf(activeProfileId) }
            )
            Spacer(Modifier.height(sizing.spacing(10.dp)))
            ProfileActionButton(
                title = stringResource(R.string.preference_profiles_export_selected),
                desc = stringResource(R.string.preference_profiles_export_selected_desc),
                icon = Icons.Default.FileDownload,
                useOneUi = useOneUi,
                onClick = { showExportSelection = true }
            )
            Spacer(Modifier.height(sizing.spacing(10.dp)))
            ProfileActionButton(
                title = stringResource(R.string.preference_profiles_export_all),
                desc = stringResource(R.string.preference_profiles_export_all_desc),
                icon = Icons.Default.FileDownload,
                useOneUi = useOneUi,
                onClick = { exportIds = profiles.map { it.id }.toSet() }
            )
        }
    }

    if (showCreateDialog) {
        CreatePreferenceProfileDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, color, icon, imageUri ->
                val profile = PreferenceProfileManager.createProfileFromCurrentSettings(context, name, color, icon)
                if (imageUri != null) {
                    PreferenceProfileManager.updateProfileImage(context, profile.id, imageUri)
                }
                showCreateDialog = false
                refreshProfiles()
                Toast.makeText(context, R.string.preference_profiles_created, Toast.LENGTH_SHORT).show()
            }
        )
    }

    profileToDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text(stringResource(R.string.preference_profiles_delete_title)) },
            text = { Text(stringResource(R.string.preference_profiles_delete_desc, profile.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        PreferenceProfileManager.deleteProfile(context, profile.id)
                        profileToDelete = null
                        refreshProfiles()
                        Toast.makeText(context, R.string.preference_profiles_deleted, Toast.LENGTH_SHORT).show()
                    }
                ) { Text(stringResource(R.string.preference_profiles_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    profileToApply?.let { profile ->
        val changes = remember(profile.id, profiles, activeProfileId) {
            PreferenceProfileManager.profileChanges(context, profile)
        }
        ApplyPreferenceProfileDialog(
            profile = profile,
            changes = changes,
            onDismiss = { profileToApply = null },
            onApply = {
                PreferenceProfileManager.applyProfile(context, profile.id)
                profileToApply = null
                refreshProfiles()
                recreateHostActivity(context)
            }
        )
    }

    importPreview?.let { preview ->
        ImportPreferenceProfilesDialog(
            preview = preview,
            onDismiss = { importPreview = null },
            onImport = { resolution ->
                val result = PreferenceProfileManager.importProfiles(context, preview, resolution)
                importPreview = null
                refreshProfiles()
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.preference_profiles_import_success,
                        result.addedCount,
                        result.replacedCount
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                if (result.activeProfileChanged) {
                    recreateHostActivity(context)
                }
            }
        )
    }

    importError?.let { message ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text(stringResource(R.string.preference_profiles_import_error_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { importError = null }) {
                    Text(stringResource(R.string.common_validate))
                }
            }
        )
    }

    exportIds?.let { ids ->
        val exportProfiles = selectedProfiles(ids)
        val fileName = PreferenceProfileManager.suggestedExportFileName(exportProfiles)
        AlertDialog(
            onDismissRequest = { exportIds = null },
            title = { Text(stringResource(R.string.preference_profiles_export_choice_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.preference_profiles_export_choice_desc,
                        exportProfiles.size
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val json = PreferenceProfileManager.exportProfilesJson(context, ids)
                        PreferenceProfileManager.shareExport(context, json, fileName)
                        exportIds = null
                    }
                ) { Text(stringResource(R.string.preference_profiles_share)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingExportJson = PreferenceProfileManager.exportProfilesJson(context, ids)
                        exportIds = null
                        saveLauncher.launch(fileName)
                    }
                ) { Text(stringResource(R.string.preference_profiles_save)) }
            }
        )
    }

    if (showExportSelection) {
        ExportSelectionDialog(
            profiles = profiles,
            initiallySelected = setOf(activeProfileId),
            onDismiss = { showExportSelection = false },
            onContinue = { ids ->
                showExportSelection = false
                exportIds = ids
            }
        )
    }
}

@Composable
private fun PreferenceProfileRow(
    profile: PreferenceProfile,
    active: Boolean,
    useOneUi: Boolean,
    onClick: () -> Unit,
    onImageClick: () -> Unit,
    onDelete: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val shape = RoundedCornerShape(if (useOneUi) sizing.component(24.dp) else sizing.component(12.dp))
    val cardColor = if (useOneUi) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Transparent
    val border = if (active) {
        BorderStroke(sizing.component(2.dp), MaterialTheme.colorScheme.primary)
    } else if (useOneUi) {
        null
    } else {
        BorderStroke(sizing.component(1.dp), MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = cardColor,
        shape = shape,
        border = border
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(sizing.spacing(14.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileIconBadge(profile = profile)
            Spacer(Modifier.width(sizing.spacing(14.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (active) stringResource(R.string.preference_profiles_active) else stringResource(R.string.preference_profiles_tap_to_apply),
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (active) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(sizing.component(24.dp))
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(sizing.component(24.dp))
                )
            }
            Spacer(Modifier.width(sizing.spacing(4.dp)))
            IconButton(onClick = onImageClick) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!profile.isDefault) {
                Spacer(Modifier.width(sizing.spacing(4.dp)))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileActionButton(
    title: String,
    desc: String,
    icon: ImageVector,
    useOneUi: Boolean,
    onClick: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val shape = RoundedCornerShape(if (useOneUi) sizing.component(22.dp) else sizing.component(12.dp))
    val cardColor = if (useOneUi) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else Color.Transparent
    val border = if (useOneUi) null else BorderStroke(sizing.component(1.dp), MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = cardColor, shape = shape, border = border) {
        Row(modifier = Modifier.fillMaxWidth().padding(sizing.spacing(16.dp)), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(sizing.spacing(14.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                Text(desc, style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProfileIconBadge(profile: PreferenceProfile) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val color = Color(profile.colorArgb)
    val imageFile = remember(profile.imagePath) {
        profile.imagePath?.let(::File)?.takeIf { it.exists() && it.isFile }
    }
    Box(
        modifier = Modifier
            .size(sizing.component(46.dp))
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        if (imageFile != null) {
            AsyncImage(
                model = imageFile,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = profileIcon(profile.icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(sizing.component(24.dp))
            )
        }
    }
}

@Composable
private fun CreatePreferenceProfileDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Int, String, Uri?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(profileColors.first()) }
    var selectedIcon by remember { mutableStateOf(profileIcons.first()) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val scrollState = rememberScrollState()
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preference_profiles_create)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.preference_profiles_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.preference_profiles_color_label), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    profileColors.forEach { colorInt ->
                        val color = Color(colorInt)
                        Surface(
                            onClick = { selectedColor = colorInt },
                            modifier = Modifier.padding(end = 10.dp).size(44.dp),
                            shape = CircleShape,
                            color = color.copy(alpha = 0.18f),
                            border = BorderStroke(
                                if (selectedColor == colorInt) 3.dp else 1.dp,
                                if (selectedColor == colorInt) color else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (selectedColor == colorInt) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = color)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.preference_profiles_icon_label), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    profileIcons.forEach { icon ->
                        Surface(
                            onClick = { selectedIcon = icon },
                            modifier = Modifier.padding(end = 10.dp).size(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = if (selectedIcon == icon) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(profileIcon(icon), contentDescription = null)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.preference_profiles_image_label), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = { imageLauncher.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(selectedColor).copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedImageUri != null) {
                                AsyncImage(
                                    model = selectedImageUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = null,
                                    tint = Color(selectedColor)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(
                                if (selectedImageUri == null) {
                                    R.string.preference_profiles_image_select
                                } else {
                                    R.string.preference_profiles_image_change
                                }
                            ),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, selectedColor, selectedIcon, selectedImageUri) }) {
                Text(stringResource(R.string.preference_profiles_create_short))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun ApplyPreferenceProfileDialog(
    profile: PreferenceProfile,
    changes: List<PreferenceProfileChange>,
    onDismiss: () -> Unit,
    onApply: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preference_profiles_apply_title, profile.name)) },
        text = {
            if (changes.isEmpty()) {
                Text(stringResource(R.string.preference_profiles_no_changes))
            } else {
                ChangePreviewList(changes = changes)
            }
        },
        confirmButton = {
            TextButton(onClick = onApply) {
                Text(stringResource(R.string.preference_profiles_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun ChangePreviewList(changes: List<PreferenceProfileChange>) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(scrollState)) {
        Text(
            text = stringResource(R.string.preference_profiles_changes_count, changes.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        changes.groupBy { it.section }.forEach { (section, sectionChanges) ->
            Text(section, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            sectionChanges.forEach { change ->
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Text(change.label, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = stringResource(R.string.preference_profiles_change_from, change.oldValue),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.preference_profiles_change_to, change.newValue),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ImportPreferenceProfilesDialog(
    preview: PreferenceProfileImportPreview,
    onDismiss: () -> Unit,
    onImport: (PreferenceProfileImportResolution) -> Unit
) {
    val hasConflicts = preview.conflicts.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preference_profiles_import_preview_title)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.preference_profiles_import_preview_desc, preview.profiles.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                preview.profiles.forEach { profile ->
                    Text("• ${profile.name}", fontWeight = FontWeight.SemiBold)
                }
                if (hasConflicts) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.preference_profiles_import_conflicts_title),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    preview.conflicts.forEach { conflict ->
                        Text(
                            stringResource(
                                R.string.preference_profiles_import_conflict_item,
                                conflict.importedProfile.name
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (hasConflicts) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onImport(PreferenceProfileImportResolution.ReplaceExisting) }) {
                        Text(stringResource(R.string.preference_profiles_replace))
                    }
                    Button(onClick = { onImport(PreferenceProfileImportResolution.RenameImported) }) {
                        Text(stringResource(R.string.preference_profiles_rename_import))
                    }
                }
            } else {
                Button(onClick = { onImport(PreferenceProfileImportResolution.RenameImported) }) {
                    Text(stringResource(R.string.preference_profiles_import_apply))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun ExportSelectionDialog(
    profiles: List<PreferenceProfile>,
    initiallySelected: Set<String>,
    onDismiss: () -> Unit,
    onContinue: (Set<String>) -> Unit
) {
    var selection by remember { mutableStateOf(initiallySelected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preference_profiles_export_selected)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                profiles.forEach { profile ->
                    Surface(
                        onClick = {
                            selection = if (profile.id in selection) selection - profile.id else selection + profile.id
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = profile.id in selection,
                                onCheckedChange = { checked ->
                                    selection = if (checked) selection + profile.id else selection - profile.id
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            ProfileIconBadge(profile)
                            Spacer(Modifier.width(12.dp))
                            Text(profile.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selection.isNotEmpty(),
                onClick = { onContinue(selection) }
            ) {
                Text(stringResource(R.string.common_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

private val profileColors = listOf(
    0xFF2563EB.toInt(),
    0xFF059669.toInt(),
    0xFFDC2626.toInt(),
    0xFF9333EA.toInt(),
    0xFFF59E0B.toInt(),
    0xFF0F766E.toInt()
)

private val profileIcons = listOf("settings", "tune", "map", "palette", "share", "star")

private fun profileIcon(icon: String): ImageVector {
    return when (icon) {
        "tune" -> Icons.Default.Tune
        "map" -> Icons.Default.Map
        "palette" -> Icons.Default.Palette
        "share" -> Icons.Default.Share
        "star" -> Icons.Default.Star
        else -> Icons.Default.Settings
    }
}

private fun recreateHostActivity(context: Context) {
    var current = context
    while (current is ContextWrapper && current !is Activity) {
        current = current.baseContext
    }
    (current as? Activity)?.recreate()
}
