package com.ticketing.system.Presentation.components.admin;

import com.ticketing.system.Core.Application.dto.OrganizationalTreeNodeDTO;
import com.ticketing.system.Core.Domain.users.Permission;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;

import java.util.stream.Collectors;

public class OrgTreeRenderer extends Composite<Div> {

    public OrgTreeRenderer(OrganizationalTreeNodeDTO root) {
        Div tree = getContent();
        tree.addClassName("org-chart");
        UnorderedList ul = new UnorderedList();
        ul.add(buildNode(root));
        tree.add(ul);
    }

    private ListItem buildNode(OrganizationalTreeNodeDTO dto) {
        ListItem li = new ListItem();

        String variant   = dto.isFounder() ? "founder" : dto.role().equals("Owner") ? "owner" : "manager";
        String roleLabel = dto.isFounder() ? "Founder" : dto.role().equals("Owner") ? "Owner"  : "Manager";
        String initial   = dto.username().substring(0, 1).toUpperCase();
        String sub       = dto.isFounder()
                ? "Company Founder"
                : dto.grantedPermissions().stream()
                        .map(Permission::name)
                        .collect(Collectors.joining(", "));

        boolean composite = !dto.appointedByThisUser().isEmpty();

        Div ocCard = new Div();
        ocCard.addClassName("oc-card");
        ocCard.addClassName("oc-" + variant);

        Span kind = new Span(composite ? "Composite" : "Leaf");
        kind.addClassName("oc-kind");
        kind.addClassName("lk-mono");

        Span avatar = new Span(initial);
        avatar.addClassName("oc-avatar");
        avatar.addClassName("oc-av-" + variant);

        Div name = new Div(new Span(dto.username()));
        name.addClassName("oc-name");

        Span role = new Span(roleLabel);
        role.addClassName("oc-role");
        role.addClassName("oc-rb-" + variant);

        ocCard.add(kind, avatar, name, role);
        if (!sub.isEmpty()) {
            Div subDiv = new Div(new Span(sub));
            subDiv.addClassName("oc-sub");
            ocCard.add(subDiv);
        }
        li.add(ocCard);

        if (composite) {
            UnorderedList kidsUl = new UnorderedList();
            for (OrganizationalTreeNodeDTO child : dto.appointedByThisUser()) kidsUl.add(buildNode(child));
            li.add(kidsUl);
        }
        return li;
    }
}
