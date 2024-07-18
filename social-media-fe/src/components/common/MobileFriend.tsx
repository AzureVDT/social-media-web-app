import { RootState } from "@/store/configureStore";
import { Chip, Grid } from "@mui/material";
import React, { useMemo } from "react";
import { useSelector } from "react-redux";

const MobileFriend = () => {
    const [tab, setTab] = React.useState(0);
    const relationshipUser = useSelector(
        (state: RootState) => state.user.relationshipUsers
    );

    console.log("MobileFriend ~ relationshipUser:", relationshipUser);
    const listSuggestions = useMemo(() => {
        console.log("Running");
        const list = [];
        list.push(...Object.values(relationshipUser.receive_request));
        list.push(...Object.values(relationshipUser.send_request));
        return list;
    }, []);

    return (
        <div className="mt-20">
            <div className="flex items-center justify-start ml-2 gap-x-2">
                <Chip
                    label="Suggestions"
                    variant={tab === 0 ? "filled" : "outlined"}
                    color={tab === 0 ? "info" : "default"}
                    onClick={() => setTab(0)}
                />
                <Chip
                    label="Your friends"
                    variant={tab === 1 ? "filled" : "outlined"}
                    color={tab === 1 ? "info" : "default"}
                    onClick={() => setTab(1)}
                />
            </div>
            <Grid container>{}</Grid>
        </div>
    );
};

export default MobileFriend;
