# Ayuda y diseño de cuentos

**Proyecto:** Aprender para jugar  
**Rama de referencia:** `main`  
**Propósito:** documentar el uso general de la aplicación y las reglas funcionales de creación de contenido y cuentos.

Este documento complementa la ayuda integrada en la aplicación. Distingue entre el funcionamiento general de la interfaz, las pautas comunes para crear contenido y la especificación de diseño narrativo.

## 1. Ayuda sobre uso de la interfaz

- La pantalla principal presenta las actividades disponibles y el tiempo acumulado.
- El botón de configuración abre la zona familiar protegida mediante PIN.
- La zona familiar permite configurar actividades, bibliotecas, recompensas y límites, sonido, tipografía, estadísticas, permisos y transferencia de bibliotecas.
- Las bibliotecas permiten consultar, crear, editar, activar/desactivar y organizar contenido.
- Los botones de volver mantienen la navegación jerárquica entre bibliotecas, editores y configuración.
- El control de sonido permite activar o desactivar el audio de la aplicación.
- La transferencia permite seleccionar modalidades y categorías para importar o exportar. Tras una importación conviene comprobar el contenido y sus imágenes.

## 2. Reglas comunes para la creación de contenido nuevo

- Organizar cada contenido en su modalidad y categoría correspondiente.
- Utilizar títulos claros y completar todos los campos obligatorios.
- Asignar dificultad y estado de activación adecuados.
- Comprobar las imágenes y demás recursos en sus vistas previas y asociarlos al elemento correcto.
- Desactivar un contenido lo conserva en la biblioteca, pero evita que se ofrezca como contenido activo.
- Guardar al terminar cada sesión y comprobar la persistencia reabriendo el elemento.
- Antes de combinar o sustituir bibliotecas, revisar la selección y comprobar después que no se han cruzado imágenes ni referencias.

### Modalidades

- **Tarjetas:** parejas de palabras e imágenes.
- **Palabras:** ejercicios de formación de palabras con letras e imagen de apoyo.
- **Secuencias:** ejercicios ordenados por tipo: Orden temporal, Procesos, Causa y consecuencia y Ciclos. Cada paso requiere su contenido de texto e imagen; pueden existir soluciones alternativas completas.

## 3. Diseño de cuentos

### 3.1. Organización por colecciones

Una colección agrupa cuentos relacionados (por ejemplo, una saga o universo). Cada cuento pertenece a una colección. La colección organiza la biblioteca, pero no define el recorrido narrativo.

Los objetos también se organizan en colecciones dentro de una biblioteca común. Los objetos se reutilizan mediante referencias; no se duplican por el mero hecho de aparecer en distintos cuentos o escenas.

### 3.2. Cuento y escenas

Un cuento contiene:
- Título y colección.
- Portada opcional.
- Escena inicial seleccionada explícitamente.
- Lista de escenas.
- Conexiones entre escenas.
- Condiciones asociadas a conexiones.

Cada escena mantiene contenido independiente:
- Nombre/título.
- Texto.
- Resumen de ilustración e ilustración.
- Audio ambiental y narración.
- Opción de mantener el audio ambiental anterior.
- Objeto que puede entregar la escena.
- Momento de entrega: al entrar o al salir.

La creación y edición de escenas puede hacerse en cualquier orden. El orden de creación no define el orden de reproducción. Añadir escenas no debe destruir conexiones o bifurcaciones ya configuradas.

### 3.3. Grafo narrativo, conexiones y bifurcaciones

La estructura es un grafo dirigido. Cada conexión representa una salida desde una escena hacia otra.

Reglas:
1. El autor elige la escena inicial.
2. Cada escena admite un máximo de dos salidas.
3. Una escena no puede conectarse consigo misma.
4. Las conexiones pueden definirse en cualquier orden y apuntar a escenas creadas antes o después.
5. Una bifurcación tiene dos destinos y al menos una salida debe exigir un objeto.
6. Si ambas salidas están condicionadas, deben exigir objetos distintos.
7. Cada condición se asocia a una conexión concreta.
8. Varias escenas pueden converger en un mismo destino. La convergencia no necesita una condición adicional.
9. Las conexiones de retorno están permitidas y pueden crear circuitos.

Una escena se reutiliza al regresar a ella; no es necesario duplicarla para repetir su contenido.

### 3.4. Objetos, condiciones e inventario

Los objetos se definen en la biblioteca común y se agrupan en colecciones. Las escenas y conexiones los referencian por su identidad de biblioteca.

Una escena puede entregar un objeto al entrar o al salir. Una conexión puede exigir que el jugador posea un objeto para poder tomar esa salida.

Cada condición incluye la opción **«Se consume»**:
- Desactivada: el objeto se exige, pero permanece en el inventario.
- Activada: el objeto requerido se consume cuando se utiliza esa conexión durante la reproducción.

El editor solo configura las reglas. No consulta ni modifica el inventario real del jugador al diseñar el cuento. El consumo y la entrega pertenecen a la ejecución de la historia.

### 3.5. Importación de cuentos y dependencias de objetos

Un cuento no se debe importar de forma que queden referencias a objetos inexistentes. Al exportar un cuento se deben incluir las definiciones y los recursos de todos los objetos que utiliza, tanto en condiciones de conexiones como en entregas de escenas. Estas definiciones son dependencias del cuento, no una concesión automática al inventario.

Al inspeccionar un paquete, el importador debe comparar cada objeto incluido con la biblioteca local. La identidad actual del objeto es el nombre de su carpeta; no existe un identificador independiente ni un campo de descripción. La comparación debe considerar las características realmente existentes en la versión de la aplicación (actualmente nombre e imagen) y, cuando se incorpore la tienda, también «Se puede comprar» y el precio en minutos.

Reglas de resolución:

1. Si no existe un objeto con ese nombre, se incorpora su definición y sus recursos.
2. Si existe con el mismo nombre y las mismas características, se reutiliza el objeto local.
3. Si existe con el mismo nombre, pero alguna característica difiere, se presenta un conflicto y la importación no puede continuar hasta resolverlo.
4. No se renombra ningún objeto automáticamente ni se generan nombres alternativos por iniciativa del importador.
5. Si el usuario considera que son objetos diferentes, debe adaptar previamente el contenido o la biblioteca para que sus nombres sean distintos y volver a intentar la importación.

Cada conflicto debe mostrar dos miniaturas reales, lado a lado: la imagen del objeto incluido en el paquete y la imagen del objeto existente en la biblioteca. Junto a ellas se muestran el nombre y todas las diferencias detectadas. Las imágenes deben proceder de los archivos incluidos y locales, respectivamente; no se deben sustituir por ilustraciones genéricas.

El selector obligatorio de resolución ofrece:

- **Adaptar el cuento al objeto existente:** conservar la definición local y hacer que todas las referencias del cuento importado apunten a ese objeto.
- **Sobrescribir el objeto existente:** sustituir su definición por la incluida en el paquete. Antes de permitirlo, advertir de los demás cuentos que utilizan ese nombre y de los efectos que el cambio tendrá sobre ellos. Cuando exista la tienda, advertir expresamente si cambia «Se puede comprar» o el precio.
- **Cancelar la importación:** no incorporar el cuento ni sus dependencias.

Las decisiones se aplican a todas las referencias del cuento importado: condiciones de conexiones y objetos entregados por escenas. No se puede dejar ninguna referencia sin resolver.

El inventario actual del niño no se incluye en los paquetes: ni se exporta ni se importa. La transferencia de cuentos incluye las definiciones y los recursos de los objetos que esos cuentos necesitan, pero no el estado de posesión del jugador. El inventario es un estado de juego independiente, que permanece en el dispositivo de destino. Importar o sobrescribir una definición no debe añadir ni retirar automáticamente ese objeto del inventario. Tampoco se importan compras ni se concede el objeto al niño por importar un cuento.

La resolución debe validarse antes de modificar la biblioteca. La operación debe ser transaccional: si se cancela o falla, no debe quedar una importación parcial ni una combinación de definiciones y referencias incoherente.

### 3.6. Importación de objetos de forma independiente

La importación de objetos como contenido independiente puede existir como operación separada. No sustituye la resolución de dependencias obligatoria cuando se importan cuentos. En ambos casos se aplican la detección de nombres coincidentes, la comparación visual y la resolución explícita de diferencias.

### 3.5. Escenas finales

Una escena es candidata a final cuando no tiene conexiones salientes. No existe un selector manual de final.

Para considerarla un desenlace válido, además debe ser alcanzable desde la escena inicial siguiendo las conexiones. Puede haber varios finales alternativos.

Ejemplo:

```text
Escena 1 → Escena 2
Escena 2 → Escena 3 y Escena 4
Escena 3 → Escena 5
Escena 4 → Escena 6

Finales: Escena 5 y Escena 6
```

Las escenas sin salidas que no sean alcanzables desde el inicio no cuentan como finales válidos. Se deben señalar como escenas inaccesibles. Una escena inaccesible puede estar aislada o formar parte de una subestructura desconectada.

### 3.6. Circuitos y riesgo de ciclos indefinidos

Se permiten ciclos narrativos. Por ejemplo, el niño puede repetir un laberinto hasta conseguir una llave y después utilizar una salida condicionada para continuar.

La aplicación debe analizar la estructura sin intentar simular todas las decisiones, estados del inventario y combinaciones de condiciones.

Análisis estático acordado:
1. Calcular las escenas alcanzables desde la escena inicial ignorando condiciones, para detectar escenas inaccesibles.
2. Identificar como finales las escenas alcanzables sin conexiones salientes.
3. Detectar componentes cíclicos del grafo entre las escenas alcanzables.
4. Para cada circuito, comprobar si desde cada escena del circuito existe una ruta hacia algún final alcanzable usando exclusivamente conexiones sin condición de objeto.
5. Si no existe esa salida incondicional, advertir que el circuito puede producir un ciclo indefinido y que el autor debe comprobar que las condiciones de salida se pueden cumplir.
6. Si no existe ningún final alcanzable, advertir de posible bloqueo estructural.

El análisis no puede demostrar que un objeto vaya a conseguirse ni que una condición compleja vaya a cumplirse. Es responsabilidad del autor revisar que las escenas, decisiones, entregas de objetos y requisitos formen una lógica jugable.

### 3.7. Advertencias y guardado

Las advertencias son informativas, no bloqueantes. Un cuento puede guardarse y activarse aunque:
- esté incompleto porque se está creando por etapas;
- tenga escenas inaccesibles;
- contenga circuitos sin salida incondicional;
- dependa de condiciones cuya satisfacibilidad debe revisar el autor.

No se debe exigir que el autor termine el cuento en una única sesión. Guardar un borrador debe conservar escenas, conexiones, condiciones, objetos referenciados y recursos.

## 4. Criterios de aceptación para la estructura narrativa

- Se puede elegir cualquier escena existente como inicio.
- Se pueden crear escenas y conexiones en orden arbitrario.
- Añadir escenas conserva las conexiones existentes.
- Una escena tiene como máximo dos salidas.
- La validación de bifurcaciones exige al menos una condición por objeto y evita repetir el mismo objeto en las dos salidas paralelas.
- Se pueden conectar varias escenas al mismo destino.
- Se pueden crear conexiones de retorno y circuitos.
- Las escenas sin salidas alcanzables desde el inicio se identifican como finales alternativos.
- Las escenas sin salidas inaccesibles no se presentan como finales válidos.
- Se muestran advertencias para escenas inaccesibles, ausencia de finales alcanzables y circuitos sin salida incondicional.
- Ninguna de esas advertencias impide guardar o activar el cuento.
- Las condiciones y el inventario se evalúan durante la reproducción, no en el editor.

## 5. Alcance y cautelas

Esta guía recoge el diseño funcional acordado. La presencia de una regla en esta documentación o en la ayuda no implica por sí sola que toda la ejecución del reproductor de cuentos esté implementada. La validación estructural es un diagnóstico estático y no sustituye las pruebas de reproducción, persistencia, inventario y consumo de objetos.
