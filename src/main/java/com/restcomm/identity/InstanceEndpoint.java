package com.restcomm.identity;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.log4j.Logger;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.adapters.HttpClientBuilder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.util.JsonSerialization;
import org.keycloak.representations.idm.RoleRepresentation;

import com.google.gson.Gson;
import com.restcomm.identity.configuration.Configuration;
import com.restcomm.identity.model.CreateInstanceResponse;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

@Path("instances")
public class InstanceEndpoint {
    static final Logger logger = Logger.getLogger(InstanceEndpoint.class.getName());

    static class RolesRepresentation extends ArrayList<RoleRepresentation> {
    }
    static class AdminClientException extends Exception {

        public AdminClientException(String message) {
            super(message);
            // TODO Auto-generated constructor stub
        }

    }

    private Keycloak keycloak;
    private Gson gson;
    private Configuration configuration;

    @Context
    HttpServletRequest request;

    public InstanceEndpoint() {
        gson = new Gson();
        configuration = Configuration.get();
    }

    private void initKeycloakClient() {
        String authServer = configuration.getAuthServerUrlBase() + "/auth";
        this.keycloak = Keycloak.getInstance(authServer, "restcomm", configuration.getAdminUsername(), configuration.getAdminPassword(), Configuration.DEFAULT_ADMIN_CLIENT_ID);
        // Retrieve a token
        AccessTokenResponse tokenResponse = keycloak.tokenManager().getAccessToken();
    }

    private AccessToken getRegistrarToken(HttpServletRequest request) {
        KeycloakSecurityContext session = (KeycloakSecurityContext) request.getAttribute(KeycloakSecurityContext.class.getName());
        return session.getToken();
    }

    @POST
    @Produces("application/json")
    public Response createInstanceMethod(@FormParam(value = "name") String instanceName, @FormParam(value = "prefix") String prefix, @FormParam(value = "secret") String clientSecret) throws Exception {
        logger.info("Creating instance '" + instanceName + "'");
        initKeycloakClient();

        // get registrar username
        String registrarUsername = null;
        String registrarUserId = null;
        AccessToken registrarToken = getRegistrarToken(request);
        if (registrarToken != null) {
            registrarUsername = registrarToken.getPreferredUsername();
            registrarUserId = registrarToken.getSubject();
        } else {
            logger.error("No token found for registrar user. Won't properly initialize roles to the user");
        }

        // initialize roles to be assigned to registrar
        List<String> addedRoleNames = new ArrayList<String>();
        addedRoleNames.add("Developer");
        addedRoleNames.add("Admin");

        // get a token for accessing the admin REST api
        String adminToken = keycloak.tokenManager().getAccessTokenString();

        // create Restcomm application
        String clientId = getRestcommRestClientName(instanceName);
        ClientRepresentation clientRepr = buildRestcommClientRepresentation(clientId, prefix, clientSecret);
        createClientRequest(clientRepr, adminToken);
        addRolesToClient(addedRoleNames, clientRepr.getId(), adminToken);
        if ( registrarUserId != null )
            addClientRolesToRegistarUser(clientRepr.getId(), addedRoleNames, registrarUserId, adminToken );

        // Create Restcomm UI application
        clientId = getRestcommUiClientName(instanceName);
        clientRepr = buildRestcommUiClientRepresentation(clientId, prefix);
        createClientRequest(clientRepr, adminToken);
        addRolesToClient(addedRoleNames, clientRepr.getId(), adminToken);
        if ( registrarUserId != null )
            addClientRolesToRegistarUser(clientRepr.getId(), addedRoleNames, registrarUserId, adminToken );

        // Create RVD application
        clientId = getRestcommRvdClientName(instanceName);
        clientRepr = buildRvdClientRepresentation(clientId, prefix, clientSecret);
        createClientRequest(clientRepr, adminToken);
        addRolesToClient(addedRoleNames, clientRepr.getId(), adminToken);
        if ( registrarUserId != null )
            addClientRolesToRegistarUser(clientRepr.getId(), addedRoleNames, registrarUserId, adminToken );

        // Create RVD-UI application
        clientId = getRestcommRvdUiClientName(instanceName);
        clientRepr = buildRvdUiClientRepresentation(clientId, prefix);
        createClientRequest(clientRepr, adminToken);
        addRolesToClient(addedRoleNames, clientRepr.getId(), adminToken);
        if ( registrarUserId != null )
            addClientRolesToRegistarUser(clientRepr.getId(), addedRoleNames, registrarUserId, adminToken );

        CreateInstanceResponse responseModel = new CreateInstanceResponse();
        // TODO - normally, we should generate a random value and return it
        responseModel.setInstanceId(instanceName);

        return Response.ok(gson.toJson(responseModel), MediaType.APPLICATION_JSON).build();
    }

    @DELETE
    @Path("/{instanceName}")
    public Response dropInstanceMethod(@PathParam("instanceName") String instanceName) throws AdminClientException {
        if ( !validateInstanceName(instanceName) )
            return Response.status(Status.BAD_REQUEST).build();

        initKeycloakClient();
        String adminToken = keycloak.tokenManager().getAccessTokenString();

        logger.info("Dropping instance '" + instanceName + "'");

        // TODO remove these
        //AccessToken registrarToken = getRegistrarToken(request);
        //String adminToken = keycloak.tokenManager().getAccessTokenString();


        String[] clientNames = {
                instanceName + "-restcomm-rvd",
                instanceName + "-restcomm-rvd-ui",
                instanceName + "-restcomm-rest",
                instanceName + "-restcomm-ui"
        };
        for ( String clientName: clientNames) {
            dropClientRequest(clientName, adminToken);
        }

        return Response.status(Status.OK).build();
        //return dropInstance(instanceName);
    }

    private void addClientRolesToRegistarUser(String clientName, List<String> roles, String username, String token ) {
        for (String role: roles) {
            RoleRepresentation roleRepr = getClientRoleRequest(clientName, role, token);
            if (roleRepr != null)
                addUserClientRoleRequest(username, clientName, roleRepr, token);
        }
    }

    // TODO return the created role representation. It will allow not looking them up again again when granting registrar access.
    private void addRolesToClient(List<String> roleNames, String clientName, String token) {
        for (String roleName: roleNames) {
            RoleRepresentation role = new RoleRepresentation(roleName, roleName);
            logger.info("Creating client role '" + clientName + ":" + roleName + "'");
            createClientRoleRequest(role, clientName, token);
        }
    }

    private static String getRestcommRestClientName(String instanceName) {
        return instanceName + "-restcomm-rest";
    }

    private static String getRestcommUiClientName(String instanceName) {
        return instanceName + "-restcomm-ui";
    }

    private static String getRestcommRvdClientName(String instanceName) {
        return instanceName + "-restcomm-rvd";
    }

    private static String getRestcommRvdUiClientName(String instanceName) {
        return instanceName + "-restcomm-rvd-ui";
    }

    // make sure the name abides by the general instance naming convention. For now it acceptschecks all names
    // TODO add proper limitations
    protected boolean validateInstanceName(String instanceName) {
        if ( instanceName == null || instanceName.isEmpty() ) {
            return false;
        }
        return true;
    }

    // returns the RoleRepresentation requested or null if not found
    protected RoleRepresentation getClientRoleRequest(String clientName, String roleName, String token) {
        HttpClient client = new HttpClientBuilder().disableTrustManager().build();
        try {
            // retrieve the
            HttpGet request = new HttpGet(configuration.getAuthServerUrlBase() + "/auth/admin/realms/" + configuration.getRestcommRealm() + "/clients/"+clientName+"/roles");
            request.addHeader("Authorization", "Bearer " + token);
            HttpResponse response = client.execute(request);
            int status = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            if (status != 200 || entity == null ) {
                throw new AdminClientException ("Request failed with status " + response.getStatusLine().toString());
            }
            RolesRepresentation roles = JsonSerialization.readValue(entity.getContent(), RolesRepresentation.class);
            for (RoleRepresentation role: roles) {
                if (role.getName().equals(roleName))
                    return role;
            }
            throw new AdminClientException("Role '" + clientName + ":" + roleName + "' does not exist");
        } catch (IllegalStateException | IOException e) {
            throw new RuntimeException(e);
        } catch (AdminClientException e) {
            logger.error("Error retrieving roles for client '" + clientName + "'", e );
        } finally {
            //client.close();
            client.getConnectionManager().shutdown();
        }
        return null;
    }

    // adds user client roles using the REST api. I couldn't find a way to that with admin-rest-client.
    protected void addUserClientRoleRequest(String username, String clientName, RoleRepresentation roleRepr, String token) {
        logger.info("Granting client role '" + clientName + ":" + roleRepr.getName() + "' to user '" + username + "'" );
        HttpClient client = new HttpClientBuilder().disableTrustManager().build();
        try {
            // retrieve the
            HttpPost postRequest = new HttpPost(configuration.getAuthServerUrlBase() + "/auth/admin/realms/" + configuration.getRestcommRealm() + "/users/" + username + "/role-mappings/clients/" + clientName);
            postRequest.addHeader("Authorization", "Bearer " + token);
            postRequest.addHeader("Content-Type","application/json");

            RolesRepresentation rolesRepr = new RolesRepresentation();
            rolesRepr.add(roleRepr);
            String roleString = JsonSerialization.writeValueAsString(rolesRepr);
            StringEntity stringBody = new StringEntity(roleString,"UTF-8");
            postRequest.setEntity(stringBody);

            HttpResponse response = client.execute(postRequest);
            if (response.getStatusLine().getStatusCode() >= 300) {
                throw new AdminClientException("Request failed with status " + response.getStatusLine().toString());
            }
        } catch (IOException e1) {
            throw new RuntimeException("Error granting client role '" + clientName + ":" + roleRepr.getName() + "' to user " + username, e1);
        } catch (AdminClientException e) {
            logger.error("Error granting client role '" + clientName + ":" + roleRepr.getName() + "' to user " + username, e);
        } finally {
            //client.close();
            client.getConnectionManager().shutdown();
        }
    }

    protected void createClientRequest(ClientRepresentation client_repr, String token) throws AdminClientException {
        logger.info("Creating client '" + client_repr.getId() + "'");
        HttpClient client = new HttpClientBuilder().disableTrustManager().build();
        try {
            //
            HttpPost post = new HttpPost(configuration.getAuthServerUrlBase() + "/auth/admin/realms/" + configuration.getRestcommRealm() + "/clients");
            post.addHeader("Authorization", "Bearer " + token);
            post.addHeader("Content-Type","application/json");

            String data = JsonSerialization.writeValueAsString(client_repr);
            StringEntity stringBody = new StringEntity(data,"UTF-8");
            post.setEntity(stringBody);
            try {
                HttpResponse response = client.execute(post);
                if (response.getStatusLine().getStatusCode() >= 300 || response.getEntity() == null)
                    //throw new AdminClientException("Cannot create client '" + client_repr.getName() + "'. " + response.getStatusLine().getReasonPhrase() + " - " + response.getStatusLine().getStatusCode());
                    throw new AdminClientException("Cannot create client '" + client_repr.getName() + "'");
                return;
            } catch (IOException e) {
                logger.error(e);
                throw new RuntimeException("Error creating client '" + client_repr.getName() + "'", e);
            }

        } catch (IOException e1) {
            logger.error(e1);
            throw new RuntimeException("Error creating client '" + client_repr.getName() + "'", e1);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    protected void dropClientRequest(String clientId, String token) throws AdminClientException {
        logger.info("Dropping client '" + clientId + "'");
        HttpClient client = new HttpClientBuilder().disableTrustManager().build();
        try {
            //
            HttpDelete request = new HttpDelete(configuration.getAuthServerUrlBase() + "/auth/admin/realms/" + configuration.getRestcommRealm() + "/clients/" + clientId);
            request.addHeader("Authorization", "Bearer " + token);

            try {
                HttpResponse response = client.execute(request);
                if (response.getStatusLine().getStatusCode() != 404 &&  (response.getStatusLine().getStatusCode() >= 300) )
                    //throw new AdminClientException("Cannot create client '" + client_repr.getName() + "'. " + response.getStatusLine().getReasonPhrase() + " - " + response.getStatusLine().getStatusCode());
                    throw new AdminClientException("Error removing client '" + clientId + "'");
                else
                if (response.getStatusLine().getStatusCode() == 404)
                    logger.warn("Cannot drop client '" + clientId + "': Not found");
                return;
            } catch (IOException e) {
                logger.error(e);
                throw new RuntimeException("Error removing client '" + clientId + "'", e);
            }

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    protected Response createClientRoleRequest(RoleRepresentation role_repr, String clientName, String token) {
        HttpClient client = new HttpClientBuilder().disableTrustManager().build();
        try {
            //admin/realms/{realm}/clients/{id}/roles
            HttpPost post = new HttpPost(configuration.getAuthServerUrlBase() + "/auth/admin/realms/" + configuration.getRestcommRealm() + "/clients/" + clientName + "/roles");
            post.addHeader("Authorization", "Bearer " + token);
            post.addHeader("Content-Type","application/json");

            String data = JsonSerialization.writeValueAsString(role_repr);
            StringEntity stringBody = new StringEntity(data,"UTF-8");
            post.setEntity(stringBody);
            try {
                HttpResponse response = client.execute(post);
                return Response.status(response.getStatusLine().getStatusCode()).build();
            } catch (IOException e) {
                logger.error(e);
                throw new RuntimeException("Error creating client '" + role_repr.getName() + "'", e);
            }

        } catch (IOException e1) {
            logger.error(e1);
            throw new RuntimeException("Error creating client '" + role_repr.getName() + "'", e1);
        } finally {
            client.getConnectionManager().shutdown();
        }


    }

    protected ClientRepresentation buildRestcommClientRepresentation(String name, String prefix, String clientSecret) throws UnsupportedEncodingException, InstanceManagerException {
        ClientRepresentation client_model = new ClientRepresentation();
        //client_model.setAdminUrl(prefix + "/restcomm/keycloak");
        client_model.setBaseUrl(prefix + "/");
        client_model.setSurrogateAuthRequired(false);
        client_model.setEnabled(true);
        client_model.setNotBefore(0);
        client_model.setBearerOnly(false);
        client_model.setConsentRequired(false);
        client_model.setDirectGrantsOnly(false);
        client_model.setPublicClient(false);
        client_model.setFrontchannelLogout(false);
        client_model.setFullScopeAllowed(true);
        client_model.setNodeReRegistrationTimeout(-1);
        client_model.setClientId(name);
        client_model.setId(name);
        client_model.setSecret(clientSecret);

        List<String> redirectUris = new ArrayList<String>();
        redirectUris.add(prefix + "/*");
        client_model.setRedirectUris(redirectUris);

        List<String> webOrigins = new ArrayList<String>();
        webOrigins.add(prefix);
        client_model.setWebOrigins(webOrigins);

        //makeRequest(client_model);
        return client_model;
    }

    protected ClientRepresentation buildRestcommUiClientRepresentation(String name, String prefix) throws UnsupportedEncodingException, InstanceManagerException {
        ClientRepresentation client_model = new ClientRepresentation();
        client_model.setBaseUrl(prefix + "/index.html");
        client_model.setSurrogateAuthRequired(false);
        client_model.setEnabled(true);
        client_model.setNotBefore(0);
        client_model.setBearerOnly(false);
        client_model.setConsentRequired(false);
        client_model.setDirectGrantsOnly(false);
        client_model.setPublicClient(true);
        client_model.setFrontchannelLogout(false);
        client_model.setFullScopeAllowed(true);
        client_model.setNodeReRegistrationTimeout(-1);
        client_model.setId(name);
        client_model.setClientId(name);
        client_model.setName(name);
        //client_model.setSecret(clientSecret);

        List<String> redirectUris = new ArrayList<String>();
        redirectUris.add(prefix + "/*");
        client_model.setRedirectUris(redirectUris);

        List<String> webOrigins = new ArrayList<String>();
        webOrigins.add(prefix);
        client_model.setWebOrigins(webOrigins);

        //makeRequest(client_model);
        return client_model;
    }


    protected ClientRepresentation buildRvdClientRepresentation(String name, String prefix, String clientSecret) throws UnsupportedEncodingException, InstanceManagerException {
        ClientRepresentation client_model = new ClientRepresentation();
        //client_model.setAdminUrl(prefix + "/restcomm-rvd/services");
        client_model.setBaseUrl(prefix + "/restcomm-rvd/services");
        client_model.setSurrogateAuthRequired(false);
        client_model.setEnabled(true);
        client_model.setNotBefore(0);
        client_model.setBearerOnly(true);
        client_model.setConsentRequired(false);
        client_model.setDirectGrantsOnly(false);
        client_model.setPublicClient(false);
        client_model.setFrontchannelLogout(false);
        client_model.setFullScopeAllowed(true);
        client_model.setNodeReRegistrationTimeout(-1);
        client_model.setId(name);
        client_model.setClientId(name);
        client_model.setSecret(clientSecret);

        //makeRequest(client_model);
        return client_model;
    }

    protected ClientRepresentation buildRvdUiClientRepresentation(String name, String prefix) throws UnsupportedEncodingException, InstanceManagerException {
        ClientRepresentation client_model = new ClientRepresentation();
        client_model.setBaseUrl(prefix + "/restcomm-rvd/index.html");
        client_model.setSurrogateAuthRequired(false);
        client_model.setEnabled(true);
        client_model.setNotBefore(0);
        client_model.setBearerOnly(false);
        client_model.setConsentRequired(false);
        client_model.setDirectGrantsOnly(false);
        client_model.setPublicClient(true);
        client_model.setFrontchannelLogout(false);
        client_model.setFullScopeAllowed(true);
        client_model.setNodeReRegistrationTimeout(-1);
        client_model.setId(name);
        client_model.setClientId(name);
        //client_model.setSecret(clientSecret);


        List<String> redirectUris = new ArrayList<String>();
        redirectUris.add(prefix + "/restcomm-rvd/*");
        client_model.setRedirectUris(redirectUris);

        List<String> webOrigins = new ArrayList<String>();
        webOrigins.add(prefix);
        client_model.setWebOrigins(webOrigins);

        //makeRequest(client_model);
        return client_model;
    }

}
