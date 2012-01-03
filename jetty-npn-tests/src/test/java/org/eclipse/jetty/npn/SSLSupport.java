package org.eclipse.jetty.npn;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public class SSLSupport
{
    private SSLSupport()
    {
    }

    public static SSLContext newSSLContext() throws Exception
    {
        KeyStore keyStore = getKeyStore("keystore", "storepwd");
        KeyManager[] keyManagers = getKeyManagers(keyStore, "keypwd");

        KeyStore trustStore = getKeyStore("truststore", "storepwd");
        TrustManager[] trustManagers = getTrustManagers(trustStore);

        SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
        SSLContext context = SSLContext.getInstance("TLSv1");
        context.init(keyManagers, trustManagers, secureRandom);
        return context;
    }

    private static KeyStore getKeyStore(String keyStoreResource, String keyStorePassword) throws Exception
    {
        if (keyStoreResource == null)
            return null;
        InputStream keyStoreStream = SSLSupport.class.getClassLoader().getResourceAsStream(keyStoreResource);
        if (keyStoreStream == null)
        {
            File keyStoreFile = new File(keyStoreResource);
            if (keyStoreFile.exists() && keyStoreFile.canRead())
                keyStoreStream = new FileInputStream(keyStoreFile);
        }
        if (keyStoreStream == null)
            return null;
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(keyStoreStream, keyStorePassword == null ? null : keyStorePassword.toCharArray());
        keyStoreStream.close();
        return keyStore;
    }

    private static KeyManager[] getKeyManagers(KeyStore keyStore, String password) throws Exception
    {
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
        keyManagerFactory.init(keyStore, password == null ? null : password.toCharArray());
        return keyManagerFactory.getKeyManagers();
    }

    private static TrustManager[] getTrustManagers(KeyStore trustStore) throws Exception
    {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
        trustManagerFactory.init(trustStore);
        return trustManagerFactory.getTrustManagers();
    }

}
