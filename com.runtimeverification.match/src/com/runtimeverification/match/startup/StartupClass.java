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
import org.eclipse.debug.internal.ui.commands.actions.TerminateAllAction;
import org.eclipse.linuxtools.valgrind.core.ValgrindCoreParser;
import org.eclipse.linuxtools.valgrind.launch.ProjectBuildListener;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.IConsole;

import com.runtimeverification.match.RVMatchPlugin;
import com.runtimeverification.match.handlers.PasteOutputToBuildConsoleHandler;
import com.runtimeverification.match.handlers.SelectBuildHandler;
import com.runtimeverification.match.startup.StartupClass.ReporterThread;

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
				PasteOutputToBuildConsoleHandler outputParser = new PasteOutputToBuildConsoleHandler(launch); 
				outputParser.getProject().deleteMarkers(PasteOutputToBuildConsoleHandler.MARKER_TYPE, true,IResource.DEPTH_INFINITE);

				// clear valgrind error view
				Display.getDefault().syncExec(() -> RVMatchPlugin.getDefault().resetView());
 
				while (true) {
					List<String> record =  outputParser.parseCVSRecord();
					if (record != null) {
						outputParser.handleRecord(record);
					} else {
						if (done) {
							ResourcesPlugin.getWorkspace().addResourceChangeListener(
									new ProjectBuildListener(outputParser.getProject()), IResourceChangeEvent.POST_BUILD);
							
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
				System.out.println(" Launch changed!");
				reportingThread = new ReporterThread(launch);
				
				reportingThread.start();
				IProcess[] processes = launch.getProcesses();
				System.out.println("We have " + processes.length + " processes.");
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
				ICProject project;
				try {
					project = CDebugUtils.getCProject(config);
					Path reportFilePath = SelectBuildHandler.getReportFilePath(project.getProject()).toAbsolutePath();
					File outputFile = reportFilePath.toFile();				
					if (outputFile.exists()) {
						outputFile.delete();
						Process process = new ProcessBuilder("mkfifo",reportFilePath.toString()).inheritIO().start();
						process.waitFor();
					}
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
