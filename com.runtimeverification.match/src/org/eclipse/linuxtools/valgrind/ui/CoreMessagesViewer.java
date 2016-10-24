/*******************************************************************************
 * Copyright (c) 2009 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Elliott Baron <ebaron@redhat.com> - initial API and implementation
 *******************************************************************************/
package org.eclipse.linuxtools.valgrind.ui;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.model.ISourceLocator;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.debug.ui.sourcelookup.ISourceLookupResult;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.linuxtools.valgrind.core.ValgrindError;
import org.eclipse.linuxtools.valgrind.core.ValgrindStackFrame;
import org.eclipse.linuxtools.profiling.ui.ProfileUIUtils;
import org.eclipse.linuxtools.valgrind.core.IValgrindMessage;
import org.eclipse.linuxtools.valgrind.ui.CollapseAction;
import org.eclipse.linuxtools.valgrind.ui.ExpandAction;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import com.runtimeverification.match.RVMatchCitation;
import com.runtimeverification.match.RVMatchPlugin;

/**
 * The inner tree view that holds the output messages to be displayed. Also hold
 * double click listeners.
 */
public class CoreMessagesViewer {

    static ImageRegistry imageRegistry = new ImageRegistry();

    private static final String VALGRIND_ERROR = "Valgrind_Error"; //$NON-NLS-1$
    /**
     * @since 0.10
     */
    private static final String VALGRIND_INFO = "Valgrind_Info"; //$NON-NLS-1$
    private static final String LINK = "RV_Link"; //$NON-NLS-1$
    private static final String VALGRIND_ERROR_IMAGE = "icons/RV-error.png"; //$NON-NLS-1$
    /**
     * @since 0.10
     */
    public static final String VALGRIND_INFO_IMAGE = "icons/RV-info.png"; //$NON-NLS-1$
    public static final String LINK_IMAGE = "icons/link.png"; //$NON-NLS-1$
    private IDoubleClickListener doubleClickListener;
    private ITreeContentProvider contentProvider;

    private TreeViewer viewer;

    /**
     * @param parent  the parent control
     * @param style   an SWT style
     */
    public CoreMessagesViewer(Composite parent, int style) {
        viewer = new TreeViewer(parent, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL | style);
        viewer.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));
        if (imageRegistry.getDescriptor(VALGRIND_ERROR) == null) {
            ImageDescriptor d = AbstractUIPlugin.imageDescriptorFromPlugin(RVMatchPlugin.PLUGIN_ID, VALGRIND_ERROR_IMAGE);
            if (d != null) {
                imageRegistry.put(VALGRIND_ERROR, d);
            }
        }
        if (imageRegistry.getDescriptor(VALGRIND_INFO) == null) {
            ImageDescriptor d = AbstractUIPlugin.imageDescriptorFromPlugin(RVMatchPlugin.PLUGIN_ID, VALGRIND_INFO_IMAGE);
            if (d != null) {
                imageRegistry.put(VALGRIND_INFO, d);
            }
        }
        if (imageRegistry.getDescriptor(LINK) == null) {
            ImageDescriptor d = AbstractUIPlugin.imageDescriptorFromPlugin(RVMatchPlugin.PLUGIN_ID, LINK_IMAGE);
            if (d != null) {
                imageRegistry.put(LINK, d);
            }
        }
        contentProvider = new ITreeContentProvider() {

            @Override
            public Object[] getChildren(Object parentElement) {
                if (parentElement instanceof Object[]) {
                    return (Object[]) parentElement;
                }
                return ((IValgrindMessage) parentElement).getChildren();
            }

            @Override
            public Object getParent(Object element) {
                return ((IValgrindMessage) element).getParent();
            }

            @Override
            public boolean hasChildren(Object element) {
                return getChildren(element).length > 0;
            }

            @Override
            public Object[] getElements(Object inputElement) {
                return getChildren(inputElement);
            }

            @Override
            public void dispose() {}

            @Override
            public void inputChanged(Viewer viewer, Object oldInput,
                    Object newInput) {}

        };
        viewer.setContentProvider(contentProvider);

        viewer.setLabelProvider(new LabelProvider() {
            @Override
            public String getText(Object element) {
                return ((IValgrindMessage) element).getText();
            }

            @Override
            public Image getImage(Object element) {
                Image image;
                if (element instanceof ValgrindStackFrame) {
                    image = DebugUITools.getImage(IDebugUIConstants.IMG_OBJS_STACKFRAME);
                } else if (element instanceof ValgrindError)  {
                    image = imageRegistry.get(VALGRIND_ERROR);
                } else if (element instanceof RVMatchCitation){
                	image = imageRegistry.get(LINK);
                } else {
                    image = imageRegistry.get(VALGRIND_INFO);
                }
                return image;
            }

        });

		doubleClickListener = event -> {
			Object element = ((TreeSelection) event.getSelection()).getFirstElement();
			if (element instanceof ValgrindStackFrame) {
				ValgrindStackFrame frame = (ValgrindStackFrame) element;
				// locator stored in the frame should be valid for the lifespan of the frame object
				ISourceLocator locator = frame.getSourceLocator();
				ISourceLookupResult result = DebugUITools.lookupSource(frame.getFile(), locator);
				try {
					if (result.getSourceElement() != null)
						ProfileUIUtils.openEditorAndSelect(result, frame.getLine());
					else // if lookup failed there is good chance we can just open the file by name
						ProfileUIUtils.openEditorAndSelect(frame.getFile(), frame.getLine(),
								RVMatchPlugin.getDefault().getProfiledProject());
				} catch (BadLocationException | CoreException e) {
					RVMatchPlugin.log(e);
				}
			} else if (element instanceof RVMatchCitation) {
				RVMatchCitation citation = (RVMatchCitation) element;
				String source = citation.getSource();
				String section = citation.getSection().trim();
				try {
					if ("C11".equals(source)) {
						int endSection = section.indexOf(' ');
						if (endSection != -1) {
							section = section.substring(0, endSection);
						}
						String page = RVMatchPlugin.getDefault().getC11Page(section);
						PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(
								new URL("http://www.open-std.org/jtc1/sc22/wg14/www/docs/n1548.pdf#page="+page+"&search="+section));
					} else	if ("CERT-C".equals(source)) {
						PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(
								new URL("https://www.google.com/search?q=\""+section+"\"&as_sitesearch=securecoding.cert.org&btnI"));
					}
				} catch (PartInitException | MalformedURLException e) {
					RVMatchPlugin.logErrorMessage("Could not launch webpage");
				}
			} else {
				if (viewer.getExpandedState(element)) {
					viewer.collapseToLevel(element, AbstractTreeViewer.ALL_LEVELS);
				} else {
					viewer.expandToLevel(element, 1);
				}
			 }
		};
        viewer.addDoubleClickListener(doubleClickListener);

        final ExpandAction expandAction = new ExpandAction(viewer);
        final CollapseAction collapseAction = new CollapseAction(viewer);

        MenuManager manager = new MenuManager();
        manager.addMenuListener(manager1 -> {
		    ITreeSelection selection = (ITreeSelection) viewer.getSelection();
		    Object element = selection.getFirstElement();
		    if (contentProvider.hasChildren(element)) {
		        manager1.add(expandAction);
		        manager1.add(collapseAction);
		    }
		});

        manager.setRemoveAllWhenShown(true);
        Menu contextMenu = manager.createContextMenu(viewer.getTree());
        viewer.getControl().setMenu(contextMenu);
    }

    /**
     * @return the double click listener
     */
    public IDoubleClickListener getDoubleClickListener() {
        return doubleClickListener;
    }

    /**
     * @return the tree viewer
     */
    public TreeViewer getTreeViewer() {
        return viewer;
    }
}