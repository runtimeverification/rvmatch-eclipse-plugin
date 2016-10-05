# rvmatch-eclipse-plugin

## Eclipse set-up instructions 

Use the Eclipse IDE for Eclipse Committers

* File -> Import... -> Git -> Projects from Git -> Clone URI
* Enter ``git@github.com:eclipse/linuxtools``
* Next select the `stable-5.1` branch
* Import at least the following projects:
 * org.eclipse.linuxtools.profiling.launch
 * org.eclipse.linuxtools.profiling.ui
 * org.eclipse.linuxtools.target
 * org.eclipse.linuxtools.tools.launch.core
 * org.eclipse.linuxtools.valgrind.core
 * org.eclipse.linuxtools.valgrind.launch
 * org.eclipse.linuxtools.valgrind.ui
* Open the `linuxtools-e4.6.target` file from the `org.eclipse.linuxtools.target` project.
 * Click the link `Set as Target Platform` in the top-right corner and wait a couple of minutes for Eclipse to download prerequisites and set up.

* Window -> Preferences -> Plug-in Development -> API Baselines -> Add Baseline... -> A target platform -> linuxtools-e4.6

* File -> Import... -> Git -> Projects from Git -> Clone URI
* Enter `git@github.com:runtimeverification/rvmatch-eclipse-plugin`
* Import all projects

You should now have a working version of the plugin.


