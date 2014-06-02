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

import org.sonatype.configuration.ConfigurationException;
import org.sonatype.nexus.configuration.ExternalConfiguration;
import org.sonatype.nexus.configuration.application.ApplicationConfiguration;
import org.sonatype.nexus.configuration.model.CRepositoryCoreConfiguration;
import org.sonatype.nexus.proxy.repository.AbstractShadowRepositoryConfigurator;
import org.sonatype.nexus.proxy.repository.Repository;

@Named
@Singleton
public class UnzipRepositoryConfigurator extends AbstractShadowRepositoryConfigurator {

    @Override
    public void doApplyConfiguration(final Repository repository, final ApplicationConfiguration configuration,
            final CRepositoryCoreConfiguration coreConfig) throws ConfigurationException {
        super.doApplyConfiguration(repository, configuration, coreConfig);

        final ExternalConfiguration<?> externalConfiguration = coreConfig.getExternalConfiguration();
        final UnzipRepositoryConfiguration unzipRepoConfig = (UnzipRepositoryConfiguration) externalConfiguration
                .getConfiguration(false);

        repository.adaptToFacet(UnzipRepository.class).setUseVirtualVersion(unzipRepoConfig.isUseVirtualVersion());
    }
}
