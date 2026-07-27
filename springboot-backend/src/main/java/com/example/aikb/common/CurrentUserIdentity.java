package com.example.aikb.common;

import com.example.aikb.security.AuthenticatedUser;

import static com.example.aikb.common.RequestIdentity.requireDepartment;
import static com.example.aikb.common.RequestIdentity.requireUserId;

public final class CurrentUserIdentity {

    private CurrentUserIdentity() {
    }

    public static String userIdOrRequestParam(AuthenticatedUser currentUser, String requestUserId) {
        if (currentUser != null) {
            return currentUser.username();
        }
        return requireUserId(requestUserId);
    }

    public static String departmentOrRequestParam(AuthenticatedUser currentUser, String requestDepartment) {
        if (currentUser != null) {
            return currentUser.department();
        }
        return requireDepartment(requestDepartment);
    }
}
