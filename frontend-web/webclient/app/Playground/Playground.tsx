import {MainContainer} from "MainContainer/MainContainer";
import * as React from "react";
import {EveryIcon} from "ui-components/Icon";
import {Grid, Box, Button} from "ui-components";
import * as PublicLinks from "Applications/PublicLinks/Management";
import {dialogStore} from "Dialog/DialogStore";
import {ThemeColor} from "ui-components/theme";
import {getCssVar} from "Utilities/StyledComponentsUtilities";
import {useCloudAPI} from "Authentication/DataHook";
import {defaultAvatar} from "UserSettings/Avataaar";

export const Playground: React.FunctionComponent = () => {
    const [result, setParams, params] = useCloudAPI<typeof defaultAvatar>(
        {path: "/avatar/find", method: "GET"},
        defaultAvatar,
        {cacheKey: "avatar", cacheTtlMs: 10_000}
    );

    console.log(result);
    console.log("render");


    const main = (
        <>
            <EveryIcon />
            <Grid
                gridTemplateColumns="repeat(auto-fit, minmax(40px, 40px))"
                style={{overflowY: "scroll"}}
            >
                {colors.map((c: ThemeColor) => (
                    <Box
                        title={`${c}, ${getCssVar(c)}`}
                        key={c}
                        backgroundColor={c}
                        width="40px"
                        height="40px"
                    />
                ))}
            </Grid>

            <Button onClick={() => {
                dialogStore.addDialog(<PublicLinks.PublicLinkManagement onSelect={e => console.log(e)} />, () => 0);
            }}>
                Trigger me
            </Button>
            <Button onClick={() => setParams({...params})}>
                Refetch
            </Button>

        </>
    );
    return <MainContainer main={main} />;
};

const colors = [
    "black",
    "white",
    "textBlack",
    "lightGray",
    "midGray",
    "gray",
    "darkGray",
    "lightBlue",
    "lightBlue2",
    "blue",
    "darkBlue",
    "lightGreen",
    "green",
    "darkGreen",
    "lightRed",
    "red",
    "darkRed",
    "orange",
    "darkOrange",
    "lightPurple",
    "purple",
    "yellow",
    "text",
    "textHighlight",
    "headerText",
    "headerBg",
    "headerIconColor",
    "headerIconColor2",
    "borderGray",
    "paginationHoverColor",
    "paginationDisabled",
    "iconColor",
    "iconColor2",
    "FtIconColor",
    "FtIconColor2",
    "FtFolderColor",
    "FtFolderColor2",
    "spinnerColor",
    "tableRowHighlight",
    "appCard",
    "wayfGreen",
];

export default Playground;
