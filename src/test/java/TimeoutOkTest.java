import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.http2.StreamResetException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TimeoutOkTest
{
    private OkHttpClient okHttpClient;

    @BeforeClass
    public static void setUpClass()
    {
        new Main().start();
    }

    @Before
    public void setUp()
    {
        final List<Protocol> protocols = new ArrayList<>();
        protocols.add(Protocol.HTTP_1_1);
        protocols.add(Protocol.HTTP_2);
        final Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequestsPerHost(1024);
        dispatcher.setMaxRequests(1024);
        okHttpClient = new OkHttpClient.Builder()
                .protocols(protocols)
                .dispatcher(dispatcher)
                .readTimeout(120, TimeUnit.SECONDS)
                .sslSocketFactory(getSslContext().getSocketFactory(), getTrustManager())
                .hostnameVerifier((hostname, session) -> true)
                .build();
    }

    private X509TrustManager getTrustManager()
    {
        return new X509TrustManager()
        {
            @Override
            public void checkClientTrusted(final X509Certificate[] chain, final String authType) throws CertificateException
            {

            }

            @Override
            public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException
            {

            }

            @Override
            public X509Certificate[] getAcceptedIssuers()
            {
                return new X509Certificate[0];
            }
        };
    }

    private SSLContext getSslContext()
    {
        try {
            final File file = new File("secrets/truststore");
            final KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(new FileInputStream(file), "secret".toCharArray());
            SSLContext sslContext = SSLContext.getInstance("SSL");
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, "keystore_pass".toCharArray());
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), new SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @Ignore
    public void ok() throws IOException
    {
        final Request request = new Request.Builder()
                .url("https://localhost:8443/")
                .build();
        final Response response = okHttpClient.newCall(request).execute();
        System.out.println(response.body().string());
    }

    @Test
    public void timeoutCheck() throws InterruptedException
    {
        final Request request = new Request.Builder()
                .url("https://localhost:8443/")
                .build();

        final AtomicInteger integer = new AtomicInteger(0);
        while (integer.get() == 0) {
            System.out.println("Sending request.");
            okHttpClient.newCall(request).enqueue(new Callback()
            {
                @Override
                public void onFailure(final Call call, final IOException e)
                {
                }

                @Override
                public void onResponse(final Call call, final Response response) throws IOException
                {
                    try {
                        System.out.println("Response:" + response.body().string());
                    } catch (StreamResetException reset) {
                        System.out.println("Stream reset exception caught.");
                        reset.printStackTrace();
                        integer.set(1);
                    }
                }
            });
            Thread.sleep(2_000);
        }
    }
}
