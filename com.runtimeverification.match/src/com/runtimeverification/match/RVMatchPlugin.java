package com.runtimeverification.match;
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

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.linuxtools.valgrind.ui.IValgrindToolView;
import org.eclipse.linuxtools.valgrind.ui.ValgrindViewPart;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import java.util.HashMap;

public class RVMatchPlugin extends AbstractUIPlugin {

    // The plug-in ID
    public static final String PLUGIN_ID = "com.runtimeverification.match"; //$NON-NLS-1$
    public static final String MARKER_TYPE = PLUGIN_ID + ".marker"; //$NON-NLS-1$

    // Extension point constants
    private static final String VIEW_EXT_ID = "rvMatchToolViews"; //$NON-NLS-1$
    protected static final String EXT_ELEMENT = "view"; //$NON-NLS-1$
    protected static final String EXT_ATTR_ID = "definitionId"; //$NON-NLS-1$
    protected static final String EXT_ATTR_CLASS = "class"; //$NON-NLS-1$

    private HashMap<String, IConfigurationElement> toolMap;


    // The shared instance
    private static RVMatchPlugin plugin;

    // The launch mode this plug-in supports
    public static final String LAUNCH_MODE = "linuxtools"; //$NON-NLS-1$

    private ValgrindViewPart view;
    // The page containing the created Valgrind view
    private IWorkbenchPage activePage;

    // The last profiled project
    private IProject project;

    /**
     * The constructor
     */
    public RVMatchPlugin() {
    }

    /**
     * Convenience method which returns the unique identifier of this plugin.
     * @return The identifier.
     * @since 1.1
     */
    public static String getUniqueIdentifier() {
        if (getDefault() == null) {
            // If the default instance is not yet initialized,
            // return a static identifier. This identifier must
            // match the plugin id defined in plugin.xml
            return PLUGIN_ID;
        }
        return getDefault().getBundle().getSymbolicName();
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);
    }

    /**
     * Returns the shared instance
     *
     * @return the shared instance
     */
    public static RVMatchPlugin getDefault() {
        return plugin;
    }

    public static Shell getActiveWorkbenchShell() {
        IWorkbenchWindow window = getDefault().getWorkbench().getActiveWorkbenchWindow();
        if (window != null) {
            return window.getShell();
        }
        return null;
    }

    public static Shell getShell() {
        if (getActiveWorkbenchShell() != null) {
            return getActiveWorkbenchShell();
        }
        IWorkbenchWindow[] windows = getDefault().getWorkbench().getWorkbenchWindows();
        return windows[0].getShell();
    }
    
    /**
     * Creates the Valgrind view specialized of the valgrind tool.
     *
     * @param contentDescription  the content description
     * @param toolID              the valgrind tool identifier
     */
    public void createView(final String contentDescription, final String toolID) {
        Display.getDefault().syncExec(() -> {
		    try {
		        activePage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		        activePage.showView(IValgrindToolView.VIEW_ID, null, IWorkbenchPage.VIEW_CREATE);
		        // Bug #366831 Need to show the view otherwise the toolbar is disposed.
		        activePage.showView(IValgrindToolView.VIEW_ID);

		        // create the view's tool specific controls and populate content description
		        view.createDynamicContent(contentDescription, toolID);

		        view.refreshView();
		    } catch (CoreException e) {
		        e.printStackTrace();
		    }
		});
    }

    /**
     * Shows the Valgrind view in the active page and gives it focus.
     */
    public void showView() {
        Display.getDefault().syncExec(() -> {
		    try {
		        activePage.showView(IValgrindToolView.VIEW_ID);
		    } catch (PartInitException e) {
		        e.printStackTrace();
		    }
		});
    }

    /**
     * Refreshes the Valgrind view
     */
    public void refreshView() {
        if (view != null) {
            Display.getDefault().syncExec(() -> view.refreshView());
        }
    }

    /**
     * Empties the contents of the view and restores its original state.
     */
    public void resetView() {
        if (view != null) {
            Display.getDefault().syncExec(() -> {
			    try {
			        view.createDynamicContent("No RV Match output to display at this time.", null); //$NON-NLS-1$
			    } catch (CoreException e) {
			        e.printStackTrace();
			    }
			});
        }
    }

    public void setView(ValgrindViewPart view) {
        this.view = view;
    }

    /**
     * @return the Valgrind view
     */
    public ValgrindViewPart getView() {
        return view;
    }

    private void initializeToolMap() {
        toolMap = new HashMap<>();
        IExtensionPoint extPoint = Platform.getExtensionRegistry().getExtensionPoint(PLUGIN_ID, VIEW_EXT_ID);
        IConfigurationElement[] configs = extPoint.getConfigurationElements();
        for (IConfigurationElement config : configs) {
            if (config.getName().equals(EXT_ELEMENT)) {
                String id = config.getAttribute(EXT_ATTR_ID);
                if (id != null && config.getAttribute(EXT_ATTR_CLASS) != null) {
                    toolMap.put(id, config);
                }
            }
        }
    }

    private HashMap<String, IConfigurationElement> getToolMap() {
        if (toolMap == null) {
            initializeToolMap();
        }
        return toolMap;
    }

    /**
     * Returns the view specialized of a tool.
     *
     * @param id The valgrind view identifier
     * @return IValgrindToolView The valgrind view
     * @throws CoreException The view cannot be retrieved
     */
    public IValgrindToolView getToolView(String id) throws CoreException {
        IValgrindToolView view = null;
        IConfigurationElement config = getToolMap().get(id);
        if (config != null) {
            Object obj = config.createExecutableExtension(EXT_ATTR_CLASS);
            if (obj instanceof IValgrindToolView) {
                view = (IValgrindToolView) obj;
            }
        }
        if (view == null) {
            throw new CoreException(new Status(IStatus.ERROR, PLUGIN_ID, "Cannot retrieve view from extension")); //$NON-NLS-1$
        }
        return view;
    }

	/**
	 * Set the project to be profiled
	 *
	 * @param project - project to be profiled
	 */
	public void setProfiledProject(IProject project) {
		this.project = project;
	}

	/**
	 * Get the project to be profiled
	 *
	 * @return project
	 */
	public IProject getProfiledProject() {
		return project;
	}

    

    /**
     * Logs the specified status with this plug-in's log.
     *
     * @param status
     *            status to log
     * @since 1.1
     */
    public static void log(IStatus status) {
		if (plugin != null)
			getDefault().getLog().log(status);
		else {
			// log on console when plugin is not loaded, can happen when run junit without osgi
			System.err.println(status.getMessage());
			if (status.getException() != null) {
				status.getException().printStackTrace(System.err);
			}
		}
	}
    /**
     * Logs an internal error with the specified message.
     *
     * @param message
     *            the error message to log
     * @since 1.1
     */
    public static void logErrorMessage(String message) {
		log(new Status(IStatus.ERROR, PLUGIN_ID, IStatus.ERROR, message, null));
    }

    /**
     * Logs an internal error with the specified throwable
     *
     * @param e
     *            the exception to be logged
     * @since 1.1
     */
    public static void log(Throwable e) {
		log(new Status(IStatus.ERROR, PLUGIN_ID, IStatus.ERROR, e.getMessage(), e));
    }

    public static void log(int status, String msg, Throwable e) {
        plugin.getLog().log(new Status(status, PLUGIN_ID, IStatus.OK, msg, e));
    }

    public static void log(int status, String msg) {
        log(status, msg, null);
    }
    
    
    
    
}
