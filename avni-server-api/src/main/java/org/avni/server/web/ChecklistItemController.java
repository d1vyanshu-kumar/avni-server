package org.avni.server.web;

import jakarta.transaction.Transactional;
import org.avni.server.dao.ChecklistDetailRepository;
import org.avni.server.dao.ChecklistItemDetailRepository;
import org.avni.server.dao.ChecklistItemRepository;
import org.avni.server.dao.ChecklistRepository;
import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.Checklist;
import org.avni.server.domain.ChecklistDetail;
import org.avni.server.domain.ChecklistItem;
import org.avni.server.service.ObservationService;
import org.avni.server.service.ScopeBasedSyncService;
import org.avni.server.service.UserService;
import org.avni.server.web.request.application.ChecklistItemRequest;
import org.avni.server.web.response.slice.SlicedResources;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

import static org.avni.server.web.resourceProcessors.ResourceProcessor.addAuditFields;

@RestController
public class ChecklistItemController extends AbstractController<ChecklistItem> implements RestControllerResourceProcessor<ChecklistItem> {
    private final ObservationService observationService;
    private final ChecklistItemDetailRepository checklistItemDetailRepository;
    private final ChecklistRepository checklistRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final ChecklistDetailRepository checklistDetailRepository;
    private final UserService userService;
    private ScopeBasedSyncService<ChecklistItem> scopeBasedSyncService;

    @Autowired
    public ChecklistItemController(ChecklistRepository checklistRepository, ChecklistItemRepository checklistItemRepository, ObservationService observationService, ChecklistItemDetailRepository checklistItemDetailRepository, ChecklistDetailRepository checklistDetailRepository, UserService userService, ScopeBasedSyncService<ChecklistItem> scopeBasedSyncService) {
        this.checklistRepository = checklistRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.observationService = observationService;
        this.checklistItemDetailRepository = checklistItemDetailRepository;
        this.checklistDetailRepository = checklistDetailRepository;
        this.userService = userService;
        this.scopeBasedSyncService = scopeBasedSyncService;
    }

    @RequestMapping(value = "/checklistItems", method = RequestMethod.POST)
    public void saveOld(@RequestBody Object object) {
    }

    @Transactional
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @RequestMapping(value = "/txNewChecklistItemEntitys", method = RequestMethod.POST)
    public void save(@RequestBody ChecklistItemRequest checklistItemRequest) {
        ChecklistItem checklistItem = newOrExistingEntity(checklistItemRepository, checklistItemRequest, new ChecklistItem());

        checklistItem.setCompletionDate(checklistItemRequest.getCompletionDate());

        checklistItem.setObservations(this.observationService.createObservations(checklistItemRequest.getObservations()));
        if (checklistItem.isNew()) {
            Checklist checklist = checklistRepository.findByUuid(checklistItemRequest.getChecklistUUID());
            checklistItem.setChecklist(checklist);
            checklistItem.setChecklistItemDetail(checklistItemDetailRepository.findByUuid(checklistItemRequest.getChecklistItemDetailUUID()));
        }
        checklistItemRepository.save(checklistItem);
    }

    @RequestMapping(value = "/txNewChecklistItemEntity/search/byIndividualsOfCatchmentAndLastModified", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public CollectionModel<EntityModel<ChecklistItem>> getByIndividualsOfCatchmentAndLastModified(
            @RequestParam("catchmentId") long catchmentId,
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            Pageable pageable) {
        return wrap(checklistItemRepository.findByChecklistProgramEnrolmentIndividualAddressLevelVirtualCatchmentsIdAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(catchmentId, CHSEntity.toDate(lastModifiedDateTime), CHSEntity.toDate(now), pageable));
    }


    @RequestMapping(value = "/txNewChecklistItemEntity/v2", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public SlicedResources<EntityModel<ChecklistItem>> getChecklistItemsByOperatingIndividualScopeAsSlice(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "checklistDetailUuid", required = false) String checklistDetailUuid,
            Pageable pageable) {
        if (checklistDetailUuid.isEmpty()) return wrap(new SliceImpl<>(Collections.emptyList()));
        ChecklistDetail checklistDetail = checklistDetailRepository.findByUuid(checklistDetailUuid);
        if (checklistDetail == null) return wrap(new SliceImpl<>(Collections.emptyList()));
        Checklist checklist = checklistRepository.findFirstByChecklistDetail(checklistDetail);
        if(checklist == null || checklist.getProgramEnrolment() == null) return wrap(new SliceImpl<>(Collections.emptyList()));
        return wrap(scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocationAsSlice(checklistItemRepository, userService.getCurrentUser(), lastModifiedDateTime, now, checklistDetail.getId(), pageable, checklist.getProgramEnrolment().getIndividual().getSubjectType(), SyncEntityName.ChecklistItem));
    }

    @RequestMapping(value = "/txNewChecklistItemEntity", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public CollectionModel<EntityModel<ChecklistItem>> getChecklistItemsByOperatingIndividualScope(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "checklistDetailUuid", required = false) String checklistDetailUuid,
            Pageable pageable) {
        if (checklistDetailUuid.isEmpty()) return wrap(new PageImpl<>(Collections.emptyList()));
        ChecklistDetail checklistDetail = checklistDetailRepository.findByUuid(checklistDetailUuid);
        if (checklistDetail == null) return wrap(new PageImpl<>(Collections.emptyList()));
        Checklist checklist = checklistRepository.findFirstByChecklistDetail(checklistDetail);
        if(checklist == null || checklist.getProgramEnrolment() == null) return wrap(new PageImpl<>(Collections.emptyList()));
        return wrap(scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocation(checklistItemRepository, userService.getCurrentUser(), lastModifiedDateTime, now, checklistDetail.getId(), pageable, checklist.getProgramEnrolment().getIndividual().getSubjectType(), SyncEntityName.ChecklistItem));
    }

    @Override
    public EntityModel<ChecklistItem> process(EntityModel<ChecklistItem> resource) {
        ChecklistItem checklistItem = resource.getContent();
        resource.removeLinks();
        resource.add(Link.of(checklistItem.getChecklist().getUuid(), "checklistUUID"));
        if (checklistItem.getChecklistItemDetail() != null) {
            resource.add(Link.of(checklistItem.getChecklistItemDetail().getUuid(), "checklistItemDetailUUID"));
        }
        addAuditFields(checklistItem, resource);
        return resource;
    }

}
