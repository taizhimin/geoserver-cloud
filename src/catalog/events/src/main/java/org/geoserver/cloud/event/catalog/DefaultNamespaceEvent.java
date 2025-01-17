/*
 * (c) 2020 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.cloud.event.catalog;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

import lombok.Getter;
import lombok.NonNull;

import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.plugin.Patch;
import org.geoserver.cloud.event.info.ConfigInfoType;
import org.geoserver.cloud.event.info.InfoEvent;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonTypeName("DefaultNamespaceSet")
public class DefaultNamespaceEvent extends CatalogInfoModifyEvent {

    private @Getter String newNamespaceId;

    protected DefaultNamespaceEvent() {}

    DefaultNamespaceEvent(String newNamespaceId, @NonNull Patch patch) {
        super(InfoEvent.CATALOG_ID, ConfigInfoType.Catalog, patch);
        this.newNamespaceId = newNamespaceId;
    }

    public @Override String toString() {
        return toStringBuilder().append("namespace", getNewNamespaceId()).toString();
    }

    public static DefaultNamespaceEvent createLocal(NamespaceInfo defaultNamespace) {

        String namespaceId = resolveId(defaultNamespace);
        Patch patch = new Patch();
        patch.add("defaultNamespace", defaultNamespace);
        return new DefaultNamespaceEvent(namespaceId, patch);
    }
}
