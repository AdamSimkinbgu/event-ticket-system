package com.ticketing.system.Presentation.presenters.messaging;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.ComplaintFilterDTO;
import com.ticketing.system.Core.Application.dto.ConversationDTO;
import com.ticketing.system.Core.Application.dto.RespondToComplaintRequestDTO;
import com.ticketing.system.Core.Application.services.MessagingService;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Presentation.components.ErrorPayload;
import com.ticketing.system.Presentation.presenters.ExceptionTranslator;

/**
 * MVP presenter for {@code AdminComplaintQueueView} (#269). Holds no Vaadin imports so the
 * outcome → UI translation lives in the view and the service-call decision tree is unit-testable
 * in isolation (the view reads the token from {@code AuthSession} and passes it in, mirroring
 * {@code SupportInboxPresenter} / {@code CompanyInquiryInboxPresenter}).
 *
 * <p>Lists the platform-wide complaint queue ({@code viewAllComplaints}, admin-gated, status
 * filtered server-side). Both "reply" and "mark resolved" go through {@code respondToComplaint}:
 * a reply leaves the status to auto-flip OPEN→RESPONDED, while resolve passes RESOLVED. Because
 * the domain forbids blank message bodies and the service always appends one, resolve sends a
 * short canned note (no participant-callable "resolve without a message" exists for a complaint).
 */
@Component
public class AdminComplaintQueuePresenter {

    private static final String ALL = "All";
    private static final String RESOLVED = "RESOLVED";
    private static final String RESOLVED_NOTE = "Marked as resolved.";

    private final MessagingService messagingService;

    @Autowired
    public AdminComplaintQueuePresenter(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    /** Loads the complaint queue, optionally filtered to a single status ("All"/blank → no filter). */
    public Outcome load(String token, String statusFilter) {
        if (token == null) {
            return new Outcome.NotAuthenticated();
        }
        try {
            String status = (statusFilter == null || statusFilter.isBlank() || ALL.equals(statusFilter))
                ? null : statusFilter;
            List<ConversationDTO> complaints = messagingService.viewAllComplaints(
                token, new ComplaintFilterDTO(status, null, null, null));
            return new Outcome.Success(complaints);
        } catch (InvalidTokenException e) {
            return new Outcome.NotAuthenticated();
        } catch (RuntimeException e) {
            return new Outcome.Failure(ExceptionTranslator.toPayload(e));
        }
    }

    /** Appends an admin response (status auto-flips to RESPONDED); the admin is resolved from the token. */
    public ActionOutcome reply(String token, String conversationId, String body) {
        if (token == null) {
            return new ActionOutcome.NotAuthenticated();
        }
        try {
            // adminId is ignored by the service (it resolves the caller from the token); null
            // newStatus leaves the transition to addMessage (OPEN → RESPONDED).
            messagingService.respondToComplaint(token,
                new RespondToComplaintRequestDTO(conversationId, 0, body, null));
            return new ActionOutcome.Success();
        } catch (InvalidTokenException e) {
            return new ActionOutcome.NotAuthenticated();
        } catch (RuntimeException e) {
            return new ActionOutcome.Failure(ExceptionTranslator.toPayload(e));
        }
    }

    /**
     * Marks a complaint resolved. The only RESOLVED path is {@code respondToComplaint}, which always
     * appends a (non-blank) message, so we send a short canned closing note alongside the transition.
     */
    public ActionOutcome resolve(String token, String conversationId) {
        if (token == null) {
            return new ActionOutcome.NotAuthenticated();
        }
        try {
            messagingService.respondToComplaint(token,
                new RespondToComplaintRequestDTO(conversationId, 0, RESOLVED_NOTE, RESOLVED));
            return new ActionOutcome.Success();
        } catch (InvalidTokenException e) {
            return new ActionOutcome.NotAuthenticated();
        } catch (RuntimeException e) {
            return new ActionOutcome.Failure(ExceptionTranslator.toPayload(e));
        }
    }

    /** Sealed outcome the view switches on to render the queue or an empty/error state. */
    public sealed interface Outcome {
        record Success(List<ConversationDTO> complaints) implements Outcome { }
        record NotAuthenticated() implements Outcome { }
        record Failure(ErrorPayload error) implements Outcome { }
    }

    /** Result of a reply / resolve the view reacts to. */
    public sealed interface ActionOutcome {
        record Success() implements ActionOutcome { }
        record NotAuthenticated() implements ActionOutcome { }
        record Failure(ErrorPayload error) implements ActionOutcome { }
    }
}
