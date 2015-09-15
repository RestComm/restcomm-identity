package com.restcomm.identity.model;

import java.util.List;

public class UserEntity {

    String username;
    String email;
    String firstname;
    String lastname;
    String password;
    List<String> memberOf; // instanceIds of restcomm instances User is a member of

    public UserEntity() {
        super();
    }

    public UserEntity(String username, String email, String firstname, String lastname, String password, List<String> memberOf) {
        super();
        this.username = username;
        this.email = email;
        this.firstname = firstname;
        this.lastname = lastname;
        this.password = password;
        this.memberOf = memberOf;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstname() {
        return firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public String getPassword() {
        return password;
    }

    public List<String> getMemberOf() {
        return memberOf;
    }



}
