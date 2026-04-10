package fr.geotower.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fr.geotower.MainActivity
import fr.geotower.R
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import fr.geotower.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AntennaWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {

        val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
        val lastUpdate = prefs.getString("widget_last_update", "--:--") ?: "--:--"
        val isOled = prefs.getBoolean("oled_mode", false)
        val isSamsungDevice = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

        val lang = prefs.getString("app_language", "Système") ?: "Système"
        val isFr = lang == "Français" || (lang == "Système" && java.util.Locale.getDefault().language == "fr")

        val jsonData = prefs.getString("widget_data_api", null)
        var realSites: List<WidgetSiteData> = emptyList()

        if (jsonData != null) {
            try {
                val type = object : TypeToken<List<WidgetSiteData>>() {}.type
                realSites = Gson().fromJson(jsonData, type) ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // ✅ VÉRIFICATION DE LA PERMISSION ARRIÈRE-PLAN
        val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        val txtWarningTitle = fr.geotower.utils.AppStrings.widgetBgLocationWarning(context)
        val txtWarningDesc = fr.geotower.utils.AppStrings.widgetBgLocationDesc(context)

        provideContent {
            val size = LocalSize.current
            val isSmallWidget = size.width < 250.dp

            // Détection de la hauteur réduite (Format 2x1)
            val isShortWidget = size.height < 130.dp

            val widgetTitleText = if (isFr) "📍 Antennes à proximité" else "📍 Nearby antennas"

            // Si le widget est court, on n'affiche que LA première antenne de la liste !
            val displaySites = if (isShortWidget) realSites.take(1) else realSites

            GlanceTheme {
                val mainBgColor = if (isOled) {
                    ColorProvider(day = Color(0xFFF3F3F3), night = Color.Black)
                } else {
                    GlanceTheme.colors.background
                }

                val cardBgColor = if (isOled) {
                    ColorProvider(day = Color.White, night = Color(0xFF121212))
                } else {
                    GlanceTheme.colors.surface
                }

                val globalIntent = Intent(context, MainActivity::class.java).apply {
                    action = "ACTION_WIDGET_NEARBY"
                    putExtra("widget_dest", "nearby")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }

                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(mainBgColor)
                        // Si 2x1, on compresse un peu les marges globales pour que ça respire
                        .padding(if (isShortWidget) 6.dp else if (isSmallWidget) 8.dp else 12.dp)
                        .clickable(actionStartActivity(globalIntent))
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = widgetTitleText,
                            style = TextStyle(
                                color = GlanceTheme.colors.onBackground,
                                fontSize = if (isSmallWidget) 12.sp else 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = if (isSmallWidget) TextAlign.Center else TextAlign.Start
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight()
                        )

                        // ✅ BOUTON ACTUALISER MASQUÉ :
                        /*
                        Button(
                            text = "↻",
                            onClick = actionRunCallback<RefreshWidgetAction>()
                        )
                        */
                    }

                    // ✅ On cache l'heure de mise à jour si le widget est en format 2x1 pour gagner de la place
                    if (!isShortWidget) {
                        Text(
                            text = if (isFr) "Mis à jour à $lastUpdate" else "Updated at $lastUpdate",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 11.sp,
                                // ✅ ALIGNEMENT DYNAMIQUE : Au centre si c'est petit (2 de large), à gauche sinon
                                textAlign = if (isSmallWidget) TextAlign.Center else TextAlign.Start
                            ),
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )
                    }

                    // ✅ SI PAS DE PERMISSION, ON AFFICHE LE MESSAGE D'ERREUR CLIQUABLE
                    if (!hasBackgroundLocation) {
                        // (On a supprimé la création du settingsIntent ici, car l'action intelligente s'en charge !)
                        Column(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .background(cardBgColor)
                                .cornerRadius(if (isSamsungDevice) 24.dp else 12.dp)
                                .padding(8.dp)
                                .clickable(actionRunCallback<CheckPermissionAndRefreshAction>()), // ✅ On appelle notre nouvelle action ici !
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = txtWarningTitle,
                                style = TextStyle(color = GlanceTheme.colors.error, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                modifier = GlanceModifier.padding(bottom = 4.dp)
                            )
                            // On masque le long texte si le widget est trop écrasé (2x1)
                            if (!isShortWidget) {
                                Text(
                                    text = txtWarningDesc,
                                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center)
                                )
                            }
                        }
                    }
                    // ✅ SINON ON CONTINUE NORMALEMENT
                    else if (displaySites.isEmpty()) {
                        Text(
                            text = if (isFr) "En attente du GPS..." else "Waiting for GPS...",
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center),
                            modifier = GlanceModifier.padding(top = 8.dp).fillMaxWidth()
                        )
                    } else {
                        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                            items(displaySites) { site ->

                                val safeId = site.id ?: "unknown"
                                val itemIntent = Intent(context, MainActivity::class.java).apply {
                                    action = "ACTION_WIDGET_DETAIL_$safeId"
                                    putExtra("widget_dest", "detail")
                                    putExtra("widget_site_id", safeId)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }

                                val opsList = site.operateur?.split(" • ") ?: emptyList()

                                Column(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .padding(bottom = if (isShortWidget) 0.dp else 8.dp)
                                        .background(cardBgColor)
                                        .cornerRadius(if (isSamsungDevice) 24.dp else 12.dp)
                                        .padding(
                                            horizontal = if (isShortWidget) 6.dp else if (isSmallWidget) 8.dp else 12.dp,
                                            vertical = if (isShortWidget) 2.dp else if (isSmallWidget) 8.dp else 12.dp
                                        )
                                        .clickable(actionStartActivity(itemIntent))
                                ) {
                                    Row(
                                        modifier = GlanceModifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {

                                        // --- 1. BLOC DISTANCE ---
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = GlanceModifier.width(if (isShortWidget) 44.dp else if (isSmallWidget) 48.dp else 56.dp)
                                        ) {
                                            Box(
                                                modifier = GlanceModifier
                                                    .size(if (isShortWidget) 34.dp else if (isSmallWidget) 38.dp else 42.dp)
                                                    .cornerRadius(10.dp)
                                                    .background(GlanceTheme.colors.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Image(
                                                    provider = ImageProvider(R.drawable.ic_place),
                                                    contentDescription = null,
                                                    modifier = GlanceModifier.size(if (isShortWidget) 18.dp else if (isSmallWidget) 20.dp else 24.dp),
                                                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
                                                )
                                            }
                                            Spacer(modifier = GlanceModifier.height(2.dp))
                                            Text(
                                                text = site.distance ?: "",
                                                style = TextStyle(
                                                    color = GlanceTheme.colors.primary,
                                                    fontSize = if (isSmallWidget) 11.sp else 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center
                                                )
                                            )
                                        }

                                        Spacer(modifier = GlanceModifier.width(if (isSmallWidget) 8.dp else 12.dp))

                                        // --- 2. GRILLE OPÉRATEURS ---
                                        GlanceOperatorGrid(opsList = opsList, gridSize = if (isShortWidget) 44.dp else if (isSmallWidget) 50.dp else 56.dp)

                                        // --- 3. L'ESPACE MAGIQUE ---
                                        if (!isSmallWidget) {
                                            Spacer(modifier = GlanceModifier.width(12.dp))
                                            Column(modifier = GlanceModifier.defaultWeight()) {
                                                Text(
                                                    text = site.adresse ?: "",
                                                    style = TextStyle(
                                                        color = GlanceTheme.colors.onSurface,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    maxLines = 1
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = GlanceModifier.defaultWeight())
                                        }

                                        // --- 4. FLÈCHE DROITE ---
                                        Text(
                                            text = "❯",
                                            style = TextStyle(
                                                color = GlanceTheme.colors.onSurfaceVariant,
                                                fontSize = 16.sp
                                            ),
                                            modifier = GlanceModifier.padding(start = 4.dp, end = 4.dp)
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
}

// ====================================================================
// COMPOSANTS GLANCE POUR DESSINER LA GRILLE
// ====================================================================

@Composable
fun GlanceOperatorGrid(opsList: List<String>, gridSize: Dp) {
    Column(
        modifier = GlanceModifier
            .size(gridSize)
            .cornerRadius(6.dp)
    ) {
        Row(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
            GlanceGridCell(opName = opsList.getOrNull(0), modifier = GlanceModifier.defaultWeight())
            Spacer(modifier = GlanceModifier.width(2.dp))
            GlanceGridCell(opName = opsList.getOrNull(1), modifier = GlanceModifier.defaultWeight())
        }
        Spacer(modifier = GlanceModifier.height(2.dp))
        Row(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
            GlanceGridCell(opName = opsList.getOrNull(2), modifier = GlanceModifier.defaultWeight())
            Spacer(modifier = GlanceModifier.width(2.dp))
            GlanceGridCell(opName = opsList.getOrNull(3), modifier = GlanceModifier.defaultWeight())
        }
    }
}

@Composable
fun GlanceGridCell(opName: String?, modifier: GlanceModifier) {
    val iconRes = when {
        opName == null -> null
        opName.contains("ORANGE", true) -> R.drawable.logo_orange
        opName.contains("BOUYGUES", true) -> R.drawable.logo_bouygues
        opName.contains("SFR", true) -> R.drawable.logo_sfr
        opName.contains("FREE", true) -> R.drawable.logo_free
        else -> null
    }

    val cellColor = if (iconRes == null) {
        ColorProvider(day = Color.Gray.copy(alpha = 0.1f), night = Color.Gray.copy(alpha = 0.1f))
    } else {
        ColorProvider(day = Color.Transparent, night = Color.Transparent)
    }

    val boxModifier = modifier
        .fillMaxHeight()
        .cornerRadius(6.dp)
        .background(cellColor)

    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.Center
    ) {
        if (iconRes != null) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(6.dp),
                contentScale = ContentScale.Crop
            )
        }
    }
}

// ====================================================================
// ACTION DU BOUTON ACTUALISER (SÉCURISÉ EN ARRIÈRE-PLAN)
// ====================================================================

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // 1. On affiche le message TOUT DE SUITE pour confirmer le clic
        val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_language", "Système") ?: "Système"
        val isFr = lang == "Français" || (lang == "Système" && java.util.Locale.getDefault().language == "fr")

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, if (isFr) "Recherche immédiate..." else "Searching...", Toast.LENGTH_SHORT).show()
        }

        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())

        // 2. BASCULE OBLIGATOIRE EN ARRIÈRE-PLAN POUR LE GPS ET LA BASE DE DONNÉES
        withContext(Dispatchers.IO) {
            suspend fun updateUi(isSuccess: Boolean, jsonToSave: String? = null) {
                val currentTime = timeFormat.format(java.util.Date())
                val timeString = if (isSuccess) currentTime else "$currentTime ⚠️"

                val editor = prefs.edit().putString("widget_last_update", timeString)
                if (jsonToSave != null) {
                    editor.putString("widget_data_api", jsonToSave)
                }
                editor.apply()
                AntennaWidget().updateAll(context)
            }

            try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    updateUi(false)
                    return@withContext
                }

                // 3. Demande GPS
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                val location: Location? = withTimeoutOrNull(6000L) {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        CancellationTokenSource().token
                    ).await()
                }

                if (location == null) {
                    updateUi(false)
                    return@withContext
                }

                // 4. Recherche dans la base de données (Ici ça ne crashera plus !)
                val lat = location.latitude
                val lon = location.longitude
                val dao = AppDatabase.getDatabase(context).geoTowerDao()
                val offsetLat = 0.045
                val offsetLon = 0.045 / Math.cos(Math.toRadians(lat))

                val antennas = dao.getLocalisationsInBox(
                    minLat = lat - offsetLat, maxLat = lat + offsetLat,
                    minLon = lon - offsetLon, maxLon = lon + offsetLon
                )

                if (antennas.isEmpty()) {
                    updateUi(true, "[]")
                    return@withContext
                }

                // 5. Tri et formatage
                val groupedSites = antennas.groupBy {
                    "${String.format(java.util.Locale.US, "%.4f", it.latitude)}_${String.format(java.util.Locale.US, "%.4f", it.longitude)}"
                }.values

                val closestSites = groupedSites.map { siteAntennas ->
                    val main = siteAntennas.first()
                    val results = FloatArray(1)
                    Location.distanceBetween(lat, lon, main.latitude, main.longitude, results)
                    siteAntennas to results[0]
                }.sortedBy { it.second }.take(10)

                val widgetSites = closestSites.map { (siteAntennas, distance) ->
                    val main = siteAntennas.first()

                    val isMi = try {
                        prefs.getInt("distance_unit", 0) == 1
                    } catch (e: Exception) {
                        try {
                            prefs.getBoolean("distance_unit", false)
                        } catch (e2: Exception) {
                            false
                        }
                    } || fr.geotower.utils.AppConfig.distanceUnit.intValue == 1

                    val distStr = if (isMi) {
                        val distMiles = distance / 1609.34f
                        if (distMiles < 0.1f) {
                            "${(distance * 3.28084f).toInt()} ft"
                        } else {
                            String.format(java.util.Locale.US, "%.2f mi", distMiles)
                        }
                    } else {
                        if (distance >= 1000) {
                            String.format(java.util.Locale.US, "%.1f km", distance / 1000f)
                        } else {
                            "${distance.toInt()} m"
                        }
                    }

                    val baseOrder = listOf("ORANGE", "BOUYGUES", "SFR", "FREE")

                    val ops = siteAntennas.mapNotNull { it.operateur }
                        .flatMap { it.split(Regex("[/,\\-]")) }
                        .map { it.trim().uppercase() }
                        .filter { it.isNotEmpty() }
                        .sortedBy { op ->
                            val match = baseOrder.firstOrNull { op.contains(it) }
                            if (match != null) baseOrder.indexOf(match) else 99
                        }
                        .map { op ->
                            when {
                                op.contains("ORANGE") -> "ORANGE"
                                op.contains("BOUYGUES") -> "BOUYGUES"
                                op.contains("FREE") -> "FREE"
                                op.contains("SFR") -> "SFR"
                                else -> op
                            }
                        }
                        .distinct()

                    val operateurText = ops.joinToString(" • ")
                    val colorHex = if (ops.size == 1) {
                        when {
                            ops[0].contains("ORANGE") -> "#FF7900"
                            ops[0].contains("FREE") -> "#757575"
                            ops[0].contains("BOUYGUES") -> "#00295F"
                            ops[0].contains("SFR") -> "#E2001A"
                            else -> "#FFFFFF"
                        }
                    } else "#MULTI"

                    val technique = dao.getTechniqueDetails(main.idAnfr.toString())
                    val adresseAffichee = technique?.adresse ?: "Site N°${main.idAnfr}"

                    WidgetSiteData(
                        id = main.idAnfr.toString(),
                        operateur = operateurText,
                        distance = distStr,
                        adresse = adresseAffichee,
                        colorHex = colorHex
                    )
                }

                // 6. Sauvegarde et affichage
                val json = Gson().toJson(widgetSites)
                updateUi(true, json)

            } catch (e: Exception) {
                e.printStackTrace()
                updateUi(false)
            }
        }
    }
}

// ✅ NOUVELLE ACTION INTELLIGENTE POUR LA PERMISSION
class CheckPermissionAndRefreshAction : androidx.glance.appwidget.action.ActionCallback {
    override suspend fun onAction(context: android.content.Context, glanceId: androidx.glance.GlanceId, parameters: androidx.glance.action.ActionParameters) {
        val hasBgLocation = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        val workManager = androidx.work.WorkManager.getInstance(context)

        if (hasBgLocation) {
            // L'utilisateur a déjà donné la permission, il a juste cliqué parce que le widget n'était pas encore à jour !
            // -> On force la mise à jour immédiate
            val request = androidx.work.OneTimeWorkRequestBuilder<AntennaWidgetWorker>().build()
            workManager.enqueueUniqueWork("widget_manual_refresh", androidx.work.ExistingWorkPolicy.REPLACE, request)
        } else {
            // L'utilisateur n'a pas la permission -> On ouvre les paramètres
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)

            // -> Et on programme une mise à jour silencieuse dans 12 secondes pour quand il reviendra
            val delayedRequest = androidx.work.OneTimeWorkRequestBuilder<AntennaWidgetWorker>()
                .setInitialDelay(12, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniqueWork("widget_delayed_refresh", androidx.work.ExistingWorkPolicy.REPLACE, delayedRequest)
        }
    }
}