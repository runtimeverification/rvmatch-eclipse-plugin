package com.runtimeverification.match.handlers;

import java.io.IOException;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Our sample handler extends AbstractHandler, an IHandler base class.
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class KServerHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		Command command = event.getCommand();
		boolean oldValue = HandlerUtil.toggleCommandState(command);
		try {
			if (oldValue) {
				new ProcessBuilder().inheritIO().command("stop-kserver").start();			
			} else {
				new ProcessBuilder().inheritIO().command("kserver").start();
			}
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}
		return null;
	}
}
