package com.andreas_kratzer.ghosttalk.core.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page

data class NavEdge(
    val sourcePageId: String,
    val sourceButtonIndex: Int,
    val targetPageId: String
)

data class TreeNode(
    val pageId: String,
    val depth: Int,
    val children: List<TreeNode>,
    val isReference: Boolean // true = bereits anderswo im Baum gezeigt (Zyklus/Mehrfach-Elternteil)
)

class BookNavigationGraph private constructor(
    val outgoing: Map<String, List<NavEdge>>,   // pageId -> ausgehende Kanten
    val incoming: Map<String, List<String>>,    // pageId -> Quell-pageIds
    val allPageIds: Set<String>,
    val startPageId: String?,
    private val fallbackStartPageId: String?
) {
    /** Auflösung der Wurzel: bevorzugt startPageId, fällt auf fallbackStartPageId zurück;
     *  ignoriert stale IDs, die nicht (mehr) im Buch existieren. */
    private fun resolveRootId(): String? =
        startPageId?.takeIf { it in allPageIds } ?: fallbackStartPageId?.takeIf { it in allPageIds }

    /** Alle von der Wurzel aus erreichbaren Seiten (folgt nur echten Navigations-Kanten). */
    fun reachablePageIds(): Set<String> {
        val root = resolveRootId() ?: return emptySet()
        val visited = mutableSetOf<String>()
        val stack = ArrayDeque<String>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val id = stack.removeLast()
            if (!visited.add(id)) continue
            outgoing[id]?.forEach { stack.addLast(it.targetPageId) }
        }
        return visited
    }

    /** „Nicht verbunden" = von der Startseite aus NICHT erreichbar (inkl. abgekoppelter Cluster). */
    fun orphans(): List<String> {
        val reachable = reachablePageIds()
        return allPageIds.filter { it !in reachable }
    }

    /** „Sackgassen" = Seiten ohne ausgehende Navigationskanten. */
    fun deadEnds(): List<String> {
        return allPageIds.filter { pageId ->
            outgoing[pageId].isNullOrEmpty()
        }
    }

    /** Schwach zusammenhängende Komponenten des Seiten-Graphen. */
    fun connectedComponents(): List<Set<String>> {
        val adj = mutableMapOf<String, MutableSet<String>>()
        for (pageId in allPageIds) {
            adj[pageId] = mutableSetOf()
        }
        for ((source, edges) in outgoing) {
            for (edge in edges) {
                val target = edge.targetPageId
                adj[source]?.add(target)
                adj[target]?.add(source)
            }
        }

        val visited = mutableSetOf<String>()
        val components = mutableListOf<Set<String>>()

        for (pageId in allPageIds) {
            if (pageId !in visited) {
                val component = mutableSetOf<String>()
                val queue = ArrayDeque<String>()
                queue.addLast(pageId)
                visited.add(pageId)

                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    component.add(current)

                    val neighbors = adj[current] ?: emptySet()
                    for (neighbor in neighbors) {
                        if (neighbor !in visited) {
                            visited.add(neighbor)
                            queue.addLast(neighbor)
                        }
                    }
                }
                components.add(component)
            }
        }
        return components
    }

    fun buildTree(): TreeNode? {
        val rootId = resolveRootId() ?: return null

        val globalVisited = mutableSetOf<String>()

        fun dfs(pageId: String, depth: Int, currentPath: Set<String>): TreeNode {
            if (pageId in currentPath || pageId in globalVisited) {
                return TreeNode(
                    pageId = pageId,
                    depth = depth,
                    children = emptyList(),
                    isReference = true
                )
            }

            globalVisited.add(pageId)
            val nextPath = currentPath + pageId

            val edges = outgoing[pageId] ?: emptyList()
            val children = edges.map { edge ->
                dfs(edge.targetPageId, depth + 1, nextPath)
            }

            return TreeNode(
                pageId = pageId,
                depth = depth,
                children = children,
                isReference = false
            )
        }

        return dfs(rootId, depth = 0, currentPath = emptySet())
    }

    companion object {
        fun from(pages: List<Page>, startPageId: String?): BookNavigationGraph {
            val allPageIds = pages.map { it.id }.toSet()
            val fallbackStartPageId = pages.minByOrNull { it.orderIndex }?.id

            val outgoing = mutableMapOf<String, MutableList<NavEdge>>()
            val incoming = mutableMapOf<String, MutableSet<String>>()

            pages.forEach { page ->
                page.buttonConfigs.forEachIndexed { index, config ->
                    if (config != null && config.isActive && config.label.isNotBlank()) {
                        val action = config.buttonAction
                        if (action is NavigateToPageButtonAction) {
                            val targetId = action.pageId.ifEmpty { startPageId ?: fallbackStartPageId }
                            // targetPageId muss in allPageIds existieren
                            if (targetId != null && targetId in allPageIds) {
                                val edge = NavEdge(
                                    sourcePageId = page.id,
                                    sourceButtonIndex = index,
                                    targetPageId = targetId
                                )
                                outgoing.getOrPut(page.id) { mutableListOf() }.add(edge)
                                // Exclude general "To Start" links (empty pageId) from incoming map to avoid cluttering the start page
                                if (action.pageId.isNotEmpty()) {
                                    incoming.getOrPut(targetId) { mutableSetOf() }.add(page.id)
                                }
                            }
                        } else if (action is NavigateToStartPageButtonAction) {
                            val resolvedStartPageId = startPageId?.takeIf { it in allPageIds } ?: fallbackStartPageId?.takeIf { it in allPageIds }
                            if (resolvedStartPageId != null) {
                                val edge = NavEdge(
                                    sourcePageId = page.id,
                                    sourceButtonIndex = index,
                                    targetPageId = resolvedStartPageId
                                )
                                outgoing.getOrPut(page.id) { mutableListOf() }.add(edge)
                                // Exclude explicit "To Start" action from incoming map to avoid cluttering the start page
                            }
                        }
                    }
                }
            }

            return BookNavigationGraph(
                outgoing = outgoing,
                incoming = incoming.mapValues { it.value.toList() },
                allPageIds = allPageIds,
                startPageId = startPageId,
                fallbackStartPageId = fallbackStartPageId
            )
        }
    }
}
