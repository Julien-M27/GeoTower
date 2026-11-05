package fr.geotower.wear.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import fr.geotower.wear.data.model.GpsFixStatus
import java.util.Locale

@Composable
fun GpsStatusHeader(
    gpsStatus: GpsFixStatus,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        when (gpsStatus) {
            is GpsFixStatus.Searching -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFB300))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Fix GPS montre en cours...",
                        fontSize = 11.sp,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                }
            }
            is GpsFixStatus.Fixed -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF30D158))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "GPS Montre (Fix ±${gpsStatus.accuracyMeters.toInt()}m)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF30D158)
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = String.format(Locale.US, "%.5f, %.5f", gpsStatus.latitude, gpsStatus.longitude),
                        fontSize = 10.sp,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                }
            }
            is GpsFixStatus.Error -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF453A))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = gpsStatus.message,
                        fontSize = 11.sp,
                        color = Color(0xFFFF453A)
                    )
                }
            }
        }
    }
}
