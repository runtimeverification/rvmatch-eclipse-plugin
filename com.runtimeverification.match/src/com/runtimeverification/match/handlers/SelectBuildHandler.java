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
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.handlers.HandlerUtil;

import com.runtimeverification.match.RVMatchPlugin;

public class SelectBuildHandler extends AbstractHandler {

	private static Path home = Paths.get(System.getProperty("user.home"));

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
				if (activeConfig != null) {
					createRVConfiguration(currentProject, activeConfig);		
				}
			} else {
				RVMatchPlugin.log(IStatus.INFO, "Setting " + rv.getName() + " as the active build configuration for project " + currentProject.getName() +  ".");		
				projDes.setActiveConfiguration(rv);
				mngr.setProjectDescription(currentProject, projDes);
			}
		} catch (CoreException | BuildException e) {
			throw new ExecutionException(e.getMessage(), e);
		}
		return null;
	}

	public static ICConfigurationDescription createRVConfiguration(IProject currentProject, 
			ICConfigurationDescription config) throws CoreException, BuildException {
		ICProjectDescriptionManager mngr = CoreModel.getDefault().getProjectDescriptionManager();
		ICProjectDescription projDes = mngr.getProjectDescription(currentProject);
		
		ICConfigurationDescription rv;
		String id = "rv." + config.getId();
		String name = config.getName() + " RV";
		rv = projDes.createConfiguration(id, name, config);
		projDes.setActiveConfiguration(rv);
		mngr.setProjectDescription(currentProject, projDes);
		IManagedProject imgr = ManagedBuildManager.getBuildInfo(currentProject).getManagedProject();
		IConfiguration conf = imgr.getConfiguration(rv.getId());
		String reportFile = getReportFilePath().toString();
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
					String newValue = option.getStringValue().replaceAll("-fmessage-length=\\d+",""); 
					ManagedBuildManager.setOption(conf, tool, option, newValue);
				}
				option = tool.getOptionBySuperClassId("gnu.c.link.option.ldflags");
				if (option != null) {
					
					ManagedBuildManager.setOption(conf, tool, option, "-fissue-report=" + reportFile);
				}
			}
		}
		ManagedBuildManager.saveBuildInfo(currentProject, true);
		RVMatchPlugin.log(IStatus.INFO, "Created "+ rv.getName() + " configuration for project " + currentProject.getName() +  ".");

		return rv;
	}

	public static Path getReportFilePath() {
		return home.resolve(".kcc-report.csv").toAbsolutePath();
	}

}
