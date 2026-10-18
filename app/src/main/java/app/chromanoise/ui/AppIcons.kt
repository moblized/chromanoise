package app.chromanoise.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small round button that opens the Appearance (theme) dialog; sits at the top right of each tab. */
@Composable
fun AppearanceButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(AppIcons.Contrast, contentDescription = "Appearance", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Big title with the Appearance button on the right, as on every tab. */
@Composable
fun ScreenHeader(title: String, onOpenAppearance: () -> Unit, subtitle: String? = null) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AppearanceButton(onOpenAppearance)
    }
}

/** The few icons the app needs, drawn here so there's no icon-library dependency. */
object AppIcons {
    val Play: ImageVector = icon("Play") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(8f, 5f)
            lineTo(19f, 12f)
            lineTo(8f, 19f)
            close()
        }
    }

    val Pause: ImageVector = icon("Pause") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 5f); lineTo(10f, 5f); lineTo(10f, 19f); lineTo(6f, 19f); close()
            moveTo(14f, 5f); lineTo(18f, 5f); lineTo(18f, 19f); lineTo(14f, 19f); close()
        }
    }

    val Close: ImageVector = icon("Close") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(7f, 7f); lineTo(17f, 17f)
            moveTo(17f, 7f); lineTo(7f, 17f)
        }
    }

    val Add: ImageVector = icon("Add") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(5f, 12f); lineTo(19f, 12f)
        }
    }

    val Speaker: ImageVector = icon("Volume") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 9f); lineTo(8f, 9f); lineTo(13f, 5f); lineTo(13f, 19f); lineTo(8f, 15f); lineTo(4f, 15f); close()
        }
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
            moveTo(16f, 9f); quadTo(18f, 12f, 16f, 15f)
            moveTo(18.5f, 6.5f); quadTo(22f, 12f, 18.5f, 17.5f)
        }
    }

    /** Alarm clock: round face, two hands, two little bells. */
    val Alarm: ImageVector = icon("Alarm") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(5f, 13f)
            arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 19f, y1 = 13f)
            arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 5f, y1 = 13f)
            close()
            moveTo(12f, 9.5f); lineTo(12f, 13f); lineTo(14.5f, 15f)
            moveTo(4.5f, 6.5f); lineTo(7.5f, 3.5f)
            moveTo(19.5f, 6.5f); lineTo(16.5f, 3.5f)
        }
    }

    /** Half-filled circle: the usual "light / dark appearance" symbol. */
    val Contrast: ImageVector = icon("Appearance") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
            moveTo(4f, 12f)
            arcTo(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 20f, y1 = 12f)
            arcTo(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 4f, y1 = 12f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 4f)
            arcTo(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 12f, y1 = 20f)
            close()
        }
    }

    val Hourglass: ImageVector = icon("Timer") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(6f, 3f); lineTo(18f, 3f)
            moveTo(6f, 21f); lineTo(18f, 21f)
            moveTo(7.5f, 3f); lineTo(7.5f, 6.5f); lineTo(12f, 12f); lineTo(7.5f, 17.5f); lineTo(7.5f, 21f)
            moveTo(16.5f, 3f); lineTo(16.5f, 6.5f); lineTo(12f, 12f); lineTo(16.5f, 17.5f); lineTo(16.5f, 21f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(9f, 19f); lineTo(15f, 19f); lineTo(12f, 15.5f); close()
        }
    }

    val Stopwatch: ImageVector = icon("Stopwatch") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(5f, 14f)
            arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 19f, y1 = 14f)
            arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 5f, y1 = 14f)
            close()
            moveTo(12f, 14f); lineTo(12f, 10f)
            moveTo(10f, 2.5f); lineTo(14f, 2.5f)
            moveTo(12f, 2.5f); lineTo(12f, 7f)
            moveTo(18f, 6.5f); lineTo(19.5f, 5f)
        }
    }

    /** Keypad delete key. */
    val Backspace: ImageVector = icon("Delete digit") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
            moveTo(9f, 5f); lineTo(20f, 5f); lineTo(20f, 19f); lineTo(9f, 19f); lineTo(3f, 12f); close()
            moveTo(12f, 9f); lineTo(17f, 15f)
            moveTo(17f, 9f); lineTo(12f, 15f)
        }
    }

    /** Spectrum bars, for the Noise tab. */
    val Bars: ImageVector = icon("Noise") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f, strokeLineCap = StrokeCap.Round) {
            moveTo(4f, 19f); lineTo(4f, 10f)
            moveTo(8f, 19f); lineTo(8f, 6f)
            moveTo(12f, 19f); lineTo(12f, 11f)
            moveTo(16f, 19f); lineTo(16f, 14f)
            moveTo(20f, 19f); lineTo(20f, 16f)
        }
    }

    private inline fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()
}
