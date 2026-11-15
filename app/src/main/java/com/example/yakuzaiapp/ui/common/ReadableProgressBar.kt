package com.example.yakuzaiapp.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

private val UnfilledProgressTextColor = Color(0xFF003A8C)

@Composable
fun ReadableProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val fraction = progress.coerceIn(0f, 1f)
    val label = "${(fraction * 100).toInt()}%"

    Box(
        modifier = modifier.clearAndSetSemantics {
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
        },
        contentAlignment = Alignment.Center,
    ) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.matchParentSize(),
        )
        Text(
            text = label,
            style = textStyle,
            fontWeight = FontWeight.Bold,
            color = UnfilledProgressTextColor,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    clipRect(right = size.width * fraction) {
                        this@drawWithContent.drawContent()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = textStyle,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
