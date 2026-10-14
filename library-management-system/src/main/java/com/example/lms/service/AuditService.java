package com.example.lms.service;

public interface AuditService {

    String BOOK_ADDED = "BOOK_ADDED";
    String BOOK_UPDATED = "BOOK_UPDATED";
    String BOOK_DELETED = "BOOK_DELETED";
    String BOOK_ISSUED = "BOOK_ISSUED";
    String BOOK_RETURNED = "BOOK_RETURNED";
    String MEMBER_REGISTERED = "MEMBER_REGISTERED";
    String MEMBER_UPDATED = "MEMBER_UPDATED";
    String MEMBER_STATUS_CHANGED = "MEMBER_STATUS_CHANGED";
    String FINE_PAID = "FINE_PAID";
    String FINE_WAIVED = "FINE_WAIVED";
    String RESERVATION_CREATED = "RESERVATION_CREATED";
    String RESERVATION_READY = "RESERVATION_READY";
    String LOGIN_FAILED = "LOGIN_FAILED";

    void record(String action, String entityType, String entityId, String detail);

    void recordChange(String action, String entityType, String entityId,
                      String oldValue, String newValue);
}
