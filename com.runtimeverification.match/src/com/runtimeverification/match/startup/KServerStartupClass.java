package com.runtimeverification.match.startup;

import java.io.IOException;

import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.HandlerUtil;

public class KServerStartupClass implements IStartup {

	@Override
	public void earlyStartup() {
		try {
			new ProcessBuilder().inheritIO().command("kserver").start();
			IWorkbench window = PlatformUI.getWorkbench();
			ICommandService service = (ICommandService) window.getService(ICommandService.class);
			Command kServer = service.getCommand("com.runtimeverification.match.commands.kServerCommand");
			boolean isEnabled = HandlerUtil.toggleCommandState(kServer);
			if (isEnabled) {
				HandlerUtil.toggleCommandState(kServer);
			}
		} catch (IOException | ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
