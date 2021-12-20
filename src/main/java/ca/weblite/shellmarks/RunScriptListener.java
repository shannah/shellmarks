package ca.weblite.shellmarks;

public interface RunScriptListener {

    public void runScript(DocumentationAppFX app, String name);
    public void editScript(DocumentationAppFX app, String name);
    public void deleteScript(DocumentationAppFX app, String name);
    public void cloneScript(DocumentationAppFX app, String name);
    public void refresh(DocumentationAppFX app);
    public void newScript(DocumentationAppFX app);
    public void importScriptFromFileSystem(DocumentationAppFX app);
    public void importScriptFromURL(DocumentationAppFX app);
    public void editSection(DocumentationAppFX app, String sectionName);
    public void newSection(DocumentationAppFX app);

}
