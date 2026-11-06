package es.aprenderparajugar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun WelcomeTourScreen(onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF8E8))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Conoce Aprender para jugar",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF253344),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Una herramienta para aprender, practicar y disfrutar. Cada niño tiene su propio ritmo y sus propios intereses.",
            fontSize = 16.sp,
            color = Color(0xFF667085),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text("¿Qué actividades incluye?", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color(0xFF253344))
        TourActivityCard(
            image = R.drawable.activity_cards,
            title = "Tarjetas",
            description = "Relacionar palabras e imágenes, reconocer conceptos y ampliar vocabulario."
        )
        TourActivityCard(
            image = R.drawable.activity_sequences,
            title = "Secuencias",
            description = "Ordenar acontecimientos y comprender procesos, el orden temporal, las causas y consecuencias y los ciclos."
        )
        TourActivityCard(
            image = R.drawable.activity_words,
            title = "Palabras",
            description = "Construir palabras ordenando letras, con apoyo visual para practicar la lectoescritura."
        )
        TourActivityCard(
            image = R.drawable.activity_stories,
            title = "Cuentos",
            description = "Leer, escuchar y descubrir historias ilustradas. Las familias pueden crear cuentos propios, organizar escenas y añadir imágenes y audios."
        )

        TourInfoCard(
            title = "La familia también forma parte del aprendizaje",
            body = "Los tutores pueden crear y adaptar ejercicios y cuentos, elegir imágenes, textos y audios, y organizar sus propias bibliotecas. También pueden importar y exportar contenidos para seguir personalizando la experiencia."
        )
        TourInfoCard(
            title = "Nadie conoce a tu hijo o hija mejor que tú",
            body = "Las familias saben qué despierta su curiosidad y qué puede motivarles. En mi caso, mi hijo es un apasionado de los coches. Por eso procuro adaptar muchos contenidos a ese mundo para captar su atención. No me importa crear cuentos de coches o inventar ejercicios sobre vehículos si con ello consigo que aprenda a leer con soltura. Cuando conectamos el aprendizaje con aquello que les apasiona, aprender puede convertirse en algo que desean hacer."
        )
        TourInfoCard(
            title = "Aprender también tiene recompensa",
            body = "Al completar actividades se consiguen recompensas. La aplicación dispone de un inventario y una tienda para descubrir objetos, conseguirlos y utilizarlos como motivación. La familia configura cómo quiere utilizar las recompensas."
        )
        Text(
            "No se trata de competir ni de hacerlo todo deprisa. Se trata de acompañar, practicar y avanzar a su ritmo.",
            fontSize = 15.sp,
            color = Color(0xFF667085),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(2.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Empezar", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TourActivityCard(image: Int, title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                painter = painterResource(image),
                contentDescription = null,
                modifier = Modifier.size(76.dp),
                contentScale = ContentScale.Fit
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF253344))
                Text(description, fontSize = 14.sp, color = Color(0xFF667085))
            }
        }
    }
}

@Composable
private fun TourInfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF8))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF253344))
            Text(body, fontSize = 14.sp, color = Color(0xFF475467))
        }
    }
}
