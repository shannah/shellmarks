package ca.weblite.shellmarks;


import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.controlsfx.control.textfield.CustomTextField;
import org.json.JSONObject;
import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign.MaterialDesign;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;

import java.awt.*;
import java.net.URI;


public class DocumentationAppFX extends Application {

    private static String title;
    private static String content;
    private CustomTextField searchField;
    private static RunScriptListener runScriptListener;
    private WebEngine webEngine;

    public static void launchNow(String title, String content, RunScriptListener listener) {
        DocumentationAppFX.title = title;
        DocumentationAppFX.content = content;
        DocumentationAppFX.runScriptListener = listener;
        launch(new String[0]);
    }


    private void findUpdate(WebEngine webEngine, String text) {
        StringBuilder js = new StringBuilder();
        js.append("try {window.getSelection().collapseToStart();}catch(e){};window.find(");
        js.append(JSONObject.quote(text)).append(", false, false, true)");
        webEngine.executeScript(js.toString());
    }

    private void findNext(WebEngine webEngine, String text) {
        StringBuilder js = new StringBuilder();
        js.append("window.find(");
        js.append(JSONObject.quote(text)).append(", false, false, true)");
        webEngine.executeScript(js.toString());
    }

    private void findPrevious(WebEngine webEngine, String text) {
        StringBuilder js = new StringBuilder();
        js.append("window.find(");
        js.append(JSONObject.quote(text)).append(", false, true, true)");
        webEngine.executeScript(js.toString());
    }

    private void openLinkInSystemBrowser(String url) {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception ex) {
                System.err.println("Failed to open link "+url);
                ex.printStackTrace(System.err);
            }
        } else {
            System.err.println("Failed to open link "+url+".  Not supported on this platform.");
        }
    }

    public void updateContents(String contents) {
        webEngine.loadContent(contents);
    }


    @Override
    public void start(Stage primaryStage) throws Exception {

        BorderPane borderPane = new BorderPane();


        WebView webview = new WebView();
        StackPane stackPane=new StackPane();
        stackPane.getChildren().add(webview);

        webEngine= webview.getEngine();
        webEngine.setJavaScriptEnabled(true);
        webEngine.loadContent(content);
        webEngine.getLoadWorker().stateProperty().addListener(
                new ChangeListener<Worker.State>() {
                    public void changed(@SuppressWarnings("rawtypes") ObservableValue ov, Worker.State oldState, Worker.State newState) {

                        if(newState.toString().equals("FAILED")){

                            webEngine.loadContent("<!DOCTYPE html><html><head><title>Page Title</title></head><body>"
                                    + "<h1>Error:</h1>"
                                    + "<p>Internet connection Problem.</p>" +"<button onclick=\"window.location.href='http://www.ruemerc.co.ke/apps/smsgateway/'\">Retry</button>"

                                    + "</body></html>");
                        } else if (newState == Worker.State.SUCCEEDED) {
                            EventListener listener = new EventListener() {
                                @Override
                                public void handleEvent(Event ev) {
                                    String domEventType = ev.getType();

                                    String href = ((Element) ev.getCurrentTarget()).getAttribute("href");
                                    if (href == null) return;

                                    if (href.startsWith("http://run/") || href.startsWith("https://run/")) {
                                        href = "run:" + href.substring(href.indexOf("//run/")+6);
                                        ev.preventDefault();
                                        ev.stopPropagation();
                                    }
                                    if (domEventType.equals("click")) {
                                        if (href.startsWith("http://") || href.startsWith("https://")) {

                                            ev.preventDefault();
                                            ev.stopPropagation();
                                            openLinkInSystemBrowser(href);
                                            return;

                                        }

                                        if (href.startsWith("#")) {
                                            webEngine.executeScript("document.querySelector('"+href+"').scrollIntoView()");
                                            return;
                                        }

                                        if (href.startsWith("run:")) {
                                            if (runScriptListener != null) {

                                                runScriptListener.runScript(DocumentationAppFX.this, href.substring(href.indexOf(":")+1));
                                            }
                                            return;
                                        }

                                        if (href.startsWith("edit:")) {
                                            if (runScriptListener != null) {
                                                runScriptListener.editScript(DocumentationAppFX.this, href.substring(href.indexOf(":")+1));
                                            }
                                            return;
                                        }
                                        if (href.startsWith("delete:")) {
                                            if (runScriptListener != null) {
                                                runScriptListener.deleteScript(DocumentationAppFX.this, href.substring(href.indexOf(":")+1));
                                            }
                                            return;
                                        }

                                        if (href.startsWith("clone:")) {
                                            if (runScriptListener != null) {
                                                runScriptListener.cloneScript(DocumentationAppFX.this, href.substring(href.indexOf(":")+1));
                                            }
                                            return;
                                        }
                                        if (href.toLowerCase().startsWith("editsection:")) {
                                            if (runScriptListener != null) {

                                                runScriptListener.editSection(DocumentationAppFX.this, href.substring(href.indexOf(":")+1));
                                            }
                                            return;
                                        }

                                    }
                                }
                            };

                            Document doc = webEngine.getDocument();
                            NodeList nodeList = doc.getElementsByTagName("a");
                            for (int i = 0; i < nodeList.getLength(); i++) {
                                //String href = ((Element) nodeList.item(i)).getAttribute("href");

                                ((EventTarget) nodeList.item(i)).addEventListener("click", listener, true);
                                //((EventTarget) nodeList.item(i)).addEventListener("mouseover", listener, false);
                                //((EventTarget) nodeList.item(i)).addEventListener("mouseout", listener, false);


                            }
                        }

                    }


                });



        searchField = new CustomTextField();
        Label searchIcon = new Label();
        searchIcon.setGraphic(FontIcon.of(FontAwesome.SEARCH));
        searchField.setLeft(searchIcon);
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.isEmpty()) {
                return;
            }
            findUpdate(webEngine, newValue);
        });

        ToolBar toolbar = new ToolBar();
        toolbar.getItems().add(searchField);

        Button findNext = new Button();
        findNext.setGraphic(FontIcon.of(FontAwesome.CHEVRON_RIGHT));

        findNext.setOnAction(evt->{
            System.out.println("Clicked");
            if (searchField.getText().isEmpty()) {
                return;
            }
            findNext(webEngine, searchField.getText());
        });

        Button findPrev = new Button();

        findPrev.setGraphic(FontIcon.of(FontAwesome.CHEVRON_LEFT));
        findPrev.setOnAction(evt->{
            if (searchField.getText().isEmpty()) {
                return;
            }
            findPrevious(webEngine, searchField.getText());
        });


        toolbar.getItems().add(findPrev);
        toolbar.getItems().add(findNext);

        Button refresh = new Button();
        refresh.setTooltip(new Tooltip("Refresh catalog"));
        refresh.setOnAction(evt->{
            runScriptListener.refresh(this);
        });
        refresh.setGraphic(FontIcon.of(FontAwesome.REFRESH));
        toolbar.getItems().add(refresh);






        ToolBar rightToolbar = new ToolBar();

        ContextMenu contextMenu = new ContextMenu();
        MenuItem newScript = new MenuItem("New Script");
        newScript.setGraphic(FontIcon.of(MaterialDesign.MDI_FILE_DOCUMENT));
        newScript.setOnAction(evt->{
            runScriptListener.newScript(this);
        });
        contextMenu.getItems().add(newScript);



        MenuItem installScript = new MenuItem("Import Script from File");

        installScript.setGraphic(FontIcon.of(MaterialDesign.MDI_FILE_IMPORT));
        installScript.setOnAction(evt->{
            runScriptListener.importScriptFromFileSystem(this);
        });
        contextMenu.getItems().add(installScript);
        MenuItem installScriptUrl = new MenuItem("Import Script from URL");

        installScriptUrl.setGraphic(FontIcon.of(MaterialDesign.MDI_WEB));

        installScriptUrl.setOnAction(evt->{
            runScriptListener.importScriptFromURL(this);
        });
        contextMenu.getItems().add(installScriptUrl);

        Button menu = new Button();
        menu.setGraphic(FontIcon.of(MaterialDesign.MDI_MENU));
        menu.setOnAction(evt->{
            contextMenu.show(menu, Side.BOTTOM, 0, 0 );
        });
        rightToolbar.getItems().add(menu);

        HBox toolbars = new HBox();
        HBox.setHgrow(toolbar, Priority.ALWAYS);
        HBox.setHgrow(rightToolbar, Priority.NEVER);
        toolbars.getChildren().add(toolbar);
        toolbars.getChildren().add(rightToolbar);
        borderPane.setTop(toolbars);
        Scene scene=new Scene(borderPane);
        borderPane.setCenter(stackPane);
        primaryStage.setTitle(title);
        //primaryStage.getIcons().add(new Image("images/logoedtrack.png"));

        primaryStage.setScene(scene);

        scene.setOnKeyPressed(evt -> {
            if (evt.isMetaDown() && "g".equals(evt.getText()) && evt.isShiftDown()) {
                if (searchField.getText().isEmpty()) {
                    return;
                }
                findPrevious(webEngine, searchField.getText());
            } else if (evt.isMetaDown() && "g".equals(evt.getText()) && !evt.isShiftDown()) {
                if (searchField.getText().isEmpty()) {
                    return;
                }
                findNext(webEngine, searchField.getText());
            } else if (evt.isMetaDown() && "f".equals(evt.getText())) {
                searchField.requestFocus();
                searchField.selectAll();
            }
        });

        Rectangle2D primaryScreenBounds = Screen.getPrimary().getVisualBounds();

        //set Stage boundaries to visible bounds of the main screen
        primaryStage.setX(primaryScreenBounds.getMinX());
        primaryStage.setY(primaryScreenBounds.getMinY());
        primaryStage.setWidth(primaryScreenBounds.getWidth());
        primaryStage.setHeight(primaryScreenBounds.getHeight());

        primaryStage.show();

    }
}
