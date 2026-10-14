package com.supportai.dto.response;

import java.util.List;

/**
 * @param corrections what the verifier changed before this was shown. Returned
 *                    rather than hidden: a draft that needed three corrections is
 *                    one to read more carefully, and concealing that would make
 *                    the verification invisible to the person relying on it.
 */
public record RcaResponse(RcaDraft draft, List<String> corrections, int evidenceCount,
                          List<String> evidenceReferences, String verificationOutcome,
                          boolean usable, String note) {
}
