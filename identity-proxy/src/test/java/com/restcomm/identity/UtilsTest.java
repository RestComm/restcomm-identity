package com.restcomm.identity;

import org.junit.Test;
import org.junit.Assert;

/**
 * Created by nando on 2/25/16.
 */
public class UtilsTest {
    @Test
    public void removeUrlTrailingSlashTest() {
        Assert.assertEquals( Utils.removeUrlTrailingSlash("http://www.google.com/"), "http://www.google.com");
        Assert.assertEquals( Utils.removeUrlTrailingSlash("http://www.google.com"), "http://www.google.com");
        Assert.assertEquals( Utils.removeUrlTrailingSlash(null), null);
        Assert.assertEquals( Utils.removeUrlTrailingSlash(""), "");
    }
}
