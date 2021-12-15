package ca.weblite.shellmarks;

import com.alexandriasoftware.swing.Validation;
import com.alexandriasoftware.swing.VerifyingValidator;
import com.moandjiezana.toml.Toml;
import org.apache.commons.io.FileUtils;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.OptionsBuilder;
import org.asciidoctor.SafeMode;
import picocli.CommandLine;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.io.*;
import java.math.BigInteger;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Array;
import java.util.*;
import java.util.List;

@CommandLine.Command(name = "shellmarks", version = "shellmarks 1.0.4", mixinStandardHelpOptions = true)
public class Main implements Runnable {
    private Map<String,String> environment = new HashMap<>();
    private File scriptFile;
    private Form form;
    private final Object lock = new Object();
    private boolean submitted = false;

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
                    System.exit(1);
                    return;
                }
                String name = new File(u.getFile()).getName();
                if (targetName != null && !targetName.isEmpty()) {
                    name = targetName;
                }
                File dest = new File(installDir, name);
                if (dest.exists()) {
                    System.err.println("A script already exists at "+dest+".  Use -f option to force overwite");
                    System.exit(1);
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
                            System.exit(1);
                        }
                        FileUtils.moveFile(temp, dest);
                    } catch (IOException ex) {
                        System.err.println("Failed to download script from "+u);
                        ex.printStackTrace(System.err);
                        System.exit(1);
                    }
                } else {
                    try {

                        FileUtils.copyURLToFile(u, dest);
                        System.out.println("Script successfully installed at "+dest);

                    } catch (Exception ex) {
                        System.err.println("Failed to download "+u+" to "+dest);
                        ex.printStackTrace(System.err);
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
                        System.exit(0);
                    } catch (Exception ex) {
                        System.err.println("Failed to install script to "+dest);
                        ex.printStackTrace(System.err);
                        System.exit(1);
                    }
                } else {
                    System.err.println("Install failed because "+f+" does not exist");
                    System.exit(1);
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
                System.err.println("Use --help flag to see usage");
                System.exit(1);
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

        void addField(Field field) {
            if (fields == null) fields = new ArrayList<>();
            fields.add(field);
        }

        boolean hasFields() {
            return fields != null && !fields.isEmpty();
        }
    }

    private class Field {
        boolean required;
        String varName;
        String label, help;
        FieldType type;
        String defaultValue;
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
        if (pos < 0) return false;
        pos += "<shellmarks>".length();
        int lpos = scriptString.indexOf("</shellmarks>", pos);
        if (lpos < 0) {
            lpos = scriptString.length();
        }

        Toml toml = new Toml().read(scriptString.substring(pos, lpos));
        form = new Form();

        for (Map.Entry<String,Object> entry : toml.entrySet()) {
            if (entry.getKey().equalsIgnoreCase("title")) {
                form.title = (String)entry.getValue();
            } else if (entry.getKey().equalsIgnoreCase("description")) {
                form.description = (String)entry.getValue();
            } else if (entry.getValue() instanceof Toml) {
                Toml value = (Toml)entry.getValue();
                Field field = new Field();
                field.varName = entry.getKey();
                field.label = value.getString("label", field.varName);
                field.help = value.getString("help", null);
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
            if (form.description.startsWith("<html>")) {
                isHtml = true;
            } else if (form.description.startsWith("<asciidoc>")) {
                isHtml = true;
                int startPos = "<asciidoc>".length();
                int endPos = form.description.indexOf("</asciidoc>", startPos);
                if (endPos < 0) {
                    endPos = form.description.length();
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
        });

        out.add(center(submit));

        return out;
    }

    private JPanel center(JComponent wrapped) {
        JPanel out = new JPanel();
        out.setLayout(new FlowLayout(FlowLayout.CENTER));
        out.add(wrapped);
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
                return buildTextField(field);

            case Number:
                return buildNumberField(field);
            case CheckBox:
                return buildCheckboxField(field);
            case Date:
                return buildTextField(field);


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
        installValidation(pathField, field);
        pathField.setColumns(30);
        pathField.putClientProperty(FIELD_KEY, field);
        if (field.defaultValue != null) {
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

    private JComponent buildNumberField(Field field) {
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
        if (field.defaultValue != null) {
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
        panel.add(pathField);

        return panel;
    }

    private JComponent buildCheckboxField(Field field) {

        JCheckBox out =  new JCheckBox(field.label);
        if (("true".equalsIgnoreCase(field.defaultValue) || "1".equals(field.defaultValue) || "on".equalsIgnoreCase(field.defaultValue) || "checked".equalsIgnoreCase(field.defaultValue) || "yes".equalsIgnoreCase(field.defaultValue))) {
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
        if (field.defaultValue != null) {
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
        System.exit(exitCode);
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
        if (!file.exists()) {
            throw new IOException("File not found "+file);

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
                f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                if (form.title != null) {
                    f.setTitle(form.title);
                }
                f.getContentPane().setLayout(new BorderLayout());
                f.getContentPane().add(ui, BorderLayout.CENTER);


                f.pack();
                f.setVisible(true);

            });
            while (!submitted) {
                synchronized (lock) {
                    lock.wait();
                }
            }
        }
        runScript(readToString(new FileInputStream(scriptFile)));


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
}
