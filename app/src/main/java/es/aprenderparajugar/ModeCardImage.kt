package es.aprenderparajugar

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

@Composable
internal fun ModeCardImage(index: Int, description: String, modifier: Modifier = Modifier) {
    val imageResource = when (index) {
        0 -> R.drawable.activity_cards
        1 -> R.drawable.activity_sequences
        else -> R.drawable.activity_words
    }
    Image(
        painter = painterResource(id = imageResource),
        contentDescription = description,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
