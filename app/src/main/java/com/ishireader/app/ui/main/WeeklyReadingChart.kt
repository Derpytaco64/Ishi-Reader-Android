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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

// CLAUDE-ADDED: Deliberately literal RGB, not the app's usual palette -- the user's own choice, not
// tuned toward a design-system hue. Audiobook/Comic were swapped from the original red/green/blue
// stacking-order mnemonic per the user's explicit request, so the color-to-category mapping no longer
// matches the top/middle/bottom stack order -- that's intentional, not a bug.
private val EpubColor = Color(0xFFE53935)
private val AudiobookColor = Color(0xFF1E88E5)
private val ComicColor = Color(0xFF43A047)

private val ChartHeight = 140.dp
private val AxisLabelWidth = 36.dp
private const val AreaFillAlpha = 0.35f

/**
 * Top-of-stats-screen graph: 7 local calendar days of reading/listening time, stacked by book type --
 * EPUB on top, Audiobooks in the middle, Manga/Comic at the bottom, each band's fill a translucent
 * version of its line color. Title is the covered date range, flanked by prev/next-week arrows
 * ([onPreviousWeek]/[onNextWeek], the latter disabled once [canGoToNextWeek] is false -- there's no
 * "next week" past the one that includes today); a left-side time scale and a legend below (identity
 * is never color-alone) round it out.
 */
@Composable
fun WeeklyReadingChart(
    days: List<WeeklyBookTypeDay>,
    canGoToNextWeek: Boolean,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (days.isEmpty()) return

    val hasActivity = days.any { it.epubSeconds + it.comicSeconds + it.audiobookSeconds > 0.0 }
    val maxTotalSeconds = remember(days) {
        days.maxOf { it.epubSeconds + it.comicSeconds + it.audiobookSeconds }.coerceAtLeast(60.0)
    }
    val dayLabelFormatter = remember { DateTimeFormatter.ofPattern("EEE") }
    val n = days.size

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPreviousWeek) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous week")
            }
            Text(
                text = formatDateRangeTitle(days),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onNextWeek, enabled = canGoToNextWeek) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next week")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        val gridlineColor = MaterialTheme.colorScheme.onSurfaceVariant

        Row(modifier = Modifier.fillMaxWidth().height(ChartHeight)) {
            // CLAUDE-ADDED: Left-side time scale -- three labels (max/half/zero) distributed by
            // SpaceBetween across the same ChartHeight as the Canvas below, so they land next to the
            // matching gridline (text height is negligible next to 140.dp, so the middle label centers
            // close enough to size.height / 2). Recessive/neutral color, never a series color, per the
            // "text wears text tokens, not series color" rule -- this is the axis, not another band.
            Column(
                modifier = Modifier.width(AxisLabelWidth).height(ChartHeight),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                val topLabel = if (hasActivity) formatAxisSeconds(maxTotalSeconds) else ""
                val midLabel = if (hasActivity) formatAxisSeconds(maxTotalSeconds / 2) else ""
                Text(text = topLabel, style = MaterialTheme.typography.labelSmall, color = gridlineColor)
                Text(text = midLabel, style = MaterialTheme.typography.labelSmall, color = gridlineColor)
                Text(text = "0", style = MaterialTheme.typography.labelSmall, color = gridlineColor)
            }

            Spacer(modifier = Modifier.width(4.dp))

            Box(modifier = Modifier.weight(1f).height(ChartHeight)) {
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

                    // Recessive gridlines at the same max/half/zero levels as the axis labels.
                    val gridStroke = Stroke(width = 1.dp.toPx())
                    val gridlinePaintColor = gridlineColor.copy(alpha = 0.2f)
                    listOf(0f, 0.5f, 1f).forEach { fraction ->
                        val y = size.height * fraction
                        drawLine(
                            color = gridlinePaintColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = gridStroke.width
                        )
                    }

                    // Bottom-to-top fills first, so no band's fill can paint over a lower band's top line.
                    drawPath(bandFillPath(zero, comicTop), color = ComicColor.copy(alpha = AreaFillAlpha))
                    drawPath(bandFillPath(comicTop, audiobookTop), color = AudiobookColor.copy(alpha = AreaFillAlpha))
                    drawPath(bandFillPath(audiobookTop, epubTop), color = EpubColor.copy(alpha = AreaFillAlpha))

                    val lineStroke = Stroke(width = 2.dp.toPx())
                    drawPath(topEdgePath(comicTop), color = ComicColor, style = lineStroke)
                    drawPath(topEdgePath(audiobookTop), color = AudiobookColor, style = lineStroke)
                    drawPath(topEdgePath(epubTop), color = EpubColor, style = lineStroke)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(AxisLabelWidth + 4.dp))
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

/** Compact single-unit axis label ("2h"/"45m"/"30s") -- unlike formatFullReadingTime, an axis tick
 *  doesn't need every unit down to the second, just enough precision to read the scale at a glance. */
private fun formatAxisSeconds(seconds: Double): String {
    val whole = seconds.toLong()
    return when {
        whole >= 3600 -> "${whole / 3600}h"
        whole >= 60 -> "${whole / 60}m"
        else -> "${whole}s"
    }
}

private fun formatDateRangeTitle(days: List<WeeklyBookTypeDay>): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    val start = LocalDate.parse(days.first().date)
    val end = LocalDate.parse(days.last().date)
    return "${start.format(formatter)} – ${end.format(formatter)}"
}
