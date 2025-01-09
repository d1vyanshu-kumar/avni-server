package org.avni.server.domain.metadata;

import java.util.ArrayList;
import java.util.List;

public class ObjectCollectionChangeReport {
    private final List<ObjectChangeReport> objectChangeReports = new ArrayList<>();

    public void addObjectReport(ObjectChangeReport entityChangeReport) {
        objectChangeReports.add(entityChangeReport);
    }

    public boolean hasNoChange() {
        return objectChangeReports.stream().allMatch(objectChangeReport -> ChangeType.NoChange.equals(objectChangeReport.getChangeType()));
    }

    public boolean hasChangeIn(String uuid) {
        return objectChangeReports.stream().anyMatch(objectChangeReport -> uuid.equals(objectChangeReport.getUuid()));
    }
}
