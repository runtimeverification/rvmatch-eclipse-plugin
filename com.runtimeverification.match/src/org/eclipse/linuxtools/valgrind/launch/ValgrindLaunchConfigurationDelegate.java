/*******************************************************************************
 * Copyright (c) 2008, 2011 Red Hat, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Elliott Baron <ebaron@redhat.com> - initial API and implementation
 * Martin Oberhuber (Wind River) - [360085] Fix valgrind problem marker lifecycle
 *******************************************************************************/
package org.eclipse.linuxtools.valgrind.launch;

import java.net.URI;
import java.util.Arrays;
import java.util.Stack;

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
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.sourcelookup.ISourceLookupResult;
import org.eclipse.linuxtools.valgrind.core.IValgrindMessage;
import org.eclipse.linuxtools.valgrind.core.ValgrindError;
import org.eclipse.linuxtools.valgrind.core.ValgrindStackFrame;

public class ValgrindLaunchConfigurationDelegate {
	
	private String markerType;

	public ValgrindLaunchConfigurationDelegate(String markerType) {
		this.markerType = markerType;
	}

	public void createMarkers(IValgrindMessage[] messages) throws CoreException {
		// find the topmost stack frame within the workspace to annotate with marker
		// traverse nested errors as well
		Stack<IValgrindMessage> messageStack = new Stack<>();
		messageStack.addAll(Arrays.asList(messages));
		while (!messageStack.isEmpty()) {
			IValgrindMessage message = messageStack.pop();
			IMarker marker = null;
			IValgrindMessage[] children = message.getChildren();
			for (int i = 0; i < children.length; i++) {
				// if we've found our marker we don't care about any further frames in this stack
				if (children[i] instanceof ValgrindStackFrame && marker == null) {
					ValgrindStackFrame frame = (ValgrindStackFrame) children[i];
					if (frame.getLine() > 0) {
						ISourceLocator locator = frame.getSourceLocator();
						ISourceLookupResult result = DebugUITools.lookupSource(frame.getFile(), locator);
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
								marker = resource.createMarker(markerType);
								marker.setAttribute(IMarker.MESSAGE, message.getText());
								marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
								marker.setAttribute(IMarker.LINE_NUMBER, frame.getLine());
							}
						}
					}
				}
				else if (children[i] instanceof ValgrindError) {
					// nested error
					messageStack.push(children[i]);
				}
			}
		}
	}
}
