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
import javax.inject.Singleton;

import org.sonatype.nexus.plugins.ui.contribution.UiContributionBuilder;
import org.sonatype.nexus.plugins.ui.contribution.UiContributorSupport;

@Named
@Singleton
public class UnzipUiContributor extends UiContributorSupport {
    @Inject
    public UnzipUiContributor(final UnzipPlugin owner) {
        super(owner);
    }

    protected void customize(final UiContributionBuilder builder) {
        builder.withDependency(UnzipRepositoryResourceBundle.JS_SCRIPT_PATH);
        builder.withDependency(UnzipRepositoryResourceBundle.JS_SCRIPT_BOOT_PATH);
    }
}
