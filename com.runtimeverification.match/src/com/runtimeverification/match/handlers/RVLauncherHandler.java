package com.runtimeverification.match.handlers;

import java.util.Arrays;

import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescriptionManager;
import org.eclipse.cdt.managedbuilder.core.BuildException;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.handlers.HandlerUtil;

import com.runtimeverification.match.RVMatchPlugin;

public class RVLauncherHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		try {
			IProject currentProject = null;
			IEditorPart activeEditor = HandlerUtil.getActiveEditor(event);
			if (activeEditor != null) {
				IResource currentFile = ((IFileEditorInput) activeEditor.getEditorInput()).getFile();
				currentProject = currentFile.getProject();
			}
			
			ILaunchConfiguration rvLaunch = createRVLaunchConfiguration(currentProject, getLastLaunch(currentProject));
			if (rvLaunch != null) {
				rvLaunch.launch(ILaunchManager.RUN_MODE, null);
			} else {
				RVMatchPlugin.logErrorMessage("Cannot run RV Match: no current project nor previous launch found.");				
			}
			return null;			
		} catch (CoreException | BuildException e) {
			throw new ExecutionException(e.getMessage(), e);
		}
	}

	private ILaunchConfiguration createRVLaunchConfiguration(IProject currentProject,
			ILaunchConfiguration baseLaunchConfig) throws CoreException, BuildException {
		ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
		ICConfigurationDescription buildConfig;
		if (baseLaunchConfig == null) {
			if (currentProject != null) { // retrieve build configuration from current project
				buildConfig = CoreModel.getDefault().getProjectDescriptionManager()
						.getProjectDescription(currentProject).getActiveConfiguration();
			} else { // no last launch & no active project : cannot run
				return null;
			}
		} else {
			String configId = baseLaunchConfig.getAttribute("org.eclipse.cdt.launch.PROJECT_BUILD_CONFIG_ID_ATTR", "");
			if (configId.startsWith("rv.")) { // if it is based on an RV build configuration
				return baseLaunchConfig;
			}
			// If lastLaunch is not based on an RV configuration
			if (currentProject == null) { // retrieve project from launch configuration
				String projectName = baseLaunchConfig.getAttribute("org.eclipse.cdt.launch.PROJECT_ATTR", "");
				currentProject = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
			}
			// here both lastLaunch and currentProject should be non-null
			String rvConfigId = "rv." + configId;
			ICProjectDescriptionManager mngr = CoreModel.getDefault().getProjectDescriptionManager();
			ICProjectDescription projDes = mngr.getProjectDescription(currentProject);
			buildConfig= projDes.getConfigurationById(rvConfigId);
			if (buildConfig == null) {
				buildConfig = projDes.getConfigurationById(configId);
			}
		}
		if (!buildConfig.getId().startsWith("rv.")) {
			buildConfig = SelectBuildHandler.createRVConfiguration(currentProject, buildConfig);
		}
		// create Launch configuration based on the RV Build config
		ILaunchConfigurationType type = launchManager.getLaunchConfigurationType("org.eclipse.cdt.launch.applicationLaunchType");
		ILaunchConfigurationWorkingCopy workingCopy;
		if (baseLaunchConfig == null) {
			workingCopy = type.newInstance(null, currentProject.getName() + " (" + buildConfig.getName() + ")");
			workingCopy.setAttribute("org.eclipse.cdt.launch.PROJECT_ATTR", currentProject.getName());
			workingCopy.setAttribute("org.eclipse.debug.core.MAPPED_RESOURCE_PATHS", Arrays.asList("/" + currentProject.getName()));
			workingCopy.setAttribute("org.eclipse.debug.core.MAPPED_RESOURCE_TYPES", Arrays.asList("4"));
		} else {
			workingCopy = type.newInstance(null, baseLaunchConfig.getName() + " (RV)");
			workingCopy.setAttributes(baseLaunchConfig.getAttributes());
		}
		workingCopy.setAttribute("org.eclipse.cdt.launch.ATTR_BUILD_BEFORE_LAUNCH_ATTR", 1);
		workingCopy.setAttribute("org.eclipse.cdt.launch.DEBUGGER_START_MODE", "run");
		workingCopy.setAttribute("org.eclipse.cdt.launch.PROGRAM_NAME", buildConfig.getName() + "/" + currentProject.getName());
		workingCopy.setAttribute("org.eclipse.cdt.launch.PROJECT_BUILD_CONFIG_AUTO_ATTR",false);
		workingCopy.setAttribute("org.eclipse.cdt.launch.PROJECT_BUILD_CONFIG_ID_ATTR", buildConfig.getId());
		return workingCopy.doSave();				
	}

	private ILaunchConfiguration getLastLaunch(IProject currentProject) throws ExecutionException {
		ILaunchConfiguration[] launchHistory = DebugUIPlugin.getDefault().getLaunchConfigurationManager().getLaunchHistory("org.eclipse.debug.ui.launchGroup.debug").getCompleteLaunchHistory();
		if (launchHistory.length != 0) {
			if (currentProject == null) {
				return launchHistory[0];
			} else {
				for (ILaunchConfiguration launchConfig : launchHistory) {
					try {
						if (launchConfig.getAttribute("org.eclipse.cdt.launch.PROJECT_ATTR", "").equals(currentProject.getName())) {
							return launchConfig;
						}
					} catch (CoreException e) {
						throw new ExecutionException(e.getMessage(), e);
					}
				}
			}
		}
		return null;
	}


}
