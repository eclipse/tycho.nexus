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

import java.io.File;
import java.util.Arrays;

import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.inject.Named;

import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.sisu.Description;
import org.eclipse.tycho.nexus.internal.plugin.cache.ConversionResult;
import org.eclipse.tycho.nexus.internal.plugin.cache.RequestPathConverter;
import org.eclipse.tycho.nexus.internal.plugin.cache.UnzipCache;
import org.eclipse.tycho.nexus.internal.plugin.storage.Util;
import org.eclipse.tycho.nexus.internal.plugin.storage.ZipAwareStorageCollectionItem;
import org.eclipse.tycho.nexus.internal.plugin.storage.ZippedItem;
import org.slf4j.Logger;
import org.sonatype.configuration.ConfigurationException;
import org.sonatype.nexus.ApplicationStatusSource;
import org.sonatype.nexus.SystemState;
import org.sonatype.nexus.configuration.model.CRepository;
import org.sonatype.nexus.configuration.model.CRepositoryExternalConfigurationHolderFactory;
import org.sonatype.nexus.plugins.RepositoryType;
import org.sonatype.nexus.proxy.IllegalOperationException;
import org.sonatype.nexus.proxy.ItemNotFoundException;
import org.sonatype.nexus.proxy.LocalStorageException;
import org.sonatype.nexus.proxy.NoSuchRepositoryException;
import org.sonatype.nexus.proxy.ResourceStoreRequest;
import org.sonatype.nexus.proxy.events.NexusStartedEvent;
import org.sonatype.nexus.proxy.events.RepositoryRegistryEventAdd;
import org.sonatype.nexus.proxy.item.StorageCollectionItem;
import org.sonatype.nexus.proxy.item.StorageItem;
import org.sonatype.nexus.proxy.item.StorageLinkItem;
import org.sonatype.nexus.proxy.registry.ContentClass;
import org.sonatype.nexus.proxy.registry.RepositoryRegistry;
import org.sonatype.nexus.proxy.repository.AbstractRepositoryConfigurator;
import org.sonatype.nexus.proxy.repository.AbstractShadowRepository;
import org.sonatype.nexus.proxy.repository.DefaultRepositoryKind;
import org.sonatype.nexus.proxy.repository.IncompatibleMasterRepositoryException;
import org.sonatype.nexus.proxy.repository.LocalStatus;
import org.sonatype.nexus.proxy.repository.Repository;
import org.sonatype.nexus.proxy.repository.RepositoryKind;
import org.sonatype.nexus.proxy.repository.ShadowRepository;
import org.sonatype.nexus.proxy.storage.UnsupportedStorageOperationException;
import org.sonatype.nexus.proxy.storage.local.LocalRepositoryStorage;
import org.sonatype.nexus.proxy.storage.local.fs.DefaultFSLocalRepositoryStorage;

import com.google.common.eventbus.Subscribe;

/**
 * Shadow repository that allows to directly browse and access the content of archive files (e.g.
 * zip, jar files) that are stored in the master repository. In the shadow repository all files and
 * folders of the master repository can be accessed in the same way as in the master repository. The
 * additional functionality is that <br>
 * 1. archives can be browsed under their path + trailing slash.<br>
 * 2. files and folders in an archive can be browsed under the path of the archive + slash + path
 * within archive
 */
@Named(DefaultUnzipRepository.REPOSITORY_HINT)
@Description("Unzip Repository")
@Typed(UnzipRepository.class)
@RepositoryType(pathPrefix = "unzip")
public class DefaultUnzipRepository extends AbstractShadowRepository implements UnzipRepository {
    static final String REPOSITORY_HINT = "org.eclipse.tycho.nexus.plugin.DefaultUnzipRepository";

    private final UnzipRepositoryConfigurator configurator;

    private final ContentClass contentClass;

    private final ContentClass masterContentClass;

    private final ApplicationStatusSource statusSource;

    private RepositoryKind repositoryKind;
    private UnzipCache cache;
    private boolean processedNexusStartedEvent = false;
    private boolean isMasterAvailable = false;

    private final RepositoryRegistry repositoryRegistry;

    @Inject
    public DefaultUnzipRepository(final RepositoryRegistry repositoryRegistry,
            final UnzipRepositoryConfigurator configurator, @Named("maven2") final ContentClass contentClass,
            @Named("maven2") final ContentClass masterContentClass, final ApplicationStatusSource statusSource) {
        this.repositoryRegistry = repositoryRegistry;
        this.configurator = configurator;
        this.contentClass = contentClass;
        this.masterContentClass = masterContentClass;
        this.statusSource = statusSource;
    }

    // If a class instance of DefaultUnzipRepository is created before Nexus startup finished the field statusSource gets
    // and keeps an invalid proxy instance from plexus which always throws an IllegalStateException if being asked
    // for the current Nexus state. All these instances get the NexusStartedEvent and with it set the MasterRepository
    // from what is stored in the ExternalConfiguration.
    // This is true for all unzip repositories being listed in the nexus.xml file on startup.
    //
    // If a class instance of DefaultUnzipRepository is created e.g. from the UI after Nexus startup finished it
    // won't ever get a NexusStartedEvent so for these DefaultUnzipRepository instances the field
    // processedNexusStartedEvent will always be false. But the field statusSource is now valid and we can ask for the
    // correct state.
    private boolean isNexusStarted() {
        if (processedNexusStartedEvent) {
            return true;
        }

        try {
            final SystemState systemState = statusSource.getSystemStatus().getState();
            return systemState.equals(SystemState.STARTED);
        } catch (final IllegalStateException e) {
        }
        return false;
    }

    @Override
    protected AbstractRepositoryConfigurator getConfigurator() {
        return configurator;
    }

    @Override
    public ContentClass getRepositoryContentClass() {
        return contentClass;
    }

    @Override
    public ContentClass getMasterRepositoryContentClass() {
        return masterContentClass;
    }

    @Override
    public RepositoryKind getRepositoryKind() {
        if (repositoryKind == null) {
            repositoryKind = new DefaultRepositoryKind(UnzipRepository.class,
                    Arrays.asList(new Class<?>[] { ShadowRepository.class }));
        }

        return repositoryKind;
    }

    @Override
    protected CRepositoryExternalConfigurationHolderFactory<?> getExternalConfigurationHolderFactory() {
        return new CRepositoryExternalConfigurationHolderFactory<UnzipRepositoryConfiguration>() {
            @Override
            public UnzipRepositoryConfiguration createExternalConfigurationHolder(final CRepository config) {
                return new UnzipRepositoryConfiguration((Xpp3Dom) config.getExternalConfiguration());
            }
        };
    }

    /*
     * Need to overwrite setMasterRepository(final Repository masterRepository),
     * getMasterRepository(), getLocalStatus() onEvent(Event<?> evt), doConfigure() in order to
     * allow Unzip repositories in front of repository groups. During Nexus startup repository
     * creation repository groups are explicitly created AFTER all other repositories. see
     * org.sonatype.nexus.configuration.application.DefaultNexusConfiguration#createRepositories()
     * As a result at creation time of this repository the master is not yet available in case the
     * master is a group. After Nexus startup is complete all methods behave like default. In the
     * meantime the master repository id will be stored without availability, compatibility checks
     * avoiding error logs.
     */
    @Override
    public void setMasterRepository(final Repository masterRepository) throws IncompatibleMasterRepositoryException {
        super.setMasterRepository(masterRepository);
        isMasterAvailable = true;
    }

    // see comment at setMasterRepositoryId(String id)
    @Override
    public Repository getMasterRepository() {
        if (isNexusStarted() && isMasterAvailable) {
            return super.getMasterRepository();
        }
        return null;
    }

    // see comment at setMasterRepositoryId(String id)
    @Override
    public LocalStatus getLocalStatus() {
        LocalStatus localStatus = null;
        if (isNexusStarted() && isMasterAvailable) {
            localStatus = super.getLocalStatus();
        } else {
            final String localStatusString = getCurrentConfiguration(false).getLocalStatus();
            if (localStatusString == null) {
                localStatus = LocalStatus.OUT_OF_SERVICE;
            } else {
                localStatus = LocalStatus.valueOf(localStatusString);
            }
        }
        return localStatus;
    }

    @Subscribe
    public void onNexusStartedEvent(NexusStartedEvent evt) {
        if (!isMasterAvailable) {
            String repositoryId = getExternalConfiguration(false).getMasterRepositoryId();
            try {
                reallyDoConfigure();
            } catch (IncompatibleMasterRepositoryException e) {
                getLogger().error("[" + repositoryId + "] " + "cannot set master repository " + e.getMessage());
            } catch (ConfigurationException e) {
                getLogger().error("[" + repositoryId + "] " + "cannot configure " + e.getMessage());
            }
        }
        processedNexusStartedEvent = true;
    }

    @Override
    protected void doConfigure() throws ConfigurationException {
        final String repositoryId = getExternalConfiguration(false).getMasterRepositoryId();
        try {
            getRepositoryRegistry().getRepository(repositoryId);
            reallyDoConfigure();
        } catch (final NoSuchRepositoryException e) {
            getLogger().warn("Repository '" + repositoryId + "' not yet present. doConfigure skipped.");
        }
    }

    private void reallyDoConfigure() throws ConfigurationException {
        super.doConfigure();
    }

    protected Logger getLogger() {
        return this.log;
    }

    protected RepositoryRegistry getRepositoryRegistry() {
        return this.repositoryRegistry;
    }

    @Subscribe
    public void onRepositoryRegistryEventAdd(RepositoryRegistryEventAdd evt) {
        final String eventRepositoryId = evt.getRepository().getId();
        if (super.getMasterRepository() != null && eventRepositoryId.equals(super.getMasterRepository().getId())) {
            try {
                final Repository masterRepository = getRepositoryRegistry().getRepository(eventRepositoryId);
                setMasterRepository(masterRepository);
            } catch (final NoSuchRepositoryException e) {
                getLogger().warn("Master Repository not available", e);
            } catch (final IncompatibleMasterRepositoryException e) {
                getLogger().warn("Master Repository incompatible", e);
            }
        }
    }

    /**
     * Retrieves an item from the master repository.
     *
     * @param requestPath
     *            the path to the item be retrieved from the master repository
     * @param originalRequest
     *            the original request to the virtual repository providing context information the
     *            request to the master repository
     * @return the item from the master repository
     * @throws ItemNotFoundException
     *             is thrown if there is no item under the specified request path in the master
     *             repository
     * @throws LocalStorageException
     */
    StorageItem retrieveItemFromMaster(String requestPath, ResourceStoreRequest originalRequest)
            throws ItemNotFoundException, LocalStorageException {
        try {
            // use original request with modified path (e.g. like in AbstractMavenRepository.doRetrieveArtifactItem)
            originalRequest.pushRequestPath(requestPath);
            try {
                return doRetrieveItemFromMaster(originalRequest);
            } finally {
                originalRequest.popRequestPath();
            }

        } catch (final IllegalOperationException e) {
            throw new LocalStorageException(e);
        } catch (@SuppressWarnings("deprecation") final org.sonatype.nexus.proxy.StorageException e) {
            throw new LocalStorageException(e);
        }
    }

    @Override
    protected StorageItem doRetrieveItem(final ResourceStoreRequest request) throws IllegalOperationException,
            ItemNotFoundException, LocalStorageException {

        final RequestTimeTrace timeTrace = new RequestTimeTrace(request.getRequestPath());

        final ConversionResult conversionResult = RequestPathConverter.convert(getMasterRepository(), request,
                isUseVirtualVersion());

        if (conversionResult.isPathConverted()) {
            getLogger().debug(
                    "Resolved dynamic request: " + request.getRequestUrl() + ". Resolved request path: "
                            + conversionResult.getConvertedPath());
        }

        // First check for zip content to avoid unnecessary and expensive backend calls for zip content requests.
        // Due to naming conventions zippedItem creation will normally only call the backend in case it is a zip content request.
        // a) path does not point to zip content (-> null)
        // b) a path to a file/folder inside a zip file (-> ZippedItem is created and returned)
        // c) a non-existing path under an existing zip file (-> retrieving ZippedItem fails with ItemNotFoundException)
        final ZippedItem zippedItem = getZippedItem(conversionResult, request);
        if (zippedItem != null) {
            final StorageItem zippedStorageItem = zippedItem.getZippedStorageItem();
            getLogger().debug(timeTrace.getMessage());
            return zippedStorageItem;
        }

        // check if item exists in master repository
        // this call will fail with ItemNotFoundException if the item does not exist in the master repository
        final StorageItem masterItem = retrieveItemFromMaster(conversionResult.getConvertedPath(), request);

        if (masterItem instanceof StorageCollectionItem) {
            // item is non-zip folder
            final ZipAwareStorageCollectionItem zipAwareStorageCollectionItem = new ZipAwareStorageCollectionItem(this,
                    (StorageCollectionItem) masterItem, getLogger());
            getLogger().debug(timeTrace.getMessage());
            return zipAwareStorageCollectionItem;
        } else {
            getLogger().debug(timeTrace.getMessage());
            // if item is a non-zip file we simply return it as it is
            return masterItem;
        }

    }

    /**
     * Checks if the request path represents a zipped item (a file or directory within a zip file)
     * and if yes returns it. If the request path does not represent a zipped item <code>null</code>
     * is returned
     *
     * @param conversionResult
     *            the result of the snapshot path conversion, containing the converted path
     * @param request
     *            the {@link ResourceStoreRequest} for the item. The request is included in the
     *            {@link StorageItem} returned by the repository.
     * @return item that represents a file or folder within a zip file, <code>null</code> if the
     *         requested path does not point to zip content
     * @throws LocalStorageException
     * @throws ItemNotFoundException
     *             is thrown if for non-existing or invalid request path
     */
    private ZippedItem getZippedItem(final ConversionResult conversionResult, ResourceStoreRequest request)
            throws LocalStorageException, ItemNotFoundException {
        final StringBuilder pathInZip = new StringBuilder();
        final String[] pathSegments = conversionResult.getConvertedPath().split("/");
        String zipFilePath = "";
        String zipItemPath = null;
        long zipLastModified = 0L;

        for (final String pathSegment : pathSegments) {
            if (zipItemPath == null) {
                if (!zipFilePath.toString().endsWith("/")) {
                    zipFilePath = zipFilePath + "/";
                }
                zipFilePath = zipFilePath + pathSegment;
                if (zipFilePath.endsWith(Util.UNZIP_TYPE_EXTENSION)) {
                    final String zipFilePathWithoutExtension = zipFilePath.substring(0, zipFilePath.length()
                            - Util.UNZIP_TYPE_EXTENSION.length());
                    getCache().cleanSnapshots(conversionResult);
                    final File zipFile = getCache().getArchive(zipFilePathWithoutExtension);
                    if (zipFile != null) {
                        zipLastModified = zipFile.lastModified();
                        zipItemPath = zipFilePathWithoutExtension;
                    }
                }
            } else {
                if (pathInZip.length() > 0) {
                    pathInZip.append("/");
                }
                pathInZip.append(pathSegment);
            }
        }
        if (zipItemPath != null) {
            // creating a new ZippedItem fails with ItemNotFoundException if a non-existing file or folder
            // inside the (existing) zip file is accessed
            getLogger().debug(conversionResult.getConvertedPath() + " points into a zip file.");
            final ZippedItem zippedItem = ZippedItem.newZippedItem(this, request, zipItemPath, pathInZip.toString(),
                    zipLastModified, getLogger());
            return zippedItem;
        }
        return null;
    }

    public synchronized UnzipCache getCache() {
        if (cache == null) {
            cache = new UnzipCache(this, getLogger());
        }
        return cache;
    }

    @Override
    protected StorageLinkItem createLink(final StorageItem item) throws UnsupportedStorageOperationException,
            IllegalOperationException, LocalStorageException {
        // abstract super methods not documented.
        // called during #onEvent processing.
        // any thrown Exception will be logged polluting the nexus log with Exception stacks.
        // So we interpret the method as automated hook allowing, but not forcing LinkItem creation.
        return null;
    }

    @Override
    protected void deleteLink(final StorageItem item) throws UnsupportedStorageOperationException,
            IllegalOperationException, ItemNotFoundException, LocalStorageException {
        // nothing created. Nothing to be deleted
    }

    @Override
    public void setLocalStorage(final LocalRepositoryStorage localStorage) {
        if (localStorage instanceof DefaultFSLocalRepositoryStorage) {
            super.setLocalStorage(localStorage);
        } else {
            throw new RuntimeException(localStorage + " is not an instance of DefaultFSLocalRepositoryStorage");
        }
    }

    @Override
    public boolean isUseVirtualVersion() {
        return ((UnzipRepositoryConfiguration) getExternalConfiguration(false)).isUseVirtualVersion();
    }

    @Override
    public void setUseVirtualVersion(final boolean val) {
        ((UnzipRepositoryConfiguration) getExternalConfiguration(true)).setUseVirtualVersion(val);
    }
}
