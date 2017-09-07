package org.archicontribs.script;

import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.PlatformUI;
import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.archicontribs.database.DBScript;

public class ScriptStartup implements IStartup {
    private boolean         verbose = false;
    private boolean         debug = false;
    private String          scriptFilename = null;
    private IArchimateModel currentModel = null;
    private boolean         exitArchi = false;
    private Exception       asyncException = null;
    private BufferedReader  br = null;
    private File            report = null;
    
    @Override
    public void earlyStartup() {
        Options options = new Options();
        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine = null;
        
        options.addOption(Option.builder("s").longOpt("script").desc("Script filename").hasArg().argName("script file").required().build());
        options.addOption(Option.builder("v").longOpt("verbose").desc("Prints verbose information on the standard output").build());
        options.addOption(Option.builder("d").longOpt("debug").desc("Prints debug information on the standard output").build());
        options.addOption(Option.builder("l").longOpt("logfile").desc("Specifies the log filename").hasArg().argName("log file").build());
        options.addOption(Option.builder("?").longOpt("help").desc("Shows this help message").build());
        
        //TODO: ajouter une option pour empecher les actions manuelles dans Archi pendant que le script tourne (modal window)
        
        try {
            commandLine = parser.parse(options, Platform.getApplicationArgs());
        } catch (ParseException e) {
            error("Parse error:", e);
            help(options);
            return;
        }
        
        verbose = commandLine.hasOption("v");
        debug = commandLine.hasOption("d");
        
        if (commandLine.hasOption("?")) {
            help(options);
            return;
        }
        
        scriptFilename = commandLine.getOptionValue("s");
        
        verbose("*** Script plugin initialized");
        verbose("    Verbose mode  : "+verbose);
        verbose("    Debug mode    : "+debug);
        verbose("    Script file   : "+scriptFilename);
        verbose("    DB plugin     : "+(Platform.getBundle("org.archicontribs.database")!=null));
        
        File script = new File(scriptFilename);

        

        try {
            br = new BufferedReader(new FileReader(script));

            int lineNumber = 0;
            for ( String line; (line=br.readLine()) != null; ) {
                ++lineNumber;
                
                verbose(String.format("%02d", lineNumber) + " | " + line);
                
                if ( !line.startsWith("#") ) {
                    String[] words = parseLine(line);
                    for (String s: words) debug("     \""+s+"\"");
                    
                    if ( words.length != 0 ) {
                        switch ( words[0].toUpperCase() ) {
                            case "OPTION":
                                if ( !setOption(lineNumber, words) )
                                    return;
                                break;
                                
                            case "SELECT":
                                if ( !selectModel(lineNumber, words) )
                                    return;
                                break;
                                
                            case "CLOSE":
                                if ( !closeModel(lineNumber, words) )
                                    return;
                                break;
                                
                            case "REPORT":
                                if ( !reportModel(lineNumber, words) )
                                    return;
                                break;
                                
                            default:
                                error("Line " + lineNumber + ": Unknown command.\nValid commandes are : OPTION, SELECT, REPORT, CLOSE");
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            error(scriptFilename+": not found..");
            return;
        } catch (IOException e) {
            error(scriptFilename+": " + e.getMessage());
            return;
        }

        finish();
        
        if ( exitArchi ) {
            exitArchi();
        }
    }
    
    private void help(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( "Script plugin", options );
        System.out.flush();
    }
    
    private void verbose(String msg) {
        if ( verbose ) {
            System.out.println("INFO: " + msg);
            System.out.flush();
        }
    }
    
    private void debug(String msg) {
        if ( debug ) {
            System.out.println("DEBUG: " + msg);
            System.out.flush();
        }
    }
    
    private void error(String msg) {
        error(msg, null);
    }
    
    private void error(String msg, Exception e) {
        if ( e == null )
            System.err.println("ERROR: " + msg);
        else {
            System.err.println("ERROR: " + msg + "\n" + e.getMessage());
            e.printStackTrace(System.err);
        }
        System.err.flush();
        if ( exitArchi ) {
            exitArchi();
        }
    }
    
    private String[] parseLine(String line) {
        final String QUOTES = "\"";
        final String BLANKS = " \t\"";

        String currentDelims = BLANKS;
        StringTokenizer parser = new StringTokenizer(line, currentDelims, true);

        String token = null;
        List<String> result = new ArrayList<String>();
        while ( parser.hasMoreTokens() ) {
            token = parser.nextToken(currentDelims).trim();
            if ( token.equals(QUOTES) )
                currentDelims = (currentDelims.equals(QUOTES) ? BLANKS : QUOTES);
            else
                if ( !token.isEmpty() ) result.add(token.trim());
        }
        return result.toArray(new String[0]);
      }

    
    private boolean setOption(int lineNumber, String[] words) {
        if ( !checkOptions(lineNumber, words, 3, "OPTION <option name> [ON|OFF]") )
            return false;
        
        if ( words[1].toUpperCase().equals("EXITARCHI") ) {
                switch ( words[2].toLowerCase() ) {
                    case "on" : exitArchi = true; break;
                    case "off": exitArchi = false; break;
                    default :
                        error("Line " + lineNumber + ": Syntax error: option flag can only be ON or OFF.\nSyntax: OPTION <option name> [ON|OFF]");
                        return false;
                }
                
                debug("Setting ExitArchi option to " + exitArchi);
                return true;
        }
        
        error("Line " + lineNumber + ": Unknown option.\nValid options are : ExitArchi");
        return false;
    }
    
    private boolean selectModel(int lineNumber, String[] words) {
        // the line should have 2 or 5 words
        if ( words.length != 2 && !checkOptions(lineNumber, words, 5, "SELECT <model name> [FROM DATABASE <database name>|FROM FILE <filename>]") )
            return false;
        
        String filename = (words.length==5 && words[3].toUpperCase().equals("FILE") ) ? words[3] : null;
        String dbname = (words.length==5 && words[3].toUpperCase().equals("DATABASE") ) ? words[3] : null;
        
        if ( (filename != null || dbname != null) && !words[2].toUpperCase().equals("FROM") ) {
            error("Line " + lineNumber + ": Unrecognised keyword \""+words[2]+"\".\nSyntax: SELECT <model name> [FROM DATABASE <database name>|FROM FILE <filename>]");
            return false;
        }
        
        // we check if the model is already loaded
        //    if no filename is given, we compare the name
        //    if a filename is provided, we compare the filename
        List<IArchimateModel> allModels = IEditorModelManager.INSTANCE.getModels();
        File file = null;
        if ( filename != null )
            file = new File(filename);
        for ( IArchimateModel existingModel: allModels ) {
            if ( filename == null ) {
                if ( existingModel.getName().equals(words[1]) ) {
                    currentModel = existingModel;
                    return true;
                }
            } else {
                File existingFile = existingModel.getFile();
                try {
                    if ( existingFile!=null && existingFile.getCanonicalPath().equals(file.getCanonicalPath()) ) {
                        currentModel = existingModel;
                        return true;
                    }
                } catch (IOException e) {
                    error("Line " + lineNumber + ": An exception has been raised.", e);
                    return false;
                }
            }
        }
        
        // if we're here, this means that we need to load the model from the archimate file or the database
        if ( filename==null && dbname == null ) {
            error("Line " + lineNumber + ": The model \""+words[1]+"\" has not been found.");
            return false;
        }

        switch ( words[3].toUpperCase() ) {
            case "FILE":
                File modelFile = new File(words[4]);
                if ( !modelFile.exists() ) {
                    error("Line " + lineNumber + ": file \""+words[4]+"\" does not exist.");
                    return false;
                }
                if ( !modelFile.canRead() ) {
                    error("Line " + lineNumber + ": file \""+words[4]+"\" cannot be read.");
                    return false;
                }
                
                // we open the model
                debug("opening the model");
                asyncException = null;
                Display.getDefault().syncExec(new Runnable() {
                    @Override public void run() {
                        currentModel = IEditorModelManager.INSTANCE.openModel(new File(words[4]));
                    }
                });
                break;
            case "DATABASE":
                // we check that the database plugin bundle is loaded
                if ( Platform.getBundle("org.archicontribs.database") == null ) {
                    error("Line " + lineNumber + ": The database plugin is not found.");
                    return false;
                }
                
                // we ask the database plugin to import the model
                DBScript dbScript = new DBScript();
                currentModel = null;
                asyncException = null;
                Display.getDefault().syncExec(new Runnable() {
                    @Override public void run() {
                        try {
                            currentModel = dbScript.importModel(words[1], words[4], false);
                        } catch (Exception e) {
                            asyncException = e;
                        }
                    }
                });
        }
        
        // we check if the model has been imported
        if ( currentModel == null ) {
            error("Line " + lineNumber + ": import of model failed.", asyncException);
            return false;
        }

        return true;
    }
    
    private boolean closeModel(int lineNumber, String[] words) {
        if ( !checkOptions(lineNumber, words, 1, "CLOSE") )
            return false;
            
        if ( currentModel == null ) {
            error("Line " + lineNumber + ": No model is selected.");
            return false;
        }
        
        // closing the model
        debug("closing the model");
        asyncException = null;
        Display.getDefault().syncExec(new Runnable() {
            @Override public void run() {
                try {
                    IEditorModelManager.INSTANCE.closeModel(currentModel);
                    currentModel = null;
                } catch (IOException e) {
                    asyncException = e;
                }
            }
        });
        
        // we check if the model has been closed
        if ( currentModel != null ) {
            error("Line " + lineNumber + ": Closure of model failed.", asyncException);
            return false;
        }
        
        return true;
    }
    
    private boolean reportModel(int lineNumber, String[] words) {
        if ( !checkOptions(lineNumber, words, 4, "REPORT HTML TO <web site path>") )
            return false;
        
        if ( !words[1].toUpperCase().equals("HTML") || !words[2].toUpperCase().equals("TO") ) {
            error("Line " + lineNumber + ": Unrecognised keyword \""+words[1]+"\"\nSyntax: REPORT HTML TO <web site path>");
        }
        
        if ( currentModel == null ) {
            error("Line " + lineNumber + ": No model is selected.");
            return false;
        }
        
        HTMLReportExporter htmlExporter = new HTMLReportExporter(currentModel);
        asyncException = null;
        Display.getDefault().syncExec(new Runnable() {
            @Override public void run() {
                try {
                    verbose("exporting the model");
                    report = htmlExporter.createReport(new File(words[3]), "index.html");
                } catch (IOException e) {
                    asyncException = e;
                }
            }
        });
            
        if ( asyncException != null ) {
            error("Line " + lineNumber + ": An exception occurred.", asyncException);
            return false;
        }
        
        if ( report == null ) {
            error("Line " + lineNumber + ": HTML report could not be created.");
            return false;
        }
        
        return true;
    }
    
    private boolean checkOptions(int lineNumber, String[] words, int nb, String syntax) {
        if ( words.length != nb) {
            String errorMsg = (words.length<nb) ? "missing parameter" : "too many parameters";
            error("Line " + lineNumber + ": Syntax error: " + errorMsg + ".\nSyntax: "+syntax);
            return false;
        }
        return true;
    }
    
    private void finish() {
        verbose ("*** Script plugin has finished");
        try {
            if ( br != null )
                br.close();
        } catch (IOException e) {
            error("An exception occurred.", e);
        }
    }
    
    private void exitArchi() {
        verbose ("*** Quitting Archi");
        Display.getDefault().syncExec(new Runnable() {
            @Override public void run() {
                PlatformUI.getWorkbench().close();
            }
        });
    }
}
