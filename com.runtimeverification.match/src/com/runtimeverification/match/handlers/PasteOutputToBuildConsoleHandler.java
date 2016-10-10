package com.runtimeverification.match.handlers;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
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
import org.eclipse.linuxtools.valgrind.core.ValgrindInfo;
import org.eclipse.linuxtools.valgrind.core.ValgrindStackFrame;
import org.eclipse.cdt.debug.core.CDebugUtils;

public class PasteOutputToBuildConsoleHandler extends AbstractHandler {
    public static final String PLUGIN_ID = "com.runtimeverification.match"; //$NON-NLS-1$
    public static final String MARKER_TYPE = PLUGIN_ID + ".marker"; //$NON-NLS-1$

	public static void checkRVMatchOutput(ILaunch launch) throws CoreException, IOException {
		ILaunchConfiguration config = launch.getLaunchConfiguration();
		ICProject project = CDebugUtils.getCProject(config);
		Path outputFile = SelectBuildHandler.getReportFilePath(project.getProject());
		if (outputFile.toFile().exists()) {
			System.out.println("Processing " + outputFile.toString());
			IValgrindMessage[] messages = parseLogs(outputFile, launch);
			outputFile.toFile().delete();
			RVMatchPlugin.getDefault().createView("RV Match", null);
			// set log messages
			ValgrindViewPart view = RVMatchPlugin.getDefault().getView();
			view.setMessages(messages);

			// refresh view
			RVMatchPlugin.getDefault().refreshView();

			// show view
			RVMatchPlugin.getDefault().showView();
			ResourcesPlugin.getWorkspace().addResourceChangeListener(
					new ProjectBuildListener(project.getProject()), IResourceChangeEvent.POST_BUILD);
		} else {
			System.out.println("File " + outputFile.toString() + " not found.");
		}
	}

	private static List<List<String>> parseCSV(File in) throws IOException {
		List<List<String>> records = new ArrayList<>();
		List<String> record = new ArrayList<>();
		BufferedReader bin = new BufferedReader(new FileReader(in));
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
					records.add(record);
					record = new ArrayList<>();
					skipWhiteSpace = true;
				}
			} else {
				value.append((char)c);
			}
		}
		return records;
	}


	public static IValgrindMessage[] parseLogs(Path outputPath, ILaunch launch) throws IOException, CoreException {

		File rvFile = outputPath.toFile();
		List<List<String>> records = parseCSV(rvFile);
		File valgrindFile = outputPath.getParent().resolve("valgrind_1.txt").toFile();
		PrintStream writer = new PrintStream(valgrindFile); 
		for (List<String> record : records) {
			writer.printf(" %s %s (%s).\n  See %s\n  %s\n", record.get(1), record.get(2), record.get(0), record.get(3), record.get(4));
		}
		writer.close();
		

		List<IValgrindMessage> messages = new ArrayList<>();

		File log = valgrindFile;
		ValgrindCoreParser parser = new ValgrindCoreParser(log, launch);
		IValgrindMessage[] results = parser.getMessages();

		if (results.length == 0){
			results = new IValgrindMessage[1];
			results[0] = new ValgrindInfo(null, "RV Match found no problems to report", launch); //$NON-NLS-1$
		}
		messages.addAll(Arrays.asList(results));
		createMarkers(results);

		return messages.toArray(new IValgrindMessage[messages.size()]);
	}

	public static void createMarkers(IValgrindMessage[] messages) throws CoreException {
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

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		ILaunch[] launches = manager.getLaunches();
		ILaunch launch = launches[launches.length-1];
        try {
			checkRVMatchOutput(launch);
		} catch (CoreException | IOException e) {
			throw new ExecutionException(e.getMessage(), e);
		}
		
		return null;
	}

}
