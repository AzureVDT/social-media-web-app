import { MESSAGE_TYPE, NOTIFICATION_TYPE } from "@/constants/global";
import handleSaveUnreadMessage from "../conversation/messages/handleSaveUnreadMessage";
import handleUpdateUnreadMessage from "../conversation/messages/handleUpdateUnreadMessage";
import { collection, doc, getDoc } from "firebase/firestore";
import { db } from "@/constants/firebaseConfig";
import { setCurrentConversation } from "@/store/actions/conversationSlice";
import { Dispatch } from "@reduxjs/toolkit";
import {
    ConversationResponse,
    Member,
    MessageResponse,
} from "@/types/conversationType";

export default async function onMessageReceived(
    payload: any,
    showChatModal: boolean,
    currentConversation: ConversationResponse,
    dispatch: Dispatch<any>,
    userId: number,
    setMessages: (func: (messages: MessageResponse) => MessageResponse) => void,
    setTriggerScrollChat: (isNewMessage: boolean) => void,
    triggerScrollChat: boolean
) {
    const payloadData = JSON.parse(payload.body);
    const isSavedUnreadMessage =
        !showChatModal ||
        currentConversation.conversation_id !== payloadData.conversation_id;
    if (isSavedUnreadMessage) {
        console.log("unread message");
        const docRef = doc(
            collection(db, "unreadTrack"),
            payloadData.conversation_id
        );
        const docSnap = await getDoc(docRef);
        if (docSnap.exists()) {
            handleUpdateUnreadMessage(
                payloadData.conversation_id,
                userId,
                payloadData.message_id
            );
        } else {
            handleSaveUnreadMessage(payloadData.conversation_id, {
                [userId]: [
                    {
                        user_id: userId,
                        message_id: payloadData.message_id,
                    },
                ],
            });
        }
    }
    if (payloadData.type === MESSAGE_TYPE.NOTIFICATION) {
        switch (payloadData.notification_type) {
            case NOTIFICATION_TYPE.CHANGE_GROUP_NAME: {
                const newCurrentConversation = {
                    ...currentConversation,
                    name: payloadData.content,
                };
                dispatch(setCurrentConversation(newCurrentConversation));
                break;
            }
            case NOTIFICATION_TYPE.CHANGE_GROUP_AVATAR: {
                const newCurrentConversation = {
                    ...currentConversation,
                    image: payloadData.content,
                };
                dispatch(setCurrentConversation(newCurrentConversation));
                break;
            }
            case NOTIFICATION_TYPE.REMOVE_MEMBER: {
                console.log(payloadData.target_user_id[0].user_id);
                const newMembers = Object.keys(currentConversation.members)
                    .filter(
                        (key) =>
                            key !==
                            payloadData.target_user_id[0].user_id.toString()
                    )
                    .reduce((obj, key) => {
                        obj[key] = currentConversation.members[key];
                        return obj;
                    }, {} as { [key: string]: Member });

                const newCurrentConversation = {
                    ...currentConversation,
                    members: newMembers,
                };

                dispatch(setCurrentConversation(newCurrentConversation));
                break;
            }

            case NOTIFICATION_TYPE.ADD_MEMBER: {
                console.log("payloadData", payloadData);
                const newMembers = {
                    ...currentConversation.members,
                    [payloadData.target_user_id[0].user_id]: {
                        ...payloadData.target_user_id[0],
                    },
                };
                console.log("newMembers:", newMembers);

                const newCurrentConversation = {
                    ...currentConversation,
                    members: newMembers,
                };

                dispatch(setCurrentConversation(newCurrentConversation));
                break;
            }

            default:
                break;
        }
    }
    setMessages((prev: MessageResponse) => ({
        ...prev,
        [payloadData.message_id]: {
            ...payloadData,
        },
    }));
    setTriggerScrollChat(!triggerScrollChat);
}
