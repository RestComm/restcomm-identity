package com.restcomm.identity;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.ws.rs.core.Response;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.log4j.Logger;
import org.keycloak.adapters.HttpClientBuilder;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.util.JsonSerialization;

import com.restcomm.identity.configuration.Configuration;
import com.restcomm.identity.http.UserEndpoint.RolesRepresentation;
import com.restcomm.identity.http.UserEndpoint.UsersRepresentation;

public class AdminClient {
    static final Logger logger = Logger.getLogger(AdminClient.class.getName());

    Configuration configuration;

    public AdminClient(Configuration configuration) {
        this.configuration = configuration;
    }

 // returns the RoleRepresentation requested or null if not found
    public RoleRepresentation getClientRoleRequest(String clientName, String roleName, String token) {
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

    public UserRepresentation getUserByUsername(String username, String token) {
        HttpClient client = new HttpClientBuilder().disableTrustManager().build();
        try {
            // retrieve the
            URI uri = new URIBuilder(configuration.getAuthServerUrlBase() + "/auth/admin/realms/" + configuration.getRestcommRealm() + "/users").addParameter("username", username).build();
            HttpGet request = new HttpGet(uri);
            request.addHeader("Authorization", "Bearer " + token);
            HttpResponse response = client.execute(request);
            int status = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            if (status == 200) {
                UsersRepresentation users = JsonSerialization.readValue(entity.getContent(), UsersRepresentation.class);
                for (UserRepresentation user: users)
                    return user; // return first user found
                return null; // no users found
            } else {
                logger.error("Error searching for user '" + username + "' - " + response.getStatusLine().toString());
                return null;
            }
        } catch (IllegalStateException | IOException e) {
            throw new RuntimeException(e);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Error searching for user '" + username + "'" );
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    // adds user client roles using the REST api.
    public void addUserClientRoleRequest(String username, String clientName, RoleRepresentation roleRepr, String token) {
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

    public void createClientRequest(ClientRepresentation client_repr, String token) throws AdminClientException {
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

    public void dropClientRequest(String clientId, String token) throws AdminClientException {
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

    public Response createClientRoleRequest(RoleRepresentation role_repr, String clientName, String token) {
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

    public HttpResponse createUserRequest(UserRepresentation userRepr, String token) {
       HttpClient client = new HttpClientBuilder().disableTrustManager().build();
        try {
            HttpPost post = new HttpPost(configuration.getAuthServerUrlBase() + "/auth/admin/realms/" + configuration.getRestcommRealm() + "/users");
            post.addHeader("Authorization", "Bearer " + token);
            post.addHeader("Content-Type","application/json");

            String data = JsonSerialization.writeValueAsString(userRepr);
            StringEntity stringBody = new StringEntity(data,"UTF-8");
            post.setEntity(stringBody);
            try {
                HttpResponse response = client.execute(post);
                return response;
            } catch (IOException e) {
                logger.error(e);
                throw new RuntimeException("Error creating user '" + userRepr.getUsername() + "'", e);
            }

        } catch (IOException e1) {
            logger.error(e1);
            throw new RuntimeException("Error creating user '" + userRepr.getUsername() + "'", e1);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    public Response dropUserRequest( String userId, String token) {
        HttpClient client = new HttpClientBuilder().disableTrustManager().build();
         try {
             URI uri = new URIBuilder(configuration.getAuthServerUrlBase() + "/auth/admin/realms/" + configuration.getRestcommRealm() + "/users/" + userId).build();
             HttpDelete request = new HttpDelete(uri);
             request.addHeader("Authorization", "Bearer " + token);
             HttpResponse response = client.execute(request);
             int status = response.getStatusLine().getStatusCode();
             if (status >= 300) {
                 logger.warn("Cannot drop user '" + userId + "': " + response.getStatusLine());
             }
             return Response.status(response.getStatusLine().getStatusCode()).build();
         } catch (IOException | URISyntaxException e1) {
             logger.error(e1);
             throw new RuntimeException("Error dropping user '" + userId + "'", e1);
         } finally {
             client.getConnectionManager().shutdown();
         }
     }

    public Response resetUserPassword(CredentialRepresentation credRepr, String token, String userId) {
        HttpClient client = new HttpClientBuilder().disableTrustManager().build();
         try {
             HttpPut put = new HttpPut(configuration.getAuthServerUrlBase() + "/auth/admin/realms/" + configuration.getRestcommRealm() + "/users/" + userId + "/reset-password");
             put.addHeader("Authorization", "Bearer " + token);
             put.addHeader("Content-Type","application/json");

             String data = JsonSerialization.writeValueAsString(credRepr);
             StringEntity stringBody = new StringEntity(data,"UTF-8");
             put.setEntity(stringBody);
             try {
                 HttpResponse response = client.execute(put);
                 int status = response.getStatusLine().getStatusCode();
                 if (status >=300)
                     logger.error("Cannot set password for user '" + userId + "': " + response.getStatusLine());
                 return Response.status(status).build();
             } catch (IOException e) {
                 logger.error(e);
                 throw new RuntimeException("Error reseting password for user '" + userId + "'", e);
             }

         } catch (IOException e1) {
             logger.error(e1);
             throw new RuntimeException("Error reseting password for user '" + userId + "'", e1);
         } finally {
             client.getConnectionManager().shutdown();
         }
     }

    // returns true if every single operation was successfull or false otherwise
    public boolean addClientRolesToUser(String clientName, List<String> roles, String username, String token ) {
        boolean ok = true;
        for (String role: roles) {
            RoleRepresentation roleRepr = getClientRoleRequest(clientName, role, token);
            if (roleRepr != null)
                addUserClientRoleRequest(username, clientName, roleRepr, token);
            else
                ok = false;
        }
        return ok;
    }

    // TODO return the created role representation. It will allow not looking them up again again when granting registrar access.
    public void addRolesToClient(List<String> roleNames, String clientName, String token) {
        for (String roleName: roleNames) {
            RoleRepresentation role = new RoleRepresentation(roleName, roleName);
            logger.info("Creating client role '" + clientName + ":" + roleName + "'");
            createClientRoleRequest(role, clientName, token);
        }
    }

    public static class AdminClientException extends Exception {

        public AdminClientException(String message) {
            super(message);
            // TODO Auto-generated constructor stub
        }

    }

}
