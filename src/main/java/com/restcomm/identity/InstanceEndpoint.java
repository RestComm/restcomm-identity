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

import org.apache.log4j.Logger;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import com.google.gson.Gson;
import com.restcomm.identity.AdminClient.AdminClientException;
import com.restcomm.identity.configuration.Configuration;
import com.restcomm.identity.model.CreateInstanceResponse;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Path("instances")
public class InstanceEndpoint extends Endpoint {
    static final Logger logger = Logger.getLogger(InstanceEndpoint.class.getName());

    private Gson gson;

    @Context
    HttpServletRequest request;

    public InstanceEndpoint() {
        gson = new Gson();
    }

    @POST
    @Produces("application/json")
    public Response createInstanceMethod( @FormParam(value = "prefix") String prefix, @FormParam(value = "secret") String clientSecret) throws Exception {
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
        // generate instance-id (instanceName)
        String instanceName = generateInstanceId();
        logger.info("Creating instance '" + instanceName + "' for user '" + registrarUsername + "'");

        // initialize roles to be assigned to registrar
        List<String> addedRoleNames = new ArrayList<String>();
        addedRoleNames.add("Developer");
        addedRoleNames.add("Admin");

        // get a token for accessing the admin REST api
        String adminToken = keycloak.tokenManager().getAccessTokenString();

        // create Restcomm application
        String clientId = Configuration.getRestcommRestClientName(instanceName);
        ClientRepresentation clientRepr = buildRestcommClientRepresentation(clientId, prefix, clientSecret);
        client.createClientRequest(clientRepr, adminToken);
        client.addRolesToClient(addedRoleNames, clientRepr.getId(), adminToken);
        if ( registrarUserId != null )
            client.addClientRolesToUser(clientRepr.getId(), addedRoleNames, registrarUserId, adminToken );

        // Create Restcomm UI application
        clientId = Configuration.getRestcommUiClientName(instanceName);
        clientRepr = buildRestcommUiClientRepresentation(clientId, prefix);
        client.createClientRequest(clientRepr, adminToken);
        client.addRolesToClient(addedRoleNames, clientRepr.getId(), adminToken);
        if ( registrarUserId != null )
            client.addClientRolesToUser(clientRepr.getId(), addedRoleNames, registrarUserId, adminToken );

        // Create RVD application
        clientId = Configuration.getRestcommRvdClientName(instanceName);
        clientRepr = buildRvdClientRepresentation(clientId, prefix, clientSecret);
        client.createClientRequest(clientRepr, adminToken);
        client.addRolesToClient(addedRoleNames, clientRepr.getId(), adminToken);
        if ( registrarUserId != null )
            client.addClientRolesToUser(clientRepr.getId(), addedRoleNames, registrarUserId, adminToken );

        // Create RVD-UI application
        clientId = Configuration.getRestcommRvdUiClientName(instanceName);
        clientRepr = buildRvdUiClientRepresentation(clientId, prefix);
        client.createClientRequest(clientRepr, adminToken);
        client.addRolesToClient(addedRoleNames, clientRepr.getId(), adminToken);
        if ( registrarUserId != null )
            client.addClientRolesToUser(clientRepr.getId(), addedRoleNames, registrarUserId, adminToken );

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

    protected String generateInstanceId() {
        return UUID.randomUUID().toString().split("-")[0];
    }

    /*
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
        String userId = client.extractUserIdFromResourceUrl(userResourceUrl);

        // assign credentials (password)
        CredentialRepresentation creds = buildCredentialsRepresentation(user);
        Response resetResponse = client.resetUserPassword(creds, adminToken, userId);

        // then grant this user access to restcomm instances
        client.addClientRolesToUser(Configuration.getRestcommRestClientName(instanceId), getDefaultDeveloperRoles(), userId, adminToken);
        client.addClientRolesToUser(Configuration.getRestcommUiClientName(instanceId), getDefaultDeveloperRoles(), userId, adminToken);
        client.addClientRolesToUser(Configuration.getRestcommRvdClientName(instanceId), getDefaultDeveloperRoles(), userId, adminToken);
        client.addClientRolesToUser(Configuration.getRestcommRvdUiClientName(instanceId), getDefaultDeveloperRoles(), userId, adminToken);

        return Response.ok().build();
    }
    */

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
        boolean ok = true;
        if ( ! client.addClientRolesToUser(Configuration.getRestcommRestClientName(instanceId), configuration.getDefaultDeveloperRoles(), userId, adminToken) )
            ok = false;
        if (! client.addClientRolesToUser(Configuration.getRestcommUiClientName(instanceId), configuration.getDefaultDeveloperRoles(), userId, adminToken) )
            ok = false;
        if ( ! client.addClientRolesToUser(Configuration.getRestcommRvdClientName(instanceId), configuration.getDefaultDeveloperRoles(), userId, adminToken) )
            ok = false;
        if ( ! client.addClientRolesToUser(Configuration.getRestcommRvdUiClientName(instanceId), configuration.getDefaultDeveloperRoles(), userId, adminToken) )
            ok = false;

        if ( !ok )
            return Response.status(Status.NOT_FOUND).build(); // best guess for error. It is pointless to try to return an actual error code in this complex fault tolerant operation

        return Response.ok().build();
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

    /*
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
    }*/
}
