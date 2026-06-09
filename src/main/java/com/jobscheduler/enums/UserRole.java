package com.jobscheduler.enums;

/**
 * User roles for role-based access control.
 *
 * ADMIN — full access to all endpoints
 * USER  — read-only access (GET endpoints only)
 *
 * Spring Security prefixes roles with "ROLE_" internally,
 * so "ADMIN" maps to "ROLE_ADMIN" in security expressions.
 */
public enum UserRole {
    ADMIN,
    USER
}
