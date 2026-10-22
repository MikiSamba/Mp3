package com.example.mp3

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Tema "anni '80": colori, carattere dei titoli, forme, sfondo disegnato dietro a tutta l'app e decorazioni. */
class RetroTheme(
    val label: String,
    val dark: Boolean,
    val scheme: ColorScheme,
    val font: FontFamily,
    val fontScale: Float = 1f,        // titoli: Bungee e Press Start 2P sono larghissimi a parità di sp
    val labelScale: Float = fontScale, // etichette (barra in basso, chip): possono restare un po' più grandi dei titoli
    val shapes: Shapes = Shapes(),
    val stripe: List<Color>? = null,  // bande colorate sotto la barra in alto
    val scanlines: Boolean = false,   // righe da monitor a tubo catodico sopra tutto
    val backdrop: DrawScope.() -> Unit,
)

private fun shapes(vararg r: Int) = Shapes(RoundedCornerShape(r[0].dp), RoundedCornerShape(r[1].dp), RoundedCornerShape(r[2].dp), RoundedCornerShape(r[3].dp), RoundedCornerShape(r[4].dp))

val RETRO: Map<String, RetroTheme> = linkedMapOf(
    "synthwave" to RetroTheme(
        "Synthwave", dark = true,
        scheme = darkColorScheme(
            primary = Color(0xFFFF2D95), onPrimary = Color.White, primaryContainer = Color(0x59FF2D95), onPrimaryContainer = Color(0xFFF3E9FF),
            secondary = Color(0xFF00E5FF), onSecondary = Color.Black, secondaryContainer = Color(0x3300E5FF), onSecondaryContainer = Color(0xFF00E5FF),
            tertiary = Color(0xFFFFD166), background = Color(0xFF140827), onBackground = Color(0xFFF3E9FF),
            surface = Color(0xFF1D0C3F), onSurface = Color(0xFFF3E9FF), surfaceVariant = Color(0xFF2B1052), onSurfaceVariant = Color(0xFFC3A9E8), outline = Color(0xFF6B3FA0),
            surfaceContainerLowest = Color(0xFF140827), surfaceContainerLow = Color(0xFF1D0C3F), surfaceContainer = Color(0xFF241048), surfaceContainerHigh = Color(0xFF2B1052), surfaceContainerHighest = Color(0xFF36175F),
        ),
        font = FontFamily(Font(R.font.orbitron, FontWeight.Normal), Font(R.font.orbitron, FontWeight.Bold)),
        backdrop = {
            drawRect(Brush.verticalGradient(0f to Color(0xFF140827), 0.5f to Color(0xFF1D0C3F), 1f to Color(0xFF2B1052)))
            // Griglia all'orizzonte: righe che si infittiscono verso l'alto, colonne che convergono al centro.
            val cyan = Color(0x5500E5FF)
            val horizon = size.height * 0.62f
            for (i in 1..10) { val y = horizon + (size.height - horizon) * (i / 10f) * (i / 10f); drawLine(cyan, Offset(0f, y), Offset(size.width, y), 1.5f) }
            val cx = size.width / 2
            for (i in -6..6) drawLine(cyan, Offset(cx + i * size.width / 3f, size.height), Offset(cx + i * size.width / 16f, horizon), 1.5f)
            drawLine(Color(0xFF00E5FF), Offset(0f, horizon), Offset(size.width, horizon), 2f)
        },
    ),
    "miami" to RetroTheme(
        "Miami", dark = false,
        scheme = lightColorScheme(
            primary = Color(0xFFD61F7A), onPrimary = Color.White, primaryContainer = Color(0xFFFFC2DC), onPrimaryContainer = Color(0xFF1B1B3A),
            secondary = Color(0xFF0E9C8C), onSecondary = Color.White, secondaryContainer = Color(0xFFB5F0E8), onSecondaryContainer = Color(0xFF1B1B3A),
            tertiary = Color(0xFFFF9A62), background = Color(0xFFFFE3EC), onBackground = Color(0xFF1B1B3A),
            surface = Color(0xFFFFF4F8), onSurface = Color(0xFF1B1B3A), surfaceVariant = Color(0xFFFFE0EC), onSurfaceVariant = Color(0xFF55567A), outline = Color(0xFFE39CBB),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF7FA), surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFFFF0F5), surfaceContainerHighest = Color(0xFFFFE6EF),
        ),
        font = FontFamily(Font(R.font.righteous)),
        shapes = shapes(12, 16, 20, 24, 32),
        backdrop = { drawRect(Brush.linearGradient(listOf(Color(0xFFFFD3E6), Color(0xFFFFE6D6), Color(0xFFBDF3EC)), Offset.Zero, Offset(size.width, size.height))) },
    ),
    "memphis" to RetroTheme(
        "Memphis", dark = false,
        scheme = lightColorScheme(
            primary = Color(0xFFD7261F), onPrimary = Color.White, primaryContainer = Color(0xFFFFD400), onPrimaryContainer = Color(0xFF141414),
            secondary = Color(0xFF2962FF), onSecondary = Color.White, secondaryContainer = Color(0xFFFFE766), onSecondaryContainer = Color(0xFF141414),
            tertiary = Color(0xFF27C48A), background = Color(0xFFFFF8E7), onBackground = Color(0xFF141414),
            surface = Color(0xFFFFF8E7), onSurface = Color(0xFF141414), surfaceVariant = Color(0xFFFFF0C2), onSurfaceVariant = Color(0xFF4A4A4A), outline = Color(0xFF141414),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFFBF0), surfaceContainer = Color(0xFFFFD400), surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFFFF0C2),
        ),
        font = FontFamily(Font(R.font.bungee)), fontScale = 0.8f, labelScale = 0.9f,
        shapes = shapes(4, 8, 10, 12, 16),
        backdrop = {
            drawRect(Color(0xFFFFF8E7))
            val s = 18.dp.toPx()
            val pts = ArrayList<Offset>()
            var y = s / 2; while (y < size.height) { var x = s / 2; while (x < size.width) { pts += Offset(x, y); x += s }; y += s }
            drawPoints(pts, PointMode.Points, Color(0x40141414), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
        },
    ),
    "arcade" to RetroTheme(
        "Arcade", dark = true,
        scheme = darkColorScheme(
            primary = Color(0xFF39FF14), onPrimary = Color.Black, primaryContainer = Color(0x4D39FF14), onPrimaryContainer = Color(0xFF39FF14),
            secondary = Color(0xFFFF00FF), onSecondary = Color.Black, secondaryContainer = Color(0x40FF00FF), onSecondaryContainer = Color(0xFFFF00FF),
            tertiary = Color(0xFF00FFFF), background = Color.Black, onBackground = Color(0xFF39FF14),
            surface = Color.Black, onSurface = Color(0xFF39FF14), surfaceVariant = Color(0xFF0F2A0A), onSurfaceVariant = Color(0xFF8CE07E), outline = Color(0xFF163B10),
            surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF050505), surfaceContainer = Color(0xFF0A0A0A), surfaceContainerHigh = Color(0xFF121212), surfaceContainerHighest = Color(0xFF1A1A1A),
        ),
        font = FontFamily(Font(R.font.press_start_2p)), fontScale = 0.55f, labelScale = 0.72f,
        shapes = shapes(0, 0, 0, 0, 0), scanlines = true,
        backdrop = { drawRect(Color.Black) },
    ),
    "cassetta" to RetroTheme(
        "Cassetta", dark = true,
        scheme = darkColorScheme(
            primary = Color(0xFFFF7A00), onPrimary = Color(0xFF1C1410), primaryContainer = Color(0x59FF7A00), onPrimaryContainer = Color(0xFFF4E5C3),
            secondary = Color(0xFFFFC53D), onSecondary = Color(0xFF1C1410), secondaryContainer = Color(0x40FFC53D), onSecondaryContainer = Color(0xFFFFC53D),
            tertiary = Color(0xFFE8452C), background = Color(0xFF2B1D14), onBackground = Color(0xFFF4E5C3),
            surface = Color(0xFF2B1D14), onSurface = Color(0xFFF4E5C3), surfaceVariant = Color(0xFF4A3628), onSurfaceVariant = Color(0xFFC9B08E), outline = Color(0xFF7A5A40),
            surfaceContainerLowest = Color(0xFF1C1410), surfaceContainerLow = Color(0xFF241810), surfaceContainer = Color(0xFF3B2A20), surfaceContainerHigh = Color(0xFF4A3628), surfaceContainerHighest = Color(0xFF5A4332),
        ),
        font = FontFamily(Font(R.font.bebas_neue)),
        stripe = listOf(Color(0xFFFF7A00), Color(0xFFE8452C), Color(0xFFFFC53D), Color(0xFF8C5A2B)),
        backdrop = { drawRect(Color(0xFF2B1D14)) },
    ),
)

/** Il carattere del tema va su titoli ed etichette; il corpo del testo resta quello di sistema, più leggibile. */
fun retroTypography(t: RetroTheme): Typography {
    val b = Typography()
    fun TextStyle.r(k: Float = t.fontScale) = copy(fontFamily = t.font, fontSize = fontSize * k)
    return b.copy(
        displayLarge = b.displayLarge.r(), displayMedium = b.displayMedium.r(), displaySmall = b.displaySmall.r(),
        headlineLarge = b.headlineLarge.r(), headlineMedium = b.headlineMedium.r(), headlineSmall = b.headlineSmall.r(),
        titleLarge = b.titleLarge.r(), titleMedium = b.titleMedium.r(), titleSmall = b.titleSmall.r(),
        labelLarge = b.labelLarge.r(t.labelScale), labelMedium = b.labelMedium.r(t.labelScale), labelSmall = b.labelSmall.r(t.labelScale),
    )
}

/** Righe scure ogni 4 dp sopra tutta la UI: un'immagine 1×4 ripetuta, non blocca i tocchi. */
@Composable
fun Scanlines() {
    val density = LocalDensity.current.density
    val brush = remember(density) {
        val h = (4 * density).toInt().coerceAtLeast(2)
        val bmp = Bitmap.createBitmap(1, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) bmp.setPixel(0, y, if (y < h / 2) 0x50000000 else 0)
        ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
    Box(Modifier.fillMaxSize().drawBehind { drawRect(brush) })
}
