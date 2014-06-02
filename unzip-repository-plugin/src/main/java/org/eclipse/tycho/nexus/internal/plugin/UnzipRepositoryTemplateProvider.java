/*******************************************************************************
 * Copyright (c) 2010, 2014 SAP SE and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP SE - initial API and implementation
 *    Angel Lopez-Cima - convert from plexus to javax.inject annotations (bug 432793)
 *******************************************************************************/
package org.eclipse.tycho.nexus.internal.plugin;

import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.templates.TemplateProvider;
import org.sonatype.nexus.templates.TemplateSet;
import org.sonatype.nexus.templates.repository.AbstractRepositoryTemplateProvider;

// Alike org.sonatype.nexus.templates.repository.DefaultRepositoryTemplateProvider
@Named(UnzipRepositoryTemplateProvider.PROVIDER_ID)
@Singleton
public class UnzipRepositoryTemplateProvider extends AbstractRepositoryTemplateProvider implements TemplateProvider {
    static final String PROVIDER_ID = "unzipRepo-templates";

    @Override
    public TemplateSet getTemplates() {
        final TemplateSet templates = new TemplateSet(null);

        final String templateId = PROVIDER_ID;
        final String templateDescription = "Unzip Repository Template";
        templates.add(new UnzipRepositoryTemplate(this, templateId, templateDescription));

        return templates;
    }
}
