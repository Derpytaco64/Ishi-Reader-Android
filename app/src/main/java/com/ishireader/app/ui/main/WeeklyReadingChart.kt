package com.ishireader.app.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ishireader.app.data.model.WeeklyBookTypeDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// CLAUDE-ADDED: Deliberately literal RGB, not the app's usual palette -- the user's own mnemonic for
// the stacking order (Red on top, Green in the middle, Blue at the bottom), so these three must stay
// distinguishable as "red/green/blue" rather than being tuned toward a design-system hue.
private val EpubColor = Color(0xFFE53935)
private val AudiobookColor = Color(0xFF43A047)
private val ComicColor = Color(0xFF1E88E5)

private val ChartHeight = 140.dp
private const val AreaFillAlpha = 0.35f

/**
 * Top-of-stats-screen graph: last 7 local calendar days of reading/listening time, stacked by book
 * type -- EPUB (red) on top, Audiobooks (green) in the middle, Manga/Comic (blue) at the bottom,
 * each band's fill a translucent version of its line color. Title is the covered date range; a
 * legend below names each color since identity is never color-alone.
 */
@Composable
fun WeeklyReadingChart(days: List<WeeklyBookTypeDay>, modifier: Modifier = Modifier) {
    if (days.isEmpty()) return

    val hasActivity = days.any { it.epubSeconds + it.comicSeconds + it.audiobookSeconds > 0.0 }
    val maxTotalSeconds = remember(days) {
        days.maxOf { it.epubSeconds + it.comicSeconds + it.audiobookSeconds }.coerceAtLeast(60.0)
    }
    val dayLabelFormatter = remember { DateTimeFormatter.ofPattern("EEE") }
    val n = days.size

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = formatDateRangeTitle(days),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxWidth().height(ChartHeight)) {
            if (!hasActivity) {
                Text(
                    text = "No reading this week",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            Canvas(modifier = Modifier.fillMaxWidth().height(ChartHeight)) {
                // CLAUDE-ADDED: Points sit at the center of n equal-width columns, matching the
                // fillMaxWidth/weight(1f) day-label Row below so the chart and its x-axis labels
                // line up exactly.
                fun xAt(i: Int) = (i + 0.5f) / n * size.width
                fun yAt(seconds: Double) = size.height - (seconds / maxTotalSeconds).toFloat() * size.height

                val zero = DoubleArray(n)
                val comicTop = DoubleArray(n) { days[it].comicSeconds }
                val audiobookTop = DoubleArray(n) { comicTop[it] + days[it].audiobookSeconds }
                val epubTop = DoubleArray(n) { audiobookTop[it] + days[it].epubSeconds }

                fun bandFillPath(bottom: DoubleArray, top: DoubleArray): Path = Path().apply {
                    for (i in 0 until n) {
                        val point = Offset(xAt(i), yAt(top[i]))
                        if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                    }
                    for (i in n - 1 downTo 0) lineTo(xAt(i), yAt(bottom[i]))
                    close()
                }

                fun topEdgePath(top: DoubleArray): Path = Path().apply {
                    for (i in 0 until n) {
                        val point = Offset(xAt(i), yAt(top[i]))
                        if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                    }
                }

                // Bottom-to-top fills first, so no band's fill can paint over a lower band's top line.
                drawPath(bandFillPath(zero, comicTop), color = ComicColor.copy(alpha = AreaFillAlpha))
                drawPath(bandFillPath(comicTop, audiobookTop), color = AudiobookColor.copy(alpha = AreaFillAlpha))
                drawPath(bandFillPath(audiobookTop, epubTop), color = EpubColor.copy(alpha = AreaFillAlpha))

                val lineStroke = Stroke(width = 2.dp.toPx())
                drawPath(topEdgePath(comicTop), color = ComicColor, style = lineStroke)
                drawPath(topEdgePath(audiobookTop), color = AudiobookColor, style = lineStroke)
                drawPath(topEdgePath(epubTop), color = EpubColor, style = lineStroke)

                drawLine(
                    color = ComicColor.copy(alpha = 0.4f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            days.forEach { day ->
                Text(
                    text = LocalDate.parse(day.date).format(dayLabelFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LegendItem(EpubColor, "EPUB")
            LegendItem(AudiobookColor, "Audiobook")
            LegendItem(ComicColor, "Manga/Comic")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, shape = RoundedCornerShape(2.dp)))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatDateRangeTitle(days: List<WeeklyBookTypeDay>): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    val start = LocalDate.parse(days.first().date)
    val end = LocalDate.parse(days.last().date)
    return "${start.format(formatter)} – ${end.format(formatter)}"
}
