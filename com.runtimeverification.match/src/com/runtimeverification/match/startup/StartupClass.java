package com.runtimeverification.match.startup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.cdt.debug.core.CDebugUtils;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchListener;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.linuxtools.valgrind.launch.ProjectBuildListener;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IStartup;

import com.runtimeverification.match.ReportExecutionOutput;
import com.runtimeverification.match.RVMatchPlugin;
import com.runtimeverification.match.handlers.SelectBuildHandler;

public class StartupClass implements IStartup {
	public class ReporterThread extends Thread {
		ILaunch launch;
		private boolean done;
		
		public ReporterThread(ILaunch launch) {
			this.launch = launch;
			done = false;
		}
		
		public void terminate() throws InterruptedException {
			done = true;
			this.join();
		}
		
		@Override
		public void run() {
			try {
				ReportExecutionOutput outputParser = new ReportExecutionOutput(launch); 
				outputParser.getProject().deleteMarkers(RVMatchPlugin.MARKER_TYPE, true,IResource.DEPTH_INFINITE);

				// clear valgrind error view
				Display.getDefault().syncExec(() -> RVMatchPlugin.getDefault().resetView());
 
				while (true) {
					List<String> record =  outputParser.parseCSVRecord();
					if (record != null) {
						outputParser.handleRecord(record);
					} else {
						if (done) {
							ResourcesPlugin.getWorkspace().addResourceChangeListener(
									new ProjectBuildListener(outputParser.getProject(), RVMatchPlugin.PLUGIN_ID, RVMatchPlugin.MARKER_TYPE), IResourceChangeEvent.POST_BUILD);
							
							break;
						}
						sleep(1000);
					}
				}

			} catch (CoreException | IOException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	private ReporterThread reportingThread = null;

	@Override
	public void earlyStartup() {
		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		manager.addLaunchListener(new ILaunchListener() {


			@Override
			public void launchRemoved(ILaunch launch) {
			}

			@Override
			public void launchChanged(ILaunch launch) {
				ILaunchConfiguration config = launch.getLaunchConfiguration();
				try {
					if (!config.getAttribute("org.eclipse.cdt.launch.PROJECT_BUILD_CONFIG_ID_ATTR", "").startsWith("rv.")) {
						return;
					}
				} catch (CoreException e) {
					e.printStackTrace();
				}
				reportingThread = new ReporterThread(launch);
				
				reportingThread.start();
				IProcess[] processes = launch.getProcesses();
				for (IProcess process : processes) {
					if (!process.isTerminated()) {
						DebugPlugin.getDefault().addDebugEventListener(new
								IDebugEventSetListener() {
							public void handleDebugEvents(DebugEvent[] events) {
								for (int i = 0; i < events.length; i++) {
									Object source = events[i].getSource();
									if (source instanceof IProcess && (IProcess)source == process)
									{
										if (events[i].getKind() == DebugEvent.TERMINATE) {
											DebugPlugin.getDefault().removeDebugEventListener(this);
											launchTerminated(launch);
										}
									}
								}
							}
						});
					} else {
						launchTerminated(launch);
					}
				}
			}

			@Override
			public void launchAdded(ILaunch launch) {
				ILaunchConfiguration config = launch.getLaunchConfiguration();
				try {
					if (!config.getAttribute("org.eclipse.cdt.launch.PROJECT_BUILD_CONFIG_ID_ATTR", "").startsWith("rv.")) {
						return;
					}
					ICProject project;
					project = CDebugUtils.getCProject(config);
					Path reportFilePath = SelectBuildHandler.getReportFilePath(project.getProject()).toAbsolutePath();
					File outputFile = reportFilePath.toFile();				
					if (outputFile.exists()) {
						outputFile.delete();
					}
					Process process = new ProcessBuilder("mkfifo",reportFilePath.toString()).inheritIO().start();
					process.waitFor();
				} catch (CoreException | IOException | InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
	}

	public void launchTerminated(ILaunch launch) {
		try {
			if (reportingThread != null) reportingThread.terminate();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
