package org.avni.server.dao.metabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import org.avni.server.domain.metabase.*;
import org.avni.server.util.ObjectMapperSingleton;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class QuestionRepository extends MetabaseConnector {
    public static final String IS_VOIDED = "is_voided";

    private final MetabaseDatabaseRepository databaseRepository;

    public QuestionRepository(RestTemplateBuilder restTemplateBuilder, MetabaseDatabaseRepository databaseRepository) {
        super(restTemplateBuilder);
        this.databaseRepository = databaseRepository;
    }

    public void createCustomQuestionOfVisualization(Database database, QuestionName question, VisualizationType visualizationType, List<FilterCondition> additionalFilterConditions) {
        QuestionConfig config = new QuestionConfig()
                .withAggregation(AggregationType.COUNT)
                .withBreakout(question.getBreakoutField())
                .withFilters(getFilterConditions(additionalFilterConditions, database, question).toArray(FilterCondition[]::new))
                .withVisualization(visualizationType);
        MetabaseQuery query = createAdvancedQuery(question.getViewName(), config, database);
        postQuestion(
                question.getQuestionName(),
                query,
                config,
                databaseRepository.getCollectionForDatabase(database).getIdAsInt()
        );
    }

    private List<FilterCondition> getFilterConditions(List<FilterCondition> additionalFilterConditions, Database database, QuestionName question) {
        return ImmutableList.<FilterCondition>builder()
                .addAll(List.of(new FilterCondition(ConditionType.EQUAL, databaseRepository.getFieldDetailsByName(database, new TableDetails(question.getViewName(), database.getName()), new FieldDetails(IS_VOIDED)).getId(), FieldType.BOOLEAN.getTypeName(), false)))
                .addAll(additionalFilterConditions).build();
    }

    private void postQuestion(String questionName, MetabaseQuery query, QuestionConfig config, int collectionId) {
        MetabaseRequestBody requestBody = new MetabaseRequestBody(
                questionName,
                query,
                config.getVisualizationType(),
                null,
                ObjectMapperSingleton.getObjectMapper().createObjectNode(),
                collectionId
        );
        databaseRepository.postForObject(metabaseApiUrl + "/card", requestBody.toJson(), ObjectNode.class);
    }

    public void createQuestionForTable(Database database, TableDetails tableDetails, TableDetails addressTableDetails, FieldDetails originField, FieldDetails destinationField, List<FieldDetails> fieldsToShow) {
        FieldDetails joinField1 = databaseRepository.getFieldDetailsByName(database, addressTableDetails, originField);
        FieldDetails joinField2 = databaseRepository.getFieldDetailsByName(database, tableDetails, destinationField);

        ArrayNode joinsArray = ObjectMapperSingleton.getObjectMapper().createArrayNode();
        MetabaseQuery query = new MetabaseQueryBuilder(database, joinsArray)
                    .forTable(tableDetails)
                    .joinWith(addressTableDetails, joinField1, joinField2, fieldsToShow)
                    .build();

        MetabaseRequestBody requestBody = new MetabaseRequestBody(
                tableDetails.getDisplayName(),
                query,
                VisualizationType.TABLE,
                null,
                ObjectMapperSingleton.getObjectMapper().createObjectNode(),
                databaseRepository.getCollectionForDatabase(database).getIdAsInt()
        );

        databaseRepository.postForObject(metabaseApiUrl + "/card", requestBody.toJson(), JsonNode.class);
    }

    public void createQuestionForASingleTable(Database database, TableDetails tableDetails) {
        MetabaseQuery query = new MetabaseQueryBuilder(database, ObjectMapperSingleton.getObjectMapper().createArrayNode())
                .forTable(tableDetails)
                .build();

        MetabaseRequestBody requestBody = new MetabaseRequestBody(
                tableDetails.getDisplayName(),
                query,
                VisualizationType.TABLE,
                null,
                ObjectMapperSingleton.getObjectMapper().createObjectNode(),
                databaseRepository.getCollectionForDatabase(database).getIdAsInt()
        );

        databaseRepository.postForObject(metabaseApiUrl + "/card", requestBody.toJson(), JsonNode.class);
    }

    private MetabaseQuery createAdvancedQuery(String primaryTableName, QuestionConfig config, Database database) {
        TableDetails primaryTable = databaseRepository.findTableDetailsByName(database, new TableDetails(primaryTableName, database.getName()));
        FieldDetails breakoutField = databaseRepository.getFieldDetailsByName(database, primaryTable, new FieldDetails(config.getBreakoutField()));

        return new MetabaseQueryBuilder(database, ObjectMapperSingleton.getObjectMapper().createArrayNode())
                .forTable(primaryTable)
                .addAggregation(config.getAggregationType())
                .addBreakout(breakoutField.getId())
                .addFilter(config.getFilters())
                .build();
    }

}
