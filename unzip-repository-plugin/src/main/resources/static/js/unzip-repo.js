/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2012 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
define('Sonatype/repoServer/unzipRepositoryEditContribution', function() {

  (function() {
    var originalHandler = Sonatype.repoServer.VirtualRepositoryEditor.prototype.afterProviderSelectHandler;

    Ext.override(Sonatype.repoServer.VirtualRepositoryEditor, {
        afterProviderSelectHandler: function (combo, rec, index) {
            var provider = rec.data.provider;
            var sourceRepoCombo = this.form.findField("shadowOf");
            sourceRepoCombo.clearValue();
            sourceRepoCombo.focus();
    		if (provider == "m1-m2-shadow") {
                    sourceRepoCombo.store.filterBy(function fn(rec, id) {
                        if (rec.data.repoType != "virtual" && rec.data.format == "maven1") {
                            return true
                        }
                        return false
                    })
            } else if (provider == "m2-m1-shadow") {
                    sourceRepoCombo.store.filterBy(function fn(rec, id) {
                        if (rec.data.repoType != "virtual" && rec.data.format == "maven2") {
                            return true
                        }
                        return false
                    })
            } else if (provider == "org.eclipse.tycho.nexus.plugin.DefaultUnzipRepository") {
                    sourceRepoCombo.store.filterBy(function fn(rec, id) {
                        if (rec.data.format == "maven2") {
                            return true
                        }
                        return false
                    })
            } else {
            		sourceRepoCombo.store.filterBy(function fn(rec, id) {
    	                return true
                    })
            }
        }
    });
  })();
});

