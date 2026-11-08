package es.aprenderparajugar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

@Composable
internal fun HomeFoxHeader() {
    Image(
        painter = painterResource(id = R.drawable.home_header_fox),
        contentDescription = "¡Aprender para jugar! Aprende, descubre y diviértete",
        modifier = Modifier.fillMaxWidth().aspectRatio(2f),
        contentScale = ContentScale.Fit
    )
}
