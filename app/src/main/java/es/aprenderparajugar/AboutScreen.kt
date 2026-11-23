package es.aprenderparajugar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AboutScreen(
    versionName: String,
    versionCode: Int,
    buildDate: String,
    onOpenUri: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Acerca de esta app", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("Aprender para jugar", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3567A8))
            Text("Una herramienta para aprender jugando.", fontSize = 14.sp, color = Color(0xFF667085))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sobre el proyecto", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Aplicación educativa para practicar vocabulario, asociación de palabras e imágenes y secuencias. El aprendizaje se refuerza mediante recompensas y controles configurables por la familia.", fontSize = 14.sp, color = Color(0xFF667085))
                Text("Creada por Miguel Ponce Torres, con ayuda de inteligencia artificial como herramienta de apoyo en el diseño, la programación y la resolución de problemas.", fontSize = 13.sp, color = Color(0xFF667085))
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Colabora con otras causas", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("La aplicación es gratuita. Si te apetece apoyar alguna iniciativa relacionada con el autismo, puedes conocer estas organizaciones y colaborar directamente con ellas.", fontSize = 14.sp, color = Color(0xFF667085))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Fundación Miradas", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Investigación, conocimiento y defensa de los derechos de las personas autistas.", fontSize = 13.sp, color = Color(0xFF667085))
                    OutlinedButton(onClick = { onOpenUri("https://fundacionmiradas.org/") }, modifier = Modifier.fillMaxWidth()) { Text("Conocer Fundación Miradas ↗") }
                }
                androidx.compose.material3.HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Autismo España", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Representación, investigación y apoyo a las personas autistas y sus familias.", fontSize = 13.sp, color = Color(0xFF667085))
                    OutlinedButton(onClick = { onOpenUri("https://autismo.org.es/") }, modifier = Modifier.fillMaxWidth()) { Text("Conocer Autismo España ↗") }
                }
                androidx.compose.material3.HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Fundación ConecTEA", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Orientación, intervención y acompañamiento a personas autistas y sus familias.", fontSize = 13.sp, color = Color(0xFF667085))
                    OutlinedButton(onClick = { onOpenUri("https://www.fundacionconectea.org/") }, modifier = Modifier.fillMaxWidth()) { Text("Conocer Fundación ConecTEA ↗") }
                }
                Text("Las donaciones son voluntarias y, si decides realizarlas, se hacen directamente a cada entidad. Aprender para jugar no recibe dinero. No es necesario donar para utilizar la aplicación.", fontSize = 12.sp, color = Color(0xFF667085))
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Código abierto y colaboración", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("El código fuente está disponible públicamente para consultar el desarrollo, revisar los cambios y colaborar.", fontSize = 14.sp, color = Color(0xFF667085))
                OutlinedButton(onClick = { onOpenUri("https://github.com/Scorpyo82/Aprender-para-jugar") }, modifier = Modifier.fillMaxWidth()) { Text("Ver proyecto en GitHub ↗") }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Información de versión", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Versión $versionName (código $versionCode)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Fecha de compilación: $buildDate", fontSize = 13.sp, color = Color(0xFF667085))
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Estado del proyecto", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("La aplicación continúa en desarrollo. Las funciones y la interfaz pueden cambiar entre versiones. Las mejoras y correcciones se realizarán según la disponibilidad del desarrollador.", fontSize = 13.sp, color = Color(0xFF667085))
            }
        }
    }
}
