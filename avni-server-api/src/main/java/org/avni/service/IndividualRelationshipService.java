package org.avni.service;

import org.joda.time.DateTime;
import org.avni.dao.OperatingIndividualScopeAwareRepository;
import org.avni.dao.SubjectTypeRepository;
import org.avni.dao.individualRelationship.IndividualRelationshipRepository;
import org.avni.domain.SubjectType;
import org.avni.domain.User;
import org.avni.framework.security.UserContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class IndividualRelationshipService implements ScopeAwareService {

    private final IndividualRelationshipRepository individualRelationshipRepository;
    private final SubjectTypeRepository subjectTypeRepository;

    @Autowired
    public IndividualRelationshipService(IndividualRelationshipRepository individualRelationshipRepository, SubjectTypeRepository subjectTypeRepository) {
        this.individualRelationshipRepository = individualRelationshipRepository;
        this.subjectTypeRepository = subjectTypeRepository;
    }

    @Override
    public boolean isScopeEntityChanged(DateTime lastModifiedDateTime, String subjectTypeUUID) {
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUUID);
        User user = UserContextHolder.getUserContext().getUser();
        return subjectType != null && isChanged(user, lastModifiedDateTime, subjectType.getId());
    }

    @Override
    public OperatingIndividualScopeAwareRepository repository() {
        return individualRelationshipRepository;
    }
}
