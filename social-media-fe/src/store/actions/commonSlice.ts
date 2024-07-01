import { PayloadAction, createSlice } from "@reduxjs/toolkit";

type CommonType = {
    triggerReFetchingRelationship: boolean;
    showChatModal: boolean;
    openCallDialog: boolean;
    openGroupCallDialog: boolean;
};

const initialState: CommonType = {
    triggerReFetchingRelationship: true,
    showChatModal: false,
    openCallDialog: false,
    openGroupCallDialog: false,
};

const commonSlice = createSlice({
    name: "common",
    initialState,
    reducers: {
        setTriggerReFetchingRelationship(
            state,
            action: PayloadAction<boolean>
        ) {
            state.triggerReFetchingRelationship = action.payload;
        },
        setShowChatModal(state, action: PayloadAction<boolean>) {
            state.showChatModal = action.payload;
        },
        setOpenCallDialog(state, action: PayloadAction<boolean>) {
            state.openCallDialog = action.payload;
        },
        setOpenGroupCallDialog(state, action: PayloadAction<boolean>) {
            state.openGroupCallDialog = action.payload;
        },
    },
});

export const {
    setTriggerReFetchingRelationship,
    setShowChatModal,
    setOpenCallDialog,
    setOpenGroupCallDialog,
} = commonSlice.actions;
export default commonSlice.reducer;
