package com.ticketing.system.Presentation.components.admin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit coverage for {@link OrgTreeRenderer} and its nested {@link OrgTreeRenderer.Node} record.
 * Pure JUnit — no Spring context needed; Vaadin components can be instantiated headlessly.
 */
class OrgTreeRendererTest {

    // ── Node record ────────────────────────────────────────────────────────────

    @Test
    void leafConstructor_setsChildrenToEmptyList() {
        var node = new OrgTreeRenderer.Node("A", "Alice Cohen", "Founder", "founder", "Founded 2024");
        assertTrue(node.children().isEmpty());
    }

    @Test
    void fullConstructor_storesAllFields() {
        var child = new OrgTreeRenderer.Node("B", "Bob", "Owner", "owner", "sub");
        var node  = new OrgTreeRenderer.Node("A", "Alice", "Founder", "founder", "sub", List.of(child));

        assertEquals("A",       node.initial());
        assertEquals("Alice",   node.name());
        assertEquals("Founder", node.role());
        assertEquals("founder", node.variant());
        assertEquals("sub",     node.sub());
        assertEquals(1,         node.children().size());
        assertSame(child,       node.children().get(0));
    }

    @Test
    void leafNode_hasNoChildren() {
        var node = new OrgTreeRenderer.Node("C", "Carol", "Manager", "manager", "Appointed by Bob");
        assertTrue(node.children().isEmpty());
    }

    @Test
    void compositeNode_reportsAllChildren() {
        var carol = new OrgTreeRenderer.Node("C", "Carol", "Manager", "manager", "Appointed by Bob");
        var dave  = new OrgTreeRenderer.Node("D", "Dave",  "Manager", "manager", "Appointed by Bob");
        var bob   = new OrgTreeRenderer.Node("B", "Bob", "Owner", "owner", "Appointed by Alice", List.of(carol, dave));

        assertEquals(2, bob.children().size());
        assertEquals("C", bob.children().get(0).initial());
        assertEquals("D", bob.children().get(1).initial());
    }

    @Test
    void nodeEquality_sameFieldsSameChildren_areEqual() {
        var a = new OrgTreeRenderer.Node("X", "Name", "Role", "variant", "sub", List.of());
        var b = new OrgTreeRenderer.Node("X", "Name", "Role", "variant", "sub", List.of());
        assertEquals(a, b);
    }

    // ── OrgTreeRenderer construction ───────────────────────────────────────────

    @Test
    void singleRootConstructor_doesNotThrow() {
        var root = new OrgTreeRenderer.Node("A", "Alice", "Founder", "founder", "Founded 2024");
        assertDoesNotThrow(() -> new OrgTreeRenderer(root));
    }

    @Test
    void listConstructor_doesNotThrow() {
        var a = new OrgTreeRenderer.Node("A", "Alice", "Founder", "founder", "sub");
        var b = new OrgTreeRenderer.Node("B", "Bob",   "Owner",   "owner",   "sub");
        assertDoesNotThrow(() -> new OrgTreeRenderer(List.of(a, b)));
    }

    @Test
    void emptyListConstructor_doesNotThrow() {
        assertDoesNotThrow(() -> new OrgTreeRenderer(List.of()));
    }

    @Test
    void content_hasOrgChartClass() {
        var root = new OrgTreeRenderer.Node("A", "Alice", "Founder", "founder", "sub");
        var renderer = new OrgTreeRenderer(root);
        String classes = renderer.getElement().getAttribute("class");
        assertNotNull(classes, "class attribute must not be null");
        assertTrue(classes.contains("org-chart"),
            "wrapper div must carry 'org-chart' so CSS connector-line selectors fire");
    }

    @Test
    void deepTree_constructsWithoutError() {
        var carol = new OrgTreeRenderer.Node("C", "Carol Levy",   "Manager", "manager", "Appointed by Bob · Manage events");
        var dave  = new OrgTreeRenderer.Node("D", "Dave Peretz",  "Manager", "manager", "Appointed by Bob · Inquiries");
        var bob   = new OrgTreeRenderer.Node("B", "Bob Mizrahi",  "Owner",   "owner",   "Appointed by Alice · 2025-01-08", List.of(carol, dave));
        var frank = new OrgTreeRenderer.Node("F", "Frank Tal",    "Manager", "manager", "Appointed by Eve · Manage events");
        var eve   = new OrgTreeRenderer.Node("E", "Eve Bar",      "Owner",   "owner",   "Appointed by Alice · 2025-02-14", List.of(frank));
        var alice = new OrgTreeRenderer.Node("A", "Alice Cohen",  "Founder", "founder", "Founded 2024-12-15", List.of(bob, eve));

        assertDoesNotThrow(() -> new OrgTreeRenderer(alice));
    }
}
