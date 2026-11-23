package es.aprenderparajugar

import androidx.compose.foundation.lazy.LazyListScope

internal fun LazyListScope.parentMenuItems(
    rewardMode: RewardMode,
    linkedExerciseFont: Boolean,
    onSelectTime: () -> Unit,
    onSelectStars: () -> Unit,
    onOpenTour: () -> Unit,
    onNavigate: (String) -> Unit,
    onOpenLibraryTransfer: () -> Unit,
    onOpenDeviceSetup: () -> Unit
) {
                item {
                    RewardModeSelectorCard(
                        rewardMode = rewardMode,
                        onSelectTime = { onSelectTime() },
                        onSelectStars = { onSelectStars() }
                    )
                }
                item {
                    ParentMenuTourCard(
                        onOpenTour = { onOpenTour() }
                    )
                }
                item {
                    ParentMenuNavigationCard("🎮", "Configurar actividades", "Opciones específicas de cada modalidad") {
                        onNavigate("game-modes")
                    }
                }
                item { ParentMenuSectionLabel("CONFIGURACIÓN GENERAL") }
                item {
                    ParentMenuNavigationCard(
                        "🔤",
                        "Tipografía de los ejercicios",
                        if (linkedExerciseFont) "Playwrite ES · Letra escolar ligada" else "Fuente del teléfono"
                    ) {
                        onNavigate("typography")
                    }
                }
                item {
                    ParentMenuActiveGamesCard(onClick = { onNavigate("active-games") })
                }
                item {
                    ParentMenuNavigationCard("⏱️", "Recompensas y límites", "Minutos/Estrellas por dificultad") {
                        onNavigate("rewards")
                    }
                }
                item {
                    ParentMenuNavigationCard("🔊", "Ajustes de sonido", "Música, audio de cuentos y efectos de logros") {
                        onNavigate("sound-settings")
                    }
                }
                item {
                    ParentMenuNavigationCard("☁️⬇️", "Descargar contenido online", "Explorar y descargar bibliotecas y paquetes de contenido desde Internet") {
                        onNavigate("basic-library")
                    }
                }
                item {
                    ParentMenuNavigationCard("📦", "Importar y exportar bibliotecas", "Copias selectivas de Tarjetas, Palabras y Secuencias") { onOpenLibraryTransfer() }
                }

                item {
                    ParentMenuNavigationCard("📊", "Estadísticas", "Consulta el aprendizaje y el uso de la aplicación") {
                        onNavigate("statistics-menu")
                    }
                }
                item {
                    ParentMenuAboutCard(onClick = { onNavigate("about") })
                }
                item {
                    ParentMenuNavigationCard("🔐", "Cambiar PIN", "Modificar el PIN de acceso familiar") {
                        onNavigate("security")
                    }
                }
                item { ParentMenuSectionDivider() }
                item {
                    ParentMenuNavigationCard(
                        "⚙️",
                        "Asistente de permisos",
                        if (rewardMode.isStars) "Prepara el dispositivo para activar el modo Tiempo" else "Comprueba y configura la protección del tiempo"
                    ) { onOpenDeviceSetup() }
                }

}
