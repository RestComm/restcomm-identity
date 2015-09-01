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
import com.restcomm.identity.model.CreateInstanceResponse;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

@Path("instances")
public class InstanceEndpoint {

    static class RolesRepresentation extends ArrayList<RoleRepresentation> {
    }
    static class AdminClientException extends Exception {

        public AdminClientException(String message) {
            super(message);
            // TODO Auto-generated constructor stub
        }

    }

    static final Logger logger = Logger.getLogger(InstanceEndpoint.class.getName());

    private static String authServerPrefix = "https://identity.restcomm.com:8443"; // port 8443 should be used for accessing server from inside. From outside use 443 instead (or blank)
    private static String ADMIN_USERNAME = "otsakir";
    private static String ADMIN_PASSWORD = "password";
    private static String ADMIN_CLIENT_ID = "restcomm-identity-rest";
    //private static String ADMIN_CLIENT_SECRET = "a735a223-2760-4248-bb34-744176b8b931"; // open the realm-management client and go to 'Credentials' to find it
    private static String RESTCOMM_REALM = "restcomm";

    private Keycloak keycloak;
    private AccessTokenResponse registrarToken;
    private Gson gson;

    @Context
    HttpServletRequest request;

    public InstanceEndpoint() {
        gson = new Gson();
    }

    public static String getAuthServerPrefix() {
        return authServerPrefix;
    }

    public static String getAdminUsername() {
        return ADMIN_USERNAME;
    }

    public static String getAdminPassword() {
        return ADMIN_PASSWORD;
    }

    public static String getAdminClientId() {
        return ADMIN_CLIENT_ID;
    }

    //public static String getAdminClientSecret() {
    //    return ADMIN_CLIENT_SECRET;
    //}

    public static String getRestcommRealm() {
        return RESTCOMM_REALM;
    }
/*
    @GET
    @Path("/test")
    public Response test() {
        String authServer = getAuthServerPrefix() + "/auth";
        Keycloak keycloak = Keycloak.getInstance(authServer, "restcomm", getAdminUsername(), getAdminPassword(), "restcomm-identity-rest" );

        AccessTokenResponse tokenResponse = keycloak.tokenManager().getAccessToken();
        ClientsResource clients = keycloak.realm("restcomm").clients();
        ClientResource client = clients.get("otsakir-39cd4940-restcomm-rvd");
        ClientRepresentation representation = client.toRepresentation();

        return Response.ok().build();
    }
*/
    private void initKeycloakClient() {
        String authServer = getAuthServerPrefix() + "/auth";
        this.keycloak = Keycloak.getInstance(authServer, "restcomm", getAdminUsername(), getAdminPassword(), getAdminClientId());
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
        AccessToken registrarToken = getRegistrarToken(request);
        if (registrarToken != null) {
            registrarUsername = getRegistrarToken(request).getPreferredUsername();
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
        String clientName = getRestcommRestClientName(instanceName);
        ClientRepresentation clientRepr = createRestcommClient(clientName, prefix, clientSecret);
        keycloak.realm(getRestcommRealm()).clients().create(clientRepr);
        addRolesToClient(addedRoleNames, clientName);
        if ( registrarUsername != null )
            addClientRolesToRegistarUser(clientName, addedRoleNames, registrarUsername, adminToken );

        // Create Restcomm UI application
        clientName = getRestcommUiClientName(instanceName);
        clientRepr = createRestcommUiClient(clientName, prefix, clientSecret);
        keycloak.realm(getRestcommRealm()).clients().create(clientRepr);
        addRolesToClient(addedRoleNames, clientName);
        if ( registrarUsername != null )
            addClientRolesToRegistarUser(clientName, addedRoleNames, registrarUsername, adminToken );

        // Create RVD application
        clientName = getRestcommRvdClientName(instanceName);
        clientRepr = createRvdClient(clientName, prefix, clientSecret);
        keycloak.realm(getRestcommRealm()).clients().create(clientRepr);
        addRolesToClient(addedRoleNames, clientName);
        if ( registrarUsername != null )
            addClientRolesToRegistarUser(clientName, addedRoleNames, registrarUsername, adminToken );

        // Create RVD-UI application
        clientName = getRestcommRvdUiClientName(instanceName);
        clientRepr = createRvdUiClient(clientName, prefix, clientSecret);
        keycloak.realm(getRestcommRealm()).clients().create(clientRepr);
        addRolesToClient(addedRoleNames, clientName);
        if ( registrarUsername != null )
            addClientRolesToRegistarUser(clientName, addedRoleNames, registrarUsername, adminToken );

        CreateInstanceResponse responseModel = new CreateInstanceResponse();
        // TODO - normally, we should generate a random value and return it
        responseModel.setInstanceId(instanceName);

        return Response.ok(gson.toJson(responseModel), MediaType.APPLICATION_JSON).build();
    }

    private void addClientRolesToRegistarUser(String clientName, List<String> roles, String username, String token ) {
        for (String role: roles) {
            RoleRepresentation roleRepr = getClientRoleRequest(clientName, role, token);
            if (roleRepr != null)
                addUserClientRoleRequest(username, clientName, roleRepr, token);
        }
    }

    // TODO return the created role representation. It will allow not looking them up again again when granting registrar access.
    private void addRolesToClient(List<String> roleNames, String clientName) {
        for (String roleName: roleNames) {
            RoleRepresentation role = new RoleRepresentation(roleName, roleName);
            logger.info("Creating client role '" + clientName + ":" + roleName + "'");
            keycloak.realm(getRestcommRealm()).clients().get(clientName).roles().create(role);
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

    @DELETE
    @Path("/{instanceName}")
    public Response dropInstanceMethod(@PathParam("instanceName") String instanceName) {
        if ( !validateInstanceName(instanceName) )
            return Response.status(Status.BAD_REQUEST).build();

        initKeycloakClient();
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
            logger.info("Dropping client '" + clientName + "'");
            keycloak.realm(getRestcommRealm()).clients().get(clientName).remove();
        }

        return Response.status(Status.OK).build();
        //return dropInstance(instanceName);
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
            HttpGet request = new HttpGet(getAuthServerPrefix() + "/auth/admin/realms/" + getRestcommRealm() + "/clients/"+clientName+"/roles");
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
        HttpClient client = new HttpClientBuilder().disableTrustManager().build();
        try {
            // retrieve the
            HttpPost postRequest = new HttpPost(getAuthServerPrefix() + "/auth/admin/realms/" + getRestcommRealm() + "/users/" + username + "/role-mappings/clients/" + clientName);
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
            logger.info("Granted client role '" + clientName + ":" + roleRepr.getName() + "' to " + username );
        } catch (IOException e1) {
            throw new RuntimeException("Error granting client role '" + clientName + ":" + roleRepr.getName() + "' to user " + username, e1);
        } catch (AdminClientException e) {
            logger.error("Error granting client role '" + clientName + ":" + roleRepr.getName() + "' to user " + username, e);
        } finally {
            //client.close();
            client.getConnectionManager().shutdown();
        }
    }

    protected ClientRepresentation createRvdClient(String name, String prefix, String clientSecret) throws UnsupportedEncodingException, InstanceManagerException {
        ClientRepresentation client_model = new ClientRepresentation();
        client_model.setAdminUrl(prefix + "/restcomm-rvd/services");
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
        client_model.setClientId(name);
        client_model.setSecret(clientSecret);

        //makeRequest(client_model);
        return client_model;
    }

    protected ClientRepresentation createRvdUiClient(String name, String prefix, String clientSecret) throws UnsupportedEncodingException, InstanceManagerException {
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
        client_model.setClientId(name);
        client_model.setSecret(clientSecret);


        List<String> redirectUris = new ArrayList<String>();
        redirectUris.add(prefix + "/restcomm-rvd/*");
        client_model.setRedirectUris(redirectUris);

        List<String> webOrigins = new ArrayList<String>();
        webOrigins.add(prefix);
        client_model.setWebOrigins(webOrigins);

        //makeRequest(client_model);
        return client_model;
    }

    protected ClientRepresentation createRestcommClient(String name, String prefix, String clientSecret) throws UnsupportedEncodingException, InstanceManagerException {
        ClientRepresentation client_model = new ClientRepresentation();
        client_model.setAdminUrl(prefix + "/restcomm/keycloak");
        client_model.setBaseUrl(prefix + "/restcomm/keycloak");
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

    protected ClientRepresentation createRestcommUiClient(String name, String prefix, String clientSecret) throws UnsupportedEncodingException, InstanceManagerException {
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
        client_model.setClientId(name);
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

}
