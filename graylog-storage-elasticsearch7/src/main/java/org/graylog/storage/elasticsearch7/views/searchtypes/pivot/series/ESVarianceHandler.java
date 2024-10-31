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
package org.graylog.storage.elasticsearch7.views.searchtypes.pivot.series;

import org.graylog.plugins.views.search.searchtypes.pivot.Pivot;
import org.graylog.plugins.views.search.searchtypes.pivot.series.Variance;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.action.search.SearchResponse;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.search.aggregations.AggregationBuilders;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.search.aggregations.metrics.ExtendedStats;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.search.aggregations.metrics.ExtendedStatsAggregationBuilder;
import org.graylog.storage.elasticsearch7.views.ESGeneratedQueryContext;
import org.graylog.storage.elasticsearch7.views.searchtypes.ESSearchTypeHandler;
import org.graylog.storage.elasticsearch7.views.searchtypes.pivot.ESPivotSeriesSpecHandler;
import org.graylog.storage.elasticsearch7.views.searchtypes.pivot.SeriesAggregationBuilder;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.stream.Stream;

public class ESVarianceHandler extends ESPivotSeriesSpecHandler<Variance, ExtendedStats> {
    @Nonnull
    @Override
    public List<SeriesAggregationBuilder> doCreateAggregation(String name, Pivot pivot, Variance varianceSpec, ESSearchTypeHandler<Pivot> searchTypeHandler, ESGeneratedQueryContext queryContext) {
        final ExtendedStatsAggregationBuilder variance = AggregationBuilders.extendedStats(name).field(varianceSpec.field());
        record(queryContext, pivot, varianceSpec, name, ExtendedStats.class);
        return List.of(SeriesAggregationBuilder.metric(variance));
    }

    @Override
    public Stream<Value> doHandleResult(Pivot pivot, Variance pivotSpec,
                                        SearchResponse searchResult,
                                        ExtendedStats varianceAggregation,
                                        ESSearchTypeHandler<Pivot> searchTypeHandler,
                                        ESGeneratedQueryContext esGeneratedQueryContext) {
        return Stream.of(ESPivotSeriesSpecHandler.Value.create(pivotSpec.id(), Variance.NAME, varianceAggregation.getVariance()));
    }
}

