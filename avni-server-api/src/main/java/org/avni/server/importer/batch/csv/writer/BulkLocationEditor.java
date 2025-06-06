package org.avni.server.importer.batch.csv.writer;

import jakarta.transaction.Transactional;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.ValidationException;
import org.avni.server.importer.batch.csv.creator.ObservationCreator;
import org.avni.server.importer.batch.csv.writer.header.LocationHeaderCreator;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.ImportLocationsConstants;
import org.avni.server.service.LocationService;
import org.avni.server.util.CollectionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.springframework.util.ObjectUtils.nullSafeEquals;

@Component
public class BulkLocationEditor extends BulkLocationModifier {
    private final LocationService locationService;

    @Autowired
    public BulkLocationEditor(LocationRepository locationRepository, ObservationCreator observationCreator, LocationService locationService, LocationHeaderCreator locationHeaderCreator) {
        super(locationRepository, observationCreator, locationHeaderCreator);
        this.locationService = locationService;
    }

    public void editLocation(Row row, List<String> allErrorMsgs) throws ValidationException {
        String existingLocationTitleLineage = row.get(ImportLocationsConstants.COLUMN_NAME_LOCATION_WITH_FULL_HIERARCHY);
        String newLocationParentTitleLineage = row.get(ImportLocationsConstants.COLUMN_NAME_PARENT_LOCATION_WITH_FULL_HIERARCHY);

        Optional<AddressLevel> existingLocationAddressLevel = locationRepository.findByTitleLineageIgnoreCase(existingLocationTitleLineage);
        if (!existingLocationAddressLevel.isPresent()) {
            allErrorMsgs.add(String.format("Provided Location does not exist in Avni. Please add it or check for spelling mistakes and ensure space between two locations '%s'", existingLocationTitleLineage));
            throw new RuntimeException(String.join(", ", allErrorMsgs));
        }

        AddressLevel newLocationParentAddressLevel = null;
        if (!StringUtils.isEmpty(newLocationParentTitleLineage)) {
            newLocationParentAddressLevel = locationRepository.findByTitleLineageIgnoreCase(newLocationParentTitleLineage).orElse(null);
            if (newLocationParentAddressLevel == null) {
                allErrorMsgs.add(String.format("Provided new location parent does not exist in Avni. Please add it or check for spelling mistakes and ensure space between two locations - '%s'", newLocationParentTitleLineage));
                throw new RuntimeException(String.join(", ", allErrorMsgs));
            }

            AddressLevelType allowedParentType = existingLocationAddressLevel.get().getType().getParent();
            if (!nullSafeEquals(allowedParentType, newLocationParentAddressLevel.getType())) {
                if (allowedParentType != null) {
                    allErrorMsgs.add(String.format("Only parent of location type \'%s\' is allowed for %s.", allowedParentType.getName(), existingLocationAddressLevel.get().getTitle()));
                } else {
                    allErrorMsgs.add(String.format("No parent is allowed for %s since it is a top level location.", existingLocationAddressLevel.get().getTitle()));
                }
                throw new RuntimeException(String.join(", ", allErrorMsgs));
            }
        }
        updateExistingLocation(existingLocationAddressLevel.get(), newLocationParentAddressLevel, row, allErrorMsgs);
    }

    public void validateEditModeHeaders(String[] headers, List<String> allErrorMsgs) {
        List<String> headerList = Arrays.asList(headers);
        if (!headerList.contains(ImportLocationsConstants.COLUMN_NAME_LOCATION_WITH_FULL_HIERARCHY)) {
            allErrorMsgs.add(String.format("'%s' is required", ImportLocationsConstants.COLUMN_NAME_LOCATION_WITH_FULL_HIERARCHY));
        }
        if (!(headerList.contains(ImportLocationsConstants.COLUMN_NAME_NEW_LOCATION_NAME)
                || headerList.contains(ImportLocationsConstants.COLUMN_NAME_GPS_COORDINATES)
                || headerList.contains(ImportLocationsConstants.COLUMN_NAME_PARENT_LOCATION_WITH_FULL_HIERARCHY))) {
            allErrorMsgs.add(String.format("At least one of '%s', '%s' or '%s' is required", ImportLocationsConstants.COLUMN_NAME_NEW_LOCATION_NAME, ImportLocationsConstants.COLUMN_NAME_GPS_COORDINATES, ImportLocationsConstants.COLUMN_NAME_PARENT_LOCATION_WITH_FULL_HIERARCHY));
        }
        if (!allErrorMsgs.isEmpty()) {
            throw new RuntimeException(String.join(", ", allErrorMsgs));
        }
    }

    private void updateExistingLocation(AddressLevel location, AddressLevel newParent, Row row, List<String> allErrorMsgs) throws ValidationException {
        String newTitle = row.get(ImportLocationsConstants.COLUMN_NAME_NEW_LOCATION_NAME);
        if (!StringUtils.isEmpty(newTitle)) location.setTitle(newTitle);
        if (newParent != null) {
            locationService.updateParent(location, newParent);
        }
        updateLocationProperties(row, allErrorMsgs, location);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void write(List<? extends Row> rows) throws ValidationException {
        List<String> allErrorMsgs = new ArrayList<>();
        validateEditModeHeaders(rows.get(0).getHeaders(), allErrorMsgs);
        for (Row row : rows) {
            if (skipRow(row, Arrays.asList(rows.get(0).getHeaders()))) {
                continue;
            }
            editLocation(row, allErrorMsgs);
        }
    }

    private boolean skipRow(Row row, List<String> editLocationHeaders) {
        String existingLocationTitleLineage = row.get(ImportLocationsConstants.COLUMN_NAME_LOCATION_WITH_FULL_HIERARCHY);
        return (existingLocationTitleLineage.equalsIgnoreCase(ImportLocationsConstants.LOCATION_WITH_FULL_HIERARCHY_DESCRIPTION)
                || existingLocationTitleLineage.equalsIgnoreCase(ImportLocationsConstants.LOCATION_WITH_FULL_HIERARCHY_EXAMPLE)
                || CollectionUtil.anyStartsWith(row.get(editLocationHeaders), ImportLocationsConstants.EXAMPLE));
    }
}
