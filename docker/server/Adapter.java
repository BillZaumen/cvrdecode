import java.io.ByteArrayOutputStream;
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
	+"</HEAD><BODY><H1>Illegal Input</H1>%s</BODY></HTML>\r\n";

    static void sendErrorResponse(HttpServerResponse res,
				  String msg)
    {
	try {
	    String response = String.format(errorResponse, msg);

	    res.setHeader("Content-type",
			  "text/html; charset=UTF-8");
	    res.sendResponseHeaders(415, response.length());
	    OutputStreamWriter w = new OutputStreamWriter(res.getOutputStream(),
							  "UTF-8");
	    w.write(response);
	    w.flush();
	    w.close();
	} catch (Exception e) {}
    }
    
    public void doPost(HttpServerRequest req, HttpServerResponse res)
	throws IOException
    {
	InputStream is = req.getDecodedInputStream();
	String ctype = req.getMediaType();
	if (ctype.equalsIgnoreCase("multipart/form-data")) {
	    String boundary = req.getFromHeader("Content-type", "boundary");
	    try {
		FormDataIterator it = new FormDataIterator(is, boundary);
		boolean done = false;
		String mtype = "missing";
		StringBuilder mtypes = new StringBuilder();
		int exitCode = 0;
		if (it.hasNext()) {
		    is = it.next();
		    mtype = it.getMediaType();
		    if (mtype == null) {
			mtype = "application/octet-stream";
		    } else {
			mtype = mtype.toLowerCase();
		    }
		    if (mtypes.length() > 0) mtypes.append(", ");
		    mtypes.append(mtype);
		    if (mtype.equals("application/zip")
			|| (mtype.equals("application/octet-stream")
			    && it.getFileName().toLowerCase()
			    .endsWith(".zip"))
			|| mtype.equals("application/x-zip")
			|| mtype.startsWith("application/x-zip-")) {
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
			final InputStream eis = p.getErrorStream();
			final ByteArrayOutputStream eos =
			    new ByteArrayOutputStream(2048);
			Thread sender = new Thread(() -> {
				try {
				    iis.transferTo(pout);
				    pout.flush();
				    pout.close();
				} catch (Exception e) {
				}
			});
			Thread eh = new Thread(()-> {
				try {
				    eis.transferTo(eos);
				    eis.close();
				    eos.flush();
				    eos.close();
				} catch (Exception e) {
				}
			});
			sender.start();
			eh.start();
			ByteArrayOutputStream baos =
			    new ByteArrayOutputStream(1 << 13);
			pis.transferTo(baos);
			// pis.transferTo(os);
			exitCode = p.waitFor();
			eh.join();
			pis.close();
			pout.close();
			if (exitCode != 0) {
			    throw new Exception("cvrdecode failed: exit code "
						+ exitCode);
			}
			byte[] ebytes = eos.toByteArray();
			if (ebytes.length > 0) {
			    // cvrdecode printed error messages
			    String errs = new String(ebytes, "UTF-8");
			    errs = errs.replaceAll("[\\n]", "<br>");
			    sendErrorResponse(res, errs);
			    
			} else {
			    res.setHeader("Content-type",
					  "text/csv; charset=UTF-8");
			    res.setHeader("Content-encoding", "gzip");
			    res.sendResponseHeaders(200, 0);
			    OutputStream os =
				new GZIPOutputStream(res.getOutputStream());
			    byte[] bytes = baos.toByteArray();
			    os.write(bytes);
			    os.flush();
			    os.close();
			}
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
		    String msg = "Input media types is/are " + mtypes;
		    sendErrorResponse(res, msg);
		}
	    } catch (Exception e) {
		try {
		    while (is.read() != -1);
		} catch(Exception e3) {}
		String msg = e.getClass().getName() +": "
		    + e.getMessage();
		sendErrorResponse(res, msg);
	    }
	} else {
	    if (is != null) {
		try {
		    while (is.read() != -1);
		    is.close();
		} catch (IOException eio) {
		}
	    }
	    sendErrorResponse(res,
			      "input not multipart/form-data: found "
			      + ctype);
	}
    }
}
