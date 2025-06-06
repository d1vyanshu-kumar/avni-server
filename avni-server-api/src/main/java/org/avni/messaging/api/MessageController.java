package org.avni.messaging.api;

import org.avni.messaging.contract.ManualMessageContract;
import org.avni.messaging.contract.SendMessageResponse;
import org.avni.messaging.contract.StartFlowForContactRequest;
import org.avni.messaging.contract.glific.GlificMessageTemplate;
import org.avni.messaging.contract.web.MessageRequestResponse;
import org.avni.messaging.domain.MessageDeliveryStatus;
import org.avni.messaging.domain.MessageRequest;
import org.avni.messaging.domain.ReceiverType;
import org.avni.messaging.domain.exception.GlificException;
import org.avni.messaging.domain.exception.GlificNotConfiguredException;
import org.avni.messaging.service.MessageRequestService;
import org.avni.messaging.service.MessageTemplateService;
import org.avni.messaging.service.MessagingService;
import org.avni.messaging.service.PhoneNumberNotAvailableOrIncorrectException;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.IndividualService;
import org.avni.server.service.accessControl.AccessControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
public class MessageController {
    private static final String MessageEndpoint = "/web/message";
    private final AccessControlService accessControlService;
    private final MessageRequestService messageRequestService;
    private final MessagingService messagingService;
    private final UserRepository userRepository;
    private final IndividualService individualService;
    private final MessageTemplateService messageTemplateService;

    @Autowired
    public MessageController(AccessControlService accessControlService, MessageRequestService messageRequestService, MessagingService messagingService, UserRepository userRepository,
                             IndividualService individualService, MessageTemplateService messageTemplateService) {
        this.accessControlService = accessControlService;
        this.messageRequestService = messageRequestService;
        this.messagingService = messagingService;
        this.userRepository = userRepository;
        this.individualService = individualService;
        this.messageTemplateService = messageTemplateService;
    }

    @RequestMapping(value = MessageEndpoint + "/subject/{id}/msgsNotYetSent", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<MessageRequestResponse>> fetchAllMsgsNotYetSentForContactSubject(@PathVariable("id") String subjectId) {
        Stream<MessageRequest> messagesNotSent = messageRequestService.fetchPendingScheduledMessages(
                individualService.getIndividual(subjectId).getId(), ReceiverType.Subject, MessageDeliveryStatus.NotSent);
        List<GlificMessageTemplate> messageTemplates = messageTemplateService.findAll();
        return ResponseEntity.ok(messagesNotSent.map(msg -> MessageRequestResponse.fromMessageRequest(msg, messageTemplates))
                .collect(Collectors.toList()));
    }

    @RequestMapping(value = MessageEndpoint + "/user/{id}/msgsNotYetSent", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<MessageRequestResponse>> fetchAllMsgsNotYetSentForContactUser(@PathVariable("id") String userId) {
        Stream<MessageRequest> messagesNotSent = messageRequestService.fetchPendingScheduledMessages(
                userRepository.getUser(userId).getId(), ReceiverType.User, MessageDeliveryStatus.NotSent);
        List<GlificMessageTemplate> messageTemplates = messageTemplateService.findAll();
        return ResponseEntity.ok(messagesNotSent.map(msg -> MessageRequestResponse.fromMessageRequest(msg, messageTemplates))
                .collect(Collectors.toList()));
    }

    @RequestMapping(value = MessageEndpoint + "/sendMsg", method = RequestMethod.POST)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional
    public ResponseEntity<SendMessageResponse> sendMsgToContactUser(@RequestBody ManualMessageContract manualMessageContract) {
        accessControlService.checkPrivilege(PrivilegeType.Messaging);
        accessControlService.checkPrivilege(PrivilegeType.EditUserConfiguration);
        try {
            messagingService.sendMessageSynchronously(manualMessageContract);
        } catch (PhoneNumberNotAvailableOrIncorrectException e) {
            return ResponseEntity.badRequest().body(new SendMessageResponse(MessageDeliveryStatus.NotSentNoPhoneNumberInAvni, e.getMessage()));
        } catch (GlificNotConfiguredException e) {
            return ResponseEntity.badRequest().body(new SendMessageResponse(MessageDeliveryStatus.NotSent, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new SendMessageResponse(MessageDeliveryStatus.Failed, e.getMessage()));
        }
        return ResponseEntity.ok(new SendMessageResponse(MessageDeliveryStatus.Sent, "Success"));
    }

    @RequestMapping(value = MessageEndpoint + "/startFlowForContact", method = RequestMethod.POST)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional
    public ResponseEntity<SendMessageResponse> startFlowForContact(@RequestBody StartFlowForContactRequest startFlowForContactRequest) {
        accessControlService.checkPrivilege(PrivilegeType.Messaging);
        accessControlService.checkPrivilege(PrivilegeType.EditUserConfiguration);
        try {
            messagingService.sendStartFlowForContactSynchronously(startFlowForContactRequest);
        } catch (PhoneNumberNotAvailableOrIncorrectException e) {
            return ResponseEntity.badRequest().body(new SendMessageResponse(MessageDeliveryStatus.NotSentNoPhoneNumberInAvni, e.getMessage()));
        } catch (GlificNotConfiguredException e) {
            return ResponseEntity.badRequest().body(new SendMessageResponse(MessageDeliveryStatus.NotSent, e.getMessage()));
        } catch (GlificException e) {
            return ResponseEntity.badRequest().body(new SendMessageResponse(MessageDeliveryStatus.NotSent, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new SendMessageResponse(MessageDeliveryStatus.Failed, e.getMessage()));
        }
        return ResponseEntity.ok(new SendMessageResponse(MessageDeliveryStatus.Sent, "Success"));
    }

    @RequestMapping(value = MessageEndpoint + "/contactGroup/{id}/msgsNotYetSent", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<MessageRequestResponse>> fetchAllMsgsNotYetSentForContactGroup(@PathVariable("id") String groupId) {
        return getListResponseEntity(groupId, MessageDeliveryStatus.NotSent);
    }

    @RequestMapping(value = MessageEndpoint + "/contactGroup/{id}/msgsSent", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<MessageRequestResponse>> fetchAllMsgsSentForContactGroup(@PathVariable("id") String groupId) {
        return getListResponseEntity(groupId, MessageDeliveryStatus.Sent);
    }

    private ResponseEntity<List<MessageRequestResponse>> getListResponseEntity(String groupId, MessageDeliveryStatus messageDeliveryStatus) {
        Stream<MessageRequest> groupMessages = messageRequestService.getGroupMessages(groupId, messageDeliveryStatus);
        List<GlificMessageTemplate> messageTemplates = messageTemplateService.findAll();
        return ResponseEntity.ok(groupMessages.map(msg -> MessageRequestResponse.fromMessageRequest(msg, messageTemplates))
                .collect(Collectors.toList()));
    }
}
