package de.paul.sonoscontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlin.math.roundToInt

/** Akkustand des Tablets. */
data class BatteryStatus(
    val percent: Int,
    /** Hängt am Strom — lädt oder ist schon voll. */
    val charging: Boolean
)

/** Bis hierhin wird der Akku rot — Zeit fürs Ladekabel. */
private const val LOW_BATTERY_PERCENT = 20

/** Hell genug für den dunklen Cover-Verlauf, kräftig genug für den hellen Hintergrund. */
private val LowBatteryColor = Color(0xFFFF5449)
private val ChargingColor = Color(0xFF4CAF50)

/**
 * Kleine Akku-Anzeige für die Kopfzeile: Prozentzahl und eine gezeichnete
 * Batterie mit Füllstand, am Strom mit Blitz. Da das Tablet fixiert läuft,
 * sehen die Eltern den Akkustand hier statt in der Statusleiste — für die
 * Kinder ist er unwichtig, deshalb klein und in der Farbe der Menü-Icons.
 */
@Composable
fun BatteryIndicator(modifier: Modifier = Modifier) {
    // Ohne Akku (oder bis der erste Stand da ist) bleibt die Anzeige weg
    val status = rememberBatteryStatus() ?: return
    val contentColor = LocalContentColor.current
    val low = !status.charging && status.percent <= LOW_BATTERY_PERCENT
    val fillColor = when {
        status.charging -> ChargingColor
        low -> LowBatteryColor
        else -> contentColor
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "Akku ${status.percent} %" + if (status.charging) ", lädt" else ""
        }
    ) {
        Text(
            text = "${status.percent} %",
            style = MaterialTheme.typography.labelMedium,
            color = if (low) LowBatteryColor else contentColor
        )
        Spacer(modifier = Modifier.width(6.dp))
        BatteryGraphic(
            percent = status.percent,
            charging = status.charging,
            outlineColor = contentColor,
            fillColor = fillColor,
            modifier = Modifier.size(width = 26.dp, height = 13.dp)
        )
    }
}

/** Umriss des Blitzes, relativ zu seiner Breite und Höhe (0..1). */
private val BoltPoints = listOf(
    0.62f to 0f,
    0.08f to 0.58f,
    0.46f to 0.58f,
    0.38f to 1f,
    0.92f to 0.42f,
    0.54f to 0.42f
)

/** Batterie mit Pluspol rechts; die Füllung wächst von links mit dem Ladestand. */
@Composable
private fun BatteryGraphic(
    percent: Int,
    charging: Boolean,
    outlineColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = 1.5.dp.toPx()
        val capWidth = 2.dp.toPx()
        val bodyWidth = size.width - capWidth

        // Gehäuse
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(bodyWidth - stroke, size.height - stroke),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(width = stroke)
        )
        // Pluspol, leicht unter den Rand geschoben, damit keine Lücke bleibt
        val capHeight = size.height * 0.4f
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(bodyWidth - stroke / 2, (size.height - capHeight) / 2),
            size = Size(capWidth + stroke / 2, capHeight),
            cornerRadius = CornerRadius(1.dp.toPx())
        )

        // Füllung mit etwas Luft zum Rand
        val inset = stroke + 1.dp.toPx()
        val innerWidth = bodyWidth - 2 * inset
        val innerHeight = size.height - 2 * inset
        val fillWidth = innerWidth * percent.coerceIn(0, 100) / 100f
        if (fillWidth > 0f) {
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(inset, inset),
                size = Size(fillWidth, innerHeight),
                cornerRadius = CornerRadius(1.5.dp.toPx())
            )
        }

        // Blitz in der Mitte, in der Farbe des Rands: auf der grünen Füllung wie im leeren Teil zu sehen.
        // Er reicht bis an den Rand, damit er trotz der kleinen Batterie gut erkennbar bleibt.
        if (charging) {
            val boltHeight = size.height - 2 * stroke
            val boltWidth = boltHeight * 0.6f
            val left = (bodyWidth - boltWidth) / 2
            val bolt = Path().apply {
                BoltPoints.forEachIndexed { index, (x, y) ->
                    val px = left + x * boltWidth
                    val py = stroke + y * boltHeight
                    if (index == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            drawPath(bolt, color = outlineColor)
        }
    }
}

/** Liest den Akkustand und hält ihn aktuell, solange die Anzeige zu sehen ist — bei ausgeschaltetem Display nicht. */
@Composable
private fun rememberBatteryStatus(): BatteryStatus? {
    val context = LocalContext.current
    var status by remember { mutableStateOf<BatteryStatus?>(null) }

    // Am Lebenszyklus der App statt an der Neuzeichnung: Die ruht bei ausgeschaltetem
    // Display, abgemeldet werden muss trotzdem sofort.
    LifecycleStartEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                intent.toBatteryStatus()?.let { status = it }
            }
        }
        // ACTION_BATTERY_CHANGED ist „sticky": Die Anmeldung liefert sofort den
        // aktuellen Stand, danach kommt jede Änderung. Als reines System-Ereignis
        // braucht der Receiver kein RECEIVER_EXPORTED/RECEIVER_NOT_EXPORTED.
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.toBatteryStatus()
            ?.let { status = it }
        onStopOrDispose { context.unregisterReceiver(receiver) }
    }
    return status
}

private fun Intent.toBatteryStatus(): BatteryStatus? {
    if (!getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)) return null
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null

    val plugged = getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    val state = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
    return BatteryStatus(
        percent = (level * 100f / scale).roundToInt().coerceIn(0, 100),
        charging = plugged || state == BatteryManager.BATTERY_STATUS_CHARGING
    )
}
