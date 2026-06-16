package com.andreas_kratzer.ghosttalk.core.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookNavigationGraphTest {

    @Test
    fun `empty pages list returns empty graph`() {
        val graph = BookNavigationGraph.from(emptyList(), null)
        assertTrue(graph.allPageIds.isEmpty())
        assertNull(graph.buildTree())
        assertTrue(graph.orphans().isEmpty())
    }

    @Test
    fun `linear chain resolves correctly`() {
        val pages = listOf(
            Page(
                id = "page1",
                bookId = "book1",
                name = "Page 1",
                orderIndex = 1,
                buttonConfigs = listOf(
                    ButtonConfig(label = "Go to 2", isActive = true, buttonAction = NavigateToPageButtonAction("page2"))
                )
            ),
            Page(
                id = "page2",
                bookId = "book1",
                name = "Page 2",
                orderIndex = 2,
                buttonConfigs = listOf(
                    ButtonConfig(label = "Go to 3", isActive = true, buttonAction = NavigateToPageButtonAction("page3"))
                )
            ),
            Page(
                id = "page3",
                bookId = "book1",
                name = "Page 3",
                orderIndex = 3,
                buttonConfigs = emptyList()
            )
        )

        val graph = BookNavigationGraph.from(pages, "page1")
        assertEquals(setOf("page1", "page2", "page3"), graph.allPageIds)

        // Verify incoming and outgoing
        assertEquals(listOf(NavEdge("page1", 0, "page2")), graph.outgoing["page1"])
        assertEquals(listOf(NavEdge("page2", 0, "page3")), graph.outgoing["page2"])
        assertEquals(listOf("page1"), graph.incoming["page2"])
        assertEquals(listOf("page2"), graph.incoming["page3"])

        // Orphans (none since page1 is startPage, and page2, page3 have incoming edges)
        assertTrue(graph.orphans().isEmpty())

        // Build tree
        val root = graph.buildTree()
        assertNotNull(root)
        assertEquals("page1", root!!.pageId)
        assertEquals(0, root.depth)
        assertEquals(false, root.isReference)
        assertEquals(1, root.children.size)

        val child1 = root.children[0]
        assertEquals("page2", child1.pageId)
        assertEquals(1, child1.depth)
        assertEquals(false, child1.isReference)
        assertEquals(1, child1.children.size)

        val child2 = child1.children[0]
        assertEquals("page3", child2.pageId)
        assertEquals(2, child2.depth)
        assertEquals(false, child2.isReference)
        assertTrue(child2.children.isEmpty())
    }

    @Test
    fun `cycle detection prevents infinite recursion`() {
        // A -> B -> A
        val pages = listOf(
            Page(
                id = "pageA",
                bookId = "book1",
                name = "Page A",
                orderIndex = 1,
                buttonConfigs = listOf(
                    ButtonConfig(label = "Go to B", isActive = true, buttonAction = NavigateToPageButtonAction("pageB"))
                )
            ),
            Page(
                id = "pageB",
                bookId = "book1",
                name = "Page B",
                orderIndex = 2,
                buttonConfigs = listOf(
                    ButtonConfig(label = "Go to A", isActive = true, buttonAction = NavigateToPageButtonAction("pageA"))
                )
            )
        )

        val graph = BookNavigationGraph.from(pages, "pageA")
        val root = graph.buildTree()
        assertNotNull(root)
        assertEquals("pageA", root!!.pageId)
        assertEquals(false, root.isReference)
        assertEquals(1, root.children.size)

        val child = root.children[0]
        assertEquals("pageB", child.pageId)
        assertEquals(false, child.isReference)
        assertEquals(1, child.children.size)

        val leaf = child.children[0]
        assertEquals("pageA", leaf.pageId)
        assertEquals(true, leaf.isReference) // Cycle back to A detected
        assertTrue(leaf.children.isEmpty())
    }

    @Test
    fun `multiple parents shows first expansion and then reference`() {
        // A -> C
        // B -> C (B is not starting point, but let's see how they are structured)
        // If we start at A, and then we also traversal-wise or graph-wise see C.
        // Let's create a graph with:
        // A -> B, A -> C, B -> C
        val pages = listOf(
            Page(
                id = "pageA",
                bookId = "book1",
                name = "Page A",
                orderIndex = 1,
                buttonConfigs = listOf(
                    ButtonConfig(label = "Go to B", isActive = true, buttonAction = NavigateToPageButtonAction("pageB")),
                    ButtonConfig(label = "Go to C", isActive = true, buttonAction = NavigateToPageButtonAction("pageC"))
                )
            ),
            Page(
                id = "pageB",
                bookId = "book1",
                name = "Page B",
                orderIndex = 2,
                buttonConfigs = listOf(
                    ButtonConfig(label = "Go to C from B", isActive = true, buttonAction = NavigateToPageButtonAction("pageC"))
                )
            ),
            Page(
                id = "pageC",
                bookId = "book1",
                name = "Page C",
                orderIndex = 3,
                buttonConfigs = emptyList()
            )
        )

        val graph = BookNavigationGraph.from(pages, "pageA")
        val root = graph.buildTree()
        assertNotNull(root)

        // A has children: B and C
        // Order of children in the tree is defined by outgoing edges, which follow the buttonConfig order: pageB first, then pageC.
        // So B is expanded first, then C.
        // When B is expanded, its children are processed. C is a child of B. C has not been visited yet, so C is expanded under B.
        // When A's second child C is processed, it is already globally visited (since B expanded it). So it should be a reference.
        assertEquals(2, root!!.children.size)

        val childB = root.children[0]
        assertEquals("pageB", childB.pageId)
        assertEquals(1, childB.children.size)

        val childCUnderB = childB.children[0]
        assertEquals("pageC", childCUnderB.pageId)
        assertEquals(false, childCUnderB.isReference) // first expansion

        val childCUnderA = root.children[1]
        assertEquals("pageC", childCUnderA.pageId)
        assertEquals(true, childCUnderA.isReference) // reference because already globally visited
    }

    @Test
    fun `orphans logic identifies unconnected pages`() {
        val pages = listOf(
            Page(id = "start", bookId = "book1", name = "Start", orderIndex = 1, buttonConfigs = emptyList()),
            Page(id = "connected", bookId = "book1", name = "Connected", orderIndex = 2, buttonConfigs = emptyList()),
            Page(id = "orphan1", bookId = "book1", name = "Orphan 1", orderIndex = 3, buttonConfigs = emptyList()),
            Page(id = "orphan2", bookId = "book1", name = "Orphan 2", orderIndex = 4, buttonConfigs = emptyList())
        )

        // Make start point to connected
        val startPageWithLink = pages[0].copy(
            buttonConfigs = listOf(ButtonConfig(label = "Go", isActive = true, buttonAction = NavigateToPageButtonAction("connected")))
        )

        val graph = BookNavigationGraph.from(listOf(startPageWithLink) + pages.drop(1), "start")

        val orphans = graph.orphans()
        assertEquals(2, orphans.size)
        assertTrue(orphans.contains("orphan1"))
        assertTrue(orphans.contains("orphan2"))
    }

    @Test
    fun `dead link targets are filtered out`() {
        val pages = listOf(
            Page(
                id = "page1",
                bookId = "book1",
                name = "Page 1",
                buttonConfigs = listOf(
                    ButtonConfig(label = "Go to non-existent", isActive = true, buttonAction = NavigateToPageButtonAction("nonexistent"))
                )
            )
        )

        val graph = BookNavigationGraph.from(pages, "page1")
        assertTrue(graph.outgoing.isEmpty())
        assertTrue(graph.incoming.isEmpty())
    }

    @Test
    fun `null start page fallback uses smallest orderIndex`() {
        val pages = listOf(
            Page(id = "page3", bookId = "book1", name = "Page 3", orderIndex = 3, buttonConfigs = emptyList()),
            Page(id = "page2", bookId = "book1", name = "Page 2", orderIndex = 2, buttonConfigs = emptyList()),
            Page(id = "page4", bookId = "book1", name = "Page 4", orderIndex = 4, buttonConfigs = emptyList())
        )

        val graph = BookNavigationGraph.from(pages, null)
        val root = graph.buildTree()
        assertNotNull(root)
        assertEquals("page2", root!!.pageId) // page2 has orderIndex 2 (smallest)
    }

    @Test
    fun `source button index respects null gaps and preceding buttons`() {
        // buttonConfigs: [null, speak, nav->page2] -> der Nav-Button sitzt auf echtem Index 2
        val pages = listOf(
            Page(
                id = "page1",
                bookId = "book1",
                name = "Page 1",
                orderIndex = 1,
                buttonConfigs = listOf(
                    null,
                    ButtonConfig(label = "Sprich", isActive = true, buttonAction = SpeakTextButtonAction()),
                    ButtonConfig(label = "Go to 2", isActive = true, buttonAction = NavigateToPageButtonAction("page2"))
                )
            ),
            Page(id = "page2", bookId = "book1", name = "Page 2", orderIndex = 2, buttonConfigs = emptyList())
        )

        val graph = BookNavigationGraph.from(pages, "page1")
        assertEquals(listOf(NavEdge("page1", 2, "page2")), graph.outgoing["page1"])
    }

    @Test
    fun `stale start page id falls back to smallest order index`() {
        val pages = listOf(
            Page(id = "page2", bookId = "book1", name = "Page 2", orderIndex = 2, buttonConfigs = emptyList()),
            Page(id = "page3", bookId = "book1", name = "Page 3", orderIndex = 3, buttonConfigs = emptyList())
        )

        // startPageId zeigt auf eine gelöschte Seite -> Baum darf NICHT null sein, sondern Fallback nutzen
        val graph = BookNavigationGraph.from(pages, "deleted-page")
        val root = graph.buildTree()
        assertNotNull(root)
        assertEquals("page2", root!!.pageId)
    }

    @Test
    fun `orphans use reachability not just incoming edges`() {
        // Start -> A (erreichbar). Abgekoppelter Cluster: X -> Y (Y hat eingehende Kante, ist aber unerreichbar)
        val pages = listOf(
            Page(
                id = "start", bookId = "book1", name = "Start", orderIndex = 1,
                buttonConfigs = listOf(ButtonConfig(label = "A", isActive = true, buttonAction = NavigateToPageButtonAction("pageA")))
            ),
            Page(id = "pageA", bookId = "book1", name = "A", orderIndex = 2, buttonConfigs = emptyList()),
            Page(
                id = "pageX", bookId = "book1", name = "X", orderIndex = 3,
                buttonConfigs = listOf(ButtonConfig(label = "Y", isActive = true, buttonAction = NavigateToPageButtonAction("pageY")))
            ),
            Page(id = "pageY", bookId = "book1", name = "Y", orderIndex = 4, buttonConfigs = emptyList())
        )

        val graph = BookNavigationGraph.from(pages, "start")
        val orphans = graph.orphans()
        // Sowohl X als auch Y sind nicht erreichbar -> beide Waisen, obwohl Y eine eingehende Kante hat
        assertEquals(setOf("pageX", "pageY"), orphans.toSet())
        assertEquals(setOf("start", "pageA"), graph.reachablePageIds())
    }

    @Test
    fun `inactive and empty label buttons are ignored`() {
        val pages = listOf(
            Page(
                id = "page1",
                bookId = "book1",
                name = "Page 1",
                buttonConfigs = listOf(
                    ButtonConfig(label = "Go to 2", isActive = false, buttonAction = NavigateToPageButtonAction("page2")),
                    ButtonConfig(label = "", isActive = true, buttonAction = NavigateToPageButtonAction("page2")),
                    ButtonConfig(label = "Speech button", isActive = true, buttonAction = SpeakTextButtonAction())
                )
            ),
            Page(
                id = "page2",
                bookId = "book1",
                name = "Page 2",
                buttonConfigs = emptyList()
            )
        )

        val graph = BookNavigationGraph.from(pages, "page1")
        assertTrue(graph.outgoing.isEmpty())
        assertTrue(graph.incoming.isEmpty())
    }

    @Test
    fun `deadEnds identifies pages without outgoing navigation edges`() {
        val pages = listOf(
            Page(
                id = "page1",
                bookId = "book1",
                name = "Page 1",
                buttonConfigs = listOf(
                    ButtonConfig(label = "Go to 2", isActive = true, buttonAction = NavigateToPageButtonAction("page2"))
                )
            ),
            Page(
                id = "page2", // dead end: has Speak action but no navigate action
                bookId = "book1",
                name = "Page 2",
                buttonConfigs = listOf(
                    ButtonConfig(label = "Speak", isActive = true, buttonAction = SpeakTextButtonAction())
                )
            ),
            Page(
                id = "page3", // dead end: has no buttons
                bookId = "book1",
                name = "Page 3",
                buttonConfigs = emptyList()
            ),
            Page(
                id = "page4", // dead end: has a navigate action but to nonexistent page (which is filtered out)
                bookId = "book1",
                name = "Page 4",
                buttonConfigs = listOf(
                    ButtonConfig(label = "Go to fake", isActive = true, buttonAction = NavigateToPageButtonAction("nonexistent"))
                )
            )
        )

        val graph = BookNavigationGraph.from(pages, "page1")
        val deadEnds = graph.deadEnds()
        assertEquals(3, deadEnds.size)
        assertTrue(deadEnds.contains("page2"))
        assertTrue(deadEnds.contains("page3"))
        assertTrue(deadEnds.contains("page4"))
        // page1 is not a dead end
        assertTrue(!deadEnds.contains("page1"))
    }

    @Test
    fun `mixed dead-ends and orphans cycles`() {
        // page1 -> page2 -> page1 (cycle)
        // page3 (orphan and dead-end)
        val pages = listOf(
            Page(
                id = "page1",
                bookId = "book1",
                name = "Page 1",
                buttonConfigs = listOf(
                    ButtonConfig(label = "Go to 2", isActive = true, buttonAction = NavigateToPageButtonAction("page2"))
                )
            ),
            Page(
                id = "page2",
                bookId = "book1",
                name = "Page 2",
                buttonConfigs = listOf(
                    ButtonConfig(label = "Go to 1", isActive = true, buttonAction = NavigateToPageButtonAction("page1"))
                )
            ),
            Page(
                id = "page3",
                bookId = "book1",
                name = "Page 3",
                buttonConfigs = emptyList()
            )
        )

        val graph = BookNavigationGraph.from(pages, "page1")
        assertEquals(listOf("page3"), graph.deadEnds())
        assertEquals(listOf("page3"), graph.orphans())
    }

    @Test
    fun `connectedComponents with fully connected, fully disconnected and multiple clusters`() {
        // Cluster 1: page1 <-> page2
        // Cluster 2: page3 -> page4
        // Cluster 3: page5 (isolated)
        val pages = listOf(
            Page(
                id = "page1",
                bookId = "book1",
                name = "Page 1",
                buttonConfigs = listOf(
                    ButtonConfig(label = "Go to 2", isActive = true, buttonAction = NavigateToPageButtonAction("page2"))
                )
            ),
            Page(
                id = "page2",
                bookId = "book1",
                name = "Page 2",
                buttonConfigs = listOf(
                    ButtonConfig(label = "Go to 1", isActive = true, buttonAction = NavigateToPageButtonAction("page1"))
                )
            ),
            Page(
                id = "page3",
                bookId = "book1",
                name = "Page 3",
                buttonConfigs = listOf(
                    ButtonConfig(label = "Go to 4", isActive = true, buttonAction = NavigateToPageButtonAction("page4"))
                )
            ),
            Page(
                id = "page4",
                bookId = "book1",
                name = "Page 4",
                buttonConfigs = emptyList()
            ),
            Page(
                id = "page5",
                bookId = "book1",
                name = "Page 5",
                buttonConfigs = emptyList()
            )
        )

        val graph = BookNavigationGraph.from(pages, "page1")
        val components = graph.connectedComponents()

        assertEquals(3, components.size)
        // Find which component is which
        val c1 = components.first { it.contains("page1") }
        val c2 = components.first { it.contains("page3") }
        val c3 = components.first { it.contains("page5") }

        assertEquals(setOf("page1", "page2"), c1)
        assertEquals(setOf("page3", "page4"), c2)
        assertEquals(setOf("page5"), c3)
    }

    @Test
    fun `connectedComponents treats directed edges as undirected`() {
        // pageA -> pageB (directed). Under weakly connected, they should be in the same component.
        val pages = listOf(
            Page(
                id = "pageA",
                bookId = "book1",
                name = "Page A",
                buttonConfigs = listOf(
                    ButtonConfig(label = "Go to B", isActive = true, buttonAction = NavigateToPageButtonAction("pageB"))
                )
            ),
            Page(
                id = "pageB",
                bookId = "book1",
                name = "Page B",
                buttonConfigs = emptyList()
            )
        )

        val graph = BookNavigationGraph.from(pages, "pageA")
        val components = graph.connectedComponents()
        assertEquals(1, components.size)
        assertEquals(setOf("pageA", "pageB"), components[0])
    }

    @Test
    fun `NavigateToStartPageButtonAction is recognized as valid outgoing edge and not dead end`() {
        val pages = listOf(
            Page(
                id = "page1",
                bookId = "book1",
                name = "Page 1",
                orderIndex = 1,
                buttonConfigs = emptyList()
            ),
            Page(
                id = "page2",
                bookId = "book1",
                name = "Page 2",
                orderIndex = 2,
                buttonConfigs = listOf(
                    ButtonConfig(label = "Zurück zum Start", isActive = true, buttonAction = NavigateToStartPageButtonAction())
                )
            )
        )

        val graph = BookNavigationGraph.from(pages, "page1")
        // page2 has an outgoing edge to page1 (startPageId)
        assertEquals(listOf(NavEdge("page2", 0, "page1")), graph.outgoing["page2"])
        // page2 should not be in deadEnds
        assertTrue(!graph.deadEnds().contains("page2"))
        // but target page1 should not have page2 in its incoming references (to avoid graph clutter)
        assertTrue(graph.incoming["page1"].isNullOrEmpty())
    }

    @Test
    fun `NavigateToPageButtonAction with empty pageId is recognized as navigation to start page`() {
        val pages = listOf(
            Page(
                id = "page1",
                bookId = "book1",
                name = "Page 1",
                orderIndex = 1,
                buttonConfigs = emptyList()
            ),
            Page(
                id = "page2",
                bookId = "book1",
                name = "Page 2",
                orderIndex = 2,
                buttonConfigs = listOf(
                    ButtonConfig(label = "Zum Start", isActive = true, buttonAction = NavigateToPageButtonAction(pageId = ""))
                )
            )
        )

        val graph = BookNavigationGraph.from(pages, "page1")
        // page2 has an outgoing edge to page1 (startPageId)
        assertEquals(listOf(NavEdge("page2", 0, "page1")), graph.outgoing["page2"])
        // page2 should not be in deadEnds
        assertTrue(!graph.deadEnds().contains("page2"))
        // but target page1 should not have page2 in its incoming references (to avoid graph clutter)
        assertTrue(graph.incoming["page1"].isNullOrEmpty())
    }
}
