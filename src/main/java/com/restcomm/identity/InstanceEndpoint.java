package org.keycloak.example.oauth;


import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.ArrayList;
import java.util.List;

@Path("instances")
public class InstanceEndpoint {
    @GET
    @Produces("application/json")
    public List<String> getInstance() {
        ArrayList<String> rtn = new ArrayList<String>();
        rtn.add("iphone");
        rtn.add("ipad");
        rtn.add("ipod");
        return rtn;
    }
}
