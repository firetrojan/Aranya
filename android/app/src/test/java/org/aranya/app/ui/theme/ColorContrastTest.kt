package org.aranya.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Verifies the accessibility claims made in Color.kt and Theme.kt.
 *
 * These are real assertions about real token values, not documentation. If a palette
 * revision breaks contrast or colour-vision distinguishability, this fails.
 */
class ColorContrastTest {

    private fun relativeLuminance(color: Color): Double {
        fun channel(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    // Each pair is (foreground, background, minimum ratio, description).
    private val lightPairs = listOf(
        Quad(Charcoal, Ivory, 4.5, "body text on app background (light)"),
        Quad(Charcoal, Parchment, 4.5, "body text on content surface (light)"),
        Quad(Slate, Ivory, 4.5, "secondary text on background (light)"),
        Quad(Slate, IvorySunk, 4.5, "secondary text on sunken surface (light)"),
        Quad(Leaf900, Leaf050, 4.5, "primary text on provenance fill (light)"),
        Quad(Color.White, Leaf600, 4.5, "text on primary button (light)"),
        Quad(Leaf900, Leaf100, 4.5, "text on primary container (light)"),
        Quad(Color(0xFF12314A), Color(0xFFDCE9F4), 4.5, "text on tertiary container (light)"),
        Quad(Color(0xFF3A2C1D), Bark100, 4.5, "text on secondary container (light)"),
        Quad(Color(0xFF5A1A0C), Color(0xFFF7E2DC), 4.5, "error text on error container (light)"),
        Quad(Ivory, Leaf900, 4.5, "inverse text (light)"),
        Quad(OutlineWarm, Ivory, 1.5, "outline on background, structural only (light)"),
    )

    private val darkPairs = listOf(
        Quad(Mist, Night, 4.5, "body text on app background (dark)"),
        Quad(Mist, Slate900, 4.5, "body text on content surface (dark)"),
        Quad(MistDim, Night, 4.5, "secondary text on background (dark)"),
        Quad(MistDim, Slate900, 4.5, "secondary text on content surface (dark)"),
        Quad(Color(0xFFD8E7D9), LeafContainerDark, 4.5, "text on primary container (dark)"),
        Quad(Leaf900Dark, Leaf300, 4.5, "text on primary button (dark)"),
        Quad(Color(0xFFDCE9F4), Color(0xFF1E3B50), 4.5, "text on tertiary container (dark)"),
        Quad(Color(0xFFF0E6DA), BarkContainerDark, 4.5, "text on secondary container (dark)"),
        Quad(Color(0xFFF7E2DC), Color(0xFF4A1D12), 4.5, "error text on error container (dark)"),
        Quad(Night, Mist, 4.5, "inverse text (dark)"),
    )

    private data class Quad(val fg: Color, val bg: Color, val minRatio: Double, val label: String)

    @Test
    fun lightPaletteMeetsContrast() {
        lightPairs.forEach { q ->
            val ratio = contrast(q.fg, q.bg)
            assertTrue(
                "${q.label}: contrast was %.2f, needed %.2f".format(ratio, q.minRatio),
                ratio >= q.minRatio,
            )
        }
    }

    @Test
    fun darkPaletteMeetsContrast() {
        darkPairs.forEach { q ->
            val ratio = contrast(q.fg, q.bg)
            assertTrue(
                "${q.label}: contrast was %.2f, needed %.2f".format(ratio, q.minRatio),
                ratio >= q.minRatio,
            )
        }
    }

    @Test
    fun primaryReadoutMeetsAAALargeText() {
        // The big numeric readout is the most important text in the app, so it must clear
        // AAA (7:1), not merely AA.
        assertTrue(
            "light numeric readout %.2f".format(contrast(Charcoal, Ivory)),
            contrast(Charcoal, Ivory) >= 7.0,
        )
        assertTrue(
            "dark numeric readout %.2f".format(contrast(Mist, Night)),
            contrast(Mist, Night) >= 7.0,
        )
    }

    @Test
    fun semanticColoursAreDistinguishableInGreyscale() {
        // Charts and status rails must not rely on hue alone, so the semantic set has to
        // separate on lightness too. Deuteranopia/protanopia collapse red-green, which is
        // exactly what greyscale lightness approximates here.
        val light = listOf(
            RainLight to "rain",
            HeatLight to "heat",
            DroughtLight to "drought",
            RiskHighLight to "riskHigh",
        )
        val dark = listOf(
            RainDark to "rain",
            HeatDark to "heat",
            DroughtDark to "drought",
            RiskHighDark to "riskHigh",
        )

        fun assertSeparated(set: List<Pair<Color, String>>, theme: String) {
            set.forEachIndexed { i, (c1, n1) ->
                set.drop(i + 1).forEach { (c2, n2) ->
                    val l1 = relativeLuminance(c1)
                    val l2 = relativeLuminance(c2)
                    val delta = abs(l1 - l2)
                    assertTrue(
                        "$theme: $n1 and $n2 differ by only %.4f luminance; status colours must be " +
                            "distinguishable without colour".format(delta),
                        delta > 0.02,
                    )
                }
            }
        }

        assertSeparated(light, "light")
        assertSeparated(dark, "dark")
    }

    @Test
    fun chartSeriesAreDistinguishableInBothThemes() {
        listOf(
            AranyaSemanticColors.Light.chartSeries to "light",
            AranyaSemanticColors.Dark.chartSeries to "dark",
        ).forEach { (series, theme) ->
            assertEqualsMsg(4, series.size, "$theme chart series count")
            series.forEachIndexed { i, a ->
                series.drop(i + 1).forEach { b ->
                    assertTrue(
                        "$theme: chart series $i and nearby series differ by only %.2f contrast".format(
                            contrast(a, b),
                        ),
                        contrast(a, b) >= 1.15,
                    )
                }
            }
        }
    }

    @Test
    fun noColourIsPureBlackOrPureWhite() {
        // Pure #000/#FFF are harsh on cheap panels; the palette deliberately avoids them.
        val all = listOf(
            Leaf900, Leaf700, Leaf600, Leaf100, Leaf050,
            Bark600, Bark300, Bark100,
            Ivory, IvorySunk, Parchment, Charcoal, Slate,
            Night, NightSunk, Slate900, Mist, MistDim,
        )
        all.forEach { c ->
            val isPureBlack = c.red == 0f && c.green == 0f && c.blue == 0f
            val isPureWhite = c.red == 1f && c.green == 1f && c.blue == 1f
            assertTrue("pure black/white found: $c", !isPureBlack && !isPureWhite)
        }
    }

    private fun assertEqualsMsg(expected: Int, actual: Int, label: String) {
        assertTrue("$label: expected $expected, got $actual", expected == actual)
    }
}
