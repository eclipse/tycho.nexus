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
package org.eclipse.tycho.nexus.internal.plugin.storage;

import java.io.IOException;

import org.eclipse.tycho.nexus.internal.plugin.DefaultUnzipRepository;
import org.eclipse.tycho.nexus.internal.plugin.test.TestUtil;
import org.eclipse.tycho.nexus.internal.plugin.test.UnzipPluginTestSupport;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.proxy.ItemNotFoundException;
import org.sonatype.nexus.proxy.LocalStorageException;
import org.sonatype.nexus.proxy.RequestContext;
import org.sonatype.nexus.proxy.ResourceStoreRequest;
import org.sonatype.nexus.proxy.item.DefaultStorageCollectionItem;
import org.sonatype.nexus.proxy.item.DefaultStorageFileItem;
import org.sonatype.nexus.util.PathUtils;

@SuppressWarnings("nls")
public class ZippedItemTest extends UnzipPluginTestSupport {

    private final String pathToArchive = "/dir/subdir/archive.zip";
    private final String pathToUnzippedArchive = "/dir/subdir/archive.zip" + Util.UNZIP_TYPE_EXTENSION;

    private DefaultUnzipRepository unzipReposMock;

    @Before
    public void setup() throws Exception {
        unzipReposMock = createUnzipRepo(createMasterRepo());
    }

    @Test
    public void testZippedItemInRoot() throws ItemNotFoundException, IOException {
        final String pathInZip = "test.txt";
        final ZippedItem zippedItem = createZippedItem(pathInZip);

        Assert.assertEquals(pathInZip, zippedItem.getPathInZip());
        Assert.assertEquals(pathToUnzippedArchive + "/" + pathInZip, zippedItem.getPath());
        Assert.assertEquals("text/plain", zippedItem.getMimeType().toString());

        final DefaultStorageFileItem zippedStorageItem = (DefaultStorageFileItem) zippedItem.getZippedStorageItem();
        Assert.assertEquals(pathToUnzippedArchive + "/" + pathInZip, zippedStorageItem.getPath());
        Assert.assertEquals(pathToUnzippedArchive, zippedStorageItem.getParentPath());
        Assert.assertEquals("text/plain", zippedStorageItem.getMimeType().toString());
        TestUtil.assertContent("some content", zippedStorageItem);
    }

    @Test
    public void testZippedItemInDir() throws ItemNotFoundException, IOException {
        final String pathInZip = "dir/test.txt";
        final ZippedItem zippedItem = createZippedItem(pathInZip);

        Assert.assertEquals(pathInZip, zippedItem.getPathInZip());
        Assert.assertEquals(pathToUnzippedArchive + "/" + pathInZip, zippedItem.getPath());
        Assert.assertEquals("text/plain", zippedItem.getMimeType().toString());

        final DefaultStorageFileItem zippedStorageItem = (DefaultStorageFileItem) zippedItem.getZippedStorageItem();
        Assert.assertEquals(pathToUnzippedArchive + "/" + pathInZip, zippedStorageItem.getPath());
        Assert.assertEquals(pathToUnzippedArchive + "/dir", zippedStorageItem.getParentPath());
        Assert.assertEquals("text/plain", zippedStorageItem.getMimeType().toString());

        TestUtil.assertContent("some file content", zippedStorageItem);
    }

    @Test(expected = ItemNotFoundException.class)
    public void testZippedItemNotExisting() throws ItemNotFoundException, IOException {
        final String pathInZip = "x.txt";
        createZippedItem(pathInZip);
    }

    @Test(expected = ItemNotFoundException.class)
    public void testZippedItemNotExisting2() throws ItemNotFoundException, IOException {
        final String pathInZip = "dir/x.txt";
        createZippedItem(pathInZip);
    }

    @Test
    public void testZippedItemEmptyPath() throws ItemNotFoundException, IOException {
        final String pathInZip = "";
        final ZippedItem zippedItem = createZippedItem(pathInZip);

        Assert.assertEquals(pathInZip, zippedItem.getPathInZip());
        Assert.assertEquals(pathToUnzippedArchive, zippedItem.getPath());
        Assert.assertNull(zippedItem.getMimeType());

        final DefaultStorageCollectionItem zippedStorageItem = (DefaultStorageCollectionItem) zippedItem
                .getZippedStorageItem();
        Assert.assertEquals(pathToUnzippedArchive, zippedStorageItem.getPath());
    }

    @Test
    public void testZippedItemNullPath() throws ItemNotFoundException, IOException {
        final String pathInZip = null;
        final ZippedItem zippedItem = createZippedItem(pathInZip);

        Assert.assertEquals("", zippedItem.getPathInZip());
        Assert.assertEquals(pathToUnzippedArchive, zippedItem.getPath());
        Assert.assertNull(zippedItem.getMimeType());

        final DefaultStorageCollectionItem zippedStorageItem = (DefaultStorageCollectionItem) zippedItem
                .getZippedStorageItem();
        Assert.assertEquals(pathToUnzippedArchive, zippedStorageItem.getPath());
    }

    @Test
    public void testZippedItemDir() throws ItemNotFoundException, IOException {
        final String pathInZip = "dir";
        final ZippedItem zippedItem = createZippedItem(pathInZip);

        Assert.assertEquals(pathInZip, zippedItem.getPathInZip());
        Assert.assertEquals(pathToUnzippedArchive + "/" + pathInZip, zippedItem.getPath());
        Assert.assertNull(zippedItem.getMimeType());

        final DefaultStorageCollectionItem zippedStorageItem = (DefaultStorageCollectionItem) zippedItem
                .getZippedStorageItem();
        Assert.assertEquals(pathToUnzippedArchive + "/" + pathInZip, zippedStorageItem.getPath());
    }

    @Test
    public void testZippedItemDirWithTrailingSlash() throws ItemNotFoundException, IOException {
        final String pathInZip = "dir/";
        final ZippedItem zippedItem = createZippedItem(pathInZip);

        Assert.assertEquals(PathUtils.cleanUpTrailingSlash(pathInZip), zippedItem.getPathInZip());
        Assert.assertEquals(PathUtils.cleanUpTrailingSlash(pathToUnzippedArchive + "/" + pathInZip),
                zippedItem.getPath());
        Assert.assertNull(zippedItem.getMimeType());

        final DefaultStorageCollectionItem zippedStorageItem = (DefaultStorageCollectionItem) zippedItem
                .getZippedStorageItem();
        Assert.assertEquals(PathUtils.cleanUpTrailingSlash(pathToUnzippedArchive + "/" + pathInZip),
                zippedStorageItem.getPath());
    }

    @Test(expected = LocalStorageException.class)
    public void testZipItemNotFound() throws Exception {
        final String pathInZip = "test.txt";
        ZippedItem.newZippedItem(unzipReposMock, new ResourceStoreRequest("/-unzip/" + pathInZip), "/", pathInZip, 0L,
                LoggerFactory.getLogger(getClass()));
    }

    @Test
    public void testListMembersEmptyPath() throws ItemNotFoundException, IOException {
        final String pathInZip = "";
        final ZippedItem zippedItem = createZippedItem(pathInZip);

        TestUtil.assertMembers(new String[] { pathToUnzippedArchive + "/dir" }, new String[] { pathToUnzippedArchive
                + "/test.txt" }, zippedItem.listMembers());
    }

    @Test
    public void testListMembersDir() throws ItemNotFoundException, IOException {
        final String pathInZip = "dir/";
        final ZippedItem zippedItem = createZippedItem(pathInZip);

        TestUtil.assertMembers(new String[] { pathToUnzippedArchive + "/" + pathInZip + "subdir" },
                new String[] { pathToUnzippedArchive + "/" + pathInZip + "test.txt" }, zippedItem.listMembers());
    }

    @Test(expected = LocalStorageException.class)
    public void testListMembersOnFile() throws ItemNotFoundException, IOException {
        final String pathInZip = "test.txt";
        final ZippedItem zippedItem = createZippedItem(pathInZip);
        zippedItem.listMembers();
    }

    private ZippedItem createZippedItem(final String pathInZip) throws LocalStorageException, ItemNotFoundException {
        return ZippedItem.newZippedChildItem(unzipReposMock, new RequestContext(), pathToArchive, pathInZip,
                System.currentTimeMillis(), LoggerFactory.getLogger(getClass()));
    }

    @AfterClass
    public static void classTearDown() {
        TestUtil.cleanUpTestFiles();
    }
}
