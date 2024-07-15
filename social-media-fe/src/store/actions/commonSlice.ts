import { PayloadAction, createSlice } from "@reduxjs/toolkit";

type CommonType = {
    triggerReFetchingRelationship: boolean;
    showChatModal: boolean;
    openCallDialog: boolean;
    openGroupCallDialog: boolean;
    progress: number;
};

const initialState: CommonType = {
    triggerReFetchingRelationship: true,
    showChatModal: false,
    openCallDialog: false,
    openGroupCallDialog: false,
    progress: 0,
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
        setProgress(state, action: PayloadAction<number>) {
            state.progress = action.payload;
        },
    },
});

export const {
    setTriggerReFetchingRelationship,
    setShowChatModal,
    setOpenCallDialog,
    setOpenGroupCallDialog,
    setProgress,
} = commonSlice.actions;
export default commonSlice.reducer;
