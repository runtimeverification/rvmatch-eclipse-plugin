package com.runtimeverification.match;


import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.linuxtools.valgrind.launch.ProjectBuildListener;
import org.eclipse.linuxtools.valgrind.launch.ValgrindLaunchConfigurationDelegate;
import org.eclipse.linuxtools.valgrind.ui.ValgrindViewPart;

import com.runtimeverification.match.handlers.SelectBuildHandler;

import org.eclipse.linuxtools.valgrind.core.IValgrindMessage;
import org.eclipse.linuxtools.valgrind.core.ValgrindCoreParser;
import org.eclipse.cdt.debug.core.CDebugUtils;

public class ReportExecutionOutput {
	private ValgrindLaunchConfigurationDelegate delegate;
    
    public ReportExecutionOutput(ILaunch launch) {
		ILaunchConfiguration config = launch.getLaunchConfiguration();
		try {
			project = CDebugUtils.getCProject(config);
			outputPath = SelectBuildHandler.getReportFilePath();
			RVMatchPlugin.getDefault().createView("RV Match", null);
			view = RVMatchPlugin.getDefault().getView();
			parser = new ValgrindCoreParser(launch);
			delegate = new ValgrindLaunchConfigurationDelegate(RVMatchPlugin.MARKER_TYPE);
			bin = null; // because opening a named pipe is blocking we postpone opening it to the first usage.
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
					new ProjectBuildListener(project.getProject(), RVMatchPlugin.PLUGIN_ID, RVMatchPlugin.MARKER_TYPE), IResourceChangeEvent.POST_BUILD);
		} else {
			System.out.println("File " + outputPath.toString() + " not found.");
		}
	}

	private List<List<String>> parseCSV() throws IOException {
		List<List<String>> records = new ArrayList<>();
		List<String> record;
		while ((record = parseCSVRecord()) != null) {
			records.add(record);
		}
		return records;
	}

	public List<String> parseCSVRecord() throws IOException {
		if (bin == null) {
			bin = new BufferedReader(new FileReader(outputPath.toFile()));
		}
		return parseCSVRecord(bin);
	}

	private List<String> parseCSVRecord(BufferedReader bin) throws IOException {
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
				new ProjectBuildListener(project.getProject(), RVMatchPlugin.PLUGIN_ID, RVMatchPlugin.MARKER_TYPE), IResourceChangeEvent.POST_BUILD);
	}

	/**
	 * @param record
	 * @throws IOException
	 * @throws CoreException
	 */
	public void handleRecord(List<String> record) throws IOException, CoreException {
		BufferedReader br = new BufferedReader(new StringReader(formatRecord(record)));
		List<IValgrindMessage> resultList = parser.parseBuffer(br);
		messages.addAll(resultList);
		delegate.createMarkers(resultList.toArray(new IValgrindMessage[resultList.size()]));
		view.setMessages(messages.toArray(new IValgrindMessage[messages.size()]));

		// refresh view
		RVMatchPlugin.getDefault().refreshView();

		// show view
		RVMatchPlugin.getDefault().showView();
	}

	private String formatRecord(List<String> record) {
		String title = record.get(0), msg = record.get(1), errType = record.get(2), citation = record.get(3), reason = record.get(4), trace = record.get(5);
		StringBuilder sb = new StringBuilder();
		sb.append(' ').append(msg);
		if (!trace.isEmpty()) {
			sb.append("\n   ");
			if (! trace.contains("at ")) {
				sb.append("at (").append(trace).append(')');
			} else {
				sb.append(trace);
			}
		}
		if (!reason.isEmpty()) {
			sb.append("\n  ").append(reason);
		}
		sb.append("\n  ").append(errType).append(" (").append(title).append(").");
		try {
			List<String> citationRecord;
			BufferedReader citStream = new BufferedReader(new StringReader(citation));
			while ((citationRecord = parseCSVRecord(citStream)) != null) {
				String source = citationRecord.get(0);
				String section = citationRecord.get(1);
				String details = citationRecord.get(2);
				String url = citationRecord.get(3);
				sb.append("\n   see ").append(source).append(" section ").append(section);
				if (! details.isEmpty()) {
					sb.append(':').append(details);
				}
				sb.append(' ').append(url);
			}
		} catch (IOException e) {
			RVMatchPlugin.log(IStatus.ERROR, "Malformated citations", e);
		}
		return sb.toString();
	}

	/*
	 * To avoid blocking because no process has written to the pipe, we write here something.
	 */
	public void terminate() {
		try {
			OutputStream bout = new FileOutputStream(outputPath.toFile());
			bout.write("\n".getBytes());
			bout.close();
		} catch (IOException e) {
			RVMatchPlugin.log(IStatus.ERROR, "Error while closing the pipe", e);
		}
	}
}
