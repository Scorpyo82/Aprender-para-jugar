package es.aprenderparajugar

/** Diagnóstico estático de la estructura narrativa; nunca bloquea el guardado. */
internal data class StoryStructureDiagnostics(
    val reachableScenes: Set<String>,
    val unreachableScenes: Set<String>,
    val finalScenes: Set<String>,
    val cycleComponents: List<Set<String>>,
    val cyclesWithoutUnconditionalExit: List<Set<String>>,
    val hasReachableFinal: Boolean,
    val orphanedConnections: List<Pair<String, String>>
)

internal object StoryStructureAnalysis {

    fun analyze(
        scenes: List<String>,
        connections: Map<String, List<String>>,
        conditions: List<StoryConnectionCondition>,
        startScene: String,
        alternatives: List<StoryConnectionAlternative> = emptyList()
    ): StoryStructureDiagnostics {
        val sceneSet = scenes.toSet()
        val configuredOrigins = alternatives.map { it.fromScene }.toSet()

        // El motor decide por origen: las alternativas nuevas prevalecen solo
        // en las escenas que ya las utilizan; las demás conservan el formato heredado.
        fun destinationsFrom(origin: String): List<String> =
            if (origin in configuredOrigins) {
                alternatives.filter { it.fromScene == origin }.map { it.toScene }.distinct()
            } else {
                connections[origin].orEmpty().filter { it != STORY_LOST_DESTINATION }.distinct()
            }

        val adjacency = scenes.associateWith { origin ->
            destinationsFrom(origin).filter { it in sceneSet }
        }
        val reachable = reachableFrom(startScene, adjacency)
        val unreachable = sceneSet - reachable
        val finals = reachable.filterTo(linkedSetOf()) { scene ->
            adjacency[scene].orEmpty().isEmpty() &&
                STORY_LOST_DESTINATION !in connections[scene].orEmpty() &&
                alternatives.none { it.fromScene == scene && it.toScene == STORY_LOST_DESTINATION }
        }

        val unconditionalAdjacency = scenes.associateWith { origin ->
            adjacency[origin].orEmpty().filter { destination ->
                if (origin in configuredOrigins) {
                    alternatives.any {
                        it.fromScene == origin && it.toScene == destination && it.kind == "free"
                    }
                } else {
                    conditions.none {
                        it.fromScene == origin && it.toScene == destination && it.requiredObject != null
                    }
                }
            }
        }
        val components = stronglyConnectedComponents(reachable, adjacency)
            .filter { component ->
                component.size > 1 || component.any { node -> node in adjacency[node].orEmpty() }
            }
        val unsafeCycles = components.filter { component ->
            component.any { scene -> !canReachAny(scene, finals, unconditionalAdjacency) }
        }

        val orphaned = buildList {
            alternatives.filter { it.fromScene == STORY_LOST_ORIGIN || it.toScene == STORY_LOST_DESTINATION }
                .forEach { add(it.fromScene to it.toScene) }
            connections.forEach { (origin, destinations) ->
                destinations.filter { it == STORY_LOST_DESTINATION }
                    .forEach { add(origin to it) }
            }
        }.distinct()

        return StoryStructureDiagnostics(
            reachableScenes = reachable,
            unreachableScenes = unreachable,
            finalScenes = finals,
            cycleComponents = components,
            cyclesWithoutUnconditionalExit = unsafeCycles,
            hasReachableFinal = finals.isNotEmpty(),
            orphanedConnections = orphaned
        )
    }

    private fun reachableFrom(start: String, adjacency: Map<String, List<String>>): Set<String> {
        if (start !in adjacency) return emptySet()
        val visited = linkedSetOf<String>()
        val pending = ArrayDeque<String>()
        pending.add(start)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (!visited.add(current)) continue
            adjacency[current].orEmpty().forEach { if (it in adjacency && it !in visited) pending.add(it) }
        }
        return visited
    }

    private fun canReachAny(
        start: String,
        targets: Set<String>,
        adjacency: Map<String, List<String>>
    ): Boolean {
        if (start in targets) return true
        val visited = mutableSetOf<String>()
        val pending = ArrayDeque<String>()
        pending.add(start)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (!visited.add(current)) continue
            adjacency[current].orEmpty().forEach { next ->
                if (next in targets) return true
                if (next !in visited) pending.add(next)
            }
        }
        return false
    }

    private fun stronglyConnectedComponents(
        nodes: Set<String>,
        adjacency: Map<String, List<String>>
    ): List<Set<String>> {
        var nextIndex = 0
        val indices = mutableMapOf<String, Int>()
        val lowLinks = mutableMapOf<String, Int>()
        val stack = ArrayDeque<String>()
        val onStack = mutableSetOf<String>()
        val result = mutableListOf<Set<String>>()

        fun visit(node: String) {
            indices[node] = nextIndex
            lowLinks[node] = nextIndex
            nextIndex++
            stack.addLast(node)
            onStack.add(node)

            adjacency[node].orEmpty().filter { it in nodes }.forEach { next ->
                if (next !in indices) {
                    visit(next)
                    lowLinks[node] = minOf(lowLinks.getValue(node), lowLinks.getValue(next))
                } else if (next in onStack) {
                    lowLinks[node] = minOf(lowLinks.getValue(node), indices.getValue(next))
                }
            }

            if (lowLinks[node] == indices[node]) {
                val component = linkedSetOf<String>()
                do {
                    val member = stack.removeLast()
                    onStack.remove(member)
                    component.add(member)
                } while (member != node)
                result.add(component)
            }
        }

        nodes.forEach { if (it !in indices) visit(it) }
        return result
    }
}
