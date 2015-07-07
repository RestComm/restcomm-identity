package com.restcomm.identity;

import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;
import org.keycloak.OAuth2Constants;
import org.keycloak.adapters.HttpClientBuilder;
import org.keycloak.constants.ServiceUrlConstants;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.util.JsonSerialization;
import org.keycloak.util.KeycloakUriBuilder;

import com.google.gson.Gson;
import com.restcomm.identity.model.CreateInstanceResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

@Path("instances")
public class InstanceEndpoint {

    static final Logger logger = Logger.getLogger(InstanceEndpoint.class.getName());

    private static String authServerPrefix = "https://identity.restcomm.com"; // port 8443 should be used for accessing server from itself. From outside use 443 instead (or blank)

    private AccessTokenResponse token;
    private Gson gson;

    public InstanceEndpoint() {
        gson = new Gson();
    }

    public static String getAuthServerPrefix() {
        return authServerPrefix;
    }

    @POST
    @Produces("application/json")
    public Response createInstanceMethod(@FormParam(value = "name") String instanceName, @FormParam(value = "prefix") String prefix) throws Exception {
        AccessTokenResponse token = getToken();
        createInstance(instanceName, prefix, token);

        CreateInstanceResponse responseModel = new CreateInstanceResponse();
        // TODO - normally, we should generate a random value and return it
        responseModel.setInstanceId(instanceName);

        return Response.ok(gson.toJson(responseModel), MediaType.APPLICATION_JSON).build();
    }

    protected void createInstance(String instanceName, String prefix, AccessTokenResponse token ) throws Exception {
        logger.info("Creating instance '" + instanceName + "'");

        createRvdClient(instanceName + "-restcomm-rvd", prefix);
        createRvdUiClient(instanceName + "-restcomm-rvd-ui", prefix);
        createRestcommClient(instanceName + "-restcomm-rest", prefix);
        createRestcommUiClient(instanceName + "-restcomm-ui", prefix);
    }

    protected ClientRepresentation createRvdClient(String name, String prefix) throws UnsupportedEncodingException, InstanceManagerException {
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

        makeRequest(client_model);
        return client_model;
    }

    protected ClientRepresentation createRvdUiClient(String name, String prefix) throws UnsupportedEncodingException, InstanceManagerException {
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

        List<String> redirectUris = new ArrayList<String>();
        redirectUris.add(prefix + "/restcomm-rvd/*");
        client_model.setRedirectUris(redirectUris);

        List<String> webOrigins = new ArrayList<String>();
        webOrigins.add(prefix);
        client_model.setWebOrigins(webOrigins);

        makeRequest(client_model);
        return client_model;
    }

    protected ClientRepresentation createRestcommClient(String name, String prefix) throws UnsupportedEncodingException, InstanceManagerException {
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

        List<String> redirectUris = new ArrayList<String>();
        redirectUris.add(prefix + "/*");
        client_model.setRedirectUris(redirectUris);

        List<String> webOrigins = new ArrayList<String>();
        webOrigins.add(prefix);
        client_model.setWebOrigins(webOrigins);

        makeRequest(client_model);
        return client_model;
    }

    protected ClientRepresentation createRestcommUiClient(String name, String prefix) throws UnsupportedEncodingException, InstanceManagerException {
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

        List<String> redirectUris = new ArrayList<String>();
        redirectUris.add(prefix + "/*");
        client_model.setRedirectUris(redirectUris);

        List<String> webOrigins = new ArrayList<String>();
        webOrigins.add(prefix);
        client_model.setWebOrigins(webOrigins);

        makeRequest(client_model);
        return client_model;
    }

    protected HttpResponse makeRequest(ClientRepresentation client_model) throws UnsupportedEncodingException, InstanceManagerException {
        /*SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build());
        CloseableHttpClient client = HttpClients.custom().setSSLSocketFactory(sslsf).build();
        */
        //CloseableHttpClient client = HttpClients.custom().createDefault();

        HttpClient client = new HttpClientBuilder().disableTrustManager().build();
        try {
            //
            HttpPost post = new HttpPost(getAuthServerPrefix() + "/auth/admin/realms/restcomm/clients");
            post.addHeader("Authorization", "Bearer " + token.getToken());
            post.addHeader("Content-Type","application/json");


            String json_user = gson.toJson(client_model);
            StringEntity stringBody = new StringEntity(json_user,"UTF-8");
            post.setEntity(stringBody);
            try {
                HttpResponse response = client.execute(post);
                if (response.getStatusLine().getStatusCode() >= 300) {
                    throw new InstanceManagerException();
                }
                return response;

            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        } finally {
            //client.close();
            client.getConnectionManager().shutdown();
        }

    }

    // Retrieves a token and stores it for future use (within this request).
    protected AccessTokenResponse getToken() throws Exception {
        if (token != null)
            return token;

        HttpClient client = new HttpClientBuilder().disableTrustManager().build();

        try {
            HttpPost post = new HttpPost(KeycloakUriBuilder.fromUri(getAuthServerPrefix() + "/auth")
                    .path(ServiceUrlConstants.TOKEN_PATH).build("restcomm"));
            List<NameValuePair> formparams = new ArrayList<NameValuePair>();
            formparams.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, "password"));
            formparams.add(new BasicNameValuePair("username", "otsakir"));
            formparams.add(new BasicNameValuePair("password", "password"));

            if (isPublic()) { // if client is public access type
                formparams.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ID, "realm-management"));
            } else {
                throw new UnsupportedOperationException();
                //String authorization = BasicAuthHelper.createHeader("customer-portal", "secret-secret-secret");
                //post.setHeader("Authorization", authorization);
            }
            UrlEncodedFormEntity form = new UrlEncodedFormEntity(formparams, "UTF-8");
            post.setEntity(form);

            HttpResponse response = client.execute(post);
            int status = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            if (status != 200) {
                throw new IOException("Bad status: " + status);
            }
            if (entity == null) {
                throw new IOException("No Entity");
            }
            InputStream is = entity.getContent();
            try {
                token = JsonSerialization.readValue(is, AccessTokenResponse.class);
                return token;
            } finally {
                try {
                    is.close();
                } catch (IOException ignored) {
                }
            }
        } finally {
            //client.close();
            client.getConnectionManager().shutdown();
        }
    }

    /*public static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }*/

    protected boolean isPublic() {
        return true;
    }
}
