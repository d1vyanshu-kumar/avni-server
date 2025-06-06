package org.avni.server.web;

import jakarta.transaction.Transactional;
import org.avni.server.dao.IdentifierAssignmentRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.ProgramEnrolmentRepository;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.IdentifierAssignment;
import org.avni.server.domain.User;
import org.avni.server.service.IdentifierAssignmentService;
import org.avni.server.service.UserService;
import org.avni.server.web.request.IdentifierAssignmentRequest;
import org.avni.server.web.response.slice.SlicedResources;
import org.joda.time.DateTime;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static org.avni.server.web.resourceProcessors.ResourceProcessor.addAuditFields;

@RestController
@Transactional
public class IdentifierAssignmentController extends AbstractController<IdentifierAssignment> implements RestControllerResourceProcessor<IdentifierAssignment> {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(IndividualController.class);
    private final IdentifierAssignmentRepository identifierAssignmentRepository;
    private final UserService userService;
    private final IdentifierAssignmentService identifierAssignmentService;
    private final IndividualRepository individualRepository;
    private final ProgramEnrolmentRepository programEnrolmentRepository;

    @Autowired
    public IdentifierAssignmentController(IdentifierAssignmentRepository identifierAssignmentRepository, UserService userService, IdentifierAssignmentService identifierAssignmentService, IndividualRepository individualRepository, ProgramEnrolmentRepository programEnrolmentRepository) {
        this.identifierAssignmentRepository = identifierAssignmentRepository;
        this.userService = userService;
        this.identifierAssignmentService = identifierAssignmentService;
        this.individualRepository = individualRepository;
        this.programEnrolmentRepository = programEnrolmentRepository;
    }

    /**
     * This method generates identifiers on the fly if there are missing identifiers.
     * Due to this, we do not pass the <code>now</code> parameter that is generated in
     * <code>TransactionalResourceInterceptor</code>.
     *
     * This approach is fine because we do not expect IdentifierAssignments to be shared across users.
     *
     * @param lastModifiedDateTime
     * @param pageable
     * @return
     */
    @RequestMapping(value = "/identifierAssignment", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional
    public CollectionModel<EntityModel<IdentifierAssignment>> get(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam(value = "deviceId", required = false) String deviceId,
            Pageable pageable) {
        User currentUser = userService.getCurrentUser();
        identifierAssignmentService.generateIdentifiersIfNecessary(currentUser, deviceId);

        return wrap(identifierAssignmentRepository.findByAssignedToAndLastModifiedDateTimeGreaterThanAndIsVoidedFalseAndIndividualIsNullAndProgramEnrolmentIsNullAndDeviceIdEqualsOrderByAssignmentOrderAsc(currentUser, CHSEntity.toDate(lastModifiedDateTime), deviceId, pageable));
    }

    @RequestMapping(value = "/identifierAssignment/v2", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional
    public SlicedResources<EntityModel<IdentifierAssignment>> getAsSlice(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam(value = "deviceId", required = false) String deviceId,
            Pageable pageable) {
        User currentUser = userService.getCurrentUser();
        identifierAssignmentService.generateIdentifiersIfNecessary(currentUser, deviceId);

        return wrap(identifierAssignmentRepository.findSliceByAssignedToAndLastModifiedDateTimeGreaterThanAndIsVoidedFalseAndIndividualIsNullAndProgramEnrolmentIsNullAndDeviceIdEqualsOrderByAssignmentOrderAsc(currentUser, CHSEntity.toDate(lastModifiedDateTime), deviceId, pageable));
    }

    @RequestMapping(value = "/identifierAssignments", method = RequestMethod.POST)
    @Transactional
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public void save(@RequestBody IdentifierAssignmentRequest identifierAssignmentRequest) {
        logger.info(String.format("Saving identifierAssignment with UUID %s", identifierAssignmentRequest.getUuid()));

        IdentifierAssignment identifierAssignment = createIdentifierAssignment(identifierAssignmentRequest);

        identifierAssignmentRepository.save(identifierAssignment);
        logger.info(String.format("Saved identifierAssignment with UUID %s", identifierAssignmentRequest.getUuid()));
    }

    private IdentifierAssignment createIdentifierAssignment(IdentifierAssignmentRequest identifierAssignmentRequest) {
        IdentifierAssignment identifierAssignment = identifierAssignmentRepository.findByUuid(identifierAssignmentRequest.getUuid());
        if (identifierAssignmentRequest.getIndividualUUID() != null) {
            identifierAssignment.setIndividual(individualRepository.findByUuid(identifierAssignmentRequest.getIndividualUUID()));
        }

        if (identifierAssignmentRequest.getProgramEnrolmentUUID() != null) {
            identifierAssignment.setProgramEnrolment(programEnrolmentRepository.findByUuid(identifierAssignmentRequest.getProgramEnrolmentUUID()));
        }

        identifierAssignment.setUsed(identifierAssignmentRequest.isUsed());
        return identifierAssignment;
    }

    @Override
    public EntityModel<IdentifierAssignment> process(EntityModel<IdentifierAssignment> resource) {
        IdentifierAssignment identifierAssignment = resource.getContent();
        resource.removeLinks();
        if (identifierAssignment.getProgramEnrolment() != null) {
            resource.add(Link.of(identifierAssignment.getProgramEnrolment().getUuid(), "programEnrolmentUUID"));
        }
        if (identifierAssignment.getIndividual() != null) {
            resource.add(Link.of(identifierAssignment.getIndividual().getUuid(), "individualUUID"));
        }
        resource.add(Link.of(identifierAssignment.getIdentifierSource().getUuid(), "identifierSourceUUID"));
        addAuditFields(identifierAssignment, resource);
        return resource;
    }
}
