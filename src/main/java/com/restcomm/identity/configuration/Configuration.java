package com.restcomm.identity.configuration;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletContext;

import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.restcomm.identity.http.utils.UriUtils;
import com.restcomm.identity.model.RiConfigEntity;

public class Configuration {
    static final Logger logger = Logger.getLogger(Configuration.class.getName());

    private static String RELATIVE_CONFIG_PATH = "/WEB-INF/restcomm-identity.json";
    private static String DEFAULT_AUTH_SERVER_URL_BASE = "https://identity.restcomm.com:8443";  // port 8443 should be used for accessing server from inside. From outside use 443 instead (or blank)
    private static String DEFAULT_ADMIN_USERNAME = "riAdmin";
    private static String DEFAULT_ADMIN_PASSWORD = "password";
    public static String DEFAULT_ADMIN_CLIENT_ID = "restcomm-identity-rest";
    public static String DEFAULT_RESTCOMM_REALM = "restcomm";

    private static Configuration cached;

    private RiConfigEntity source;
    private URI authServerUrlBaseFromContainer;


    private Configuration(ServletContext servletContext) {
        String configPath = servletContext.getRealPath(RELATIVE_CONFIG_PATH);

        Gson gson = new Gson();
        FileReader reader;
        try {
            reader = new FileReader(configPath);
        } catch (FileNotFoundException e) {
            logger.error("Error reading configuration from '" + configPath + "'. Will fallback to defaults.", e);
            source = getDefaultConfig();
            return;
        }
        source = gson.fromJson(reader, RiConfigEntity.class);
        return;
    }

    public static Configuration get() {
        if (cached == null)
            throw new IllegalStateException("Configuration object has not been created");
        return cached;
    }

    public static Configuration createOnce(ServletContext servletContext) {
        synchronized (Configuration.class) {
            if (cached == null)
                cached = new Configuration(servletContext);
        }
        return cached;
    }

    private RiConfigEntity getDefaultConfig() {

        RiConfigEntity config = new RiConfigEntity(DEFAULT_AUTH_SERVER_URL_BASE, DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_PASSWORD);
        return config;
    }

    public String getAuthServerUrlBase() {
        if ( source.getAuthServerUrlBase() != null )
            return source.getAuthServerUrlBase();

        if (authServerUrlBaseFromContainer == null) {
            UriUtils uriUtils = new UriUtils();
            try {
                URI uri = new URI("/");
                authServerUrlBaseFromContainer = uriUtils.resolve(uri);
            } catch (URISyntaxException e) {
                throw new IllegalStateException("Error building auth server url", e);
            }
        }
        return authServerUrlBaseFromContainer.toString();
    }

    public String getAdminUsername() {
        return source.getAdminUsername();
    }

    public String getAdminPassword() {
        return source.getAdminPassword();
    }

    public String getRestcommRealm() {
        return DEFAULT_RESTCOMM_REALM;
    }

    public static String getRestcommRestClientName(String instanceName) {
        return instanceName + "-restcomm-rest";
    }

    public static String getRestcommUiClientName(String instanceName) {
        return instanceName + "-restcomm-ui";
    }

    public static String getRestcommRvdClientName(String instanceName) {
        return instanceName + "-restcomm-rvd";
    }

    public static String getRestcommRvdUiClientName(String instanceName) {
        return instanceName + "-restcomm-rvd-ui";
    }

    public List<String> getDefaultDeveloperRoles() {
        List<String> addedRoleNames = new ArrayList<String>();
        addedRoleNames.add("Developer");
        return addedRoleNames;
    }

}
