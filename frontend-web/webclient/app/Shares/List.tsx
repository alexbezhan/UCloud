import {
    APICallParameters,
    APICallState,
    callAPI,
    mapCallState,
    useAsyncCommand,
    useCloudAPI
} from "Authentication/DataHook";
import {Cloud} from "Authentication/SDUCloudObject";
import {UserAvatar} from "AvataaarLib/UserAvatar";
import {emptyPage} from "DefaultObjects";
import {loadingAction} from "Loading";
import {MainContainer} from "MainContainer/MainContainer";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, updatePageTitle} from "Navigation/Redux/StatusActions";
import {PaginationButtons} from "Pagination";
import * as Pagination from "Pagination";
import * as React from "react";
import {useEffect, useRef, useState} from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {AccessRight, AccessRights, Dictionary, Page, singletonToPage} from "Types";
import {Box, Card, Flex, Icon, SelectableText, SelectableTextWrapper, Text} from "ui-components";
import Button from "ui-components/Button";
import ClickableDropdown from "ui-components/ClickableDropdown";
import * as Heading from "ui-components/Heading";
import Input, {InputLabel} from "ui-components/Input";
import Link from "ui-components/Link";
import {SidebarPages} from "ui-components/Sidebar";
import {Spacer} from "ui-components/Spacer";
import {TextSpan} from "ui-components/Text";
import {colors} from "ui-components/theme";
import {AvatarType, defaultAvatar} from "UserSettings/Avataaar";
import {fileTablePage, getFilenameFromPath} from "Utilities/FileUtilities";
import {addStandardDialog, FileIcon} from "UtilityComponents";
import {defaultErrorHandler, iconFromFilePath} from "UtilityFunctions";
import {ListProps, ListSharesParams, loadAvatars, Share, SharesByPath, ShareState} from ".";
import {acceptShare, createShare, findShare, listShares, revokeShare, updateShare} from "./index";

export const List: React.FunctionComponent<ListProps & ListOperations> = props => {
    const initialFetchParams = props.byPath === undefined ?
        listShares({sharedByMe: false, itemsPerPage: 25, page: 0}) : findShare(props.byPath);

    const [avatars, setAvatarParams, avatarParams] = useCloudAPI<Dictionary<AvatarType>>(
        loadAvatars({usernames: new Set([])}), {}
    );

    let sharedByMe = false;
    // Start of real data
    const [response, setFetchParams, params] = props.byPath === undefined ?
        useCloudAPI<Page<SharesByPath>>(initialFetchParams, emptyPage) :
        useCloudAPI<SharesByPath | null>(initialFetchParams, null);

    const page = props.byPath === undefined ?
        response as APICallState<Page<SharesByPath>> :
        mapCallState(response as APICallState<SharesByPath | null>, item => singletonToPage(item));
    // End of real data

    // Need dummy data? Remove the comments!
    // const [params, setFetchParams] = useState(listShares({sharedByMe, itemsPerPage: 100, page: 0}));
    // const items = receiveDummyShares(params.parameters!.itemsPerPage, params.parameters!.page);
    // const page: APICallState<Page<SharesByPath>> = {loading: false, data: items, error: undefined};
    // End of dummy data

    if (props.byPath !== undefined && page.data.items.length > 0) {
        sharedByMe = page.data.items[0].sharedByMe;
    } else {
        const listParams = params as APICallParameters<ListSharesParams>;
        if (listParams.parameters !== undefined) {
            sharedByMe = listParams.parameters.sharedByMe;
        }
    }

    props.setGlobalLoading(page.loading);

    const refresh = () => setFetchParams({...params, reloadId: Math.random()});

    useEffect(() => {
        if (!props.innerComponent) {
            props.setActivePage();
            props.updatePageTitle();
            props.setRefresh(refresh);
        }

        return () => {
            if (!props.innerComponent) {
                // Revert reload action
                props.setGlobalLoading(false);
                props.setRefresh(undefined);
            }
        };
    });

    useEffect(() => {
        const usernames: Set<string> = new Set(page.data.items.map(group =>
            group.shares.map(share => group.sharedByMe ? share.sharedWith : group.sharedBy)
        ).reduce((acc, val) => acc.concat(val), []));

        if (JSON.stringify(Array.from(avatarParams.parameters!.usernames)) !== JSON.stringify(Array.from(usernames))) {
            setAvatarParams(loadAvatars({usernames}));
        }
    }, [page]);

    const AvatarComponent = (props: {username: string}) => {
        let avatar = defaultAvatar;
        const loadedAvatar =
            !!avatars.data && !!avatars.data.avatars ? avatars.data.avatars[props.username] : undefined;
        if (!!loadedAvatar) avatar = loadedAvatar;
        return <UserAvatar avatar={avatar} mr={"10px"} />;
    };

    const GroupedShareCardWrapper = (props: {shareByPath: SharesByPath; simple: boolean;}) => {
        const [page, setPage] = useState(0);
        const pageSize = 5;
        return (
            <GroupedShareCard
                simple={props.simple}
                onUpdate={refresh}
                groupedShare={props.shareByPath}
                key={props.shareByPath.path}
            >
                {props.shareByPath.shares.slice(pageSize * page, pageSize * page + pageSize).map(share => (
                    <ShareRow
                        simple={props.simple}
                        key={share.id}
                        sharedBy={props.shareByPath.sharedBy}
                        onUpdate={refresh}
                        share={share}
                        sharedByMe={sharedByMe}
                    >
                        <AvatarComponent username={sharedByMe ? share.sharedWith : props.shareByPath.sharedBy} />
                    </ShareRow>
                ))}
                <PaginationButtons
                    totalPages={Math.floor(props.shareByPath.shares.length / pageSize)}
                    currentPage={page}
                    toPage={setPage}
                />
            </GroupedShareCard>
        );
    };

    const header = props.byPath !== undefined ? null : (
        <SelectableTextWrapper>
            <SelectableText
                mr="1em"
                cursor="pointer"
                fontSize={3}
                selected={!sharedByMe}
                onClick={() => setFetchParams(listShares({sharedByMe: false, itemsPerPage: 25, page: 0}))}
            >
                Shared with Me
            </SelectableText>

            <SelectableText
                mr="1em"
                cursor="pointer"
                selected={sharedByMe}
                fontSize={3}
                onClick={() => setFetchParams(listShares({sharedByMe: true, itemsPerPage: 25, page: 0}))}
            >
                Shared by Me
            </SelectableText>
        </SelectableTextWrapper>
    );

    const shares = page.data.items.filter(it => it.sharedByMe === sharedByMe || props.byPath !== undefined);
    const simple = !!props.simple;
    const main = (
        <Pagination.List
            loading={page.loading}
            page={page.data}
            customEmptyPage={simple ? (
                <Box>
                    No shares for <b>{getFilenameFromPath(props.byPath!)}</b>
                </Box>
            ) : <NoShares sharedByMe={sharedByMe} />}
            onPageChanged={(pageNumber, page) => setFetchParams(listShares({
                sharedByMe,
                page: pageNumber,
                itemsPerPage: page.itemsPerPage
            }))}
            pageRenderer={() => (
                <>
                    {props.innerComponent ? header : null}
                    {shares.map(it => <GroupedShareCardWrapper key={it.path} shareByPath={it} simple={simple} />)}
                </>
            )}
        />
    );

    if (simple) return main;

    return (
        <MainContainer
            headerSize={55}
            header={props.innerComponent ? null : header}
            main={main}
            sidebar={null}
        />
    );
};

const NoShares = ({sharedByMe}: {sharedByMe: boolean}) => (
    <Heading.h3 textAlign="center">
        No shares
        <br />
        {sharedByMe ?
            <small>You can create a new share by clicking 'Share' on one of your files.</small> :
            <small>Files shared will appear here.</small>
        }
    </Heading.h3>
);


interface ListEntryProperties {
    groupedShare: SharesByPath;
    onUpdate: () => void;
    simple: boolean;
}

const GroupedShareCard: React.FunctionComponent<ListEntryProperties> = props => {
    const {groupedShare} = props;

    const [isCreatingShare, setIsCreatingShare] = useState(false);
    const [newShareRights, setNewShareRights] = useState(AccessRights.READ_RIGHTS);
    const newShareUsername = useRef<HTMLInputElement>(null);

    const doCreateShare = async (event: React.FormEvent<HTMLFormElement>) => {
        if (!isCreatingShare) {
            event.preventDefault();

            const username = newShareUsername.current!.value;
            if (username.length === 0) {
                snackbarStore.addFailure("Please fill out the username.");
                return;
            }

            try {
                setIsCreatingShare(true);
                await callAPI(createShare(groupedShare.path, username, newShareRights));
                newShareUsername.current!.value = "";
                props.onUpdate();
            } catch (e) {
                defaultErrorHandler(e);
            } finally {
                setIsCreatingShare(false);
            }
        }
    };

    const [isLoading, sendCommand] = useAsyncCommand();

    const revokeAll = async () => {
        addStandardDialog({
            title: "Revoke?",
            message: `Remove all shares for ${getFilenameFromPath(groupedShare.path)}?`,
            onConfirm: () => groupedShare.shares.filter(it => inCancelableState(it.state))
                .forEach(({id}) => sendCommand(revokeShare(id)))
        });
    };

    const folderLink = (groupedShare.shares[0].state === ShareState.ACCEPTED) || groupedShare.sharedByMe ?
        <Link to={fileTablePage(groupedShare.path)}>{getFilenameFromPath(groupedShare.path)}</Link> :
        <Text>{getFilenameFromPath(groupedShare.path)}</Text>;
    return (
        <Card height="auto" width={1} boxShadow="sm" borderWidth={1} borderRadius={6} mb={12}>
            <Flex
                bg="lightGray"
                color="darkGray"
                px={3}
                py={2}
                alignItems="center"
                style={{
                    borderRadius: "6px 6px 0px 0px"
                }}
            >
                <Box ml="3px" mr="10px">
                    <FileIcon fileIcon={iconFromFilePath(groupedShare.path, "DIRECTORY", Cloud.homeFolder)} />
                </Box>
                <Heading.h4> {folderLink} </Heading.h4>
                <Box ml="auto" />
                {groupedShare.sharedByMe ?
                    `${groupedShare.shares.length} ${groupedShare.shares.length > 1 ?
                        "collaborators" : "collaborator"}` : sharePermissionsToText(groupedShare.shares[0].rights)}
            </Flex>
            <Box px={3} pt={3}>
                {!groupedShare.sharedByMe || props.simple ? null : (
                    <form onSubmit={doCreateShare}>
                        <Flex mb={"16px"} alignItems={"center"}>
                            <Flex flex="1 0 auto">
                                <Flex flex="1 0 auto" style={{zIndex: 1}}>
                                    <Input
                                        disabled={isCreatingShare}
                                        rightLabel
                                        placeholder={"Username"}
                                        ref={newShareUsername}
                                    />
                                </Flex>
                                <InputLabel rightLabel backgroundColor="lightBlue" width="125px">
                                    <ClickableDropdown
                                        left={"-16px"}
                                        chevron
                                        width="125px"
                                        trigger={sharePermissionsToText(newShareRights)}
                                    >
                                        <OptionItem
                                            onClick={() => setNewShareRights(AccessRights.READ_RIGHTS)}
                                            text={CAN_VIEW_TEXT}
                                        />
                                        <OptionItem
                                            onClick={() => setNewShareRights(AccessRights.WRITE_RIGHTS)}
                                            text={CAN_EDIT_TEXT}
                                        />
                                    </ClickableDropdown>
                                </InputLabel>
                            </Flex>
                            <Box ml={"12px"} width="150px">
                                <Button fullWidth type="submit">
                                    <Icon name="share" size="1em" mr=".7em" />
                                    Share
                            </Button>
                            </Box>
                        </Flex>
                    </form>
                )}
                {props.children}
            </Box>
            {!(groupedShare.sharedByMe &&
                groupedShare.shares.some(it => inCancelableState(it.state)) &&
                groupedShare.shares.length > 1) ? null : (
                    <Spacer
                        left={<Box />}
                        right={<Button onClick={revokeAll} disabled={isLoading} mb="8px" mr="16px">Remove all</Button>}
                    />
                )}
        </Card>
    );
};

function inCancelableState(state: ShareState) {
    return state !== ShareState.UPDATING;
}

export const ShareRow: React.FunctionComponent<{
    share: Share,
    sharedByMe: boolean,
    sharedBy: string,
    onUpdate: () => void,
    revokeAsIcon?: boolean,
    simple: boolean
}> = ({share, sharedByMe, onUpdate, sharedBy, ...props}) => {
    const [isLoading, sendCommand] = useAsyncCommand();

    const sendCommandAndUpdate = async (call: APICallParameters) => {
        await sendCommand(call);
        onUpdate();
    };

    const doAccept = () => sendCommandAndUpdate(acceptShare(share.id));
    const doRevoke = (e: React.MouseEvent<HTMLButtonElement, MouseEvent>) => {
        e.preventDefault();
        if (props.simple) sendCommandAndUpdate(revokeShare(share.id));
        else addStandardDialog({
            title: "Revoke?",
            message: "Remove share?",
            onConfirm: () => sendCommandAndUpdate(revokeShare(share.id))
        });
    };
    const doUpdate = (newRights: AccessRight[]) => sendCommandAndUpdate(updateShare(share.id, newRights));

    let permissionsBlock: JSX.Element | string | null = null;

    if (share.state === ShareState.FAILURE) {
        permissionsBlock = (
            <Button
                color={"red"}
                disabled={isLoading}
                onClick={doRevoke}
            >
                <Icon name="close" size="1em" mr=".7em" />Remove
            </Button>
        );
    } else if (share.state === ShareState.UPDATING || isLoading) {
        permissionsBlock = null;
    } else if (!sharedByMe) {
        if (share.state === ShareState.REQUEST_SENT) {
            permissionsBlock = (
                <Box flexShrink={1}>
                    <Button
                        color="red"
                        mx="8px"
                        onClick={doRevoke}
                    >
                        <Icon name="close" size="1em" mr=".7em" />Reject
                    </Button>
                    <Button
                        color="green"
                        onClick={doAccept}
                    >
                        <Icon name="check" size="1em" mr=".7em" />Accept
                    </Button>
                </Box>
            );
        } else {
            permissionsBlock = (
                <Button
                    color="red"
                    ml="16px"
                    onClick={doRevoke}
                >
                    <Icon name="close" size="1em" mr=".7em" />Reject
                </Button>
            );
        }
    } else {
        permissionsBlock = (
            <>
                <ClickableDropdown
                    right={"0px"}
                    chevron
                    width="100px"
                    trigger={sharePermissionsToText(share.rights)}
                >
                    {share.rights.indexOf(AccessRight.WRITE) !== -1 ?
                        <OptionItem onClick={() => doUpdate(AccessRights.READ_RIGHTS)} text={CAN_VIEW_TEXT} /> :
                        <OptionItem onClick={() => doUpdate(AccessRights.WRITE_RIGHTS)} text={CAN_EDIT_TEXT} />
                    }
                </ClickableDropdown>
                {props.revokeAsIcon ? (
                    <Icon
                        name="close"
                        size="1em"
                        mr=".7em"
                        ml=".7em"
                        color="red"
                        cursor="pointer"
                        onClick={() => sendCommandAndUpdate(revokeShare(share.id))}
                    />
                ) : (
                        <Button color={"red"} ml={"16px"} onClick={doRevoke}>
                            <Icon name="close" size="1em" mr=".7em" />
                            Revoke
                        </Button>
                    )}
            </>
        );
    }

    return (
        <Flex alignItems={"center"} mb={"16px"}>
            {props.children}

            <Box>
                <Text bold>{sharedByMe ? share.sharedWith : sharedBy}</Text>
                <ShareStateRow state={share.state} />
            </Box>

            <Box flexGrow={1} />

            {permissionsBlock}
        </Flex>
    );
};

const OptionItem: React.FunctionComponent<{onClick: () => void, text: string, color?: string}> = (props) => (
    <Box cursor="pointer" width="auto" ml="-17px" pl="15px" mr="-17px" onClick={props.onClick}>
        <TextSpan color={props.color}>{props.text}</TextSpan>
    </Box>
);

const ShareStateRow: React.FunctionComponent<{state: ShareState}> = props => {
    let body: JSX.Element = <></>;

    switch (props.state) {
        case ShareState.ACCEPTED:
            body = <><Icon size={20} color={colors.green} name={"check"} /> The share has been accepted.</>;
            break;
        case ShareState.FAILURE:
            body = (
                <>
                    <Icon size={20} color={colors.red} name={"close"} /> An error has occurred. The share is no longer
                    valid.
                </>
            );
            break;
        case ShareState.UPDATING:
            body = <><Icon size={20} color={colors.blue} name={"refresh"} /> The share is currently updating.</>;
            break;
        case ShareState.REQUEST_SENT:
            body = <>The share has not yet been accepted.</>;
            break;
    }

    return <Text>{body}</Text>;
};

const CAN_EDIT_TEXT = "Can Edit";
const CAN_VIEW_TEXT = "Can View";

function sharePermissionsToText(rights: AccessRight[]): string {
    if (rights.indexOf(AccessRight.WRITE) !== -1) return CAN_EDIT_TEXT;
    else if (rights.indexOf(AccessRight.READ) !== -1) return CAN_VIEW_TEXT;
    else return "No permissions";
}

const receiveDummyShares = (itemsPerPage: number, page: number) => {
    const payload = [...Array(itemsPerPage).keys()].map(i => {
        const extension = Math.floor(Math.random() * 2) === 0 ? ".png" : "";
        const path = `/home/user/SharedItem${i + page * itemsPerPage}${extension}`;
        const sharedBy = "user";
        const sharedByMe = Math.floor(Math.random() * 2) === 0;

        const shares: Share[] = [...Array(1 + Math.floor(Math.random() * 6))].map(j => {
            const states = Object.keys(ShareState);
            const state = ShareState[states[(Math.floor(Math.random() * states.length))]];
            return {
                state,
                rights: AccessRights.WRITE_RIGHTS,
                id: (Math.random() * 100000000).toString(),
                sharedWith: "user"
            };
        });

        return {
            path,
            sharedBy,
            sharedByMe,
            shares
        };
    });

    return ({
        itemsInTotal: 500,
        itemsPerPage,
        pageNumber: page,
        pagesInTotal: 500 / itemsPerPage,
        items: payload
    });
};

interface ListOperations {
    updatePageTitle: () => void;
    setRefresh: (f?: () => void) => void;
    setActivePage: () => void;
    setGlobalLoading: (loading: boolean) => void;
}

const mapDispatchToProps = (dispatch: Dispatch): ListOperations => ({
    updatePageTitle: () => dispatch(updatePageTitle("Shares")),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
    setActivePage: () => dispatch(setActivePage(SidebarPages.Shares)),
    setGlobalLoading: loading => dispatch(loadingAction(loading)),
});

export default connect(null, mapDispatchToProps)(List);
