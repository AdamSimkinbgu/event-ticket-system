package com.ticketing.system.Presentation.presenters.messaging;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.AnnounceResultDTO;
import com.ticketing.system.Core.Application.dto.AnnouncementRequestDTO;
import com.ticketing.system.Core.Application.dto.ConversationDTO;
import com.ticketing.system.Core.Application.services.MessagingService;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Presentation.components.ErrorPayload;
import com.ticketing.system.Presentation.presenters.ExceptionTranslator;

/**
 * MVP presenter for {@code AdminAnnouncementsView} (#270). Holds no Vaadin imports so the
 * outcome → UI translation lives in the view and the service-call decision tree is unit-testable
 * in isolation (the view reads the token from {@code AuthSession} and passes it in, mirroring the
 * other messaging wire-up presenters).
 *
 * <p>{@code send} broadcasts via {@code announce} and reports the recipient count. {@code load}
 * reads the platform's sent announcements ({@code viewSentAnnouncements}) and <em>groups</em> the
 * per-recipient fan-out back into logical broadcasts: announce creates one ANNOUNCEMENT conversation
 * per recipient with no shared id, so we bucket by {@code initiatorId + subject + body}, count
 * recipients, and infer the audience from the recipients' participant type.
 */
@Component
public class AdminAnnouncementsPresenter {

    private static final String COMPANY = "COMPANY";

    private final MessagingService messagingService;

    @Autowired
    public AdminAnnouncementsPresenter(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    /** Loads the sent-announcement history, grouped into logical broadcasts (newest first). */
    public Outcome load(String token) {
        if (token == null) {
            return new Outcome.NotAuthenticated();
        }
        try {
            List<ConversationDTO> announcements = messagingService.viewSentAnnouncements(token);
            return new Outcome.Success(group(announcements));
        } catch (InvalidTokenException e) {
            return new Outcome.NotAuthenticated();
        } catch (RuntimeException e) {
            return new Outcome.Failure(ExceptionTranslator.toPayload(e));
        }
    }

    /** Broadcasts an announcement to the given audience; returns the recipient count on success. */
    public ActionOutcome send(String token, String subject, String body, String audienceType) {
        if (token == null) {
            return new ActionOutcome.NotAuthenticated();
        }
        try {
            // adminId is ignored by the service (it resolves the caller from the token); member /
            // company id lists are empty — the audienceType alone selects all members or all producers.
            AnnounceResultDTO result = messagingService.announce(token,
                new AnnouncementRequestDTO(0, subject, body, audienceType, List.of(), List.of()));
            return new ActionOutcome.Success(result.recipientCount());
        } catch (InvalidTokenException e) {
            return new ActionOutcome.NotAuthenticated();
        } catch (RuntimeException e) {
            return new ActionOutcome.Failure(ExceptionTranslator.toPayload(e));
        }
    }

    // -- Grouping the per-recipient fan-out into logical broadcasts -----------

    private static List<SentAnnouncement> group(List<ConversationDTO> announcements) {
        Map<String, Bucket> buckets = new LinkedHashMap<>();
        for (ConversationDTO c : announcements) {
            String body = c.messages().isEmpty() ? "" : c.messages().get(0).body();
            String key = c.initiatorId() + "\u0000" + c.subject() + "\u0000" + body;
            buckets.computeIfAbsent(key, k -> new Bucket(c.initiatorId(), c.subject())).add(c);
        }
        return buckets.values().stream()
            .map(Bucket::toSentAnnouncement)
            .sorted(Comparator.comparing(SentAnnouncement::sentAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .toList();
    }

    /** Mutable accumulator for one logical broadcast (a group of fan-out conversations). */
    private static final class Bucket {
        private final int initiatorId;
        private final String subject;
        private int count;
        private LocalDateTime earliest;
        private boolean hasMember;
        private boolean hasCompany;

        Bucket(int initiatorId, String subject) {
            this.initiatorId = initiatorId;
            this.subject = subject;
        }

        void add(ConversationDTO c) {
            count++;
            if (c.createdAt() != null && (earliest == null || c.createdAt().isBefore(earliest))) {
                earliest = c.createdAt();
            }
            if (COMPANY.equals(c.counterpartyType())) {
                hasCompany = true;
            } else {
                hasMember = true;   // announcements target MEMBER or COMPANY
            }
        }

        SentAnnouncement toSentAnnouncement() {
            String audience = hasMember && hasCompany ? "Members + producers"
                : hasCompany ? "Producers" : "Members";
            return new SentAnnouncement(earliest, subject, audience, count, "Admin #" + initiatorId);
        }
    }

    /** One logical broadcast row for the sent-history grid. */
    public record SentAnnouncement(LocalDateTime sentAt, String subject, String audienceLabel,
                                   int recipientCount, String senderLabel) { }

    /** Sealed outcome the view switches on to render the history grid or an empty/error state. */
    public sealed interface Outcome {
        record Success(List<SentAnnouncement> announcements) implements Outcome { }
        record NotAuthenticated() implements Outcome { }
        record Failure(ErrorPayload error) implements Outcome { }
    }

    /** Result of a send the view reacts to (carries the recipient count for the confirmation). */
    public sealed interface ActionOutcome {
        record Success(int recipientCount) implements ActionOutcome { }
        record NotAuthenticated() implements ActionOutcome { }
        record Failure(ErrorPayload error) implements ActionOutcome { }
    }
}
