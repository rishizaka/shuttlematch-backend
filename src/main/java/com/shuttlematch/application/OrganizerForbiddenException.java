package com.shuttlematch.application;

/**
 * 運営者トークンが無い、または一致しないのに運営者専用の操作が要求された。
 */
public class OrganizerForbiddenException extends RuntimeException {

    public OrganizerForbiddenException(String message) {
        super(message);
    }
}
