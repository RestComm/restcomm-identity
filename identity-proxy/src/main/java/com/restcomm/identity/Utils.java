package com.restcomm.identity;

/**
 * Created by nando on 2/25/16.
 */
public class Utils {
    public static String removeUrlTrailingSlash(String url) {
        if ( url == null || "".equals(url))
            return url;
        if ( url.endsWith("/") )
            return url.substring(0, url.length()-1);
        return url.toString();
    }
}
