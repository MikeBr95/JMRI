package jmri.jmrix.nce.configurexml;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * NceLightManagerXmlTest.java
 *
 * Description: tests for the NceLightManagerXml class
 *
 * @author   Paul Bender  Copyright (C) 2016
 */
public class NceLightManagerXmlTest {

    @Test
    public void testCtor(){
      Assert.assertNotNull("NceLightManagerXml constructor",new NceLightManagerXml());
    }

    // The minimal setup for log4J
    @Before
    public void setUp() {
        apps.tests.Log4JFixture.setUp();
    }

    @After
    public void tearDown() {
        apps.tests.Log4JFixture.tearDown();
    }

}

