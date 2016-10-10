package com.runtimeverification.match.startup;

import java.io.IOException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchListener;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.IConsole;

import com.runtimeverification.match.handlers.PasteOutputToBuildConsoleHandler;

public class StartupClass implements IStartup {

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
			}
		});
	}

	public void launchTerminated(ILaunch launch) {
		try {
			PasteOutputToBuildConsoleHandler.checkRVMatchOutput(launch);
		} catch (CoreException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
