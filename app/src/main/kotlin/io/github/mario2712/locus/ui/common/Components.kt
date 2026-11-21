package io.github.mario2712.locus.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun TypeDot(colorArgb: Long, size: Dp = 12.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(Color(colorArgb), CircleShape))
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Eine Zeile pro Aufenthalt: Farbe der Art, Zeitspanne, Art · Ort, Personen. */
@Composable
fun StayRow(item: StayItem, onClick: (() -> Unit)? = null, showDates: Boolean = false) {
    val stay = item.stay
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TypeDot(item.typeColor, 14.dp)
        Column(Modifier.weight(1f)) {
            Text("${item.typeName} · ${item.placeName}", style = MaterialTheme.typography.bodyLarge)
            val details = buildList {
                if (showDates || stay.endDate != stay.startDate) {
                    add(
                        if (stay.endDate == stay.startDate) Format.shortDate(stay.startDate)
                        else "${Format.shortDate(stay.startDate)}–${Format.shortDate(stay.endDate)}",
                    )
                }
                Format.timeRange(stay.startTime, stay.endTime).takeIf { it.isNotEmpty() }?.let(::add)
                add(item.participants)
            }.joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
