import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.NegotiatingServerConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class Main
{
    private final Server server = new Server();

    public static void main(String[] args) throws InterruptedException
    {
        final Main main = new Main();
        main.start();
        main.await();
    }

    public void start()
    {
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStorePath("secrets/keystore");
        sslContextFactory.setKeyStorePassword("secret");
        sslContextFactory.setTrustStorePath("secrets/truststore");
        sslContextFactory.setTrustStorePassword("secret");
        sslContextFactory.setIncludeProtocols("TLSv1.2");

        final HttpConfiguration httpConfiguration = new HttpConfiguration();
        final NegotiatingServerConnectionFactory alpn = new ALPNServerConnectionFactory("h2");
        alpn.setDefaultProtocol("http/1.1");
        final HTTP2ServerConnectionFactory http2 = new HTTP2ServerConnectionFactory(httpConfiguration);
        http2.setMaxConcurrentStreams(1024);

        final ServerConnector serverConnector = new ServerConnector(server, sslContextFactory, alpn, http2);
        serverConnector.setPort(8443);
        server.addConnector(serverConnector);

        final ServletHandler handler = new ServletHandler();
        handler.addServletWithMapping(AsyncHttpServlet.class, "/");
        server.setHandler(handler);

        try {
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public void await() throws InterruptedException
    {
        server.join();
    }
}
