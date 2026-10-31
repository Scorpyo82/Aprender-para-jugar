# Plan de reestructuración de la arquitectura de contenidos

**Proyecto:** Aprender para jugar  
**Rama de trabajo:** `refactor/arquitectura-almacenamiento-contenidos`  
**Referencia de partida:** `d51008a48229630f6ec33e08e5469fefde47d6c8`  
**Versión de la aplicación en esa referencia:** `0.3.34` (`versionCode 38`)  
**Estado:** diseño cerrado. Paso 1 iniciado; paso 2 en implementación (modelos JSON y repositorio centralizados incorporados en la rama de trabajo).

---

## 1. Objetivo

Sustituir el almacenamiento actual de los ejercicios por una estructura de carpetas y archivos independiente por ejercicio. El contenido educativo debe ser portable, inspeccionable y fácil de mantener.

La reestructuración afecta a las tres modalidades:

- Tarjetas.
- Secuencias.
- Palabras.

No se pretende rediseñar los juegos ni sus pantallas. Se pretende separar el almacenamiento de la lógica de presentación y juego mediante un gestor de contenidos.

## 2. Estructura de almacenamiento acordada

La aplicación utilizará el almacenamiento privado de Android (`context.filesDir`), que ya corresponde al directorio físico `files/`. Dentro de él se crearán tres carpetas principales:

**Modalidad → Categoría → Ejercicio → Datos e imágenes**

```text
files/
├── tarjetas/
│   ├── Animales/
│   │   └── Rana/
│   │       ├── ejercicio.json
│   │       ├── statistics.json
│   │       ├── imagen_1.png
│   │       └── imagen_2.png
│   └── Objetos/
│
├── palabras/
│   ├── Animales/
│   └── Objetos/
│
└── secuencias/
    ├── Vida cotidiana/
    │   ├── Preparar_un_bocadillo/
    │   │   ├── ejercicio.json
    │   │   └── imagen_1.png
    │   └── Regar_una_planta/
    │       ├── ejercicio.json
    │       └── imagen_1.png
    └── Naturaleza/
        └── Ciclo_de_la_mariposa/
            ├── ejercicio.json
            └── imagen_1.png
```

Las categorías de Secuencias son comunes a sus cuatro tipos, tal como funciona actualmente la aplicación. Por tanto, las tres modalidades tendrán la misma jerarquía física: modalidad, categoría y ejercicio.

**El tipo de Secuencias no tendrá carpetas propias.** Se guardará como metadato en el JSON de cada ejercicio (por ejemplo, `orden_temporal`, `procesos`, `causa_efecto` o `ciclos`). Esto permite filtrar y agrupar los cuatro tipos desde la biblioteca común sin fragmentar el almacenamiento.

Los nombres de carpeta se derivan del título/nombre del ejercicio y se normalizan para crear rutas válidas. El título original se conserva en el JSON; normalizar la carpeta no debe modificar el texto que ve el usuario.

### Reglas de nombres y rutas

- Las carpetas de categorías se crean dinámicamente a partir de las categorías utilizadas en cada modalidad.
- Los nombres de carpeta se normalizan para formar rutas seguras, conservando los nombres originales en los metadatos.
- Los nombres de imagen pueden repetirse en ejercicios distintos (`imagen_1.png`, etc.) porque la ruta completa es diferente.
- El acceso a imágenes debe resolverse dentro de la carpeta del ejercicio; no se deben buscar imágenes globalmente ni asociarlas por el orden físico de los archivos ZIP.
- Debe definirse una política para títulos duplicados dentro de la misma modalidad y categoría. La colisión se detectará antes de escribir; nunca se sobrescribirá otro ejercicio. Se podrá añadir un sufijo de carpeta cuando sea necesario, conservando el título original.
- Las referencias guardadas en los JSON serán relativas a la carpeta del ejercicio, no URI absolutas del dispositivo.

## 3. Datos del ejercicio: un JSON por carpeta

Cada ejercicio tendrá un archivo `ejercicio.json` con metadatos comunes y un bloque `content` específico de su modalidad. Las imágenes se guardarán junto al JSON y se referenciarán mediante rutas relativas a la carpeta del ejercicio.

### Metadatos comunes

- `schemaVersion`: versión del esquema JSON.
- `modality`: `tarjetas`, `palabras` o `secuencias`.
- `title`, `category`, `difficulty` y `active`.
- `createdAt` y `updatedAt`.

### Contenido por modalidad

- **Tarjetas:** `content.word` y `content.image`.
- **Palabras:** `content.word` y `content.hintImage`.
- **Secuencias:** `content.type`, `content.steps` y `content.alternativeSolutions`.

En Secuencias, cada elemento de `steps` agrupa `id`, `text` e `image`. Las soluciones alternativas son listas ordenadas de identificadores de pasos; cada solución debe incluir todos los pasos exactamente una vez. Los tipos se serializan como `orden_temporal`, `procesos`, `causa_efecto` y `ciclos`.

Ejemplo conceptual del formato implementado para Secuencias:

```json
{
  "schemaVersion": 1,
  "modality": "secuencias",
  "title": "Preparar una tostada",
  "category": "Vida cotidiana",
  "difficulty": "Normal",
  "active": true,
  "createdAt": "2026-10-31T10:00:00Z",
  "updatedAt": "2026-10-31T10:00:00Z",
  "content": {
    "type": "orden_temporal",
    "steps": [
      { "id": "paso-1", "text": "Coger una rebanada de pan", "image": "imagen_1.png" },
      { "id": "paso-2", "text": "Introducir el pan en la tostadora", "image": "imagen_2.png" }
    ],
    "alternativeSolutions": [
      ["paso-1", "paso-2"]
    ]
  }
}
```

El modelo valida los metadatos, la modalidad, las referencias relativas a recursos y la integridad de las soluciones alternativas. Las secuencias admiten actualmente entre 2 y 6 pasos y mantienen el formato de imagen y texto: ambos campos son obligatorios en cada paso.

Los identificadores de paso son referencias internas del ejercicio; no se asignan UUID a los ejercicios. El nombre de carpeta deriva del título normalizado y las colisiones se rechazan antes de sobrescribir otro ejercicio.

## 4. Gestor de contenidos: separación entre almacenamiento y aplicación

Se creará una capa centralizada (gestor/repositorio de contenidos) responsable de:

- Leer y validar ejercicios.
- Crear ejercicios y sus carpetas.
- Editar datos e imágenes.
- Eliminar ejercicios y sus recursos.
- Consultar bibliotecas.
- Obtener categorías y ejercicios.
- Filtrar por modalidad, categoría, tipo, dificultad y estado.
- Importar, sustituir y combinar paquetes.
- Exportar ejercicios y bibliotecas.

Las pantallas, editores y juegos solicitarán datos al gestor y trabajarán con modelos de datos en memoria. No deberán construir rutas ni leer/escribir JSON directamente.

### Consecuencias para las funciones actuales

- **Búsqueda/selección para los juegos:** se consulta el catálogo en memoria y se seleccionan ejercicios según las reglas actuales. Las imágenes se resuelven desde la carpeta propia de cada ejercicio.
- **Creación:** el editor entrega el ejercicio al gestor; este crea la carpeta, guarda imágenes y JSON, valida el resultado y actualiza el catálogo.
- **Edición:** se actualiza el ejercicio correspondiente sin afectar a otros con nombres de imagen iguales.
- **Filtros:** se aplican sobre los modelos cargados; no es necesario recorrer archivos cada vez que se filtra.
- **Bibliotecas y categorías:** se construyen desde el catálogo de ejercicios y las carpetas de categorías. La configuración adicional de categorías (por ejemplo, habilitada/deshabilitada) puede mantenerse separada como configuración de aplicación.
- **Juegos y pantallas:** se conservará su funcionamiento siempre que sea posible; se adaptarán sus fuentes de datos, no se reescribirán sin necesidad.

## 5. Datos de contenido frente a datos de la aplicación

Se mantendrá separada la información educativa de los datos de uso y configuración.

**Dentro de la carpeta de cada ejercicio:**
- `ejercicio.json`: título, categoría, modalidad, tipo, formato, dificultad, estado y contenido educativo, con referencias relativas a sus imágenes.
- `statistics.json`: estadísticas locales de aprendizaje de ese ejercicio.
- Imágenes y demás recursos propios del ejercicio.

**Fuera de las carpetas de ejercicios, en almacenamiento de aplicación:**
- Progreso e historial global de uso que no pertenezcan a un ejercicio concreto.
- Temporizadores, saldo y recompensas.
- Configuración parental y preferencias generales.
- Estado global de categorías y demás ajustes de biblioteca.

Exportar un ejercicio no debe exportar estadísticas personales ni configuración parental.

## 6. Importación y exportación

El ZIP debe conservar la misma jerarquía de carpetas que el almacenamiento interno e incluir `ejercicio.json` y las imágenes de cada ejercicio. **No incluirá `statistics.json`**, porque contiene datos locales de aprendizaje.

Reglas de importación:

1. Inspeccionar y validar el paquete antes de modificar la biblioteca.
2. Leer cada JSON y resolver cada imagen por su ruta relativa exacta dentro de la carpeta del ejercicio.
3. No depender del orden de las entradas del ZIP.
4. Detectar duplicados y aplicar las reglas de sustitución o combinación.
5. Copiar los recursos a las carpetas definitivas sin reutilizar rutas de otros ejercicios.
6. Actualizar el catálogo solo cuando el ejercicio se haya guardado correctamente.
7. Evitar dejar referencias rotas o ejercicios parcialmente importados si ocurre un error.

En una combinación, los ejercicios no afectados deben conservar sus recursos y sus estadísticas locales. Si un ejercicio existente se actualiza, se actualizan sus datos educativos e imágenes como una unidad coherente, pero se conserva su `statistics.json`. Un ejercicio nuevo comienza con estadísticas vacías.

En una sustitución, la biblioteca anterior no se eliminará hasta que la nueva biblioteca se haya validado y guardado correctamente. La limpieza de archivos antiguos se hará después de comprobar que no siguen referenciados.

El formato de ZIP tendrá una versión propia o un mecanismo de compatibilidad explícito. No se asumirá que cualquier ZIP antiguo es compatible con el nuevo formato.

## 7. Migración de datos existentes

La aplicación ya almacena contenido en `SharedPreferences`. La migración debe preservar los ejercicios existentes y sus imágenes.

Situación observada en el código de referencia:

- Secuencias: `sequence_exercises_temporal_v1`, guardado como JSON dentro de `SharedPreferences`. El modelo actual incluye `title`, `cards`, `format`, `category`, `alternatives`, `pictures`, `type`, `difficulty` y `active`.
- Tarjetas: `pairs_v1`, texto separado por tabuladores y saltos de línea.
- Palabras: `letter_word_exercises_v1`, también texto separado por tabuladores y saltos de línea.
- Categorías y parte de su configuración también se guardan actualmente en preferencias.

La migración deberá:
- Leer el formato antiguo.
- Convertir cada ejercicio al nuevo modelo.
- Copiar o materializar sus imágenes en su carpeta propia.
- Validar que todos los archivos y referencias existen.
- Guardar los JSON.
- Verificar el catálogo resultante.
- Conservar los datos antiguos hasta que la migración se haya completado y probado satisfactoriamente.

Debe ser idempotente: repetir el arranque o el proceso de comprobación no debe duplicar ejercicios ni corromper datos. Se diseñará una marca de versión/estado de migración y una estrategia de recuperación ante interrupciones.

No retirar el almacenamiento antiguo hasta completar pruebas y confirmar que la migración es correcta.

## 8. Incidencias conocidas de la Alpha de partida

La versión de referencia compila, pero tiene fallos conocidos pendientes. No debe tratarse como versión estable.

### BUG-001 · El control de tiempo no se activa correctamente al salir mediante Home

Desde algunas pantallas secundarias (identificadas: ejercicio en curso y pantalla de premios), al pulsar Home el contador no se activa correctamente y se puede salir con 0 minutos, eludiendo el control de tiempo.

- Área: ciclo de vida, temporizador y control parental.
- Es una incidencia funcional independiente de la reestructuración de contenidos.
- No se considerará resuelta por la migración de almacenamiento.
- Debe probarse y corregirse de forma explícita.

### BUG-002 · Mezcla de ejercicios e imágenes en bibliotecas

Al cargar o combinar bibliotecas aparecen imágenes de otros ejercicios, imágenes repetidas y asociaciones incorrectas entre texto e imagen.

Causa técnica identificada en la importación actual de Secuencias: los archivos se guardan en rutas reutilizadas basadas en índices, con nombres del tipo `imported_sequence_<index>_<pictureIndex>.<extension>`. Otra importación puede sobrescribir archivos a los que todavía apuntan ejercicios anteriores.

- Área: almacenamiento, importación, combinación y bibliotecas.
- La nueva arquitectura debe eliminar esta colisión de rutas.
- Deben probarse tanto ejercicios coincidentes como no coincidentes al combinar paquetes.

Issue de seguimiento: [#15 - Incidencias conocidas de la Alpha 0.3.34 y criterios de regresión](https://github.com/Scorpyo82/Aprender-para-jugar/issues/15).

## 9. Plan de trabajo acordado

La implementación se realizará en este orden, con commits pequeños y comprobaciones antes de avanzar:

1. **Adaptar la estructura:** establecer la jerarquía modalidad/categoría/ejercicio y los archivos propios de cada ejercicio.
2. **Adaptar el código:** conectar modelos, catálogo, gestores, editores, juegos y operaciones de almacenamiento con la nueva arquitectura.
3. **Comprobar creación y edición:** verificar que se pueden crear y editar ejercicios en las tres modalidades, incluidos sus recursos y metadatos.
4. **Exportar y comprobar la estructura exportada:** revisar los ZIP, sus rutas, imágenes, JSON y exclusión de estadísticas; comprobar también la importación.
5. **Adaptar los ejercicios actuales:** migrar todos los ejercicios existentes, conservando contenido, asociaciones de imágenes y datos locales que correspondan.

No se avanzará al siguiente paso sin comprobar el anterior. La compilación por sí sola no constituye una validación funcional.

## 10. Pruebas de aceptación imprescindibles

- Crear, cerrar y volver a abrir la aplicación: los ejercicios siguen presentes.
- Crear, editar y eliminar ejercicios en las tres modalidades.
- Repetir nombres de imágenes en ejercicios diferentes sin cruces.
- Filtrar y mostrar bibliotecas por categoría, tipo, dificultad y estado.
- Mover un ejercicio de categoría sin perder imágenes ni contenido.
- Gestionar títulos duplicados sin sobrescrituras.
- Exportar e importar un ejercicio y una biblioteca completa.
- Importar un paquete, después combinar otro y comprobar imágenes de todos los ejercicios.
- Sustituir una biblioteca y comprobar que no quedan referencias rotas.
- Interrumpir una migración y comprobar que puede recuperarse sin pérdida ni duplicados.
- Verificar BUG-001 desde las pantallas secundarias y BUG-002 con paquetes de prueba.

## 11. Estado y límites actuales

- Las ramas de respaldo y de trabajo se han creado desde el mismo commit de referencia.
- La rama de trabajo es `refactor/arquitectura-almacenamiento-contenidos`.
- `ContentStorage.kt` crea al iniciar la aplicación las tres carpetas raíz (`tarjetas/`, `palabras/` y `secuencias/`) bajo `context.filesDir`; las categorías se crean dinámicamente.
- La rama de respaldo es `backup/v0.3.34-alpha.1`.
- El Issue #15 documenta los dos fallos conocidos.
- Este documento registra las decisiones acordadas y el estado real de la implementación.
- El diseño está cerrado; continuar directamente con el paso 1 y respetar el orden de trabajo acordado.
- Cualquier cambio posterior de alcance o decisión debe actualizar este documento.

---

## Instrucción para retomar el trabajo en otro chat

Al continuar este proyecto, leer primero este documento en la rama `refactor/arquitectura-almacenamiento-contenidos` y revisar el estado real del repositorio. No asumir que los hitos están implementados por el hecho de estar descritos aquí. Confirmar los commits y cambios existentes antes de continuar. Mantener `main` y la rama de respaldo sin cambios durante el desarrollo de la reestructuración.
