# Aprender para jugar

**Aplicación Android en fase beta** para practicar habilidades de lectoescritura, asociación y secuenciación mediante actividades breves, con un sistema de recompensas de tiempo de juego gestionado por la familia.

> **Estado: Beta en desarrollo.** La aplicación está en pruebas. Las funciones, los contenidos y la interfaz pueden cambiar; se recomienda conservar copias de seguridad de las bibliotecas antes de actualizar o probar operaciones de importación y eliminación.

## Modalidades de aprendizaje

- **Tarjetas:** asociación de palabras e imágenes.
- **Palabras:** construcción de palabras mediante letras y apoyo visual.
- **Secuencias:** ordenar situaciones y procesos en cuatro tipos: orden temporal, procesos, causa y consecuencia, y ciclos.

Cada modalidad dispone de bibliotecas organizadas por categorías. Los ejercicios pueden incluir imágenes locales y metadatos de dificultad.

## Control parental

La zona familiar permite configurar las modalidades activas, categorías, recompensas, límites y opciones de seguridad. La aplicación incorpora un saldo de tiempo ganado mediante la realización de ejercicios y un contador de tiempo de juego en segundo plano.

Se está desarrollando una pantalla de estadísticas orientativas para que las familias puedan observar resultados por áreas y modalidades. Estas métricas describen el rendimiento registrado en los ejercicios; **no son diagnósticos, pruebas estandarizadas ni conclusiones pedagógicas definitivas**.

## Bibliotecas y copias de seguridad

Las bibliotecas se almacenan de forma privada en el dispositivo, separadas por modalidad, categoría y ejercicio. La función de importación y exportación permite crear y restaurar paquetes ZIP unificados, con selección de categorías y posibilidad de incluir estadísticas.

Conserva una copia externa de tus bibliotecas antes de realizar cambios importantes o eliminar contenido.

## Versión

- Versión de la aplicación: **0.4.0-beta**
- Código de versión Android: **39**
- Paquete: `es.aprenderparajugar`
- Tecnología: Kotlin y Jetpack Compose.

## Compilar APK

El repositorio utiliza GitHub Actions para compilar la aplicación Android. Abre la pestaña **Actions**, selecciona la ejecución de **Build Android APK** y descarga el artefacto de APK cuando el trabajo haya terminado correctamente.

## Requisitos de desarrollo

- JDK 17
- Android SDK Platform 35
- Gradle 8.9
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21

## Aviso

Esta aplicación es una herramienta educativa complementaria y de apoyo familiar. No sustituye la valoración de profesionales de educación, pedagogía, logopedia o salud. Las estadísticas dependen de los ejercicios disponibles, de cómo se hayan diseñado y del número de intentos registrados.
