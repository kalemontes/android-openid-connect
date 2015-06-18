package com.lnikkila.oidc;

/**
 * Simple utility class for storing OpenID Connect provider endpoints configuration.
 */
public final class Config {

    // TODO: Add the OIDC endpoints information you received from your OIDC provider below.

    public static final String authorizationServerUrl = "http://openam.example.com:8080/openam/oauth2/authorize";
    public static final String tokenServerUrl = "http://openam.example.com:8080/openam/oauth2/access_token";
    public static final String userInfoUrl = "http://openam.example.com:8080/openam/oauth2/userinfo";
}
