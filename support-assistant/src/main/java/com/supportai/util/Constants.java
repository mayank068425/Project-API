package com.supportai.util;

public final class Constants {

    private Constants() {
    }

    public static final String BEARER_PREFIX = "Bearer ";
    public static final String AUTH_HEADER = "Authorization";

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_CLEARANCE = "clearance";
    public static final String CLAIM_TOKEN_TYPE = "type";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    public static final String ROLE_PREFIX = "ROLE_";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_LEAD = "LEAD";
    public static final String ROLE_ENGINEER = "ENGINEER";
    public static final String ROLE_VIEWER = "VIEWER";

    public static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    public static final int ACCOUNT_LOCK_MINUTES = 15;
    public static final int PASSWORD_MIN_LENGTH = 12;

    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;

    public static final String INCIDENT_SEQUENCE = "INCIDENT";
    public static final String INCIDENT_NUMBER_FORMAT = "INC-%06d";

    /** Prefix the model is asked to use when citing a retrieved chunk. */
    public static final String CITATION_PREFIX = "chunk:";
    /** Prefix for timeline evidence references inside an RCA. */
    public static final String EVIDENCE_PREFIX = "E";
}
