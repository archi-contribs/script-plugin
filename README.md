Script plugin
=============

[![N|Solid](http://www.archimatetool.com/img/archi_logo.png)](http://www.archimatetool.com/)[![N|Solid](http://www.archimatetool.com/img/archi_text.png)](http://www.archimatetool.com/)
# Archimate Tool Form plugin
This is a plugin for Archi, the Archimate tool.

## Archi versions compatibility
The plugin works with the version 4 of Archi

## Installation instructions:
You just need to download the **org.archicontribs.script_vx.x.jar** file anc copy it into your Archi **plugins** folder

## What the plugin is able to do:
The plugin is ran right after Archi starte. It reads a script file and execute the commands in the background.

## Commands recognized by the plugin:
* **#** lines beginning with a hash tag and empty lines are ignored
* **SELECT "model name"** selects a model by its name and raise an error if the model is not yet loaded in the memory
* **SELECT "model name" FROM FILE "file name"** selects a model by its name and import the model from the archimate file if needed. An error is raised if the archimate file is not found or if the model cannot be imported.
* **SELECT "model name" FROM DATABASE "database name"** selects a model by its name and import it from the database if needed. An error is raised if Archi cannot connect to the database or if the model cannot be imported. This command requires that the database plugin is installed and configured.
* **REPORT HTML TO "folder name"** export the selected model to HTML using the default template provided by Archi. If the folder does not exist, then the plugin tries to create it. An error os raised if the folder cannot be created or the model cannot be exported.
* **CLOSE** closes the selected model
* **OPTION EXITARCHI [on|off]** if set to on, the Archi is shutdown after the script ends or if an error is raised. If set to off (the default), then Archi stays active.

The command keywords are written here in uppercase to emphasis them, but the script plugin is case insensitive.

## How to run a script in Archi ?
You may write your commands in a script file and run archi from a command line or from a batch with the following arguments:
* **-v, --vebose** switch the plugin to verbose mode
* **-d, --debug** switch the plugin to debug mode
* **-s, --script filename** specifies the filename of the script
* **-?, --help** shows up a help message

For instance:
> **On linux:**
>> $> Archi -v -s ~/archi.script
>
> **On Windows**
>> C:\> Archi.exe -v -s D:\archi.script
>
## How to run a script in Archi on a server (without any graphical interface) ?
Some servers do not have any graphical interface at all (usually Linux) while some others do not accept that software ran as a scheduled task reach the graphical interface (usually Windows).

Nevertheless, it is possible to run Archi without any graphical interface using [Xvfb](ttps://www.google.fr/url?sa=t&rct=j&q=&esrc=s&source=web&cd=2&cad=rja&uact=8&ved=0ahUKEwickpD52ZPWAhXK0xoKHSYRCKUQFggyMAE&url=https%3A%2F%2Fwww.x.org%2Farchive%2FX11R7.6%2Fdoc%2Fman%2Fman1%2FXvfb.1.xhtml&usg=AFQjCNHuSS3WGCsWPxnRaAsG0XASqVpeiA) on Linux:
> sudo yum install xorg-x11-server-Xvfb
> xvfb-run Archi -v -s ~/archi.script

or PsExec from the [SysInternals](https://docs.microsoft.com/en-us/sysinternals/downloads/sysinternals-suite) suite.
> psexec -i -s Archi.exe -v -s D:\archi\archi.script

## Script sample
> \# lines beginning with a hash key are comments
> \# empty lines are ignored
> \# keywords are case insensitive
> 
> \# We set the option to automatically exit Archi when the script ends
> \#    the scripts end when the last line is reached or when an error is raised
> **OPTION ExitArchi on**
>
> \# We select the model "my model"
> \#    and imports it from the archimate file it is not yet loaded in memory
> \#    an error is generated if the archimate file cannot be loaded
> **SELECT "my model" FROM FILE "D:\archi\my model.archimate"**
> 
> \# we generate the HTML report from the selected model
> \#    if the specified folder does not exits, it is created
> \#    if the report can't be created, then an error is raised
> **REPORT HTML TO "D:\archi\web"**
> 
> \# We close the model "my model"
> **CLOSE**
> 
> \# This is the end of the script.
> \# Now Archi will be automatically closed (see ExitArchi option at the beginning of the script)