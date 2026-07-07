package fr.geotower.ui.screens.settings

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil.compose.AsyncImage
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.api.SignalQuestClient
import fr.geotower.data.api.SqPhotoData
import fr.geotower.data.community.CommunityDataPreferences
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.GeoTowerLoadingMessage
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.screens.emitters.CommunityPhoto
import fr.geotower.ui.screens.emitters.PhotoExifDialog
import fr.geotower.ui.screens.emitters.PhotoViewerActionButton
import fr.geotower.ui.screens.emitters.copyCommunityPhotoToClipboard
import fr.geotower.ui.screens.emitters.formatPhotoDate
import fr.geotower.ui.screens.emitters.saveCommunityPhotoToGallery
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.PreferenceStores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val FAVORITE_PHOTO_ACCENT = Color(0xFFE53935)
private const val VIEWER_MIN_SCALE = 1f
private const val VIEWER_MAX_SCALE = 5f
private const val VIEWER_ZOOM_THRESHOLD = 1.05f
private const val VIEWER_DOUBLE_TAP_SCALE = 2.5f

/** Une photo favorite enregistrée + sa version résolue (URL, auteur, date…), null si indisponible. */
private data class FavoritePhotoUi(
    val entry: CommunityDataPreferences.FavoritePhotoEntry,
    val photo: CommunityPhoto?
)

/** Les photos favorites d'un même site physique, avec un titre affichable. */
private data class FavoriteSiteGroup(
    val siteId: String,
    val title: String,
    val subtitle: String,
    val photos: List<FavoritePhotoUi>
)

/**
 * Page « Photos favorites » (ouverte depuis Réglages ▸ Préférences).
 * Liste les sites ayant au moins une photo favorite, avec leurs images, et permet de retirer
 * un favori après confirmation. L'écran respecte toutes les options transverses de l'app :
 * traduction (chaînes localisées), flou de défilement ([geoTowerFadingEdge]), thème/One UI et
 * mise à l'échelle de l'interface via [LocalGeoTowerUiStyle].
 */
@Composable
fun PhotosFavoritesScreen(
    navController: NavController,
    repository: AnfrRepository
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val scrollState = rememberScrollState()
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "settings")
    val prefs = remember { context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    // null = chargement en cours ; liste vide = aucun favori.
    var groups by remember { mutableStateOf<List<FavoriteSiteGroup>?>(null) }
    var reloadTick by remember { mutableStateOf(0) }

    var viewerState by remember { mutableStateOf<Pair<List<CommunityPhoto>, Int>?>(null) }
    var pendingRemoval by remember { mutableStateOf<FavoritePhotoUi?>(null) }

    BackHandler(enabled = !safeBackNavigation.isLocked) {
        safeBackNavigation.navigateBack()
    }

    LaunchedEffect(reloadTick) {
        groups = null
        groups = loadFavoriteGroups(context, repository, prefs)
    }

    val columns = if (minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600) 4 else 3

    Scaffold(
        containerColor = uiStyle.backgroundColor,
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.photos_favorites_title),
                onBack = { safeBackNavigation.navigateBack() },
                backEnabled = !safeBackNavigation.isLocked,
                actionsWidth = 56.dp,
                actions = {
                    IconButton(
                        onClick = { reloadTick++ },
                        enabled = groups != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.photos_favorites_refresh)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val currentGroups = groups
        when {
            currentGroups == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(uiStyle.backgroundColor)
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    GeoTowerLoadingMessage(
                        title = stringResource(R.string.photos_favorites_loading_title),
                        detail = stringResource(R.string.photos_favorites_loading_desc)
                    )
                }
            }

            currentGroups.isEmpty() -> {
                FavoritesEmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(uiStyle.backgroundColor)
                        .padding(innerPadding)
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(uiStyle.backgroundColor)
                        .padding(innerPadding)
                        .geoTowerFadingEdge(scrollState, fadeHeight = sizing.component(72.dp))
                        .verticalScroll(scrollState)
                        .navigationBarsPadding()
                        .padding(
                            horizontal = sizing.spacing(18.dp),
                            vertical = sizing.spacing(14.dp)
                        ),
                    verticalArrangement = Arrangement.spacedBy(sizing.spacing(16.dp))
                ) {
                    currentGroups.forEach { group ->
                        FavoriteSiteSection(
                            group = group,
                            columns = columns,
                            onOpenPhoto = { clicked ->
                                val resolved = group.photos.mapNotNull { it.photo }
                                val index = resolved.indexOfFirst { it === clicked.photo }
                                if (index >= 0) viewerState = resolved to index
                            },
                            onRemovePhoto = { pendingRemoval = it }
                        )
                    }
                    Spacer(Modifier.height(sizing.spacing(24.dp)))
                }
            }
        }
    }

    viewerState?.let { (photos, index) ->
        FavoritePhotoViewer(
            photos = photos,
            initialIndex = index,
            onDismiss = { viewerState = null }
        )
    }

    pendingRemoval?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.photos_favorites_remove_title)) },
            text = { Text(stringResource(R.string.photos_favorites_remove_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemoval = null
                    CommunityDataPreferences.setFavoritePhotoIdForSource(
                        prefs = prefs,
                        siteId = target.entry.siteId,
                        sourceId = target.entry.bucketId,
                        photoId = null
                    )
                    // Mise à jour optimiste : on évite un re-fetch réseau après le retrait.
                    groups = groups?.mapNotNull { group ->
                        if (group.siteId != target.entry.siteId) {
                            group
                        } else {
                            val remaining = group.photos.filterNot {
                                it.entry.bucketId == target.entry.bucketId &&
                                    it.entry.photoId == target.entry.photoId
                            }
                            if (remaining.isEmpty()) null else group.copy(photos = remaining)
                        }
                    }
                }) {
                    Text(
                        stringResource(R.string.photos_favorites_remove_confirm),
                        color = FAVORITE_PHOTO_ACCENT
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun FavoritesEmptyState(modifier: Modifier) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = sizing.spacing(32.dp))
        ) {
            Icon(
                imageVector = Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.size(sizing.component(64.dp))
            )
            Spacer(Modifier.height(sizing.spacing(16.dp)))
            Text(
                text = stringResource(R.string.photos_favorites_empty_title),
                style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(sizing.spacing(8.dp)))
            Text(
                text = stringResource(R.string.photos_favorites_empty_desc),
                style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FavoriteSiteSection(
    group: FavoriteSiteGroup,
    columns: Int,
    onOpenPhoto: (FavoritePhotoUi) -> Unit,
    onRemovePhoto: (FavoritePhotoUi) -> Unit
) {
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val thumbShape = androidx.compose.foundation.shape.RoundedCornerShape(sizing.component(14.dp))
    val gap = sizing.spacing(10.dp)

    Surface(
        shape = uiStyle.cardShape,
        border = uiStyle.cardBorder,
        color = if (uiStyle.useOneUi) uiStyle.bubbleColor else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(sizing.spacing(16.dp))) {
            Text(
                text = group.title,
                style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (group.subtitle.isNotBlank()) {
                Spacer(Modifier.height(sizing.spacing(2.dp)))
                Text(
                    text = group.subtitle,
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(sizing.spacing(14.dp)))

            group.photos.chunked(columns).forEachIndexed { rowIndex, rowItems ->
                if (rowIndex > 0) Spacer(Modifier.height(gap))
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rowItems.forEach { item ->
                        FavoritePhotoThumbnail(
                            modifier = Modifier.weight(1f),
                            item = item,
                            shape = thumbShape,
                            onOpen = { onOpenPhoto(item) },
                            onRemove = { onRemovePhoto(item) }
                        )
                    }
                    repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun FavoritePhotoThumbnail(
    modifier: Modifier,
    item: FavoritePhotoUi,
    shape: Shape,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val photo = item.photo
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .then(if (photo != null) Modifier.clickable { onOpen() } else Modifier)
    ) {
        if (photo != null) {
            AsyncImage(
                model = photo.url,
                contentDescription = stringResource(R.string.appstrings_site_photo_desc),
                contentScale = ContentScale.Crop,
                error = painterResource(id = R.drawable.chateau_deau),
                fallback = painterResource(id = R.drawable.chateau_deau),
                placeholder = painterResource(id = R.drawable.chateau_deau),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(sizing.spacing(8.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(sizing.component(28.dp))
                )
                Spacer(Modifier.height(sizing.spacing(6.dp)))
                Text(
                    text = stringResource(R.string.photos_favorites_photo_unavailable),
                    style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(sizing.spacing(6.dp))
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(sizing.component(32.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = stringResource(R.string.appstrings_photo_favorite_remove_desc),
                    tint = FAVORITE_PHOTO_ACCENT,
                    modifier = Modifier.size(sizing.component(18.dp))
                )
            }
        }
    }
}

@Composable
private fun FavoritePhotoViewer(
    photos: List<CommunityPhoto>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    if (photos.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val startIndex = initialIndex.coerceIn(0, photos.lastIndex)
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { photos.size })

    var scale by remember { mutableFloatStateOf(VIEWER_MIN_SCALE) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var exifPhoto by remember { mutableStateOf<CommunityPhoto?>(null) }

    // Réinitialise le zoom au changement de photo.
    LaunchedEffect(pagerState.currentPage) {
        scale = VIEWER_MIN_SCALE
        offset = Offset.Zero
    }

    val currentPhoto = photos[pagerState.currentPage.coerceIn(0, photos.lastIndex)]
    val overlayButtonBg = Color.Black.copy(alpha = 0.45f)
    val overlayContent = Color.White

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.97f))
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = scale <= VIEWER_ZOOM_THRESHOLD
            ) { page ->
                val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                    if (pagerState.currentPage != page) return@rememberTransformableState
                    val next = (scale * zoomChange).coerceIn(VIEWER_MIN_SCALE, VIEWER_MAX_SCALE)
                    scale = if (next < VIEWER_ZOOM_THRESHOLD) VIEWER_MIN_SCALE else next
                    offset = if (scale > VIEWER_ZOOM_THRESHOLD) offset + panChange else Offset.Zero
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(page) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > VIEWER_ZOOM_THRESHOLD) {
                                        scale = VIEWER_MIN_SCALE
                                        offset = Offset.Zero
                                    } else {
                                        scale = VIEWER_DOUBLE_TAP_SCALE
                                    }
                                }
                            )
                        }
                        .transformable(
                            state = transformState,
                            canPan = { scale > VIEWER_ZOOM_THRESHOLD },
                            lockRotationOnZoomPan = true
                        )
                ) {
                    AsyncImage(
                        model = photos[page].url,
                        contentDescription = stringResource(R.string.appstrings_full_screen_photo_desc),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (pagerState.currentPage == page) {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                }
                            }
                    )
                }
            }

            // Fermeture (haut-gauche).
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(32.dp)
                    .background(overlayButtonBg, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.appstrings_close),
                    tint = overlayContent,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Actions (haut-droite) : infos EXIF, copier, télécharger.
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (AppConfig.siteShowPhotoExif.value && !currentPhoto.exifMetadata.isNullOrEmpty()) {
                    PhotoViewerActionButton(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.appstrings_photo_exif_info_desc),
                        backgroundColor = overlayButtonBg,
                        contentColor = overlayContent,
                        enabled = true,
                        onClick = { exifPhoto = currentPhoto }
                    )
                }
                PhotoViewerActionButton(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.appstrings_photo_copy),
                    backgroundColor = overlayButtonBg,
                    contentColor = overlayContent,
                    enabled = true,
                    onClick = {
                        scope.launch {
                            try {
                                copyCommunityPhotoToClipboard(context, currentPhoto)
                                Toast.makeText(context, R.string.appstrings_photo_copied_to_clipboard, Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, R.string.appstrings_photo_export_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                PhotoViewerActionButton(
                    imageVector = Icons.Default.Download,
                    contentDescription = stringResource(R.string.appstrings_photo_download),
                    backgroundColor = overlayButtonBg,
                    contentColor = overlayContent,
                    enabled = true,
                    onClick = {
                        scope.launch {
                            try {
                                saveCommunityPhotoToGallery(context, currentPhoto)
                                Toast.makeText(context, R.string.appstrings_photo_saved_to_gallery, Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, R.string.appstrings_photo_export_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            // Flèche précédente.
            if (pagerState.currentPage > 0 && scale <= VIEWER_ZOOM_THRESHOLD) {
                IconButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                        .size(36.dp)
                        .background(overlayButtonBg, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = overlayContent)
                }
            }
            // Flèche suivante.
            if (pagerState.currentPage < photos.lastIndex && scale <= VIEWER_ZOOM_THRESHOLD) {
                IconButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .size(36.dp)
                        .background(overlayButtonBg, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = overlayContent)
                }
            }

            // Légende (bas) : source/opérateur, auteur, date, compteur.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val sourceLabel = currentPhoto.operatorLabel?.let { "${currentPhoto.communityName} · $it" }
                    ?: currentPhoto.communityName
                Text(
                    text = sourceLabel,
                    color = overlayContent,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
                currentPhoto.author?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = stringResource(R.string.photo_by_author, it),
                        color = overlayContent.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
                formatPhotoDate(currentPhoto.date).takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = stringResource(R.string.photo_on_date, it),
                        color = overlayContent.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
                if (photos.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${pagerState.currentPage + 1} / ${photos.size}",
                        color = overlayContent.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    exifPhoto?.let { photo ->
        PhotoExifDialog(photo = photo, onDismiss = { exifPhoto = null })
    }
}

/**
 * Lit les favoris enregistrés, les regroupe par site, résout les infos d'affichage (adresse/commune)
 * depuis la base locale, et récupère les URLs des photos. Pour CellularFR l'identifiant stable est
 * déjà l'URL ; pour SignalQuest on relit la liste des photos du site (une requête par site) et on
 * fait correspondre l'identifiant stable. Best-effort : hors ligne ou photo supprimée -> favori
 * conservé mais marqué indisponible (l'utilisateur peut alors le retirer).
 */
private suspend fun loadFavoriteGroups(
    context: Context,
    repository: AnfrRepository,
    prefs: SharedPreferences
): List<FavoriteSiteGroup> = withContext(Dispatchers.IO) {
    val entries = CommunityDataPreferences.favoritePhotoEntries(prefs)
    if (entries.isEmpty()) return@withContext emptyList()

    entries.groupBy { it.siteId }.map { (siteId, siteEntries) ->
        val rows = try {
            repository.getFavoriteScopeSiteRows(siteId)
        } catch (e: Exception) {
            emptyList()
        }
        val address = rows.firstNotNullOfOrNull { it.adresse?.trim()?.takeIf(String::isNotBlank) }
        val commune = rows.firstNotNullOfOrNull { it.commune?.trim()?.takeIf(String::isNotBlank) }

        val needsSignalQuest = siteEntries.any {
            CommunityDataPreferences.favoriteBucketBaseSourceId(it.bucketId) ==
                CommunityDataPreferences.SOURCE_SIGNALQUEST
        }
        val signalQuestById: Map<String, SqPhotoData> = if (needsSignalQuest) {
            try {
                SignalQuestClient.api.getSitePhotos(siteId = siteId).body()?.data
                    ?.associateBy { it.id ?: it.imageUrl }
                    ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
        } else {
            emptyMap()
        }

        val photos = siteEntries.map { entry ->
            val baseSource = CommunityDataPreferences.favoriteBucketBaseSourceId(entry.bucketId)
            val resolved = if (baseSource == CommunityDataPreferences.SOURCE_CELLULARFR) {
                CommunityPhoto(
                    url = entry.photoId,
                    communityName = "CellularFR",
                    sourceId = CommunityDataPreferences.SOURCE_CELLULARFR,
                    stableId = entry.photoId
                )
            } else {
                signalQuestById[entry.photoId]?.let { sq ->
                    val opKey = CommunityDataPreferences.favoriteBucketOperatorKey(entry.bucketId)
                    CommunityPhoto(
                        url = sq.imageUrl,
                        communityName = "Signal Quest",
                        author = sq.authorName,
                        date = sq.uploadedAt,
                        exifMetadata = sq.publicMetadata,
                        sourceId = CommunityDataPreferences.SOURCE_SIGNALQUEST,
                        stableId = sq.id ?: sq.imageUrl,
                        operatorKey = opKey,
                        operatorLabel = opKey?.let { OperatorColors.specForKey(it)?.label }
                    )
                }
            }
            FavoritePhotoUi(entry = entry, photo = resolved)
        }

        val title = address ?: commune
            ?: context.getString(R.string.photos_favorites_site_fallback_title, siteId)
        val subtitleParts = buildList {
            if (address != null && commune != null) add(commune)
            add(context.getString(R.string.photos_favorites_site_ref, siteId))
        }
        FavoriteSiteGroup(
            siteId = siteId,
            title = title,
            subtitle = subtitleParts.joinToString("   ·   "),
            photos = photos
        )
    }.sortedBy { it.title.lowercase(Locale.getDefault()) }
}
