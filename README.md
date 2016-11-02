# rvmatch-eclipse-plugin

## Eclipse set-up instructions 

Use the Eclipse IDE for Eclipse Committers (or download the Eclipse Plug-in Development Environment)

* File -> Import... -> Git -> Projects from Git -> Clone URI
* Enter `git@github.com:runtimeverification/rvmatch-eclipse-plugin`
* Import all projects
* Open the `rvmatch-e4.6.target` file from the `com.runtimeverification.match.target` project.
 * Click the link `Set as Target Platform` in the top-right corner and wait a couple of minutes for Eclipse to download prerequisites and set up.

* Window -> Preferences -> Plug-in Development -> API Baselines -> Add Baseline... -> A target platform -> rvmatch-e4.6


You should now have a working version of the plugin. You can run it by selecting "Run as Eclipse Application"

