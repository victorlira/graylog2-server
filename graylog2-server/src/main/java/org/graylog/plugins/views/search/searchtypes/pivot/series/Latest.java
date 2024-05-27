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
package org.graylog.plugins.views.search.searchtypes.pivot.series;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import org.graylog.plugins.formatting.units.model.UnitId;
import org.graylog.plugins.views.search.searchtypes.pivot.HasField;
import org.graylog.plugins.views.search.searchtypes.pivot.MayHaveUnit;
import org.graylog.plugins.views.search.searchtypes.pivot.SeriesSpec;

import javax.annotation.Nullable;
import java.util.Optional;

@AutoValue
@JsonTypeName(Latest.NAME)
@JsonDeserialize(builder = Latest.Builder.class)
public abstract class Latest implements SeriesSpec, HasField, MayHaveUnit {
    public static final String NAME = "latest";

    @Override
    public abstract String type();

    @Override
    public abstract String id();

    @Override
    public abstract Optional<UnitId> unit();

    @JsonProperty
    public abstract String field();

    @Override
    public String literal() {
        return type() + "(" + Strings.nullToEmpty(field()) + ")";
    }

    public abstract Builder toBuilder();

    @Override
    public Latest withId(String id) {
        return toBuilder().id(id).build();
    }

    public static Builder builder() {
        return new AutoValue_Latest.Builder().type(NAME);
    }

    @AutoValue.Builder
    public abstract static class Builder extends SeriesSpecBuilder<Latest, Latest.Builder> {
        @JsonCreator
        public static Builder create() {
            return Latest.builder();
        }

        @Override
        @JsonProperty
        public abstract Builder id(String id);

        @JsonProperty
        public abstract Builder field(String field);

        @JsonProperty
        public abstract Builder unit(@Nullable UnitId unit);

        abstract Optional<String> id();
        abstract String field();
        abstract Latest autoBuild();

        @Override
        public Latest build() {
            if (id().isEmpty()) {
                id(NAME + "(" + field() + ")");
            }
            return autoBuild();
        }
    }
}
