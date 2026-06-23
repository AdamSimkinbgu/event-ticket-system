package com.ticketing.system.unit.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ticketing.system.Core.Application.dto.AnnounceResultDTO;
import com.ticketing.system.Core.Application.dto.AnnouncementRequestDTO;
import com.ticketing.system.Core.Application.dto.ConversationDTO;
import com.ticketing.system.Core.Application.dto.MessageDTO;
import com.ticketing.system.Core.Application.services.MessagingService;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Presentation.presenters.messaging.AdminAnnouncementsPresenter;
import com.ticketing.system.Presentation.presenters.messaging.AdminAnnouncementsPresenter.SentAnnouncement;

class AdminAnnouncementsPresenterTest {

    private static final String TOKEN = "jwt-token";

    private MessagingService messaging;
    private AdminAnnouncementsPresenter presenter;

    @BeforeEach
    void setUp() {
        messaging = mock(MessagingService.class);
        presenter = new AdminAnnouncementsPresenter(messaging);
    }

    private static ConversationDTO announcement(String id, int initiatorId, String subject, String body,
                                                String counterpartyType, int counterpartyId, LocalDateTime when) {
        return new ConversationDTO(
            id, "ANNOUNCEMENT", "OPEN", initiatorId, "ADMIN", counterpartyId, counterpartyType,
            subject, when, when, 0,
            List.of(new MessageDTO("m-" + id, initiatorId, "ADMIN", body, when, false)));
    }

    // -- load -----------------------------------------------------------------

    @Test
    void load_nullToken_returnsNotAuthenticated_withoutCallingService() {
        AdminAnnouncementsPresenter.Outcome outcome = presenter.load(null);

        assertInstanceOf(AdminAnnouncementsPresenter.Outcome.NotAuthenticated.class, outcome);
        verify(messaging, never()).viewSentAnnouncements(anyString());
    }

    @Test
    void load_groupsFanOutByBroadcast_countsRecipients_andSortsNewestFirst() {
        LocalDateTime older = LocalDateTime.of(2026, 6, 20, 10, 0);
        LocalDateTime newer = LocalDateTime.of(2026, 6, 21, 9, 0);
        when(messaging.viewSentAnnouncements(TOKEN)).thenReturn(List.of(
            // newer broadcast: one producer recipient
            announcement("c3", 1, "Producers notice", "hi", "COMPANY", 5, newer),
            // older broadcast: two member recipients sharing subject+body+sender
            announcement("c1", 1, "Maintenance", "down", "MEMBER", 10, older),
            announcement("c2", 1, "Maintenance", "down", "MEMBER", 11, older)));

        AdminAnnouncementsPresenter.Outcome outcome = presenter.load(TOKEN);

        AdminAnnouncementsPresenter.Outcome.Success ok =
            assertInstanceOf(AdminAnnouncementsPresenter.Outcome.Success.class, outcome);
        assertEquals(2, ok.announcements().size());

        SentAnnouncement first = ok.announcements().get(0);   // newest
        assertEquals("Producers notice", first.subject());
        assertEquals("Producers", first.audienceLabel());
        assertEquals(1, first.recipientCount());

        SentAnnouncement second = ok.announcements().get(1);
        assertEquals("Maintenance", second.subject());
        assertEquals("Members", second.audienceLabel());
        assertEquals(2, second.recipientCount());
        assertEquals("Admin #1", second.senderLabel());
    }

    @Test
    void load_mixedRecipientTypes_labelsMembersPlusProducers() {
        LocalDateTime when = LocalDateTime.of(2026, 6, 20, 10, 0);
        when(messaging.viewSentAnnouncements(TOKEN)).thenReturn(List.of(
            announcement("c1", 1, "Hello", "body", "MEMBER", 10, when),
            announcement("c2", 1, "Hello", "body", "COMPANY", 5, when)));

        AdminAnnouncementsPresenter.Outcome outcome = presenter.load(TOKEN);

        AdminAnnouncementsPresenter.Outcome.Success ok =
            assertInstanceOf(AdminAnnouncementsPresenter.Outcome.Success.class, outcome);
        assertEquals(1, ok.announcements().size());
        assertEquals("Members + producers", ok.announcements().get(0).audienceLabel());
        assertEquals(2, ok.announcements().get(0).recipientCount());
    }

    @Test
    void load_invalidToken_returnsNotAuthenticated() {
        when(messaging.viewSentAnnouncements(TOKEN)).thenThrow(new InvalidTokenException("bad"));

        AdminAnnouncementsPresenter.Outcome outcome = presenter.load(TOKEN);

        assertInstanceOf(AdminAnnouncementsPresenter.Outcome.NotAuthenticated.class, outcome);
    }

    @Test
    void load_serviceThrows_returnsFailureWithMessage() {
        when(messaging.viewSentAnnouncements(TOKEN)).thenThrow(new RuntimeException("history down"));

        AdminAnnouncementsPresenter.Outcome outcome = presenter.load(TOKEN);

        AdminAnnouncementsPresenter.Outcome.Failure fail =
            assertInstanceOf(AdminAnnouncementsPresenter.Outcome.Failure.class, outcome);
        assertEquals("history down", fail.reason());
    }

    // -- send -----------------------------------------------------------------

    @Test
    void send_nullToken_returnsNotAuthenticated_withoutCallingService() {
        AdminAnnouncementsPresenter.ActionOutcome outcome =
            presenter.send(null, "Subject", "Body", "ALL_MEMBERS");

        assertInstanceOf(AdminAnnouncementsPresenter.ActionOutcome.NotAuthenticated.class, outcome);
        verify(messaging, never()).announce(anyString(), any());
    }

    @Test
    void send_happyPath_returnsRecipientCount_andForwardsForm() {
        when(messaging.announce(eq(TOKEN), any())).thenReturn(new AnnounceResultDTO(5, "c1"));

        AdminAnnouncementsPresenter.ActionOutcome outcome =
            presenter.send(TOKEN, "Subject", "Body", "ALL_MEMBERS");

        AdminAnnouncementsPresenter.ActionOutcome.Success ok =
            assertInstanceOf(AdminAnnouncementsPresenter.ActionOutcome.Success.class, outcome);
        assertEquals(5, ok.recipientCount());

        ArgumentCaptor<AnnouncementRequestDTO> captor =
            ArgumentCaptor.forClass(AnnouncementRequestDTO.class);
        verify(messaging).announce(eq(TOKEN), captor.capture());
        assertEquals("Subject", captor.getValue().subject());
        assertEquals("Body", captor.getValue().body());
        assertEquals("ALL_MEMBERS", captor.getValue().audienceType());
    }

    @Test
    void send_serviceThrows_returnsFailureWithMessage() {
        when(messaging.announce(eq(TOKEN), any()))
            .thenThrow(new RuntimeException("Announcement matched no recipients"));

        AdminAnnouncementsPresenter.ActionOutcome outcome =
            presenter.send(TOKEN, "Subject", "Body", "PRODUCERS");

        AdminAnnouncementsPresenter.ActionOutcome.Failure fail =
            assertInstanceOf(AdminAnnouncementsPresenter.ActionOutcome.Failure.class, outcome);
        assertEquals("Announcement matched no recipients", fail.reason());
    }

    @Test
    void send_invalidToken_returnsNotAuthenticated() {
        when(messaging.announce(eq(TOKEN), any())).thenThrow(new InvalidTokenException("bad"));

        AdminAnnouncementsPresenter.ActionOutcome outcome =
            presenter.send(TOKEN, "Subject", "Body", "ALL_MEMBERS");

        assertInstanceOf(AdminAnnouncementsPresenter.ActionOutcome.NotAuthenticated.class, outcome);
    }
}
