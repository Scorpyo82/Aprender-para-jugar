package es.aprenderparajugar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PlayTimeScreen(
    balanceText: String,
    exerciseFontFamily: FontFamily,
    onResumeLearning: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "¡Hola de nuevo!",
            fontSize = 29.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = exerciseFontFamily,
            color = Color(0xFF713F20),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "¿Seguimos aprendiendo?",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = exerciseFontFamily,
            color = Color(0xFF9A5B2D),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Image(
            painter = painterResource(id = R.drawable.home_header_fox),
            contentDescription = "El zorrito de Aprender para jugar",
            modifier = Modifier.fillMaxWidth().height(172.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE9C7)),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Tu tiempo disponible",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = exerciseFontFamily,
                    color = Color(0xFF75451F),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    balanceText,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = exerciseFontFamily,
                    color = Color(0xFF267A55),
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onResumeLearning,
            modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp)
        ) {
            Text(
                "¡Volver a aprender!",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = exerciseFontFamily,
                textAlign = TextAlign.Center
            )
        }
    }
}
