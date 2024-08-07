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
package org.graylog.storage.opensearch2.views.searchtypes;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.inject.Inject;
import org.graylog.events.event.EventDto;
import org.graylog.plugins.views.search.Query;
import org.graylog.plugins.views.search.SearchJob;
import org.graylog.plugins.views.search.SearchType;
import org.graylog.plugins.views.search.searchtypes.events.CommonEventSummary;
import org.graylog.plugins.views.search.searchtypes.events.EventList;
import org.graylog.plugins.views.search.searchtypes.events.EventSummary;
import org.graylog.shaded.opensearch2.org.opensearch.action.search.SearchResponse;
import org.graylog.shaded.opensearch2.org.opensearch.index.query.BoolQueryBuilder;
import org.graylog.shaded.opensearch2.org.opensearch.index.query.QueryBuilders;
import org.graylog.shaded.opensearch2.org.opensearch.search.SearchHit;
import org.graylog.shaded.opensearch2.org.opensearch.search.aggregations.Aggregations;
import org.graylog.shaded.opensearch2.org.opensearch.search.sort.FieldSortBuilder;
import org.graylog.shaded.opensearch2.org.opensearch.search.sort.SortOrder;
import org.graylog.storage.opensearch2.views.OSGeneratedQueryContext;
import org.graylog2.streams.StreamService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class OSEventList implements EventListStrategy {
    private final ObjectMapper objectMapper;
    private final StreamService streamService;

    @Inject
    public OSEventList(ObjectMapper objectMapper, StreamService streamService) {
        this.objectMapper = objectMapper;
        this.streamService = streamService;
    }

    @Override
    public void doGenerateQueryPart(Query query, EventList eventList,
                                    OSGeneratedQueryContext queryContext) {
        final Set<String> queryStreamIds = query.usedStreamIds();
        queryStreamIds.addAll(streamService.mapCategoriesToIds(query.usedStreamCategories()));
        final Set<String> effectiveStreams = eventList.streams().isEmpty()
                ? queryStreamIds
                : eventList.streams();

        final var searchSourceBuilder = queryContext.searchSourceBuilder(eventList);
        final FieldSortBuilder sortConfig = sortConfig(eventList);
        searchSourceBuilder.sort(sortConfig);
        final var queryBuilder = searchSourceBuilder.query();
        if (!effectiveStreams.isEmpty() && queryBuilder instanceof BoolQueryBuilder boolQueryBuilder) {
            boolQueryBuilder.must(QueryBuilders.termsQuery(EventDto.FIELD_SOURCE_STREAMS, effectiveStreams));
        }
        if (!eventList.attributes().isEmpty() && queryBuilder instanceof BoolQueryBuilder boolQueryBuilder) {
            final var filterQueries = eventList.attributes().stream()
                    .filter(attribute -> EventList.KNOWN_ATTRIBUTES.contains(attribute.field()))
                    .flatMap(attribute -> attribute.toQueryStrings().stream())
                    .toList();

            filterQueries.forEach(filterQuery -> boolQueryBuilder.filter(QueryBuilders.queryStringQuery(filterQuery)));
        }

        eventList.page().ifPresentOrElse(page -> {
            final int pageSize = eventList.perPage().orElse(EventList.DEFAULT_PAGE_SIZE);
            searchSourceBuilder.size(pageSize);
            searchSourceBuilder.from((page - 1) * pageSize);
        }, () -> searchSourceBuilder.size(10000));
    }

    private SortOrder toSortOrder(EventList.Direction direction) {
        return switch (direction) {
            case ASC -> SortOrder.ASC;
            case DESC -> SortOrder.DESC;
        };
    }

    protected FieldSortBuilder sortConfig(EventList eventList) {
        final var sortConfig = eventList.sort()
                .filter(sort -> EventList.KNOWN_ATTRIBUTES.contains(sort.field()))
                .orElse(EventList.DEFAULT_SORT);
        return new FieldSortBuilder(sortConfig.field()).order(toSortOrder(sortConfig.direction()));
    }

    protected List<Map<String, Object>> extractResult(SearchResponse result) {
        return StreamSupport.stream(result.getHits().spliterator(), false)
                .map(SearchHit::getSourceAsMap)
                .collect(Collectors.toList());
    }

    @WithSpan
    @Override
    public SearchType.Result doExtractResult(SearchJob job, Query query, EventList searchType, SearchResponse result,
                                             Aggregations aggregations, OSGeneratedQueryContext queryContext) {
        final List<CommonEventSummary> eventSummaries = extractResult(result).stream()
                .map(rawEvent -> objectMapper.convertValue(rawEvent, EventDto.class))
                .map(EventSummary::parse)
                .collect(Collectors.toList());
        final EventList.Result.Builder resultBuilder = EventList.Result.builder()
                .events(eventSummaries)
                .id(searchType.id())
                .totalResults(result.getHits().getTotalHits().value);
        searchType.name().ifPresent(resultBuilder::name);
        return resultBuilder.build();
    }
}
