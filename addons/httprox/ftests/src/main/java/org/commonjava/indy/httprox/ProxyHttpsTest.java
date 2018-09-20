/**
 * Copyright (C) 2011-2018 Red Hat, Inc. (https://github.com/Commonjava/indy)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.indy.httprox;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.commonjava.indy.client.core.helper.HttpResources;
import org.commonjava.indy.test.fixture.core.CoreServerFixture;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class ProxyHttpsTest
                extends AbstractHttproxFunctionalTest
{

    private static final String USER = "user";

    private static final String PASS = "password";

    private static final String content = "This is a test";

    String https_url =
                    "https://oss.sonatype.org/content/repositories/releases/org/commonjava/indy/indy-api/1.3.1/indy-api-1.3.1.pom";

    @Test
    public void run() throws Exception
    {
        String ret = null;
        ret = regression();
        assertEquals( content, ret );

        ret = get( https_url, true );
        assertTrue( ret.contains( "<artifactId>indy-api</artifactId>" ) );
    }

    // Regression test for HTTP url
    private String regression() throws Exception
    {
        final String path = "org/foo/bar/1.0/bar-1.0.nocache";
        final String testRepo = "test";
        String url = server.formatUrl( testRepo, path );
        server.expect( url, 200, content );
        return get( url, false );
    }

    private String get( String url, boolean withCACert ) throws Exception
    {
        CloseableHttpClient client;

        if ( withCACert )
        {
            File jks = new File( etcDir, "ssl/ca.jks" );
            KeyStore trustStore = getTrustStore( jks );
            SSLSocketFactory socketFactory = new SSLSocketFactory( trustStore );
            client = proxiedHttp( socketFactory );
        }
        else
        {
            client = proxiedHttp();
        }

        HttpGet get = new HttpGet( url );
        CloseableHttpResponse response = null;

        InputStream stream = null;
        try
        {
            response = client.execute( get, proxyContext( USER, PASS ) );
            stream = response.getEntity().getContent();
            final String resulting = IOUtils.toString( stream );

            assertThat( resulting, notNullValue() );
            System.out.println( "\n\n>>>>>>>\n\n" + resulting + "\n\n" );

            return resulting;
        }
        finally
        {
            IOUtils.closeQuietly( stream );
            HttpResources.cleanupResources( get, response, client );
        }
    }

    private KeyStore getTrustStore( File jks ) throws Exception
    {
        KeyStore trustStore = KeyStore.getInstance( KeyStore.getDefaultType() );
        try (FileInputStream instream = new FileInputStream( jks ))
        {
            trustStore.load( instream, "passwd".toCharArray() );
        }
        return trustStore;
    }

    @Override
    protected int getTestTimeoutMultiplier()
    {
        return 3;
    }

    @Override
    protected String getAdditionalHttproxConfig()
    {
        return "MITM.ca.key=${indy.home}/etc/indy/ssl/ca.der\n"
                        + "MITM.ca.cert=${indy.home}/etc/indy/ssl/ca.crt\n"
                        + "MITM.dn.template=CN=<host>, O=Test Org";
    }

    @Override
    protected void initTestData( CoreServerFixture fixture ) throws IOException
    {
        copyToConfigFile( "ssl/ca.der", "ssl/ca.der" );
        copyToConfigFile( "ssl/ca.crt", "ssl/ca.crt" );
        copyToConfigFile( "ssl/ca.jks", "ssl/ca.jks" );
    }
}
