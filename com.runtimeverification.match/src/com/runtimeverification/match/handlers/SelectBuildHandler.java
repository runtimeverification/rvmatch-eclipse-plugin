package com.runtimeverification.match.handlers;

import java.nio.file.Path;
import java.nio.file.Paths;

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
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.handlers.HandlerUtil;

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
			System.err.println("Project " + currentProject.getName() +  "; RV configuration was " + (rv == null ? "not ": "") + "found.");
			if (rv == null) {
				if (activeConfig != null) {
					String id = "rv." + activeConfig.getId();
					String name = "RV Build Configuration";
					rv = projDes.createConfiguration(id, name, activeConfig);
					projDes.setActiveConfiguration(rv);
					mngr.setProjectDescription(currentProject, projDes);
					IManagedProject imgr = ManagedBuildManager.getBuildInfo(currentProject).getManagedProject();
					IConfiguration conf = imgr.getConfiguration(rv.getId());
					String reportFile = getReportFilePath(currentProject).toString();
					conf.setPostbuildStep("parse-kcc-to-gcc " + reportFile);
					conf.setPostannouncebuildStep("Parsing kcc output");
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
								ManagedBuildManager.setOption(conf, tool, option, oldValue + " -fissue-report=" + reportFile);
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
				projDes.setActiveConfiguration(rv);
				mngr.setProjectDescription(currentProject, projDes);
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
