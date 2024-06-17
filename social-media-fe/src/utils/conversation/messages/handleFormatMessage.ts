import { MESSAGE_TYPE } from "@/constants/global";
import { MessageData } from "@/types/conversationType";

export default function handleFormatMessage(message: MessageData) {
    let formattedMessage = "";
    switch (message.type) {
        case MESSAGE_TYPE.TEXT:
            formattedMessage = message.content;
            break;
        case MESSAGE_TYPE.REVOKED:
            formattedMessage = "Tin nhắn đã bị thu hồi";
            break;
        case MESSAGE_TYPE.EMOJI:
            formattedMessage = message.content;
            break;
        case MESSAGE_TYPE.GIF:
            formattedMessage = message.content;
            break;
        case MESSAGE_TYPE.STICKER:
            formattedMessage = message.content;
            break;
        default:
            formattedMessage = "Unsupported message type";
            break;
    }
    return formattedMessage;
}
