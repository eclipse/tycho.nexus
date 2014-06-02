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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Collection;
import java.util.LinkedList;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.sonatype.nexus.configuration.Configurator;
import org.sonatype.nexus.configuration.model.CRepositoryExternalConfigurationHolderFactory;
import org.sonatype.nexus.proxy.AccessDeniedException;
import org.sonatype.nexus.proxy.IllegalOperationException;
import org.sonatype.nexus.proxy.ItemNotFoundException;
import org.sonatype.nexus.proxy.LocalStorageException;
import org.sonatype.nexus.proxy.NoSuchResourceStoreException;
import org.sonatype.nexus.proxy.RequestContext;
import org.sonatype.nexus.proxy.ResourceStoreRequest;
import org.sonatype.nexus.proxy.StorageException;
import org.sonatype.nexus.proxy.item.ContentLocator;
import org.sonatype.nexus.proxy.item.DefaultStorageFileItem;
import org.sonatype.nexus.proxy.item.RepositoryItemUidFactory;
import org.sonatype.nexus.proxy.item.StorageCollectionItem;
import org.sonatype.nexus.proxy.item.StorageItem;
import org.sonatype.nexus.proxy.item.uid.RepositoryItemUidAttributeManager;
import org.sonatype.nexus.proxy.registry.ContentClass;
import org.sonatype.nexus.proxy.repository.AbstractRepository;
import org.sonatype.nexus.proxy.repository.RepositoryKind;
import org.sonatype.nexus.util.PathUtils;

@SuppressWarnings({ "nls", "deprecation" })
public class RepositoryMock extends AbstractRepository {

    private final String repositoryId;
    private boolean behaveAsProxy = false;

    private RepositoryItemUidFactory repositoryItemUidFactory;
    private RepositoryItemUidAttributeManager repositoryItemUidAttributeManager;

    /**
     * Creates a Repository with dummy snapshot content from src/test/resources/snapshotRepo
     * 
     * @return
     */
    public static RepositoryMock createSnapshotRepo(RepositoryItemUidFactory repositoryItemUidFactory,
            RepositoryItemUidAttributeManager repositoryItemUidAttributeManager) {
        return new RepositoryMock("snapshotRepo", repositoryItemUidFactory, repositoryItemUidAttributeManager);
    }

    /**
     * Creates a Repository with dummy content from src/test/resources/masterRepo
     * 
     * @return
     */
    public static RepositoryMock createMasterRepo(RepositoryItemUidFactory repositoryItemUidFactory,
            RepositoryItemUidAttributeManager repositoryItemUidAttributeManager) {
        return new RepositoryMock("masterRepo", repositoryItemUidFactory, repositoryItemUidAttributeManager);
    }

    private RepositoryMock(final String repositoryId, RepositoryItemUidFactory repositoryItemUidFactory,
            RepositoryItemUidAttributeManager repositoryItemUidAttributeManager) {
        this.repositoryId = repositoryId;
        this.repositoryItemUidFactory = repositoryItemUidFactory;
        this.repositoryItemUidAttributeManager = repositoryItemUidAttributeManager;
    }

    @Override
    protected CRepositoryExternalConfigurationHolderFactory<?> getExternalConfigurationHolderFactory() {
        return null;
    }

    @Override
    protected Configurator getConfigurator() {
        return null;
    }

    @Override
    public String getId() {
        return repositoryId;
    }

    @Override
    public StorageItem doRetrieveItem(final ResourceStoreRequest request) throws IllegalOperationException,
            ItemNotFoundException, StorageException {
        return RepositoryMock.this.createStorageItem(request.getRequestPath());
    }

    @Override
    public StorageItem retrieveItem(final ResourceStoreRequest request) throws IllegalOperationException,
            ItemNotFoundException, StorageException, AccessDeniedException {
        if (request.getRequestPath().equals("") || request.getRequestPath().equals("/")) {
            throw new ItemNotFoundException(request);
        }
        return doRetrieveItem(request);
    }

    @Override
    protected RepositoryItemUidFactory getRepositoryItemUidFactory() {
        return repositoryItemUidFactory;
    }

    @Override
    public RepositoryItemUidAttributeManager getRepositoryItemUidAttributeManager() {
        return repositoryItemUidAttributeManager;
    }

    public StorageItem createStorageItem(final String path) throws StorageException, ItemNotFoundException,
            IllegalOperationException {
        final String pathWithoutTrailingSlash = PathUtils.cleanUpTrailingSlash(path);
        final File file = new File("./src/test/resources/" + repositoryId + pathWithoutTrailingSlash);
        if (!file.exists()) {
            throw new ItemNotFoundException(new ResourceStoreRequest(pathWithoutTrailingSlash));
        }

        if (file.isFile()) {
            if (path.endsWith("/")) {
                // in nexus repositories files are not found if the path ends with a trailing slash
                throw new ItemNotFoundException(new ResourceStoreRequest(path));
            }
            try {
                final ContentLocator contentLocator = EasyMock.createMock(ContentLocator.class);
                // we need a new input stream for every getContent() call
                EasyMock.expect(contentLocator.getContent()).andAnswer(new IAnswer<InputStream>() {
                    @Override
                    public InputStream answer() throws Throwable {
                        return new FileInputStream(file);
                    }
                }).anyTimes();
                EasyMock.expect(contentLocator.getMimeType()).andAnswer(new IAnswer<String>() {
                    @Override
                    public String answer() throws Throwable {
                        return URLConnection.guessContentTypeFromName(pathWithoutTrailingSlash);
                    }
                }).anyTimes();
                EasyMock.replay(contentLocator);
                final DefaultStorageFileItem defaultStorageFileItem = new DefaultStorageFileItem(this,
                        new ResourceStoreRequest(pathWithoutTrailingSlash), true, false, contentLocator);
                defaultStorageFileItem.setModified(file.lastModified());

                return defaultStorageFileItem;
            } catch (final IOException e) {
                throw new LocalStorageException(e);
            }
        } else {
            if (behaveAsProxy) {
                // a proxy repository throws an ItemNotFoundException for folders existing in the
                // proxied repository if no artefact within this folder hierarchy was cached in
                // the proxy repository
                // to test this behavior an ItemNotFoundException is thrown here
                throw new ItemNotFoundException(new ResourceStoreRequest(pathWithoutTrailingSlash));
            }
            try {
                final StorageCollectionItem collectionItem = EasyMock.createMock(StorageCollectionItem.class);
                EasyMock.expect(collectionItem.getRepositoryId()).andStubReturn(repositoryId);
                EasyMock.expect(collectionItem.getPath()).andStubReturn(pathWithoutTrailingSlash);
                EasyMock.expect(collectionItem.getResourceStoreRequest()).andStubAnswer(
                        new IAnswer<ResourceStoreRequest>() {
                            @Override
                            public ResourceStoreRequest answer() throws Throwable {
                                return new ResourceStoreRequest(pathWithoutTrailingSlash);
                            }
                        });
                EasyMock.expect(collectionItem.getItemContext()).andStubReturn(new RequestContext());
                EasyMock.expect(collectionItem.list()).andAnswer(new IAnswer<Collection<StorageItem>>() {

                    @Override
                    public Collection<StorageItem> answer() throws Throwable {
                        final Collection<StorageItem> members = new LinkedList<StorageItem>();
                        final String[] memberNames = file.list();
                        for (final String memberName : memberNames) {
                            members.add(createStorageItem(pathWithoutTrailingSlash + "/" + memberName));
                        }
                        return members;
                    }
                });
                EasyMock.replay(collectionItem);
                return collectionItem;
            } catch (final AccessDeniedException e) {
                throw new LocalStorageException(e);
            } catch (final NoSuchResourceStoreException e) {
                throw new LocalStorageException(e);
            }
        }
    }

    public void setBehaveAsProxy(final boolean behaveAsProxy) {
        this.behaveAsProxy = behaveAsProxy;
    }

    @Override
    public RepositoryKind getRepositoryKind() {
        return null;
    }

    @Override
    public ContentClass getRepositoryContentClass() {
        return null;
    }

}
