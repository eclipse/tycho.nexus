/*******************************************************************************
 * Copyright (c) 2014 Angel Lopez-Cima and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Angel Lopez-Cima - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.nexus.internal.plugin;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.sisu.EagerSingleton;
import org.sonatype.nexus.plugin.PluginIdentity;

/**
 * @since 0.14.0
 */
@Named
@EagerSingleton
public class UnzipPlugin extends PluginIdentity {

    @Inject
    public UnzipPlugin() throws Exception {
        super("org.eclipse.tycho.nexus", "unzip-repository-plugin");
    }
}
