# Continuidad de la refactorización de Aprender para jugar

## Estado de referencia

- Repositorio: `Scorpyo82/Aprender-para-jugar`.
- Rama de trabajo: `main`.
- Último HEAD verificado: `2dfb2f213a9e101e86a6546d9203a7337182616c` para el código de la fase 7; la actualización documental más reciente queda registrada en el historial de commits de `main`.
- Archivo principal: `app/src/main/java/es/aprenderparajugar/MainActivity.kt`.
- Tamaño observado en la auditoría: 4.881 líneas.
- Este documento es la referencia de continuidad. Actualizarlo al cerrar cada fase.

La rama `refactor/` no forma parte del flujo de trabajo acordado. No cambiar de rama ni trabajar en ella salvo petición expresa del usuario.

## Objetivo arquitectónico

Reducir progresivamente la responsabilidad de `AprenderApp()` para evitar que siga creciendo en complejidad y reducir el riesgo de errores del compilador/verificador de Kotlin Compose, incluido el `VerifyError` observado anteriormente.

Criterios acordados:

1. `AprenderApp()` debe actuar principalmente como coordinador de pantallas y flujos.
2. No añadir nuevas funcionalidades acumulando dentro de `AprenderApp()` estado, persistencia o lógica de negocio.
3. Cada funcionalidad extraída debe tener sus componentes/estado/lógica en archivos propios y bien delimitados.
4. Separar la interfaz de la gestión de datos y de las operaciones de negocio.
5. No hacer extracciones puramente cosméticas: mover solo UI mientras el estado y la lógica permanecen en `AprenderApp()` no cumple el objetivo.
6. Preservar el comportamiento existente. La refactorización no debe introducir cambios funcionales no solicitados.

## Historial de extracciones completadas

Las siguientes seis extracciones se han realizado sobre `main`. El usuario ha validado el funcionamiento en el dispositivo de las fases descritas.

### 1. Estado del editor de cuentos

- Archivos: `StoryEditorState.kt` e integración en `MainActivity.kt`.
- Responsabilidad extraída: estado del editor, colecciones/escenas, multimedia y persistencia del borrador.
- Commits:
  - `729d233008b67d356b7073776a2dbf0f7831422b` — creación del estado.
  - `3e4ab389429ed24fb77d8eda899c243997b6748a` — integración.
  - `0c1c14fe1fb6a1cc4332e41001bd2b8b83d009a1` — corrección del parámetro `context`.
- GitHub Actions: run `41749020158`, correcto.
- Validación del usuario: arranque correcto y datos guardados correctamente.

### 2. Estado de colecciones de cuentos

- Archivo: `StoryCollectionsState.kt`.
- Responsabilidad extraída: estado y operaciones de las colecciones de cuentos, usando `SharedPreferences`.
- Commits:
  - `884bd0cf94f5fc5f38b2ef234d43865acc2cbd9d`
  - `02f9b780d551cece6bed207eaf482879575dff88`
- GitHub Actions: run `41751130365`, correcto.
- Validación: incluida entre las fases que el usuario dio por buenas durante la continuidad; no consta una prueba independiente más detallada en el registro.

### 3. Estado de colecciones de objetos de cuentos

- Archivo: `StoryObjectCollectionsState.kt`.
- Responsabilidad extraída: listar, añadir, validar duplicados, eliminar y cancelar operaciones de colecciones de objetos.
- Commits:
  - `6e04d6987c3f700750b67ec2aa1c2cf6b4bff55b`
  - `59a8c79942ee38bc810a9557debdc6490a890c37`
- GitHub Actions: run `41752198008`, correcto.
- Validación del usuario: la aplicación arrancó y funcionó correctamente.

### 4. Estado del editor de objetos de cuentos

- Archivo: `StoryObjectEditorState.kt`.
- Responsabilidad extraída: estado del formulario, edición, selección de imagen, validación y persistencia de objetos.
- Commits relevantes:
  - `444cec93a4d8270547058de5d22de9010f5f4771`
  - `ecd85e8dc863040ace222cb73983bf8bd990511d`
- GitHub Actions: runs `41755587693`, `41755628996`, `41755641092`, `41755653416` y `41755671247`, correctos.
- Validación del usuario: funcionamiento correcto confirmado con capturas de colecciones e inventario.

### 5. Estado del inventario de objetos de cuentos

- Archivo: `StoryObjectInventoryState.kt`.
- Responsabilidad extraída: estado y operaciones del inventario (`folders`, `refresh`, `toggle`, `remove`) mediante `InventoryRepository`.
- Commits:
  - `219008b2895d8cecf69d9789a66b50814dffac16`
  - `7e76d87d2be92a6b5db9b2341ac0c97d19b5e047`
- GitHub Actions: el estado final del run asociado no quedó registrado en esta nota de continuidad.
- Validación del usuario: «Todo ok» después de probar añadir, retirar, persistencia tras cerrar/reabrir y sincronización con el editor.

### 6. Estado de la biblioteca de Secuencias

- Archivo: `SequenceLibraryState.kt`.
- Responsabilidad extraída: estado de ejercicios de Secuencias, categorías, categorías deshabilitadas y filtros de tipo, categoría, dificultad y estado.
- Operaciones delegadas: guardar y recargar ejercicios mediante `ExerciseUiStorage`; se añadieron métodos de apoyo para persistencia/mezcla de categorías.
- Integración en `MainActivity.kt`: los estados de la biblioteca pasan a delegarse en `sequenceLibraryState`; el estado del editor y la lógica de las partidas quedaron fuera del alcance.
- Commits:
  - `a102ebc61b9ebf0362d02fd5c14d42834de43865` — creación de la clase.
  - `7732c463b5c7fa1a8bf6c16b065121d84251a95b` — primera delegación.
  - `f7f3405ccfc0af244e6e8a882fcc25eb99624da9` — integración completa del estado de biblioteca.
- HEAD de `main` tras la fase: `f7f3405ccfc0af244e6e8a882fcc25eb99624da9`.
- CI: el conector no devolvió estados ni ejecuciones de Actions asociadas a este SHA; no se registra aquí una compilación confirmada por la API.
- Validación del usuario: «Todo funcionando». Se da por validado el arranque y el comportamiento de la biblioteca en el dispositivo.

## Archivos de estado extraídos

- `StoryEditorState.kt`
- `StoryCollectionsState.kt`
- `StoryObjectCollectionsState.kt`
- `StoryObjectEditorState.kt`
- `StoryObjectInventoryState.kt`

También existen repositorios y componentes previos, entre ellos `ContentStorage.kt`, `ExerciseRepository.kt`, `InventoryRepository.kt`, `ObjectRepository.kt`, `StoryRepository.kt`, `UsageStatisticsRepository.kt` y `UsageStatisticsScreen.kt`. Antes de extraer nuevas responsabilidades, revisar y reutilizar estas capas cuando corresponda.

## Auditoría de MainActivity.kt

Auditoría realizada sobre `main` en HEAD `7e76d87d2be92a6b5db9b2341ac0c97d19b5e047`, sin modificar código.

- `MainActivity.kt`: 4.881 líneas.
- Se identificaron aproximadamente 144 llamadas `remember` dentro del archivo y ocho funciones locales en `AprenderApp()`. Son indicadores orientativos, no una métrica exacta de complejidad.
- Persisten grupos de responsabilidades de Palabras, Secuencias, Tarjetas, partidas, estadísticas, recompensas/tiempo, PIN y control parental, navegación, permisos, configuración y transferencia de bibliotecas.
- La extracción debe guiarse por límites funcionales y dependencias reales, no por el mero recuento de líneas.

## Plan orientativo de fases pendientes

Este orden es una propuesta de trabajo que debe revisarse al inspeccionar las dependencias de cada fase. No implica autorización para ejecutar todas las fases automáticamente.

6. Biblioteca y editor de Secuencias: ejercicios, tarjetas, filtros y categorías.
7. Partida de Secuencias: selección, ordenación, respuestas y resultados.
8. Biblioteca y editor de Palabras.
9. Partidas de Tarjetas y Palabras: lógica y estado.
10. Categorías generales y estadísticas de aprendizaje.
11. Control parental: límites, recompensas, penalizaciones y PIN.
12. Transferencia de bibliotecas: importación, exportación y eliminación.
13. Navegación y composición de las pantallas restantes.

No extraer selectores o botones aislados si forman parte de una responsabilidad mayor que puede separarse coherentemente.

## Reglas obligatorias para cada fase

1. Comprobar antes de tocar nada que la rama activa es `main`, y verificar HEAD y archivos actuales. No asumir que el estado remoto sigue siendo el mismo que el de esta nota.
2. No cambiar a `refactor/` ni crear ramas alternativas sin autorización expresa.
3. Inspeccionar el código y las dependencias reales antes de diseñar la extracción.
4. Mantener el alcance de cada fase pequeño y centrado en una responsabilidad.
5. No alterar el comportamiento funcional ni introducir mejoras no solicitadas en la misma fase.
6. Compilar mediante GitHub Actions después de cada fase. No declarar una compilación correcta hasta comprobar el resultado real.
7. Si falla la compilación, corregir el problema antes de iniciar otra extracción.
8. Tras una compilación correcta, indicar al usuario qué debe probar en el dispositivo. Pedir únicamente pruebas relacionadas con el código modificado.
9. No repetir pruebas de funciones ya validadas si no se han visto afectadas.
10. Distinguir siempre entre commit realizado, compilación de GitHub Actions y prueba funcional en dispositivo.
11. Actualizar este documento al cerrar cada fase, incluyendo archivos, commits, ejecución de Actions y resultado de la prueba del usuario.
12. No comenzar una fase nueva sin acuerdo del usuario sobre el siguiente paso.

## Antecedente: VerifyError y estructura narrativa

En una implementación anterior de condiciones de conexión y editor de estructura narrativa se añadieron estado e integración directamente a `AprenderApp()`. Aunque una ejecución de GitHub Actions terminó correctamente, el usuario confirmó que la aplicación se cerraba al arrancar con un `java.lang.VerifyError` relacionado con la verificación de `AprenderApp(Composer, int)`.

La integración del editor se retiró y se recuperó una base que el usuario confirmó que arrancaba. Se conservó el modelo/persistencia de `StoryRepository.kt`. Commits históricos de aquella implementación:

- `026ef2b5b662aecfc9c9de44d50e5fb0c30c5216` — modelo y persistencia de condiciones.
- `b0add1712d13dd87f63a10003406a8fbcac2f460` — editor de estructura.
- `0e23332d5d339fa34dfc089bf9794952c1c2bc71` — integración inicial.
- `115d8617db026318abdc057fb14bdb87e7897c25` — ajuste de conexiones.
- `4bd66a45eec47d94316c90955a77f64d81c1244d` — recuperación de la integración estable.

El archivo `StoryStructureEditor.kt` existe, pero no debe darse por integrado ni funcional sin comprobar sus referencias y probarlo. No reintroducirlo como parte de una extracción ajena.

### Requisitos funcionales pendientes de bifurcaciones narrativas

- Una bifurcación crea dos escenas paralelas como destinos de una misma escena origen; ambas se editan independientemente.
- Las ramas pueden converger posteriormente en una escena común.
- Las condiciones se configuran en las salidas/conexiones del compositor.
- Una bifurcación requiere dos destinos.
- Al menos una salida debe estar condicionada por un objeto; si ambas lo están, deben usar objetos distintos.
- No reutilizar el mismo objeto en dos salidas paralelas del mismo nivel narrativo ni en bifurcaciones paralelas del mismo nivel.
- Una convergencia admite varias entradas sin exigir otro objeto.
- Cada condición dispone de la casilla «Se consume».
- El consumo del objeto ocurre únicamente durante la interpretación del cuento, nunca en el compositor.

Estas reglas deben conservarse para cuando se retome expresamente esa funcionalidad.

## Fase 7: estado y reglas de la partida de Secuencias — VALIDADA POR EL USUARIO

El usuario autorizó continuar con la fase 7 después de validar la biblioteca de Secuencias.

- Nuevo archivo: `SequenceGameState.kt`.
- Commit de creación: `558fcd27f0c52eb2e8a2474eb1d037d438f9c1a8`.
- Commit de integración: `2dfb2f213a9e101e86a6546d9203a7337182616c`.
- HEAD de `main`: `2dfb2f213a9e101e86a6546d9203a7337182616c`.
- Estado extraído:
  - ejercicio activo, orden aleatorio, orden seleccionado y errores;
  - categoría y dificultad elegidas;
  - filtrado de ejercicios elegibles por tipo, dificultad, actividad, categorías desactivadas y máximo de tarjetas;
  - selección equilibrada del ejercicio menos completado en el día;
  - validación de orden principal y soluciones alternativas;
  - mensajes de progreso y finalización;
  - clave diaria de uso para persistir la frecuencia de realización.
- `MainActivity.kt` delega estos estados y reglas a `SequenceGameState`. La navegación, el cálculo/concesión de recompensas, el registro de resultados y la pantalla visual de la partida permanecen en el coordinador en esta primera extracción para mantener intacto el flujo funcional.
- `MainActivity.kt` pasa de 4.876 a 4.833 líneas en esta fase.
- Revisión estática posterior al cambio: referencias al antiguo helper eliminadas y llaves balanceadas en ambos archivos.
- CI: el conector no ha devuelto un estado de compilación ni ejecuciones de Actions para este commit. No declarar compilación correcta hasta disponer del resultado real.
- Validación en dispositivo: pendiente. No marcar la fase como completada hasta que el usuario confirme.

### Pruebas de dispositivo realizadas y validadas para fase 7

1. Iniciar una partida de Secuencias de cada tipo/dificultad que tenga ejercicios disponibles.
2. Confirmar que las tarjetas aparecen desordenadas.
3. Seleccionar una tarjeta incorrecta y comprobar que aumenta el contador de errores sin avanzar.
4. Seleccionar los pasos correctos y comprobar que el progreso avanza, incluyendo ejercicios con solución alternativa.
5. Completar una secuencia y comprobar resultado, tiempo/recompensa y estadísticas.
6. Volver a jugar y comprobar que la selección prioriza ejercicios menos realizados durante el día.
7. Verificar que los filtros de máximo de tarjetas y categorías desactivadas siguen respetándose.

No hace falta repetir las pruebas de la biblioteca de Secuencias, que ya quedó validada en la fase 6.

## Fase 8: biblioteca y editor de Palabras — IMPLEMENTADA, PENDIENTE DE COMPILACIÓN Y PRUEBA

El usuario autorizó la fase 8 y se ha realizado la extracción sobre `main`.

- Nuevo archivo: `WordLibraryState.kt`.
- Commits:
  - `0857d9e57ca0fdd21e7d2cfc25142c91ccac56ad` — creación de la clase de estado.
  - `2b57e75a66554354ff794227d0c2b956f534b3a8` — sincronización de categorías para transferencias.
  - `aad6ee8a4617e0d871e5319fd567cecfe0203398` — delegación del estado de biblioteca/editor.
  - `cd9a4ed2c4ed103198a77e7dc85cbf6d9e8638c9` — delegación completa de persistencia y recargas.
- Estado delegado: ejercicios, categorías, categorías desactivadas, página de biblioteca, índice de edición, campos del formulario, mensajes, filtros, creación/eliminación/reasignación de categorías y persistencia.
- Operaciones delegadas: cargar/recargar/guardar ejercicios, guardar y combinar categorías importadas, activar/desactivar categorías, crear categorías, borrar categorías con su contenido y reasignar ejercicios a otra categoría.
- Se mantienen en `MainActivity.kt` la composición visual de la biblioteca/editor, navegación, lanzadores de galería/cámara y mensajes de transferencia ZIP; la lógica de datos y persistencia se centraliza en `WordLibraryState`.
- También se ha conectado la recarga y sincronización de la biblioteca desde la pantalla de transferencia/importación.
- `MainActivity.kt` tiene 4.828 líneas tras la fase (antes 4.833).
- Revisión estática: sin llamadas directas restantes a `ExerciseUiStorage.loadPairs/savePairs` para la modalidad PALABRAS en `MainActivity.kt`; llaves balanceadas en ambos archivos.
- CI: pendiente de confirmación. No se ha podido recuperar desde el conector un resultado de Actions asociado a este commit.
- Validación en dispositivo: pendiente.

### Pruebas pendientes para fase 8

1. Abrir Configuración > Palabras > Biblioteca y comprobar que aparecen los ejercicios existentes.
2. Crear un ejercicio con palabra, imagen, categoría y dificultad; comprobar que se guarda y aparece en la lista.
3. Editar un ejercicio existente y comprobar que conserva/actualiza correctamente sus datos e imagen.
4. Activar y desactivar un ejercicio; comprobar que el estado persiste al salir y volver.
5. Probar los filtros de categoría, dificultad y estado.
6. Crear una categoría y comprobar que aparece en el editor y en la lista de categorías.
7. Desactivar una categoría y comprobar que sigue persistiendo esa configuración.
8. Probar eliminar una categoría con contenido: borrar contenido y reasignar a otra categoría.
9. Comprobar importación/exportación de Palabras y recarga desde Transferencia de bibliotecas, si utilizas esas funciones.
10. Cerrar y abrir la aplicación y verificar que ejercicios y categorías permanecen guardados.

No hace falta repetir pruebas de las partidas de Secuencias ni de su biblioteca: no se han modificado.

## Próximo paso

Esperar la compilación y la validación del usuario para cerrar la fase 8. No iniciar la fase 9 antes de esa validación.
