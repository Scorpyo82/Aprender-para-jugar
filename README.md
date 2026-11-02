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

La pantalla de estadísticas familiares resume intentos, aciertos, errores y ejercicios completados por modalidad, tipo de secuencia y categoría. Incluye áreas descriptivas como asociación palabra-imagen y construcción de palabras, y señala expresamente cuándo una habilidad —por ejemplo, comprensión lectora de textos— no puede valorarse con los ejercicios disponibles.

**La aplicación no diagnostica ni evalúa clínicamente, y no determina lo que un niño es o no es capaz de hacer.** Únicamente ofrece actividades y registra cómo interactúa con ellas. Los resultados dependen de los ejercicios disponibles y de su diseño, de la experiencia previa y de factores como la atención, la motivación, la memoria o la familiaridad con patrones y símbolos.

Un acierto o un error aislado no demuestra que una capacidad esté adquirida o ausente. Un niño puede desarrollar estrategias propias, establecer asociaciones inesperadas o encontrar soluciones que el ejercicio no había previsto, incluso relaciones que a un adulto podrían pasarle inadvertidas. Por eso es importante observar también el proceso y no limitar sus posibilidades a las cifras registradas.

Las estadísticas son exclusivamente orientativas y descriptivas: **no son pruebas estandarizadas, diagnósticos ni conclusiones pedagógicas definitivas**, y no sustituyen la valoración profesional cuando sea necesaria.

## Bibliotecas y copias de seguridad

Las bibliotecas se almacenan de forma privada en el dispositivo, separadas por modalidad, categoría y ejercicio. La función de importación y exportación permite crear y restaurar paquetes ZIP unificados, con selección de categorías y posibilidad de incluir estadísticas.

Conserva una copia externa de tus bibliotecas antes de realizar cambios importantes o eliminar contenido.

## Estado del repositorio y punto de partida

La rama **main** se actualizó para incorporar íntegramente el estado de **refactor/arquitectura-almacenamiento-contenidos**, mediante una actualización que conservó el historial de cambios y sin modificar ni eliminar la rama refactor.

- Commit de integración del código: **bf63cc791e0311d55d4def3191350a8179963c64**.
- En el momento de la integración, ambas ramas quedaron idénticas.
- La compilación de GitHub Actions correspondiente a ese commit finalizó correctamente: [Build Android APK — ejecución 352](https://github.com/Scorpyo82/Aprender-para-jugar/actions/runs/41293585125).
- Posteriormente, este README se ha actualizado en main para documentar el estado del proyecto. Por ello, main contiene ahora un commit adicional exclusivamente documental; la rama refactor permanece intacta.

Se ha acordado identificar este punto de partida mediante la etiqueta **v0.4.1-beta-estable**. La etiqueta está pendiente de creación en GitHub; por tanto, todavía no debe considerarse publicada. Cuando se cree, deberá señalar el commit de main que contiene esta documentación actualizada.

## Versión

- Versión de la aplicación: **0.4.1-beta**
- Código de versión Android: **40**
- Paquete: es.aprenderparajugar
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
