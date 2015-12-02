package com.restcomm.identity.bootstrap;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.apache.log4j.Logger;

import com.restcomm.identity.configuration.Configuration;


public class RestcommIdentityInitializationServlet extends HttpServlet {
    static final Logger logger = Logger.getLogger(RestcommIdentityInitializationServlet.class.getName());

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config) ;
        Configuration configuration = Configuration.createOnce(config.getServletContext());
        logger.info("Restcomm Indentity proxy initialized. Authorization server: '" + configuration.getAuthServerUrlBase() +"'");
    }

}
