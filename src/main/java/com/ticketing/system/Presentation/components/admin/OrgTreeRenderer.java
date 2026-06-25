package com.ticketing.system.Presentation.components.admin;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;

import java.util.List;

public class OrgTreeRenderer extends Composite<Div> {

    public record Node(String initial, String name, String role, String variant, String sub, List<Node> children) {
        public Node(String initial, String name, String role, String variant, String sub) {
            this(initial, name, role, variant, sub, List.of());
        }
    }

    public OrgTreeRenderer(Node root) {
        this(List.of(root));
    }

    public OrgTreeRenderer(List<Node> roots) {
        Div tree = getContent();
        tree.addClassName("org-chart");
        UnorderedList ul = new UnorderedList();
        for (Node root : roots) ul.add(buildNode(root));
        tree.add(ul);
    }

    private ListItem buildNode(Node node) {
        ListItem li = new ListItem();

        Div ocCard = new Div();
        ocCard.addClassName("oc-card");
        ocCard.addClassName("oc-" + node.variant());

        boolean composite = !node.children().isEmpty();
        Span kind = new Span(composite ? "Composite" : "Leaf");
        kind.addClassName("oc-kind");
        kind.addClassName("lk-mono");

        Span avatar = new Span(node.initial());
        avatar.addClassName("oc-avatar");
        avatar.addClassName("oc-av-" + node.variant());

        Div name = new Div(new Span(node.name()));
        name.addClassName("oc-name");

        Span role = new Span(node.role());
        role.addClassName("oc-role");
        role.addClassName("oc-rb-" + node.variant());

        ocCard.add(kind, avatar, name, role);
        if (node.sub() != null && !node.sub().isEmpty()) {
            Div sub = new Div(new Span(node.sub()));
            sub.addClassName("oc-sub");
            ocCard.add(sub);
        }
        li.add(ocCard);

        if (composite) {
            UnorderedList kidsUl = new UnorderedList();
            for (Node child : node.children()) kidsUl.add(buildNode(child));
            li.add(kidsUl);
        }
        return li;
    }
}
