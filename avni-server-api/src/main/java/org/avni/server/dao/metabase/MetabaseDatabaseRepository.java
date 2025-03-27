package org.avni.server.dao.metabase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.metabase.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.util.ObjectMapperSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Repository
public class MetabaseDatabaseRepository extends MetabaseConnector {
    private final CollectionRepository collectionRepository;
    private static final Logger logger = LoggerFactory.getLogger(MetabaseDatabaseRepository.class);

    private static ThreadLocal<Map<String, List<TableDetails>>> tablesThreadLocalContext = new ThreadLocal<>();
    private static ThreadLocal<Map<Integer, List<FieldDetails>>> fieldsThreadLocalContext = new ThreadLocal<>();

    public MetabaseDatabaseRepository(RestTemplateBuilder restTemplateBuilder, CollectionRepository collectionRepository) {
        super(restTemplateBuilder);
        this.collectionRepository = collectionRepository;
    }

    public Database save(Database database) {
        String url = metabaseApiUrl + "/database";
        Database response = postForObject(url, database, Database.class);
        database.setId(response.getId());
        return database;
    }

    public Database getDatabase(String organisationName, String organisationDbUser) {
        String url = metabaseApiUrl + "/database";
        try {
            String jsonResponse = getForObject(url, String.class);
            JsonNode rootNode = ObjectMapperSingleton.getObjectMapper().readTree(jsonResponse);
            JsonNode dataArray = rootNode.path("data");

            for (JsonNode dbNode : dataArray) {
                Database db = ObjectMapperSingleton.getObjectMapper().treeToValue(dbNode, Database.class);
                if (db.getName().equals(organisationName) && db.getDetails().getUser().equals(organisationDbUser)) {
                    return db;
                }
            }
            return null;
        } catch (Exception e) {
            logger.error("Get database failed for: {}", url);
            throw new RuntimeException(e);
        }
    }

    public Database getDatabase(Organisation organisation) {
        return getDatabase(organisation.getName(), organisation.getDbUser());
    }

    protected CollectionInfoResponse getCollectionForDatabase(Database database) {
        CollectionInfoResponse collectionByName = collectionRepository.getCollection(database.getName());
        if (Objects.isNull(collectionByName)) {
            throw new RuntimeException(String.format("Failed to fetch collection for database %s", database.getName()));
        }
        return collectionByName;
    }

    private MetabaseDatabaseInfo getDatabaseDetails(Database database) {
        try {
            String url = metabaseApiUrl + "/database/" + database.getId() + "?include=tables";
            String jsonResponse = getForObject(url, String.class);
            return ObjectMapperSingleton.getObjectMapper().readValue(jsonResponse, MetabaseDatabaseInfo.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> getSchemas(Database database) {
        String url = String.format("%s/database/%d/schemas", metabaseApiUrl, database.getId());
        return (List<String>) this.getObject(url, new TypeReference<List<String>>() {});
    }

    public List<TableDetails> getTables(Database database, String schema) {
        if (tablesThreadLocalContext.get() == null) {
            tablesThreadLocalContext.set(new HashMap<>());
        }
        if (!tablesThreadLocalContext.get().containsKey(schema)) {
            String url = String.format("%s/database/%d/schema/%s?include_hidden=true", metabaseApiUrl, database.getId(), schema);
            List<TableDetails> tableDetails = (List<TableDetails>) this.getObject(url, new TypeReference<List<TableDetails>>() {});
            Map<String, List<TableDetails>> tables = tablesThreadLocalContext.get();
            tables.put(schema, tableDetails);
        }
        return tablesThreadLocalContext.get().get(schema);
    }

    private TableDetails getTable(Database database, String schema, String tableName) {
        return getTables(database, schema).stream()
                .filter(table -> table.getName().equalsIgnoreCase(tableName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Table with name " + tableName + " not found."));
    }

    public TableDetails findTableDetailsByName(Database database, TableDetails targetTable) {
        MetabaseDatabaseInfo databaseInfo = getDatabaseDetails(database);
        return databaseInfo.getTables().stream()
                .filter(tableDetail -> tableDetail.getName().equalsIgnoreCase(targetTable.getName())
                        && (tableDetail.getSchema().equalsIgnoreCase(database.getName()) == targetTable.getSchema().equalsIgnoreCase(database.getName())))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Table with name " + targetTable.getName() + " not found."));
    }

    public DatabaseSyncStatus getInitialSyncStatus(Database database) {
        String url = metabaseApiUrl + "/database/" + database.getId();
        try {
            String jsonResponse = getForObject(url, String.class);
            return ObjectMapperSingleton.getObjectMapper().readValue(jsonResponse, DatabaseSyncStatus.class);
        } catch (Exception e) {
            logger.error("Get initial sync status failed for: {}", url);
            throw new RuntimeException("Failed to parse sync status", e);
        }
    }

    private DatasetResponse getDataset(DatasetRequestBody requestBody) {
        try {
            String url = metabaseApiUrl + "/dataset";
            String jsonRequestBody = requestBody.toJson().toString();
            String jsonResponse = postForObject(url, jsonRequestBody, String.class);
            return ObjectMapperSingleton.getObjectMapper().readValue(jsonResponse, DatasetResponse.class);
        } catch (Exception e) {
            logger.error("Get dataset failed for: {}", requestBody);
            throw new RuntimeException(e);
        }
    }

    public DatasetResponse findAll(TableDetails table, Database database) {
        DatasetRequestBody requestBody = createRequestBodyForDataset(database, table);
        return getDataset(requestBody);
    }

    private DatasetRequestBody createRequestBodyForDataset(Database database, TableDetails table) {
        return new DatasetRequestBody(database, table);
    }

    public void delete(Database database) {
        String url = metabaseApiUrl + "/database/" + database.getId();
        deleteForObject(url, Void.class);
    }

    public void reSyncSchema(Database database) {
        String url = metabaseApiUrl + "/database/" + database.getId() + "/sync_schema";
        this.postForObject(url, "", String.class);
    }

    public void rescanFieldValues(Database database) {
        String url = metabaseApiUrl + "/database/" + database.getId() + "/rescan_values";
        this.postForObject(url, "", String.class);
    }

    public List<FieldDetails> getFields(TableDetails table) {
        if (fieldsThreadLocalContext.get() == null) {
            fieldsThreadLocalContext.set(new HashMap<>());
        }
        if (!fieldsThreadLocalContext.get().containsKey(table.getId())) {
            String url = String.format("%s/table/%d/query_metadata?include_sensitive_fields=true", metabaseApiUrl, table.getId());
            TableFieldsResponse tableFieldsResponse = (TableFieldsResponse) this.getObject(url, new TypeReference<TableFieldsResponse>() {});
            Map<Integer, List<FieldDetails>> fields = MetabaseDatabaseRepository.fieldsThreadLocalContext.get();
            fields.put(table.getId(), tableFieldsResponse.getFields());
        }
        return fieldsThreadLocalContext.get().get(table.getId());
    }

    public FieldDetails getOrgSchemaField(Database database, String tableName, String fieldName) {
        return this.getField(database, UserContextHolder.getOrganisation().getSchemaName(), tableName, fieldName);
    }

    public FieldDetails getOrgSchemaField(Database database, String tableName, FieldDetails field) {
        return this.getField(database, UserContextHolder.getOrganisation().getSchemaName(), tableName, field.getName());
    }

    public FieldDetails getField(Database database, String schemaName, String tableName, String fieldName) {
        List<TableDetails> tables = this.getTables(database, schemaName);
        TableDetails table = tables.stream().filter(t -> t.getName().equalsIgnoreCase(tableName)).findFirst().orElseThrow(() -> new RuntimeException("Table not found: " + tableName));
        List<FieldDetails> fields = this.getFields(table);
        return fields.stream().filter(f -> f.getName().equals(fieldName)).findFirst().orElseThrow(() -> new RuntimeException("Field: " + fieldName + "not found in table: " + tableName));
    }

    public static void clearThreadLocalContext() {
        tablesThreadLocalContext.remove();
        fieldsThreadLocalContext.remove();
    }
}
