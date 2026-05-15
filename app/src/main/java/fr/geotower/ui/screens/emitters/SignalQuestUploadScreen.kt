package fr.geotower.ui.screens.emitters

// NOUVEAUX IMPORTS POUR LA CARTE
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.net.Uri // 🚨 NOUVEAU
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Add // 🚨 NOUVEAU
import androidx.compose.material.icons.filled.PhotoLibrary // 🚨 NOUVEAU
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton // 🚨 NOUVEAU
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
// The crucial imports that fix the delegation error:
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.components.oneUiActionButtonShape
import fr.geotower.data.upload.SignalQuestUploadQueue
import fr.geotower.data.upload.SignalQuestUploadRules
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings
import fr.geotower.utils.MapUtils
import fr.geotower.utils.isNetworkAvailable
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import fr.geotower.data.models.LocalisationEntity
import org.osmdroid.mapsforge.MapsForgeTileProvider
import org.osmdroid.mapsforge.MapsForgeTileSource
import org.mapsforge.map.rendertheme.InternalRenderTheme
import org.osmdroid.tileprovider.MapTileProviderBasic
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalQuestUploadScreen(
    imageUris: List<String>,
    siteId: String,
    operatorName: String,
    lat: Double,
    lon: Double,
    azimuts: String,
    onNavigateBack: () -> Unit,
    onStartUpload: (List<String>, String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val safeClick = rememberSafeClick()
    var isBackNavigationLocked by remember { mutableStateOf(false) }

    fun handleBackNavigation() {
        if (isBackNavigationLocked) return
        isBackNavigationLocked = true
        val didNavigate = runCatching { onNavigateBack() }.isSuccess
        if (!didNavigate) {
            isBackNavigationLocked = false
        }
    }

    BackHandler(enabled = !isBackNavigationLocked) {
        handleBackNavigation()
    }

    // --- 1. ÉTATS ---
    val currentUris = remember { mutableStateListOf<String>().apply { addAll(imageUris) } }
    var description by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showImageSourceDialog by remember { mutableStateOf(false) }

    fun addSelectedUris(uris: List<Uri>) {
        if (uris.isNotEmpty()) {
            val newUris = uris.map { it.toString() }.filter { !currentUris.contains(it) }
            val availableSlots = SignalQuestUploadRules.MAX_PHOTOS - currentUris.size
            if (availableSlots > 0) {
                currentUris.addAll(newUris.take(availableSlots))
            }
        }
    }

    val density = LocalDensity.current
    val photoCardWidth = 240.dp
    val photoCardHeight = 280.dp
    val photoSpacing = 12.dp
    val addPhotoCardWidth = 100.dp
    val photoCardWidthPx = with(density) { photoCardWidth.toPx() }
    val photoSpacingPx = with(density) { photoSpacing.toPx() }
    val addPhotoCardWidthPx = with(density) { addPhotoCardWidth.toPx() }
    val photoReorderStepPx = photoCardWidthPx + photoSpacingPx
    val addPhotoPullThresholdPx = with(density) { 72.dp.toPx() }
    val addPhotoPullMaxPx = with(density) { 120.dp.toPx() }
    var draggedPhotoUri by remember { mutableStateOf<String?>(null) }
    var draggedPhotoOffsetX by remember { mutableStateOf(0f) }
    var draggedPhotoStartViewportX by remember { mutableStateOf(0f) }
    var draggedPhotoStartScrollX by remember { mutableStateOf(0f) }
    var draggedPhotoStartIndex by remember { mutableIntStateOf(-1) }
    var draggedPhotoTargetIndex by remember { mutableIntStateOf(-1) }
    var draggedPhotoHasMoved by remember { mutableStateOf(false) }
    var dragAutoScrollVelocityPx by remember { mutableStateOf(0f) }
    var addPhotoPullPx by remember { mutableStateOf(0f) }
    val addPhotoPullProgress by animateFloatAsState(
        targetValue = if (showImageSourceDialog) {
            1f
        } else {
            (addPhotoPullPx / addPhotoPullThresholdPx).coerceIn(0f, 1f)
        },
        label = "add-photo-pull-progress"
    )
    val addPhotoExpandedWidth = 76.dp
    val addPhotoExpandedWidthPx = with(density) { addPhotoExpandedWidth.toPx() }
    val addPhotoPushPx = addPhotoPullProgress * addPhotoExpandedWidthPx

    fun resetAddPhotoPull() {
        addPhotoPullPx = 0f
    }

    fun openImageSourceFromPull() {
        addPhotoPullPx = addPhotoPullThresholdPx
        if (!showImageSourceDialog) {
            showImageSourceDialog = true
        }
    }

    fun updateDraggedPhotoTarget(currentScrollX: Float) {
        if (draggedPhotoStartIndex >= 0 && currentUris.size > 1) {
            val scrollDelta = currentScrollX - draggedPhotoStartScrollX
            draggedPhotoTargetIndex = (
                draggedPhotoStartIndex + ((draggedPhotoOffsetX + scrollDelta) / photoReorderStepPx).roundToInt()
            ).coerceIn(0, currentUris.lastIndex)
        }
    }

    fun clearPhotoDrag() {
        draggedPhotoUri = null
        draggedPhotoOffsetX = 0f
        draggedPhotoStartViewportX = 0f
        draggedPhotoStartScrollX = 0f
        draggedPhotoStartIndex = -1
        draggedPhotoTargetIndex = -1
        draggedPhotoHasMoved = false
        dragAutoScrollVelocityPx = 0f
    }

    fun finishPhotoDrag() {
        val uri = draggedPhotoUri
        val targetIndex = draggedPhotoTargetIndex
        if (uri != null && targetIndex >= 0) {
            val currentIndex = currentUris.indexOf(uri)
            if (currentIndex >= 0 && currentIndex != targetIndex) {
                val movedUri = currentUris.removeAt(currentIndex)
                currentUris.add(targetIndex.coerceIn(0, currentUris.size), movedUri)
            }
        }
        clearPhotoDrag()
    }

    // Pour la carte
    val ignStyle by AppConfig.ignStyle

    // ✅ NOUVEAU : Fournisseur effectif calculé une seule fois au chargement
    var effectiveProvider by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(AppConfig.mapProvider.intValue) }
    var mapFiles by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptyArray<java.io.File>()) }

    androidx.compose.runtime.LaunchedEffect(AppConfig.mapProvider.intValue) {
        effectiveProvider = AppConfig.mapProvider.intValue
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val offlineDir = java.io.File(context.getExternalFilesDir(null), "maps")
        val files = offlineDir.listFiles { file -> file.extension == "map" && file.length() > 0L } ?: emptyArray()
        mapFiles = files

        // Si hors-ligne ET présence de fichiers : on bascule.
        if (!isNetworkAvailable(context) && files.isNotEmpty()) {
            effectiveProvider = 4
        }
    }

    // --- 1.5 LANCEURS GALERIE ET CAMERA ---
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = SignalQuestUploadRules.MAX_PHOTOS)
    ) { uris ->
        addSelectedUris(uris)
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        addSelectedUris(uris)
    }

    var currentCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentCameraUri != null) {
            if (currentUris.size < 10) {
                currentUris.add(currentCameraUri.toString())
            }
        }
    }

    fun createCameraUri(): Uri {
        return SignalQuestUploadQueue.createCameraUri(context)
    }

    // Thème et couleurs
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val useOneUi = AppConfig.useOneUiDesign
    val isDark = (themeMode == 2) || (themeMode == 0 && androidx.compose.foundation.isSystemInDarkTheme())

    // --- NOUVEAU : FORMES DYNAMIQUES SELON LE MODE ---
    val photoShape = if (useOneUi) RoundedCornerShape(32.dp) else RoundedCornerShape(24.dp)
    val blockShape = if (useOneUi) RoundedCornerShape(24.dp) else RoundedCornerShape(16.dp)
    val buttonShape = oneUiActionButtonShape(useOneUi, RoundedCornerShape(16.dp))
    val mapShape = if (useOneUi) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)

    val effectiveOled = isOledMode && isDark
    val activeColor = MaterialTheme.colorScheme.primary
    val backgroundColor = when { effectiveOled -> Color.Black; isDark -> Color(0xFF141414); else -> MaterialTheme.colorScheme.background }
    val surfaceColor = when { effectiveOled -> Color(0xFF222222); isDark -> Color(0xFF2C2C2C); else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) }
    val dialogContainerColor = when {
        effectiveOled -> Color(0xFF222222)
        isDark -> Color(0xFF2C2C2C)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val textColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .padding(top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { handleBackNavigation() },
                    enabled = !isBackNavigationLocked,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = AppStrings.back,
                        tint = textColor
                    )
                }
                Text(
                    text = AppStrings.uploadSqTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.width(48.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- 2. CARROUSEL OU IMAGE CENTRÉE ---
            if (currentUris.isEmpty()) {
                Card(
                    modifier = Modifier
                        .size(width = 240.dp, height = 280.dp)
                        .clip(photoShape)
                        .clickable { safeClick { showImageSourceDialog = true } },
                    shape = photoShape,
                    colors = CardDefaults.cardColors(containerColor = surfaceColor.copy(alpha = 0.3f)),
                    border = BorderStroke(2.dp, activeColor.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(48.dp), tint = activeColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = AppStrings.addPhotos,
                            color = textColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                val carouselScrollState = rememberScrollState()
                val carouselContentWidth = with(density) {
                    val photoSlotsWidth = (photoCardWidthPx * currentUris.size) +
                        (photoSpacingPx * (currentUris.size - 1).coerceAtLeast(0))
                    val addSlotWidth = if (currentUris.size < SignalQuestUploadRules.MAX_PHOTOS) {
                        photoSpacingPx + addPhotoCardWidthPx
                    } else {
                        0f
                    }
                    (photoSlotsWidth + addSlotWidth).toDp()
                }
                val addPhotoPullConnection = remember(carouselScrollState, currentUris.size) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (showImageSourceDialog) return Offset.Zero
                            if (source != NestedScrollSource.UserInput || addPhotoPullPx <= 0f) return Offset.Zero
                            if (available.x <= 0f) return Offset.Zero

                            val shrink = minOf(addPhotoPullPx, available.x)
                            addPhotoPullPx -= shrink
                            return Offset(x = shrink, y = 0f)
                        }

                        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                            if (showImageSourceDialog) {
                                addPhotoPullPx = addPhotoPullThresholdPx
                                return Offset.Zero
                            }
                            if (source != NestedScrollSource.UserInput || currentUris.size >= SignalQuestUploadRules.MAX_PHOTOS) {
                                resetAddPhotoPull()
                                return Offset.Zero
                            }

                            val isAtEnd = carouselScrollState.value >= carouselScrollState.maxValue - 1
                            if (!isAtEnd && addPhotoPullPx <= 0f) {
                                return Offset.Zero
                            }

                            val leftPull = -available.x
                            return when {
                                leftPull > 0f -> {
                                    addPhotoPullPx = (addPhotoPullPx + leftPull).coerceIn(0f, addPhotoPullMaxPx)
                                    if (addPhotoPullPx >= addPhotoPullThresholdPx) {
                                        openImageSourceFromPull()
                                    }
                                    Offset(x = available.x, y = 0f)
                                }
                                available.x > 0f && addPhotoPullPx > 0f -> {
                                    val shrink = minOf(addPhotoPullPx, available.x)
                                    addPhotoPullPx -= shrink
                                    Offset(x = shrink, y = 0f)
                                }
                                else -> Offset.Zero
                            }
                        }

                        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                            if (showImageSourceDialog) {
                                addPhotoPullPx = addPhotoPullThresholdPx
                                return Velocity.Zero
                            }
                            if (addPhotoPullPx >= addPhotoPullThresholdPx * 0.45f) {
                                openImageSourceFromPull()
                            } else {
                                resetAddPhotoPull()
                            }
                            return Velocity.Zero
                        }
                    }
                }

                fun currentCarouselScrollX(): Float {
                    return carouselScrollState.value.toFloat()
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(photoCardHeight)
                        .nestedScroll(addPhotoPullConnection)
                ) {
                    val viewportWidthPx = with(density) { maxWidth.toPx() }
                    val autoScrollEdgePx = with(density) { 88.dp.toPx() }
                    val dragAutoScrollActivationPx = with(density) { 16.dp.toPx() }
                    val maxAutoScrollVelocityPx = with(density) { 7.dp.toPx() }

                    fun updateDragAutoScroll() {
                        if (draggedPhotoStartIndex < 0 || draggedPhotoUri == null || !draggedPhotoHasMoved) {
                            dragAutoScrollVelocityPx = 0f
                            return
                        }

                        val draggedLeftInViewport = draggedPhotoStartViewportX + draggedPhotoOffsetX
                        val draggedRightInViewport = draggedLeftInViewport + photoCardWidthPx
                        dragAutoScrollVelocityPx = when {
                            draggedRightInViewport > viewportWidthPx - autoScrollEdgePx -> {
                                val edgeProgress = (
                                    (draggedRightInViewport - (viewportWidthPx - autoScrollEdgePx)) /
                                        autoScrollEdgePx
                                ).coerceIn(0f, 1f)
                                edgeProgress * edgeProgress * maxAutoScrollVelocityPx
                            }
                            draggedLeftInViewport < autoScrollEdgePx -> {
                                val edgeProgress = ((autoScrollEdgePx - draggedLeftInViewport) / autoScrollEdgePx)
                                    .coerceIn(0f, 1f)
                                -edgeProgress * edgeProgress * maxAutoScrollVelocityPx
                            }
                            else -> 0f
                        }
                    }

                    LaunchedEffect(draggedPhotoUri) {
                        while (draggedPhotoUri != null) {
                            updateDragAutoScroll()
                            val velocity = dragAutoScrollVelocityPx
                            if (velocity != 0f) {
                                val consumedScroll = carouselScrollState.scrollBy(velocity)
                                if (abs(consumedScroll) < 0.5f) {
                                    dragAutoScrollVelocityPx = 0f
                                } else {
                                    updateDraggedPhotoTarget(currentCarouselScrollX())
                                }
                            }
                            delay(16L)
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxSize()
                            .horizontalScroll(carouselScrollState)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(carouselContentWidth)
                                .height(photoCardHeight)
                        ) {
                            currentUris.forEachIndexed { index, uri ->
                                val isDragging = draggedPhotoUri == uri
                                val targetShift = when {
                                    isDragging || draggedPhotoStartIndex < 0 || draggedPhotoTargetIndex < 0 -> 0f
                                    draggedPhotoTargetIndex > draggedPhotoStartIndex &&
                                        index > draggedPhotoStartIndex &&
                                        index <= draggedPhotoTargetIndex -> -photoReorderStepPx
                                    draggedPhotoTargetIndex < draggedPhotoStartIndex &&
                                        index >= draggedPhotoTargetIndex &&
                                        index < draggedPhotoStartIndex -> photoReorderStepPx
                                    else -> 0f
                                }
                                val animatedShift by animateFloatAsState(
                                    targetValue = targetShift,
                                    animationSpec = tween(durationMillis = 180),
                                    label = "photo-reorder-shift"
                                )

                                Box(
                                    modifier = Modifier
                                        .offset {
                                            IntOffset(
                                                x = ((index * photoReorderStepPx) + animatedShift - addPhotoPushPx).roundToInt(),
                                                y = 0
                                            )
                                        }
                                        .size(width = photoCardWidth, height = photoCardHeight)
                                        .graphicsLayer {
                                            alpha = if (isDragging) 0f else 1f
                                            shape = photoShape
                                            clip = true
                                        }
                                        .pointerInput(uri, currentUris.size) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    val currentScroll = currentCarouselScrollX()
                                                    draggedPhotoUri = uri
                                                    draggedPhotoStartIndex = currentUris.indexOf(uri)
                                                    draggedPhotoTargetIndex = draggedPhotoStartIndex
                                                    draggedPhotoStartScrollX = currentScroll
                                                    draggedPhotoStartViewportX =
                                                        draggedPhotoStartIndex * photoReorderStepPx - currentScroll
                                                    draggedPhotoOffsetX = 0f
                                                    draggedPhotoHasMoved = false
                                                    dragAutoScrollVelocityPx = 0f
                                                },
                                                onDragEnd = { finishPhotoDrag() },
                                                onDragCancel = { clearPhotoDrag() },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    if (draggedPhotoStartIndex >= 0) {
                                                        draggedPhotoOffsetX += dragAmount.x
                                                        if (!draggedPhotoHasMoved && abs(draggedPhotoOffsetX) >= dragAutoScrollActivationPx) {
                                                            draggedPhotoHasMoved = true
                                                        }
                                                        updateDraggedPhotoTarget(currentCarouselScrollX())
                                                        updateDragAutoScroll()
                                                    }
                                                }
                                            )
                                        }
                                ) {
                                    Card(
                                        modifier = Modifier.fillMaxSize(),
                                        shape = photoShape,
                                        colors = CardDefaults.cardColors(containerColor = surfaceColor)
                                    ) {
                                        AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    }
                                    // Bouton de suppression avec marge dynamique
                                    IconButton(
                                        onClick = { safeClick { currentUris.remove(uri) } },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            // NOUVEAU : 16.dp pour OneUI (radius 32), 8.dp pour Classique (radius 24)
                                            .padding(if (useOneUi) 16.dp else 8.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            .size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                    PhotoPositionBadge(
                                        position = index + 1,
                                        total = currentUris.size,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(if (useOneUi) 16.dp else 8.dp)
                                    )
                                }
                            }

                            if (currentUris.size < SignalQuestUploadRules.MAX_PHOTOS) {
                                val addPhotoExtraWidth = addPhotoExpandedWidth * addPhotoPullProgress
                                val addPhotoIconScale = 1f + (addPhotoPullProgress * 0.25f)
                                val addPhotoBorderWidth = 2.dp + (2.dp * addPhotoPullProgress)
                                Card(
                                    modifier = Modifier
                                        .offset {
                                            IntOffset(
                                                x = ((currentUris.size * photoReorderStepPx) - addPhotoPushPx).roundToInt(),
                                                y = 0
                                            )
                                        }
                                        .size(width = addPhotoCardWidth + addPhotoExtraWidth, height = photoCardHeight)
                                        .clip(photoShape)
                                        .clickable { safeClick { showImageSourceDialog = true } },
                                    shape = photoShape,
                                    colors = CardDefaults.cardColors(containerColor = surfaceColor.copy(alpha = 0.3f)),
                                    border = BorderStroke(addPhotoBorderWidth, activeColor.copy(alpha = 0.5f + (0.35f * addPhotoPullProgress)))
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            null,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .graphicsLayer {
                                                    scaleX = addPhotoIconScale
                                                    scaleY = addPhotoIconScale
                                                },
                                            tint = activeColor
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val draggedUri = draggedPhotoUri
                    if (draggedUri != null && draggedPhotoStartIndex >= 0) {
                        val draggedPosition = (
                            if (draggedPhotoTargetIndex >= 0) draggedPhotoTargetIndex else draggedPhotoStartIndex
                        ).coerceIn(0, currentUris.lastIndex) + 1
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        x = (draggedPhotoStartViewportX + draggedPhotoOffsetX).roundToInt(),
                                        y = 0
                                    )
                                }
                                .size(width = photoCardWidth, height = photoCardHeight)
                                .zIndex(1f)
                                .graphicsLayer {
                                    shape = photoShape
                                    clip = true
                                    val dragScale = 1.03f
                                    scaleX = dragScale
                                    scaleY = dragScale
                                }
                        ) {
                            Card(
                                modifier = Modifier.fillMaxSize(),
                                shape = photoShape,
                                colors = CardDefaults.cardColors(containerColor = surfaceColor)
                            ) {
                                AsyncImage(model = draggedUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                            IconButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(if (useOneUi) 16.dp else 8.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            PhotoPositionBadge(
                                position = draggedPosition,
                                total = currentUris.size,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(if (useOneUi) 16.dp else 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- 3. CHAMP DESCRIPTION ---
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(AppStrings.uploadSqDescPlaceholder, color = textColor.copy(alpha = 0.5f)) },
                minLines = 3, shape = blockShape,
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = surfaceColor, focusedContainerColor = surfaceColor, unfocusedTextColor = textColor, focusedTextColor = textColor)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Récapitulatif
            Card(modifier = Modifier.fillMaxWidth(), shape = blockShape, colors = CardDefaults.cardColors(containerColor = surfaceColor)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = activeColor)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("${AppStrings.uploadSqTargetSite} $siteId", fontWeight = FontWeight.Bold, color = textColor)
                        Text("${AppStrings.uploadSqTargetOperator} $operatorName", fontSize = 14.sp, color = textColor.copy(alpha = 0.7f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- 4. BOUTON D'ENVOI ---
            Button(
                onClick = {
                    safeClick {
                        if (currentUris.isNotEmpty()) {
                            showConfirmDialog = true
                        }
                    }
                },
                enabled = currentUris.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = buttonShape,
                colors = ButtonDefaults.buttonColors(containerColor = activeColor)
            ) {
                Icon(Icons.Default.CloudUpload, null)
                Spacer(Modifier.width(8.dp))
                Text(AppStrings.uploadSqButton(currentUris.size), fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(AppStrings.uploadSqLimit, fontSize = 12.sp, color = textColor.copy(alpha = 0.5f))
        }

        if (showImageSourceDialog) {
            AlertDialog(
                onDismissRequest = {
                    showImageSourceDialog = false
                    resetAddPhotoPull()
                },
                shape = blockShape,
                containerColor = dialogContainerColor,
                title = { Text(AppStrings.addPhotos, fontWeight = FontWeight.Bold, color = textColor) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = {
                                safeClick {
                                    showImageSourceDialog = false
                                    resetAddPhotoPull()
                                    val uri = createCameraUri()
                                    currentCameraUri = uri
                                    cameraLauncher.launch(uri)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = buttonShape,
                            colors = ButtonDefaults.buttonColors(containerColor = activeColor)
                        ) {
                            Icon(Icons.Default.PhotoCamera, null)
                            Spacer(Modifier.width(8.dp))
                            Text(AppStrings.camera, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                safeClick {
                                    showImageSourceDialog = false
                                    resetAddPhotoPull()
                                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = buttonShape,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                            border = BorderStroke(1.dp, textColor.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null)
                            Spacer(Modifier.width(8.dp))
                            Text(AppStrings.gallery, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                safeClick {
                                    showImageSourceDialog = false
                                    resetAddPhotoPull()
                                    documentPickerLauncher.launch(arrayOf("image/*"))
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = buttonShape,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                            border = BorderStroke(1.dp, textColor.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Default.FolderOpen, null)
                            Spacer(Modifier.width(8.dp))
                            Text(AppStrings.externalPhotoFiles, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // --- 5. POP-UP DE CONFIRMATION AVEC LA CARTE ---
        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                shape = blockShape,
                title = { Text(text = AppStrings.uploadConfirmTitle, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(text = AppStrings.uploadConfirmMessage(currentUris.size))

                        // NOUVEAU : LA MINI-CARTE DANS LE POP-UP
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(mapShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), mapShape)
                        ) {
                            // ✅ 1. On prépare l'antenne et les couleurs avant la carte
                            val rawPrimaryColor = MaterialTheme.colorScheme.primary.toArgb()
                            val isColorTooLight = androidx.core.graphics.ColorUtils.calculateLuminance(rawPrimaryColor) > 0.85
                            val safePrimaryColor = if (isColorTooLight) android.graphics.Color.parseColor("#2196F3") else rawPrimaryColor

                            val mappedAntennas = remember(siteId, operatorName, lat, lon, azimuts) {
                                listOf(LocalisationEntity(
                                    idAnfr = siteId,
                                    operateur = operatorName,
                                    latitude = lat,
                                    longitude = lon,
                                    azimuts = azimuts,
                                    codeInsee = null,
                                    azimutsFh = null,
                                    techMask = 0,
                                    bandMask = 0
                                ))
                            }

                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    MapView(ctx).apply {
                                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                                        setMultiTouchControls(false)
                                        setOnTouchListener { _, _ -> true } // Bloque le tactile manuel
                                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                                        controller.setZoom(17.5)
                                        controller.setCenter(GeoPoint(lat, lon))

                                        // ✅ 2. ON UTILISE TON SUPER MARQUEUR DÉDIÉ (Celui qui trace les lignes !)
                                        val marker = fr.geotower.ui.components.MiniMapAntennaMarker(
                                            this,
                                            mappedAntennas,
                                            safePrimaryColor,
                                            operatorName
                                        ).apply {
                                            position = GeoPoint(lat, lon)
                                            setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
                                            infoWindow = null
                                            setOnMarkerClickListener { _, _ -> true }
                                        }
                                        overlays.add(marker)
                                    }
                                },
                                update = { map ->
                                    // 🗺️ LOGIQUE HORS-LIGNE
                                    if (effectiveProvider == 4) {
                                        if (mapFiles.isNotEmpty()) {
                                            if (map.tileProvider !is MapsForgeTileProvider) {
                                                runCatching {
                                                    val forgeSource = MapsForgeTileSource.createFromFiles(
                                                        mapFiles,
                                                        InternalRenderTheme.OSMARENDER,
                                                        "osmarender"
                                                    )
                                                    val forgeProvider = MapsForgeTileProvider(
                                                        org.osmdroid.tileprovider.util.SimpleRegisterReceiver(context),
                                                        forgeSource,
                                                        null
                                                    )
                                                    map.tileProvider = forgeProvider
                                                }.onFailure {
                                                    mapFiles = emptyArray()
                                                    effectiveProvider = 1
                                                    AppConfig.mapProvider.value = 1
                                                    if (map.tileProvider is MapsForgeTileProvider) {
                                                        map.tileProvider = MapTileProviderBasic(context)
                                                    }
                                                    runCatching { map.setTileSource(MapUtils.OSM_Source) }
                                                }
                                            }
                                        } else {
                                            AppConfig.mapProvider.value = 1
                                        }
                                    } else {
                                        // 🌐 LOGIQUE EN LIGNE
                                        if (map.tileProvider is MapsForgeTileProvider) {
                                            map.tileProvider = MapTileProviderBasic(context)
                                        }

                                        val newSource = when (effectiveProvider) {
                                            1 -> MapUtils.OSM_Source
                                            2 -> if (ignStyle == 1) {
                                                org.osmdroid.tileprovider.tilesource.XYTileSource("MapLibreDark", 1, 20, 256, ".png", arrayOf("https://basemaps.cartocdn.com/rastertiles/dark_all/"))
                                            } else {
                                                org.osmdroid.tileprovider.tilesource.XYTileSource("MapLibre", 1, 20, 256, ".png", arrayOf("https://basemaps.cartocdn.com/rastertiles/voyager/"))
                                            }
                                            3 -> org.osmdroid.tileprovider.tilesource.TileSourceFactory.OpenTopo
                                            else -> if (ignStyle == 2) MapUtils.IgnSource.SATELLITE else MapUtils.IgnSource.PLAN_IGN
                                        }
                                        if (map.tileProvider.tileSource.name() != newSource.name()) {
                                            map.setTileSource(newSource)
                                        }
                                    }

                                    // L'inversion de couleurs si IGN sombre
                                    val shouldInvertColors = (effectiveProvider == 0 && ignStyle == 1)
                                    map.overlayManager.tilesOverlay.setColorFilter(if (shouldInvertColors) MapUtils.getInvertFilter() else null)

                                    // ✅ 3. ON MET À JOUR LE MARQUEUR PERSONNALISÉ
                                    val marker = map.overlays.filterIsInstance<fr.geotower.ui.components.MiniMapAntennaMarker>().firstOrNull()
                                    if (marker != null) {
                                        marker.icon = MapUtils.createAdaptiveMarker(context, mappedAntennas, true, operatorName)
                                    }
                                    map.invalidate()
                                }
                            )
                        }
                    }
                },
                containerColor = dialogContainerColor,
                titleContentColor = textColor,
                textContentColor = textColor,
                confirmButton = {
                    Button(
                        onClick = {
                            safeClick {
                                showConfirmDialog = false
                                onStartUpload(currentUris.toList(), description)
                            }
                        },
                        shape = buttonShape,
                        colors = ButtonDefaults.buttonColors(containerColor = activeColor)
                    ) {
                        Text(AppStrings.validate, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { safeClick { showConfirmDialog = false } }) {
                        Text(AppStrings.cancel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    }
}

@Composable
private fun PhotoPositionBadge(
    position: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = "$position/$total",
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}
