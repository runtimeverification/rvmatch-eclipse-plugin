package com.runtimeverification.match.handlers;


import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;
import java.util.Stack;

import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.ISourceLocator;
import org.eclipse.debug.core.sourcelookup.containers.LocalFileStorage;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.sourcelookup.ISourceLookupResult;
import org.eclipse.linuxtools.valgrind.launch.ProjectBuildListener;
import org.eclipse.linuxtools.valgrind.ui.ValgrindViewPart;

import com.runtimeverification.match.RVMatchPlugin;

import org.eclipse.linuxtools.valgrind.core.IValgrindMessage;
import org.eclipse.linuxtools.valgrind.core.ValgrindCoreParser;
import org.eclipse.linuxtools.valgrind.core.ValgrindError;
import org.eclipse.linuxtools.valgrind.core.ValgrindStackFrame;
import org.eclipse.cdt.debug.core.CDebugUtils;

public class PasteOutputToBuildConsoleHandler {
    public static final String PLUGIN_ID = "com.runtimeverification.match"; //$NON-NLS-1$
    public static final String MARKER_TYPE = PLUGIN_ID + ".marker"; //$NON-NLS-1$
	private ILaunch launch;
    
    public PasteOutputToBuildConsoleHandler(ILaunch launch) {
    	this.launch = launch;
		ILaunchConfiguration config = launch.getLaunchConfiguration();
		try {
			project = CDebugUtils.getCProject(config);
			outputPath = SelectBuildHandler.getReportFilePath(project.getProject());
			bin = new BufferedReader(new FileReader(outputPath.toFile()));
			RVMatchPlugin.getDefault().createView("RV Match", null);
			view = RVMatchPlugin.getDefault().getView();
			parser = new ValgrindCoreParser(launch);

		} catch (CoreException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    }
    
    public IProject getProject() {
    	return project.getProject();
    }

	public void checkRVMatchOutput() throws CoreException, IOException {
		if (outputPath.toFile().exists()) {
			System.out.println("Processing " + outputPath.toString());
			parseLogs();
			outputPath.toFile().delete();
			ResourcesPlugin.getWorkspace().addResourceChangeListener(
					new ProjectBuildListener(project.getProject()), IResourceChangeEvent.POST_BUILD);
		} else {
			System.out.println("File " + outputPath.toString() + " not found.");
		}
	}

	private List<List<String>> parseCSV() throws IOException {
		List<List<String>> records = new ArrayList<>();
		List<String> record;
		while ((record = parseCVSRecord()) != null) {
			records.add(record);
		}
		return records;
	}

	public List<String> parseCVSRecord() throws IOException {
		List<String> record = new ArrayList<>();
		boolean inString = false;
		boolean skipWhiteSpace = true;
		StringBuilder value = new StringBuilder();
		for (int c = bin.read(); c != -1; c = bin.read()) {
			if (skipWhiteSpace) {
				if (Character.isWhitespace(c)) continue;
				skipWhiteSpace = false;
			}
			if (inString) {
				if (c == '"') {
					inString = false;
					continue;
				} else {
					value.append((char) c);
				}
			} else if (c == '"') {
				inString = true;
				continue;
			} else if (c == ',' || c == '\n') {
				record.add(value.toString().trim());
				value.setLength(0);
				if (c == '\n') {
					return record;
				}
			} else {
				value.append((char)c);
			}
		}
		return null;
	}

	private List<IValgrindMessage> messages = new ArrayList<>();
	private ICProject project;
	private ValgrindViewPart view;
	private Path outputPath;
	private BufferedReader bin;
	private ValgrindCoreParser parser;

	public void parseLogs() throws IOException, CoreException {
			
		List<List<String>> records = parseCSV();
		for (List<String> record : records) {
			handleRecord(record);

		}
		ResourcesPlugin.getWorkspace().addResourceChangeListener(
				new ProjectBuildListener(project.getProject()), IResourceChangeEvent.POST_BUILD);
	}

	/**
	 * @param parser
	 * @param record
	 * @throws IOException
	 * @throws CoreException
	 */
	public void handleRecord(List<String> record) throws IOException, CoreException {
		StringBuilder sb = new StringBuilder();
		Formatter formatter = new Formatter(sb);
		formatter.format(" %s %s (%s).\n  See %s\n  %s\n", record.get(1), record.get(2), record.get(0), record.get(3), record.get(4));
		formatter.close();
		BufferedReader br = new BufferedReader(new StringReader(sb.toString()));
		List<IValgrindMessage> resultList = parser.parseBuffer(br);
		messages.addAll(resultList);
		createMarkers(resultList.toArray(new IValgrindMessage[resultList.size()]));
		view.setMessages(messages.toArray(new IValgrindMessage[messages.size()]));

		// refresh view
		RVMatchPlugin.getDefault().refreshView();

		// show view
		RVMatchPlugin.getDefault().showView();
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
								marker = resource.createMarker(MARKER_TYPE);
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
