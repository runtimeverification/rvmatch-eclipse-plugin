package com.runtimeverification.match;

import org.eclipse.debug.core.ILaunch;
import org.eclipse.linuxtools.valgrind.core.AbstractValgrindMessage;
import org.eclipse.linuxtools.valgrind.core.IValgrindMessage;

public class RVMatchCitation extends AbstractValgrindMessage {
	private String source;
	private String section;
	private String details;

	public RVMatchCitation(IValgrindMessage parent, String text, ILaunch launch, String source, String section, String details) {
		super(parent, text, launch);
		this.source = source;
		this.section = section;
		this.details = details;
	}
	
	public String getSource() {
		return source;
	}
	public String getSection() {
		return section;
	}
	public String getDetails() {
		return details;
	}

}
