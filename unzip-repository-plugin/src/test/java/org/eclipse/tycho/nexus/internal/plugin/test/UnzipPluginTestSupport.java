package org.eclipse.tycho.nexus.internal.plugin.test;

import org.eclipse.tycho.nexus.internal.plugin.DefaultUnzipRepository;
import org.sonatype.nexus.proxy.item.LinkPersister;
import org.sonatype.nexus.proxy.item.RepositoryItemUidFactory;
import org.sonatype.nexus.proxy.item.uid.RepositoryItemUidAttributeManager;
import org.sonatype.nexus.test.NexusTestSupport;

public class UnzipPluginTestSupport extends NexusTestSupport {

    protected RepositoryMock createMasterRepo() throws Exception {
        return RepositoryMock.createMasterRepo(lookup(RepositoryItemUidFactory.class),
                lookup(RepositoryItemUidAttributeManager.class));
    }

    protected RepositoryMock createSnapshotRepo() throws Exception {
        return RepositoryMock.createSnapshotRepo(lookup(RepositoryItemUidFactory.class),
                lookup(RepositoryItemUidAttributeManager.class));
    }

    protected DefaultUnzipRepository createUnzipRepo(RepositoryMock masterRepository) throws Exception {
        return UnzipRepositoryMock.createUnzipRepository(masterRepository, lookup(LinkPersister.class),
                lookup(RepositoryItemUidFactory.class));
    }

    protected RepositoryMock createRedeployRelRepo() throws Exception {
        return RepositoryMock.createRedeployRelRepo(lookup(RepositoryItemUidFactory.class),
                lookup(RepositoryItemUidAttributeManager.class));
    }
}
