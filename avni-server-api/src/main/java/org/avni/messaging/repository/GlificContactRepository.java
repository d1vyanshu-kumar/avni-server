package org.avni.messaging.repository;

import org.avni.messaging.contract.glific.*;
import org.avni.messaging.external.GlificRestClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Lazy //for better startup performance
public class GlificContactRepository extends AbstractGlificRepository {
    private final String OPTIN_CONTACT_JSON;
    private final GlificRestClient glificRestClient;
    private final String GET_CONTACT_JSON;
    private final String GET_CONTACT_GROUP_JSON;
    private final String GET_CONTACT_GROUP_COUNT_JSON;
    private final String GET_CONTACT_GROUP_CONTACTS_JSON;
    private final String GET_CONTACT_GROUP_CONTACT_COUNT_JSON;

    public GlificContactRepository(GlificRestClient glificRestClient) {
        this.glificRestClient = glificRestClient;
        GET_CONTACT_JSON = getJson("getContact");
        OPTIN_CONTACT_JSON = getJson("optinContact");
        GET_CONTACT_GROUP_JSON = getJson("getContactGroups");
        GET_CONTACT_GROUP_COUNT_JSON = getJson("getContactGroupCount");
        GET_CONTACT_GROUP_CONTACTS_JSON = getJson("getContactGroupContacts");
        GET_CONTACT_GROUP_CONTACT_COUNT_JSON = getJson("getContactGroupContactCount");
    }

    public String getOrCreateContact(String phoneNumber, String fullName) {
        GlificGetContactsResponse glificContacts = getContact(phoneNumber);
        return glificContacts.getContacts().isEmpty() ?
                createContact(phoneNumber, fullName) :
                glificContacts.getContacts().get(0).getId();
    }

    private String createContact(String phoneNumber, String fullName) {
        final int NO_OF_DIGITS_IN_INDIAN_MOBILE_NO = 10;
        String phoneNoWithCountryCode = "+91" + phoneNumber.substring(phoneNumber.length() - NO_OF_DIGITS_IN_INDIAN_MOBILE_NO);
        String message = OPTIN_CONTACT_JSON.replace("${phoneNumber}", phoneNoWithCountryCode)
                .replace("${fullName}", fullName);
        GlificOptinContactResponse glificOptinContactResponse = glificRestClient.callAPI(message, new ParameterizedTypeReference<GlificResponse<GlificOptinContactResponse>>() {
        });
        return glificOptinContactResponse.getOptinContact().getContact().getId();
    }

    private GlificGetContactsResponse getContact(String phoneNumber) {
        String message = GET_CONTACT_JSON.replace("${phoneNumber}", phoneNumber);
        return glificRestClient.callAPI(message, new ParameterizedTypeReference<GlificResponse<GlificGetContactsResponse>>() {
        });
    }

    public List<GlificContactGroupsResponse.ContactGroup> getContactGroups(Pageable pageable) {
        String message = this.populatePaginationDetails(GET_CONTACT_GROUP_JSON, pageable);
        GlificContactGroupsResponse glificContactGroupsResponse = glificRestClient.callAPI(message, new ParameterizedTypeReference<GlificResponse<GlificContactGroupsResponse>>() {
        });
        return glificContactGroupsResponse.getGroups();
    }

    public int getContactGroupCount() {
        GlificContactGroupCountResponse response = glificRestClient.callAPI(GET_CONTACT_GROUP_COUNT_JSON, new ParameterizedTypeReference<GlificResponse<GlificContactGroupCountResponse>>() {
        });
        return response.getCountGroups();
    }

    public List<GlificContactGroupContactsResponse.GlificContactGroupContacts> getContactGroupContacts(String contactGroupId, Pageable pageable) {
        String message = this.populatePaginationDetails(GET_CONTACT_GROUP_CONTACTS_JSON, pageable);
        message = message.replace("${groupId}", contactGroupId);
        GlificContactGroupContactsResponse response = glificRestClient.callAPI(message, new ParameterizedTypeReference<GlificResponse<GlificContactGroupContactsResponse>>() {
        });
        return response.getContacts();
    }

    public int getContactGroupContactsCount(String contactGroupId) {
        String message = GET_CONTACT_GROUP_CONTACT_COUNT_JSON.replace("${groupId}", contactGroupId);
        GlificContactGroupContactCountResponse response = glificRestClient.callAPI(message, new
                ParameterizedTypeReference<GlificResponse<GlificContactGroupContactCountResponse>>() {
        });
        return response.getCountContacts();
    }
}
