package org.avni.service;

import org.joda.time.DateTime;
import org.avni.dao.UserGroupRepository;
import org.avni.domain.User;
import org.avni.framework.security.UserContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserGroupService implements NonScopeAwareService {

    private final UserGroupRepository userGroupRepository;

    @Autowired
    public UserGroupService(UserGroupRepository userGroupRepository) {
        this.userGroupRepository = userGroupRepository;
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        User user = UserContextHolder.getUserContext().getUser();
        return userGroupRepository.existsByUserIdAndAuditLastModifiedDateTimeGreaterThan(user.getId(), lastModifiedDateTime);
    }

    public boolean hasAllPrivilegesToUser() {
        User user = UserContextHolder.getUserContext().getUser();
        return userGroupRepository.findByUserAndGroupHasAllPrivilegesTrueAndIsVoidedFalse(user).size() > 0;
    }
}

