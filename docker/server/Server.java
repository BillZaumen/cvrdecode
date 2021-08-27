import org.bzdev.ejws.*;
import org.bzdev.ejws.maps.*;
import org.bzdev.net.HttpMethod;
import java.net.InetAddress;

public class Server {
    public static void main(String argv[]) throws Exception {

        System.setProperty("java.awt.headless", "true");
	String s = System.getenv("PORT");
	int port = (s == null)? 8080: Integer.parseInt(s);
	s = System.getenv("BACKLOG");
	int backlog = (s == null)? 30: Integer.parseInt(s);
	s = System.getenv("NTHREADS");
	int nthreads = (s == null)? 50: Integer.parseInt(s);
	s = System.getenv("TRACE");
	boolean trace = (s == null)? false: Boolean.parseBoolean(s);
	s = System.getProperty("IPADDR");
	InetAddress addr = (s == null || s.equals("wildcard"))? null:
	    s.equalsIgnoreCase("loopback")? InetAddress.getLoopbackAddress():
	    InetAddress.getByName(s);

	EmbeddedWebServer ews = new
	    EmbeddedWebServer(addr,port, backlog, nthreads, null);

	ews.add("/", ResourceWebMap.class, "/", null, true, false, true);

	WebMap map = ews.getWebMap("/");
	map.addWelcome("webpage.html");

	ews.add("/servlet/adapter/", ServletWebMap.class,
		new ServletWebMap.Config(new Adapter(),
					 null, true,
					 HttpMethod.POST,
					 HttpMethod.HEAD,
					 HttpMethod.OPTIONS,
					 HttpMethod.TRACE),
		null, true, false, true);

	if (trace) {
	    ews.setTracer("/", System.out, true);
	    ews.setTracer("/servlet/adapter/", System.out, true);
	}

	ews.start();
    }
}
