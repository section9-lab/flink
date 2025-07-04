/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.catalog;

import org.apache.flink.api.common.JobID;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.catalog.exceptions.TableNotExistException;
import org.apache.flink.table.expressions.DefaultSqlFactory;
import org.apache.flink.table.expressions.ResolvedExpression;
import org.apache.flink.table.expressions.resolver.ExpressionResolver.ExpressionResolverBuilder;
import org.apache.flink.table.expressions.utils.ResolvedExpressionMock;
import org.apache.flink.table.legacy.api.TableColumn;
import org.apache.flink.table.legacy.api.TableSchema;
import org.apache.flink.table.refresh.ContinuousRefreshHandler;
import org.apache.flink.table.refresh.ContinuousRefreshHandlerSerializer;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.table.utils.CatalogManagerMocks;
import org.apache.flink.table.utils.ExpressionResolverMocks;
import org.apache.flink.table.utils.ParserMock;

import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.flink.table.utils.CatalogManagerMocks.DEFAULT_CATALOG;
import static org.apache.flink.table.utils.CatalogManagerMocks.DEFAULT_DATABASE;
import static org.apache.flink.table.utils.EncodingUtils.encodeBytesToBase64;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CatalogTable} to {@link ResolvedCatalogTable}, {@link CatalogMaterializedTable}
 * to {@link ResolvedCatalogMaterializedTable} and {@link CatalogView} to {@link
 * ResolvedCatalogView} including {@link CatalogPropertiesUtil}.
 */
class CatalogBaseTableResolutionTest {

    private static final ObjectIdentifier IDENTIFIER =
            ObjectIdentifier.of(DEFAULT_CATALOG, DEFAULT_DATABASE, "TestTable");

    private static final String COMPUTED_SQL = "orig_ts - INTERVAL '60' MINUTE";

    private static final ResolvedExpression COMPUTED_COLUMN_RESOLVED =
            new ResolvedExpressionMock(DataTypes.TIMESTAMP(3), () -> COMPUTED_SQL);

    private static final String WATERMARK_SQL = "ts - INTERVAL '5' SECOND";

    private static final ResolvedExpression WATERMARK_RESOLVED =
            new ResolvedExpressionMock(DataTypes.TIMESTAMP(3), () -> WATERMARK_SQL);

    private static final Schema TABLE_SCHEMA =
            Schema.newBuilder()
                    .column("id", DataTypes.INT().notNull())
                    .column("region", DataTypes.VARCHAR(200))
                    .withComment("This is a region column.")
                    .column("county", DataTypes.VARCHAR(200))
                    .columnByMetadata("topic", DataTypes.VARCHAR(200), true)
                    .withComment("") // empty column comment
                    .columnByMetadata("orig_ts", DataTypes.TIMESTAMP(3), "timestamp")
                    .columnByExpression("ts", COMPUTED_SQL)
                    .withComment("This is a computed column")
                    .watermark("ts", WATERMARK_SQL)
                    .primaryKeyNamed("primary_constraint", "id")
                    .indexNamed("idx", Collections.singletonList("id"))
                    .build();

    private static final Schema MATERIALIZED_TABLE_SCHEMA =
            Schema.newBuilder()
                    .column("id", DataTypes.INT().notNull())
                    .column("region", DataTypes.VARCHAR(200))
                    .withComment("This is a region column.")
                    .column("county", DataTypes.VARCHAR(200))
                    .column("topic", DataTypes.VARCHAR(200))
                    .withComment("") // empty column comment
                    .primaryKeyNamed("primary_constraint", "id")
                    .indexNamed("idx", Collections.singletonList("id"))
                    .build();

    private static final TableSchema LEGACY_TABLE_SCHEMA =
            TableSchema.builder()
                    .add(TableColumn.physical("id", DataTypes.INT().notNull()))
                    .add(TableColumn.physical("region", DataTypes.VARCHAR(200)))
                    .add(TableColumn.physical("county", DataTypes.VARCHAR(200)))
                    .add(TableColumn.metadata("topic", DataTypes.VARCHAR(200), true))
                    .add(TableColumn.metadata("orig_ts", DataTypes.TIMESTAMP(3), "timestamp"))
                    .add(TableColumn.computed("ts", DataTypes.TIMESTAMP(3), COMPUTED_SQL))
                    .watermark("ts", WATERMARK_SQL, DataTypes.TIMESTAMP(3))
                    .primaryKey("primary_constraint", new String[] {"id"})
                    .build();

    private static final Schema VIEW_SCHEMA =
            Schema.newBuilder()
                    .column("id", DataTypes.INT().notNull())
                    .column("region", DataTypes.VARCHAR(200))
                    .column("county", DataTypes.VARCHAR(200))
                    .build();

    private static final ResolvedSchema RESOLVED_TABLE_SCHEMA =
            new ResolvedSchema(
                    Arrays.asList(
                            Column.physical("id", DataTypes.INT().notNull()),
                            Column.physical("region", DataTypes.VARCHAR(200))
                                    .withComment("This is a region column."),
                            Column.physical("county", DataTypes.VARCHAR(200)),
                            Column.metadata("topic", DataTypes.VARCHAR(200), null, true)
                                    .withComment(""), // empty column comment
                            Column.metadata("orig_ts", DataTypes.TIMESTAMP(3), "timestamp", false),
                            Column.computed("ts", COMPUTED_COLUMN_RESOLVED)
                                    .withComment("This is a computed column")),
                    Collections.singletonList(WatermarkSpec.of("ts", WATERMARK_RESOLVED)),
                    UniqueConstraint.primaryKey(
                            "primary_constraint", Collections.singletonList("id")),
                    Collections.singletonList(
                            DefaultIndex.newIndex("idx", Collections.singletonList("id"))));

    private static final ResolvedSchema RESOLVED_MATERIALIZED_TABLE_SCHEMA =
            new ResolvedSchema(
                    Arrays.asList(
                            Column.physical("id", DataTypes.INT().notNull()),
                            Column.physical("region", DataTypes.VARCHAR(200))
                                    .withComment("This is a region column."),
                            Column.physical("county", DataTypes.VARCHAR(200)),
                            Column.physical("topic", DataTypes.VARCHAR(200)).withComment("")),
                    Collections.emptyList(),
                    UniqueConstraint.primaryKey(
                            "primary_constraint", Collections.singletonList("id")),
                    Collections.singletonList(
                            DefaultIndex.newIndex("idx", Collections.singletonList("id"))));

    private static final ContinuousRefreshHandler CONTINUOUS_REFRESH_HANDLER =
            new ContinuousRefreshHandler(
                    "remote", "StandaloneClusterId", JobID.generate().toHexString());

    private static final String DEFINITION_QUERY =
            String.format(
                    "SELECT id, region, county FROM %s.%s.T", DEFAULT_CATALOG, DEFAULT_DATABASE);

    private static final ResolvedSchema RESOLVED_VIEW_SCHEMA =
            new ResolvedSchema(
                    Arrays.asList(
                            Column.physical("id", DataTypes.INT().notNull()),
                            Column.physical("region", DataTypes.VARCHAR(200)),
                            Column.physical("county", DataTypes.VARCHAR(200))),
                    Collections.emptyList(),
                    null,
                    Collections.emptyList());

    @Test
    void testCatalogTableResolution() {
        final CatalogTable table = catalogTable();

        assertThat(table.getUnresolvedSchema()).isNotNull();

        final ResolvedCatalogTable resolvedTable =
                resolveCatalogBaseTable(ResolvedCatalogTable.class, table);

        assertThat(resolvedTable.getResolvedSchema()).isEqualTo(RESOLVED_TABLE_SCHEMA);

        assertThat(resolvedTable.getSchema()).isEqualTo(LEGACY_TABLE_SCHEMA);
    }

    @Test
    void testCatalogMaterializedTableResolution() {
        final CatalogMaterializedTable materializedTable = catalogMaterializedTable();

        assertThat(materializedTable.getUnresolvedSchema()).isNotNull();

        final ResolvedCatalogMaterializedTable resolvedMaterializedTable =
                resolveCatalogBaseTable(ResolvedCatalogMaterializedTable.class, materializedTable);

        assertThat(resolvedMaterializedTable.getResolvedSchema())
                .isEqualTo(RESOLVED_MATERIALIZED_TABLE_SCHEMA);

        assertThat(resolvedMaterializedTable.getDefinitionQuery())
                .isEqualTo(materializedTable.getDefinitionQuery());
        assertThat(resolvedMaterializedTable.getFreshness())
                .isEqualTo(materializedTable.getFreshness());
        assertThat(resolvedMaterializedTable.getLogicalRefreshMode())
                .isEqualTo(materializedTable.getLogicalRefreshMode());
        assertThat(resolvedMaterializedTable.getRefreshMode())
                .isEqualTo(materializedTable.getRefreshMode());
        assertThat(resolvedMaterializedTable.getRefreshStatus())
                .isEqualTo(materializedTable.getRefreshStatus());
    }

    @Test
    void testCatalogViewResolution() {
        final CatalogView view = catalogView();

        final ResolvedCatalogView resolvedView =
                resolveCatalogBaseTable(ResolvedCatalogView.class, view);

        assertThat(resolvedView.getResolvedSchema()).isEqualTo(RESOLVED_VIEW_SCHEMA);
    }

    @Test
    void testPropertyDeSerialization() throws Exception {
        final CatalogTable table = CatalogTable.fromProperties(catalogTableAsProperties());

        final ResolvedCatalogTable resolvedTable =
                resolveCatalogBaseTable(ResolvedCatalogTable.class, table);

        assertThat(resolvedTable.toProperties(DefaultSqlFactory.INSTANCE))
                .isEqualTo(catalogTableAsProperties());

        assertThat(resolvedTable.getResolvedSchema()).isEqualTo(RESOLVED_TABLE_SCHEMA);

        // test materialized table de/serialization
        final CatalogMaterializedTable catalogMaterializedTable =
                CatalogPropertiesUtil.deserializeCatalogMaterializedTable(
                        catalogMaterializedTableAsProperties());
        final ResolvedCatalogMaterializedTable resolvedCatalogMaterializedTable =
                resolveCatalogBaseTable(
                        ResolvedCatalogMaterializedTable.class, catalogMaterializedTable);
        assertThat(
                        CatalogPropertiesUtil.serializeCatalogMaterializedTable(
                                resolvedCatalogMaterializedTable, DefaultSqlFactory.INSTANCE))
                .isEqualTo(catalogMaterializedTableAsProperties());

        assertThat(resolvedCatalogMaterializedTable.getResolvedSchema())
                .isEqualTo(RESOLVED_MATERIALIZED_TABLE_SCHEMA);
        assertThat(resolvedCatalogMaterializedTable.getDefinitionFreshness())
                .isEqualTo(IntervalFreshness.ofSecond("30"));
        assertThat(resolvedCatalogMaterializedTable.getDefinitionQuery())
                .isEqualTo(DEFINITION_QUERY);
        assertThat(resolvedCatalogMaterializedTable.getLogicalRefreshMode())
                .isEqualTo(CatalogMaterializedTable.LogicalRefreshMode.CONTINUOUS);
        assertThat(resolvedCatalogMaterializedTable.getRefreshMode())
                .isEqualTo(CatalogMaterializedTable.RefreshMode.CONTINUOUS);
        assertThat(resolvedCatalogMaterializedTable.getRefreshStatus())
                .isEqualTo(CatalogMaterializedTable.RefreshStatus.INITIALIZING);
        byte[] expectedBytes =
                ContinuousRefreshHandlerSerializer.INSTANCE.serialize(CONTINUOUS_REFRESH_HANDLER);
        assertThat(resolvedCatalogMaterializedTable.getSerializedRefreshHandler())
                .isEqualTo(expectedBytes);
    }

    @Test
    void testPropertyDeserializationError() {
        assertThatThrownBy(
                        () -> {
                            final Map<String, String> properties = catalogTableAsProperties();
                            properties.remove("schema.4.data-type");
                            CatalogTable.fromProperties(properties);
                        })
                .hasRootCauseMessage("Could not find property key 'schema.4.data-type'.");
    }

    @Test
    void testInvalidPartitionKeys() {
        final CatalogTable catalogTable =
                CatalogTable.newBuilder()
                        .schema(TABLE_SCHEMA)
                        .comment(null)
                        .partitionKeys(Arrays.asList("region", "countyINVALID"))
                        .options(Collections.emptyMap())
                        .build();

        assertThatThrownBy(() -> resolveCatalogBaseTable(ResolvedCatalogTable.class, catalogTable))
                .hasRootCauseMessage(
                        "Invalid partition key 'countyINVALID'. A partition key must "
                                + "reference a physical column in the schema. Available "
                                + "columns are: [id, region, county]");
    }

    @Test
    void testValidDistributionKeys() {
        final CatalogTable catalogTable =
                new DefaultCatalogTable(
                        TABLE_SCHEMA,
                        null,
                        Collections.emptyList(),
                        Collections.emptyMap(),
                        null,
                        TableDistribution.ofHash(Collections.singletonList("county"), 6));
        final ResolvedCatalogTable resolvedTable =
                resolveCatalogBaseTable(ResolvedCatalogTable.class, catalogTable);
        assertThat(resolvedTable.getDistribution().get().getBucketKeys())
                .isEqualTo(Collections.singletonList("county"));
        assertThat(resolvedTable.getDistribution().get().getKind())
                .isEqualTo(TableDistribution.Kind.HASH);
    }

    @Test
    void testInvalidDistributionKeys() {
        final CatalogTable catalogTable =
                new DefaultCatalogTable(
                        TABLE_SCHEMA,
                        null,
                        Collections.emptyList(),
                        Collections.emptyMap(),
                        null,
                        TableDistribution.ofHash(Collections.singletonList("countyINVALID"), 6));
        assertThatThrownBy(() -> resolveCatalogBaseTable(ResolvedCatalogTable.class, catalogTable))
                .hasRootCauseMessage(
                        "Invalid bucket key 'countyINVALID'. A bucket key for a distribution must "
                                + "reference a physical column in the schema. "
                                + "Available columns are: [id, region, county]");
    }

    @Test
    void testInvalidDistributionBucketCount() {
        final CatalogTable catalogTable =
                new DefaultCatalogTable(
                        TABLE_SCHEMA,
                        null,
                        Collections.emptyList(),
                        Collections.emptyMap(),
                        null,
                        TableDistribution.ofHash(Collections.singletonList("id"), 0));

        assertThatThrownBy(() -> resolveCatalogBaseTable(ResolvedCatalogTable.class, catalogTable))
                .hasRootCauseMessage(
                        "Invalid bucket count '0'. The number of buckets for a "
                                + "distributed table must be at least 1.");
    }

    // --------------------------------------------------------------------------------------------
    // Utilities
    // --------------------------------------------------------------------------------------------

    private static CatalogTable catalogTable() {
        final String comment = "This is an example table.";

        final List<String> partitionKeys = Arrays.asList("region", "county");

        final Map<String, String> options = new HashMap<>();
        options.put("connector", "custom");
        options.put("version", "12");

        return CatalogTable.newBuilder()
                .schema(TABLE_SCHEMA)
                .comment(comment)
                .partitionKeys(partitionKeys)
                .options(options)
                .build();
    }

    private static Map<String, String> catalogTableAsProperties() {
        final Map<String, String> properties = new HashMap<>();
        properties.put("schema.0.name", "id");
        properties.put("schema.0.data-type", "INT NOT NULL");
        properties.put("schema.1.name", "region");
        properties.put("schema.1.data-type", "VARCHAR(200)");
        properties.put("schema.1.comment", "This is a region column.");
        properties.put("schema.2.name", "county");
        properties.put("schema.2.data-type", "VARCHAR(200)");
        properties.put("schema.3.name", "topic");
        properties.put("schema.3.data-type", "VARCHAR(200)");
        properties.put("schema.3.comment", "");
        properties.put("schema.3.metadata", "topic");
        properties.put("schema.3.virtual", "true");
        properties.put("schema.4.name", "orig_ts");
        properties.put("schema.4.data-type", "TIMESTAMP(3)");
        properties.put("schema.4.metadata", "timestamp");
        properties.put("schema.4.virtual", "false");
        properties.put("schema.5.name", "ts");
        properties.put("schema.5.data-type", "TIMESTAMP(3)");
        properties.put("schema.5.expr", "orig_ts - INTERVAL '60' MINUTE");
        properties.put("schema.5.comment", "This is a computed column");
        properties.put("schema.watermark.0.rowtime", "ts");
        properties.put("schema.watermark.0.strategy.data-type", "TIMESTAMP(3)");
        properties.put("schema.watermark.0.strategy.expr", "ts - INTERVAL '5' SECOND");
        properties.put("schema.primary-key.name", "primary_constraint");
        properties.put("schema.primary-key.columns", "id");
        properties.put("schema.index.0.name", "idx");
        properties.put("schema.index.0.columns", "id");
        properties.put("partition.keys.0.name", "region");
        properties.put("partition.keys.1.name", "county");
        properties.put("version", "12");
        properties.put("connector", "custom");
        properties.put("comment", "This is an example table.");
        properties.put("snapshot", "1688918400000");

        return properties;
    }

    private static Map<String, String> catalogMaterializedTableAsProperties() throws Exception {
        // add base properties
        final Map<String, String> properties = new HashMap<>();
        properties.put("schema.0.name", "id");
        properties.put("schema.0.data-type", "INT NOT NULL");
        properties.put("schema.1.name", "region");
        properties.put("schema.1.data-type", "VARCHAR(200)");
        properties.put("schema.1.comment", "This is a region column.");
        properties.put("schema.2.name", "county");
        properties.put("schema.2.data-type", "VARCHAR(200)");
        properties.put("schema.3.name", "topic");
        properties.put("schema.3.data-type", "VARCHAR(200)");
        properties.put("schema.3.comment", "");
        properties.put("schema.primary-key.name", "primary_constraint");
        properties.put("schema.primary-key.columns", "id");
        properties.put("schema.index.0.name", "idx");
        properties.put("schema.index.0.columns", "id");
        properties.put("freshness-interval", "30");
        properties.put("freshness-unit", "SECOND");
        properties.put("logical-refresh-mode", "CONTINUOUS");
        properties.put("refresh-mode", "CONTINUOUS");
        properties.put("refresh-status", "INITIALIZING");
        properties.put("definition-query", DEFINITION_QUERY);

        // put refresh handler
        properties.put(
                "refresh-handler-bytes",
                encodeBytesToBase64(
                        ContinuousRefreshHandlerSerializer.INSTANCE.serialize(
                                CONTINUOUS_REFRESH_HANDLER)));
        return properties;
    }

    private static CatalogMaterializedTable catalogMaterializedTable() {
        final String comment = "This is an example materialized table.";
        final List<String> partitionKeys = Arrays.asList("region", "county");

        final String definitionQuery =
                String.format(
                        "SELECT id, region, county FROM %s.%s.T",
                        DEFAULT_CATALOG, DEFAULT_DATABASE);

        CatalogMaterializedTable.Builder builder = CatalogMaterializedTable.newBuilder();
        return builder.schema(MATERIALIZED_TABLE_SCHEMA)
                .comment(comment)
                .partitionKeys(partitionKeys)
                .options(Collections.emptyMap())
                .definitionQuery(definitionQuery)
                .freshness(IntervalFreshness.ofSecond("30"))
                .logicalRefreshMode(CatalogMaterializedTable.LogicalRefreshMode.AUTOMATIC)
                .refreshMode(CatalogMaterializedTable.RefreshMode.CONTINUOUS)
                .refreshStatus(CatalogMaterializedTable.RefreshStatus.INITIALIZING)
                .build();
    }

    private static CatalogView catalogView() {
        final String comment = "This is an example table.";

        final String originalQuery = "SELECT * FROM T";

        final String expandedQuery =
                String.format(
                        "SELECT id, region, county FROM %s.%s.T",
                        DEFAULT_CATALOG, DEFAULT_DATABASE);

        return CatalogView.of(
                VIEW_SCHEMA, comment, originalQuery, expandedQuery, Collections.emptyMap());
    }

    private static CatalogManager catalogManager() {
        final CatalogManager catalogManager = CatalogManagerMocks.createEmptyCatalogManager();

        final ExpressionResolverBuilder expressionResolverBuilder =
                ExpressionResolverMocks.forSqlExpression(
                        CatalogBaseTableResolutionTest::resolveSqlExpression);

        catalogManager.initSchemaResolver(true, expressionResolverBuilder, new ParserMock());

        return catalogManager;
    }

    private static <T extends CatalogBaseTable> T resolveCatalogBaseTable(
            Class<T> expectedClass, CatalogBaseTable table) {
        final CatalogManager catalogManager = catalogManager();
        catalogManager.createTable(table, IDENTIFIER, false);

        final Catalog catalog =
                catalogManager.getCatalog(DEFAULT_CATALOG).orElseThrow(IllegalStateException::new);

        assertThat(catalog)
                .as("GenericInMemoryCatalog expected for test")
                .isInstanceOf(GenericInMemoryCatalog.class);

        // retrieve the resolved catalog table
        final CatalogBaseTable storedTable;
        try {
            storedTable = catalog.getTable(IDENTIFIER.toObjectPath());
        } catch (TableNotExistException e) {
            throw new RuntimeException(e);
        }

        assertThat(expectedClass.isAssignableFrom(storedTable.getClass()))
                .as("In-memory implies that output table equals input table.")
                .isTrue();

        return expectedClass.cast(storedTable);
    }

    private static ResolvedExpression resolveSqlExpression(
            String sqlExpression, RowType inputRowType, @Nullable LogicalType outputType) {
        switch (sqlExpression) {
            case COMPUTED_SQL:
                return COMPUTED_COLUMN_RESOLVED;
            case WATERMARK_SQL:
                return WATERMARK_RESOLVED;
            default:
                throw new UnsupportedOperationException("Unknown SQL expression.");
        }
    }
}
