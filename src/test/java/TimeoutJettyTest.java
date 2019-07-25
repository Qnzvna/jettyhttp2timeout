import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.api.Stream;
import org.eclipse.jetty.http2.api.server.ServerSessionListener;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.frames.DataFrame;
import org.eclipse.jetty.http2.frames.HeadersFrame;
import org.eclipse.jetty.http2.frames.ResetFrame;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.FuturePromise;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class TimeoutJettyTest
{
    private HTTP2Client http2Client;
    private SslContextFactory sslContextFactory;

    @BeforeClass
    public static void setUpClass()
    {
        new Main().start();
    }

    @Before
    public void setUp() throws Exception
    {
        sslContextFactory = new SslContextFactory(true);
        sslContextFactory.setIncludeCipherSuites("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384");
        http2Client = new HTTP2Client();
        http2Client.addBean(sslContextFactory);
        final List<String> protocols = new ArrayList<>();
        protocols.add("http/1.1");
        protocols.add("h2");
        http2Client.setProtocols(protocols);
        http2Client.setIdleTimeout(120_000);
        http2Client.start();
    }

    @Test
    @Ignore
    public void ok() throws InterruptedException, ExecutionException, TimeoutException
    {
        final FuturePromise<Session> sessionPromise = new FuturePromise<>();
        http2Client.connect(sslContextFactory,
                new InetSocketAddress("localhost", 8443),
                new ServerSessionListener.Adapter(),
                sessionPromise);
        final Session session = sessionPromise.get(5, TimeUnit.SECONDS);

        final HttpFields requestFields = new HttpFields();
        MetaData.Request request = new MetaData.Request("GET",
                new HttpURI("https://localhost:8443/"),
                HttpVersion.HTTP_2,
                requestFields);
        HeadersFrame headersFrame = new HeadersFrame(request, null, false);
        final CountDownLatch latch = new CountDownLatch(1);
        Stream.Listener responseListener = new Stream.Listener.Adapter()
        {
            @Override
            public void onHeaders(Stream stream, HeadersFrame frame)
            {
                System.err.println(frame);
            }

            @Override
            public void onData(Stream stream, DataFrame frame, Callback callback)
            {
                System.out.println("Response:" + new String(frame.getData().array()));
                callback.succeeded();
                latch.countDown();
            }
        };

        FuturePromise<Stream> streamPromise = new FuturePromise<>();
        session.newStream(headersFrame, streamPromise, responseListener);
        latch.await();
    }

    @Test
    public void timeoutCheck() throws InterruptedException, TimeoutException, ExecutionException
    {
        final FuturePromise<Session> sessionPromise = new FuturePromise<>();
        http2Client.connect(sslContextFactory,
                new InetSocketAddress("localhost", 8443),
                new ServerSessionListener.Adapter(),
                sessionPromise);
        final Session session = sessionPromise.get(5, TimeUnit.SECONDS);
        final AtomicInteger integer = new AtomicInteger(0);
        while (integer.get() == 0) {
            final HttpFields requestFields = new HttpFields();
            final MetaData.Request request = new MetaData.Request("GET",
                    new HttpURI("https://localhost:8443/"),
                    HttpVersion.HTTP_2,
                    requestFields);
            final HeadersFrame headersFrame = new HeadersFrame(request, null, true);
            final Stream.Listener responseListener = new Stream.Listener.Adapter()
            {
                @Override
                public void onHeaders(Stream stream, HeadersFrame frame)
                {
                    System.err.println(frame);
                }

                @Override
                public void onData(Stream stream, DataFrame frame, Callback callback)
                {
                    System.out.println("Response:" + BufferUtil.toString(frame.getData()));
                    callback.succeeded();
                }

                @Override
                public void onReset(final Stream stream, final ResetFrame frame)
                {
                    System.out.println("Stream reset. Error:" + frame.getError());
                    integer.set(1);
                }
            };
            session.newStream(headersFrame, new FuturePromise<>(), responseListener);
            Thread.sleep(2_000);
        }
    }
}
