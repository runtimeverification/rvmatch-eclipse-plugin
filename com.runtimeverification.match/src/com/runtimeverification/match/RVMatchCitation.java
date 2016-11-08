package com.runtimeverification.match;

import org.eclipse.linuxtools.valgrind.core.AbstractValgrindMessage;
import org.eclipse.linuxtools.valgrind.core.IValgrindMessage;

public class RVMatchCitation extends AbstractValgrindMessage {
	private String source;
	private String section;
	private String details;
	private String url;

	public RVMatchCitation(IValgrindMessage parent, String text, String source, String section, String details, String url) {
		super(parent, text);
		this.source = source;
		this.section = section;
		this.details = details;
		this.url = url;
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

	public String getUrl() {
		return url;
	}
}
