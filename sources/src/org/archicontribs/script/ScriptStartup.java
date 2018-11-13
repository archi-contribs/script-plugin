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
    boolean         verbose = false;
    boolean         debug = false;
    String          scriptFilename = null;
    IArchimateModel currentModel = null;
    boolean         exitArchi = false;
    Exception       asyncException = null;
    BufferedReader  br = null;
    File            report = null;
    
    @Override
    public void earlyStartup() {
        Options options = new Options();
        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine = null;
        
        verbose("*** Script plugin initialized");
        
        options.addOption(Option.builder("s").longOpt("script").desc("Script filename").hasArg().argName("script file").build());
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
        
        this.verbose = commandLine.hasOption("v");
        this.debug = commandLine.hasOption("d");
        
        if (commandLine.hasOption("?")) {
            help(options);
            return;
        }
        
        if ( !commandLine.hasOption("s") ) {
            verbose("    No script provided. Please use '-?' option for more information.");
            return;
        }
        
        this.scriptFilename = commandLine.getOptionValue("s");
        verbose("    Verbose mode  : "+this.verbose);
        verbose("    Debug mode    : "+this.debug);
        verbose("    Script file   : "+this.scriptFilename);
        verbose("    DB plugin     : "+(Platform.getBundle("org.archicontribs.database")!=null));
        
        File script = new File(this.scriptFilename);

        

        try {
            this.br = new BufferedReader(new FileReader(script));

            int lineNumber = 0;
            for ( String line; (line=this.br.readLine()) != null; ) {
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
        } catch (@SuppressWarnings("unused") FileNotFoundException e) {
            error(this.scriptFilename+": not found.");
            return;
        } catch (IOException e) {
            error(this.scriptFilename+": " + e.getMessage());
            return;
        }

        finish();
        
        if ( this.exitArchi ) {
            exitArchi();
        }
    }
    
    static void help(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( "Script plugin", options );
        System.out.flush();
    }
    
    void verbose(String msg) {
        if ( this.verbose ) {
            System.out.println("INFO: " + msg);
            System.out.flush();
        }
    }
    
    void debug(String msg) {
        if ( this.debug ) {
            System.out.println("DEBUG: " + msg);
            System.out.flush();
        }
    }
    
    void error(String msg) {
        error(msg, null);
    }
    
    void error(String msg, Exception e) {
        System.err.println("ERROR: " + msg);
        if ( e != null ) {
            System.err.println(e.getMessage());
            e.printStackTrace(System.err);
        }
        System.err.flush();
        if ( this.exitArchi ) {
            exitArchi();
        }
    }
    
    static String[] parseLine(String line) {
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

    
    boolean setOption(int lineNumber, String[] words) {
        if ( !checkOptions(lineNumber, words, 3, "OPTION <option name> [ON|OFF]") )
            return false;
        
        if ( words[1].toUpperCase().equals("EXITARCHI") ) {
                switch ( words[2].toLowerCase() ) {
                    case "on" : this.exitArchi = true; break;
                    case "off": this.exitArchi = false; break;
                    default :
                        error("Line " + lineNumber + ": Syntax error: option flag can only be ON or OFF.\nSyntax: OPTION <option name> [ON|OFF]");
                        return false;
                }
                
                debug("Setting ExitArchi option to " + this.exitArchi);
                return true;
        }
        
        error("Line " + lineNumber + ": Unknown option.\nValid options are : ExitArchi");
        return false;
    }
    
    boolean selectModel(int lineNumber, String[] words) {
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
            if ( (filename == null) || (file == null) ) {
                if ( existingModel.getName().equals(words[1]) ) {
                    this.currentModel = existingModel;
                    return true;
                }
            } else {
                File existingFile = existingModel.getFile();
                try {
                    if ( existingFile!=null && existingFile.getCanonicalPath().equals(file.getCanonicalPath()) ) {
                        this.currentModel = existingModel;
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
                this.asyncException = null;
                Display.getDefault().syncExec(new Runnable() {
                    @Override public void run() {
                        ScriptStartup.this.currentModel = IEditorModelManager.INSTANCE.openModel(new File(words[4]));
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
                this.currentModel = null;
                this.asyncException = null;
                Display.getDefault().syncExec(new Runnable() {
                    @Override public void run() {
                        try {
                            verbose("Calling Database plugin to import model \""+words[1]+"\" from database \""+words[4]+"\"");
                            ScriptStartup.this.currentModel = DBScript.importModel(words[1], words[4], false);
                        } catch (Exception e) {
                            ScriptStartup.this.asyncException = e;
                        }
                    }
                });
                break;
                
             default:
                 error("Line " + lineNumber + ": unknown keyword \""+words[3]+"\", must be \"FILE\" or \"DATABASE\".");
                 return false;
        }
        
        // we check if the model has been imported
        if ( this.currentModel == null ) {
            error("Line " + lineNumber + ": import of model failed.", this.asyncException);
            return false;
        }

        return true;
    }
    
    boolean closeModel(int lineNumber, String[] words) {
        if ( !checkOptions(lineNumber, words, 1, "CLOSE") )
            return false;
            
        if ( this.currentModel == null ) {
            error("Line " + lineNumber + ": No model is selected.");
            return false;
        }
        
        // closing the model
        debug("Closing the model");
        this.asyncException = null;
        Display.getDefault().syncExec(new Runnable() {
            @Override public void run() {
                try {
                    IEditorModelManager.INSTANCE.closeModel(ScriptStartup.this.currentModel);
                    ScriptStartup.this.currentModel = null;
                } catch (IOException e) {
                    ScriptStartup.this.asyncException = e;
                }
            }
        });
        
        // we check if the model has been closed
        if ( this.currentModel != null ) {
            error("Line " + lineNumber + ": Closure of model failed.", this.asyncException);
            return false;
        }
        
        return true;
    }
    
    boolean reportModel(int lineNumber, String[] words) {
        if ( !checkOptions(lineNumber, words, 4, "REPORT HTML TO <web site path>") )
            return false;
        
        if ( !words[1].toUpperCase().equals("HTML") || !words[2].toUpperCase().equals("TO") ) {
            error("Line " + lineNumber + ": Unrecognised keyword \""+words[1]+"\"\nSyntax: REPORT HTML TO <web site path>");
        }
        
        if ( this.currentModel == null ) {
            error("Line " + lineNumber + ": No model is selected.");
            return false;
        }
        
        HTMLReportExporter htmlExporter = new HTMLReportExporter(this.currentModel);
        this.asyncException = null;
        Display.getDefault().syncExec(new Runnable() {
            @Override public void run() {
                try {
                    verbose("exporting the model");
                    ScriptStartup.this.report = htmlExporter.createReport(new File(words[3]), "index.html");
                } catch (IOException e) {
                    ScriptStartup.this.asyncException = e;
                }
            }
        });
            
        if ( this.asyncException != null ) {
            error("Line " + lineNumber + ": An exception occurred.", this.asyncException);
            return false;
        }
        
        if ( this.report == null ) {
            error("Line " + lineNumber + ": HTML report could not be created.");
            return false;
        }
        
        return true;
    }
    
    boolean checkOptions(int lineNumber, String[] words, int nb, String syntax) {
        if ( words.length != nb) {
            String errorMsg = (words.length<nb) ? "missing parameter" : "too many parameters";
            error("Line " + lineNumber + ": Syntax error: " + errorMsg + ".\nSyntax: "+syntax);
            return false;
        }
        return true;
    }
    
    void finish() {
        verbose ("*** Script plugin has finished");
        try {
            if ( this.br != null )
                this.br.close();
        } catch (IOException e) {
            error("An exception occurred.", e);
        }
    }
    
    void exitArchi() {
        verbose ("*** Quitting Archi");
        Display.getDefault().syncExec(new Runnable() {
            @Override public void run() {
                PlatformUI.getWorkbench().close();
            }
        });
    }
}
