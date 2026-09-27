package com.iamamansid.urlshortener.entity;

/**
 * Application roles. {@code ADMIN} unlocks the admin dashboard and can manage
 * any link; {@code USER} manages only their own links.
 */
public enum UserRole {
    USER,
    ADMIN
}
