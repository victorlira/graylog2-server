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
package org.graylog.plugins.views.search;

import com.codahale.metrics.json.MetricsModule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.graylog.plugins.views.search.elasticsearch.ElasticsearchQueryString;
import org.graylog.plugins.views.search.engine.BackendQuery;
import org.graylog.plugins.views.search.filter.AndFilter;
import org.graylog.plugins.views.search.filter.OrFilter;
import org.graylog.plugins.views.search.filter.QueryStringFilter;
import org.graylog.plugins.views.search.filter.StreamCategoryFilter;
import org.graylog.plugins.views.search.filter.StreamFilter;
import org.graylog.plugins.views.search.rest.ExecutionState;
import org.graylog.plugins.views.search.rest.ExecutionStateGlobalOverride;
import org.graylog.plugins.views.search.rest.SearchTypeExecutionState;
import org.graylog.plugins.views.search.searchfilters.model.InlineQueryStringSearchFilter;
import org.graylog.plugins.views.search.searchfilters.model.ReferencedQueryStringSearchFilter;
import org.graylog.plugins.views.search.searchfilters.model.UsedSearchFilter;
import org.graylog.plugins.views.search.searchtypes.MessageList;
import org.graylog.plugins.views.search.searchtypes.events.EventList;
import org.graylog2.contentpacks.EntityDescriptorIds;
import org.graylog2.database.ObjectIdSerializer;
import org.graylog2.jackson.JodaTimePeriodKeyDeserializer;
import org.graylog2.plugin.indexer.searches.timeranges.InvalidRangeParametersException;
import org.graylog2.plugin.indexer.searches.timeranges.RelativeRange;
import org.graylog2.plugin.indexer.searches.timeranges.TimeRange;
import org.graylog2.shared.jackson.SizeSerializer;
import org.graylog2.shared.rest.RangeJsonSerializer;
import org.joda.time.Period;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class QueryTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        final ObjectMapper mapper = new ObjectMapper();
        final TypeFactory typeFactory = mapper.getTypeFactory().withClassLoader(this.getClass().getClassLoader());

        this.objectMapper = mapper
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .setPropertyNamingStrategy(new PropertyNamingStrategies.SnakeCaseStrategy())
                .setTypeFactory(typeFactory)
                .registerModule(new GuavaModule())
                .registerModule(new JodaModule())
                .registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule())
                .registerModule(new MetricsModule(TimeUnit.SECONDS, TimeUnit.SECONDS, false))
                .registerModule(new SimpleModule("Graylog")
                        .addKeyDeserializer(Period.class, new JodaTimePeriodKeyDeserializer())
                        .addSerializer(new RangeJsonSerializer())
                        .addSerializer(new SizeSerializer())
                        .addSerializer(new ObjectIdSerializer()));

        // kludge because we don't have an injector in tests
        ImmutableMap<String, Class> subtypes = ImmutableMap.<String, Class>builder()
                .put(StreamFilter.NAME, StreamFilter.class)
                .put(ElasticsearchQueryString.NAME, ElasticsearchQueryString.class)
                .put(MessageList.NAME, MessageList.class)
                .build();

        subtypes.forEach((name, klass) -> objectMapper.registerSubtypes(new NamedType(klass, name)));
    }

    @Test
    void mergeWithExecutionState() {
        final String messageListId = UUID.randomUUID().toString();
        Query query = Query.builder()
                .id("abc123")
                .timerange(RelativeRange.create(600))
                .query(ElasticsearchQueryString.of("*"))
                .searchTypes(ImmutableSet.of(MessageList.builder().id(messageListId).build()))
                .build();


        ExecutionStateGlobalOverride.Builder executionState = ExecutionStateGlobalOverride.builder();

        executionState.timerange(RelativeRange.create(60));
        executionState.searchTypesBuilder().put(messageListId, SearchTypeExecutionState.builder().offset(150).limit(300).build());

        final Query mergedQuery = query.applyExecutionState(executionState.build());
        assertThat(mergedQuery)
                .isNotEqualTo(query)
                .extracting(Query::timerange).extracting("range").isEqualTo(60);

        final Optional<SearchType> messageList = mergedQuery.searchTypes().stream().filter(searchType -> messageListId.equals(searchType.id())).findFirst();
        assertThat(messageList).isPresent();
        final MessageList msgList = (MessageList) messageList.get();
        assertThat(msgList).extracting(MessageList::offset).isEqualTo(150);
        assertThat(msgList).extracting(MessageList::limit).isEqualTo(300);
    }

    @Test
    void appliesExecutionStateTimeRangeToGlobalOverride() throws InvalidRangeParametersException {


        final ExecutionStateGlobalOverride executionState = ExecutionStateGlobalOverride.builder()
                .timerange(RelativeRange.create(60))
                .build();


        Query sut = validQueryBuilder().build();
        Query query = sut.applyExecutionState(executionState);
        assertThat(query.globalOverride()).hasValueSatisfying(go ->
                assertThat(go.timerange()).contains(relativeRange(60)));
    }

    @Test
    void appliesExecutionStateQueryToGlobalOverride() {

        final ExecutionStateGlobalOverride executionState = ExecutionStateGlobalOverride.builder()
                .query(ElasticsearchQueryString.of("NACKEN"))
                .build();

        Query sut = validQueryBuilder().build();
        Query query = sut.applyExecutionState(executionState);
        assertThat(query.globalOverride()).hasValueSatisfying(go ->
                assertThat(go.query()).contains(ElasticsearchQueryString.of("NACKEN")));
    }

    @Test
    void appliesExecutionStateTimeRangeAndQueryToGlobalOverrideIfBothArePresent() throws InvalidRangeParametersException {

        final ExecutionStateGlobalOverride executionState = ExecutionStateGlobalOverride.builder()
                .timerange(RelativeRange.create(60))
                .query(ElasticsearchQueryString.of("NACKEN"))
                .build();


        Query sut = validQueryBuilder().build();
        Query query = sut.applyExecutionState(executionState);
        assertThat(query.globalOverride()).hasValueSatisfying(go -> {
            assertThat(go.timerange()).contains(relativeRange(60));
            assertThat(go.query()).contains(ElasticsearchQueryString.of("NACKEN"));
        });
    }

    @Test
    void doesNotAddGlobalOverrideIfNeitherTimeRangeNorQueryArePresent() {

        final ExecutionStateGlobalOverride.Builder executionState = ExecutionStateGlobalOverride.builder();
        executionState.searchTypesBuilder().put("some-id",
                SearchTypeExecutionState.builder().offset(150).limit(300).build());

        Query sut = validQueryBuilder().build();
        Query query = sut.applyExecutionState(executionState.build());
        assertThat(query.globalOverride()).isEmpty();
    }

    @Test
    void builderGeneratesQueryId() {
        final Query build = Query.builder().timerange(mock(TimeRange.class)).query(ElasticsearchQueryString.empty()).build();
        assertThat(build.id()).isNotNull();
    }

    @Test
    void builderGeneratesDefaultQueryAndRange() {
        final Query build = Query.builder().build();
        final BackendQuery query = build.query();
        assertThat(query.queryString()).isEqualTo("");
        assertThat(build.timerange()).isNotNull();
    }

    private RelativeRange relativeRange(int range) {
        try {
            return RelativeRange.create(range);
        } catch (InvalidRangeParametersException e) {
            throw new RuntimeException("invalid time range", e);
        }
    }

    private Query.Builder validQueryBuilder() {
        return Query.builder().id(UUID.randomUUID().toString()).timerange(mock(TimeRange.class)).query(ElasticsearchQueryString.empty());
    }

    /**
     * Test that json parser recognizes full query with its type and query string value as an object (backwards compatibility)
     */
    @Test
    void testFullQueryWithType() throws IOException {
        final Query query = objectMapper.readValue(getClass().getResourceAsStream("/org/graylog/plugins/views/search/query/full-query.json"), Query.class);
        final ElasticsearchQueryString queryString = (ElasticsearchQueryString) query.query();
        assertThat(queryString.queryString()).isEqualTo("some-full-query");
    }

    /**
     * Test that json parser recognizes query that's just a string, not object
     */
    @Test
    void testSimpleQuery() throws IOException {
        final Query query = objectMapper.readValue(getClass().getResourceAsStream("/org/graylog/plugins/views/search/query/simple-query.json"), Query.class);
        final ElasticsearchQueryString queryString = (ElasticsearchQueryString) query.query();
        assertThat(queryString.queryString()).isEqualTo("some-simple-query");
    }

    @Test
    void testSerializeQuery() throws JsonProcessingException {
        final String value = objectMapper.writeValueAsString(ElasticsearchQueryString.of("foo:bar"));
        assertThat(value).isEqualTo("{\"type\":\"elasticsearch\",\"query_string\":\"foo:bar\"}");
    }

    @Test
    void testHasReferencedSearchFiltersReturnsFalseOnEmptySearchFilters() {
        Query query = Query.builder()
                .filters(Collections.emptyList())
                .build();

        assertThat(query.hasReferencedStreamFilters())
                .isFalse();
    }

    @Test
    void testHasReferencedSearchFiltersReturnsFalseWhenNoReferencedSearchFilters() {
        Query query = Query.builder()
                .filters(Collections.singletonList(InlineQueryStringSearchFilter.builder().title("title").description("descr").queryString("*").build()))
                .build();

        assertThat(query.hasReferencedStreamFilters())
                .isFalse();
    }

    @Test
    void testHasReferencedSearchFiltersReturnsTrueWhenReferencedSearchFilterPresent() {
        Query query = Query.builder()
                .filters(ImmutableList.of(
                        InlineQueryStringSearchFilter.builder().title("title").description("descr").queryString("*").build(),
                        ReferencedQueryStringSearchFilter.create("007")))
                .build();

        assertThat(query.hasReferencedStreamFilters())
                .isTrue();
    }

    @Test
    void testSavesEmptySearchFiltersCollectionInContentPack() {
        Query noFiltersQuery = Query.builder().build();
        assertThat(noFiltersQuery.toContentPackEntity(EntityDescriptorIds.empty()).filters())
                .isNotNull()
                .isEmpty();
    }

    @Test
    void testSavesSearchFiltersCollectionInContentPack() {
        final ImmutableList<UsedSearchFilter> originalSearchFilters = ImmutableList.of(
                InlineQueryStringSearchFilter.builder().title("title").description("descr").queryString("*").build(),
                ReferencedQueryStringSearchFilter.create("007")
        );
        Query queryWithFilters = Query.builder().filters(originalSearchFilters).build();
        assertThat(queryWithFilters.toContentPackEntity(EntityDescriptorIds.empty()).filters())
                .isNotNull()
                .isEqualTo(originalSearchFilters);
    }

    @Test
    void collectsStreamIdsForPermissionsCheckFromFiltersAndSearchTypes() {
        Query query = Query.builder()
                .id("Test query")
                .query(ElasticsearchQueryString.of("*"))
                .searchTypes(
                        Set.of(
                                MessageList.builder().streams(Set.of("a", "b")).build(),
                                EventList.builder().streams(Set.of("b", "c")).build()
                        )
                )
                .build();
        query = query.addStreamsToFilter(Set.of("x", "y"));

        assertThat(query.streamIdsForPermissionsCheck())
                .isNotNull()
                .hasSize(5)
                .contains("a", "b", "c", "x", "y");
    }

    @Test
    void prefersGlobalOverrideIfPresentWhileApplyingExecutionState() {
        final ExecutionStateGlobalOverride executionStateGlobalOverride = ExecutionStateGlobalOverride.builder().query(ElasticsearchQueryString.of("global")).build();
        final ExecutionState.Builder builder = ExecutionState.builder()
                .setGlobalOverride(executionStateGlobalOverride)
                .withParameterBindings(Map.of());
        builder.queriesBuilder().put("query1", ExecutionStateGlobalOverride.builder().query(ElasticsearchQueryString.of("query")).build());
        ExecutionState executionState = builder.build();


        Query query = Query.builder().id("query1").build();

        query = query.applyExecutionState(executionState);

        assertThat(query.query().queryString())
                .isEqualTo("global");
    }

    @Test
    void appliesProperQueryExecutionStateIfEmptyGlobalOverride() {
        final ExecutionState.Builder builder = ExecutionState.builder()
                .setGlobalOverride(ExecutionStateGlobalOverride.empty())
                .withParameterBindings(Map.of());
        builder.queriesBuilder().put("query1", ExecutionStateGlobalOverride.builder().query(ElasticsearchQueryString.of("query")).build());
        builder.queriesBuilder().put("query2", ExecutionStateGlobalOverride.builder().query(ElasticsearchQueryString.of("1+1=2")).build());
        ExecutionState executionState = builder.build();

        Query query = Query.builder().id("query1").build();

        query = query.applyExecutionState(executionState);

        assertThat(query.query().queryString())
                .isEqualTo("query");
    }

    @Test
    void replaceStreamCategoryFiltersWithStreamFilters() {
        var queryWithCategories = Query.builder()
                .id("query1")
                .query(ElasticsearchQueryString.of("*"))
                .filter(AndFilter.and(StreamCategoryFilter.ofCategory("colors"), StreamCategoryFilter.ofCategory("numbers")))
                .build();

        queryWithCategories = queryWithCategories.replaceStreamCategoryFilters(this::categoryMapping, streamId -> true);
        Filter filter = queryWithCategories.filter();
        assertThat(filter).isInstanceOf(AndFilter.class);
        assertThat(filter.filters()).isNotNull();
        // The two StreamCategoryFilters should have been replaced with two OrFilters of three StreamFilters
        assertThat(filter.filters()).hasSize(2);
        assertThat(filter.filters().stream()).allSatisfy(f -> {
            assertThat(f).isInstanceOf(OrFilter.class);
            assertThat(f.filters()).isNotEmpty();
            assertThat(f.filters()).hasSize(3);
            assertThat(f.filters().stream()).allSatisfy(f2 -> {
                assertThat(f2).isInstanceOf(StreamFilter.class);
                assertThat(f2.filters()).isNull();
            });
        });
    }

    @Test
    void replaceStreamCategoryFiltersLeavesOtherFiltersAlone() {
        var queryWithCategories = Query.builder()
                .id("query1")
                .query(ElasticsearchQueryString.of("*"))
                .filter(AndFilter.builder()
                        .filters(ImmutableSet.<Filter>builder()
                                .add(OrFilter.or(StreamCategoryFilter.ofCategory("colors"), StreamCategoryFilter.ofCategory("numbers")))
                                .add(QueryStringFilter.builder().query("source:localhost").build())
                                .build())
                        .build())
                .build();

        queryWithCategories = queryWithCategories.replaceStreamCategoryFilters(this::categoryMapping, streamId -> true);
        Filter filter = queryWithCategories.filter();
        assertThat(filter).isInstanceOf(AndFilter.class);
        assertThat(filter.filters()).isNotNull();
        assertThat(filter.filters()).hasSize(2);
        // The QueryStringFilter should have been left alone in the replacement
        assertThat(filter.filters().stream()).satisfiesOnlyOnce(f -> {
            assertThat(f).isInstanceOf(QueryStringFilter.class);
            assertThat(f.filters()).isNull();
            assertThat(((QueryStringFilter) f).query()).isEqualTo("source:localhost");
        });
        // The OrFilter of StreamCategoryFilters should have been converted to an OrFilter of StreamFilters
        assertThat(filter.filters().stream()).satisfiesOnlyOnce(f -> {
            assertThat(f).isInstanceOf(OrFilter.class);
            assertThat(f.filters()).isNotEmpty();
            assertThat(f.filters()).hasSize(2);
            assertThat(f.filters().stream()).allSatisfy(f2 -> {
                assertThat(f2).isInstanceOf(OrFilter.class);
                assertThat(f2.filters()).isNotEmpty();
                assertThat(f2.filters()).hasSize(3);
                assertThat(f2.filters().stream()).allSatisfy(f3 -> {
                    assertThat(f3).isInstanceOf(StreamFilter.class);
                    assertThat(f3.filters()).isNull();
                });
            });
        });
    }

    @Test
    void replaceStreamCategoryFiltersRespectsPermissions() {
        var queryWithCategories = Query.builder()
                .id("query1")
                .query(ElasticsearchQueryString.of("*"))
                .filter(AndFilter.and(StreamCategoryFilter.ofCategory("colors"), StreamCategoryFilter.ofCategory("numbers")))
                .build();

        queryWithCategories = queryWithCategories.replaceStreamCategoryFilters(this::categoryMapping, (streamId) -> List.of("blue", "red", "one", "two").contains(streamId));
        Filter filter = queryWithCategories.filter();
        assertThat(filter).isInstanceOf(AndFilter.class);
        assertThat(filter.filters()).isNotNull();
        // The two StreamCategoryFilters should have been replaced with two OrFilters of two StreamFilters
        assertThat(filter.filters()).hasSize(2);
        assertThat(filter.filters().stream()).allSatisfy(f -> {
            assertThat(f).isInstanceOf(OrFilter.class);
            assertThat(f.filters()).isNotEmpty();
            assertThat(f.filters()).hasSize(2);
            assertThat(f.filters().stream()).allSatisfy(f2 -> {
                assertThat(f2).isInstanceOf(StreamFilter.class);
                assertThat(f2.filters()).isNull();
            });
        });
    }

    @Test
    void replacementLeavesNoFilters() {
        var queryWithCategories = Query.builder()
                .id("query1")
                .query(ElasticsearchQueryString.of("*"))
                .filter(AndFilter.and(StreamCategoryFilter.ofCategory("colors"), StreamCategoryFilter.ofCategory("numbers")))
                .build();

        queryWithCategories = queryWithCategories.replaceStreamCategoryFilters(this::categoryMapping, (streamId) -> false);
        Filter filter = queryWithCategories.filter();
        assertThat(filter).isNull();
    }

    @Test
    void emptyReplacementFiltersAreRemoved() {
        var queryWithCategories = Query.builder()
                .id("query1")
                .query(ElasticsearchQueryString.of("*"))
                .filter(AndFilter.builder()
                        .filters(ImmutableSet.<Filter>builder()
                                .add(OrFilter.or(StreamCategoryFilter.ofCategory("colors"), StreamCategoryFilter.ofCategory("numbers")))
                                .add(QueryStringFilter.builder().query("source:localhost").build())
                                .build())
                        .build())
                .build();

        queryWithCategories = queryWithCategories.replaceStreamCategoryFilters(this::categoryMapping, (streamId) -> false);
        Filter filter = queryWithCategories.filter();
        assertThat(filter).isInstanceOf(AndFilter.class);
        assertThat(filter.filters()).isNotNull();
        assertThat(filter.filters()).hasSize(1);
    }

    private Stream<String> categoryMapping(Collection<String> categories) {
        Set<String> streams = new HashSet<>();
        if (categories.contains("colors")) {
            streams.addAll(List.of("red", "yellow", "blue"));
        }
        if (categories.contains("numbers")) {
            streams.addAll(List.of("one", "two", "three"));
        }
        if (categories.contains("animals")) {
            streams.addAll(List.of("cat", "dog", "fox"));
        }
        return streams.stream();
    }
}
