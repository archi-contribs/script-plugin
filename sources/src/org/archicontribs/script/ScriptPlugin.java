package org.archicontribs.script;

import org.eclipse.ui.plugin.AbstractUIPlugin;

public class ScriptPlugin extends AbstractUIPlugin {
    public static final String PLUGIN_ID = "org.archicontribs.script"; //$NON-NLS-1$

    /**
     * The shared instance
     */
    public static ScriptPlugin INSTANCE;

    public ScriptPlugin() {
        INSTANCE = this;
        System.out.println("Script plugin activation ...");
    }
}
