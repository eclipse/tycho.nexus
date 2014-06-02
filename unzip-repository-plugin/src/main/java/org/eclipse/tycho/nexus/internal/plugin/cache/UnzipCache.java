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
package org.eclipse.tycho.nexus.internal.plugin.cache;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.tycho.nexus.internal.plugin.DefaultUnzipRepository;
import org.eclipse.tycho.nexus.internal.plugin.cache.PathLock.PathLockMonitor;
import org.slf4j.Logger;
import org.sonatype.nexus.proxy.AccessDeniedException;
import org.sonatype.nexus.proxy.IllegalOperationException;
import org.sonatype.nexus.proxy.ItemNotFoundException;
import org.sonatype.nexus.proxy.LocalStorageException;
import org.sonatype.nexus.proxy.ResourceStoreRequest;
import org.sonatype.nexus.proxy.item.StorageItem;
import org.sonatype.nexus.proxy.storage.UnsupportedStorageOperationException;
import org.sonatype.nexus.proxy.storage.local.LocalRepositoryStorage;
import org.sonatype.nexus.proxy.storage.local.fs.DefaultFSLocalRepositoryStorage;
import org.sonatype.nexus.util.PathUtils;

public class UnzipCache {

    final DefaultUnzipRepository repository;
    private final LocalRepositoryStorage localStorage;
    private final Logger logger;

    public UnzipCache(final DefaultUnzipRepository repository, final Logger logger) {
        this.logger = logger;
        this.repository = repository;
        localStorage = this.repository.getLocalStorage();
    }

    /**
     * Returns the requested artifact from the local storage if the artifact was already cached. If
     * not it retrieves it from the corresponding repository and stores it in the local storage.
     *
     * @param zipItemPath
     *            the path to the zip file
     * @return the file in the local storage
     *
     * @throws ItemNotFoundException
     *             thrown if the artifact cannot be found in the repository
     *
     * @throws LocalStorageException
     *
     */
    public File getArchive(final String zipItemPath) throws ItemNotFoundException, LocalStorageException {
        final PathLockMonitor folderLock = PathLock.getLock(getRequestPathParent(zipItemPath));
        try {
            synchronized (folderLock) {
                final ResourceStoreRequest request = new ResourceStoreRequest(zipItemPath);
                if (!localStorage.containsItem(repository, request)) {
                    logger.debug("Caching zip file from master repository: " + zipItemPath);
                    final StorageItem storageItem = retrieveItemFromMaster(request);
                    localStorage.storeItem(repository, storageItem);
                }
                final File file = ((DefaultFSLocalRepositoryStorage) localStorage).getFileFromBase(repository, request);
                logger.debug("Accessed cached zip file: " + zipItemPath);
                return file;
            }
        } catch (final UnsupportedStorageOperationException e) {
            throw new LocalStorageException(e);
        } finally {
            PathLock.releaseLock(folderLock);
        }
    }

    /**
     * Depending on the conversion result out-dated snapshots are removed from the storage, if
     * possible.
     *
     *
     * @param conversionResult
     *            if a snapshot conversion took place, old snapshot artifacts are removed from the
     *            cache. If no snapshot has been found, all snapshot artifacts are removed from the
     *            cache
     *
     * @throws ItemNotFoundException
     *             thrown if the artifact cannot be found in the repository
     */
    public void cleanSnapshots(final ConversionResult conversionResult) throws ItemNotFoundException {
        if (conversionResult.isPathConverted() || !conversionResult.isASnapshotAvailable()) {

            logger.debug("Looking for outdated cached snapshots artifacts to clean up");

            final String requestPathParent = getRequestPathParent(conversionResult.getPathUpToVersion());
            final ResourceStoreRequest parentPathRequest = new ResourceStoreRequest(requestPathParent);
            final PathLockMonitor folderLock = PathLock.getLock(requestPathParent);
            try {
                synchronized (folderLock) {
                    final List<String> toBeDeleted = new LinkedList<String>();
                    for (final StorageItem item : localStorage.listItems(repository, parentPathRequest)) {
                        final String itemPath = item.getPath();
                        if (!conversionResult.isASnapshotAvailable()) {
                            toBeDeleted.add(itemPath);
                        } else if (itemPath.startsWith(conversionResult.getPathUpToVersion())
                                && !itemPath.contains(conversionResult.getLatestVersion())) {
                            toBeDeleted.add(itemPath);
                        }
                    }
                    //use list of Strings instead of items, cause file handles will prevent deletion in many cases
                    for (final String itemPath : toBeDeleted) {
                        localStorage.shredItem(repository, new ResourceStoreRequest(itemPath));
                        logger.debug("Deleted outdated cached snapshot artifact: " + itemPath);
                    }
                    if (toBeDeleted.size() == 0) {
                        logger.debug("No outdated cached snapshots artifacts found");
                    }
                }
            } catch (final UnsupportedStorageOperationException e) {
                logger.warn(this.getClass().getName() + ": Unable to delete cached item", e);
            } catch (@SuppressWarnings("deprecation") final org.sonatype.nexus.proxy.StorageException e) {
                // do nothing, as we accept if the file cannot be deleted
            } catch (final ItemNotFoundException e) {
                // do nothing, as we accept that files might be deleted on OS level
                // e,g localStorage.listItems(repository, parentPathRequest) throws this exception in case
                // the parent folder was removed from the file system
            } finally {
                PathLock.releaseLock(folderLock);
            }
        }
    }

    private static String getRequestPathParent(final String path) {
        return PathUtils.getParentPath(path) + PathUtils.PATH_SEPARATOR;
    }

    private StorageItem retrieveItemFromMaster(final ResourceStoreRequest masterRepositoryRequest)
            throws ItemNotFoundException, LocalStorageException {
        try {
            return repository.getMasterRepository().retrieveItem(masterRepositoryRequest);
        } catch (final IllegalOperationException e) {
            throw new LocalStorageException(e);
        } catch (final AccessDeniedException e) {
            throw new LocalStorageException(e);
        } catch (@SuppressWarnings("deprecation") final org.sonatype.nexus.proxy.StorageException e) {
            throw new LocalStorageException(e);
        }
    }

}
