/*******************************************************************************
 * Copyright (c) 2008 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Elliott Baron <ebaron@redhat.com> - initial API and implementation
 *******************************************************************************/
package org.eclipse.linuxtools.profiling.ui;

import java.net.URI;

import org.eclipse.cdt.debug.core.CDebugCorePlugin;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.debug.core.sourcelookup.containers.LocalFileStorage;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugModelPresentation;
import org.eclipse.debug.ui.sourcelookup.ISourceLookupResult;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.linuxtools.profiling.launch.IRemoteFileProxy;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

import com.runtimeverification.match.RVMatchPlugin;

public class ProfileUIUtils {
    /**
     * Open a file in the Editor at the specified offset, highlighting the given length
     *
     * @param path : Absolute path pointing to the file which will be opened.
     * @param line - line number to select, 0 to not select a line
     * @param project - current project object
     * @throws BadLocationException - Line number not valid in file
     * @throws PartInitException if the editor could not be initialized
     * @throws CoreException if the proxy cannot be initialized
     * @since 3.1
     */
    public static void openEditorAndSelect(String path, int line, IProject project) throws PartInitException, BadLocationException, CoreException {
        IWorkbenchPage activePage = RVMatchPlugin.getDefault().getWorkbench().getActiveWorkbenchWindow().getActivePage();
        IRemoteFileProxy proxy = null;
        proxy = RemoteProxyManager.getInstance().getFileProxy(project);
        IFileStore file = proxy.getResource(path);
        if (file.fetchInfo().exists()) {
            IEditorPart editor = IDE.openEditorOnFileStore(activePage, file);
            if (editor instanceof ITextEditor) {
                ITextEditor textEditor = (ITextEditor) editor;

                if (line > 0) {
                    IDocumentProvider provider = textEditor.getDocumentProvider();
                    IDocument document = provider.getDocument(textEditor.getEditorInput());

                    int start = document.getLineOffset(line - 1); //zero-indexed
                    textEditor.selectAndReveal(start, 0);
                }
            }
        }
    }

    /**
     * Opens the specified file in an editor (or selects an already open
     * editor) and highlights the specified line.
     * @param result - result of performing source lookup with a org.eclipse.debug.core.model.ISourceLocator
     * @param line - line number to select, 0 to not select a line
     * @throws PartInitException - Failed to open editor
     * @throws BadLocationException - Line number not valid in file
     * @see DebugUITools#lookupSource(Object, org.eclipse.debug.core.model.ISourceLocator)
     */
    public static void openEditorAndSelect(ISourceLookupResult result, int line) throws PartInitException, BadLocationException {
        IEditorInput input = result.getEditorInput();
        String editorID = result.getEditorId();

        if (input == null || editorID == null) {
            // Consult the CDT DebugModelPresentation
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

                IDebugModelPresentation pres = DebugUITools.newDebugModelPresentation(CDebugCorePlugin.getUniqueIdentifier());
                input = pres.getEditorInput(sourceElement);
                editorID = pres.getEditorId(input, sourceElement);
                pres.dispose();
            }
        }
        if (input != null && editorID != null) {
            // Open the editor
            IWorkbenchPage activePage = RVMatchPlugin.getDefault().getWorkbench().getActiveWorkbenchWindow().getActivePage();

            IEditorPart editor = IDE.openEditor(activePage, input, editorID);
            // Select the line
            if (editor instanceof ITextEditor) {
                ITextEditor textEditor = (ITextEditor) editor;

                if (line > 0) {
                    IDocumentProvider provider = textEditor.getDocumentProvider();
                    IDocument document = provider.getDocument(textEditor.getEditorInput());

                    IRegion lineRegion = document.getLineInformation(line - 1); //zero-indexed
                    textEditor.selectAndReveal(lineRegion.getOffset(), lineRegion.getLength());
                }
            }
        }
    }
}