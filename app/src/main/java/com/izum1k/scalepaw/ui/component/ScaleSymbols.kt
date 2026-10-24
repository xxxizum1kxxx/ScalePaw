@file:Suppress("MaxLineLength")

package com.izum1k.scalepaw.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Glyphs Yoin Symbols does not have, drawn in its language: 24 viewport, 1.5 stroke, round caps
 * and joins, continuous corners, outline only (tinted by the Icon that shows them).
 */
object ScaleSymbols {
    private fun symbol(name: String, vararg strokes: String, fills: List<String> = emptyList()): ImageVector {
        val b = ImageVector.Builder("ScaleSymbols.$name", 24.dp, 24.dp, 24f, 24f)
        strokes.forEach {
            b.addPath(
                pathData = addPathNodes(it),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        fills.forEach { b.addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
        return b.build()
    }

    /** Bathroom scale: a continuous-corner plate with a dial window and needle. */
    val Scale: ImageVector by lazy {
        symbol(
            "Scale",
            "M9.03 3.75 L14.97 3.75 C16.818 3.75 17.742 3.75 18.448 4.11 C19.069 4.426 19.574 4.931 19.89 5.552 C20.25 6.258 20.25 7.182 20.25 9.03 L20.25 14.97 C20.25 16.818 20.25 17.742 19.89 18.448 C19.574 19.069 19.069 19.574 18.448 19.89 C17.742 20.25 16.818 20.25 14.97 20.25 L9.03 20.25 C7.182 20.25 6.258 20.25 5.552 19.89 C4.931 19.574 4.426 19.069 4.11 18.448 C3.75 17.742 3.75 16.818 3.75 14.97 L3.75 9.03 C3.75 7.182 3.75 6.258 4.11 5.552 C4.426 4.931 4.931 4.426 5.552 4.11 C6.258 3.75 7.182 3.75 9.03 3.75 Z",
            "M8 10.25 C8.9 8.6 10.35 7.75 12 7.75 C13.65 7.75 15.1 8.6 16 10.25",
            "M12 10.5 L13.2 8.6",
        )
    }

    val ScaleFilled: ImageVector by lazy {
        symbol(
            "ScaleFilled",
            "M8 10.25 C8.9 8.6 10.35 7.75 12 7.75 C13.65 7.75 15.1 8.6 16 10.25",
            fills = listOf(
                "M8.48 3 L15.52 3 C17.788 3 18.922 3 19.789 3.441 C20.551 3.83 21.17 4.449 21.559 5.211 C22 6.078 22 7.212 22 9.48 L22 14.52 C22 16.788 22 17.922 21.559 18.789 C21.17 19.551 20.551 20.17 19.789 20.559 C18.922 21 17.788 21 15.52 21 L8.48 21 C6.212 21 5.078 21 4.211 20.559 C3.449 20.17 2.83 19.551 2.441 18.789 C2 17.922 2 16.788 2 14.52 L2 9.48 C2 7.212 2 6.078 2.441 5.211 C2.83 4.449 3.449 3.83 4.211 3.441 C5.078 3 6.212 3 8.48 3 Z M12 7 C10.06 7 8.33 8 7.34 9.89 C7.15 10.26 7.29 10.71 7.66 10.9 C8.02 11.1 8.48 10.96 8.67 10.6 C9.4 9.2 10.63 8.5 12 8.5 C12.15 8.5 12.3 8.51 12.45 8.53 L11.37 10.1 C11.13 10.44 11.22 10.91 11.56 11.14 C11.9 11.38 12.37 11.29 12.6 10.95 L13.98 8.95 C14.53 9.32 15 9.87 15.33 10.6 C15.52 10.96 15.98 11.1 16.34 10.9 C16.71 10.71 16.85 10.26 16.66 9.89 C15.67 8 13.94 7 12 7 Z",
            ),
        )
    }

    /** Line chart with a rising trend. */
    val Chart: ImageVector by lazy {
        symbol("Chart", "M4 4.5 L4 17.4 C4 18.836 5.164 20 6.6 20 L20 20", "M7.5 15 L11 10.5 L14 13 L19 7")
    }

    val Table: ImageVector by lazy {
        symbol(
            "Table",
            "M9.03 4 L14.97 4 C16.818 4 17.742 4 18.448 4.36 C19.069 4.676 19.574 5.181 19.89 5.802 C20.25 6.508 20.25 7.432 20.25 9.28 L20.25 14.72 C20.25 16.568 20.25 17.492 19.89 18.198 C19.574 18.819 19.069 19.324 18.448 19.64 C17.742 20 16.818 20 14.97 20 L9.03 20 C7.182 20 6.258 20 5.552 19.64 C4.931 19.324 4.426 18.819 4.11 18.198 C3.75 17.492 3.75 16.568 3.75 14.72 L3.75 9.28 C3.75 7.432 3.75 6.508 4.11 5.802 C4.426 5.181 4.931 4.676 5.552 4.36 C6.258 4 7.182 4 9.03 4 Z",
            "M3.75 9.5 L20.25 9.5",
            "M3.75 14.75 L20.25 14.75",
            "M9.5 9.5 L9.5 20",
        )
    }

    /** Bars of different heights. */
    val Statistics: ImageVector by lazy {
        symbol("Statistics", "M6 20 L6 13", "M10 20 L10 8", "M14 20 L14 11", "M18 20 L18 4.5")
    }

    val Bluetooth: ImageVector by lazy {
        symbol("Bluetooth", "M7 7.5 L16.5 16 L12 20 L12 4 L16.5 8 L7 16.5")
    }

    val Drop: ImageVector by lazy {
        symbol("Drop", "M12 3.75 C12 3.75 5.75 10.3 5.75 14.25 C5.75 17.702 8.548 20.5 12 20.5 C15.452 20.5 18.25 17.702 18.25 14.25 C18.25 10.3 12 3.75 12 3.75 Z")
    }

    /** Tape measure ring. */
    val Tape: ImageVector by lazy {
        symbol(
            "Tape",
            "M12 4 C16.418 4 20 7.582 20 12 C20 16.418 16.418 20 12 20 C7.582 20 4 16.418 4 12 C4 7.582 7.582 4 12 4 Z",
            "M12 9.5 C13.381 9.5 14.5 10.619 14.5 12 C14.5 13.381 13.381 14.5 12 14.5 C10.619 14.5 9.5 13.381 9.5 12 C9.5 10.619 10.619 9.5 12 9.5 Z",
            "M20 12 L20 20 L12 20",
        )
    }

    val Ruler: ImageVector by lazy {
        symbol(
            "Ruler",
            "M4.6 15.4 L15.4 4.6 C15.9 4.1 16.6 4.1 17.1 4.6 L19.4 6.9 C19.9 7.4 19.9 8.1 19.4 8.6 L8.6 19.4 C8.1 19.9 7.4 19.9 6.9 19.4 L4.6 17.1 C4.1 16.6 4.1 15.9 4.6 15.4 Z",
            "M8 12 L9.75 13.75",
            "M10.75 9.25 L12.5 11",
            "M13.5 6.5 L15.25 8.25",
        )
    }

    val Flame: ImageVector by lazy {
        symbol("Flame", "M12 20.5 C15.3 20.5 18 18 18 14.5 C18 10.5 14.5 8.5 13.5 3.5 C11 5.5 10 8 10.25 10.5 C9.2 10 8.6 9 8.5 8 C6.9 9.6 6 11.9 6 14.5 C6 18 8.7 20.5 12 20.5 Z")
    }

    val Bolt: ImageVector by lazy {
        symbol("Bolt", "M13.25 3.75 L5.75 13.5 L11.5 13.5 L10.75 20.25 L18.25 10.5 L12.5 10.5 Z")
    }

    val Bone: ImageVector by lazy {
        symbol("Bone", "M8.6 13.3 L13.3 8.6 C13.1 7.6 13.4 6.5 14.2 5.8 C15.3 4.7 17.2 4.8 18.2 6.1 C18.5 6.5 18.7 6.9 18.8 7.3 C19.2 7.4 19.6 7.6 20 7.9 C21.2 8.9 21.3 10.7 20.2 11.8 C19.5 12.6 18.4 12.9 17.4 12.7 L12.7 17.4 C12.9 18.4 12.6 19.5 11.8 20.2 C10.7 21.3 8.9 21.2 7.9 20 C7.6 19.6 7.4 19.2 7.3 18.8 C6.9 18.7 6.5 18.5 6.1 18.2 C4.8 17.2 4.7 15.3 5.8 14.2 C6.5 13.4 7.6 13.1 8.6 13.3 Z")
    }

    /** Flexed arm. */
    val Muscle: ImageVector by lazy {
        symbol(
            "Muscle",
            "M4.5 19.5 C4.5 14 6.5 9 9.5 5.5 C10.4 4.4 12.1 4.6 12.6 5.9 L13.1 7.3 C13.4 8.1 12.9 9 12.1 9.2 L10.6 9.6 C10 11.4 9.8 13.2 10 15 C11.6 13.2 14 12.3 16.3 12.6 C18.3 12.9 19.75 14.6 19.75 16.6 L19.75 17.2 C19.75 18.5 18.7 19.5 17.4 19.5 Z",
        )
    }

    val Target: ImageVector by lazy {
        symbol(
            "Target",
            "M12 3.75 C16.556 3.75 20.25 7.444 20.25 12 C20.25 16.556 16.556 20.25 12 20.25 C7.444 20.25 3.75 16.556 3.75 12 C3.75 7.444 7.444 3.75 12 3.75 Z",
            "M12 8 C14.209 8 16 9.791 16 12 C16 14.209 14.209 16 12 16 C9.791 16 8 14.209 8 12 C8 9.791 9.791 8 12 8 Z",
            fills = listOf("M12 11 C12.552 11 13 11.448 13 12 C13 12.552 12.552 13 12 13 C11.448 13 11 12.552 11 12 C11 11.448 11.448 11 12 11 Z"),
        )
    }

    val Calendar: ImageVector by lazy {
        symbol(
            "Calendar",
            "M9.03 5 L14.97 5 C16.818 5 17.742 5 18.448 5.36 C19.069 5.676 19.574 6.181 19.89 6.802 C20.25 7.508 20.25 8.432 20.25 10.28 L20.25 14.97 C20.25 16.818 20.25 17.742 19.89 18.448 C19.574 19.069 19.069 19.574 18.448 19.89 C17.742 20.25 16.818 20.25 14.97 20.25 L9.03 20.25 C7.182 20.25 6.258 20.25 5.552 19.89 C4.931 19.574 4.426 19.069 4.11 18.448 C3.75 17.742 3.75 16.818 3.75 14.97 L3.75 10.28 C3.75 8.432 3.75 7.508 4.11 6.802 C4.426 6.181 4.931 5.676 5.552 5.36 C6.258 5 7.182 5 9.03 5 Z",
            "M3.75 10 L20.25 10",
            "M8.5 3.5 L8.5 6.5",
            "M15.5 3.5 L15.5 6.5",
        )
    }

    val Clock: ImageVector by lazy {
        symbol(
            "Clock",
            "M12 3.75 C16.556 3.75 20.25 7.444 20.25 12 C20.25 16.556 16.556 20.25 12 20.25 C7.444 20.25 3.75 16.556 3.75 12 C3.75 7.444 7.444 3.75 12 3.75 Z",
            "M12 7.5 L12 12 L15 14",
        )
    }

    val Upload: ImageVector by lazy {
        symbol("Upload", "M12 15 L12 4.5", "M7.75 8.5 L12 4.25 L16.25 8.5", "M4.5 14.5 L4.5 16.9 C4.5 18.336 5.664 19.5 7.1 19.5 L16.9 19.5 C18.336 19.5 19.5 18.336 19.5 16.9 L19.5 14.5")
    }

    val Ratio: ImageVector by lazy {
        symbol(
            "Ratio",
            "M7 4.5 C8.381 4.5 9.5 5.619 9.5 7 C9.5 8.381 8.381 9.5 7 9.5 C5.619 9.5 4.5 8.381 4.5 7 C4.5 5.619 5.619 4.5 7 4.5 Z",
            "M17 14.5 C18.381 14.5 19.5 15.619 19.5 17 C19.5 18.381 18.381 19.5 17 19.5 C15.619 19.5 14.5 18.381 14.5 17 C14.5 15.619 15.619 14.5 17 14.5 Z",
            "M18.5 5.5 L5.5 18.5",
        )
    }

    /** Pinch: two jaws of a skinfold caliper. */
    val Caliper: ImageVector by lazy {
        symbol("Caliper", "M5 4 L5 20", "M5 6 L19 6", "M5 10 L15 10", "M15 6 L15 13.5", "M19 6 L19 13.5 C19 15.433 17.433 17 15.5 17")
    }

    val Organ: ImageVector by lazy {
        symbol(
            "Organ",
            "M12 5.5 C9.5 3.5 4.5 4 4.5 9.5 C4.5 14.5 9 18.75 12 20.25 C15 18.75 19.5 14.5 19.5 9.5 C19.5 4 14.5 3.5 12 5.5 Z",
            "M12 5.5 L12 13",
        )
    }

    val Protein: ImageVector by lazy {
        symbol(
            "Protein",
            "M7 4 C9.5 6.5 14.5 6.5 17 4",
            "M7 20 C9.5 17.5 14.5 17.5 17 20",
            "M7 4 C12 9 12 15 7 20",
            "M17 4 C12 9 12 15 17 20",
        )
    }

    val Comment: ImageVector by lazy {
        symbol(
            "Comment",
            "M9.03 4.5 L14.97 4.5 C16.818 4.5 17.742 4.5 18.448 4.86 C19.069 5.176 19.574 5.681 19.89 6.302 C20.25 7.008 20.25 7.932 20.25 9.78 L20.25 11.22 C20.25 13.068 20.25 13.992 19.89 14.698 C19.574 15.319 19.069 15.824 18.448 16.14 C17.742 16.5 16.818 16.5 14.97 16.5 L10 16.5 L6 19.75 L6 16.4 C5.15 16.2 4.55 15.7 4.11 14.698 C3.75 13.992 3.75 13.068 3.75 11.22 L3.75 9.78 C3.75 7.932 3.75 7.008 4.11 6.302 C4.426 5.681 4.931 5.176 5.552 4.86 C6.258 4.5 7.182 4.5 9.03 4.5 Z",
        )
    }

    val Trend: ImageVector by lazy {
        symbol("Trend", "M4 17 L9.5 11.5 L13 15 L20 8", "M15.5 8 L20 8 L20 12.5")
    }

    val Swap: ImageVector by lazy {
        symbol("Swap", "M4.5 8.5 L19.5 8.5", "M15.5 4.5 L19.5 8.5 L15.5 12.5", "M19.5 15.5 L4.5 15.5", "M8.5 11.5 L4.5 15.5 L8.5 19.5")
    }
}
