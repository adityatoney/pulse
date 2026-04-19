package com.pulse.core.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulse.core.designsystem.theme.SuccessGreen
import com.pulse.core.designsystem.theme.WarnAmber

enum class InsightSentimentUi { Positive, Neutral, Negative, Celebratory }

@Composable
fun InsightCard(
    headline: String,
    body: String,
    sentiment: InsightSentimentUi,
    modifier: Modifier = Modifier,
) {
    val (icon, tint, bgColor) = when (sentiment) {
        InsightSentimentUi.Positive -> Triple(Icons.Outlined.TrendingUp, SuccessGreen, SuccessGreen.copy(alpha = 0.08f))
        InsightSentimentUi.Negative -> Triple(Icons.Outlined.TrendingDown, Color(0xFFB3261E), Color(0xFFB3261E).copy(alpha = 0.08f))
        InsightSentimentUi.Neutral -> Triple(Icons.Outlined.AutoAwesome, WarnAmber, WarnAmber.copy(alpha = 0.08f))
        InsightSentimentUi.Celebratory -> Triple(Icons.Outlined.EmojiEvents, Color(0xFFCC8800), Color(0xFFCC8800).copy(alpha = 0.10f))
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = tint,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun InsightCardList(
    insights: List<InsightCardData>,
    modifier: Modifier = Modifier,
) {
    if (insights.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        insights.forEach { data ->
            InsightCard(
                headline = data.headline,
                body = data.body,
                sentiment = data.sentiment,
            )
        }
    }
}

data class InsightCardData(
    val headline: String,
    val body: String,
    val sentiment: InsightSentimentUi,
)
