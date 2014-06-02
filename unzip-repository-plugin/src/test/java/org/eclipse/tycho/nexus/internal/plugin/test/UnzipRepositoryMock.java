/*******************************************************************************
 * Copyright (c) 2010, 2014 SAP SE and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP SE - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.nexus.internal.plugin.test;

import org.easymock.EasyMock;
import org.eclipse.tycho.nexus.internal.plugin.DefaultUnzipRepository;
import org.sonatype.nexus.proxy.attributes.AttributesHandler;
import org.sonatype.nexus.proxy.item.LinkPersister;
import org.sonatype.nexus.proxy.item.RepositoryItemUidFactory;
import org.sonatype.nexus.proxy.item.uid.RepositoryItemUidAttributeManager;
import org.sonatype.nexus.proxy.repository.LocalStatus;
import org.sonatype.nexus.proxy.repository.Repository;
import org.sonatype.nexus.proxy.storage.local.LocalRepositoryStorage;

public class UnzipRepositoryMock extends DefaultUnzipRepository {

    private final Repository masterRepository;
    private final LocalRepositoryStorage localStorage;
    private final RepositoryItemUidFactory repositoryItemUidFactory;

    @Override
    public RepositoryItemUidAttributeManager getRepositoryItemUidAttributeManager() {
        return masterRepository.getRepositoryItemUidAttributeManager();
    }

    public static DefaultUnzipRepository createUnzipRepository(final Repository masterRepo,
            final LinkPersister linkPersister, final RepositoryItemUidFactory repositoryItemUidFactory) {
        return new UnzipRepositoryMock(masterRepo, linkPersister, repositoryItemUidFactory);
    }

    private UnzipRepositoryMock(final Repository masterRepository, final LinkPersister linkPersister,
            final RepositoryItemUidFactory repositoryItemUidFactory) {
        super(null, null, null, null, null);
        this.masterRepository = masterRepository;
        this.localStorage = new FSLocalRepositoryStorageMock(linkPersister);
        this.repositoryItemUidFactory = repositoryItemUidFactory;
    }

    @Override
    public AttributesHandler getAttributesHandler() {
        return EasyMock.createNiceMock(AttributesHandler.class);
    }

    @Override
    public String getId() {
        return UnzipRepositoryMock.class.getName();
    }

    @Override
    public LocalRepositoryStorage getLocalStorage() {
        return localStorage;
    }

    @Override
    protected RepositoryItemUidFactory getRepositoryItemUidFactory() {
        return repositoryItemUidFactory;
    }

    @Override
    public LocalStatus getLocalStatus() {
        return LocalStatus.IN_SERVICE;
    }

    @Override
    public Repository getMasterRepository() {
        return masterRepository;
    }

    @Override
    public boolean isUseVirtualVersion() {
        return true;
    }

}
