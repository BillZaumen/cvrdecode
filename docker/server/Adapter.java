import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.util.zip.GZIPOutputStream;
import org.bzdev.net.*;
import org.bzdev.net.ServletAdapter.ServletException;

public class Adapter implements ServletAdapter {
    static final String errorResponse =
	"<!DOCTYPE HTML><HTML><HEAD>"
	+ "<STYLE TYPE=\"text/css\" MEDIA=\"all\">"
	+ "H1 {font-weight: bold; font-size: 18pt; font-family: sans-serif; "
	+ "font-variant: normal; font-style: normal; text-align: center; "
	+ "padding-top: 4ex; padding-bottom: 2ex; }"
	+ "BODY {background-color: rgb(10,10,25); color: rgb(255,255,255);}"
	+ "</STYLE>"
	+"</HEAD><BODY><H1>Illegal Input</H1></BODY></HTML>\r\n";

    static void sendErrorResponse(HttpServerResponse res) {
	try {
	    res.setHeader("Content-type",
			  "text/html; charset=UTF-8");
	    res.sendResponseHeaders(415, errorResponse.length());
	    OutputStreamWriter w = new OutputStreamWriter(res.getOutputStream(),
							  "UTF-8");
	    w.write(errorResponse);
	    w.flush();
	    w.close();
	} catch (Exception e) {}
    }
    
    
    public void doPost(HttpServerRequest req, HttpServerResponse res) {
	InputStream is = req.getDecodedInputStream();
	String ctype = req.getMediaType();
	if (ctype.equalsIgnoreCase("multipart/form-data")) {
	    String boundary = req.getFromHeader("Content-type", "boundary");
	    try {
		FormDataIterator it = new FormDataIterator(is, boundary);
		boolean done = false;
		if (it.hasNext()) {
		    is = it.next();
		    String mtype = it.getMediaType();
		    if (mtype.equalsIgnoreCase("application/zip")
			|| (mtype.equalsIgnoreCase("application/octet-stream")
			    && it.getFileName().toLowerCase()
			    .endsWith(".zip"))) {
			res.setHeader("Content-type",
				      "text/csv; charset=UTF-8");
			res.setHeader("Content-encoding", "gzip");
			res.sendResponseHeaders(200, 0);
			OutputStream os =
			    new GZIPOutputStream(res.getOutputStream());
			done = true;
			String cp =
			    "cvrdecode.jar:"
			    + "javase.jar:core.jar:libbzdev-base.jar";
			ProcessBuilder pb = new
			    ProcessBuilder("java", "-classpath", cp,
					   "CaVaxRecDecoder", "-z");
			Process p = pb.start();
			OutputStream pout = p.getOutputStream();
			InputStream pis = p.getInputStream();
			final InputStream iis = is;
			
			Thread sender = new Thread(() -> {
				try {
				    iis.transferTo(pout);
				    pout.flush();
				    pout.close();
				} catch (Exception e) {
				}
			});
			sender.start();
			pis.transferTo(os);
			int exitCode = p.waitFor();
			pis.close();
			pout.close();
			os.flush();
			os.close();
		    } else {
			// while (is.read() != -1);
			is.close();
			is = null;
		    }
		}
		while(it.hasNext()) {
		    is = it.next();
		    // while (is.read() != -1);
		    is.close();
		}
		if (!done) {
		    try {
			while (is.read() != -1);
			is.close();
		    } catch (Exception e2) {}
		    sendErrorResponse(res);
		}
	    } catch (Exception e) {
		try {
		    while (is.read() != -1);
		} catch(Exception e3) {}
		sendErrorResponse(res);
	    }
	} else {
	    if (is != null) {
		try {
		    while (is.read() != -1);
		    is.close();
		} catch (IOException eio) {
		}
	    }
	    sendErrorResponse(res);
	}
    }
}
