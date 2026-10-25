# Aprender para jugar

Aplicación Android en Kotlin y Jetpack Compose para practicar lectura y ganar tiempo de juego.

## Estado actual

La primera base técnica incluye pantalla de inicio, selección de dificultad, un ejercicio demostrativo de emparejar palabras e iconos, cálculo proporcional de recompensa en segundos y un flujo de compilación automática con GitHub Actions.

**Todavía es un prototipo, no una versión lista para uso diario.** El saldo no persiste al cerrar la app; la biblioteca parental, PIN, categorías, imágenes locales, base de datos, historial, límite diario, temporizador robusto en segundo plano y restricciones reales del uso del dispositivo están pendientes. Los iconos son ilustrativos y aún no se usa una tipografía de lectoescritura ligada escolar.

## Compilar APK

Cada push a `main` o ejecución manual de la acción **Build Android APK** intenta compilar un APK de depuración. En GitHub, abre **Actions**, entra en la ejecución más reciente y descarga el artefacto `aprender-para-jugar-debug-apk` si el trabajo termina correctamente.

## Requisitos

- JDK 17
- Android SDK Platform 35
- Gradle 8.9

Paquete Android: `es.aprenderparajugar`.
