package com.shopMeta.api_gateway.security;

/**
 * Per-route authorisation requirement, attached to a route as metadata (spec 6.2 route table)
 * and enforced by {@link com.shopMeta.api_gateway.filter.JwtAuthenticationFilter}.
 */
public enum AuthRequirement {

    /** No token needed (public catalogue, auth endpoints, ...). */
    PUBLIC,

    /** A valid JWT is required. */
    JWT,

    /** A valid JWT whose {@code roles} claim contains {@code ADMIN} is required. */
    ADMIN;

    /** Route metadata key holding the {@link #name()} of the requirement. */
    public static final String METADATA_KEY = "metashop.auth";
}
