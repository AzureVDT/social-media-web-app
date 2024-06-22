package vn.edu.iuh.fit.chatservice.service.impl;

import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.edu.iuh.fit.chatservice.client.UserClient;
import vn.edu.iuh.fit.chatservice.dto.*;
import vn.edu.iuh.fit.chatservice.entity.PendingMemberRequest;
import vn.edu.iuh.fit.chatservice.entity.conversation.Conversation;
import vn.edu.iuh.fit.chatservice.entity.conversation.ConversationSettings;
import vn.edu.iuh.fit.chatservice.entity.conversation.ConversationStatus;
import vn.edu.iuh.fit.chatservice.entity.conversation.ConversationType;
import vn.edu.iuh.fit.chatservice.entity.message.Message;
import vn.edu.iuh.fit.chatservice.entity.message.MessageType;
import vn.edu.iuh.fit.chatservice.entity.message.NotificationType;
import vn.edu.iuh.fit.chatservice.exception.AppException;
import vn.edu.iuh.fit.chatservice.message.MessageNotificationProducer;
import vn.edu.iuh.fit.chatservice.model.UserDetail;
import vn.edu.iuh.fit.chatservice.repository.ConversationRepository;
import vn.edu.iuh.fit.chatservice.repository.MessageRepository;
import vn.edu.iuh.fit.chatservice.repository.PendingMemberRequestRepository;
import vn.edu.iuh.fit.chatservice.service.ConversationService;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ConversationServiceImpl implements ConversationService {
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserClient userClient;
    private final MessageNotificationProducer messageNotificationProducer;
    private final PendingMemberRequestRepository pendingMemberRequestRepository;

    public ConversationServiceImpl(ConversationRepository conversationRepository, MessageRepository messageRepository, UserClient userClient, MessageNotificationProducer messageNotificationProducer, PendingMemberRequestRepository pendingMemberRequestRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userClient = userClient;
        this.messageNotificationProducer = messageNotificationProducer;
        this.pendingMemberRequestRepository = pendingMemberRequestRepository;
    }

    public String createPrivateConversation(List<Long> members) {
        Conversation conversation = Conversation.builder()
                .members(members)
                .createdAt(new Date())
                .updatedAt(new Date())
                .type(ConversationType.PRIVATE)
                .status(ConversationStatus.ACTIVE)
                .build();
        Optional<Conversation> optionalConversation = conversationRepository.findConversationByMembersAndType(members, ConversationType.PRIVATE);

        return optionalConversation
                .map(existConversation -> existConversation.getId().toHexString())
                .orElseGet(() -> {
                    Conversation savedConversation = conversationRepository.save(conversation);
                    return savedConversation.getId().toHexString();
                });
    }

    public String createGroupConversation(Long id, String name, String image, List<Long> members) {
        Conversation conversation = Conversation.builder()
                .name(Optional.ofNullable(name).orElse(""))
                .avatar(Optional.ofNullable(image).orElse(""))
                .ownerId(id)
                .settings(new ConversationSettings())
                .type(ConversationType.GROUP)
                .status(ConversationStatus.ACTIVE)
                .members(members)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();
        Conversation savedConversation = conversationRepository.save(conversation);
        ConversationDTO conversationDTO = createConversationDTO(savedConversation, members);
        messageNotificationProducer.notifyConversation(conversationDTO);
        return savedConversation.getId().toHexString();
    }

    @Override
    public String createConversation(Long id, String name, String image, List<Long> members, ConversationType conversationType) {
        List<UserDetail> userDetails = userClient.getUsersByIds(members);
        if (members.size() == userDetails.size()) {
            members.forEach(member -> {
                if (userDetails.stream().noneMatch(userDetail -> userDetail.user_id().equals(member))) {
                    throw new AppException(HttpStatus.NOT_FOUND.value(), "Member with id " + member + " not found");
                }
            });
        }

        if (conversationType.equals(ConversationType.PRIVATE)) {
            return createPrivateConversation(members);
        } else {
            return createGroupConversation(id, name, image, members);
        }
    }

    @Override
    public ConversationDTO getConversation(Long userId, String conversationId) {
        Conversation conversation = conversationRepository.findById(new ObjectId(conversationId), userId).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND.value(), "Conversation not found or you are not a member of this conversation"));
        List<Long> memberIds = conversation.getMembers();
        Map<Long, UserDetail> userModels = userClient.getUsersByIds(memberIds).stream().collect(Collectors.toMap(UserDetail::user_id, u -> u));
        Map<String, Message> messages = messageRepository.findLastMessagesByConversationIdIn(userId, List.of(conversation.getId().toHexString()));

        return new ConversationDTO(
                conversation,
                userModels,
                userId,
                messages.get(conversationId) != null ?
                        MessageDetailDTO.createMessageDetailDTO(messages.get(conversationId), userModels) :
                        null);
    }

    @Override
    public Conversation getPlainConversation(Long userId, String conversationId) {
        return conversationRepository.findById(new ObjectId(conversationId), userId).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND.value(), "Conversation not found or you are not a member of this conversation"));
    }

    @Override
    public List<ConversationDTO> findConversationsByUserId(Long id) {
        List<Conversation> conversations = conversationRepository.findByMembersAndStatus(List.of(id), ConversationStatus.ACTIVE);
        Map<String, Message> messages = messageRepository.findLastMessagesByConversationIdIn(id, conversations.stream().map(conversation -> conversation.getId().toHexString()).toList());
        List<Long> memberIds = conversations.stream()
                .flatMap(conversation -> conversation.getMembers().stream())
                .distinct().toList();
        Map<Long, UserDetail> userModels = userClient.getUsersByIds(memberIds).stream().collect(Collectors.toMap(UserDetail::user_id, u -> u));
        List<ConversationDTO> conversationDTOS = conversations.stream()
                .map(conversation -> {
                            Message lastMessage = messages.get(conversation.getId().toHexString());
                            MessageDetailDTO messageDetailDTO = (lastMessage != null) ? MessageDetailDTO.createMessageDetailDTO(lastMessage, userModels) : null;

                            return new ConversationDTO(
                                    conversation,
                                    conversation.getMembers().stream()
                                            .collect(Collectors.toMap(
                                                    memberId -> memberId,
                                                    userModels::get
                                            )),
                                    id,
                                    messageDetailDTO
                            );
                        }
                )
                .toList();
        conversationDTOS.forEach(currentConversation -> {
            if (currentConversation.getType().equals(ConversationType.PRIVATE)) {
                UserDetail userDetail = currentConversation.getOtherMember(id);
                if (userDetail != null) {
                    currentConversation.setName(userDetail.name());
                    currentConversation.setImage(userDetail.image_url());
                }
            }
        });
        return conversationDTOS;
    }

    @Override
    public Conversation disbandConversation(Long userId, String conversationId) {
        Conversation conversation = conversationRepository.findById(new ObjectId(conversationId), userId).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND.value(), "Conversation not found or you are not a member of this conversation"));
        if (conversation.getType().equals(ConversationType.PRIVATE)) {
            throw new AppException(HttpStatus.METHOD_NOT_ALLOWED.value(), "You can not disband a private conversation");
        } else if (!conversation.getOwnerId().equals(userId)) {
            throw new AppException(HttpStatus.FORBIDDEN.value(), "You are not the owner of this conversation");
        } else {
            conversation.setStatus(ConversationStatus.DISBAND);
            Message message = Message.builder()
                    .conversationId(conversationId)
                    .senderId(userId)
                    .createdAt(new Date())
                    .updatedAt(new Date())
                    .type(MessageType.NOTIFICATION)
                    .notificationType(NotificationType.DISBAND_GROUP)
                    .build();
            messageRepository.save(message);

            messageNotificationProducer.notifyConversationMembers(conversation, message, "message");
        }
        return conversationRepository.save(conversation);
    }

    @Override
    public SimpleConversationDTO addMember(Long userId, String conversationId, List<Long> newMembers) {
        Conversation conversation = conversationRepository.findById(new ObjectId(conversationId), userId).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND.value(), "Conversation not found or you are not a member of this conversation"));
        if (conversation.getType().equals(ConversationType.PRIVATE)) {
            throw new AppException(HttpStatus.METHOD_NOT_ALLOWED.value(), "You can not add member to a private conversation");
        }

        newMembers = filterNewMembers(conversation, newMembers);

        newMembers.forEach(newMemberId -> {
            //1. check if user is owner, if owner then could invite
            //2. check if member have permission to invite
            //3. check if deputy have permission to invite, and deputy is not null, and user is deputy
            if (!conversation.getOwnerId().equals(userId) &&
                    !conversation.getSettings().isAllowMemberToInviteMember() &&
                    !(conversation.getSettings().isAllowDeputyToInviteMember() && conversation.getDeputies() != null && conversation.getDeputies().contains(userId))) {
                throw new AppException(HttpStatus.FORBIDDEN.value(), "You are not allowed to invite member");
            }

            if (conversation.getSettings().isConfirmNewMember() && !conversation.getOwnerId().equals(userId)) {
                pendingMemberRequestRepository.save(
                        new PendingMemberRequest(
                                conversation.getId().toHexString(),
                                userId,
                                newMemberId,
                                new Date()
                        )
                );
            } else {
                conversation.getMembers().add(newMemberId);
                Message message = Message.builder()
                        .conversationId(conversationId)
                        .senderId(userId)
                        .targetUserId(List.of(newMemberId))
                        .createdAt(new Date())
                        .updatedAt(new Date())
                        .type(MessageType.NOTIFICATION)
                        .notificationType(NotificationType.ADD_MEMBER)
                        .build();
                messageRepository.save(message);
                messageNotificationProducer.notifyConversationMembers(conversation, message, "message");
            }
        });

        return saveAndReturnDTO(conversation);
    }

    private List<Long> filterNewMembers(Conversation conversation, List<Long> newMembers) {
        // Filter out members who are already in the conversation
        List<Long> filteredMembers = newMembers.stream()
                .filter(memberId -> !conversation.getMembers().contains(memberId))
                .toList();

        if (filteredMembers.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST.value(), "All members are already in this conversation");
        }

        // Get the list of pending member requests
        List<PendingMemberRequest> pendingMemberRequests = pendingMemberRequestRepository.findByConversationId(conversation.getId().toHexString());

        // Filter out members who are already in the pending list
        filteredMembers = filteredMembers.stream()
                .filter(newMemberId -> pendingMemberRequests.stream().noneMatch(pendingMemberRequest -> pendingMemberRequest.getMemberId().equals(newMemberId)))
                .toList();

        if (filteredMembers.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST.value(), "All new members are already in the pending list");
        }

        return filteredMembers;
    }

    @Override
    public List<PendingMemberRequestDetail> getPendingMemberRequests(String conversationId) {
        List<PendingMemberRequest> pendingMemberRequests = pendingMemberRequestRepository.findByConversationId(conversationId);
        List<Long> userIds = pendingMemberRequests.stream()
                .flatMap(pendingMemberRequest -> Stream.of(pendingMemberRequest.getRequesterId(), pendingMemberRequest.getMemberId()))
                .toList();

        Map<Long, UserDetail> userDetailMap = userClient.getUsersByIdsMap(userIds);
        return pendingMemberRequests.stream()
                .map(pendingMemberRequest -> PendingMemberRequestDetail.create(pendingMemberRequest, userDetailMap))
                .toList();
    }

    @Override
    public void approvePendingMemberRequest(Long userId, String conversationId, Long requesterId, Long waitingMemberId) {
        Conversation conversation = conversationRepository.findById(new ObjectId(conversationId), userId).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND.value(), "Conversation not found or you are not a member of this conversation"));
        PendingMemberRequest pendingMemberRequest = pendingMemberRequestRepository.findByConversationIdAndRequesterIdAndMemberId(conversationId, requesterId, waitingMemberId).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND.value(), "Pending member request not found"));
        if (!conversation.getOwnerId().equals(userId)) {
            throw new AppException(HttpStatus.FORBIDDEN.value(), "You are not allowed to approve member request");
        }
        conversation.getMembers().add(waitingMemberId);
        pendingMemberRequestRepository.delete(pendingMemberRequest);
        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(userId)
                .targetUserId(List.of(requesterId))
                .createdAt(new Date())
                .updatedAt(new Date())
                .type(MessageType.NOTIFICATION)
                .notificationType(NotificationType.ADD_MEMBER)
                .build();
        messageRepository.save(message);
        conversationRepository.save(conversation);
        messageNotificationProducer.notifyConversationMembers(conversation, message, "message");
    }

    @Override
    public void rejectPendingMemberRequest(Long userId, String conversationId, Long requesterId, Long waitingMemberId) {
        Conversation conversation = conversationRepository.findById(new ObjectId(conversationId), userId).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND.value(), "Conversation not found or you are not a member of this conversation"));
        PendingMemberRequest pendingMemberRequest = pendingMemberRequestRepository.findByConversationIdAndRequesterIdAndMemberId(conversationId, requesterId, waitingMemberId).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND.value(), "Pending member request not found"));
        if (!conversation.getOwnerId().equals(userId)) {
            throw new AppException(HttpStatus.FORBIDDEN.value(), "You are not allowed to reject member request");
        }
        pendingMemberRequestRepository.delete(pendingMemberRequest);
    }

    @Override
    public ConversationDTO joinByLink(Long id, String link) {
        Conversation conversation = conversationRepository.findByLink(link).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND.value(), "Link not found"));
        if (conversation.getMembers().contains(id)) {
            throw new AppException(HttpStatus.BAD_REQUEST.value(), "You are already a member of this conversation");
        }
        if (!conversation.getSettings().isJoinByLink()) {
            throw new AppException(HttpStatus.METHOD_NOT_ALLOWED.value(), "This conversation does not allow join by link");
        }
        if (conversation.getSettings().isConfirmNewMember()) {
            pendingMemberRequestRepository.save(
                    new PendingMemberRequest(
                            conversation.getId().toHexString(),
                            id,
                            id,
                            new Date()
                    )
            );
        } else {
            conversation.getMembers().add(id);
            Message message = Message.builder()
                    .conversationId(conversation.getId().toHexString())
                    .senderId(id)
                    .createdAt(new Date())
                    .updatedAt(new Date())
                    .type(MessageType.NOTIFICATION)
                    .notificationType(NotificationType.JOIN_BY_LINK)
                    .build();
            messageRepository.save(message);
            messageNotificationProducer.notifyConversationMembers(conversation, message, "message");
        }
        Conversation savedConversation = conversationRepository.save(conversation);
        return createConversationDTO(savedConversation, conversation.getMembers());
    }

    @Override
    public ConversationDTO findByLink(String link) {
        Conversation conversation = conversationRepository.findByLink(link).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND.value(), "Link not found"));
        return createConversationDTO(conversation, conversation.getMembers());
    }

    @Override
    public Conversation leaveConversation(Long id, String conversationId) {
        Conversation conversation = conversationRepository.findById(new ObjectId(conversationId), id).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND.value(), "Conversation not found or you are not a member of this conversation"));
        if (conversation.getType().equals(ConversationType.PRIVATE)) {
            throw new AppException(HttpStatus.METHOD_NOT_ALLOWED.value(), "You can not leave a private conversation");
        }
        if (conversation.getOwnerId().equals(id)) {
            throw new AppException(HttpStatus.FORBIDDEN.value(), "You are the owner of this conversation");
        }
        conversation.getMembers().remove(id);
        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(id)
                .createdAt(new Date())
                .updatedAt(new Date())
                .type(MessageType.NOTIFICATION)
                .notificationType(NotificationType.LEAVE_GROUP)
                .build();
        messageRepository.save(message);
        messageNotificationProducer.notifyConversationMembers(conversation, message, "message");
        return conversationRepository.save(conversation);
    }

    @Override
    public Conversation changeName(Long id, String conversationId, String name) {
        Conversation conversation = conversationRepository.findById(new ObjectId(conversationId), id).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND.value(), "Conversation not found or you are not a member of this conversation"));
        if (!conversation.getOwnerId().equals(id) &&
                !conversation.getSettings().isAllowMemberToChangeGroupInfo() &&
                !(conversation.getSettings().isAllowDeputyChangeGroupInfo() && conversation.getDeputies() != null && conversation.getDeputies().contains(id))) {
            throw new AppException(HttpStatus.FORBIDDEN.value(), "You are not allowed to change group info");
        }

        conversation.setName(name);
        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(id)
                .content(name)
                .createdAt(new Date())
                .updatedAt(new Date())
                .type(MessageType.NOTIFICATION)
                .notificationType(NotificationType.CHANGE_GROUP_NAME)
                .build();
        messageRepository.save(message);
        messageNotificationProducer.notifyConversationMembers(conversation, message, "message");
        return conversationRepository.save(conversation);
    }

    @Override
    public Conversation changeImage(Long id, String conversationId, String image) {
        Conversation conversation = conversationRepository.findById(new ObjectId(conversationId), id).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND.value(), "Conversation not found or you are not a member of this conversation"));
        if (!conversation.getOwnerId().equals(id) &&
                !conversation.getSettings().isAllowMemberToChangeGroupInfo() &&
                !(conversation.getSettings().isAllowDeputyChangeGroupInfo() && conversation.getDeputies() != null && conversation.getDeputies().contains(id))) {
            throw new AppException(HttpStatus.FORBIDDEN.value(), "You are not allowed to change group info");
        }
        conversation.setAvatar(image);
        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(id)
                .content(image)
                .createdAt(new Date())
                .updatedAt(new Date())
                .type(MessageType.NOTIFICATION)
                .notificationType(NotificationType.CHANGE_GROUP_AVATAR)
                .build();
        messageRepository.save(message);
        messageNotificationProducer.notifyConversationMembers(conversation, message, "message");
        return conversationRepository.save(conversation);
    }

    @Override
    public Conversation grantOwner(Long adminId, String conversationId, Long memberId) {
        Conversation conversation = validateConversationAndAdmin(conversationId, adminId);
        if (!conversation.getMembers().contains(memberId)) {
            throw new AppException(HttpStatus.NOT_FOUND.value(), "Member not found in this conversation");
        }
        conversation.setOwnerId(memberId);
        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(adminId)
                .targetUserId(List.of(memberId))
                .createdAt(new Date())
                .updatedAt(new Date())
                .type(MessageType.NOTIFICATION)
                .notificationType(NotificationType.CHANGE_GROUP_OWNER)
                .build();
        messageRepository.save(message);
        messageNotificationProducer.notifyConversationMembers(conversation, message, "message");
        return conversationRepository.save(conversation);
    }

    @Override
    public SimpleConversationDTO kickMember(Long senderId, String conversationId, Long memberId) {
        Conversation conversation = conversationRepository.findById(new ObjectId(conversationId), senderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND.value(), "Conversation not found or you are not a member of this conversation"));
        if (!conversation.getMembers().contains(memberId)) {
            throw new AppException(HttpStatus.NOT_FOUND.value(), "Member not found in this conversation");
        }
        //1. check if user is owner, if owner then could kick
        //2. check if deputy have permission to remove
        //3. check if user is deputy
        if (!conversation.getOwnerId().equals(senderId) &&
                conversation.getSettings().isAllowDeputyRemoveMember() &&
                !(conversation.getDeputies() != null && conversation.getDeputies().contains(senderId))) {
            throw new AppException(HttpStatus.FORBIDDEN.value(), "You are not allowed to kick member");
        }

        if (memberId.equals(conversation.getOwnerId())) {
            throw new AppException(HttpStatus.FORBIDDEN.value(), "You can not kick the owner of this conversation");
        }
        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .targetUserId(List.of(memberId))
                .createdAt(new Date())
                .updatedAt(new Date())
                .type(MessageType.NOTIFICATION)
                .notificationType(NotificationType.REMOVE_MEMBER)
                .build();
        Conversation copyOfConversation = new Conversation(conversation);
        messageRepository.save(message);
        messageNotificationProducer.notifyConversationMembers(copyOfConversation, message, "message");
        conversation.getMembers().remove(memberId);
        if (conversation.getDeputies() != null) {
            conversation.getDeputies().remove(memberId);
        }
        return saveAndReturnDTO(conversation);
    }

    @Override
    public SimpleConversationDTO grantDeputy(Long senderId, String conversationId, Long memberId) {
        Conversation conversation = conversationRepository.findById(new ObjectId(conversationId), senderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND.value(), "Conversation not found or you are not a member of this conversation"));
        if (!conversation.getMembers().contains(memberId)) {
            throw new AppException(HttpStatus.NOT_FOUND.value(), "Member not found in this conversation");
        }
        if (conversation.getDeputies() == null) {
            conversation.setDeputies(new ArrayList<>());
        }
        if (conversation.getDeputies().contains(memberId)) {
            throw new AppException(HttpStatus.BAD_REQUEST.value(), "Member is already a deputy");
        }
        if (!conversation.getOwnerId().equals(senderId) &&
                !(conversation.getSettings().isAllowDeputyPromoteMember() && conversation.getDeputies() != null && conversation.getDeputies().contains(senderId))) {
            throw new AppException(HttpStatus.FORBIDDEN.value(), "You are not allowed to grant deputy");
        }
        conversation.getDeputies().add(memberId);
        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .targetUserId(List.of(memberId))
                .createdAt(new Date())
                .updatedAt(new Date())
                .type(MessageType.NOTIFICATION)
                .notificationType(NotificationType.GRANT_DEPUTY)
                .build();
        messageRepository.save(message);
        messageNotificationProducer.notifyConversationMembers(conversation, message, "message");
        return saveAndReturnDTO(conversation);
    }

    @Override
    public SimpleConversationDTO revokeDeputy(Long senderId, String conversationId, Long memberId) {
        Conversation conversation = conversationRepository.findById(new ObjectId(conversationId), senderId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND.value(), "Conversation not found or you are not a member of this conversation"));
        if (!conversation.getMembers().contains(memberId)) {
            throw new AppException(HttpStatus.NOT_FOUND.value(), "Member not found in this conversation");
        }
        if (!conversation.getOwnerId().equals(senderId) && !(conversation.getSettings().isAllowDeputyDemoteMember() && conversation.getDeputies() != null && conversation.getDeputies().contains(senderId))) {
            throw new AppException(HttpStatus.FORBIDDEN.value(), "You are not allowed to revoke deputy");
        }

        conversation.getDeputies().remove(memberId);
        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .targetUserId(List.of(memberId))
                .createdAt(new Date())
                .updatedAt(new Date())
                .type(MessageType.NOTIFICATION)
                .notificationType(NotificationType.REVOKE_DEPUTY)
                .build();
        messageRepository.save(message);
        messageNotificationProducer.notifyConversationMembers(conversation, message, "message");
        return saveAndReturnDTO(conversation);
    }

    @Override
    public ConversationSettings updateConversationSettings(Long adminId, String
            conversationId, ConversationSettingsRequest settings) {
        Conversation conversation = conversationRepository.findById(new ObjectId(conversationId))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND.value(), "Conversation not found"));

        ConversationSettings oldSettings = conversation.getSettings();

        String linkToJoinGroup = settings.isJoinByLink() && oldSettings.getLinkToJoinGroup().isEmpty() ? UUID.randomUUID().toString() : oldSettings.getLinkToJoinGroup();

        ConversationSettings newSettings = new ConversationSettings(
                settings.isConfirmNewMember(),
                settings.isRestrictedMessaging(),
                settings.isAllowDeputySendMessages(),
                settings.isJoinByLink(),
                linkToJoinGroup,
                settings.isAllowMemberToChangeGroupInfo(),
                settings.isAllowDeputyChangeGroupInfo(),
                settings.isAllowDeputyToInviteMember(),
                settings.isAllowMemberToInviteMember(),
                settings.isAllowDeputyRemoveMember(),
                settings.isAllowMemberToPinMessage(),
                settings.isAllowDeputyPromoteMember(),
                settings.isAllowDeputyDemoteMember()
        );

        conversation.setSettings(newSettings);
        List<Message> notificationMessages = generateSettingsChangeMessages(conversationId, adminId, oldSettings, newSettings);
        notificationMessages.forEach(message -> {
            messageRepository.save(message);
            messageNotificationProducer.notifyConversationMembers(conversation, message, "message");
        });

        conversationRepository.save(conversation);

        return conversationRepository.save(conversation).getSettings();
    }

    private List<Message> generateSettingsChangeMessages(String conversationId, Long adminId, ConversationSettings
            oldSettings, ConversationSettings newSettings) {
        List<Message> messages = new ArrayList<>();

        if (oldSettings.isConfirmNewMember() != newSettings.isConfirmNewMember()) {
            messages.add(createNotificationMessageForUpdateSetting(conversationId, adminId, NotificationType.CONFIRM_NEW_MEMBER, Boolean.toString(newSettings.isConfirmNewMember())));
        }
        if (oldSettings.isRestrictedMessaging() != newSettings.isRestrictedMessaging()) {
            messages.add(createNotificationMessageForUpdateSetting(conversationId, adminId, NotificationType.RESTRICTED_MESSAGING, Boolean.toString(newSettings.isRestrictedMessaging())));
        }
        if (oldSettings.isAllowDeputySendMessages() != newSettings.isAllowDeputySendMessages()) {
            messages.add(createNotificationMessageForUpdateSetting(conversationId, adminId, NotificationType.DEPUTY_SEND_MESSAGES, Boolean.toString(newSettings.isAllowDeputySendMessages())));
        }
        if (oldSettings.isJoinByLink() != newSettings.isJoinByLink()) {
            messages.add(createNotificationMessageForUpdateSetting(conversationId, adminId, NotificationType.JOIN_BY_LINK, newSettings.getLinkToJoinGroup()));
        }
        if (oldSettings.isAllowMemberToChangeGroupInfo() != newSettings.isAllowMemberToChangeGroupInfo()) {
            messages.add(createNotificationMessageForUpdateSetting(conversationId, adminId, NotificationType.MEMBER_CHANGE_GROUP_INFO, Boolean.toString(newSettings.isAllowMemberToChangeGroupInfo())));
        }
        if (oldSettings.isAllowDeputyToInviteMember() != newSettings.isAllowDeputyToInviteMember()) {
            messages.add(createNotificationMessageForUpdateSetting(conversationId, adminId, NotificationType.DEPUTY_INVITE_MEMBER, Boolean.toString(newSettings.isAllowDeputyToInviteMember())));
        }
        if (oldSettings.isAllowMemberToInviteMember() != newSettings.isAllowMemberToInviteMember()) {
            messages.add(createNotificationMessageForUpdateSetting(conversationId, adminId, NotificationType.MEMBER_INVITE_MEMBER, Boolean.toString(newSettings.isAllowMemberToInviteMember())));
        }
        if (oldSettings.isAllowDeputyRemoveMember() != newSettings.isAllowDeputyRemoveMember()) {
            messages.add(createNotificationMessageForUpdateSetting(conversationId, adminId, NotificationType.DEPUTY_REMOVE_MEMBER, Boolean.toString(newSettings.isAllowDeputyRemoveMember())));
        }
        if (oldSettings.isAllowDeputyChangeGroupInfo() != newSettings.isAllowDeputyChangeGroupInfo()) {
            messages.add(createNotificationMessageForUpdateSetting(conversationId, adminId, NotificationType.DEPUTY_CHANGE_GROUP_INFO, Boolean.toString(newSettings.isAllowDeputyChangeGroupInfo())));
        }
        if (oldSettings.isAllowMemberToPinMessage() != newSettings.isAllowMemberToPinMessage()) {
            messages.add(createNotificationMessageForUpdateSetting(conversationId, adminId, NotificationType.MEMBER_PIN_MESSAGE, Boolean.toString(newSettings.isAllowMemberToPinMessage())));
        }
        if (oldSettings.isAllowDeputyPromoteMember() != newSettings.isAllowDeputyPromoteMember()) {
            messages.add(createNotificationMessageForUpdateSetting(conversationId, adminId, NotificationType.DEPUTY_PROMOTE_MEMBER, Boolean.toString(newSettings.isAllowDeputyPromoteMember())));
        }
        if (oldSettings.isAllowDeputyDemoteMember() != newSettings.isAllowDeputyDemoteMember()) {
            messages.add(createNotificationMessageForUpdateSetting(conversationId, adminId, NotificationType.DEPUTY_DEMOTE_MEMBER, Boolean.toString(newSettings.isAllowDeputyDemoteMember())));
        }

        return messages;
    }

    private ConversationDTO createConversationDTO(Conversation conversation, List<Long> members) {
        Map<Long, UserDetail> userModels = userClient.getUsersByIds(members).stream().collect(Collectors.toMap(UserDetail::user_id, u -> u));
        return new ConversationDTO(conversation, userModels, conversation.getOwnerId(), null);
    }

    private Message createNotificationMessageForUpdateSetting(String conversationId, Long adminId, NotificationType
            type, String content) {
        return Message.builder()
                .conversationId(conversationId)
                .senderId(adminId)
                .createdAt(new Date())
                .updatedAt(new Date())
                .type(MessageType.NOTIFICATION)
                .notificationType(type)
                .content(content)
                .notificationType(type)
                .build();
    }

    private Conversation validateConversationAndAdmin(String conversationId, Long adminId) {
        Conversation conversation = conversationRepository.findById(new ObjectId(conversationId), adminId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND.value(), "Conversation not found or you are not a member of this conversation"));
        if (!conversation.getOwnerId().equals(adminId)) {
            throw new AppException(HttpStatus.FORBIDDEN.value(), "You are not the owner of this conversation");
        }
        return conversation;
    }

    private SimpleConversationDTO saveAndReturnDTO(Conversation conversation) {
        Conversation savedConversation = conversationRepository.save(conversation);
        return new SimpleConversationDTO(savedConversation);
    }
}
