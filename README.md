# rvmatch-eclipse-plugin

## Build instructions (using Eclipse)

Use the Eclipse IDE for Eclipse Committers from
http://www.eclipse.org/downloads/packages/

* File -> Import... -> Git -> Projects from Git -> Clone URI
* Enter git@github.com:eclipse/linuxtools
* Next

Select the `stable-5.1` branch

Import at least the following projects:
* org.eclipse.linuxtools.profiling.launch
* org.eclipse.linuxtools.profiling.ui
* org.eclipse.linuxtools.target
* org.eclipse.linuxtools.tools.launch.core
* org.eclipse.linuxtools.valgrind.core
* org.eclipse.linuxtools.valgrind.launch
* org.eclipse.linuxtools.valgrind.ui

Open the `linuxtools-e4.6.target` file from the `org.eclipse.linuxtools.target` project.

Click the link `Set as Target Platform` in the top-right corner
