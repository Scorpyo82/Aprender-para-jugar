package es.aprenderparajugar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class HelpTopic(val title: String, val description: String, val sections: List<Pair<String, String>>)

private val helpTopics = listOf(
    HelpTopic(
        title = "Ayuda sobre uso de la interfaz",
        description = "Cómo moverte por la aplicación y localizar sus herramientas.",
        sections = listOf(
            "Pantalla principal" to "La pantalla de inicio reúne las actividades disponibles: Tarjetas, Secuencias, Palabras y Cuentos. Desde aquí también puedes consultar el tiempo acumulado, iniciar el tiempo de juego cuando esté disponible y acceder a la ayuda.",
            "Navegación" to "Utiliza los botones de volver que aparecen en cada pantalla para regresar al nivel anterior. La navegación conserva la jerarquía: desde un editor se vuelve a su biblioteca; desde una biblioteca, a la configuración de su actividad.",
            "Zona de familias" to "El botón de configuración abre el acceso familiar protegido por PIN. Desde el control parental se configuran las actividades, sus bibliotecas y opciones, las recompensas y límites, el sonido, la tipografía, las estadísticas y la transferencia de bibliotecas.",
            "Bibliotecas y editores" to "Las bibliotecas permiten consultar, crear, editar, activar, desactivar y organizar el contenido. Los formularios muestran los campos y recursos propios de cada modalidad. Guarda los cambios antes de salir para conservar el trabajo.",
            "Sonido y tiempo" to "El control de sonido de la pantalla principal permite activar o desactivar el audio de la aplicación. El tiempo acumulado se obtiene al completar actividades y está sujeto a los límites y condiciones configurados por la familia.",
            "Importar y exportar" to "La transferencia de bibliotecas permite seleccionar modalidades y categorías para compartir contenido. Revisa el paquete y las opciones seleccionadas antes de confirmar una importación o sustitución."
        )
    ),
    HelpTopic(
        title = "Reglas comunes para la creación de contenido nuevo",
        description = "Criterios generales para mantener las bibliotecas ordenadas y los ejercicios utilizables.",
        sections = listOf(
            "Organización" to "Crea el contenido dentro de la modalidad y categoría que le correspondan. Utiliza títulos claros y categorías coherentes para que después sea fácil localizar, filtrar y mantener los materiales.",
            "Datos completos" to "Rellena los campos obligatorios del editor antes de guardar. Evita títulos vacíos, categorías sin definir o recursos que no correspondan al contenido.",
            "Imágenes y recursos" to "Asocia cada imagen al ejercicio o elemento al que pertenece y comprueba su vista previa. No reutilices referencias de archivos de otro contenido. En cuentos, las ilustraciones y los audios pertenecen a escenas concretas.",
            "Dificultad y activación" to "Asigna una dificultad adecuada al contenido. Los elementos desactivados se conservan en la biblioteca, pero no deben ofrecerse como contenido disponible para las partidas.",
            "Edición segura" to "Puedes crear y completar contenido por etapas. Guarda los cambios al terminar cada sesión de trabajo y vuelve a abrir el elemento para comprobar que sus datos y recursos persisten.",
            "Bibliotecas compartidas" to "Antes de importar, combinar o sustituir contenido, revisa qué modalidades y categorías has seleccionado. Comprueba después que los ejercicios y sus imágenes siguen asociados correctamente."
        )
    ),
    HelpTopic(
        title = "Diseño de cuentos",
        description = "Guía de creación de historias con escenas, objetos, colecciones, decisiones y caminos alternativos.",
        sections = listOf(
            "Colecciones de cuentos" to "Una colección agrupa cuentos relacionados, por ejemplo, una saga o un universo narrativo. Cada cuento pertenece a una colección. Las colecciones sirven para organizar la biblioteca; no determinan por sí mismas el orden ni los caminos de la historia.",
            "Escenas y contenido" to "Un cuento se compone de escenas independientes. Cada escena puede tener título, texto, ilustración, audio ambiental y narración. El audio ambiental puede configurarse para mantenerse desde la escena anterior. Las escenas se pueden crear y editar en cualquier orden: el orden de creación no determina el recorrido.",
            "Escena inicial" to "El autor selecciona expresamente la escena inicial. La reproducción comienza en ella, con independencia del orden en que se hayan creado o editado las escenas.",
            "Estructura y conexiones" to "La estructura es un grafo dirigido: cada conexión indica qué escena puede suceder a otra. Una escena admite como máximo dos salidas. Las conexiones se pueden configurar en cualquier orden, pueden regresar a escenas anteriores y pueden formar circuitos. No se permite conectar una escena consigo misma.",
            "Bifurcaciones" to "Una bifurcación se forma cuando una escena tiene dos destinos posibles. Al menos una de las dos salidas debe estar condicionada por un objeto. Si ambas salidas tienen condición, deben exigir objetos distintos. Cada salida se configura por separado y puede tener su propia condición.",
            "Convergencia de caminos" to "Varias escenas pueden conducir a una misma escena de destino. La convergencia permite que ramas diferentes vuelvan a compartir una parte de la historia y no exige añadir una condición u objeto adicional.",
            "Objetos y colecciones de objetos" to "Los objetos se crean en una biblioteca común y se organizan en colecciones. Un cuento puede referirse a esos objetos en sus condiciones y en las escenas que los entregan. La referencia identifica el objeto de la biblioteca; no crea una copia distinta para cada escena.",
            "Inventario del niño" to "El inventario representa los objetos que el jugador posee durante la experiencia. Una condición de conexión puede exigir que el jugador tenga un objeto. Si no lo tiene, esa salida no está disponible. El editor configura la regla, pero no modifica el inventario del jugador.",
            "Entrega y consumo de objetos" to "Una escena puede entregar un objeto al entrar o al salir, según el momento configurado. Una condición de conexión dispone de la opción «Se consume»: cuando está activada, el objeto requerido se consume al utilizar esa salida durante la reproducción. La configuración del editor no consume objetos.",
            "Escenas finales" to "Una escena es final cuando no tiene ninguna conexión saliente. No hace falta marcarla manualmente. Puede haber varios finales alternativos: por ejemplo, si dos ramas terminan en escenas distintas sin salidas, ambas son desenlaces del cuento.",
            "Escenas alcanzables y aisladas" to "Para ser un final válido, la escena sin salidas también debe poder alcanzarse desde la escena inicial siguiendo las conexiones. Una escena aislada o inaccesible no cuenta como desenlace alcanzable y debe señalarse para que el autor revise la estructura.",
            "Circuitos y repeticiones" to "Se permiten conexiones que regresen a escenas anteriores y circuitos que repitan escenas. Esto permite repetir una parte del cuento hasta que el niño consiga un objeto o tome una decisión. La escena se reutiliza; no es necesario duplicarla para volver a visitarla.",
            "Advertencias sobre ciclos" to "La aplicación debe detectar los circuitos y comprobar si existe una ruta alternativa formada únicamente por conexiones sin condición de objeto que permita llegar a una escena final alcanzable. Si no encuentra esa salida incondicional, debe advertir que el circuito podría provocar un ciclo indefinido y que el autor debe comprobar que las condiciones pueden cumplirse.",
            "Límites del análisis automático" to "El análisis estructural no puede garantizar que el niño consiga un objeto ni que se cumplan todas las condiciones: eso puede depender de otras escenas, decisiones e inventario. Por tanto, las advertencias orientan al autor, pero no sustituyen la revisión de la lógica completa del cuento.",
            "Guardar y activar" to "Los cuentos se pueden guardar y activar aunque tengan advertencias de estructura, escenas inaccesibles o circuitos sin salida incondicional. La creación puede llevar varias sesiones y el autor debe poder conservar el trabajo incompleto. Las advertencias informan; no bloquean el guardado ni la activación.",
            "Importación de cuentos y objetos" to "Al importar cuentos, la aplicación debe revisar todos los objetos que utilizan sus escenas y conexiones. Los objetos necesarios forman parte de las dependencias del cuento: no se debe completar una importación dejando referencias a objetos inexistentes. Si el nombre del objeto no existe en la biblioteca, se incorpora su definición. Si ya existe con el mismo nombre, se comparan sus características y se muestran las dos miniaturas para que puedas reconocerlos.",
            "Resolver conflictos de objetos" to "Cuando un objeto del paquete y otro de la biblioteca tienen el mismo nombre, pero características diferentes, la importación se detiene hasta que el usuario resuelva el conflicto mediante un selector. Puede adaptar el cuento al objeto existente, sobrescribir el objeto existente con el del paquete o cancelar la importación. No se cambian nombres automáticamente. Si se sobrescribe, se advierte de los demás cuentos que utilizan ese objeto y de los efectos sobre su disponibilidad y precio en la tienda, cuando esas propiedades estén implementadas. La imagen y las propiedades comerciales deben compararse; tener el mismo nombre no demuestra que sean equivalentes.",
            "Inventario y tienda al importar" to "Importar un cuento no concede sus objetos al niño ni modifica el inventario. El estado de posesión se conserva por separado de la definición del objeto. La comparación debe mostrar también las diferencias de disponibilidad para compra y precio en minutos cuando exista la tienda; un objeto exclusivo de un cuento no debe convertirse inadvertidamente en un artículo de tienda."
        )
    )
)

@Composable
internal fun HelpScreen(onBack: () -> Unit) {
    var selectedTopic by remember { mutableStateOf<String?>(null) }
    val topic = helpTopics.firstOrNull { it.title == selectedTopic }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedButton(onClick = { if (selectedTopic != null) selectedTopic = null else onBack() }) {
                Text(if (selectedTopic != null) "← Volver a Ayuda" else "← Volver al inicio")
            }
            Text(if (topic == null) "Ayuda" else topic.title, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                topic?.description ?: "Selecciona un apartado para consultar las instrucciones.",
                fontSize = 15.sp
            )
        }
        if (topic == null) {
            helpTopics.forEach { helpTopic ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selectedTopic = helpTopic.title },
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(helpTopic.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(helpTopic.description, fontSize = 14.sp)
                            Text("Consultar apartado  ›", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            topic.sections.forEach { (heading, body) ->
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(heading, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(body, fontSize = 15.sp, lineHeight = 21.sp)
                    }
                }
            }
        }
    }
}
