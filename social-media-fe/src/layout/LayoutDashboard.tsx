"use client";
import { SOCIAL_MEDIA_API } from "@/apis/constants";
import ChatModal from "@/components/modal/ChatModal";
import DashboardTopBar from "@/modules/dashboard/DashboardTopBar";
import { setCurrentUserProfile } from "@/store/actions/profileSlice";
import { setRelationshipUsers } from "@/store/actions/userSlice";
import { RootState } from "@/store/configureStore";
import React, { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import { SocketProvider } from "@/contexts/socket-context";
import SocketType from "@/types/socketType";
import getUserInfoFromCookie from "@/utils/auth/getUserInfoFromCookie";
import { CallProvider, useCall } from "@/contexts/call-context";
import CallType from "@/types/callType";
import VideoCallDialog from "@/modules/conversation/modal/call/VideoCallDialog";

const LayoutDashboard = ({ children }: { children: React.ReactNode }) => {
    const dispatch = useDispatch();
    const openCallDialog = useSelector(
        (state: RootState) => state.common.openCallDialog
    );
    const triggerReFetchingRelationship = useSelector(
        (state: RootState) => state.common.triggerReFetchingRelationship
    );
    const showChatModal = useSelector(
        (state: RootState) => state.common.showChatModal
    );
    useEffect(() => {
        async function fetchRelationshipUsers() {
            try {
                const response =
                    await SOCIAL_MEDIA_API.USER.getCurrentUserFriends();
                if (response?.status === 200) {
                    dispatch(setRelationshipUsers(response.data));
                }
            } catch (error) {
                console.error(error);
            }
        }
        fetchRelationshipUsers();
    }, [triggerReFetchingRelationship]);

    useEffect(() => {
        const decryptedData = getUserInfoFromCookie();
        if (decryptedData) {
            dispatch(setCurrentUserProfile(decryptedData));
        }
    }, []);

    return (
        <SocketProvider value={{} as SocketType}>
            <CallProvider value={{} as CallType}>
                <div className="relative w-full h-full min-h-screen overflow-auto bg-strock">
                    <DashboardTopBar></DashboardTopBar>
                    {showChatModal && (
                        <div className="fixed bottom-0 rounded-lg shadow-md right-14 bg-lite">
                            <ChatModal></ChatModal>
                        </div>
                    )}
                    {openCallDialog && (
                        <VideoCallDialog
                            openVideoCallDialog={openCallDialog}
                            dispatch={dispatch}
                        ></VideoCallDialog>
                    )}
                    {children}
                </div>
            </CallProvider>
        </SocketProvider>
    );
};

export default LayoutDashboard;
