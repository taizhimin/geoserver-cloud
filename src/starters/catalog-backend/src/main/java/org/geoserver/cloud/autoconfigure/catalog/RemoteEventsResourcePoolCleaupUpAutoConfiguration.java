/*
 * (c) 2020 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.cloud.autoconfigure.catalog;

import org.geoserver.catalog.ResourcePool;
import org.geoserver.catalog.plugin.CatalogPlugin;
import org.geoserver.cloud.autoconfigure.catalog.event.ConditionalOnCatalogEvents;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cleans up cached {@link ResourcePool} entries upon remote Catalog events
 *
 * @since 1.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCatalogEvents
public class RemoteEventsResourcePoolCleaupUpAutoConfiguration {

    public @Bean RemoteEventResourcePoolProcessor remoteEventResourcePoolProcessor(
            @Qualifier("rawCatalog") CatalogPlugin rawCatalog) {

        return new RemoteEventResourcePoolProcessor(rawCatalog);
    }
}
