package com.ticketing.system.Presentation.components.venue;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.dom.Element;

import java.util.List;

/**
 * True top-down org chart using nested {@code <ul>/<li>} with CSS
 * connectors (the {@code .org-chart} styles draw the parent-to-child
 * tree lines). Ports the React {@code OrgChart}.
 */
public class OrgChart extends Div {

    public record Node(
        String initial,
        String name,
        String role,
        String roleVariant,
        String sub,
        List<Node> children
    ) { }

    public OrgChart(Node root) {
        addClassName("org-chart");
        Element ul = new Element("ul");
        ul.appendChild(buildLi(root));
        getElement().appendChild(ul);
    }

    private Element buildLi(Node node) {
        String variant = node.roleVariant == null ? "muted" : node.roleVariant;
        boolean hasChildren = node.children != null && !node.children.isEmpty();

        Element li = new Element("li");

        Element card = new Element("div");
        card.setAttribute("class", "oc-card oc-" + variant);

        Element kind = new Element("span");
        kind.setAttribute("class", "oc-kind lk-mono");
        kind.setText(hasChildren ? "Composite" : "Leaf");
        card.appendChild(kind);

        Element avatar = new Element("span");
        avatar.setAttribute("class", "oc-avatar oc-av-" + variant);
        avatar.setText(node.initial);
        card.appendChild(avatar);

        Element name = new Element("div");
        name.setAttribute("class", "oc-name");
        name.setText(node.name);
        card.appendChild(name);

        Element role = new Element("span");
        role.setAttribute("class", "oc-role oc-rb-" + variant);
        role.setText(node.role);
        card.appendChild(role);

        if (node.sub != null) {
            Element sub = new Element("div");
            sub.setAttribute("class", "oc-sub");
            sub.setText(node.sub);
            card.appendChild(sub);
        }

        li.appendChild(card);

        if (hasChildren) {
            Element ul = new Element("ul");
            for (Node child : node.children) ul.appendChild(buildLi(child));
            li.appendChild(ul);
        }
        return li;
    }
}
