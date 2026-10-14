package com.supportai.dto.response;

import java.util.List;

/**
 * @param redacted             what was removed, so an approver can see what the
 *                             model tried to include
 * @param commitmentsToReview  promises the incident record cannot support.
 *                             Flagged rather than deleted: a restoration-time
 *                             commitment is a contractual statement and belongs
 *                             in front of the person accountable for it.
 */
public record DraftResponse(String subject, String body, List<String> redacted,
                            List<String> commitmentsToReview, boolean requiresApproval) {
}
