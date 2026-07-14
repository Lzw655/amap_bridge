package com.espressif.amapbridge.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.espressif.amapbridge.navigation.Maneuver
import com.espressif.amapbridge.navigation.NavigationInfo

private val PreviewBackground = Color(0xFF101412)
private val PreviewPanel = Color(0xFF1D2521)
private val PreviewWhite = Color(0xFFF5F8F6)
private val PreviewGreen = Color(0xFF35D07F)

@Composable
fun EspPreviewScreen(info: NavigationInfo?, connectionStatus: String) {
    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFF7FAF8))) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val scale = maxWidth.value / 320f
            Box(modifier = Modifier.fillMaxWidth().height(240.dp * scale)) {
                EspNavigationCanvas(
                    info = info,
                    connectionStatus = connectionStatus,
                    modifier = Modifier
                        .size(320.dp, 240.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        },
                )
            }
        }
    }
}

@Composable
private fun EspNavigationCanvas(info: NavigationInfo?, connectionStatus: String, modifier: Modifier = Modifier) {
    val maneuver = info?.maneuver ?: Maneuver.UNKNOWN
    Box(modifier = modifier.background(PreviewBackground)) {
        Box(
            modifier = Modifier.offset(8.dp, 7.dp).size(304.dp, 31.dp)
                .clip(RoundedCornerShape(8.dp)).background(PreviewPanel),
        ) {
            Box(
                modifier = Modifier.offset(12.dp, 11.dp).size(9.dp).clip(CircleShape)
                    .background(if (connectionStatus == "已连接") PreviewGreen else Color(0xFFFFB74D)),
            )
            Text(
                text = info?.road ?: "--",
                modifier = Modifier.align(Alignment.Center).fillMaxWidth().offset(x = 10.dp),
                color = PreviewWhite,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }

        Image(
            painter = painterResource(maneuver.iconResource()),
            contentDescription = maneuver.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.offset(26.dp, 42.dp).size(112.dp),
        )

        Text(
            text = formatDistance(info?.distanceMeters),
            modifier = Modifier.offset(146.dp, 68.dp).size(98.dp, 43.dp),
            color = PreviewWhite,
            fontSize = 31.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = "下一路口",
            modifier = Modifier.offset(149.dp, 112.dp),
            color = Color(0xFFB8C4BD),
            fontSize = 13.sp,
        )

        SpeedBadge(value = info?.speedLimitKph, limit = true, modifier = Modifier.offset(247.dp, 44.dp))
        SpeedBadge(value = info?.currentSpeedKph, limit = false, modifier = Modifier.offset(247.dp, 98.dp))

        Row(
            modifier = Modifier.offset(8.dp, 194.dp).size(304.dp, 38.dp)
                .clip(RoundedCornerShape(8.dp)).background(PreviewPanel),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FooterValue("剩余 ${formatDistance(info?.remainingDistanceMeters)}", Modifier.weight(1.25f))
            FooterValue(formatDuration(info?.remainingDurationSeconds), Modifier.weight(0.8f))
            FooterValue("ETA ${info?.eta ?: "--:--"}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun SpeedBadge(value: Int?, limit: Boolean, modifier: Modifier) {
    val borderColor = if (limit) Color(0xFFE34545) else PreviewGreen
    val background = if (limit) PreviewWhite else PreviewPanel
    val textColor = if (limit) PreviewBackground else PreviewWhite
    Box(
        modifier = modifier.size(42.dp).clip(CircleShape).background(background)
            .border(if (limit) 5.dp else 2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(value?.toString() ?: "--", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FooterValue(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = PreviewWhite,
        fontSize = 11.sp,
        maxLines = 1,
        textAlign = TextAlign.Center,
        overflow = TextOverflow.Ellipsis,
    )
}

fun formatDistance(meters: Int?): String = when {
    meters == null -> "--"
    meters >= 10_000 -> "${meters / 1_000} km"
    meters >= 1_000 -> "%.1f km".format(meters / 1_000.0)
    else -> "$meters m"
}

fun formatDuration(seconds: Int?): String = when {
    seconds == null -> "-- min"
    seconds >= 3_600 -> "${seconds / 3_600}h ${(seconds % 3_600) / 60}m"
    else -> "${seconds / 60} min"
}
