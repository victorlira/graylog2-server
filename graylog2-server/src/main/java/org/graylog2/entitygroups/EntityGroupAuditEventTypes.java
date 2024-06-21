/*
 * Copyright (C) 2020 Graylog, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */
package org.graylog2.entitygroups;

import com.google.common.collect.ImmutableSet;
import org.graylog2.audit.PluginAuditEventTypes;

import java.util.Set;

public class EntityGroupAuditEventTypes implements PluginAuditEventTypes {
    private static final String NAMESPACE = "entity_groups";
    private static final String PREFIX = NAMESPACE + ":entity_group";

    public static final String ENTITY_GROUP_CREATE = PREFIX + ":create";
    public static final String ENTITY_GROUP_UPDATE = PREFIX + ":update";
    public static final String ENTITY_GROUP_ADD_ENTITY = PREFIX + ":addEntity";
    public static final String ENTITY_GROUP_DELETE = PREFIX + ":delete";

    private static final Set<String> EVENT_TYPES = ImmutableSet.<String>builder()
            .add(ENTITY_GROUP_CREATE)
            .add(ENTITY_GROUP_UPDATE)
            .add(ENTITY_GROUP_ADD_ENTITY)
            .add(ENTITY_GROUP_DELETE)
            .build();

    @Override
    public Set<String> auditEventTypes() {
        return EVENT_TYPES;
    }
}
