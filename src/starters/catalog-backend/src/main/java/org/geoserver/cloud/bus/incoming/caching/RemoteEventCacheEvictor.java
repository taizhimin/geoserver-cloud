/*
 * (c) 2020 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.cloud.bus.incoming.caching;

import static org.geoserver.cloud.catalog.caching.CachingCatalogFacade.DEFAULT_NAMESPACE_CACHE_KEY;
import static org.geoserver.cloud.catalog.caching.CachingCatalogFacade.DEFAULT_WORKSPACE_CACHE_KEY;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.Info;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.impl.ResolvingProxy;
import org.geoserver.cloud.catalog.caching.CachingCatalogFacade;
import org.geoserver.cloud.catalog.caching.CachingGeoServerFacade;
import org.geoserver.cloud.event.catalog.CatalogInfoModifyEvent;
import org.geoserver.cloud.event.catalog.CatalogInfoRemoveEvent;
import org.geoserver.cloud.event.catalog.DefaultDataStoreEvent;
import org.geoserver.cloud.event.catalog.DefaultNamespaceEvent;
import org.geoserver.cloud.event.catalog.DefaultWorkspaceEvent;
import org.geoserver.cloud.event.config.GeoServerInfoModifyEvent;
import org.geoserver.cloud.event.config.GeoServerInfoSetEvent;
import org.geoserver.cloud.event.config.LoggingInfoModifyEvent;
import org.geoserver.cloud.event.config.LoggingInfoSetEvent;
import org.geoserver.cloud.event.config.ServiceInfoModifyEvent;
import org.geoserver.cloud.event.config.ServiceInfoRemoveEvent;
import org.geoserver.cloud.event.config.SettingsInfoModifyEvent;
import org.geoserver.cloud.event.config.SettingsInfoRemoveEvent;
import org.geoserver.cloud.event.config.UpdateSequenceEvent;
import org.geoserver.cloud.event.info.ConfigInfoType;
import org.geoserver.cloud.event.info.InfoEvent;
import org.geoserver.config.GeoServerInfo;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.function.BooleanSupplier;

/**
 * Component to listen to {@link RemoteInfoEvent} based hierarchy of events and evict entries from
 * {@link CachingCatalogFacade} and {@link CachingGeoServerFacade} as required by the event type and
 * the object it refers to.
 */
@Slf4j(topic = "org.geoserver.cloud.events.catalog.caching")
@RequiredArgsConstructor
public @Service class RemoteEventCacheEvictor {

    private final CachingCatalogFacade catalog;
    private final CachingGeoServerFacade config;

    @EventListener(classes = {DefaultWorkspaceEvent.class})
    public void onSetDefaultWorkspaceEvent(DefaultWorkspaceEvent event) {
        evictEntry(event, () -> catalog.evict(DEFAULT_WORKSPACE_CACHE_KEY));
    }

    @EventListener(classes = {DefaultNamespaceEvent.class})
    public void onSetDefaultNamespaceEvent(DefaultNamespaceEvent event) {
        evictEntry(event, () -> catalog.evict(DEFAULT_NAMESPACE_CACHE_KEY));
    }

    @EventListener(classes = {DefaultDataStoreEvent.class})
    public void onSetDefaultDataStoreEvent(DefaultDataStoreEvent event) {
        evictEntry(
                event,
                () -> {
                    String workspaceId = event.getWorkspaceId();
                    WorkspaceInfo workspace =
                            ResolvingProxy.create(workspaceId, WorkspaceInfo.class);
                    catalog.evict(CachingCatalogFacade.generateDefaultDataStoreKey(workspace));
                    return false;
                });
    }

    @EventListener(classes = {CatalogInfoRemoveEvent.class})
    public void onCatalogInfoRemoveEvent(CatalogInfoRemoveEvent event) {
        evictCatalogInfo(event);
    }

    @EventListener(classes = {CatalogInfoModifyEvent.class})
    public void onCatalogInfoModifyEvent(CatalogInfoModifyEvent event) {
        if (CatalogInfoModifyEvent.class.equals(event.getClass())) {
            evictCatalogInfo(event);
        }
    }

    @EventListener(classes = {GeoServerInfoModifyEvent.class})
    public void onGeoServerInfoModifyEvent(GeoServerInfoModifyEvent event) {
        if (GeoServerInfoModifyEvent.class.equals(event.getClass())) {
            evictConfigEntry(event);
        }
    }

    @EventListener(classes = {UpdateSequenceEvent.class})
    public void onUpdateSequenceEvent(UpdateSequenceEvent event) {
        event.remote()
                .ifPresent(
                        evt -> {
                            applyUpdateSequence((UpdateSequenceEvent) evt);
                        });
    }

    @EventListener(classes = {LoggingInfoModifyEvent.class})
    public void onLoggingInfoModifyEvent(LoggingInfoModifyEvent event) {
        evictConfigEntry(event);
    }

    @EventListener(classes = {SettingsInfoModifyEvent.class})
    public void onSettingsInfoModifyEvent(SettingsInfoModifyEvent event) {
        evictConfigEntry(event);
    }

    @EventListener(classes = {ServiceInfoModifyEvent.class})
    public void onServiceInfoModifyEvent(ServiceInfoModifyEvent event) {
        evictConfigEntry(event);
    }

    @EventListener(classes = {SettingsInfoRemoveEvent.class})
    public void onSettingsInfoRemoveEvent(SettingsInfoRemoveEvent event) {
        evictConfigEntry(event);
    }

    @EventListener(classes = {ServiceInfoRemoveEvent.class})
    public void onServiceInfoRemoveEvent(ServiceInfoRemoveEvent event) {
        evictConfigEntry(event);
    }

    @EventListener(classes = {GeoServerInfoSetEvent.class})
    public void onSetGlobalInfoEvent(GeoServerInfoSetEvent event) {
        evictConfigEntry(event);
    }

    @EventListener(classes = {LoggingInfoSetEvent.class})
    public void onSetLoggingInfoEvent(LoggingInfoSetEvent event) {
        evictConfigEntry(event);
    }

    /**
     * Called when the only change to {@link GeoServerInfo} is its update sequence number, in order
     * to avoid evicting the locally cached object and apply the new update sequence to it instead.
     */
    private void applyUpdateSequence(UpdateSequenceEvent event) {
        GeoServerInfo global = config.getGlobal();
        long current = global.getUpdateSequence();
        long updateSequence = event.getUpdateSequence();
        if (updateSequence == current) {
            log.debug(
                    "Ignoring update sequence event, local value is up to date ({})",
                    updateSequence);
        } else if (updateSequence > current) {
            log.debug(
                    "Applying update sequence instead of evicting. Old: {}, new: {}",
                    current,
                    updateSequence);
            global.setUpdateSequence(updateSequence);
        } else {
            log.info(
                    "Ignoring update sequence event, current sequence ({}) is bigger than remote event's ({})",
                    current,
                    updateSequence);
        }
    }

    private void evictCatalogInfo(InfoEvent<?, ?> event) {
        evictEntry(
                event,
                () -> {
                    String objectId = event.getObjectId();
                    ConfigInfoType infoType = event.getObjectType();
                    CatalogInfo info =
                            (CatalogInfo) ResolvingProxy.create(objectId, infoType.getType());
                    return catalog.evict(info);
                });
    }

    public void evictConfigEntry(InfoEvent<?, ?> event) {
        evictEntry(
                event,
                () -> {
                    String objectId = event.getObjectId();
                    ConfigInfoType infoType = event.getObjectType();
                    Info info = ResolvingProxy.create(objectId, infoType.getType());
                    return config.evict(info);
                });
    }

    private void evictEntry(InfoEvent<?, ?> event, BooleanSupplier evictor) {
        event.remote()
                .ifPresent(
                        evt -> {
                            boolean evicted = evictor.getAsBoolean();
                            if (evicted) {
                                log.debug("Evicted cache entry {}", evt);
                            } else {
                                log.trace("Remote event resulted in no cache eviction: {}", evt);
                            }
                        });
    }
}
