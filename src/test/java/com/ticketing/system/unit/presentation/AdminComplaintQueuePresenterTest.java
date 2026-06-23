package com.ticketing.system.unit.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ticketing.system.Core.Application.dto.ComplaintFilterDTO;
import com.ticketing.system.Core.Application.dto.ConversationDTO;
import com.ticketing.system.Core.Application.dto.RespondToComplaintRequestDTO;
import com.ticketing.system.Core.Application.services.MessagingService;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Presentation.presenters.messaging.AdminComplaintQueuePresenter;

class AdminComplaintQueuePresenterTest {

    private static final String TOKEN = "jwt-token";

    private MessagingService messaging;
    private AdminComplaintQueuePresenter presenter;

    @BeforeEach
    void setUp() {
        messaging = mock(MessagingService.class);
        presenter = new AdminComplaintQueuePresenter(messaging);
    }

    private static ConversationDTO complaint(String id, String status) {
        return new ConversationDTO(
            id, "COMPLAINT", status, 1, "MEMBER", 0, "ADMIN_GROUP",
            "Subject " + id, LocalDateTime.now(), LocalDateTime.now(), 0, List.of());
    }

    // -- load -----------------------------------------------------------------

    @Test
    void load_nullToken_returnsNotAuthenticated_withoutCallingService() {
        AdminComplaintQueuePresenter.Outcome outcome = presenter.load(null, "Open");

        assertInstanceOf(AdminComplaintQueuePresenter.Outcome.NotAuthenticated.class, outcome);
        verify(messaging, never()).viewAllComplaints(anyString(), any());
    }

    @Test
    void load_forwardsStatusFilter() {
        when(messaging.viewAllComplaints(eq(TOKEN), any())).thenReturn(List.of());

        presenter.load(TOKEN, "Open");

        ArgumentCaptor<ComplaintFilterDTO> captor = ArgumentCaptor.forClass(ComplaintFilterDTO.class);
        verify(messaging).viewAllComplaints(eq(TOKEN), captor.capture());
        assertEquals("Open", captor.getValue().status());
    }

    @Test
    void load_allFilter_sendsNullStatus() {
        when(messaging.viewAllComplaints(eq(TOKEN), any())).thenReturn(List.of());

        presenter.load(TOKEN, "All");

        ArgumentCaptor<ComplaintFilterDTO> captor = ArgumentCaptor.forClass(ComplaintFilterDTO.class);
        verify(messaging).viewAllComplaints(eq(TOKEN), captor.capture());
        assertNull(captor.getValue().status(), "\"All\" must map to a null (absent) status filter");
    }

    @Test
    void load_nullFilter_sendsNullStatus() {
        when(messaging.viewAllComplaints(eq(TOKEN), any())).thenReturn(List.of());

        presenter.load(TOKEN, null);

        ArgumentCaptor<ComplaintFilterDTO> captor = ArgumentCaptor.forClass(ComplaintFilterDTO.class);
        verify(messaging).viewAllComplaints(eq(TOKEN), captor.capture());
        assertNull(captor.getValue().status());
    }

    @Test
    void load_success_returnsComplaints() {
        ConversationDTO a = complaint("c1", "OPEN");
        ConversationDTO b = complaint("c2", "RESPONDED");
        when(messaging.viewAllComplaints(eq(TOKEN), any())).thenReturn(List.of(a, b));

        AdminComplaintQueuePresenter.Outcome outcome = presenter.load(TOKEN, "All");

        AdminComplaintQueuePresenter.Outcome.Success ok =
            assertInstanceOf(AdminComplaintQueuePresenter.Outcome.Success.class, outcome);
        assertEquals(List.of(a, b), ok.complaints());
    }

    @Test
    void load_invalidToken_returnsNotAuthenticated() {
        when(messaging.viewAllComplaints(eq(TOKEN), any())).thenThrow(new InvalidTokenException("bad"));

        AdminComplaintQueuePresenter.Outcome outcome = presenter.load(TOKEN, "All");

        assertInstanceOf(AdminComplaintQueuePresenter.Outcome.NotAuthenticated.class, outcome);
    }

    @Test
    void load_serviceThrows_returnsFailureWithMessage() {
        when(messaging.viewAllComplaints(eq(TOKEN), any()))
            .thenThrow(new RuntimeException("not an admin"));

        AdminComplaintQueuePresenter.Outcome outcome = presenter.load(TOKEN, "All");

        AdminComplaintQueuePresenter.Outcome.Failure fail =
            assertInstanceOf(AdminComplaintQueuePresenter.Outcome.Failure.class, outcome);
        assertEquals("not an admin", fail.error().message());
    }

    // -- reply ----------------------------------------------------------------

    @Test
    void reply_nullToken_returnsNotAuthenticated_withoutCallingService() {
        AdminComplaintQueuePresenter.ActionOutcome outcome = presenter.reply(null, "c1", "hi");

        assertInstanceOf(AdminComplaintQueuePresenter.ActionOutcome.NotAuthenticated.class, outcome);
        verify(messaging, never()).respondToComplaint(anyString(), any());
    }

    @Test
    void reply_happyPath_returnsSuccess_andSendsBodyWithoutStatusTransition() {
        AdminComplaintQueuePresenter.ActionOutcome outcome =
            presenter.reply(TOKEN, "c1", "Looking into the duplicate charge.");

        assertInstanceOf(AdminComplaintQueuePresenter.ActionOutcome.Success.class, outcome);
        ArgumentCaptor<RespondToComplaintRequestDTO> captor =
            ArgumentCaptor.forClass(RespondToComplaintRequestDTO.class);
        verify(messaging).respondToComplaint(eq(TOKEN), captor.capture());
        assertEquals("c1", captor.getValue().conversationId());
        assertEquals("Looking into the duplicate charge.", captor.getValue().body());
        assertNull(captor.getValue().newStatus(), "a plain reply must not transition status");
    }

    @Test
    void reply_serviceThrows_returnsFailureWithMessage() {
        doThrow(new RuntimeException("conversation closed"))
            .when(messaging).respondToComplaint(anyString(), any());

        AdminComplaintQueuePresenter.ActionOutcome outcome = presenter.reply(TOKEN, "c1", "late");

        AdminComplaintQueuePresenter.ActionOutcome.Failure fail =
            assertInstanceOf(AdminComplaintQueuePresenter.ActionOutcome.Failure.class, outcome);
        assertEquals("conversation closed", fail.error().message());
    }

    @Test
    void reply_invalidToken_returnsNotAuthenticated() {
        doThrow(new InvalidTokenException("bad"))
            .when(messaging).respondToComplaint(anyString(), any());

        AdminComplaintQueuePresenter.ActionOutcome outcome = presenter.reply(TOKEN, "c1", "hi");

        assertInstanceOf(AdminComplaintQueuePresenter.ActionOutcome.NotAuthenticated.class, outcome);
    }

    // -- resolve --------------------------------------------------------------

    @Test
    void resolve_nullToken_returnsNotAuthenticated_withoutCallingService() {
        AdminComplaintQueuePresenter.ActionOutcome outcome = presenter.resolve(null, "c1");

        assertInstanceOf(AdminComplaintQueuePresenter.ActionOutcome.NotAuthenticated.class, outcome);
        verify(messaging, never()).respondToComplaint(anyString(), any());
    }

    @Test
    void resolve_happyPath_returnsSuccess_andSendsResolvedStatusWithNonBlankBody() {
        AdminComplaintQueuePresenter.ActionOutcome outcome = presenter.resolve(TOKEN, "c1");

        assertInstanceOf(AdminComplaintQueuePresenter.ActionOutcome.Success.class, outcome);
        ArgumentCaptor<RespondToComplaintRequestDTO> captor =
            ArgumentCaptor.forClass(RespondToComplaintRequestDTO.class);
        verify(messaging).respondToComplaint(eq(TOKEN), captor.capture());
        assertEquals("c1", captor.getValue().conversationId());
        assertEquals("RESOLVED", captor.getValue().newStatus());
        // The domain rejects blank bodies; resolve must still carry a message.
        assertFalse(captor.getValue().body() == null || captor.getValue().body().isBlank(),
            "resolve must send a non-blank message body");
    }

    @Test
    void resolve_serviceThrows_returnsFailureWithMessage() {
        doThrow(new RuntimeException("already resolved"))
            .when(messaging).respondToComplaint(anyString(), any());

        AdminComplaintQueuePresenter.ActionOutcome outcome = presenter.resolve(TOKEN, "c1");

        AdminComplaintQueuePresenter.ActionOutcome.Failure fail =
            assertInstanceOf(AdminComplaintQueuePresenter.ActionOutcome.Failure.class, outcome);
        assertEquals("already resolved", fail.error().message());
    }

    @Test
    void resolve_invalidToken_returnsNotAuthenticated() {
        doThrow(new InvalidTokenException("bad"))
            .when(messaging).respondToComplaint(anyString(), any());

        AdminComplaintQueuePresenter.ActionOutcome outcome = presenter.resolve(TOKEN, "c1");

        assertInstanceOf(AdminComplaintQueuePresenter.ActionOutcome.NotAuthenticated.class, outcome);
    }
}
