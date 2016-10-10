package com.runtimeverification.match.handlers;

import java.net.URI;

import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.debug.core.model.ISourceLocator;
import org.eclipse.debug.core.sourcelookup.containers.LocalFileStorage;
import org.eclipse.debug.internal.ui.views.console.ProcessConsole;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.sourcelookup.ISourceLookupResult;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.ui.console.IPatternMatchListenerDelegate;
import org.eclipse.ui.console.PatternMatchEvent;
import org.eclipse.ui.console.TextConsole;


public class ConsolePatternMatcher implements IPatternMatchListenerDelegate {
	
	ProcessConsole fConsole;
	private ISourceLocator sourceLocator;
	

	@Override
	public void connect(TextConsole console) {
		System.out.println("Connected to " + console.getName());
		fConsole = (ProcessConsole) console;
		System.out.println(fConsole.getProcess().getLabel());
		sourceLocator = fConsole.getProcess().getLaunch().getSourceLocator();
		
	}

	@Override
	public void disconnect() {
		System.out.println("Disconnected from console.");

	}

	@Override
	public void matchFound(PatternMatchEvent event) {
		int lineNo = -1;
		String filename = null;
		try {
			String line = fConsole.getDocument().get(event.getOffset(), event.getLength());
			if (line.startsWith("at ")) {
				int ix = line.lastIndexOf('(');
				if (ix >= 0) {
					String part = line.substring(ix, line.length());
					part = part.substring(1, part.length() - 1); // remove leading and trailing parentheses
					if ((ix = part.lastIndexOf(':')) >= 0) {
						String strLineNo = part.substring(ix + 1);
						lineNo = Integer.parseInt(strLineNo);
						filename = part.substring(0, ix);
						ISourceLookupResult result = DebugUITools.lookupSource(filename, sourceLocator);
						Object sourceElement = result.getSourceElement();

						if (sourceElement != null) {
							// Resolve IResource in case we get a LocalFileStorage object
							if (sourceElement instanceof LocalFileStorage) {
								IPath filePath = ((LocalFileStorage) sourceElement).getFullPath();
								URI fileURI = URIUtil.toURI(filePath);
								IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
								IFile[] files = root.findFilesForLocationURI(fileURI);
								if (files.length > 0) {
									// Take the first match
									sourceElement = files[0];
								}
							}

							if (sourceElement instanceof IResource) {
								IResource resource = (IResource) sourceElement;
								IMarker marker = resource.createMarker("com.runtimeverification.match.marker");
								marker.setAttribute(IMarker.MESSAGE, "RV Match Error");
								marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
								marker.setAttribute(IMarker.LINE_NUMBER, lineNo);
							}
						}
					}
				}
			}
		} catch (BadLocationException | CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
