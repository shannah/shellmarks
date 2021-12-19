package ca.weblite.shellmarks;

import com.alexandriasoftware.swing.Validation;
import com.alexandriasoftware.swing.VerifyingValidator;
import com.moandjiezana.toml.Toml;
import javafx.application.Platform;
import jnr.ffi.Struct;
import org.apache.commons.io.FileUtils;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.OptionsBuilder;
import org.asciidoctor.SafeMode;
import picocli.CommandLine;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.List;

@CommandLine.Command(name = "shellmarks", version = "shellmarks 1.0.4", mixinStandardHelpOptions = true)
public class Main implements Runnable {
    private Map<String,String> environment = new HashMap<>();
    private File scriptFile;
    private Form form;
    private final Object lock = new Object();
    private boolean submitted = false;
    private boolean cancelled = false;
    private static boolean doNotExit;

    @CommandLine.Option(names = {"-i", "--install"}, description = "Install scripts")
    private boolean installScript;

    @CommandLine.Option(names = {"--as"}, description = "Alias used for the installed script")
    private String targetName;

    @CommandLine.Option(names = {"-f", "--force"}, description = "Force overwite already installed script")
    private boolean forceOverwrite;

    @CommandLine.Option(names = {"-l", "--list"}, description = "Print a list of installed scripts")
    private boolean listScripts;

    @CommandLine.Option(names = {"--hash"}, description = "SHA1 hash onf install script contents to verify that script is not tampered with.")
    private String hash;

    @CommandLine.Option(names = {"-e", "--edit"}, description = "Edit the provided scripts in default text editor app")
    private boolean edit;


    @CommandLine.Parameters(paramLabel = "<script>", description = "Shell scripts to be run")
    private String[] files;




    private File findScript(String name) {
        String includePaths = System.getenv("SHELLMARKS_PATH");
        if (includePaths == null) {
            includePaths = System.getProperty("user.home") + File.separator + ".shellmarks" + File.separator + "scripts";
        }
        String[] includePathsArr = includePaths.split(File.pathSeparator);
        for (String p : includePathsArr) {
            File f = new File(p + File.separator + name);
            if (f.exists()) return f;
        }
        return null;
    }

    private File[] getScriptPaths() {
        String includePaths = System.getenv("SHELLMARKS_PATH");
        if (includePaths == null) {
            includePaths = System.getProperty("user.home") + File.separator + ".shellmarks" + File.separator + "scripts";
        }
        String[] includePathsArr = includePaths.split(File.pathSeparator);
        List<File> includePathFiles = new ArrayList<File>();
        int index = 0;
        for (String p : includePathsArr) {
            File f = new File(p);
            if (f.exists()) includePathFiles.add(f);
        }
        return includePathFiles.toArray(new File[includePathFiles.size()]);
    }

    public static String sha1(String input)
    {
        try {
            // getInstance() method is called with algorithm SHA-1
            MessageDigest md = MessageDigest.getInstance("SHA-1");

            // digest() method is called
            // to calculate message digest of the input string
            // returned as array of byte
            byte[] messageDigest = md.digest(input.getBytes());

            // Convert byte array into signum representation
            BigInteger no = new BigInteger(1, messageDigest);

            // Convert message digest into hex value
            String hashtext = no.toString(16);

            // Add preceding 0s to make it 32 bit
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }

            // return the HashText
            return hashtext;
        }

        // For specifying wrong message digest algorithms
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }


    private void runInstall() {
        runInstall((URL)null, (File)null);
    }

    private void runInstall(URL installUrl, File installFile) {
        if (installUrl != null) {
            files = new String[]{installUrl.toString()};
        } else if (installFile != null) {
            files = new String[]{installFile.getAbsolutePath()};
        }

        if (files == null || files.length == 0) {
            System.err.println("Use --help flag to see usage");
            System.exit(1);
        }
        File installDir = getScriptPaths().length > 0 ? getScriptPaths()[0] : null;
        if (installDir == null) {
            installDir = new File(System.getProperty("user.home") + File.separator + ".shellmarks" + File.separator + "scripts");

        }
        installDir.mkdirs();
        for (String arg : files) {
            if (arg.startsWith("http:") || arg.startsWith("https")) {
                URL u;

                try {
                    u = new URL(arg);
                } catch (Exception ex) {
                    System.err.println("Failed to parse URL "+arg+".");
                    ex.printStackTrace(System.err);
                    if (!doNotExit) {
                        System.exit(1);
                    }
                    if (installUrl != null) {
                        EventQueue.invokeLater(()->{
                            JOptionPane.showMessageDialog((Component)null, "Failed to parse URL: "+ex.getMessage(), "Failed to parse URL", JOptionPane.ERROR_MESSAGE);

                        });
                    }
                    return;
                }
                String name = new File(u.getFile()).getName();
                if (targetName != null && !targetName.isEmpty()) {
                    name = targetName;
                }
                File dest = new File(installDir, name);
                if (dest.exists()) {
                    System.err.println("A script already exists at "+dest+".  Use -f option to force overwite");
                    if (!doNotExit) {
                        System.exit(1);
                    }
                    if (installUrl != null) {
                        EventQueue.invokeLater(()->{
                            JOptionPane.showMessageDialog((Component)null, "A script with this name is already installed.", "Import Failed", JOptionPane.ERROR_MESSAGE);

                        });
                    }
                    return;
                }
                if (hash != null && !hash.isEmpty()) {
                    try {
                        File temp = File.createTempFile(dest.getName(), ".tmp");
                        temp.deleteOnExit();
                        FileUtils.copyURLToFile(u, temp);
                        String scriptContent = FileUtils.readFileToString(temp, "UTF-8");
                        String scriptHash = sha1(scriptContent);
                        if (!scriptHash.equals(hash)) {
                            System.err.println("SHA-1 Hash for script at "+u+" did not match the provided hash.  Found "+scriptHash+" but expected "+hash);
                            if (!doNotExit) {
                                System.exit(1);
                            }
                        }
                        FileUtils.moveFile(temp, dest);
                        if (installUrl != null) {
                            EventQueue.invokeLater(()->{
                                JOptionPane.showMessageDialog((Component)null, "The script was installed sucessfully");
                            });
                        }
                    } catch (IOException ex) {
                        System.err.println("Failed to download script from "+u);

                        ex.printStackTrace(System.err);
                        if (!doNotExit) {
                            System.exit(1);
                        }
                    }
                } else {
                    try {

                        FileUtils.copyURLToFile(u, dest);
                        System.out.println("Script successfully installed at "+dest);
                        if (installUrl != null) {
                            EventQueue.invokeLater(()->{
                                JOptionPane.showMessageDialog((Component)null, "Script successfully installed at "+dest);
                            });
                        }

                    } catch (Exception ex) {
                        System.err.println("Failed to download "+u+" to "+dest);
                        ex.printStackTrace(System.err);
                        if (installUrl != null) {
                            EventQueue.invokeLater(()->{
                                JOptionPane.showMessageDialog((Component)null, "Failed to download "+u+" to "+dest, "Failed", JOptionPane.ERROR_MESSAGE);
                            });
                        }
                    }
                }

            } else {

                File f = new File(arg);
                String name = f.getName();
                if (targetName != null && !targetName.isEmpty()) {
                    name = targetName;
                }
                File dest = new File(installDir, name);
                if (f.exists()) {
                    try {
                        FileUtils.copyFile(f, dest);
                        System.out.println("Successfully installed script at "+dest);
                        if (installFile != null) {
                            EventQueue.invokeLater(()->{
                                JOptionPane.showMessageDialog((Component)null, "Script successfully installed at "+dest);
                            });
                        }
                        if (!doNotExit) {
                            System.exit(0);
                        }
                    } catch (Exception ex) {
                        System.err.println("Failed to install script to "+dest);
                        ex.printStackTrace(System.err);
                        if (!doNotExit) {
                            System.exit(1);
                        }
                        if (installFile != null) {
                            EventQueue.invokeLater(()->{
                                JOptionPane.showMessageDialog((Component)null, "Failed to install script to "+dest, "Failed", JOptionPane.ERROR_MESSAGE);
                            });
                        }
                    }
                } else {
                    System.err.println("Install failed because "+f+" does not exist");
                    if (!doNotExit) {
                        System.exit(1);
                    }
                    if (installFile != null) {
                        EventQueue.invokeLater(()->{
                            JOptionPane.showMessageDialog((Component)null, "Install failed because "+f+" does not exist", "Failed", JOptionPane.ERROR_MESSAGE);
                        });
                    }
                }
            }
        }
    }

    private void runEdit() {
        if (files == null || files.length == 0) {
            System.err.println("Usage: shellmarks --edit scriptname");
            System.exit(1);
            return;
        }
        File scriptFile = findScript(files[0]);
        if (scriptFile == null) {
            System.err.println("Cannot find script named "+files[0]);
            System.exit(1);
            return;
        }
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().edit(scriptFile);
                try {
                    Thread.sleep(2000);

                } catch (Exception ex){}
                System.exit(0);
            } catch (Exception ex) {
                System.err.println("Failed to edit "+scriptFile);
                ex.printStackTrace(System.err);
                System.exit(1);
            }
        } else {
            System.err.println("Editing not supported on this platform");
            System.err.println("Script file located at: ");
            System.err.println(scriptFile.getAbsolutePath());
            System.exit(1);
        }
    }

    private void runList() {
        List<String> names = new ArrayList<>();
        for (File dir : getScriptPaths()) {
            for (File f : dir.listFiles()) {
                names.add(f.getName());
            }
        }
        Collections.sort(names);
        for (String name : names) {
            System.out.println(name);
        }
    }


    private void startConsoleListener() {
        Thread t = new Thread(()->{
            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.trim().isEmpty()) continue;
                try {
                    Process p = Runtime.getRuntime().exec(line);
                    InputStream inputStream = p.getInputStream();
                    Thread inputThread = new Thread(()->{
                        Scanner inputScanner = new Scanner(inputStream);
                        while (inputScanner.hasNextLine()) {
                            System.out.println(inputScanner.nextLine());
                        }

                    });
                    inputThread.start();

                } catch (Exception ex) {
                    ex.printStackTrace(System.err);
                }

            }
        });
        t.start();
    }

    private void runDocs() {
        doNotExit = true;
        System.out.println("Generating Documentation.  Please wait...");
        if (useJavaFX) {
            try {
                startConsoleListener();
                showDocs();

            } catch (Exception ex) {
                System.err.println("Failed to show docs");
                ex.printStackTrace(System.err);
            }
        } else {
            EventQueue.invokeLater(() -> {
                try {
                    showDocs();

                } catch (Exception ex) {
                    System.err.println("Failed to show docs");
                    ex.printStackTrace(System.err);
                }
            });
        }

    }

    @Override
    public void run() {
        if (edit) {
            runEdit();
        } if (installScript) {
            runInstall();
        } else if (listScripts) {
            runList();
        } else {
            if (files == null || files.length == 0) {
                runDocs();
                return;
            }
            for (String arg : files) {
                File f = new File(arg);
                if (!f.exists()) {
                    f = findScript(arg);
                }
                if (f.exists()) {
                    try {
                        Main main = new Main();
                        main.run(f);
                    } catch (Exception ex) {
                        System.err.println("Failed to run "+f);
                        ex.printStackTrace();
                        System.exit(1);
                    }
                } else {
                    System.err.println("Cannot find file ["+f+"]");
                    System.exit(1);
                }

            }
        }
    }

    private class Form {
        List<Field> fields;
        String title;
        String description;
        String docString;

        // A conceptual path to where this script should be bookmarked.
        // Like a file path, but used for documentation.
        String categoryPath;
        Set<String> tags = new HashSet<String>();

        void addField(Field field) {
            if (fields == null) fields = new ArrayList<>();
            fields.add(field);
        }

        boolean hasFields() {
            return fields != null && !fields.isEmpty();
        }

        public void setTags(String value) {
            String[] parts = value.split(" ");
            tags.clear();
            for (String part : parts) {
                if (part.isEmpty()) continue;
                if (part.charAt(0) == '#') {
                    tags.add(part.substring(1).toLowerCase());
                } else {
                    tags.add(part.toLowerCase());
                }
            }
        }


    }

    private class Field {
        boolean required;
        String varName;
        String label, help;
        FieldType type;
        String defaultValue;
        int order;
    }

    private enum FieldType {
        File,
        Text,
        Number,
        Date,
        CheckBox
    }

    private boolean parseUI2(String scriptString) {
        int pos = scriptString.indexOf("<shellmarks>");
        String tomlString;
        if (pos < 0) {
            StringBuilder tomlStringBuilder = new StringBuilder();
            boolean inHeadMatter = true;
            Scanner scanner = new Scanner(scriptString);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.matches("^--+$")) {
                    inHeadMatter = false;
                    continue;
                }
                if (!inHeadMatter) {
                    tomlStringBuilder.append(line).append(System.lineSeparator());
                }
            }
            tomlString = tomlStringBuilder.toString();
        } else {
            pos += "<shellmarks>".length();
            int lpos = scriptString.indexOf("</shellmarks>", pos);
            if (lpos < 0) {
                lpos = scriptString.length();
            }

            tomlString = scriptString.substring(pos, lpos);
        }

        if (tomlString.trim().isEmpty()) return false;
        List<String> fieldOrders = new ArrayList<String>();
        Scanner scanner = new Scanner(tomlString);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.trim().matches("^\\[[A-Za-z].*\\]$")) {
                fieldOrders.add(line.substring(line.indexOf("[")+1, line.lastIndexOf("]")));
            }
        }


        Toml toml = new Toml().read(tomlString);
        form = new Form();


        for (Map.Entry<String,Object> entry : toml.entrySet()) {
            if (entry.getKey().equalsIgnoreCase("__title__")) {
                form.title = (String)entry.getValue();
            } else if (entry.getKey().equalsIgnoreCase("__description__")) {
                form.description = (String) entry.getValue();
            } else if (entry.getKey().equalsIgnoreCase("__category__") ) {
                form.categoryPath = (String) entry.getValue();
            } else if (entry.getKey().equalsIgnoreCase("__tags__")) {
                form.setTags((String)entry.getValue());
            } else if (entry.getKey().equals("__doc__")) {
                form.docString = (String) entry.getValue();
            } else if (entry.getValue() instanceof Toml) {
                Toml value = (Toml)entry.getValue();
                Field field = new Field();
                field.varName = entry.getKey();
                field.varName = entry.getKey();
                field.label = value.getString("label", field.varName);
                field.help = value.getString("help", null);
                field.order = fieldOrders.indexOf(field.varName);
                String typestr = value.getString("type", "Text").toLowerCase();
                switch (typestr) {
                    case "file":
                        field.type = FieldType.File; break;
                    case "text":
                        field.type = FieldType.Text; break;
                    case "number":
                        field.type = FieldType.Number; break;
                    case "date":
                        field.type = FieldType.Date; break;
                    case "checkbox":
                        field.type = FieldType.CheckBox; break;
                    default:
                        field.type = FieldType.Text; break;
                }
                field.required = value.getBoolean("required", false);
                field.defaultValue = value.getString("default", null);
                form.addField(field);

            }

        }
        Collections.sort(form.fields, (f1, f2) -> {
            return f1.order - f2.order;
        });
        return true;
    }


    private void parseUI(String scriptString) {
        if (parseUI2(scriptString)) {
            return;
        }
        //System.out.println("Parsing UI for "+scriptString);
        Scanner scanner = new Scanner(scriptString);
        int mode = 0;
        Field currField = null;
        form = new Form();
        int lineNumber=0;
        while (scanner.hasNextLine()) {
            lineNumber++;
            String line = scanner.nextLine();
            switch (mode) {
                case 0: {
                    // Default mode
                    if (line.startsWith("#:")) {
                        mode = 1;
                        Field field = new Field();
                        String varName = line.substring(2).trim();
                        if (varName.indexOf(" ") > 0) {
                            varName = varName.substring(0, varName.indexOf(" "));
                        }
                        if (varName.indexOf("{") > 0) {
                            varName = varName.substring(0, varName.indexOf("{")).trim();
                        }
                        field.varName = varName;
                        field.label = varName;
                        field.type = FieldType.Text;
                        field.required = false;
                        if (line.indexOf("{") > 0 && line.indexOf("}") < line.indexOf("{")) {
                            currField = field;
                        } else {
                            mode = 0;
                            form.addField(field);
                        }

                    }
                    break;
                }
                case 1:
                    if (line.startsWith("#")) {
                        String param = line.substring(1).trim();
                        if (param.equalsIgnoreCase("required")) {
                            currField.required = true;
                        } else if (param.equalsIgnoreCase("file")) {
                            currField.type = FieldType.File;
                        } else if (param.equalsIgnoreCase("text")) {
                            currField.type = FieldType.Text;
                        } else if (param.equalsIgnoreCase("number")) {
                            currField.type = FieldType.Number;
                        } else if (param.equalsIgnoreCase("date")) {
                            currField.type = FieldType.Date;
                        } else if (param.indexOf(":") > 0) {
                            String key = param.substring(0, param.indexOf(":")).trim();
                            String value = param.substring(param.indexOf(":")+1).trim();
                            switch (key) {
                                case "type" : {
                                    switch (value) {
                                        case "file":
                                            currField.type = FieldType.File;
                                            break;
                                        case "text":
                                            currField.type = FieldType.Text;
                                            break;
                                        case "number":
                                            currField.type = FieldType.Number;
                                            break;
                                        case "date":
                                            currField.type = FieldType.Date;
                                            break;
                                        default:
                                            throw new RuntimeException("Unknown field type "+value+" on line "+lineNumber+" : "+line);
                                    }
                                    break;
                                }

                                case "label":
                                    currField.label = value;
                                    break;
                                case "default":
                                    currField.defaultValue = value;
                                    break;
                                case "required":
                                    currField.required = Boolean.parseBoolean(value);
                                    break;
                                default:
                                    throw new RuntimeException("Unexpected property key for field "+currField.varName+" on line "+lineNumber+": "+line);

                            }


                        } else if (param.indexOf("}") >= 0) {
                            form.addField(currField);
                            currField = null;
                            mode = 0;
                        }
                    }
            }

        }
    }

    private JPanel buildUI() {
        return buildUI(form);
    }

    private JPanel buildUI(Form form) {
        JPanel out = new JPanel();
        out.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        out.setLayout(new BoxLayout(out, BoxLayout.Y_AXIS));

        if (form.description != null) {
            boolean isHtml = false;

            if (form.description.trim().startsWith("<html>")) {
                isHtml = true;
            } else if (form.description.trim().startsWith("<asciidoc>") || form.description.trim().contains("\n")) {
                isHtml = true;
                int startPos = form.description.indexOf(">")+1;
                int endPos;
                if (startPos < 0) {
                    startPos = 0;
                    endPos = form.description.length();
                } else {
                    endPos = form.description.indexOf("</asciidoc>", startPos);
                    if (endPos < 0) {
                        endPos = form.description.length();
                    }
                }
                String asciidocContent = form.description.substring(startPos, endPos);

                Asciidoctor asciidoctor = Asciidoctor.Factory.create();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                asciidoctor.convert(asciidocContent, OptionsBuilder.options()
                        .safe(SafeMode.UNSAFE)
                        .docType("html")
                        .toStream(baos)
                        .build());
                try {
                    form.description = baos.toString("UTF-8");
                } catch (Exception ex) {
                    System.err.println("Failed to convert Asciidoc. "+ex.getMessage());
                    ex.printStackTrace(System.err);
                    System.exit(1);
                }
            }
            if (isHtml) {
                JEditorPane editorPane = new JEditorPane();
                editorPane.setOpaque(false);
                editorPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
                editorPane.setEditable(false);
                editorPane.setContentType("text/html");
                editorPane.setText(form.description);
                editorPane.addHyperlinkListener(evt -> {
                    if (evt.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {

                        if (Desktop.isDesktopSupported()) {
                            try {
                                Desktop.getDesktop().browse(evt.getURL().toURI());
                            } catch (Exception ex) {
                            }
                        }
                    }
                });

                out.add(editorPane);
            } else {
                JTextArea textArea = new JTextArea();
                textArea.setEditable(false);
                textArea.setText(form.description);
                textArea.setOpaque(false);
                textArea.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));

                out.add(textArea);
            }

        }


        for (Field field : form.fields) {
            out.add(buildUI(field));
        }

        JButton submit = new JButton("Run");
        submit.addActionListener(evt -> {
            try {
                validateForm(out);
            } catch (ValidationFailure ex) {
                JOptionPane.showMessageDialog(out, ex.getMessage(), "Validation Failure", JOptionPane.ERROR_MESSAGE);
                return;
            }

            synchronized (lock) {
                submitted = true;
                lock.notifyAll();
            }
            try {
                JFrame top = (JFrame) submit.getTopLevelAncestor();
                top.dispose();
            } catch (Exception ex) {
                System.err.println("Problem getting top level ancestor of submit button");
                ex.printStackTrace(System.err);
            }
        });

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(evt->{
            synchronized (lock) {
                cancelled = true;
                lock.notifyAll();
            }
            try {
                JFrame top = (JFrame) submit.getTopLevelAncestor();
                top.dispose();
            } catch (Exception ex) {
                System.err.println("Problem getting top level ancestor of submit button");
                ex.printStackTrace(System.err);
            }
        });


        out.add(center(submit, cancel));

        return out;
    }

    private JPanel center(JComponent... wrapped) {
        JPanel out = new JPanel();
        out.setLayout(new FlowLayout(FlowLayout.CENTER));
        for (JComponent c : wrapped) {
            out.add(c);
        }
        return out;
    }

    private void runScriptFile() throws Exception {
        runScript(readToString(new FileInputStream(scriptFile)));
    }

    private JComponent buildUI(Field field) {
        switch (field.type) {
            case File:
                return buildFileField(field);

            case Text:
            case Number:
            case Date:
                return buildTextField(field);

            case CheckBox:
                return buildCheckboxField(field);



        }
        throw new RuntimeException("No registered builder for field "+field.type);

    }

    private static final String FIELD_KEY = "Shellmarks.Field";


    private void installValidation(JTextField pathField, Field field) {
        if (field.required) {
            pathField.setInputVerifier(new InputVerifier() {
                @Override
                public boolean verify(JComponent input) {
                    return !pathField.getText().trim().isEmpty();
                }
            });
            pathField.setInputVerifier(new VerifyingValidator(pathField,
                    pathField.getInputVerifier(),
                    new Validation(Validation.Type.DANGER, "Too short")));
        }
    }

    private JComponent buildFileField(Field field) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel label = new JLabel(field.label);

        JPanel labelWrapper = new JPanel();

        labelWrapper.add(label);
        labelWrapper.setLayout(new FlowLayout(FlowLayout.LEFT));
        panel.add(labelWrapper);

        JTextField pathField = new JTextField();
        if (field.help != null) {
            pathField.setToolTipText(field.help);
            label.setToolTipText(field.help);
        }
        pathField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                environment.put(field.varName, pathField.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                environment.put(field.varName, pathField.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                environment.put(field.varName, pathField.getText());
            }
        });
        installValidation(pathField, field);
        pathField.setColumns(30);
        pathField.putClientProperty(FIELD_KEY, field);
        if (environment.containsKey(field.varName)) {
            pathField.setText(environment.get(field.varName));
        } else if (field.defaultValue != null) {
            pathField.setText(field.defaultValue);
            environment.put(field.varName, field.defaultValue);
        }
        pathField.addActionListener(evt->{
            environment.put(field.varName, pathField.getText());
        });


        JButton browseButton = new JButton("...");
        browseButton.addActionListener(evt->{
            FileDialog dialog = new FileDialog((Frame)null, "Select file", FileDialog.LOAD);
            if (!pathField.getText().isEmpty()) {
                dialog.setFile(pathField.getText());
            }
            dialog.setVisible(true);
            for (File f : dialog.getFiles()) {
                pathField.setText(f.getAbsolutePath());
                environment.put(field.varName, pathField.getText());
            }
        });


        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BorderLayout());
        wrapper.add(pathField, BorderLayout.CENTER);
        wrapper.add(browseButton, BorderLayout.EAST);

        panel.add(wrapper);

        return panel;
    }



    private JComponent buildCheckboxField(Field field) {

        JCheckBox out =  new JCheckBox(field.label);
        if (environment.containsKey(field.varName)) {
            String v = environment.get(field.varName).toLowerCase();
            out.setSelected("true".equals(v) || "1".equals(v) || "on".equals(v) || "checked".equals(v) || "yes".equals(v));
        } else if (("true".equalsIgnoreCase(field.defaultValue) || "1".equals(field.defaultValue) || "on".equalsIgnoreCase(field.defaultValue) || "checked".equalsIgnoreCase(field.defaultValue) || "yes".equalsIgnoreCase(field.defaultValue))) {
            out.setSelected(true);
            environment.put(field.varName, "1");
        }
        if (field.help != null) {
            out.setToolTipText(field.help);
        }
        out.putClientProperty(FIELD_KEY, field);
        out.addActionListener(evt -> {
            if (out.isSelected()) {
                environment.put(field.varName, "1");
            } else {
                environment.remove(field.varName);
            }
        });


        JPanel wrapper = new JPanel();
        wrapper.setLayout(new FlowLayout(FlowLayout.LEFT));
        wrapper.add(out);
        return wrapper;

    }

    private JComponent buildTextField(Field field) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel label = new JLabel(field.label);
        JPanel labelWrapper = new JPanel();
        labelWrapper.setLayout(new FlowLayout(FlowLayout.LEFT));
        labelWrapper.add(label);
        panel.add(labelWrapper);



        JTextField pathField = new JTextField();
        installValidation(pathField, field);
        pathField.putClientProperty(FIELD_KEY, field);
        if (environment.containsKey(field.varName)) {
            pathField.setText(environment.get(field.varName));
        } else if (field.defaultValue != null) {
            pathField.setText(field.defaultValue);
            environment.put(field.varName, field.defaultValue);
        }
        if (field.help != null) {
            pathField.setToolTipText(field.help);
            label.setToolTipText(field.help);
        }
        pathField.addActionListener(evt->{

            environment.put(field.varName, pathField.getText());

        });
        pathField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                environment.put(field.varName, pathField.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                environment.put(field.varName, pathField.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                environment.put(field.varName, pathField.getText());
            }
        });
        panel.add(pathField);

        return panel;
    }

    private JComponent findComponentForField(JComponent root, Field field) {
        if (root.getClientProperty(FIELD_KEY) == field) return root;
        int len = root.getComponentCount();
        for (int i=0; i<len; i++) {
            JComponent child  = (JComponent)root.getComponent(i);
            JComponent match = findComponentForField(child, field);
            if (match != null) {
                return match;
            }

        }
        return null;
    }

    private class ValidationFailure extends Exception {
        private Field field;

        ValidationFailure(String message, Field field) {
            super(message);
            this.field = field;
        }
    }

    private void validateField(JComponent root, Field field) throws ValidationFailure {
        JComponent cmp = findComponentForField(root, field);
        if (cmp == null) {
            throw new ValidationFailure("Field "+field.varName+" not found in form.", field);
        }
        if (field.required) {
            if (cmp instanceof JTextComponent) {
                JTextComponent textComponent = (JTextComponent) cmp;
                if (textComponent.getText().isEmpty()) {
                    throw new ValidationFailure("Field "+field.varName+" is required", field);
                }
            }
        }

    }

    private void validateForm(JComponent root) throws ValidationFailure {
        if (form.fields != null) {
            for (Field field : form.fields) {
                validateField(root, field);
            }
        }
    }



    public static void main(String[] args) {
	// write your code here
        int exitCode = new CommandLine(new Main()).execute(args); // |7|

        if (!doNotExit) System.exit(exitCode);
    }

    private JMenuBar buildMenuBar(JFrame parent, File file) {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        if (form.title != null) {
            System.setProperty("com.apple.mrj.application.apple.menu.about.name", form.title);
        }
        JMenuBar out = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem edit = new JMenuItem("Edit Script");
        edit.addActionListener(evt->{
            if (Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().edit(file);

                } catch (Exception ex) {
                    System.err.println("Failed to open file for editing");
                    ex.printStackTrace(System.err);
                    JOptionPane.showMessageDialog(parent, "Failed to open file for editing.  "+ex.getMessage(), "Failed", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(parent, "Not supported", "Editing not supported on this platform", JOptionPane.ERROR_MESSAGE);
            }
        });
        fileMenu.add(edit);
        out.add(fileMenu);
        return out;
    }


    private void run(File file) throws IOException, InterruptedException {
        run(file, new HashMap<String,String>());
    }

    private void run(File file, Map<String,String> env) throws IOException, InterruptedException {
        if (!file.exists()) {
            throw new IOException("File not found "+file);

        }
        if (env != null) {
            environment.putAll(env);
        }
        this.scriptFile = file;
        //System.out.println("Running script: "+readToString(new FileInputStream(scriptFile)));
        parseUI(readToString(new FileInputStream(scriptFile)));
        if (form.hasFields()) {
            EventQueue.invokeLater(()->{
                JPanel ui = buildUI();
                JFrame f = new JFrame("Run Script");
                f.setJMenuBar(buildMenuBar(f, file));
                f.setLocationRelativeTo(null);
                if (doNotExit) {
                    f.addWindowListener(new WindowListener() {


                        @Override
                        public void windowOpened(WindowEvent e) {

                        }

                        @Override
                        public void windowClosing(WindowEvent e) {

                        }

                        @Override
                        public void windowClosed(WindowEvent e) {
                            if (!submitted) {
                                synchronized (lock) {
                                    cancelled = true;
                                    lock.notifyAll();
                                }
                            }
                        }

                        @Override
                        public void windowIconified(WindowEvent e) {

                        }

                        @Override
                        public void windowDeiconified(WindowEvent e) {

                        }

                        @Override
                        public void windowActivated(WindowEvent e) {

                        }

                        @Override
                        public void windowDeactivated(WindowEvent e) {

                        }
                    });

                    f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                } else {
                    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                }
                if (form.title != null) {
                    f.setTitle(form.title);
                }
                f.getContentPane().setLayout(new BorderLayout());
                f.getContentPane().add(ui, BorderLayout.CENTER);


                f.pack();
                f.setVisible(true);

            });
            while (!submitted && !cancelled) {
                synchronized (lock) {
                    lock.wait();
                }
            }
        }
        if (!cancelled) {
            runScript(readToString(new FileInputStream(scriptFile)));
        }


    }


    private void runScriptByName(String name) throws IOException, InterruptedException {
        File f = findScript(name);
        if (f == null) {
            throw new IOException("Cannot find script "+name);
        }
        runScript(FileUtils.readFileToString(f, "UTF-8"));
    }

    private void runScript(String scriptString) throws IOException, InterruptedException {
        if (!scriptString.startsWith("#!")) {
            throw new IOException("Script doesn't start with #!");
        }

        String executable = scriptString.substring(2, scriptString.indexOf("\n")).trim();
        File executableFile = new File(executable);
        if (!executableFile.exists()) {
            throw new IOException("Cannot find executable "+executable);
        }

        ProcessBuilder pb = new ProcessBuilder(executable, scriptFile.getAbsolutePath())
                .inheritIO();

        pb.environment().putAll(environment);
        int result = pb.start().waitFor();
        if (result != 0) {
            throw new RuntimeException("Failed with exit code "+result);
        }


    }

    private String readToString(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[12400];
        int count;
        while ((count = inputStream.read(buffer)) > -1) {
            baos.write(buffer, 0, count);
        }
        return baos.toString("UTF-8");

    }

    private File[] getAllScriptFiles() {
        List<File> out = new ArrayList<File>();
        for (File dir : getScriptPaths()) {
            for (File file : dir.listFiles()) {
                if (!file.getName().startsWith(".") && !file.getName().endsWith(".adoc") && !file.getName().endsWith(".asciidoc")) {
                    out.add(file);
                }
            }

        }
        return out.toArray(new File[out.size()]);
    }

    private File[] getAllSectionFiles() {
        List<File> out = new ArrayList<File>();
        for (File dir : getScriptPaths()) {
            for (File file : dir.listFiles()) {
                if (!file.getName().startsWith(".") && (file.getName().endsWith(".adoc") || file.getName().endsWith(".asciidoc"))) {
                    out.add(file);
                }
            }

        }
        return out.toArray(new File[out.size()]);
    }

    private Script[] loadAllScripts() throws IOException {
        List<Script> out = new ArrayList<Script>();
        for (File f : getAllScriptFiles()) {
            Script script = new Script();
            script.load(f);
            out.add(script);
        }
        return out.toArray(new Script[out.size()]);
    }

    private ScriptCategory loadAllScriptCategories() throws IOException {
        List<ScriptCategory> out = new ArrayList<ScriptCategory>();
        Map<String,ScriptCategory> categoryMap = new HashMap<String,ScriptCategory>();

        ScriptCategory root = new ScriptCategory("");
        out.add(root);
        categoryMap.put("", root);
        for (File f : getAllSectionFiles()) {
            ScriptCategory cat = new ScriptCategory();
            cat.load(f);
            categoryMap.put(cat.name, cat);
            out.add(cat);
        }

        for (ScriptCategory cat : out) {
            if (cat == root) continue;
            if (cat.parentName != null && !categoryMap.containsKey(cat.parentName)) {
                // A parent category is referenced but it doesn't have
                // an explicit asciidoc file
                ScriptCategory parentCategory = new ScriptCategory(cat.parentName);
                parentCategory.add(cat);
                categoryMap.put(parentCategory.name, parentCategory);
                out.add(parentCategory);
            } else if (cat.parentName != null) {
                ScriptCategory parentCategory = categoryMap.get(cat.parentName);
                parentCategory.add(cat);
            } else {
                root.add(cat);
            }
        }

        Script[] allScripts = loadAllScripts();
        for (Script script : allScripts) {
            if (script.getTags().isEmpty()) {
                root.add(script);
            } else {

                for (String tag : script.getTags()) {
                    if (!categoryMap.containsKey(tag)) {
                        ScriptCategory cat = new ScriptCategory(tag);
                        categoryMap.put(tag, cat);
                        out.add(cat);
                        root.add(cat);
                    }
                    categoryMap.get(tag).add(script);
                }
            }

        }

        out.sort((cat1, cat2) -> {
            return cat1.name.compareTo(cat2.name);
        });

        return root;
    }

    private class Script {
        File file;
        String contents;
        Form form;

        private void load(File file) throws IOException {
            this.file = file;
            this.contents = FileUtils.readFileToString(file, "UTF-8");
            parseUI(contents);
            Script.this.form = Main.this.form;
        }

        private String getCategoryPath() {
            if (form.categoryPath == null) {
                return "";
            }
            return form.categoryPath;
        }

        private String getTitle() {
            if (form != null && form.title != null) return form.title;
            return file.getName();
        }

        Set<String> getTags() {
            return form.tags;
        }

        private String _getDocString() {
            if (form != null && form.docString != null) return form.docString;
            if (form != null && form.description != null) return form.description;
            return "";
        }

        private String getDocString() {
            String sep = System.lineSeparator();
            String str = _getDocString();
            if (str.startsWith("<asciidoc>")) {
                int endPos = str.indexOf("</asciidoc>");
                if (endPos >= 0) {
                    return str.substring("<asciidoc>".length(), endPos);
                } else {
                    return str.substring("<asciidoc>".length());
                }
            } else if (str.startsWith("<html>")) {
                int endPos = str.indexOf("</html>");
                if (endPos >= 0) {
                    return "++++"+sep+"<br>"+str.substring("<html>".length(), endPos)+sep+"++++"+sep;
                } else {
                    return "++++"+sep+"<br>"+str.substring("<html>".length())+sep+"++++"+sep;
                }
            } else {
                return str;
            }
        }
    }

    private static String prefixAsciidocHeadings(String content, int minHeadingLevel) {
        int currMinLevel = -1;
        Scanner scanner = new Scanner(content);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.matches("^=+ [A-Za-z0-9].*$")) {
                int headingLevel = line.indexOf(" ");
                if (currMinLevel < 0 || currMinLevel > headingLevel) {
                    currMinLevel = headingLevel;
                }
            }
        }

        if (currMinLevel > 0 && currMinLevel < minHeadingLevel) {
            int levelsToAdd = minHeadingLevel - currMinLevel;
            StringBuilder out = new StringBuilder();
            scanner = new Scanner(content);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.matches("^=+ [A-Za-z0-9].*$")) {
                    for (int i=0; i<levelsToAdd; i++) {
                        out.append("=");
                    }
                }
                out.append(line).append(System.lineSeparator());
            }
            return out.toString();
        }
        return content;

    }

    private class ScriptCategory {
        private String parentName;
        private ScriptCategory parent;
        private String name;
        private String label;
        private String description;
        private LinkedHashMap<String,ScriptCategory> subCategories = new LinkedHashMap<String,ScriptCategory>();
        private List<Script> scripts = new ArrayList<Script>();


        ScriptCategory() {

        }

        ScriptCategory(String name) {
            this.name = name;
            StringBuilder labelBuilder = new StringBuilder();
            int len = name.length();
            char[] nameChars = name.toCharArray();
            for (int i=0; i<len; i++) {
                char c = nameChars[i];
                if (c == '-') {
                    if (len > i + 1) {
                        labelBuilder.append(" ").append(Character.toTitleCase(nameChars[i + 1]));
                        i++;
                    }
                } else {
                    labelBuilder.append(c);
                }
            }
            label = labelBuilder.toString();
        }

        String getLabel() {
            if (label == null) {
                if (name != null) {
                    return name;
                }
                return "";
            }
            return label;
        }

        private boolean isRoot() {
            return parent == null;
        }

        private void add(ScriptCategory subcategory) {
            subcategory.parent = this;
            subCategories.put(subcategory.name, subcategory);
            subcategory.parentName = name;
        }

        private void add(Script script) {
            scripts.add(script);
        }



        public void load(File f) throws IOException {
            name = f.getName();
            if (name.endsWith(".adoc") || name.endsWith(".asciidoc")) {
                name = name.substring(0, name.lastIndexOf("."));
            }
            StringBuilder labelBuilder = new StringBuilder();
            int len = name.length();
            char[] nameChars = name.toCharArray();
            for (int i=0; i<len; i++) {
                char c = nameChars[i];
                if (c == '-') {
                    if (len > i + 1) {
                        labelBuilder.append(" ").append(Character.toTitleCase(nameChars[i + 1]));
                        i++;
                    }
                } else {
                    labelBuilder.append(c);
                }
            }
            label = labelBuilder.toString();

            description = FileUtils.readFileToString(f, "UTF-8");
            Scanner scanner = new Scanner(description);
            StringBuilder descriptionBuilder = new StringBuilder();
            boolean firstLine = true;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (firstLine && line.trim().isEmpty()) {
                    continue;
                }
                if (firstLine && line.matches("^=+ [A-Za-z0-9].*$")) {
                    label = line.substring(line.indexOf(" ")+1).trim();
                    firstLine = false;
                } else {
                    descriptionBuilder.append(line).append(System.lineSeparator());
                }
            }

            description = descriptionBuilder.toString();





        }
    }

    private boolean useJavaFX = true;

    File findSectionFile(String name) {
        File[] files = getAllSectionFiles();
        for (File f : files) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        for (File f : files) {
            if (f.getName().equalsIgnoreCase(name+".adoc") || f.getName().equalsIgnoreCase(name+".asciidoc")) {
                return f;
            }
        }
        return null;
    }


    private static Map<String, String> parseQuerystring(String queryString) {
        Map<String, String> map = new HashMap<String, String>();
        if ((queryString == null) || (queryString.equals(""))) {
            return map;
        }
        String[] params = queryString.split("&");
        for (String param : params) {
            try {
                String[] keyValuePair = param.split("=", 2);
                String name = URLDecoder.decode(keyValuePair[0], "UTF-8");
                if (name == "") {
                    continue;
                }
                String value = keyValuePair.length > 1 ? URLDecoder.decode(
                        keyValuePair[1], "UTF-8") : "";
                map.put(name, value);
            } catch (UnsupportedEncodingException e) {
                // ignore this parameter if it can't be decoded
            }
        }
        return map;
    }




    private String newSectionTemplate(String sectionName) {
        String label = sectionName;
        StringBuilder labelBuilder = new StringBuilder();
        int len = sectionName.length();
        char[] nameChars = sectionName.toCharArray();
        for (int i=0; i<len; i++) {
            char c = nameChars[i];
            if (c == '-') {
                if (len > i + 1) {
                    labelBuilder.append(" ").append(Character.toTitleCase(nameChars[i + 1]));
                    i++;
                }
            } else {
                labelBuilder.append(c);
            }
        }
        label = labelBuilder.toString();
        StringBuilder contents = new StringBuilder();
        contents
                .append("= ").append(label).append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("This is the section description formatted as https://asciidoctor.org/docs/asciidoc-writers-guide/[Asciidoc]").append(System.lineSeparator())
                .append(System.lineSeparator()).append("Lorem ipsum, etc...").append(System.lineSeparator());
        return contents.toString();

    }

    private File getOrCreateSection(String sectionName) throws IOException {
        File sectionFile = findSectionFile(sectionName);
        if (sectionFile != null && sectionFile.exists()) {
            return sectionFile;
        }

        if (sectionFile == null) {
            sectionFile = new File(getScriptPaths()[0], sectionName+".adoc");
        }
        FileUtils.writeStringToFile(sectionFile, newSectionTemplate(sectionName), "UTF-8");
        return sectionFile;

    }




    private void showDocs() throws IOException {
        if (useJavaFX) {
            RunScriptListener listener = new RunScriptListener() {
                @Override
                public void runScript(DocumentationAppFX app, String name) {
                    Thread t = new Thread(()->{
                        try {
                            String scriptName = name;
                            Map<String,String> query;
                            if (name.contains("?")) {
                                scriptName = name.substring(0, name.indexOf("?"));
                                query = parseQuerystring(name.substring(name.indexOf("?")+1));


                            } else {
                                query = new HashMap<String,String>();
                            }
                            new Main().run(findScript(scriptName), query);
                        } catch (Exception ex) {
                            System.err.println("Script execution failed: "+ex.getMessage());
                            ex.printStackTrace(System.err);
                        }
                    });
                    t.start();
                }

                @Override
                public void editSection(DocumentationAppFX app, String sectionName) {
                    new Thread(()->{
                        File sectionFile = findSectionFile(sectionName);
                        if (sectionFile == null || !sectionFile.exists()) {
                            try {
                                sectionFile = getOrCreateSection(sectionName);
                            } catch (Exception ex) {
                                final File fSectionFile = sectionFile;
                                EventQueue.invokeLater(()->{
                                    JOptionPane.showMessageDialog((Component)null, "Failed to write file: "+ex.getMessage(), "Oops", JOptionPane.ERROR_MESSAGE);
                                    System.err.println("Failed to write section file "+fSectionFile);
                                    ex.printStackTrace(System.err);
                                });
                            }

                        }
                        if (sectionFile != null && sectionFile.exists()) {
                            if (Desktop.isDesktopSupported()) {
                                try {
                                    Desktop.getDesktop().edit(sectionFile);
                                } catch (Exception ex) {
                                    final File fSectionFile = sectionFile;
                                    EventQueue.invokeLater(()->{
                                        JOptionPane.showMessageDialog((Component)null, "Error opening file: "+ex.getMessage(), "Oops", JOptionPane.ERROR_MESSAGE);
                                        System.err.println("Failed to open section file "+fSectionFile);
                                        ex.printStackTrace(System.err);
                                    });
                                }
                            } else {
                                EventQueue.invokeLater(()->{
                                    JOptionPane.showMessageDialog((Component)null, "Sorry, this platform doesn't support opening asciidoc files for editing", "Not supported", JOptionPane.ERROR_MESSAGE);
                                });

                            }
                        }

                    }).start();
                }

                @Override
                public void editScript(DocumentationAppFX app, String name) {
                    Thread t = new Thread(()->{

                        try {
                            File script = findScript(name);
                            if (script != null && script.exists()) {
                                if (Desktop.isDesktopSupported()) {
                                    Desktop.getDesktop().edit(script);
                                } else {
                                    System.err.println("Editing not supported on this platform.");
                                }
                            }
                        } catch (Exception ex) {
                            System.err.println("Failed to open script for editing: "+ex.getMessage());
                            ex.printStackTrace(System.err);
                        }
                    });
                    t.start();
                }

                @Override
                public void deleteScript(DocumentationAppFX app, String name) {
                    Thread t = new Thread(()->{
                        int[] result = new int[1];
                        try {
                            EventQueue.invokeAndWait(() -> {
                                result[0] = JOptionPane.showConfirmDialog(null, "Are you sure you want to delete this script?");
                            });
                        } catch (Exception ex) {
                            ex.printStackTrace(System.err);
                        }
                        if (result[0] != JOptionPane.OK_OPTION) {
                            return;
                        }

                        File script = findScript(name);
                        if (script == null || !script.exists()) {
                            try {
                                EventQueue.invokeAndWait(() -> {
                                    JOptionPane.showMessageDialog((Component)null,"Could not delete this script because it could not be found","Failed", JOptionPane.ERROR_MESSAGE);
                                });
                            } catch (Exception ex) {
                                ex.printStackTrace(System.err);
                            }
                            return;
                        }

                        if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().moveToTrash(script);
                        }
                        try {
                            EventQueue.invokeAndWait(() -> {
                                JOptionPane.showMessageDialog((Component)null,"The script has been moved to the trash.  Press 'Refresh' to update shellmarks to reflect this change.", "Moved to trash", JOptionPane.INFORMATION_MESSAGE);
                            });
                        } catch (Exception ex) {
                            ex.printStackTrace(System.err);
                        }
                        return;
                    });

                    t.start();
                }

                @Override
                public void refresh(DocumentationAppFX app) {
                    EventQueue.invokeLater(()->{
                        JOptionPane opt = new JOptionPane("Please regenerating shellmarks catalog.  Please wait.", JOptionPane.INFORMATION_MESSAGE);
                        String[] newContent = new String[1];
                        Thread t = new Thread(()-> {
                            try {
                                newContent[0] = generateDocs();
                                Platform.runLater(()->{
                                    app.updateContents(newContent[0]);
                                });
                                EventQueue.invokeLater(()->{
                                    opt.setVisible(false);
                                });
                            } catch (Exception ex) {
                                opt.setVisible(false);
                                System.err.println("Failed to regenerate shellmarks catalog: "+ex.getMessage());
                                ex.printStackTrace(System.err);
                                EventQueue.invokeLater(()-> {
                                    JOptionPane.showMessageDialog(null, "Failed to regenerate catalog: "+ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                                });
                            }
                        });
                        t.start();
                    });
                }

                @Override
                public void newScript(DocumentationAppFX app) {
                    EventQueue.invokeLater(()->{
                        String name = JOptionPane.showInputDialog("Script name", "myscript.sh");
                        if (name == null) return;
                        File file = new File(getScriptPaths()[0], name);
                        if (file.exists()) {
                            JOptionPane.showMessageDialog((Component)null, "A script by this name already exists", "Cannot create script", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        String sep = System.lineSeparator();
                        String contents = "#!/bin/bash\n" +
                                "echo \"Hello ${firstName} ${lastName}\"" + sep +
                                "echo \"You selected ${selectedFile}\"" + sep +
                                "if [ ! -z \"${option1}\" ]; then " + sep +
                                "  echo \"Option1 was selected\"" + sep +
                                "fi" + System.lineSeparator() +
                                "if [ ! -z \"${option2}\" ]; then " + sep +
                                "  echo \"Option2 was selected\"" +   sep+
                                "fi" + sep +
                                "exit 0" + sep +
                                "---" + sep +
                                "# The script title" + sep +
                                "__title__=\"" + name + "\"" + sep +
                                sep +
                                "# Script description in Asciidoc format" + sep +
                                "__description__='''" + sep +
                                //"  <asciidoc>" + sep +
                                "This description will be displayed at the top of the form." + sep +
                                sep +
                                "It can be multiline and include https://example.com[Links]" + sep +
                                //"</asciidoc>" + sep +
                                "'''" + sep +
                                sep +
                                "# Doc string.  In asciidoc format.  Displayed in Shellmarks catalog" + sep +
                                "__doc__='''" + sep +
                                "This will be displayed in the shellmarks catalog. " + sep +
                                sep +
                                "You can include _asciidoc_ markup, as well as https://www.example.com[links]." + sep +
                                "'''" + sep +
                                sep +
                                "# Tags used to place script into one or more sections of the catalog" + sep +
                                "__tags__=\"#custom-tag1 #custom-tag2\"" + sep +
                                sep +
                                "[firstName]\n" +
                                "  label=\"First Name\"" + sep +
                                "  required=true" + sep +
                                sep +
                                "[lastName]" + sep +
                                "  label=\"Last Name\"" + sep +
                                sep +
                                "[selectedFile]" + sep +
                                "  label=\"Please select a file\"" + sep +
                                "  type=\"file\"" + sep +
                                sep +
                                "[option1]" + sep +
                                "  label=\"Option 1\"" + sep +
                                "  type=\"checkbox\"" + sep +
                                sep +
                                "[option2]" + sep +
                                "  label=\"Option 2\"" + sep +
                                "  type=\"checkbox\"" + sep +
                                sep; //+
                                //"</shellmarks>";
                        try {
                            FileUtils.writeStringToFile(file, contents, "UTF-8");
                        } catch (IOException ex) {
                            JOptionPane.showMessageDialog((Component)null, "Failed to create script. "+ex.getMessage(), "Cannot create script", JOptionPane.ERROR_MESSAGE);
                            ex.printStackTrace(System.err);
                            return;
                        }
                        if (Desktop.isDesktopSupported()) {
                            try {
                                Desktop.getDesktop().edit(file);
                            } catch (IOException ex) {
                                System.err.println("Failed to open script for editing");
                                ex.printStackTrace(System.err);
                            }
                        } else {
                            System.err.println("Script was created at "+file+" but was not opened for editing because this platform doesn't support it.");
                        }

                        new Thread(() -> {
                            try {
                                String html = generateDocs();
                                Platform.runLater(()->{
                                    app.updateContents(html);
                                });
                            } catch (IOException ex) {
                                System.err.println("Failed to generate catalog. "+ex.getMessage());
                                ex.printStackTrace(System.err);
                            }
                        }).start();

                    });
                }

                @Override
                public void importScriptFromFileSystem(DocumentationAppFX app) {
                    EventQueue.invokeLater(()->{
                        FileDialog fileDialog = new FileDialog((Frame)null, "Select script", FileDialog.LOAD);

                        fileDialog.setVisible(true);
                        File[] files = fileDialog.getFiles();
                        File file = null;
                        if (files == null) return;
                        for (File f : files) {
                            if (f.isDirectory() || !f.exists()) continue;
                            try {
                                String contents = FileUtils.readFileToString(f, "UTF-8");
                                if (!contents.startsWith("#!")) {
                                    continue;
                                }
                            } catch (Exception ex) {

                            }
                            file = f;
                        }

                        if (file == null) {
                            JOptionPane.showMessageDialog((Component)null, "The file you selected could not be imported.  Make sure that the file includes a hashbang (#!) on its first line.", "Invalid script", JOptionPane.ERROR_MESSAGE );
                            return;
                        }

                        File fFile = file;
                        new Thread(()->{
                            runInstall((URL)null, fFile);

                            try {
                                String newDocs = generateDocs();
                                Platform.runLater(() -> {
                                    app.updateContents(newDocs);
                                });
                            } catch (Exception ex) {
                                System.err.println("Failed to generate catalog: "+ex.getMessage());
                                ex.printStackTrace(System.err);
                            }


                        }).start();

                    });
                }

                @Override
                public void importScriptFromURL(DocumentationAppFX app) {
                    EventQueue.invokeLater(()->{
                        String url = JOptionPane.showInputDialog("Please enter the URL of the script you want to import");
                        if (url == null) return;
                        new Thread(()->{
                            try {
                                URL u = new URL(url);
                                runInstall(u, null);


                            } catch (Exception ex) {
                                EventQueue.invokeLater(()->{
                                    JOptionPane.showMessageDialog((Component)null, "Failed to parse URL. "+ex.getMessage(), "Check URL", JOptionPane.ERROR_MESSAGE);
                                    ex.printStackTrace(System.err);
                                });
                                return;
                            }
                            try {
                                String newDocs = generateDocs();
                                Platform.runLater(() -> {
                                    app.updateContents(newDocs);
                                });
                            } catch (Exception ex) {
                                System.err.println("Failed to generate catalog: "+ex.getMessage());
                                ex.printStackTrace(System.err);
                            }

                        }).start();

                    });

                }

                @Override
                public void cloneScript(DocumentationAppFX app, String name) {
                    EventQueue.invokeLater(()->{
                        String newName = JOptionPane.showInputDialog("Please enter a name for the cloned script", name+"-copy");
                        if (newName == null) {
                            return;
                        }
                        boolean validated = true;
                        String message = null;

                        if (!newName.matches("^[a-zA-Z_\\-]+$")) {
                            validated = false;
                            message = "Invalid name.  Name can only include letters, numbers, underscores, and dashes";
                        }

                        if (validated) {
                            File existing = findScript(newName);
                            if (existing != null && existing.exists()) {
                                validated = false;
                                message = "A script by that name already exists.";

                            }
                        }

                        File original = findScript(name);
                        if (validated) {
                            if (original == null || !original.exists()) {
                                validated = false;
                                message = "Cannot find original script named "+name+".  It may have been moved or deleted.";
                            }
                        }

                        if (!validated) {
                            JOptionPane.showMessageDialog((Component) null, message, "Invalid name", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        try {
                            FileUtils.copyFile(original, new File(getScriptPaths()[0], newName));
                        } catch (Exception ex) {
                            message = "Failed to copy script: "+ex.getMessage();
                            ex.printStackTrace(System.err);
                            JOptionPane.showMessageDialog((Component) null, message, "Copy failure", JOptionPane.ERROR_MESSAGE);
                            return;
                        }

                        JOptionPane opt = new JOptionPane("Please regenerating shellmarks catalog.  Please wait.", JOptionPane.INFORMATION_MESSAGE);
                        String[] newContent = new String[1];
                        Thread t = new Thread(()-> {
                            try {
                                newContent[0] = generateDocs();
                                Platform.runLater(()->{
                                    app.updateContents(newContent[0]);
                                });
                                EventQueue.invokeLater(()->{
                                    opt.setVisible(false);
                                });
                            } catch (Exception ex) {
                                opt.setVisible(false);
                                System.err.println("Failed to regenerate shellmarks catalog: "+ex.getMessage());
                                ex.printStackTrace(System.err);
                                EventQueue.invokeLater(()-> {
                                    JOptionPane.showMessageDialog(null, "Failed to regenerate catalog: "+ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                                });
                            }
                        });
                        t.start();



                    });
                }


            };

            DocumentationAppFX.launchNow("ShellMarks", generateDocs(), listener);
        } else {
            JEditorPane editor = new JEditorPane();
            editor.setEditable(false);
            editor.setContentType("text/html");
            editor.setText(generateDocs());

            JFrame frame = new JFrame();
            JScrollPane scrollPane = new JScrollPane(editor);
            frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
            frame.pack();
            frame.setLocationRelativeTo(null);


            frame.setVisible(true);
        }

    }


    private void appendScripts(StringBuilder out) {

    }

    private String generateDocs() throws IOException {
        StringBuilder out = new StringBuilder();
        String sep = System.lineSeparator();
        out.append(sep).append("= Shellmarks").append(sep)
                .append(":doctype: book").append(sep)
                .append(":encoding: utf-8").append(sep)
                .append(":lang: en").append(sep)
                .append(":toc: left").append(sep)
                .append(":docinfo: private").append(sep).append(sep);

        appendScripts(out);

        out.append("++++").append(sep)

                .append("<style>a.command {border: 1px solid gray; padding: 8px; font-family:sans-serif; color:#333333; border-radius: 3px; text-decoration:none;}" +
                        "a.command:active {background-color: #eaeaea} .section-menu {float:right; margin-top: 20px;    } div.section-menu-content {display:none; float:right; clear:right;  border: 1px solid #cccccc;" +
                        "margin-top:10px;" +
                        "background-color: #eaeaea;" +
                        "padding: 5px;" +
                        "font-family: sans-serif;" +
                        "color: black;" +
                        "border-radius: 3px;} div.section-menu-content.active {display:block} div.section-menu-content a {text-decoration: none; padding: 5px;} " +
                        "div.section-menu-content a span {padding-left: 10px;}</style>")
                .append(sep).append("++++").append(sep);
        appendToDocs(out, loadAllScriptCategories(), 0);

        out.append("++++").append(sep).append("<script>").append(sep);
        out.append(new String(Main.class.getResourceAsStream("documentation.js")
                .readAllBytes()));

        out.append(sep).append("</script>").append(sep).append("++++").append(sep);

        Asciidoctor asciidoctor = Asciidoctor.Factory.create();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        asciidoctor.convert(out.toString(), OptionsBuilder.options()
                .safe(SafeMode.UNSAFE)

                .docType("html")
                        .headerFooter(true)
                        .compact(true)
                .toStream(baos)
                .build());
        try {
            String strout =  baos.toString("UTF-8");
            FileUtils.writeStringToFile(new File("/tmp/docs.html"), strout, "UTF-8");
            return strout;
        } catch (Exception ex) {
            System.err.println("Failed to convert Asciidoc. "+ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
        return "";

    }

    private void appendToDocs(StringBuilder out, ScriptCategory category, int depth) {
        String sep = System.lineSeparator();
        if (!category.isRoot()) {
            out.append("[#").append(category.name).append("]\n");
            out.append("=");
            for (int i=0; i < depth; i++) {
                out.append("=");
            }
            out.append(" ").append(category.getLabel()).append(sep).append(sep);
        }

        if (category.description != null) {
            out.append(prefixAsciidocHeadings(category.description, depth+2)).append(sep).append(sep);
        }

        List<Script> sortedScripts = new ArrayList<Script>(category.scripts);
        sortedScripts.sort((script1, script2) -> {
            return script1.getTitle().compareTo(script2.getTitle());
        });
        for (Script script : sortedScripts) {

            out.append("==");
            for (int i=0; i < depth; i++) {
                out.append("=");
            }
            out.append(" ").append(script.getTitle()).append(sep).append(sep);
            out.append(script.getDocString()).append(sep).append(sep);
            out.append(".Script Command").append(sep).append("[source,bash]").append(sep).append("----").append(sep).append("shellmarks ").append(script.file.getName()).append(sep).append("----").append(sep);
            out.append("++++").append(sep)

                    .append("<p><a class='command' href='run:").append(script.file.getName()).append("'>Run</a>")
                    .append(" <a class='command' href='edit:").append(script.file.getName()).append("'>Edit</a>")
                    .append(" <a class='command' href='delete:").append(script.file.getName()).append("'>Delete</a>")
                    .append(" <a class='command' href='clone:").append(script.file.getName()).append("'>Clone</a>")
                    .append("</p>").append(sep);
            out.append("++++").append(sep);

        }
        List<ScriptCategory> subcategories = new ArrayList<ScriptCategory>(category.subCategories.values());
        subcategories.sort((c1, c2) -> {
            return c1.name.compareTo(c2.name);
        });
        for (ScriptCategory subcategory : subcategories) {
            appendToDocs(out, subcategory, depth+1);
        }
    }
}
