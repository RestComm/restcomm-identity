package com.restcomm.identity;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Path("instances")
public class InstanceEndpoint {

    static final Logger logger = Logger.getLogger(InstanceEndpoint.class.getName());

    @GET
    @Produces("application/json")
    public List<String> getInstance() {
        ArrayList<String> rtn = new ArrayList<String>();
        rtn.add("iphone");
        rtn.add("ipad");
        rtn.add("ipod");
        return rtn;
    }

    @POST
    @Produces("application/json")
    public Response createInstanceMethod(@FormParam(value = "name") String instanceName) throws Exception {
        AccessTokenResponse token = getToken();
        createInstance(instanceName, token);
        return Response.ok().build();
    }

    public void createInstance(String instanceName, AccessTokenResponse token ) throws Exception {
        logger.info("Creating instance '" + instanceName + "'");

        /*SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build());
        CloseableHttpClient client = HttpClients.custom().setSSLSocketFactory(sslsf).build();
        */
        //CloseableHttpClient client = HttpClients.custom().createDefault();

        String name = instanceName;
        HttpClient client = new HttpClientBuilder().disableTrustManager().build();
        try {
            //
            HttpPost post = new HttpPost("https://identity.restcomm.com/auth/admin/realms/restcomm/clients");
            post.addHeader("Authorization", "Bearer " + token.getToken());
            post.addHeader("Content-Type","application/json");

            ClientRepresentation client_model = new ClientRepresentation();
            client_model.setAdminUrl("https://192.168.1.39:8443/restcomm-rvd/services");
            client_model.setBaseUrl("https://192.168.1.39:8443/restcomm-rvd/services");
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

            Gson gson = new Gson();
            String json_user = gson.toJson(client_model);
            StringEntity stringBody = new StringEntity(json_user,"UTF-8");
            post.setEntity(stringBody);
            try {
                HttpResponse response = client.execute(post);
                if (response.getStatusLine().getStatusCode() >= 300) {
                    throw new InstanceManagerException();
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        } finally {
            //client.close();
            client.getConnectionManager().shutdown();
        }

    }

    AccessTokenResponse getToken() throws Exception {
        HttpClient client = new HttpClientBuilder().disableTrustManager().build();

        try {
            HttpPost post = new HttpPost(KeycloakUriBuilder.fromUri("https://identity.restcomm.com/auth")
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
                AccessTokenResponse tokenResponse = JsonSerialization.readValue(is, AccessTokenResponse.class);
                return tokenResponse;
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

    public static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    protected boolean isPublic() {
        return true;
    }
}
