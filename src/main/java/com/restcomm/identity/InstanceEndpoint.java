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

import org.apache.http.HttpResponse;
import org.apache.log4j.Logger;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import com.google.gson.Gson;
import com.restcomm.identity.AdminClient.AdminClientException;
import com.restcomm.identity.configuration.Configuration;
import com.restcomm.identity.model.CreateInstanceResponse;
import com.restcomm.identity.model.UserEntity;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

@Path("instances")
public class InstanceEndpoint {
    static final Logger logger = Logger.getLogger(InstanceEndpoint.class.getName());

    static class RolesRepresentation extends ArrayList<RoleRepresentation> {
    }
    static class UsersRepresentation extends ArrayList<UserRepresentation> {
    }

    private Keycloak keycloak;
    private Gson gson;
    private Configuration configuration;
    private AdminClient client;

    @Context
    HttpServletRequest request;

    public InstanceEndpoint() {
        gson = new Gson();
        configuration = Configuration.get();
        client = new AdminClient(configuration);
    }

    private void initKeycloakClient() {
        String authServer = configuration.getAuthServerUrlBase() + "/auth";
        this.keycloak = Keycloak.getInstance(authServer, "restcomm", configuration.getAdminUsername(), configuration.getAdminPassword(), Configuration.DEFAULT_ADMIN_CLIENT_ID);
        // Retrieve a token
        AccessTokenResponse tokenResponse = keycloak.tokenManager().getAccessToken();
    }

    private AccessToken getRequesterToken(HttpServletRequest request) {
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
        AccessToken registrarToken = getRequesterToken(request);
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
        client.createClientRequest(clientRepr, adminToken);
        addRolesToClient(addedRoleNames, clientRepr.getId(), adminToken);
        if ( registrarUserId != null )
            addClientRolesToUser(clientRepr.getId(), addedRoleNames, registrarUserId, adminToken );

        // Create Restcomm UI application
        clientId = getRestcommUiClientName(instanceName);
        clientRepr = buildRestcommUiClientRepresentation(clientId, prefix);
        client.createClientRequest(clientRepr, adminToken);
        addRolesToClient(addedRoleNames, clientRepr.getId(), adminToken);
        if ( registrarUserId != null )
            addClientRolesToUser(clientRepr.getId(), addedRoleNames, registrarUserId, adminToken );

        // Create RVD application
        clientId = getRestcommRvdClientName(instanceName);
        clientRepr = buildRvdClientRepresentation(clientId, prefix, clientSecret);
        client.createClientRequest(clientRepr, adminToken);
        addRolesToClient(addedRoleNames, clientRepr.getId(), adminToken);
        if ( registrarUserId != null )
            addClientRolesToUser(clientRepr.getId(), addedRoleNames, registrarUserId, adminToken );

        // Create RVD-UI application
        clientId = getRestcommRvdUiClientName(instanceName);
        clientRepr = buildRvdUiClientRepresentation(clientId, prefix);
        client.createClientRequest(clientRepr, adminToken);
        addRolesToClient(addedRoleNames, clientRepr.getId(), adminToken);
        if ( registrarUserId != null )
            addClientRolesToUser(clientRepr.getId(), addedRoleNames, registrarUserId, adminToken );

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

        String[] clientNames = {
                instanceName + "-restcomm-rvd",
                instanceName + "-restcomm-rvd-ui",
                instanceName + "-restcomm-rest",
                instanceName + "-restcomm-ui"
        };
        for ( String clientName: clientNames) {
            client.dropClientRequest(clientName, adminToken);
        }

        return Response.status(Status.OK).build();
        //return dropInstance(instanceName);
    }

    @POST
    @Path("/{instance}/users")
    public Response createInstanceUser(UserEntity user, @PathParam("instance") String instanceId) {
        initKeycloakClient();
        String adminToken = keycloak.tokenManager().getAccessTokenString();

        UserRepresentation userRepr = buildUserRepresentation(user);
        // first created an isolated user entity with no access to restcomm instance applications
        HttpResponse createResponse = client.createUserRequest(userRepr, adminToken);
        int status = createResponse.getStatusLine().getStatusCode();
        if ( status != 201) {
            if (status == 409 )
                return Response.status(409).build();
            else
                return Response.status(400).build();
        }

        String userResourceUrl = createResponse.getFirstHeader("Location").getValue();
        String userId = extractUserIdFromResourceUrl(userResourceUrl);

        // assign credentials (password)
        CredentialRepresentation creds = buildCredentialsRepresentation(user);
        Response resetResponse = client.resetUserPassword(creds, adminToken, userId);

        // then grant this user access to restcomm instances
        addClientRolesToUser(getRestcommRestClientName(instanceId), getDefaultDeveloperRoles(), userId, adminToken);
        addClientRolesToUser(getRestcommUiClientName(instanceId), getDefaultDeveloperRoles(), userId, adminToken);
        addClientRolesToUser(getRestcommRvdClientName(instanceId), getDefaultDeveloperRoles(), userId, adminToken);
        addClientRolesToUser(getRestcommRvdUiClientName(instanceId), getDefaultDeveloperRoles(), userId, adminToken);

        return Response.ok().build();
    }

    @POST
    @Path("/{instanceId}/users/{username}/invite")
    public Response inviteUserToInstance(@PathParam("instanceId") String instanceId, @PathParam("username") String username) {
        initKeycloakClient();
        String adminToken = keycloak.tokenManager().getAccessTokenString();

        UserRepresentation userRepr = client.getUserByUsername(username, adminToken);
        if (userRepr == null)
            return Response.status(Status.NOT_FOUND).build();
        String userId = userRepr.getId();
        // grant access to instance applications
        addClientRolesToUser(getRestcommRestClientName(instanceId), getDefaultDeveloperRoles(), userId, adminToken);
        addClientRolesToUser(getRestcommUiClientName(instanceId), getDefaultDeveloperRoles(), userId, adminToken);
        addClientRolesToUser(getRestcommRvdClientName(instanceId), getDefaultDeveloperRoles(), userId, adminToken);
        addClientRolesToUser(getRestcommRvdUiClientName(instanceId), getDefaultDeveloperRoles(), userId, adminToken);

        return Response.ok().build();
    }

    // TODO - move this to a new UsersEndpoint
    @POST
    @Path("/users")
    public Response createUser(UserEntity user) {
        initKeycloakClient();
        String adminToken = keycloak.tokenManager().getAccessTokenString();

        UserRepresentation userRepr = buildUserRepresentation(user);
        // first created an isolated user entity with no access to restcomm instance applications
        HttpResponse response = client.createUserRequest(userRepr, adminToken);
        int status = response.getStatusLine().getStatusCode();
        if ( status != 201) {
            if (status == 409 )
                return Response.status(409).build();
            else
                return Response.status(400).build();
        }

        String userResourceUrl = response.getFirstHeader("Location").getValue();
        String userId = extractUserIdFromResourceUrl(userResourceUrl);

        // assign credentials (password)
        CredentialRepresentation creds = buildCredentialsRepresentation(user);
        client.resetUserPassword(creds, adminToken, userId);

        // then grant this user access to restcomm instances
        List<String> memberOf = user.getMemberOf();
        if ( memberOf != null ) {
            for ( String instanceId: memberOf ) {
                addClientRolesToUser(getRestcommRestClientName(instanceId), getDefaultDeveloperRoles(), userId, adminToken);
                addClientRolesToUser(getRestcommUiClientName(instanceId), getDefaultDeveloperRoles(), userId, adminToken);
                addClientRolesToUser(getRestcommRvdClientName(instanceId), getDefaultDeveloperRoles(), userId, adminToken);
                addClientRolesToUser(getRestcommRvdUiClientName(instanceId), getDefaultDeveloperRoles(), userId, adminToken);

            }
        }

        return Response.ok().build();
    }

    String extractUserIdFromResourceUrl(String location) {
        String userId = location.substring( location.lastIndexOf("/")+1 );
        return userId;
    }


    private CredentialRepresentation buildCredentialsRepresentation(UserEntity user) {
        // prepare credentials
        CredentialRepresentation credRepr = new CredentialRepresentation();
        credRepr.setType("password");
        credRepr.setValue(user.getPassword());
        credRepr.setTemporary(false); // NOT temporary - Hardcoded for now
        return credRepr;
    }

    private UserRepresentation buildUserRepresentation(UserEntity user) {

        // prepare user
        UserRepresentation userRepr = new UserRepresentation();
        userRepr.setUsername(user.getUsername());
        userRepr.setFirstName(user.getFirstname());
        userRepr.setLastName(user.getLastname());
        //userRepr.setCredentials(creds);
        userRepr.setEnabled(true);

        return userRepr;
    }

    private void addClientRolesToUser(String clientName, List<String> roles, String username, String token ) {
        for (String role: roles) {
            RoleRepresentation roleRepr = client.getClientRoleRequest(clientName, role, token);
            if (roleRepr != null)
                client.addUserClientRoleRequest(username, clientName, roleRepr, token);
        }
    }

    // TODO return the created role representation. It will allow not looking them up again again when granting registrar access.
    private void addRolesToClient(List<String> roleNames, String clientName, String token) {
        for (String roleName: roleNames) {
            RoleRepresentation role = new RoleRepresentation(roleName, roleName);
            logger.info("Creating client role '" + clientName + ":" + roleName + "'");
            client.createClientRoleRequest(role, clientName, token);
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

    protected List<String> getDefaultAdminRoles() {
        List<String> addedRoleNames = new ArrayList<String>();
        addedRoleNames.add("Developer");
        addedRoleNames.add("Admin");
        return addedRoleNames;
    }

    protected List<String> getDefaultDeveloperRoles() {
        List<String> addedRoleNames = new ArrayList<String>();
        addedRoleNames.add("Developer");
        return addedRoleNames;
    }
}
