/*******************************************************************************
 * Copyright (c) 2009 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Elliott Baron <ebaron@redhat.com> - initial API and implementation
 *    Alena Laskavaia - javadoc comments and cleanup
 *******************************************************************************/
package org.eclipse.linuxtools.valgrind.core;

import org.eclipse.debug.core.ILaunch;
import org.eclipse.linuxtools.valgrind.core.AbstractValgrindMessage;
import org.eclipse.linuxtools.valgrind.core.IValgrindMessage;

/**
 * Valgrind Error message object
 */
public class ValgrindError extends AbstractValgrindMessage {

    /**
     * Constructor
     * @param parent - parent message
     * @param text - message test cannot be null
     * @param launch - launch object can be null
     * @param pid - process pid
     */
    public ValgrindError(IValgrindMessage parent, String text) {
        super(parent, text);
    }

    @Override
    public String getText() {
        return super.getText(); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
