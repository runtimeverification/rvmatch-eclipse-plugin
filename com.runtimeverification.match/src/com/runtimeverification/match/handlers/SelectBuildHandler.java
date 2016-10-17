package com.runtimeverification.match.handlers;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescriptionManager;
import org.eclipse.cdt.managedbuilder.core.BuildException;
import org.eclipse.cdt.managedbuilder.core.IConfiguration;
import org.eclipse.cdt.managedbuilder.core.IManagedProject;
import org.eclipse.cdt.managedbuilder.core.IOption;
import org.eclipse.cdt.managedbuilder.core.ITool;
import org.eclipse.cdt.managedbuilder.core.IToolChain;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.handlers.HandlerUtil;

import com.runtimeverification.match.RVMatchPlugin;

public class SelectBuildHandler extends AbstractHandler {

	private static Path tmp = Paths.get(System.getProperty("java.io.tmpdir"));

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		try {
			IResource currentFile = ((IFileEditorInput) HandlerUtil.getActiveEditor(event).getEditorInput()).getFile();
			IProject currentProject = currentFile.getProject();
			ICProjectDescriptionManager mngr = CoreModel.getDefault().getProjectDescriptionManager();
			ICProjectDescription projDes = mngr.getProjectDescription(currentProject);
			ICConfigurationDescription[] configDescs = projDes.getConfigurations();
			ICConfigurationDescription rv = null;
			ICConfigurationDescription activeConfig = projDes.getActiveConfiguration();
			for (ICConfigurationDescription configDes : configDescs) {
				if (configDes.getId().startsWith("rv.")) {
					rv = configDes;
				}
			}
			if (rv == null) {
				RVMatchPlugin.log(IStatus.INFO, "Project " + currentProject.getName() +  "; RV configuration not found. Creating it.");
				if (activeConfig != null) {
					String id = "rv." + activeConfig.getId();
					String name = "RV Build Configuration";
					rv = projDes.createConfiguration(id, name, activeConfig);
					projDes.setActiveConfiguration(rv);
					mngr.setProjectDescription(currentProject, projDes);
					IManagedProject imgr = ManagedBuildManager.getBuildInfo(currentProject).getManagedProject();
					IConfiguration conf = imgr.getConfiguration(rv.getId());
					String reportFile = getReportFilePath(currentProject).toString();
//					conf.setPostbuildStep("parse-kcc-to-gcc " + reportFile);
//					conf.setPostannouncebuildStep("Parsing kcc output");
					IToolChain toolChain = conf.getToolChain();
					ITool[] tools = toolChain.getTools();
					for (ITool tool : tools) {
						if (tool.getToolCommand().equals("gcc")) {
							tool.setToolCommand("kcc");
							IOption option = tool.getOptionBySuperClassId("gnu.c.compiler.exe.debug.option.debugging.level");
							if (option != null) {
								ManagedBuildManager.setOption(conf, tool, option, "gnu.c.debugging.level.default");
							}
							option = tool.getOptionBySuperClassId("gnu.c.compiler.option.misc.other");
							if (option != null) {
								String oldValue = option.getStringValue().replaceAll("-fmessage-length=\\d+",""); 
								ManagedBuildManager.setOption(conf, tool, option, oldValue);// + " -fissue-report=" + reportFile);
							}
							option = tool.getOptionBySuperClassId("gnu.c.link.option.ldflags");
							if (option != null) {
								
								ManagedBuildManager.setOption(conf, tool, option, "-fissue-report=" + reportFile);
							}
						}
					}
					ManagedBuildManager.saveBuildInfo(currentProject, true);		
				}
			} else {
				RVMatchPlugin.log(IStatus.INFO, "Setting the RV configuration as the default for project " + currentProject.getName() +  ".");		
				projDes.setActiveConfiguration(rv);
				mngr.setProjectDescription(currentProject, projDes);
			}
			ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
			ILaunchConfigurationType type = manager.getLaunchConfigurationType("org.eclipse.cdt.launch.applicationLaunchType");
			ILaunchConfiguration[] configurations = manager.getLaunchConfigurations(type);
			ILaunchConfiguration rvLaunchConfig = null;
			ILaunchConfiguration projectConfig = null;
			for (ILaunchConfiguration configuration : configurations) {
				if (configuration.getAttribute("org.eclipse.cdt.launch.PROJECT_ATTR", "").equals(currentProject.getName())) {
					if (projectConfig == null) {
						projectConfig = configuration;
					}
					if (configuration.getAttribute("org.eclipse.cdt.launch.PROJECT_BUILD_CONFIG_ID_ATTR", "").startsWith("rv.")) {
						System.err.println("RV configuration for " + currentProject.getName() + " found.");
						rvLaunchConfig = configuration;
					}
				}
			}

			if (rvLaunchConfig == null) {
				ILaunchConfigurationWorkingCopy workingCopy;
				if (projectConfig != null) {
					workingCopy = projectConfig.getWorkingCopy();
				} else {
					workingCopy = type.newInstance(null, currentProject.getName() + " (RV)");
	 				workingCopy.setAttribute("org.eclipse.cdt.launch.ATTR_BUILD_BEFORE_LAUNCH_ATTR", 1);
					workingCopy.setAttribute("org.eclipse.cdt.launch.PROJECT_ATTR", currentProject.getName());
					workingCopy.setAttribute("org.eclipse.debug.core.MAPPED_RESOURCE_PATHS", Arrays.asList("/" + currentProject.getName()));
					workingCopy.setAttribute("org.eclipse.debug.core.MAPPED_RESOURCE_TYPES", Arrays.asList("4"));
				}
				workingCopy.setAttribute("org.eclipse.cdt.launch.DEBUGGER_START_MODE", "run");
				workingCopy.setAttribute("org.eclipse.cdt.launch.PROGRAM_NAME", rv.getName() + "/" + currentProject.getName());
				workingCopy.setAttribute("org.eclipse.cdt.launch.PROJECT_BUILD_CONFIG_AUTO_ATTR",false);
				workingCopy.setAttribute("org.eclipse.cdt.launch.PROJECT_BUILD_CONFIG_ID_ATTR", rv.getId());
				rvLaunchConfig = workingCopy.doSave();				
//				rvLaunchConfig = 
				System.err.println("RV configuration for " + currentProject.getName() + " created.");
			}
		} catch (CoreException | BuildException e) {
			throw new ExecutionException(e.getMessage(), e);
		}
		return null;
	}

	public static Path getReportFilePath(IProject currentProject) {
		return tmp.resolve(currentProject.getName().replace(' ', '_') + "-kcc-report.csv").toAbsolutePath();
	}

}
