package com.restcomm.identity;

import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.log4j.Logger;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import com.google.gson.Gson;
import com.restcomm.identity.model.CreateInstanceResponse;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

@Path("instances")
public class InstanceEndpoint {

    static final Logger logger = Logger.getLogger(InstanceEndpoint.class.getName());

    private static String authServerPrefix = "https://identity.restcomm.com:8443"; // port 8443 should be used for accessing server from inside. From outside use 443 instead (or blank)
    private static String ADMIN_USERNAME = "otsakir";
    private static String ADMIN_PASSWORD = "password";
    private static String ADMIN_CLIENT_ID = "restcomm-identity-rest";
    //private static String ADMIN_CLIENT_SECRET = "a735a223-2760-4248-bb34-744176b8b931"; // open the realm-management client and go to 'Credentials' to find it
    private static String RESTCOMM_REALM = "restcomm";

    private Keycloak keycloak;
    private AccessTokenResponse token;
    private Gson gson;

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

    @POST
    @Produces("application/json")
    public Response createInstanceMethod(@FormParam(value = "name") String instanceName, @FormParam(value = "prefix") String prefix, @FormParam(value = "secret") String clientSecret) throws Exception {
        logger.info("Creating instance '" + instanceName + "'");
        initKeycloakClient();

        // Create Restcomm application
        String clientName = instanceName + "-restcomm-rest";
        ClientRepresentation clientRepr = createRestcommClient(clientName, prefix, clientSecret);
        keycloak.realm(getRestcommRealm()).clients().create(clientRepr);
        RoleRepresentation role = new RoleRepresentation("Developer", "Instance Developer");
        keycloak.realm(getRestcommRealm()).clients().get(clientName).roles().create(role);

        // Create Restcomm UI application
        clientName = instanceName + "-restcomm-ui";
        clientRepr = createRestcommUiClient(clientName, prefix, clientSecret);
        keycloak.realm(getRestcommRealm()).clients().create(clientRepr);
        role = new RoleRepresentation("Developer", "Instance Developer");
        keycloak.realm(getRestcommRealm()).clients().get(clientName).roles().create(role);

        // Create RVD application
        clientName = instanceName + "-restcomm-rvd";
        clientRepr = createRvdClient(clientName, prefix, clientSecret);
        keycloak.realm(getRestcommRealm()).clients().create(clientRepr);
        role = new RoleRepresentation("Developer", "Instance Developer");
        keycloak.realm(getRestcommRealm()).clients().get(clientName).roles().create(role);

        // Create RVD-UI application
        clientName = instanceName + "-restcomm-rvd-ui";
        clientRepr = createRvdUiClient(clientName, prefix, clientSecret);
        keycloak.realm(getRestcommRealm()).clients().create(clientRepr);
        role = new RoleRepresentation("Developer", "Instance Developer");
        keycloak.realm(getRestcommRealm()).clients().get(clientName).roles().create(role);

        CreateInstanceResponse responseModel = new CreateInstanceResponse();
        // TODO - normally, we should generate a random value and return it
        responseModel.setInstanceId(instanceName);

        return Response.ok(gson.toJson(responseModel), MediaType.APPLICATION_JSON).build();
    }

    @DELETE
    @Path("/{instanceName}")
    public Response dropInstanceMethod(@PathParam("instanceName") String instanceName) {
        initKeycloakClient();
        logger.info("Dropping instance '" + instanceName + "'");

        if ( !validateInstanceName(instanceName) )
            return Response.status(Status.BAD_REQUEST).build();

        String[] clientNames = {
                instanceName + "-restcomm-rvd",
                instanceName + "-restcomm-rvd-ui",
                instanceName + "-restcomm-rest",
                instanceName + "-restcomm-ui"
        };
        for ( String clientName: clientNames) {
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
