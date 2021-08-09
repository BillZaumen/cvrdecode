import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.io.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.math.BigInteger;
import java.net.URL;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.ECPoint;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.zip.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileView;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.bzdev.io.CSVWriter;
import org.bzdev.io.AppendableWriter;
import org.bzdev.swing.SimpleConsole;
import org.bzdev.swing.InputTablePane;
import org.bzdev.util.JSOps;
import org.bzdev.util.JSArray;
import org.bzdev.util.JSObject;
import org.bzdev.util.JSUtilities;

/**
 * Class implementating a Java program to read and verify a
 * California Digital COVID-19 Vaccine Record.  The record
 * is typically provided as a QR-Code surrounded with some
 * logos and a summary of its contents. A QR-code scanner
 * can convert it to its purely digital form - a URI whose
 * scheme is "shc". Textually it consists of the string
 * "shc:/" followed by a large number of pairs of digits,
 * with a total length of roughly 1700 characters.  By
 * contrast, a PNG image of a screenshot of the vaccination
 * record is about 176000 bytes in size, with the QR code
 * along requiring roughly 155000 bytes at that resolution.
 * <P>
 * To compile/run,  libbzdev-base.jar and the two zxing jar files
 * core.js and javase.js (poorly named by their Debian packages
 * as they are both placed in /usr/share/java with no indication that
 * they have anything to do with zxing, Google's open-source bar-code
 * class library) must be on the class path.  These libraries provide
 * the following:
 * <UL>
 *   <LI><STRONG>libbzdev-base.jar</STRONG>. The org.bzdev classes for
 *       creating a CSV file and for processing JSON.
 *   <LI><STRONG>core.jar</STRONG> and <STRONG>javase.jar</STRONG>. The
 *       classes used to read QR bar codes.
 * </UL>
 * <P>
 * The command-line options are
 * <UL>
 *   <LI><STRONG>-u</STRONG>Print the URI provided by the QR codes,
 *       one per line.
 *   <LI><STRONG>-v</STRONG>Print a full description of the contents of
 *       a QR code, excluding the header, and indicate if the signature
 *       was valid or not.
 * </UL>
 * These two options are mutually exclusive.  If neither is present, the
 * output will be a summary of each QR code printed with a heading in
 * CSV format, so it can be easily imported into a spread sheet or database.
 * If a file name argument is '-', an image file should be read from
 * standard input.
 * If there are no arguments, the input is expected to be a file with one
 * URI per line.  Lines starting with "#' and blank lines are ignored.
 * <P>
 * The California digital vaccine record is based on the Smart Health
 * Card standard.  Unfortunately, this standard, at least what one will
 * find with an initial Google search, is somewhat lacking in clarity.
 * The QR code itself encodes a URI whose scheme is "shc". In the URI,
 * this is followed by a colon, a "/", and a long series of pairs of digits.
 * Each pair represents a number in the range [0, 99]. To turn this encoding
 * int a series of ASCII characters, take the number represented by each pair
 * and add 45 to it to obtain the character code of an ascii character.
 * The characters that are produced will be those in a URL-safe base-64
 * encoding (with some base-64 decoders, one  must explicitly request url-safe
 * base 64 decoding). All the characters except for two periods (".") are
 * allowed characters for this encoding.  The input must be split at the
 * periods. This provides three components: a header, a record, and a
 * signature. The signature, however, is not DER encoded, and Java's
 * signing code expects DER encoded signature.
 * <P>
 * When base-64 decoded, the first component contains a JSON
 * object with fields that specify the compression algorithm for the
 * second component and type of signature, with the signature itself
 * provided by the last component.  When the second component is base-64
 * decoded and then uncompressed, the result is a JSON object containing
 * the vaccination record.  To verify the signature, you have to use the
 * procedure specified in RFC 7515. What is signed is the concatenation
 * of the url-safe base-64 encoded header, a period, and
 * the url-safe base-64 encoded compressed record. The URL
 * <A HREF="https://myvaccinerecord.cdph.ca.gov/creds/.well-known/jwks.json">
 * https://myvaccinerecord.cdph.ca.gov/creds/.well-known/jwks.json</A>
 * contains a description of the public key needed to verify the signature
 * (at the time this documentation was written, there was only one key,
 * but additional keys could be added later). Also, they do not explicitly
 * indicate what vaccines are used. Instead, a vaccine record consists of
 * a "system" field that indicates a name space and a code that indicates
 * a vaccine listed in that namespace. The codes seem to be non-negative
 * integers.  The web site
 * <A HREF="https://www.cdc.gov/vaccines/programs/iis/COVID-19-related-codes.html>
 * https://www.cdc.gov/vaccines/programs/iis/COVID-19-related-codes.html</A>
 * has the vaccine codes (it lists CVX codes).
 * <P>
 * Because the COVID-19 pandemic should end once enough of the population
 * has enough sense to get vaccinated, the implementation skips some steps
 * that would be appropriate for an application whose useful life is
 * longer.  It uses a built-in key for speed, and  if the keyid of a key
 * is missing, it goes to the California Department of  Public Health link
 * mentioned above to update and extend the list of known keys. Nothing
 * downloaded is cached. Also, because this is California-specific, no
 * effort was made at internationalization: people in California who
 * can run a command-line program are typically fluent in English in
 * addition to whatever other languages they know, the result of a need
 * for companies to hire the brightest and most highly educated people they
 * can find. The extra effort needed for localizing the code would
 * unfortunately take too long given only a single individual developing
 * this software.
 * <P>
 * The method getPublicKey looks up key parameters from a key id and
 * then issues the incantation that creates the public key. The method
 * mkder takes a signature and DER encodes it.  Both have been tested
 * with a single case due to large sample of SHC QRCodes not being
 * available.
 */
public class CaVaxRecDecoder {

    static PrintWriter err =
	new PrintWriter(new OutputStreamWriter(System.err));


    // information stored at
    // https://myvaccinerecord.cdph.ca.gov/creds/.well-known/jwks.json
    static class KeyInfo {
	String kty;
	String alg;
	String crv;
	String x;
	String y;
	public KeyInfo(String kty, String alg, String crv, String x, String y) {
	    this.kty = kty;
	    this.alg = alg;
	    this.crv = crv;
	    this.x = x;
	    this.y = y;
	}
    }
    static Map<String, KeyInfo> keymap = new HashMap<>();
    static boolean needLoading = true;

    static {
	    // At the time this was written, the California web site contained
	    // a single key.  These keys should not change very often, but
	    // it would be worth checking periodically and updating this
	    // section of the code.
	keymap.put("7JvktUpf1_9NPwdM-70FJT3YdyTiSe2IvmVxxgDSRb0",
		   new KeyInfo("EC", "ES256", "P-256",
			       "3dQz5ZlbazChP3U7bdqShfF0fvSXLXD9WMa1kqqH6i4",
			       "FV4AsWjc7ZmfhSiHsw2gjnDMKNLwNqi2jMLmJpiKWtE"));
    }

    static void loadKeys() {
	// load the keys from the CA DPH web site.  This will be called
	// if a key does not match a built-in key and if this method
	// has not been called previously.
	try {
	    URL url = new URL
		("https://myvaccinerecord.cdph.ca.gov/"
		 + "creds/.well-known/jwks.json");
	    InputStream is = url.openStream();
	    Object obj = JSUtilities.JSON.parse(is, "UTF-8");
	    if (obj instanceof JSObject) {
		JSObject object = (JSObject) obj;
		obj = object.get("keys");
		if (obj instanceof JSArray) {
		    JSArray array = (JSArray) obj;
		    for (Object o: array) {
			if (o instanceof JSObject) {
			    object = (JSObject) o;
			    String kid = object.get("kid", String.class);
			    String kty = object.get("kty", String.class);
			    String alg = object.get("alg", String.class);
			    String crv = object.get("crv", String.class);
			    String x = object.get("x", String.class);
			    String y = object.get("y", String.class);
			    keymap.put(kid,
				       new KeyInfo(kty, alg, crv, x, y));
			}
		    }
		}
	    }
	    needLoading = false;
	} catch (Exception e) {
	    err.println("cvrdecode: could not load key(s): "
			       + e.getMessage());
	}
    }

    static HashMap<String,Integer> vmap = new HashMap<>();
    static {
	// List of currently used vaccines and the number of doses
	// required for each. This table applies to
	// the system http://hl7.org/fhir/sid/cvx
	// the CVX codes were taken from
	// https://www.cdc.gov/vaccines/programs/iis/COVID-19-related-codes.html
	vmap.put("207", 2);	// Moderna
	vmap.put("208", 2);	// Pfizer
	vmap.put("210", 2);	// AstraZeneca
	vmap.put("211", 2);	// Novavax
	vmap.put("212", 1);	// Jansen (J&J)
    }

    static int neededDoses(String system, String code) {
	// Currently we recognize only one "system".  The code
	// will have to be modified if that changes at some point.
	if (!system.equals("http://hl7.org/fhir/sid/cvx")) {
	    return  -1;
	}
	Integer ival = vmap.get(code);
	if (ival == null) return -1;
	return ival;
    }

    static ECPublicKey getPublicKey(String kid)
	throws NoSuchAlgorithmException, InvalidParameterSpecException,
	       InvalidKeySpecException, NoSuchProviderException,
	       IllegalArgumentException
    {
	KeyInfo info = keymap.get(kid);
	if (info == null && needLoading) {
	    loadKeys();
	    info = keymap.get(kid);
	}
	if (info == null) throw new IllegalArgumentException("unknown key ID");
	if (!info.kty.equals("EC")) {
	    throw new IllegalStateException("Cannot Handle " + info.kty);
	}
	if (!info.alg.equals("ES256")) {
	    throw new IllegalStateException("Cannot Handle " + info.alg);
	}
	if (!info.crv.equals("P-256")) {
	    throw new IllegalStateException("Cannot Handle " + info.crv);
	}

	Base64.Decoder decoder = Base64.getUrlDecoder();
	byte[] xbytes = decoder.decode(info.x);
	byte[] ybytes = decoder.decode(info.y);
	BigInteger x = new BigInteger(1, xbytes);
	BigInteger y = new BigInteger(1, ybytes);
	ECPoint ecpoint = new ECPoint(x, y);
	AlgorithmParameters parameters =
	    AlgorithmParameters.getInstance("EC", "SunEC");
	parameters.init(new ECGenParameterSpec("secp256r1"));
	ECParameterSpec ecParameters =
	    parameters.getParameterSpec(ECParameterSpec.class);
	ECPublicKeySpec pubSpec = new ECPublicKeySpec(ecpoint, ecParameters);
	KeyFactory kf = KeyFactory.getInstance("EC");
	return (ECPublicKey)kf.generatePublic(pubSpec);	
    }

    static boolean verify(ECPublicKey key, byte[] data, byte[] signature)
	throws SignatureException, NoSuchAlgorithmException,
	       NoSuchProviderException, InvalidKeyException
    {
	Signature sig = Signature.getInstance("SHA256withECDSA", "SunEC");
	sig.initVerify(key);
	sig.update(data, 0, data.length);
	return sig.verify(signature);
    }

    static byte[] mkder(byte[] sig) {
	// The signature we get from the record is not DER encoded,
	// so we have to fix it up.  It consists of two 256 bit
	// numbers (unsigned), and we have to add a 'zero' byte if
	// the first byte is negative to prevent sign-extension from
	// turning the number into a negative one.
	int lenR = 0x20;
	int lenS = 0x20;
	if (sig[0] < 0) {
	    lenR++;
	}
	if (sig[32] < 0) {
	    lenS++;
	}
	byte[] result = new byte[6 + lenR + lenS];
	int i = 0;
	result[i++] = 0x30;
	result[i++] = (byte)(0x04 + lenR + lenS);
	result[i++] = 0x02;
	result[i++] = (byte)lenR;
	if (lenR > 0x20) {
	    result[i++] = 0;
	}
	System.arraycopy(sig, 0, result, i, 0x20);
	i += 0x20;
	result[i++] = 0x02;
	result[i++] = (byte)lenS;
	if (lenS > 0x20) {
	    result[i++] = 0x00;
	}
	System.arraycopy(sig, 0x20, result, i, 0x20);
	i += 0x20;
	if (i != result.length) {
	    throw new RuntimeException("DER encoding failed");
	}
	return result;
    }

    private static void printInfo(JSOps info, int depth) {
	// Recursive function to walk through a tree and pretty print it.
	if (info instanceof JSObject) {
	    for (Map.Entry<String,Object> entry: ((JSObject) info).entrySet()) {
		for (int i = 0; i < depth; i++) {
		    System.out.print(" ");
		}
		System.out.print(entry.getKey() + ":");
		Object value = entry.getValue();
		if (value instanceof JSOps) {
		    System.out.println();
		    printInfo((JSOps) value, depth + 2);
		} else {
		    System.out.println(" " + value);
		}
	    }
	} else if (info instanceof JSArray) {
	    for (Object object: (JSArray)info) {
		    for (int i = 0; i < depth-1; i++) {
			System.out.print(" ");
		    }
		    System.out.print("-");
		if (object instanceof JSArray) {
		    System.out.println();
		    printInfo((JSOps) object, depth + 2);
		} else if (object instanceof JSObject) {
		    System.out.println();
		    printInfo((JSOps)object, depth + 2);
		} else {
		    System.out.print(" ");
		    System.out.println(object);
		}
	    }
	} else {
	    for (int i = 0; i < depth; i++) {
		System.out.print(" ");
	    }
	    System.out.println(info);
	}
    }

    private static void printInfo(String contents) throws IOException {
	Object object = JSUtilities.JSON.parse(contents);
	if (object instanceof JSOps) {
	    printInfo((JSOps) object, 0);
	} else {
	    System.out.println(object);
	}
    }


    static String familyName = null;
    static String givenNames = null;
    static String birthDate = null;
    static int dosesNeeded = -1;
    static int code = -1;
    static int oindex = 0;
    static ArrayList<LocalDate> occurrence = new ArrayList<>();;
    static boolean valid = false;
    static boolean full = false;
    static LocalDate latest = null;

    private static void processInfo(JSOps info)  throws IOException {
	// traverse the JSON data structures and store the
	// fields we want.
	if (info instanceof JSObject) {
	    JSObject object = (JSObject) info;
	    if (object.containsKey("vc")) {
		processInfo((JSOps)object.get("vc"));
	    } else if (object.containsKey("credentialSubject")) {
		processInfo((JSOps)object.get("credentialSubject"));
	    } else if (object.containsKey("fhirBundle")) {
		processInfo((JSOps)object.get("fhirBundle"));
	    } else if (object.containsKey("resource")) {
		processInfo((JSOps)object.get("resource"));
	    } else if (object.containsKey("resourceType")) {
		String rtype = object.get("resourceType", String.class);
		if (rtype.equals("Bundle")) {
		    processInfo((JSOps)object.get("entry"));
		} else if (rtype.equals("Patient")) {
		    JSArray array = (JSArray) object.get("name");
		    JSObject obj = (JSObject) array.get(0);
		    familyName = obj.get("family", String.class);
		    JSArray givens = (JSArray) obj.get("given");
		    givenNames = "";
		    for (Object sobj: givens) {
			givenNames = givenNames + " " + sobj;
		    }
		    givenNames = givenNames.trim();
		    birthDate = object.get("birthDate", String.class);
		} else if (rtype.equals("Immunization")) {
		    if (object.containsKey("vaccineCode")) {
			JSObject vcobj = (JSObject)object.get("vaccineCode");
			JSObject vobj = (JSObject)
			    (((JSArray)vcobj.get("coding")).get(0));
			dosesNeeded = neededDoses(vobj.get("system",
							   String.class),
						  vobj.get("code",
							   String.class));
			occurrence.add(LocalDate
				       .parse(object.get("occurrenceDateTime",
							 String.class)));
		    }
		}
	    }
	} else if (info instanceof JSArray) {
	    for (Object object: (JSArray)info) {
		if (object instanceof JSArray) {
		    processInfo((JSOps) object);
		} else if (object instanceof JSObject) {
		    processInfo((JSOps)object);
		} 
	    }
	} else {
	    throw new IOException("cannot recognize format");
	}
    }

    private static void processInfo(String contents) throws IOException {
	// need to initialize so we don't use old data if something is
	// missing.
	familyName = null;
	givenNames = null;
	birthDate = null;
	dosesNeeded = -1;
	code = -1;
	oindex = 0;
	occurrence.clear();
	valid = false;
	full = false;
	Object object = JSUtilities.JSON.parse(contents);
	if (object instanceof JSOps) {
	    processInfo((JSOps) object);
	} else {
	    throw new IOException("cannot recognize format");
	}
    }

    /*
    // The program that processes images containing QR codes.
    private static final String ZBARIMG =
	System.getProperty("zbarimg.path", "/usr/bin/zbarimg");
    */

    static Object monitor = null; // Set & used by the GUI
    static File target = null;
    static File output = null;
    static Vector<Vector<Object>> tableRows = null;
    static JButton showTableButton = null;

    static final Color PALE_YELLOW = new Color(255, 255, 200);



    private static void startGUI() throws Exception {
	monitor = new Object();
	SwingUtilities.invokeLater(() -> {
		java.util.List<Image> iconList = new LinkedList<Image>();
		try {
		    iconList.add((new
				  ImageIcon((CaVaxRecDecoder.class
					     .getResource("cvrdecode16.png")
					     ))).getImage());
		    iconList.add((new
				  ImageIcon((CaVaxRecDecoder.class
					     .getResource("cvrdecode24.png")
					     ))).getImage());
		    iconList.add((new
				  ImageIcon((CaVaxRecDecoder.class
					     .getResource("cvrdecode32.png")
					     ))).getImage());
		    iconList.add((new
				  ImageIcon((CaVaxRecDecoder.class
					     .getResource("cvrdecode48.png")
					     ))).getImage());
		    iconList.add((new
				  ImageIcon((CaVaxRecDecoder.class
					     .getResource("cvrdecode64.png")
					     ))).getImage());
		    iconList.add((new
				  ImageIcon((CaVaxRecDecoder.class
					     .getResource("cvrdecode96.png")
					     ))).getImage());
		    iconList.add((new
				  ImageIcon((CaVaxRecDecoder.class
					     .getResource("cvrdecode128.png")
					     ))).getImage());
		    iconList.add((new
				  ImageIcon((CaVaxRecDecoder.class
					     .getResource("cvrdecode256.png")
					     ))).getImage());
		} catch (Exception e) {
		    System.err.println("initialization failed - "
				       + "missing icon for iconList");
		}

		SimpleConsole console = SimpleConsole
		    .newFramedInstance(800, 600, "cvrdecode: Console", false);
		err = new PrintWriter(new AppendableWriter(console));

		InputTablePane.ColSpec[] spec = {
		    new InputTablePane.ColSpec("Family Name",
					       "mmmmmmmmmmmm",
					       String.class, null, null),
		    new InputTablePane.ColSpec("Given Names",
					       "mmmmmmmmmmmm",
					       String.class, null, null),
		    new InputTablePane.ColSpec("Birth Date",
					       "5555-55-55",
					       String.class, null, null),
		    new InputTablePane.ColSpec("Last Vax",
					       "5555-55-55",
					       String.class, null, null),
		    new InputTablePane.ColSpec("Fully Vaxxed",
					       "false",
					       String.class, null, null),
		    new InputTablePane.ColSpec("Valid Sig.",
					       "false",
					       String.class, null, null),
		    new InputTablePane.ColSpec("Source",
					       "mmmmmmmmmmmmmmmm",
					       String.class, null, null),
		};

		JFrame frame =
		    new JFrame("cvrdecode: CA Vaccine Record Decoder");
		frame.setIconImages(iconList);
	    
		JPanel panel = new JPanel(new GridLayout(3, 2));
		JButton runButton = new JButton("Run");
		JButton setDirButton = new JButton("Set Input Directory");
		JButton setOutputButton = new JButton("Set Output File");
		JButton showConsoleButton = new JButton("Show Console");
		runButton.setEnabled(false);
		runButton.addActionListener((ae) -> {
			synchronized(monitor) {
			    try {
				monitor.notifyAll();
			    } catch (Exception e) {}
			}
			setDirButton.setEnabled(false);
			setOutputButton.setEnabled(false);
			showConsoleButton.setEnabled(true);
			runButton.setEnabled(false);
		    });

		FileFilter filter1 = new FileFilter() {
			public boolean accept(File f) {
			    if (f.isDirectory()) {
				return f.canRead();
			    } else {
				String name = f.getName();
				return (name.endsWith(".zip")
					|| name.endsWith(".ZIP"))
				    && f.canRead();
			    }
			}
			public String getDescription() {
			    return "Directory or ZIP file";
			}
		    };
		String cdir = System.getProperty("user.dir");

		final JFileChooser fc1 = new JFileChooser(cdir);
		fc1.addChoosableFileFilter(filter1);
		fc1.setAcceptAllFileFilterUsed(false);
		fc1.setMultiSelectionEnabled(false);
		fc1.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		fc1.setDialogTitle("cvrdecode: Set Directory");
		fc1.setApproveButtonText("Set Input Directory");
		FileNameExtensionFilter filter2 =
		    new FileNameExtensionFilter("csv", "CSV");

		final JFileChooser fc2 = new JFileChooser(cdir);
		fc2.setFileFilter(filter2);
		fc2.setDialogTitle("cvrdecode: Set Output File");
		fc2.setApproveButtonText("Set Output File");
		fc2.setAcceptAllFileFilterUsed(false);
		fc2.setMultiSelectionEnabled(false);
		
		setDirButton.addActionListener((ae) -> {
			File lastTarget = target;
			if (fc1.showOpenDialog(panel) ==
			    JFileChooser.APPROVE_OPTION) {
			    target = fc1.getSelectedFile();
			    if (target == null) {
				JOptionPane
				    .showMessageDialog(panel,
						       "No directory selected",
						       "Error",
						       JOptionPane
						       .ERROR_MESSAGE);
				target = lastTarget;
				return;
			    }
			    if (output == null) {
				// set only if we haven't already selected
				// something.
				fc2.setCurrentDirectory(target.getParentFile());
			    }
			    if (target != null) {
				setDirButton.setOpaque(true);
				setDirButton.setBackground(PALE_YELLOW);
			    } else
			    if (target != null && output != null) {
				runButton.setEnabled(true);
			    }
			}
		    });
		setOutputButton.addActionListener((ae) -> {
			File lastOutput = output;
			if (fc2.showOpenDialog(panel) ==
			    JFileChooser.APPROVE_OPTION) {
			    output = fc2.getSelectedFile();
			    if (output == null) {
				output = lastOutput;
				JOptionPane
				    .showMessageDialog(panel,
						       "No file selected",
						       "Error",
						       JOptionPane
						       .ERROR_MESSAGE);
				return;
			    }
			    String oname = output.getName();
			    if (oname == null) {
				output = lastOutput;
				JOptionPane
				    .showMessageDialog(panel,
						       "No file selected",
						       "Error",
						       JOptionPane
						       .ERROR_MESSAGE);
				return;
			    }
			    int ind = oname.lastIndexOf(".");
			    if (ind == -1) {
				output = new File(output.getParentFile(),
						  oname + ".csv");
			    } else {
				String suffix = oname.substring(ind+1);
				if (!(suffix.equals("csv")
				      || suffix.equals("CSV"))) {
				    output = new File(output.getParentFile(),
						      oname + ".csv");
				}
			    }
			    if (output != null) {
				setOutputButton.setOpaque(true);
				setOutputButton.setBackground(PALE_YELLOW);
			    }
			    if (target != null && output != null) {
				runButton.setEnabled(true);
			    }
			}
		    });
		showConsoleButton.setEnabled(false);
		showConsoleButton.addActionListener((ae) -> {
			console.getFrame().setVisible(true);
		    });
		showTableButton = new JButton("Show Table");
		showTableButton.setEnabled(false);
		showTableButton.addActionListener((ae) -> {
			if (tableRows == null) return;
			InputTablePane.showDialog(panel,
						  "crvdecode: Output Table",
						  spec,
						  tableRows,
						  false, false, false);
		    });
		JButton exitButton = new JButton("Exit");
		exitButton.addActionListener((ae) -> {
			System.exit(0);
		    });
		panel.add(setDirButton);
		panel.add(setOutputButton);
		panel.add(runButton);
		panel.add(showTableButton);
		panel.add(showConsoleButton);
		panel.add(exitButton);
					  
		frame.setLayout(new BorderLayout());
		frame.add(panel, BorderLayout.CENTER);
		frame.pack();
		frame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
			    System.exit(0);
			}
		    });
		frame.setVisible(true);
	    });
	// Need to wait until the target directory and output file are set.
	synchronized (monitor) {
		while (target == null && output == null) {
		    try {
			monitor.wait();
		    } catch (Exception e) {}
		}
	}
	// We now have the directory to read.
    }


    /**
     * Main entry point.
     */
    public static void main(String argv[]) throws Exception {
	boolean guimode = false;
	ZipInputStream zis = null;

	if (argv.length == 0) {
	    startGUI();
	    String[] suffixes = ImageIO.getReaderFileSuffixes();
	    Set<String> suffixSet = new HashSet<>();
	    suffixSet.add("shc");
	    for (String suffix: suffixes) {
		suffixSet.add(suffix);
	    }
	    ArrayList<String> list = new ArrayList<>(256);
	    if (target.isDirectory()) {
		for (File f: target.listFiles()) {
		    String fname = f.getName();
		    int ind = fname.lastIndexOf(".");
		    if (ind == -1) continue;
		    String suffix = fname.substring(ind+1);
		    if (suffixSet.contains(suffix)) {
			list.add(f.getCanonicalPath());
		    }
		}
		argv = list.toArray(new String[list.size()]);
	    } else {
		try {
		    zis = new ZipInputStream(new FileInputStream(target));
		} catch (Exception ezip) {
		    err.println("cvrdecode:input not a zip file?");
		    err.flush();
		    return;
		}
	    }
	    guimode = true;
	}

	boolean showJSON = false;
	boolean uriMode = false;
	if (argv.length > 0 && argv.length < 3 && argv[0].equals("-i")) {
	    // Special case: the arguments are read from standard input
	    // or from a single file containing shc URLs,
	    // probably from a file generated using the '-u' option.
	    // Each URI must appear on a single line. We'll simply
	    // re-write argv.
		
	    Charset charset = Charset.forName("UTF-8");

	    InputStream is = (argv.length == 1 || argv[1].equals("-"))?
		System.in: new FileInputStream(new File(argv[0]));

	    BufferedReader r = new
		BufferedReader(new InputStreamReader(is, charset), 4096);
	    ArrayList<String> alist = new ArrayList<>(512);
	    String line = null;
	    int linecnt = 0;
	    while ((line = r.readLine()) != null) {
		line = line.trim();
		linecnt++;
		if (line.length() == 0) continue;
		if (line.startsWith("#")) continue;
		if (line.startsWith("shc:/")) {
		    alist.add(line.trim());
		} else {
		    err.println("cvrdecode: bad input at line number "
				       + linecnt);
		}
	    }
	    argv = alist.toArray(new String[alist.size()]);
	} else if (argv.length > 0 && argv.length < 3 && argv[0].equals("-z")) {
	    try {
		zis = new ZipInputStream((argv.length == 1)? System.in:
					 (argv[1].equals("-"))? System.in:
					 new FileInputStream(argv[1]));
	    } catch (Exception ezis) {
		err.println("crvdecode: input not a ZIP file");
		err.flush();
		System.exit(1);
	    }
	} else if (argv.length > 0 && argv[0].equals("-v")) {
	    if (argv.length == 1) {
		argv = new String[] {
		    "-v", "-"
		};
	    } else if (argv.length != 2) {
		err.println("cvrdecode: "
				   + "only one file with -v is allowed");
		err.flush();
		System.exit(1);
	    }
	    showJSON = true;
	} else if (argv.length > 0 && argv[0].equals("-u")) {
	    uriMode = true;
	}

	String uri;

	ArrayList<String[]> list = new ArrayList<>(argv.length);
	PrintWriter pw = (showJSON || uriMode)? null:
	    new PrintWriter(((output != null)? new FileOutputStream(output):
			     System.out), true, Charset.forName("UTF-8"));
	CSVWriter w = (showJSON || uriMode)? null: new CSVWriter(pw, 7);
	if (showJSON == false && uriMode == false) {
	    w.writeRow("Family Name", "Given Names", "Birth Date",
		       "Last Vaccination Date", "Fully Vaccinated",
		       "Valid Signature", "Source");
	}
	
	boolean mayHaveOptions = true;
	if (zis != null) {
	    // image and shc files are taken from a zip file.
	    ZipEntry entry = null;
	    while ((entry = zis.getNextEntry()) != null) {
		if (entry.isDirectory()) continue;
		String arg = entry.getName();
		long sz = entry.getSize();
		uri = null;
		byte[] bytes;
		int offset = 0;
		if (sz != -1) {
		    if (sz > Integer.MAX_VALUE) {
			err.println("cvrdecode: zip entry too large");
			err.flush();
			zis.closeEntry();
			continue;
		    }
		    int size = (int) sz;
		    bytes = new byte[size];
		    while (offset < size) {
			int len =  zis.read(bytes, offset, size-offset);
			if (len == -1) {
			    break;
			}
			offset += len;
		    }
		} else {
		    bytes = new byte[4048];
		    ByteArrayOutputStream bos =
			new ByteArrayOutputStream(1<<18);
		    int len;
		    while ((len = zis.read(bytes, 0, 4048)) != -1) {
			bos.write(bytes, 0, len);
		    }
		    bytes = bos.toByteArray();
		    // In the other branch, offset is the total length
		    // of the entry that was just read.
		    offset = bytes.length;
		}
		if (arg.endsWith(".shc") || arg.endsWith(".SHC")) {
		    uri = new String(bytes, 0, offset, "UTF-8").trim();
		} else {
		    // assume the entry is an image
		    try {
			InputStream in = new ByteArrayInputStream(bytes, 0,
								  offset);
			// Read the URI from an embedded QR code
			BufferedImage bufferedImage = ImageIO.read(in);
			LuminanceSource source = new
			    BufferedImageLuminanceSource(bufferedImage);
			BinaryBitmap bitmap =
			    new BinaryBitmap(new HybridBinarizer(source));
			uri = (new MultiFormatReader().decode(bitmap))
			    .getText();
		    } catch (Exception e) {
			uri = null;
		    }
		    if (uri == null)  {
			err.println("cvrdecode: zip entry not readable - "
				    + arg);
			err.flush();
			zis.closeEntry();
			continue;
		    }
		}
		try {
		    processSHC(arg, uri, false);
		} catch (Exception e) {
		    err.println("cvrdecode: " + e.getMessage()
				+ " - " + arg);
		    err.flush();
		    zis.closeEntry();
		    continue;
		}
		String[] row = {familyName, givenNames, birthDate,
		    (latest == null)? "[no date]": latest.toString(),
		    full? "true": "false",
		    valid? "true": "false",
		    "zip: " + arg
		};
		list.add(row);
		zis.closeEntry();
	    }
	} else {
	    for (String arg: argv) {
		if (mayHaveOptions && arg.equals("--")) {
		    mayHaveOptions = false;
		    continue;
		} else if (mayHaveOptions && showJSON && arg.equals("-v")) {
		    if (uriMode) {
			err.println("cvrdecode: -v option not allowed "
				    + "after -u option");
			err.flush();
			System.exit(1);
		    }
		    continue;
		} else if (mayHaveOptions && uriMode && arg.equals("-u")) {
		    if (showJSON) {
			err.println("cvrdecode: -u option not allowed "
				    + "after -v option");
			err.flush();
			System.exit(1);
		    }
		    continue;
		} else if (mayHaveOptions && !arg.equals("-")
			   && arg.startsWith("-")) {
		    err.println("cvrdecode: " + arg + " option not allowed");
		    err.flush();
		    uri = null;	// to eliminate a compiler error
		    System.exit(1);
		} else if (arg.endsWith(".shc") || arg.endsWith(".SHC")) {
		    mayHaveOptions = false;
		    try {
			uri = new
			    BufferedReader(new FileReader(arg,
							  Charset
							  .forName("UTF-8")),
					   4096).readLine();
		    } catch (Exception ee) {
			uri = null;
		    }
		    if (uri == null) {
			err.println("cvrdecode: input not readable - \"" + arg
				    + "\"");
			err.flush();
			if (showJSON) System.exit(1);
			continue;
		    }
		} else if (arg.startsWith("shc:/")) {
		    mayHaveOptions = false;
		    uri = arg;
		    arg = "shc:/..." + arg.substring(arg.length() - 24);
		} else {
		    mayHaveOptions = false;
		    try {
			InputStream in = (arg.equals("-"))? System.in:
			    new FileInputStream(arg);
			BufferedImage bufferedImage = ImageIO.read(in);
			LuminanceSource source = new
			    BufferedImageLuminanceSource(bufferedImage);
			BinaryBitmap bitmap =
			    new BinaryBitmap(new HybridBinarizer(source));
			uri = (new MultiFormatReader().decode(bitmap))
			    .getText();
		    } catch (Exception e) {
			uri = null;
		    }
		    if (uri == null) {
			err.println("cvrdecode: input not readable - "
				    + arg);
			err.flush();
			if (showJSON) System.exit(1);
			continue;
		    }
		}
		if (uriMode) {
		    System.out.println(uri);
		    continue;
		}
		try {
		    processSHC(arg, uri, showJSON);
		} catch (Exception e) {
		    err.println("cvrdecode: " + e.getMessage()
				+ " - " + arg);
		    err.flush();
		    if (showJSON) System.exit(1);
		    continue;
		}

		if (showJSON == false) {
		    String[] row = {familyName, givenNames, birthDate,
			(latest == null)? "[no date]": latest.toString(),
			full? "true": "false",
			valid? "true": "false",
			(guimode? new File(arg).getName(): arg)
		    };
		    list.add(row);
		}
	    }
	}
	if (showJSON == false && uriMode == false) {
	    list.sort((sa1, sa2) -> {
		    for (int i = 0; i < 7; i++) {
			int result = sa1[i].compareTo(sa2[i]);
			if (result != 0) return result;
		    }
		    return 0;
		});
	    if (guimode) {
		tableRows = new Vector<Vector<Object>>(list.size());
	    }
	    for (String[] row: list) {
		if (guimode) {
		    Vector<Object> tblrow = new Vector<Object>(7);
		    for (int i = 0; i < row.length; i++) {
			tblrow.add(row[i]);
		    }
		    tableRows.add(tblrow);
		}
		w.writeRow(row[0], row[1], row[2], row[3],
			   row[4], row[5], row[6]);
	    }
	    w.flush();
	    w.close();
	    if (guimode) {
		SwingUtilities.invokeLater(() -> {
			showTableButton.setEnabled(true);
		    });
	    }
	}
	if (guimode == false) System.exit(0);
    }

    public static void processSHC(String arg, String uri, boolean showJSON)
	throws Exception
    {
	String encoded = (uri.startsWith("shc:/"))?
	    uri.substring(5): null;
	if (encoded == null) {
	    throw new Exception("not shc URI");
	}
	if (encoded.length() % 2 == 1) {
	    throw new Exception("URI's content must have an "
				+ "even number of digits");
	}
	CharBuffer cb = CharBuffer.allocate(uri.length()/2);
	for (int i = 0; i < encoded.length(); i += 2) {
	    char ch1 = encoded.charAt(i);
	    char ch2 = encoded.charAt(i+1);
	    if (!Character.isDigit(ch1) || !Character.isDigit(ch2)) {
		throw new Exception("URI must contain only digits");
	    }
	    int i1 = ch1 - '0';
	    int i2 = ch2 - '0';
	    char ch = (char)((i1*10 + i2) + 45);
	    cb.append(ch);
	}
	cb.flip();
	String[] components = cb.toString().split("[.]");
	ECPublicKey key = null;
	/*
	{
	    OutputStream os = new FileOutputStream("covid-19.data");
	    OutputStreamWriter w = new OutputStreamWriter(os, "US-ASCII");
	    w.write(components[0], 0, components[0].length());
	    w.write('.');
	    w.write(components[1], 0, components[1].length());
	    w.flush();
	    w.close();
	}
	*/
	byte[] data;
	{
	    ByteArrayOutputStream os = new ByteArrayOutputStream(1024);
	    OutputStreamWriter w = new OutputStreamWriter(os, "US-ASCII");
	    w.write(components[0], 0, components[0].length());
	    w.write('.');
	    w.write(components[1], 0, components[1].length());
	    w.flush();
	    w.close();
	    data = os.toByteArray();
	}
	for (int i = 0; i < components.length; i++) {
	    byte[] bytes = Base64.getUrlDecoder().decode(components[i]);
	    if (i == 0) {
		String json = new String(bytes, 0, bytes.length, "UTF-8");
		Object object = JSUtilities.JSON.parse(json);
		if (object instanceof JSObject) {
		    JSObject obj = (JSObject) object;
		    if (!obj.get("zip").equals("DEF")) {
			throw new IllegalStateException("zip = "
							+ obj.get("zip")
							+ " not expected");
		    }
		    if (!obj.get("alg").equals("ES256")) {
			throw new IllegalStateException("alg = "
							+ obj.get("alg")
							+ " not expected");
			
		    }
		    key = getPublicKey((String)obj.get("kid"));
		    if (key == null) {
			throw new IllegalStateException("no key for kid = "
							+ obj.get("kid"));
		    }
		}
	    } else if (i == 1) {
		Inflater infl = new Inflater(true);
		infl.setInput(bytes);
		// just oversize the buffer - probably much larger than
		// needed.
		bytes = new byte[8192];
		int len = infl.inflate(bytes);
		String json = new String(bytes, 0, len, "UTF-8");
		if (showJSON) {
		    printInfo(json);
		    continue;
		}
		processInfo(json);
		latest = null;
		if (dosesNeeded > 0 && occurrence.size() > 0) {
		    latest = occurrence.get(0);
		}
		full = (dosesNeeded > 0 && dosesNeeded <= occurrence.size());
		for (LocalDate date: occurrence) {
		    if (date.isAfter(latest)) {
			latest = date;
		    }
		}
	    } else if (i == 2) {
		valid = verify(key, data, mkder(bytes));
		if (showJSON) {
		    if (valid) {
			System.out.println(".......... OK");
		    } else {
			System.out.println(".......... NOT VALID");
		    }
		}
	    }
	}
    }
}
