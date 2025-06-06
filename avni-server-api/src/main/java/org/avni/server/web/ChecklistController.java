package org.avni.server.web;

import jakarta.transaction.Transactional;
import org.avni.server.dao.ChecklistDetailRepository;
import org.avni.server.dao.ChecklistRepository;
import org.avni.server.dao.ProgramEnrolmentRepository;
import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.Checklist;
import org.avni.server.domain.ChecklistDetail;
import org.avni.server.service.ScopeBasedSyncService;
import org.avni.server.service.UserService;
import org.avni.server.web.request.ChecklistRequest;
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
public class ChecklistController extends AbstractController<Checklist> implements RestControllerResourceProcessor<Checklist> {
    private final ChecklistDetailRepository checklistDetailRepository;
    private final ChecklistRepository checklistRepository;
    private final ProgramEnrolmentRepository programEnrolmentRepository;
    private final UserService userService;
    private final ScopeBasedSyncService<Checklist> scopeBasedSyncService;

    @Autowired
    public ChecklistController(ChecklistRepository checklistRepository,
                               ProgramEnrolmentRepository programEnrolmentRepository,
                               ChecklistDetailRepository checklistDetailRepository, UserService userService, ScopeBasedSyncService<Checklist> scopeBasedSyncService) {
        this.checklistDetailRepository = checklistDetailRepository;
        this.checklistRepository = checklistRepository;
        this.programEnrolmentRepository = programEnrolmentRepository;
        this.userService = userService;
        this.scopeBasedSyncService = scopeBasedSyncService;
    }

    @Transactional
    @RequestMapping(value = "/txNewChecklistEntitys", method = RequestMethod.POST)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public void save(@RequestBody ChecklistRequest checklistRequest) {
        Checklist checklist = newOrExistingEntity(checklistRepository, checklistRequest, new Checklist());
        checklist.setChecklistDetail(this.checklistDetailRepository.findByUuid(checklistRequest.getChecklistDetailUUID()));
        checklist.setProgramEnrolment(programEnrolmentRepository.findByUuid(checklistRequest.getProgramEnrolmentUUID()));
        checklist.setBaseDate(checklistRequest.getBaseDate());
        checklistRepository.save(checklist);
    }

    @Transactional
    @RequestMapping(value = "/checklists", method = RequestMethod.POST)
    public void saveOld(@RequestBody Object object) {

    }

    @RequestMapping(value = "/txNewChecklistEntity/search/byIndividualsOfCatchmentAndLastModified", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public CollectionModel<EntityModel<Checklist>> getByIndividualsOfCatchmentAndLastModified(
            @RequestParam("catchmentId") long catchmentId,
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            Pageable pageable) {
        return wrap(checklistRepository.findByProgramEnrolmentIndividualAddressLevelVirtualCatchmentsIdAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(catchmentId, CHSEntity.toDate(lastModifiedDateTime), CHSEntity.toDate(now), pageable));
    }


    @RequestMapping(value = "/txNewChecklistEntity/v2", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public SlicedResources<EntityModel<Checklist>> getChecklistsByOperatingIndividualScopeAsSlice(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "checklistDetailUuid", required = false) String checklistDetailUuid,
            Pageable pageable) {
        if (checklistDetailUuid.isEmpty()) return wrap(new SliceImpl<>(Collections.emptyList()));
        ChecklistDetail checklistDetail = checklistDetailRepository.findByUuid(checklistDetailUuid);
        if (checklistDetail == null) return wrap(new SliceImpl<>(Collections.emptyList()));
        Checklist checklist = checklistRepository.findFirstByChecklistDetail(checklistDetail);
        if(checklist == null || checklist.getProgramEnrolment() == null) return wrap(new SliceImpl<>(Collections.emptyList()));
        return wrap(scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocationAsSlice(checklistRepository, userService.getCurrentUser(), lastModifiedDateTime, now, checklistDetail.getId(), pageable, checklist.getProgramEnrolment().getIndividual().getSubjectType(), SyncEntityName.Checklist));
    }

    @RequestMapping(value = "/txNewChecklistEntity", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public CollectionModel<EntityModel<Checklist>> getChecklistsByOperatingIndividualScope(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "checklistDetailUuid", required = false) String checklistDetailUuid,
            Pageable pageable) {
        if (checklistDetailUuid.isEmpty()) return wrap(new PageImpl<>(Collections.emptyList()));
        ChecklistDetail checklistDetail = checklistDetailRepository.findByUuid(checklistDetailUuid);
        if (checklistDetail == null) return wrap(new PageImpl<>(Collections.emptyList()));
        Checklist checklist = checklistRepository.findFirstByChecklistDetail(checklistDetail);
        if(checklist == null || checklist.getProgramEnrolment() == null) return wrap(new PageImpl<>(Collections.emptyList()));
        return wrap(scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocation(checklistRepository, userService.getCurrentUser(), lastModifiedDateTime, now, checklistDetail.getId(), pageable, checklist.getProgramEnrolment().getIndividual().getSubjectType(), SyncEntityName.Checklist));
    }

    @Override
    public EntityModel<Checklist> process(EntityModel<Checklist> resource) {
        Checklist checklist = resource.getContent();
        resource.removeLinks();
        resource.add(Link.of(checklist.getProgramEnrolment().getUuid(), "programEnrolmentUUID"));
        if (checklist.getChecklistDetail() != null) {
            resource.add(Link.of(checklist.getChecklistDetail().getUuid(), "checklistDetailUUID"));
        }
        addAuditFields(checklist, resource);
        return resource;
    }

}
