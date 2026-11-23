package es.aprenderparajugar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

/**
 * Pantalla principal de modalidades, aislada de la composición raíz.
 * La lógica de navegación y persistencia permanece en AprenderApp.
 */
@Composable
fun HomeDashboardScreen(
    cardsAllQuotaLocked: Boolean,
    wordsAllQuotaLocked: Boolean,
    sequencesAllQuotaLocked: Boolean,
    isStarsMode: Boolean,
    formattedBalance: String,
    balanceSeconds: Int,
    balanceMinutes: Int,
    balanceRemainderSeconds: Int,
    soundEnabled: Boolean,
    gameActiveCards: Boolean,
    gameActiveSequences: Boolean,
    gameActiveWords: Boolean,
    gameActiveStories: Boolean,
    minimumPlayMinutes: Int,
    canStartPlaySession: Boolean,
    showParentPlaceholder: Boolean,
    onOpenSettings: () -> Unit,
    onToggleSound: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenCards: () -> Unit,
    onOpenSequences: () -> Unit,
    onOpenWords: () -> Unit,
    onOpenStories: () -> Unit,
    onOpenInventory: () -> Unit,
    onExit: () -> Unit,
    onStartPlaySession: () -> Unit
) {
    val homeWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val modeImageSize = if (homeWidth >= 700) 210.dp else if (homeWidth >= 500) 165.dp else 118.dp
    val modeCardPadding = if (homeWidth >= 700) 20.dp else 12.dp
    val modeTitleSize = if (homeWidth >= 700) 27.sp else 21.sp
    val modeDescriptionSize = if (homeWidth >= 700) 17.sp else 14.sp
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(if (homeWidth >= 700) 28.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HomeTopActions(
                soundEnabled = soundEnabled,
                onOpenSettings = onOpenSettings,
                onToggleSound = onToggleSound,
                onOpenHelp = onOpenHelp
            )
        }
        item {
            HomeRewardBalanceCard(
                isStars = isStarsMode,
                formattedBalance = formattedBalance,
                minutes = balanceMinutes,
                remainderSeconds = balanceRemainderSeconds
            )
        }
        item { HomeFoxHeader() }
        item { HomeSectionTitle("Elige cómo aprender") }
        item {
            Card(
                Modifier.fillMaxWidth().clickable(enabled = gameActiveCards && !cardsAllQuotaLocked, onClick = onOpenCards),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0B8)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8B95D)),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(modeCardPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ModeCardImage(0, "Ilustración de Tarjetas", Modifier.width(modeImageSize).height(modeImageSize))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Tarjetas", fontSize = modeTitleSize, fontWeight = FontWeight.ExtraBold, color = Color(0xFF754411))
                            if (cardsAllQuotaLocked) Text("⌛", fontSize = 17.sp)
                        }
                        Text("Aprender con imágenes", fontSize = modeDescriptionSize, color = Color(0xFF795A3C))
                        Text(when { !gameActiveCards -> "🔒 BLOQUEADO"; cardsAllQuotaLocked -> "BLOQUEADO TEMPORALMENTE"; else -> "EMPEZAR  ›" }, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (gameActiveCards) Color(0xFF754411) else Color(0xFFB3261E))
                    }
                }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth().clickable(enabled = gameActiveSequences && !sequencesAllQuotaLocked, onClick = onOpenSequences),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFDDF3D3)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA9D79A))
            ) {
                Row(Modifier.fillMaxWidth().padding(modeCardPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ModeCardImage(1, "Ilustración de Secuencias", Modifier.width(modeImageSize).height(modeImageSize))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Secuencias", fontSize = modeTitleSize, fontWeight = FontWeight.ExtraBold, color = Color(0xFF245C2A))
                            if (sequencesAllQuotaLocked) Text("⌛", fontSize = 17.sp)
                        }
                        Text("Ordenar y razonar", fontSize = modeDescriptionSize, color = Color(0xFF476C51))
                        Text(when { !gameActiveSequences -> "🔒 BLOQUEADO"; sequencesAllQuotaLocked -> "BLOQUEADO TEMPORALMENTE"; else -> "EMPEZAR  ›" }, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (gameActiveSequences) Color(0xFF39713A) else Color(0xFFB3261E))
                    }
                }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth().clickable(enabled = gameActiveWords && !wordsAllQuotaLocked, onClick = onOpenWords),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD8EEFF)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF9CCCF4))
            ) {
                Row(Modifier.fillMaxWidth().padding(modeCardPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ModeCardImage(2, "Ilustración de Palabras", Modifier.width(modeImageSize).height(modeImageSize))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Palabras", fontSize = modeTitleSize, fontWeight = FontWeight.ExtraBold, color = Color(0xFF24537A))
                            if (wordsAllQuotaLocked) Text("⌛", fontSize = 17.sp)
                        }
                        Text("Ordenar letras y completar palabras", fontSize = modeDescriptionSize, color = Color(0xFF42647D))
                        Text(when { !gameActiveWords -> "🔒 BLOQUEADO"; wordsAllQuotaLocked -> "BLOQUEADO TEMPORALMENTE"; else -> "¡JUGAR!" }, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (gameActiveWords) Color(0xFF285F94) else Color(0xFFB3261E))
                    }
                }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth().clickable(enabled = gameActiveStories, onClick = onOpenStories),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE5D2)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8B28D))
            ) {
                Row(Modifier.fillMaxWidth().padding(modeCardPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Image(painter = painterResource(id = R.drawable.activity_stories), contentDescription = "El zorro leyendo un cuento", modifier = Modifier.width(modeImageSize).height(modeImageSize), contentScale = ContentScale.Fit)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Cuentos", fontSize = modeTitleSize, fontWeight = FontWeight.ExtraBold, color = Color(0xFF87451F))
                        Text("Leer, escuchar y descubrir historias", fontSize = modeDescriptionSize, color = Color(0xFF795A3C))
                        Text(if (gameActiveStories) "EMPEZAR  ›" else "🔒 BLOQUEADO", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (gameActiveStories) Color(0xFF87451F) else Color(0xFFB3261E))
                    }
                }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth().clickable(onClick = onOpenInventory),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8E5FA)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB9AFE8))
            ) {
                Row(Modifier.fillMaxWidth().padding(modeCardPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.width(modeImageSize).height(modeImageSize).background(Color(0xFFD8D0F4), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                        Text("🎒", fontSize = if (homeWidth >= 700) 72.sp else 48.sp)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Inventario", fontSize = modeTitleSize, fontWeight = FontWeight.ExtraBold, color = Color(0xFF493B82))
                        Text("Tus objetos conseguidos y los que puedes comprar", fontSize = modeDescriptionSize, color = Color(0xFF62578D))
                        Text("VER OBJETOS  ›", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF493B82))
                    }
                }
            }
        }
        if (!isStarsMode) item {
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Text("🔒 Salir de la aplicación", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text("Completa ejercicios para acumular minutos de juego.", fontSize = 12.sp, color = Color(0xFF667085))
            Button(
                onClick = onStartPlaySession,
                enabled = canStartPlaySession,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF267A55), disabledContainerColor = Color(0xFFE7E1D8), disabledContentColor = Color(0xFF667085))
            ) {
                Text(
                    when {
                        balanceSeconds <= 0 -> "🔒 Sin tiempo para jugar"
                        !canStartPlaySession -> "🔒 Necesitas $minimumPlayMinutes min para empezar"
                        else -> "▶ Jugar con el tiempo ganado"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (showParentPlaceholder) item {
            Text("Configuración parental pendiente: PIN, categorías y biblioteca de palabras se añadirán en una siguiente fase.")
        }
    }
}
