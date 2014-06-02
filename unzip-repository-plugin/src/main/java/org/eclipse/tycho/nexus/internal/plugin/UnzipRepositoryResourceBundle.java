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

import java.util.ArrayList;
import java.util.List;

import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.plugin.support.UrlWebResource;
import org.sonatype.nexus.web.WebResource;
import org.sonatype.nexus.web.WebResourceBundle;

@Named("UnzipRepositoryResourceBundle")
@Singleton
public class UnzipRepositoryResourceBundle implements WebResourceBundle {
    public static final String JS_SCRIPT_PATH = "js/unzip/unzip-repo.js";
    public static final String JS_SCRIPT_BOOT_PATH = "unzip-repository-plugin-boot.js";

    public List<WebResource> getResources() {
        final List<WebResource> result = new ArrayList<WebResource>();

        result.add(new UrlWebResource(getClass().getResource("/static/js/unzip-repo.js"), "/" + JS_SCRIPT_PATH,
                "application/x-javascript"));
        result.add(new UrlWebResource(getClass().getResource("/static/js/unzip-repository-plugin-boot.js"), "/"
                + JS_SCRIPT_BOOT_PATH, "application/x-javascript"));

        return result;
    }
}
