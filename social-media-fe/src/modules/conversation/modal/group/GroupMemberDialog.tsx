import {
    Box,
    Dialog,
    DialogContent,
    DialogTitle,
    IconButton,
    Popover,
    Tab,
    Tabs,
    Typography,
    styled,
} from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import MoreHorizIcon from "@mui/icons-material/MoreHoriz";
import React, { useState } from "react";
import { ConversationResponse } from "@/types/conversationType";
import Image from "next/image";
import PopupState, { bindPopover, bindTrigger } from "material-ui-popup-state";
import GroupMemberAction from "./GroupMemberAction";
import { v4 as uuidv4 } from "uuid";
import MemberTab from "./MemberTab";
import AdminTab from "./AdminTab";

const BootstrapDialog = styled(Dialog)(({ theme }) => ({
    "& .MuiDialogContent-root": {
        padding: theme.spacing(2),
    },
    "& .MuiDialogActions-root": {
        padding: theme.spacing(1),
    },
}));

const GroupMemberDialog = ({
    currentUserId,
    currentConversation,
    openGroupMemberDialog,
    setOpenGroupMemberDialog,
}: {
    currentUserId: number;
    currentConversation: ConversationResponse;
    openGroupMemberDialog: boolean;
    setOpenGroupMemberDialog: (open: boolean) => void;
}) => {
    const [value, setValue] = useState(0);
    const listMembers = Object.values(currentConversation.members).filter(
        (member) => member.user_id !== currentConversation.owner_id
    );
    const listAdmins = Object.values(currentConversation.members).filter(
        (member) => member.user_id === currentConversation.owner_id
    );
    const handleChange = (event: React.SyntheticEvent, newValue: number) => {
        setValue(newValue);
    };

    const handleClose = () => {
        setOpenGroupMemberDialog(false);
    };

    const isConversationDeputy = (id: number) => {
        return currentConversation.deputies.some((deputy) => deputy === id);
    };

    return (
        <BootstrapDialog
            onClose={handleClose}
            aria-labelledby="customized-dialog-title"
            open={openGroupMemberDialog}
            onBackdropClick={handleClose}
        >
            <DialogTitle
                sx={{
                    m: 0,
                    p: 2,
                    textAlign: "center",
                    fontWeight: 700,
                }}
                id="customized-dialog-title"
            >
                Members
            </DialogTitle>
            <IconButton
                aria-label="close"
                onClick={handleClose}
                sx={{
                    position: "absolute",
                    right: 8,
                    top: 8,
                    color: (theme) => theme.palette.grey[500],
                }}
            >
                <CloseIcon />
            </IconButton>
            <DialogContent dividers className="w-[548px] h-full">
                <Box sx={{ width: "100%" }}>
                    <Box sx={{ borderBottom: 1, borderColor: "divider" }}>
                        <Tabs
                            value={value}
                            onChange={handleChange}
                            aria-label="basic tabs example"
                        >
                            <Tab label="All" />
                            <Tab label="Admin" />
                        </Tabs>
                    </Box>
                    {value === 0 && (
                        <MemberTab
                            listMembers={listMembers}
                            currentConversation={currentConversation}
                            isConversationDeputy={isConversationDeputy}
                            currentUserId={currentUserId}
                        ></MemberTab>
                    )}
                    {value === 1 && (
                        <AdminTab
                            listAdmins={listAdmins}
                            currentConversation={currentConversation}
                            isConversationDeputy={isConversationDeputy}
                            currentUserId={currentUserId}
                        ></AdminTab>
                    )}
                </Box>
            </DialogContent>
        </BootstrapDialog>
    );
};

export default GroupMemberDialog;
