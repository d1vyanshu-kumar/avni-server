package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormMapping;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class UploadVisitDetailsStrategy implements EncounterHeaderStrategy {

    @Override
    public List<HeaderField> generateHeaders(FormMapping formMapping) {
        List<HeaderField> fields = new ArrayList<>();

        fields.add(new HeaderField(EncounterHeadersCreator.ID, "Can be used to later identify the entry", false, null, null, null));
        fields.add(new HeaderField(EncounterHeadersCreator.ENCOUNTER_TYPE_HEADER, formMapping.getEncounterType().getName(), true, null, null, null, false));
        fields.add(new HeaderField(EncounterHeadersCreator.PROGRAM_ENROLMENT_ID, "Mention identifier from previous system or UUID of the program enrolment. UUID can be identified from address bar in Data Entry App or Longitudinal export file", true, null, null, null));
        fields.add(new HeaderField(EncounterHeadersCreator.VISIT_DATE, "", false, null, "Format: DD-MM-YYYY", null));
        fields.add(new HeaderField(EncounterHeadersCreator.ENCOUNTER_LOCATION, "", false, null, "Format: (21.5135243,85.6731848)", null));
        fields.addAll(AbstractHeaders.generateConceptFields(formMapping));

        return fields;
    }
}