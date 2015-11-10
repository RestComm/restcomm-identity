package com.restcomm.identity.http;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.http.HttpResponse;
import org.apache.log4j.Logger;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import com.restcomm.identity.AdminClient;
import com.restcomm.identity.configuration.Configuration;
import com.restcomm.identity.model.UserEntity;

@Path("users")
public class UserEndpoint extends Endpoint {
    static final Logger logger = Logger.getLogger(UserEndpoint.class.getName());

    public UserEndpoint() {
        configuration = Configuration.get();
        client = new AdminClient(configuration);
    }

    @POST
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
                client.addClientRolesToUser(Configuration.getRestcommRestClientName(instanceId), configuration.getDefaultDeveloperRoles(), userId, adminToken);
                client.addClientRolesToUser(Configuration.getRestcommUiClientName(instanceId), configuration.getDefaultDeveloperRoles(), userId, adminToken);
                client.addClientRolesToUser(Configuration.getRestcommRvdClientName(instanceId), configuration.getDefaultDeveloperRoles(), userId, adminToken);
                client.addClientRolesToUser(Configuration.getRestcommRvdUiClientName(instanceId), configuration.getDefaultDeveloperRoles(), userId, adminToken);

            }
        }

        return Response.ok().build();
    }

    @DELETE
    @Path("/{username}")
    public Response dropUser(@PathParam("username") String username) {
        initKeycloakClient();
        String adminToken = keycloak.tokenManager().getAccessTokenString();
        // First retrieve the user. We need his id, not usernanme
        UserRepresentation userRepr = client.getUserByUsername(username, adminToken);
        if (userRepr == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        return client.dropUserRequest(userRepr.getId(), adminToken);
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

    public static class RolesRepresentation extends ArrayList<RoleRepresentation> {
    }
    public static class UsersRepresentation extends ArrayList<UserRepresentation> {
    }

}
