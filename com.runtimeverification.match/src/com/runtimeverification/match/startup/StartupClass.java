package com.runtimeverification.match.startup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
		private ReportExecutionOutput outputParser;
		
		public ReporterThread(ILaunch launch) {
			this.launch = launch;
			done = false;
			outputParser = null;
		}
		
		public void terminate() throws InterruptedException {
			done = true;
			if (outputParser != null) {
				outputParser.terminate();
			}
			this.join();
		}
		
		@Override
		public void run() {
			try {
				outputParser = new ReportExecutionOutput(launch);
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
	
	private Map<ILaunch, ReporterThread> reportingThreads = new HashMap<>();

	@Override
	public void earlyStartup() {
		try {
			Path reportFilePath = SelectBuildHandler.getReportFilePath().toAbsolutePath();
			File outputFile = reportFilePath.toFile();				
			if (outputFile.exists()) {
				outputFile.delete();
			}
			outputFile.deleteOnExit();
			Process process = new ProcessBuilder("mkfifo",reportFilePath.toString()).inheritIO().start();
			process.waitFor();
		} catch (IOException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		manager.addLaunchListener(new ILaunchListener() {
			@Override
			public void launchRemoved(ILaunch launch) {
				ReporterThread thread = reportingThreads.get(launch);
				try {
					thread.terminate();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				reportingThreads.remove(launch);
			}

			@Override
			public void launchAdded(ILaunch launch) {
				ILaunchConfiguration config = launch.getLaunchConfiguration();
				ReporterThread reportingThread = new ReporterThread(launch);

				reportingThread.start();
				reportingThreads.put(launch, reportingThread);
			}

			@Override
			public void launchChanged(ILaunch launch) {
			}
		});
	}
}
