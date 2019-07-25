import javax.servlet.AsyncContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncHttpServlet extends HttpServlet
{
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
    {
        final AsyncContext context = request.startAsync();
        context.setTimeout(0);
        executorService.submit(() -> {
            try {
                System.out.println("Starting processing.");
                Thread.sleep(60_000); // long processing
                System.out.println("Processing done.");
                response.setStatus(200);
                response.addHeader("content-type", "text/plain");
                final ServletOutputStream outputStream = response.getOutputStream();
                outputStream.setWriteListener(new WriteListener()
                {
                    @Override
                    public void onWritePossible() throws IOException
                    {
                        outputStream.print("ok"); // short write to output
                        context.complete();
                    }

                    @Override
                    public void onError(final Throwable t)
                    {
                        System.err.println("Failed on write.");
                        context.complete();
                    }
                });
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

    }
}
