package com.restcomm.identity.model;

public class RiConfigEntity {

    private String authServerUrlBase;
    private String adminUsername;
    private String adminPassword;

    public RiConfigEntity(String authServerUrlBase, String adminUsername, String adminPassword) {
        super();
        this.authServerUrlBase = authServerUrlBase;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    public String getAuthServerUrlBase() {
        return authServerUrlBase;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

}


