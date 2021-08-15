import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Set;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Installer {

    private static boolean stackTraceMode = false;

    private static boolean optMode = false;
    private static boolean opt2Mode = false;
    private static boolean usrlocalMode = false;
    private static boolean posixMode = false;
    private static boolean windowsMode = false;

    private static UserPrincipal owner = null;
    private static GroupPrincipal group = null;

    // false for testing/debugging ; true for use.
    private static boolean CLEANUP_CVRDIR_ON_ERROR = false;

    private static boolean notDryrun = true;
    private static boolean dryrun = false;
    private static boolean deleteRootOnFailure = false;
    private static String rootName = null;
    private static Path root = null;
    private static Path cvrdir = null;
    private static Path old = null;

    private static String tsname = null;
    private static Path tspath = null;
    private static void
	createDir(Path p, Set<PosixFilePermission> dirPerm)
	throws IOException
    {
	if (dryrun) System.out.println("would create " + p);
	if (dirPerm == null) {
	    if (notDryrun) Files.createDirectories(p);
	} else {
	    if (notDryrun) {
		Files.createDirectories
		    (p, PosixFilePermissions.asFileAttribute(dirPerm));
		PosixFileAttributeView pv =
		    Files.getFileAttributeView(p, PosixFileAttributeView.class);
		if (pv != null) {
		    if (owner != null) {
			pv.setOwner(owner);
		    }
		    if (group != null) {
			pv.setGroup(group);
		    }
		    pv.setPermissions(dirPerm);
		}
	    }
	}
    }

    // the path used is start.resolve(p). Start should already exist.
    // the group and owner will be set for p only.
    private static Path
	createDirs(Path start, Path p, Set<PosixFilePermission> dirPerm)
	throws IOException
    {
	Path path = start.resolve(p);
	if (dryrun) System.out.println("would create " + path);
	else {
	    if (dirPerm == null) {
		Files.createDirectories(path);
	    } else {
		Files.createDirectories
		    (path, PosixFilePermissions.asFileAttribute(dirPerm));
		int index = start.getNameCount();
		int limit = path.getNameCount();
		while (index < limit) {
		    Path dir = path.subpath(0, index);
		    if (start.isAbsolute()) {
			dir = start.getRoot().resolve(dir);
		    }
		    PosixFileAttributeView pv =
			Files.getFileAttributeView
			(dir, PosixFileAttributeView.class);
		    if (pv != null) {
			if (owner != null) {
			    pv.setOwner(owner);
			}
			if (group != null) {
			    pv.setGroup(group);
			}
			pv.setPermissions(dirPerm);
		    }
		    index++;
		}
	    }
	}
	return path;
    }

    // check OS - we need this for writing text files as the
    // resources all use Unix/Linux end-of-line conventions.
    private static boolean windowsOS =
	(System.getProperty("os.name").replaceAll("\\s", ""))
	.startsWith("Windows");

    private static void
	createFile(Path start, Path p,
		   Set<PosixFilePermission> dirPerm,
		   Set<PosixFilePermission> filePerm,
		   InputStream is)
	throws IOException
    {
	Path path;
	Path file = p.getFileName();
	String fileName = file.toString();
	int lastind = fileName.lastIndexOf('.');
	String extension = null;
	// extension will be the file name if no extension is provided
	extension = fileName.substring(lastind+1);
	// textfile is true if the file should have a Unix EOL character
	// replaced with CRLF on Windows systems.  We specifically do
	// not want to modify PDF files, ZIP files or JAR files.
	boolean textfile = false;
	if (extension.equals("html")
	    || extension.equals("txt")
	    || extension.equals("css")
	    || extension.equals("package-list")
	    || extension.equals("policy")
	    || extension.equals("conf")
	    || extension.equals("cmd")
	    || extension.equals("sh")
	    || extension.equals("java")
	    || extension.equals("js")
	    || extension.equals("1")
	    || extension.equals("5")
	    || extension.equals("changelog")
	    || extension.equals("copyright")) {
	    textfile = true;
	}

	if (file == null) throw new IOException("empty path");
	if (p.getNameCount() > 1) {
	    Path parent = p.getParent();
	    path = createDirs(start, parent, dirPerm);
	} else {
	    path = start;
	    if (!Files.isDirectory(path)) {
		createDir(path, dirPerm);
	    }
	}
	path = path.resolve(file);
	if (notDryrun) {
	    if (filePerm == null) {
		Files.createFile(path);
	    } else {
		Files.createFile(path,
				 PosixFilePermissions.asFileAttribute
				 (filePerm));
	    }
	    PosixFileAttributeView pv =
		Files.getFileAttributeView(path,
					   PosixFileAttributeView.class);
	    if (pv != null) {
		if (owner != null) {
		    pv.setOwner(owner);
		}
		if (group != null) {
		    pv.setGroup(group);
		}
	    }
	} else {
	    System.out.println("would create file " + path);
	}
	if (notDryrun) {
	    if (windowsOS && textfile) {
		InputStreamReader isr = new InputStreamReader(is, "UTF-8");
		LineNumberReader reader = new LineNumberReader(isr);
		PrintWriter writer = new PrintWriter(path.toFile(), "UTF-8");
		String line = null;
		while ((line = reader.readLine()) != null) {
		    writer.println(line);
		}
		writer.flush();
		writer.close();
	    } else {
		Files.copy(is, path, StandardCopyOption.REPLACE_EXISTING);
	    }
	    PosixFileAttributeView pv =
		Files.getFileAttributeView(path,
					   PosixFileAttributeView.class);
	    if (pv != null) {
		if (owner != null) {
		    pv.setOwner(owner);
		}
		if (group != null) {
		    pv.setGroup(group);
		}
		pv.setPermissions(filePerm);
	    }
	}
    }

    private static void createLink(Path link, Path target, boolean relative)
	throws IOException
    {
	Path parent = link.getParent();
	Path name = link.getFileName();
	if (Files.isSymbolicLink(parent)) {
	    int cnt = 0;
	    while (Files.isSymbolicLink(parent)) {
		parent = parent.resolveSibling
		    (Files.readSymbolicLink(parent));
		cnt++;
		if (cnt > 256) {
		    throw new IOException("Too many symbolic links: " + link);
		}
	    }
	    link = parent.resolve(name);
	}
	if (relative) {
	    // Java 7 documentation is a bit off - experimentally we
	    // need getParent() to get the right target.
	    target = link.getParent().relativize(target);
	}
	if (notDryrun) {
	    Files.createSymbolicLink(link, target);
	    PosixFileAttributeView pv =
		Files.getFileAttributeView(link, PosixFileAttributeView.class,
					   LinkOption.NOFOLLOW_LINKS);
	    if (pv != null) {
		if (owner != null) {
		    pv.setOwner(owner);
		}
		if (group != null) {
		    pv.setGroup(group);
		}
	    }
	} else {
	    System.out.println("would link " + link + " to " + target);
	}
    }

    private static void unzip(FileSystem fs, Path start, String zipResource,
			      Set<PosixFilePermission> dirPerm,
			      Set<PosixFilePermission> filePerm)
	throws IOException
    {
	InputStream is = ClassLoader.getSystemResourceAsStream(zipResource);
	ZipInputStream zis = new ZipInputStream(is);
	String separator = fs.getSeparator();
	String quotedSeparator = Matcher.quoteReplacement(separator);
	ZipEntry entry;
	while ((entry = zis.getNextEntry()) != null) {
	    Path p = fs.getPath(entry.getName().replaceAll("/",
							   quotedSeparator));
	    if (notDryrun) {
		createFile(start, p, dirPerm, filePerm, zis);
	    } else {
		Path path = start.resolve(p);
		System.out.println("would create " + path);
		zis.skip(entry.getSize());
	    }
	    zis.closeEntry();
	}
	zis.close();
    }

    private static void clearDirectory(Path dir) throws IOException {
	if (Files.isDirectory(dir)) {
	    for (Path p: Files.newDirectoryStream(dir)) {
		if (Files.isDirectory(p)) {
		    clearDirectory(p);
		}
		if (notDryrun) {
		    Files.delete(p);
		} else {
		    System.out.println("would delete " + p);
		}
	    }
	}
    }

    static void cleanupOnError() {
	if (notDryrun && old != null) {
	    if (Files.isDirectory(old)) {
		if (Files.isDirectory(cvrdir)) {
		    try {
			clearDirectory(cvrdir);
			Files.delete(cvrdir);
			Files.move(old, cvrdir);
		    } catch (IOException ee) {
			System.out.println
			    ("restoration of old directory failed");
		    }
		}
	    }
	}
	if (deleteRootOnFailure && root != null) {
	    // This branch is run only if we created the
	    // toplevel directory, in which case and old
	    // value will not exist.
	    if (CLEANUP_CVRDIR_ON_ERROR) {
		if (Files.isDirectory(cvrdir)) {
		    try {
			clearDirectory(cvrdir);
			Files.delete(cvrdir);
		    } catch (IOException e) {}
		}
	    }
	    try {
		Files.deleteIfExists(root);
	    } catch (IOException e) {}
	}
    }

    private static JRadioButton ulButton = null;
    private static JRadioButton optButton = null;
    private static JRadioButton opt2Button = null;
    private static JCheckBox gzipManCheckBox = null;

    private static String ourCodebase;
    static {
	try {
	    ourCodebase =
		(new File(Installer.class.getProtectionDomain()
			  .getCodeSource().getLocation().toURI()))
		.getCanonicalFile().getCanonicalPath();
	} catch (Exception e) {
	    System.out.println("Could not find our own codebase");
	    System.exit(1);
	}
    }

    static void installInteractively() {
	final FileSystem fs = FileSystems.getDefault();
	final JFrame frame = new JFrame("Installer");
	frame.setLocationRelativeTo(null);
	Container pane = frame.getContentPane();
	GridBagLayout gridbag = new GridBagLayout();
	GridBagConstraints c = new GridBagConstraints();
	c.gridx = 0;
	c.gridy = 0;
	c.ipadx = 10;
	c.ipady = 10;
	c.anchor = GridBagConstraints.LINE_START;
	pane.setLayout(gridbag);

	String optString = "install in /opt";
	String opt2String =
	    "install in /opt & configure /opt/bin  /opt/man/*";
	String ulString = "install in /usr/local";
	String gzipManString = "gzip man pages";
	String uninstallString = "Uninstall";
	String dryrunString = "dry run";
	String runString = "run";

	final boolean isWindows = windowsOS;
	if (windowsOS) {
	    String wdirname = System.getenv("PROGRAMDATA");
	    if (wdirname == null) {
		JOptionPane.showMessageDialog(null,
					      "Cannot find installation "
					      + "directory - %PROGRAMDATA% "
					      + "missing",
					      "cvrdecode Installer Error",
					      JOptionPane.ERROR_MESSAGE);
		System.exit(1);
	    } else {
		wdirname = "%PROGRAMDATA%"
		    + fs.getSeparator() + "cvrdecode";
	    }
	    JLabel label1 = new JLabel("Install into directory");
	    JLabel label2 = new JLabel(wdirname);

	    c.gridy++;
	    gridbag.setConstraints(label1, c);
	    pane.add(label1);
	    c.gridy++;
	    gridbag.setConstraints(label2, c);
	    pane.add(label2);
	} else {
	    ulButton  = new JRadioButton(ulString);
	    optButton = new JRadioButton(optString);
	    opt2Button = new JRadioButton(opt2String);
	    gzipManCheckBox = new JCheckBox(gzipManString);
	    gzipManCheckBox.setSelected(false);
	    // Select radio buttons and a checkbox based on
	    // an existing installation, with default values if nothing
	    // has been installed.
	    boolean oldInstallation = true;
	    if (Files.isDirectory(fs.getPath("/usr/local/share/bzdev"))) {
		ulButton.setSelected(true);
		Path p =
		    fs.getPath("/usr/local/share/bzdev/man/man1/lsnof.1.gz");
		if (Files.exists(p)) {
		    gzipManCheckBox.setSelected(true);
		}
	    } else if (Files.isDirectory(fs.getPath("/opt/bzdev"))) {
		if (Files.isDirectory(fs.getPath("/opt/bin"))) {
		    opt2Button.setSelected(true);
		} else {
		    optButton.setSelected(true);
		}
		Path p =
		    fs.getPath("/opt/bzdev/man/man1/lsnof.1.gz");
		if (Files.exists(p)) {
		    gzipManCheckBox.setSelected(true);
		}
	    } else {
		// no existing installation so assume /usr/local.
		ulButton.setSelected(true);
		oldInstallation = false;
	    }


	    ButtonGroup group = new ButtonGroup();
	    group.add(ulButton);
	    group.add(optButton);
	    group.add(opt2Button);

	    if (oldInstallation) {
		// disable options that would result in a new
		// installation not completely overriding an old one
		if (ulButton.isSelected()) {
		    optButton.setEnabled(false);
		    opt2Button.setEnabled(false);
		} else {
		    ulButton.setEnabled(false);
		}
	    }

	    c.gridy++;
	    gridbag.setConstraints(ulButton, c);
	    pane.add(ulButton);
	    c.gridy++;
	    gridbag.setConstraints(optButton, c);
	    pane.add(optButton);
	    c.gridy++;
	    gridbag.setConstraints(opt2Button, c);
	    pane.add(opt2Button);
	    c.gridy++;
	    gridbag.setConstraints(gzipManCheckBox, c);
	    pane.add(gzipManCheckBox);
	}

	final JCheckBox uninstallCheckBox = new JCheckBox(uninstallString);
	uninstallCheckBox.setSelected(false);
	c.gridy++;
	gridbag.setConstraints(uninstallCheckBox, c);
	pane.add(uninstallCheckBox);

	final JCheckBox dryrunCheckBox = new JCheckBox(dryrunString);
	dryrunCheckBox.setSelected(false);
	c.gridy++;
	gridbag.setConstraints(dryrunCheckBox, c);
	pane.add(dryrunCheckBox);

	final JButton runButton = new JButton(runString);

	runButton.addActionListener(new AbstractAction() {
		public void actionPerformed(ActionEvent event) {
		    ArrayList<String> argvList = new ArrayList<>();
		    if (isWindows) {
			argvList.add("--windows");
		    } else {
			if (ulButton.isSelected()) {
			    argvList.add("--usrlocal");
			}
			if (optButton.isSelected()) {
			    argvList.add("--opt");
			}
			if (opt2Button.isSelected()) {
			    argvList.add("--opt2");
			}
			if (gzipManCheckBox.isSelected()) {
			    argvList.add("--gzipManPages");
			}
		    }
		    if (uninstallCheckBox.isSelected()) {
			argvList.add("--uninstall");
			if (!isWindows && ulButton.isSelected()) {
			    if (!Files.isDirectory
				(fs.getPath("/usr/local/share/bzdev"))) {
				JOptionPane.showMessageDialog
				    (frame,
				     "not installed in /usr/local",
				     "cvrdecode Installer Error",
				     JOptionPane.ERROR_MESSAGE);
				return;
			    }
			}
			if (!isWindows
			    && (optButton.isSelected()
				|| opt2Button.isSelected())) {
			    if (!Files.isDirectory(fs.getPath("/opt/bzdev"))) {
				JOptionPane.showMessageDialog
				    (frame,
				     "not installed in /opt",
				     "cvrdecode Installer Error",
				     JOptionPane.ERROR_MESSAGE);
				return;
			    }
			    if (opt2Button.isSelected()) {
				if (!Files
				    .exists(fs.getPath("/opt/bin/scrunner"))) {
				    JOptionPane.showMessageDialog
					(frame,
					 "binaries not in /opt/bin",
					 "cvrdecode Installer Error",
					 JOptionPane.ERROR_MESSAGE);
				    return;
				}
			    }
			}
		    }
		    String[] argv =
			argvList.toArray(new String[argvList.size()]);
		    if (dryrunCheckBox.isSelected()) {
			String cmd = "java -jar "
			    + ourCodebase;
			for (String option: argv) {
			    cmd = cmd + " " + option;
			}
			JTextField tf = new JTextField(cmd, cmd.length());
			tf.setEditable(false);
			JOptionPane.showMessageDialog
			    (frame, tf, "cvrdecode Installer Java Command",
			     JOptionPane.PLAIN_MESSAGE);
			return;
		    }
		    try {
			mainMethod(argv);
			JOptionPane.showMessageDialog
			    (frame, "Installer will exit - no errors detected",
			     "cvrdecode Installer",
			     JOptionPane.INFORMATION_MESSAGE);
			System.exit(0);
		    } catch(Exception e) {
			cleanupOnError();
			JOptionPane.showMessageDialog
			    (frame, e.getMessage(), "cvrdecode Installer Error",
			     JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		    } catch(Error e) {
			cleanupOnError();
			JOptionPane.showMessageDialog
			    (frame, e.getMessage(), "cvrdecode Installer Error",
			     JOptionPane.ERROR_MESSAGE);
		    } catch (Throwable e) {
			JOptionPane.showMessageDialog
			    (frame, e.getMessage(), "cvrdecode Installer Error",
			     JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		    }
		}
	    });

	c.gridy++;
	c.anchor = GridBagConstraints.CENTER;
	gridbag.setConstraints(runButton, c);
	pane.add(runButton);

	frame.pack();
	frame.addWindowListener(new WindowAdapter() {
		public void
		    windowClosing(WindowEvent e) {
		    System.exit(0);
		}
	    });

	frame.setVisible(true);
    }


    public static void main(String argv[]) throws Exception {
	if (argv.length == 0) {
	    // Use a GUI to get the command-line options and
	    // call mainMethod
	    installInteractively();
	} else {
	    try {
		mainMethod(argv);
	    } catch (Exception e) {
		if (stackTraceMode) {
		    e.printStackTrace(System.out);
		} else {
		    System.out.print(e.getClass().getName() + ": ");
		    System.out.println(e.getMessage());
		}
		cleanupOnError();
		System.exit(1);
	    } catch (Error e) {
		if (stackTraceMode) {
		    e.printStackTrace(System.out);
		} else {
		    System.out.print(e.getClass().getName() + ": ");
		    System.out.println(e.getMessage());
		}
		cleanupOnError();
		System.exit(1);
	    } catch (Throwable e) {
		// We'll explicitly throw an instance of Throwable
		// when we want an error message and would exit
		// immediately.  The reason for handling it this way
		// is so that a GUI can respond by putting up a dialog box.
		System.out.println(e.getMessage());
		System.exit(1);
	    }
	    System.exit(0);
	}
    }

    private static void mainMethod(String argv[]) throws Throwable {

	// windowsOS = true; 	// test only


	boolean gzipManPages = false;

	boolean uninstall = false;

	String javaCmd = "java";
	String javawCmd = "javaw";


	// check OS - one file is hidden and the windows conventions
	// are different from the Unix ones.
	boolean isWindows = windowsOS;

	tsname = isWindows? "timestamp.dat": ".timestamp";

	String wdirname = isWindows? System.getenv("PROGRAMDATA"): null;
	int index = 0;
	while (index < argv.length) {
	    if (argv[index].equals("--help") || argv[index].equals("-?")) {
		System.out.println("installer options: ");
		System.out.println(" --opt");
		System.out.println("        Install into /opt (for "
				   + "Unix/Linux)");
		System.out.println(" --opt2");
		System.out.println("        Install into /opt " 
				   + "and configure /opt/bin, "
				   + "/opt/man/man1, & opt/man/man5");
		System.out.println(" --usrlocal");
		System.out.println("        Install into /usr/local (for "
				   + "Unix/Linux)");
		System.out.println(" --windows");
		System.out.println("        set up the installation for "
				   +"Windows systems");
		System.out.println(" --rootdir DIRNAME");
		System.out.println("        use DIRNAME as the name of the "
				   + "root directory (e.g., for testing)");
		System.out.println(" --dryrun");
		System.out.println("        show what would be installed");
		System.out.println(" --gzipManPages");
		System.out.println("        gzip the man pages (Linux/Unix)");
		System.out.println(" --java COMMAND_NAME");
		System.out.println("        the name of the java command to "
				   + "use in shell scripts");
		System.out.println(" --javaw COMMAND_NAME");
		System.out.println("        the name of the javaw command "
				   + "to use in batch files");
		System.out.println(" --stacktrace");
		System.out.println("        make the installer print a "
				   + "stacktrace if an error occurs");
		System.out.println(" --uninstall");
		System.out.println("        uninstall the software");
		System.out.println(" --help");
		System.out.println("        print installer options");
		System.out.println(" -?");
		System.out.println("        print installer options");
		return;
	    }
	    if (argv[index].equals("--opt")) {
		optMode = true;
		posixMode = true;
	    } else if (argv[index].equals("--opt2")) {
		optMode = true;
		opt2Mode = true;
		posixMode = true;
	    } else if (argv[index].equals("--usrlocal")) {
		usrlocalMode = true;
		posixMode = true;
	    } else if (argv[index].equals("--windows")) {
		windowsMode = true;
	    } else if (argv[index].equals("--rootdir")) {
		index++;
		if (index < argv.length) {
		    rootName = argv[index];
		} else {
		    throw new Throwable("missing argument for --rootdir");
		}
	    } else if (argv[index].equals("--dryrun")) {
		notDryrun = false;
		dryrun = true;
	    } else if (argv[index].equals("--gzipManPages")) {
		gzipManPages = true;
	    } else if (argv[index].equals("--java")) {
		index++;
		javaCmd = argv[index];
	    } else if (argv[index].equals("--javaw")) {
		index++;
		javawCmd = argv[index];
	    } else if (argv[index].equals("--stacktrace")) {
		stackTraceMode = true;
	    } else if (argv[index].equals("--uninstall")) {
		uninstall = true;
	    } else {
	        throw new Throwable("UnknownOption " + argv[index]);
	    }
	    index++;
	}
	FileSystem fs = FileSystems.getDefault();
	Path wdirpath = (wdirname != null)? fs.getPath(wdirname): null;

	if (rootName != null) {
	    if (wdirpath != null) {
		wdirpath = wdirpath.subpath(0, wdirpath.getNameCount());
	    } else {
		wdirpath = fs.getPath("PROGRAMDATA");
	    }
	}

	Path jhome = fs.getPath(System.getProperty("java.home"));

	root = (rootName != null)? fs.getPath(rootName): jhome.getRoot();
	if (rootName != null) {
	    if (Files.exists(root)) {
		if (!Files.isDirectory(root)) {
		    throw new Throwable(rootName + " is not a directory");
		} else if (!Files.isWritable(root)) {
		    throw new Throwable(rootName + " cannot be modified");
		}
	    } else {
		Files.createDirectory(root);
		deleteRootOnFailure = true;
	    }
	}
	if (dryrun) System.out.println("root directory = " +root);

	Map<String,Path> map = new HashMap<String,Path>();

	String executables[] = {
	    "cvrdecode"
	};

	String windowsExecutables[] = {
	    "cvrdecode.cmd",
	    "cvrdecodew.cmd",
	};

	String pdfs[] = {
	};
	
	String htmls[] = {
	    "cvrdecode.html"
	};
	String libraries[] = {
	    "cvrdecode.jar",
	    "libbzdev-base.jar",
	    "libbzdev-desktop.jar",
	    "core.jar",
	    "javase.jar",
	    "share.zip",
	    "finish.txt",
	    "cvrdecode.desktop"
	};
	
	String policyfiles[] = {
	};

	String confFiles[] = {
	};

	String man1s[] = {
	    "cvrdecode.1"
	};

	String man5s[] = {
	};

	String man1sGzip[] = {
	    "cvrdecode.1.gz",
	};
	String man5sGzip[] = {
	};

	String docfiles[] = {
	    "changelog",
	    "copyright"
	};

	String zipDocNames[] = {
	};

	UserPrincipalLookupService upls = null;

	try {
	    upls = FileSystems.getDefault().getUserPrincipalLookupService();
	    UserPrincipal user =
		upls.lookupPrincipalByName(System.getProperty("user.name"));
	    if (!windowsOS) {
		// if not Windows (this code makes no assumptions as to
		// whether some version of Windows might supoort user
		// names), assume some variant of Unix, all of which
		// use 'root' as the name for the superuser account.
		// If the attempt to find a lookup service failed, we know
		// that, regardless of the OS, that it is not a Unix-like
		// OS.
		if (rootName == null) {
		    UserPrincipal su = null;
		    su = upls.lookupPrincipalByName("root");
		    if (notDryrun && !user.equals(su)) {
			throw new Throwable("must run as root");
		    }
		}
	    }
	} catch (UnsupportedOperationException e) {}

	Path installPath = null;
	String installPathString = null; // use only for Windows.
	String cvrdirString = null;	 // use only for Windows.

	Path cvrbin = null;
	Path cvrdoc = null;
	Path cvrman = null;
	Path cvrman1 = null;
	Path stdbin = null;
	Path stdman = null;
	Path stdman1 = null;

	if (windowsMode) {
	    if (wdirpath == null) {
		throw new Throwable("Attempt to use --windows but system not "
				    + "recognized as a Windows system");

	    }
	    if (wdirpath.isAbsolute()) {
		installPath = wdirpath;
		installPathString = "%PROGRAMDATA%";
	    } else {
		installPath = root.resolve(wdirpath);
	    }
	} else {
	    if (optMode) {
		int lcnt = 0;
		installPath = root.resolve(Paths.get("opt"));
		while (Files.isSymbolicLink(installPath)) {
		    installPath = installPath.resolveSibling
			(Files.readSymbolicLink(installPath));
		    if ((++lcnt) > 256) {
			throw new IOException("too many symbolic links: "
					      + installPath);
		    }
		}
		if (opt2Mode) {
		    stdbin = installPath.resolve("bin");
		    lcnt = 0;
		    while (Files.isSymbolicLink(stdbin)) {
			stdbin = stdbin.resolveSibling
			    (Files.readSymbolicLink(stdbin));
			if ((++lcnt) > 256) {
			    throw new IOException("too many symbolic links: "
						  + stdbin);
			}
		    }
		    stdman = installPath.resolve("man");
		    lcnt = 0;
		    while (Files.isSymbolicLink(stdman)) {
			stdman = stdman.resolveSibling
			    (Files.readSymbolicLink(stdman));
			if ((++lcnt) > 256) {
			    throw new IOException("too many symbolic links: "
						  + stdman);
			}
		    }
		    stdman1 = stdman.resolve("man1");
		    lcnt = 0;
		    while (Files.isSymbolicLink(stdman1)) {
			stdman1 = stdman1.resolveSibling
			    (Files.readSymbolicLink(stdman1));
			if ((++lcnt) > 256) {
			    throw new IOException("too many symbolic links: "
						  + stdman1);
			}
		    }
		}
	    } else if (usrlocalMode) {
		int lcnt = 0;
		installPath = root.resolve("usr");
		while (Files.isSymbolicLink(installPath)) {
		    installPath = installPath.resolveSibling
			(Files.readSymbolicLink(installPath));
		    if ((++lcnt) > 256) {
			throw new IOException("too many symbolic links: "
					      + installPath);
		    }
		}
		installPath = installPath.resolve("local");
		lcnt = 0;
		while (Files.isSymbolicLink(installPath)) {
		    installPath = installPath.resolveSibling
			(Files.readSymbolicLink(installPath));
		    if ((++lcnt) > 256) {
			throw new IOException("too many symbolic links: "
					      + installPath);
		    }
		}
		stdbin = installPath.resolve("bin");
		lcnt = 0;
		while (Files.isSymbolicLink(stdbin)) {
		    stdbin = stdbin.resolveSibling
			(Files.readSymbolicLink(stdbin));
		    if ((++lcnt) > 256) {
			throw new IOException("too many symbolic links: "
					      + stdbin);
		    }
		}
		stdman = installPath.resolve("man");
		lcnt = 0;
		while (Files.isSymbolicLink(stdman)) {
		    stdman = stdman.resolveSibling
			(Files.readSymbolicLink(stdman));
		    if ((++lcnt) > 256) {
			throw new IOException("too many symbolic links: "
					      + stdman);
		    }
		}
		stdman1 = stdman.resolve("man1");
		lcnt = 0;
		while (Files.isSymbolicLink(stdman1)) {
		    stdman1 = stdman1.resolveSibling
			(Files.readSymbolicLink(stdman1));
		    if ((++lcnt) > 256) {
			throw new IOException("too many symbolic links: "
					      + stdman1);
		    }
		}
		// installPath = installPath.resolve("share");
	    } else {
		if (deleteRootOnFailure) {
		    Files.delete(root);
		}
		throw new Throwable ("Missing option: either --windows, --opt, "
				   + "opt2, or --usrlocal");
	    }
	}

	// installPath is now set up, so determine various file attributes
	Set<PosixFilePermission> dirPerm = null;
	Set<PosixFilePermission> filePerm = null;
	Set<PosixFilePermission> exePerm = null;
	PosixFileAttributeView pav = Files.exists(installPath)?
	    Files.getFileAttributeView(installPath,
				       PosixFileAttributeView.class):
	    Files.getFileAttributeView(root, PosixFileAttributeView.class);
	if (pav != null) {
	    PosixFileAttributes pfa = pav.readAttributes();
	    owner = pfa.owner();
	    group = pfa.group();
	     dirPerm = PosixFilePermissions.fromString("rwxr-xr-x");
	     filePerm = PosixFilePermissions.fromString("rw-r--r--");
	     exePerm = PosixFilePermissions.fromString("rwxr-xr-x");
	}
	// now create the additional paths.
	if (usrlocalMode) {
	    cvrdir = installPath.resolve("share");
	    int lcnt = 0;
	    while (Files.isSymbolicLink(cvrdir)) {
		cvrdir = cvrdir.resolveSibling
		    (Files.readSymbolicLink(cvrdir));
	    }
	    cvrdir = cvrdir.resolve("cvrdecode");
	} else {
	    cvrdir = installPath.resolve("cvrdecode");
	}
	if (installPathString != null) {
	    cvrdirString = installPathString
		+ System.getProperty("file.separator") + "cvrdecode";
	}
	Path ourOld =  cvrdir.resolveSibling("cvrdecode-old");
	tspath = cvrdir.resolve(tsname);

	cvrbin = cvrdir.resolve("bin");
	cvrdoc = cvrdir.resolve("doc");
	if (posixMode) {
	    cvrman = cvrdir.resolve("man");
	    cvrman1 = cvrman.resolve("man1");
	}
	// Handle uninstall ; otherwise save current installation in case
	// of errors.
	if (uninstall || (Files.isDirectory(cvrdir) && Files.exists(tspath))) {
	    if (Files.isDirectory(ourOld)) {
		clearDirectory(ourOld);
		if (notDryrun) {
		    Files.delete(ourOld);
		} else {
		    System.out.println("would delete " + old);
		}
	    }
	    if (Files.exists(ourOld)) {
		throw new Throwable("File " + old
				   + " exists and is not a directory"
				   + " or cannot be deleted");
	    } else if (uninstall) {
		if (Files.isDirectory(cvrdir)) {
		    if (stdbin != null && Files.isDirectory(stdbin)) {
			for (String name: executables) {
			    Path p = stdbin.resolve(name);
			    if (Files.isSymbolicLink(p)) {
				// make sure the symbolic link points to
				// a file in our bin directory.
				Path dp = p.resolveSibling
				    (Files.readSymbolicLink(p)).normalize()
				    .getParent();
				if (dp.equals(cvrbin)) {
				    if (notDryrun) {
					Files.deleteIfExists(p);
				    } else {
					System.out.println("would delete " + p);
				    }
				} else {
				    System.out.println
					("Warning: could not delete " + p);
				}
			    }
			}
		    }
		    if (stdman1 != null && Files.isDirectory(stdman1)) {
			for (String name: man1s) {
			    Path p = stdman1.resolve(name);
			    if (Files.isSymbolicLink(p)) {
				// make sure the symbolic link points to
				// a file in our man1 directory.
				Path dp = p.resolveSibling
				    (Files.readSymbolicLink(p)).normalize()
				    .getParent();
				if (dp.equals(cvrman1)) {
				    if (notDryrun) {
					Files.deleteIfExists(p);
				    } else {
					System.out.println("would delete " + p);
				    }
				}
			    }
			}
			for (String name: man1sGzip) {
			    Path p = stdman1.resolve(name);
			    if (Files.isSymbolicLink(p)) {
				// make sure the symbolic link points to
				// a file in our man1 directory.
				Path dp = p.resolveSibling
				    (Files.readSymbolicLink(p)).normalize()
				    .getParent();
				if (dp.equals(cvrman1)) {
				    if (notDryrun) {
					Files.deleteIfExists(p);
				    } else {
					System.out.println("would delete " + p);
				    }
				}
			    }
			}
		    }

		    clearDirectory(cvrdir);
		    if (notDryrun) {
			Files.delete(cvrdir);
		    }
		    return;
		}
		if (Files.exists(cvrdir)) {
		    throw new Throwable("File " + cvrdir
				   + " exists and is not a directory"
				   + " or cannot be deleted");
		}
	    } else {
		try {
		    if (notDryrun) {
			Files.move(cvrdir, ourOld);
		    } else {
			System.out.println("would move " + cvrdir
					   + " to " + ourOld);
		    }
		    old = ourOld;
		} catch (Exception e) {
		    throw new Throwable("could not move " + cvrdir
					+ " to " + ourOld
					+ "(" + e.getClass().getName()
					+ ": "
					+ e.getMessage() + ")");
		}
	    }
	}
	// Paths are now set up, so create the directories with
	// appropriate permissions
	if (!Files.exists(installPath)) {
	    createDir(installPath, dirPerm);
	} else if (!Files.isDirectory(installPath)) {
	    throw new Throwable("Install path does not point "
				+ "to a directory: " + installPath);
	}
	if (!Files.exists(cvrdir)) {
	    createDir(cvrdir, dirPerm);
	} else if (!Files.isDirectory(cvrdir)) {
	    System.out.println("Expected a directory: " + cvrdir);
	    System.exit(1);
	}
	clearDirectory(cvrdir);

	ArrayList<Path> alist = new ArrayList<>();
	alist.add(cvrbin);
	alist.add(cvrdoc);
	if (posixMode) {
	    alist.add(cvrman);
	    alist.add(cvrman1);
	}
	if (stdbin != null) alist.add(stdbin);
	if (stdman != null) alist.add(stdman);
	if (stdman1 != null) alist.add(stdman1);
	Path[] parray = alist.toArray(new Path[alist.size()]);

	if (notDryrun) {
	    // create the directories with the correct permissions
	    // and then set the owner and group if these exist.
	    for (Path p: parray) {
		if (Files.exists(p)) continue;
		createDir(p, dirPerm);
	    }
	} else {
	    for (Path p: parray) {
		System.out.println("Would create directory " + p);
	    }
	}

	if (posixMode) {
	    for (String name: executables) {
		InputStream is =
		    ClassLoader.getSystemResourceAsStream(name + ".sh");
		if (is == null) {
		    throw new Exception("could not find resource " + name);

		}
		InputStreamReader reader = new InputStreamReader(is, "UTF-8");
		char[] carray = new char[1024];
		int cnt = 0;
		int total = 0;
		while ((cnt = reader.read(carray,cnt,carray.length-cnt)) > 0) {
		    total += cnt;
		}
		is.close();
		String file = new String(carray, 0, total);
		file = file.replace("JAVA", javaCmd);
		file = file.replace("CVRDECODEDIR", cvrdir.toString());
		byte[] barray = file.getBytes("UTF-8");
		ByteArrayInputStream bis = new ByteArrayInputStream(barray);
		createFile(cvrbin, fs.getPath(name), dirPerm, exePerm, bis);
		bis.close();
	    }
	} else {
	    for (String name: windowsExecutables ) {
		InputStream is =
		    ClassLoader.getSystemResourceAsStream(name);
		if (is == null) {
		    throw new Exception("could not find resource " + name);
		}
		InputStreamReader reader = new InputStreamReader(is, "UTF-8");
		char[] carray = new char[1024];
		int cnt = 0;
		int total = 0;
		while ((cnt = reader.read(carray,cnt,carray.length-cnt)) > 0) {
		    total += cnt;
		}
		is.close();
		String file = new String(carray, 0, total);
		if (file.contains("JAVAW")) {
			file = file.replace("JAVAW", javawCmd);
		} else {
		    file = file.replace("JAVA", javaCmd);
		}
		if (cvrdirString != null) {
		    file = file.replace("CVRDECODEDIR", cvrdirString);
		} else {
		    file = file.replace("CVRDECODEDIR", "\"" + cvrdir.toString()
					+ "\"");
		}
		file = file.replace("/", System.getProperty("file.separator"));
		// Windows/DOS line termination now handled in createFile.
		// file = file.replace("\n", System.getProperty("line.separator"));
		byte[] barray = file.getBytes("UTF-8");
		ByteArrayInputStream bis = new ByteArrayInputStream(barray);
		createFile(cvrbin, fs.getPath(name), dirPerm, exePerm, bis);
		bis.close();
	    }
	}

	if (stdbin != null) {
	    for (String name: executables) {
		Path link = stdbin.resolve(fs.getPath(name));
		Path target = cvrbin.resolve(fs.getPath(name));
		Path rtarget = link.getParent().relativize(target);
		if (Files.isSymbolicLink(link)) {
		    Path tlink = Files.readSymbolicLink(link);
		    if (!tlink.equals(rtarget)) {
			System.out.println("ignoring symbolic link " + link
					   + ": does not point to "
					   + ".../bzdev/bin/" + name);
		    }
		} else {
		    createLink(link, target, true);
		}
	    }
	}
	for (String name: docfiles) {
	    InputStream is =
		ClassLoader.getSystemResourceAsStream(name);
	    if (is == null) {
		throw new Exception("could not find resource " + name);
	    }
	    InputStreamReader reader = new InputStreamReader(is, "UTF-8");
	    char[] carray = new char[1024];
	    int cnt = 0;
	    int total = 0;
	    while ((cnt = reader.read(carray, 0, carray.length)) > 0) {
		total += cnt;
	    }
	    reader.close();
	    is = ClassLoader.getSystemResourceAsStream(name);
	    reader = new InputStreamReader(is, "UTF-8");
	    carray = new char[total+1];
	    cnt = 0;
	    total = 0;
	    while ((cnt = reader.read(carray,cnt,carray.length-cnt)) > 0) {
		total += cnt;
	    }
	    reader.close();
	    String file = new String(carray, 0, total);
	    file = file.replace("JAVA", "java");
	    file = file.replace("BZDEVDIR", cvrdir.toString());
	    if (!posixMode) {
		file = file.replace("\n", System.getProperty("line.separator"));
	    }
	    byte[] barray = file.getBytes("UTF-8");
	    ByteArrayInputStream bis = new ByteArrayInputStream(barray);
	    createFile(cvrdoc, fs.getPath(name), dirPerm, filePerm, bis);
	    bis.close();
	}

	for (String name: libraries) {
		InputStream is = ClassLoader.getSystemResourceAsStream(name);
		createFile(cvrdir, fs.getPath(name), dirPerm, filePerm, is);
		is.close();
	}

	if (posixMode) {
	    if (gzipManPages) {
		for (String name: man1sGzip) {
		    InputStream is =
			ClassLoader.getSystemResourceAsStream(name);
		    createFile(cvrman1, fs.getPath(name), dirPerm, filePerm, is);
		    is.close();
		}
		if (stdman1 != null) {
		    for (String name: man1sGzip) {
			Path link = stdman1.resolve(fs.getPath(name));
			Path target = cvrman1.resolve(fs.getPath(name));
			Path rtarget = link.getParent().relativize(target);
			if (Files.isSymbolicLink(link)) {
			    Path tlink = Files.readSymbolicLink(link);
			    if (!tlink.equals(rtarget)) {
				throw new Throwable("symbolic link " + link
						    + "does not point "
						    + "to .../bzdev/man/man1/"
						    + name);
			    }
			} else {
			    createLink(link, target, true);
			}
		    }
		}
	    } else {
		for (String name: man1s) {
		    InputStream is =
			ClassLoader.getSystemResourceAsStream(name);
		    if (is == null) {
			System.err.println("no resource for name = " + name);
		    }
		    createFile(cvrman1, fs.getPath(name), dirPerm, filePerm, is);
		    is.close();
		}
		if (stdman1 != null) {
		    for (String name: man1s) {
			Path link = stdman1.resolve(fs.getPath(name));
			Path target = cvrman1.resolve(fs.getPath(name));
			Path rtarget = link.getParent().relativize(target);
			if (Files.isSymbolicLink(link)) {
			    Path tlink = Files.readSymbolicLink(link);
			    if (!tlink.equals(rtarget)) {
				System.out.println("symbolic link " + link
						   + "does not point "
						   + "to .../bzdev/man/man1/"
						   + name);
			    }
			} else {
			    createLink(link, target, true);
			}
		    }
		}
	    }
	} else {
	    for (String name: htmls) {
		InputStream is = ClassLoader.getSystemResourceAsStream(name);
		createFile(cvrdoc, fs.getPath(name), dirPerm, filePerm, is);
		is.close();
	    }
	}
	if (!dryrun) {
	    Files.createFile(tspath);
	    if (isWindows) {
		DosFileAttributeView pv =
		    Files.getFileAttributeView(tspath,
					       DosFileAttributeView.class);
		if (pv != null) {
		    pv.setHidden(true);
		}
	    } else {
		PosixFileAttributeView pv =
		    Files.getFileAttributeView(tspath,
					       PosixFileAttributeView.class);
		if (pv != null) {
		    if (owner != null) {
			pv.setOwner(owner);
		    }
		    if (group != null) {
			pv.setGroup(group);
		    }
		    pv.setPermissions(filePerm);
		}
	    }
	}
	if (dryrun && deleteRootOnFailure) {
	    // we created it and it didn't previously exist, so
	    // clean up.
	    Files.deleteIfExists(root);
	}
	if (notDryrun) {
	    if (old != null && Files.isDirectory(old)) {
		try {
		    clearDirectory(old);
		    Files.delete(old);
		} catch (IOException e) {
		    throw new Throwable("cleanup of old directory failed");
		}
	    }
	}
    }
}
