package com.restcomm.identity.http;

import javax.servlet.http.HttpServletRequest;

import org.keycloak.KeycloakSecurityContext;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.AccessToken;

import com.restcomm.identity.AdminClient;
import com.restcomm.identity.configuration.Configuration;

public class Endpoint {

    protected Keycloak keycloak;
    protected Configuration configuration;
    protected AdminClient client;
    
    public Endpoint() {
        configuration = Configuration.get();
        client = new AdminClient(configuration);

    }

    protected void initKeycloakClient() {
        String authServer = configuration.getAuthServerUrlBase() + "/auth";
        this.keycloak = Keycloak.getInstance(authServer, "restcomm", configuration.getAdminUsername(), configuration.getAdminPassword(), Configuration.DEFAULT_ADMIN_CLIENT_ID);
        // Retrieve a token
        keycloak.tokenManager().getAccessToken();
    }
    
    protected AccessToken getRequesterToken(HttpServletRequest request) {
        KeycloakSecurityContext session = (KeycloakSecurityContext) request.getAttribute(KeycloakSecurityContext.class.getName());
        return session.getToken();
    }    
}
