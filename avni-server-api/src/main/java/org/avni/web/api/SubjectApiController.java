package org.avni.web.api;

import org.avni.dao.*;
import org.avni.domain.*;
import org.avni.service.ConceptService;
import org.avni.service.LocationService;
import org.avni.util.S;
import org.avni.web.request.api.ApiSubjectRequest;
import org.avni.web.request.api.RequestUtils;
import org.avni.web.response.ResponsePage;
import org.avni.web.response.SubjectResponse;
import org.joda.time.DateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
public class SubjectApiController {
    private final ConceptService conceptService;
    private final IndividualRepository individualRepository;
    private final ConceptRepository conceptRepository;
    private final GroupSubjectRepository groupSubjectRepository;
    private final LocationService locationService;
    private final SubjectTypeRepository subjectTypeRepository;
    private final LocationRepository locationRepository;
    private final  GenderRepository genderRepository;

    public SubjectApiController(ConceptService conceptService, IndividualRepository individualRepository,
                                ConceptRepository conceptRepository, GroupSubjectRepository groupSubjectRepository,
                                LocationService locationService, SubjectTypeRepository subjectTypeRepository,
                                LocationRepository locationRepository, GenderRepository genderRepository) {
        this.conceptService = conceptService;
        this.individualRepository = individualRepository;
        this.conceptRepository = conceptRepository;
        this.groupSubjectRepository = groupSubjectRepository;
        this.locationService = locationService;
        this.subjectTypeRepository = subjectTypeRepository;
        this.locationRepository = locationRepository;
        this.genderRepository = genderRepository;
    }

    @RequestMapping(value = "/api/subjects", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponsePage getSubjects(@RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
                                    @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
                                    @RequestParam(value = "subjectType", required = false) String subjectType,
                                    @RequestParam(value = "concepts", required = false) String concepts,
                                    @RequestParam(value= "locationIds", required = false) List<String> locationUUIDs,
                                    Pageable pageable) {
        Page<Individual> subjects;
        boolean subjectTypeRequested = S.isEmpty(subjectType);
        List<Long> allLocationIds = locationService.getAllWithChildrenForUUIDs(locationUUIDs);
        Map<Concept, String> conceptsMap = conceptService.readConceptsFromJsonObject(concepts);
        subjects = subjectTypeRequested ?
                individualRepository.findByConcepts(CHSEntity.toDate(lastModifiedDateTime), CHSEntity.toDate(now), conceptsMap, allLocationIds, pageable) :
                individualRepository.findByConceptsAndSubjectType(CHSEntity.toDate(lastModifiedDateTime), CHSEntity.toDate(now), conceptsMap, subjectType, allLocationIds, pageable);
        List<GroupSubject> groupsOfAllMemberSubjects = groupSubjectRepository.findAllByMemberSubjectIn(subjects.getContent());
        ArrayList<SubjectResponse> subjectResponses = new ArrayList<>();
        subjects.forEach(subject -> {
            subjectResponses.add(SubjectResponse.fromSubject(subject, subjectTypeRequested, conceptRepository, conceptService, findGroupAffiliation(subject, groupsOfAllMemberSubjects)));
        });
        return new ResponsePage(subjectResponses, subjects.getNumberOfElements(), subjects.getTotalPages(), subjects.getSize());
    }

    @GetMapping(value = "/api/subject/{id}")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    public ResponseEntity<SubjectResponse> get(@PathVariable("id") String uuid) {
        Individual subject = individualRepository.findByUuid(uuid);
        if (subject == null)
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        List<GroupSubject> groupsOfAllMemberSubjects = groupSubjectRepository.findAllByMemberSubjectIn(Collections.singletonList(subject));
        return new ResponseEntity<>(SubjectResponse.fromSubject(subject, true, conceptRepository, conceptService, groupsOfAllMemberSubjects), HttpStatus.OK);
    }

    @PostMapping(value = "/api/subject")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional
    @ResponseBody
    public ResponseEntity post(@RequestBody ApiSubjectRequest request) {
        Individual subject = new Individual();
        subject.assignUUID();
        try {
            updateSubject(subject, request);
        } catch (ValidationException ve) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ve.getMessage());
        }
        return new ResponseEntity<>(SubjectResponse.fromSubject(subject, true, conceptRepository, conceptService), HttpStatus.OK);
    }

    @PutMapping(value = "/api/subject/{id}")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional
    @ResponseBody
    public ResponseEntity put(@PathVariable String id, @RequestBody ApiSubjectRequest request) {
        Individual subject = individualRepository.findByUuid(id);
        if (subject == null) {
            throw new IllegalArgumentException(String.format("Subject not found with id '%s'", id));
        }
        try {
            updateSubject(subject, request);
        } catch (ValidationException ve) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ve.getMessage());
        }
        return new ResponseEntity<>(SubjectResponse.fromSubject(subject, true, conceptRepository, conceptService), HttpStatus.OK);
    }

    private void updateSubject(Individual subject, ApiSubjectRequest request) throws ValidationException {
        SubjectType subjectType = subjectTypeRepository.findByName(request.getSubjectType());
        if (subjectType == null) {
            throw new IllegalArgumentException(String.format("Subject type not found with name '%s'", request.getSubjectType()));
        }
        Optional<AddressLevel> addressLevel = locationRepository.findByTitleLineageIgnoreCase(request.getAddress());
        if (!addressLevel.isPresent()) {
            throw new IllegalArgumentException(String.format("Address '%s' not found", request.getAddress()));
        }
        subject.setSubjectType(subjectType);
        subject.setFirstName(request.getFirstName());
        subject.setLastName(request.getLastName());
        subject.setRegistrationDate(request.getRegistrationDate());
        subject.setAddressLevel(addressLevel.get());
        if (subjectType.isPerson()) {
            subject.setDateOfBirth(request.getDateOfBirth());
            subject.setGender(genderRepository.findByName(request.getGender()));
        }
        subject.setObservations(RequestUtils.createObservations(request.getObservations(), conceptRepository));
        subject.setRegistrationLocation(request.getRegistrationLocation());
        subject.setVoided(request.isVoided());

        subject.validate();

        individualRepository.save(subject);
    }

    private List<GroupSubject> findGroupAffiliation(Individual subject, List<GroupSubject> groupSubjects) {
        return groupSubjects.stream().filter(groupSubject -> groupSubject.getMemberSubject().equals(subject)).collect(Collectors.toList());
    }
}
