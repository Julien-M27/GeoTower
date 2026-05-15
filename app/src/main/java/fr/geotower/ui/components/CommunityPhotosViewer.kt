package fr.geotower.ui.screens.emitters

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings
import fr.geotower.utils.isNetworkAvailable
import fr.geotower.ui.components.readSiteExternalLinkOrder
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.ui.res.painterResource // 🚨 Pour régler "painterResource"
import fr.geotower.R // 🚨 Pour régler "R" (le lien vers vos dossiers res/)
import androidx.compose.foundation.Image
import java.text.Normalizer

// Modèle de données unifié
data class CommunityPhoto(
    val url: String,
    val communityName: String,
    val author: String? = null,
    val date: String? = null
)

private data class DisplayedPhotoFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

private fun fittedPhotoFrame(containerSize: IntSize, sourceSize: IntSize?): DisplayedPhotoFrame {
    val containerWidth = containerSize.width.toFloat()
    val containerHeight = containerSize.height.toFloat()

    if (
        containerWidth <= 0f ||
        containerHeight <= 0f ||
        sourceSize == null ||
        sourceSize.width <= 0 ||
        sourceSize.height <= 0
    ) {
        return DisplayedPhotoFrame(0f, 0f, containerWidth, containerHeight)
    }

    val containerRatio = containerWidth / containerHeight
    val sourceRatio = sourceSize.width.toFloat() / sourceSize.height.toFloat()
    val fittedWidth: Float
    val fittedHeight: Float

    if (sourceRatio > containerRatio) {
        fittedWidth = containerWidth
        fittedHeight = containerWidth / sourceRatio
    } else {
        fittedHeight = containerHeight
        fittedWidth = containerHeight * sourceRatio
    }

    return DisplayedPhotoFrame(
        left = (containerWidth - fittedWidth) / 2f,
        top = (containerHeight - fittedHeight) / 2f,
        width = fittedWidth,
        height = fittedHeight
    )
}

private fun normalizedSupportText(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .lowercase()
}

private fun isRteOwner(owner: String?): Boolean {
    val ownerTokens = normalizedSupportText(owner)
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotBlank() }

    return "rte" in ownerTokens
}

private fun isTubularPylon(supportNature: String?): Boolean {
    val normalizedNature = normalizedSupportText(supportNature)
    return normalizedNature.contains("tubulaire")
}

fun formatPhotoDate(isoDate: String?): String {
    if (isoDate.isNullOrBlank() || isoDate == "null") return ""
    return try {
        val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        val outputFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.FRANCE)
        val date = inputFormat.parse(isoDate)
        if (date != null) outputFormat.format(date) else ""
    } catch (e: Exception) {
        ""
    }
}

@Composable
fun CommunityPhotosSectionShared(
    photos: List<CommunityPhoto>,
    operatorName: String?,
    supportNature: String? = null, // 🚨 AJOUT DE LA NATURE DU SUPPORT
    supportOwner: String? = null,
    bgColor: Color,
    shape: Shape,
    onAddPhotoClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

    val linksOrder = readSiteExternalLinkOrder(prefs)
    val showCellularFr = AppConfig.siteShowCellularFrPhotos.value
    val showSignalQuest = AppConfig.siteShowSignalQuestPhotos.value
    val showRncMobile = prefs.getBoolean("link_rncmobile", true) // Fallback to link pref if no specific site pref
    val showEnbAnalytics = prefs.getBoolean("link_enbanalytics", true)

    // FILTRAGE
    val filteredPhotos = remember(photos, linksOrder, showCellularFr, showSignalQuest, showRncMobile, showEnbAnalytics) {
        photos.filter { photo ->
            val name = photo.communityName.lowercase()
            when {
                name.contains("cellular") -> showCellularFr
                name.contains("signal") -> showSignalQuest
                name.contains("rnc") -> showRncMobile
                name.contains("enb") -> showEnbAnalytics
                else -> true
            }
        }.sortedBy { photo ->
            val name = photo.communityName.lowercase()
            val id = when {
                name.contains("cellular") -> "cellularfr"
                name.contains("rnc") -> "rncmobile"
                name.contains("signal") -> "signalquest"
                name.contains("enb") -> "enbanalytics"
                else -> ""
            }
            val index = linksOrder.indexOf(id)
            if (index != -1) index else 999
        }
    }

    // --- SÉCURITÉ : On vérifie bien la liste FILTRÉE ---
    // --- NOUVEAU : On vérifie si l'opérateur autorise l'upload (SFR / Bouygues) ---
    val canUpload = operatorName != null && (operatorName.contains("SFR", true) || operatorName.contains("BOUYGUES", true))

    // ✅ NOUVEAU : On vérifie si on est en ligne en réutilisant ta fonction MapScreen
    val isOnline = isNetworkAvailable(context)

    val isRteTubularPylon = isTubularPylon(supportNature) && isRteOwner(supportOwner)

    // 🚨 NOUVEAU : On choisit la bonne image générique en fonction du mot-clé !
    val placeholderRes = if (AppConfig.siteShowSchemes.value) {
        when {
            isRteTubularPylon -> R.drawable.pylone_electrique
            supportNature != null && (supportNature.contains("chateau", true) || supportNature.contains("château", true)) -> R.drawable.chateau_deau
            supportNature != null && supportNature.contains("autostable", true) -> R.drawable.pylone_autostable
            supportNature != null && supportNature.contains("tubulaire", true) -> R.drawable.pylone_tubulaire
            supportNature != null && (supportNature.contains("haubane", true) || supportNature.contains("haubané", true)) -> R.drawable.pylone_haubane
            supportNature != null && (supportNature.contains("immeuble", true) || supportNature.contains("bâtiment", true) || supportNature.contains("toit", true)) -> R.drawable.immeuble
            supportNature != null && (supportNature.contains("religieux", true) || supportNature.contains("eglise", true) || supportNature.contains("église", true) || supportNature.contains("clocher", true) || supportNature.contains("chapelle", true)) -> R.drawable.monument_religieux
            supportNature != null && supportNature.contains("phare", true) -> R.drawable.phare
            supportNature != null && (supportNature.contains("semaphore", true) || supportNature.contains("sémaphore", true)) -> R.drawable.semaphore
            supportNature != null && supportNature.contains("silo", true) -> R.drawable.silo
            supportNature != null && (supportNature.contains("terrasse", true) || supportNature.contains("toit-terrasse", true)) -> R.drawable.immeuble
            supportNature != null && supportNature.contains("pylône", true) -> R.drawable.pylone_autostable
            else -> null // Si aucun mot-clé ne correspond
        }
    } else null
    val themeMode by AppConfig.themeMode
    val useOneUi = AppConfig.useOneUiDesign
    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)

    val thumbnailShape = if (useOneUi) RoundedCornerShape(16.dp) else RoundedCornerShape(8.dp)
    val badgeShape = if (useOneUi) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)
    val pillButtonShape = if (useOneUi) CircleShape else RoundedCornerShape(12.dp)

    val viewerBgBaseColor = if (isDark) Color.Black else Color.White
    val viewerContentColor = if (isDark) Color.White else Color.Black
    val overlayButtonBg = if (isDark) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.7f)

    var selectedPhotoIndex by remember { mutableStateOf<Int?>(null) }
    // 🚨 AJOUT : Variable pour afficher le château d'eau en grand
    var showPlaceholderFullScreen by remember { mutableStateOf(false) }

    // 🚨 NOUVEAU : On gère le titre dynamiquement
    val isFree = operatorName != null && operatorName.contains("FREE", ignoreCase = true)

    // On veut le titre "Schéma" si on n'a pas de vraies photos (ou si c'est Free) ET qu'on a bien un schéma à montrer
    val showSchemaTitle = (filteredPhotos.isEmpty() || isFree) && placeholderRes != null

    val sectionTitle = if (showSchemaTitle) {
        AppStrings.supportDiagram
    } else {
        AppStrings.sitePhotosAndSchemesOption
    }

    // 🚨 NOUVEAU : Si on n'a ni photos, ni schéma, ET qu'on ne peut pas uploader, on masque TOUT !
    if (filteredPhotos.isEmpty() && placeholderRes == null && (!canUpload || onAddPhotoClick == null)) {
        return // On ne dessine absolument rien, le bloc disparaît !
    }

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhotoCamera, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(sectionTitle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (!isOnline) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = if (placeholderRes != null) 16.dp else 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = AppStrings.communityPhotosOffline,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (placeholderRes != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            Image(
                                painter = painterResource(id = placeholderRes),
                                contentDescription = AppStrings.supportImageDesc,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(thumbnailShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { showPlaceholderFullScreen = true }
                            )
                        }
                    }
                }
            } else {
                // 🌐 LOGIQUE EN LIGNE
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {

                    // 🚨 VÉRIFICATION : EST-CE QUE L'OPÉRATEUR EST FREE ?
                    if (operatorName != null && operatorName.contains("FREE", ignoreCase = true)) {

                        // 1. POUR FREE : ON AFFICHE L'IMAGE (Si on en a trouvé une correspondante)
                        if (placeholderRes != null) {
                            item {
                                Image(
                                    painter = painterResource(id = placeholderRes), // 🪄 L'image s'adapte toute seule !
                                    contentDescription = AppStrings.supportImageDesc,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(thumbnailShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { showPlaceholderFullScreen = true }
                                )
                            }
                        }

                    } else {

                        // 2. POUR LES AUTRES OPÉRATEURS : ON AFFICHE D'ABORD LES VRAIES PHOTOS
                        if (filteredPhotos.isNotEmpty()) {
                            itemsIndexed(filteredPhotos) { index, photo ->
                                AsyncImage(
                                    model = photo.url,
                                    contentDescription = AppStrings.sitePhotoDesc,
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(id = placeholderRes ?: R.drawable.chateau_deau),
                                    fallback = painterResource(id = placeholderRes ?: R.drawable.chateau_deau),
                                    placeholder = painterResource(id = placeholderRes ?: R.drawable.chateau_deau),
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(thumbnailShape)
                                        .clickable { selectedPhotoIndex = index }
                                )
                            }
                        }

                        // 🚨 AJOUT : LA BARRE DE SÉPARATION VERTICALE
                        // Elle s'affiche uniquement si on a des vraies photos ET un schéma à montrer
                        if (filteredPhotos.isNotEmpty() && placeholderRes != null) {
                            item {
                                Box(
                                    modifier = Modifier.height(120.dp), // Même hauteur que le carrousel
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp) // Épaisseur de la ligne
                                            .height(80.dp) // Un peu plus petite que les photos pour faire élégant
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }

                        // 3. ENSUITE, ON AFFICHE L'IMAGE GÉNÉRIQUE (Si on en a trouvé une correspondante)
                        if (placeholderRes != null) {
                            item {
                                Image(
                                    painter = painterResource(id = placeholderRes), // 🪄 L'image s'adapte toute seule !
                                    contentDescription = AppStrings.supportImageDesc,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(thumbnailShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { showPlaceholderFullScreen = true }
                                )
                            }
                        }

                        // 4. ENFIN, LE BOUTON D'UPLOAD (Seulement pour SFR / Bouygues)
                        // Il se mettra en tout dernier
                        if (canUpload && onAddPhotoClick != null) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(thumbnailShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), thumbnailShape)
                                        .clickable { onAddPhotoClick() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Outbox,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = AppStrings.uploadPhotosPrompt,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 🚨 DIALOGUE PLEIN ÉCRAN POUR LE SUPPORT GÉNÉRIQUE
    // On l'affiche seulement si on a cliqué ET qu'on a bien une image à montrer
    if (showPlaceholderFullScreen && placeholderRes != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showPlaceholderFullScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = viewerBgBaseColor) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // L'image au centre
                    Image(
                        painter = painterResource(id = placeholderRes), // 🪄 L'image en plein écran s'adapte !
                        contentDescription = AppStrings.supportImageFullScreenDesc,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { showPlaceholderFullScreen = false })
                            }
                    )

                    // La croix de fermeture
                    IconButton(
                        onClick = { showPlaceholderFullScreen = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 20.dp, end = 4.dp)
                            .background(overlayButtonBg, shape = CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = AppStrings.close, tint = viewerContentColor)
                    }
                }
            }
        }
    }

    if (selectedPhotoIndex != null) {
        // --- On utilise filteredPhotos ---
        val pagerState = rememberPagerState(initialPage = selectedPhotoIndex!!, pageCount = { filteredPhotos.size })
        val currentPhoto = filteredPhotos[pagerState.currentPage]
        val fullScreenTitle = AppStrings.communityPhotosTitle(filteredPhotos.size, currentPhoto.communityName)

        val dismissOffset = remember { Animatable(0f) }
        val coroutineScope = rememberCoroutineScope()
        var viewerContainerSize by remember { mutableStateOf(IntSize.Zero) }
        var photoSourceSizes by remember(filteredPhotos) { mutableStateOf<Map<Int, IntSize>>(emptyMap()) }

        Dialog(
            onDismissRequest = {
                selectedPhotoIndex = null
                coroutineScope.launch { dismissOffset.snapTo(0f) }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val bgAlpha = (1f - (abs(dismissOffset.value) / 800f)).coerceIn(0f, 1f)
            val density = LocalDensity.current
            val currentPhotoFrame = fittedPhotoFrame(viewerContainerSize, photoSourceSizes[pagerState.currentPage])
            val photoStartPadding = with(density) { currentPhotoFrame.left.coerceAtLeast(0f).toDp() + 16.dp }
            val photoEndPadding = with(density) { (viewerContainerSize.width - currentPhotoFrame.right).coerceAtLeast(0f).toDp() + 16.dp }
            val navigationBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val photoBottomPadding = maxOf(
                with(density) { (viewerContainerSize.height - currentPhotoFrame.bottom).coerceAtLeast(0f).toDp() + 12.dp },
                navigationBottomPadding + 12.dp
            )

            Surface(modifier = Modifier.fillMaxSize(), color = viewerBgBaseColor.copy(alpha = bgAlpha)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewerContainerSize = it }
                        .graphicsLayer { translationY = dismissOffset.value }
                ) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        var scale by remember { mutableFloatStateOf(1f) }
                        var offset by remember { mutableStateOf(Offset.Zero) }
                        var containerSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

                        val isZoomed = scale > 1.01f

                        LaunchedEffect(pagerState.currentPage) {
                            if (pagerState.currentPage != page) {
                                scale = 1f
                                offset = Offset.Zero
                            }
                        }

                        val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
                            var newScale = scale * zoomChange
                            if (newScale < 1.05f) {
                                newScale = 1f
                                offset = Offset.Zero
                            } else if (newScale > 5f) {
                                newScale = 5f
                            }
                            scale = newScale

                            if (isZoomed) {
                                val maxX = (containerSize.width * (scale - 1)) / 2f
                                val maxY = (containerSize.height * (scale - 1)) / 2f
                                offset = Offset(
                                    x = (offset.x + offsetChange.x).coerceIn(-maxX, maxX),
                                    y = (offset.y + offsetChange.y).coerceIn(-maxY, maxY)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { containerSize = it }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (scale > 1.01f) { scale = 1f; offset = Offset.Zero } else { scale = 3f }
                                        }
                                    )
                                }
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (scale > 1.01f && event.changes.size == 1) {
                                                val change = event.changes.first()
                                                if (change.pressed && change.previousPressed && !change.isConsumed) {
                                                    val dragAmount = change.position - change.previousPosition
                                                    val maxX = (containerSize.width * (scale - 1)) / 2f
                                                    val maxY = (containerSize.height * (scale - 1)) / 2f
                                                    offset = Offset(
                                                        x = (offset.x + dragAmount.x).coerceIn(-maxX, maxX),
                                                        y = (offset.y + dragAmount.y).coerceIn(-maxY, maxY)
                                                    )
                                                    change.consume()
                                                }
                                            }
                                        }
                                    }
                                }
                                .pointerInput(isZoomed) {
                                    if (!isZoomed) {
                                        detectVerticalDragGestures(
                                            onDragEnd = {
                                                if (dismissOffset.value > 250f) {
                                                    selectedPhotoIndex = null
                                                    coroutineScope.launch { dismissOffset.snapTo(0f) }
                                                } else {
                                                    coroutineScope.launch { dismissOffset.animateTo(0f) }
                                                }
                                            },
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                coroutineScope.launch {
                                                    val newOffset = (dismissOffset.value + dragAmount).coerceAtLeast(0f)
                                                    dismissOffset.snapTo(newOffset)
                                                }
                                            }
                                        )
                                    }
                                }
                                .transformable(state = transformState, enabled = isZoomed)
                        ) {
                            // --- On utilise filteredPhotos ---
                            AsyncImage(
                                model = filteredPhotos[page].url,
                                contentDescription = AppStrings.fullScreenPhotoDesc,
                                contentScale = ContentScale.Fit,
                                onSuccess = { state ->
                                    val drawable = state.result.drawable
                                    if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                                        photoSourceSizes = photoSourceSizes + (page to IntSize(drawable.intrinsicWidth, drawable.intrinsicHeight))
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y
                                    )
                            )
                        }
                    }

                    // --- FLÈCHE GAUCHE ---
                    if (pagerState.currentPage > 0) {
                        IconButton(
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = photoStartPadding).size(28.dp).background(overlayButtonBg, CircleShape)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = AppStrings.previous, tint = viewerContentColor, modifier = Modifier.size(20.dp))
                        }
                    }

                    // --- FLÈCHE DROITE (On utilise filteredPhotos.size) ---
                    if (pagerState.currentPage < filteredPhotos.size - 1) {
                        IconButton(
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = photoEndPadding).size(28.dp).background(overlayButtonBg, CircleShape)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = AppStrings.next, tint = viewerContentColor, modifier = Modifier.size(20.dp))
                        }
                    }

                    // --- TEXTES EN HAUT ---
                    Column(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 28.dp, start = 16.dp, end = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = fullScreenTitle,
                            color = viewerContentColor,
                            style = MaterialTheme.typography.titleLarge.copy(shadow = if (isDark) androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 8f) else null),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    IconButton(
                        onClick = { selectedPhotoIndex = null; coroutineScope.launch { dismissOffset.snapTo(0f) } },
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 4.dp).background(overlayButtonBg, shape = CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = AppStrings.close, tint = viewerContentColor)
                    }

                    // --- AUTEUR ET DATE ---
                    if (!currentPhoto.author.isNullOrBlank() || !currentPhoto.date.isNullOrBlank()) {
                        Column(
                            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = photoBottomPadding, start = photoStartPadding).background(overlayButtonBg, badgeShape).padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            if (!currentPhoto.author.isNullOrBlank() && currentPhoto.author != "null") {
                                Text(text = AppStrings.photoByAuthor(currentPhoto.author), color = viewerContentColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            val formattedDate = formatPhotoDate(currentPhoto.date)
                            if (formattedDate.isNotEmpty()) {
                                Text(text = AppStrings.photoOnDate(formattedDate), color = viewerContentColor.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // --- COMPTEUR (On utilise filteredPhotos.size) ---
                    if (filteredPhotos.size > 1) {
                        Column(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = photoBottomPadding, end = photoEndPadding).background(overlayButtonBg, pillButtonShape).padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(imageVector = Icons.Default.Collections, contentDescription = null, tint = viewerContentColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "${pagerState.currentPage + 1} / ${filteredPhotos.size}", color = viewerContentColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    // --- INDICATEURS "PILULE" (On utilise filteredPhotos.size) ---
                    if (filteredPhotos.size > 1) {
                        var containerWidth by remember { mutableStateOf(0) }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = photoBottomPadding)
                                .onSizeChanged { containerWidth = it.width }
                                .background(color = if (isDark) Color(0xFF2C2C2C) else Color(0xFFF0F0F0), shape = CircleShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .pointerInput(filteredPhotos.size) {
                                    detectDragGestures { change, _ ->
                                        if (containerWidth > 0) {
                                            val positionX = change.position.x.coerceIn(0f, containerWidth.toFloat())
                                            val progress = positionX / containerWidth.toFloat()
                                            val targetPage = (progress * (filteredPhotos.size - 1)).toInt()
                                            if (targetPage != pagerState.currentPage) {
                                                coroutineScope.launch { pagerState.animateScrollToPage(targetPage) }
                                            }
                                        }
                                        change.consume()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                val maxDots = 5
                                val startDot = (pagerState.currentPage - maxDots / 2).coerceIn(0, maxOf(0, filteredPhotos.size - maxDots))
                                val endDot = minOf(startDot + maxDots, filteredPhotos.size)

                                (startDot until endDot).forEach { iteration ->
                                    val isActive = pagerState.currentPage == iteration
                                    val dotColor = when {
                                        isActive && isDark -> Color.White
                                        isActive && !isDark -> Color(0xFF424242)
                                        !isActive && isDark -> Color.White.copy(alpha = 0.2f)
                                        else -> Color(0xFFC0C0C0)
                                    }

                                    val animatedWidth by animateDpAsState(targetValue = if (isActive) 18.dp else 8.dp, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "pillWidth")

                                    Box(
                                        modifier = Modifier.height(24.dp).width(if (isActive) 24.dp else 12.dp).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                                            coroutineScope.launch { pagerState.animateScrollToPage(iteration) }
                                        },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(modifier = Modifier.size(width = animatedWidth, height = 7.dp).clip(CircleShape).background(dotColor))
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
