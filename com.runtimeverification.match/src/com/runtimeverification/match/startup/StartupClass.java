package com.runtimeverification.match.startup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.linuxtools.valgrind.launch.ProjectBuildListener;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IStartup;

import com.runtimeverification.match.ReportExecutionOutput;
import com.runtimeverification.match.RVMatchPlugin;
import com.runtimeverification.match.handlers.SelectBuildHandler;

public class StartupClass implements IStartup {
	private ReportExecutionOutput outputParser;
	public class ReporterThread extends Thread {
		private boolean done;

		public ReporterThread() {
			done = false;
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

				// clear valgrind error view
				Display.getDefault().syncExec(() -> RVMatchPlugin.getDefault().resetView());
 
				while (true) {
					List<String> record =  outputParser.parseCSVRecord();
					if (record != null) {
						outputParser.handleRecord(record);
					} else {
						if (done) {
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

		outputParser = new ReportExecutionOutput();
		ResourcesPlugin.getWorkspace().addResourceChangeListener(
				new ProjectBuildListener(RVMatchPlugin.PLUGIN_ID, RVMatchPlugin.MARKER_TYPE, outputParser), IResourceChangeEvent.POST_BUILD);

		ReporterThread reportingThread = new ReporterThread();
		reportingThread.start();
	}
}
