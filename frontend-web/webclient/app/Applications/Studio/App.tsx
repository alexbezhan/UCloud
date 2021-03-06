import {WithAllAppTags, WithAppMetadata} from "Applications";
import {
    ApplicationAccessRight,
    ApplicationPermissionEntry,
    clearLogo,
    createApplicationTag,
    deleteApplicationTag,
    listByName,
    updateApplicationPermission,
    uploadLogo,
    AccessEntityType,
    DetailedAccessEntity
} from "Applications/api";
import {AppToolLogo} from "Applications/AppToolLogo";
import * as Actions from "Applications/Redux/BrowseActions";
import {Tag} from "Applications/Card";
import {useCloudCommand, useCloudAPI} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import {emptyPage} from "DefaultObjects";
import {dialogStore} from "Dialog/DialogStore";
import {loadingAction, LoadingAction} from "Loading";
import {MainContainer} from "MainContainer/MainContainer";
import {HeaderActions, setPrioritizedSearch, setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, StatusActions, updatePageTitle} from "Navigation/Redux/StatusActions";
import {useEffect, useRef} from "react";
import * as React from "react";
import {useState} from "react";
import {connect} from "react-redux";
import {RouteComponentProps} from "react-router";
import {Dispatch} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import {Button, Checkbox, Flex, Icon, Label, List, Text, VerticalButtonGroup} from "ui-components";
import Box from "ui-components/Box";
import ClickableDropdown from "ui-components/ClickableDropdown";
import * as Heading from "ui-components/Heading";
import Input, {HiddenInputField, InputLabel} from "ui-components/Input";
import {SidebarPages} from "ui-components/Sidebar";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import {addStandardDialog} from "UtilityComponents";
import {prettierString, stopPropagation} from "UtilityFunctions";
import * as Modal from "react-modal";
import {ListRow} from "ui-components/List";
import {defaultModalStyle} from "Utilities/ModalUtilities";

const IS_GROUP_AND_PROJECT_SEARCHING_WIP = true;

interface AppOperations {
    onInit: () => void;
    setRefresh: (refresh?: () => void) => void;
    setLoading: (loading: boolean) => void;
}

interface AppVersion {
    version: string;
    isPublic: boolean;
}

const entityTypes = [
    {text: prettifyEntityType(AccessEntityType.USER), value: AccessEntityType.USER},
    {text: prettifyEntityType(AccessEntityType.PROJECT_GROUP), value: AccessEntityType.PROJECT_GROUP},
];

function prettifyAccessRight(accessRight: ApplicationAccessRight): "Can launch" {
    switch (accessRight) {
        case ApplicationAccessRight.LAUNCH:
            return "Can launch";
    }
}

function prettifyEntityType(entityType: AccessEntityType): string {
    switch (entityType) {
        case AccessEntityType.USER: {
            return "User";
        }
        case AccessEntityType.PROJECT_GROUP: {
            return "Project group";
        }
        default: {
            return "Unknown";
        }
    }
}

async function loadApplicationPermissionEntries(appName: string): Promise<ApplicationPermissionEntry[]> {
    const {response} = await Client.get<Array<{
        entity: DetailedAccessEntity;
        permission: ApplicationAccessRight;
    }>>(`/hpc/apps/list-acl/${appName}`);
    return response.map(item => ({
        entity: {user: item.entity.user, project: item.entity.project, group: item.entity.group},
        permission: item.permission,
    }));
}

const LeftAlignedTableHeader = styled(TableHeader)`
    text-align: left;
`;

const App: React.FunctionComponent<RouteComponentProps<{name: string}> & AppOperations> = props => {
    const name = props.match.params.name;

    const [commandLoading, invokeCommand] = useCloudCommand();
    const [logoCacheBust, setLogoCacheBust] = useState("" + Date.now());
    const [access, setAccess] = React.useState<ApplicationAccessRight>(ApplicationAccessRight.LAUNCH);
    const [permissionEntries, setPermissionEntries] = React.useState<ApplicationPermissionEntry[]>([]);
    const [apps, setAppParameters, appParameters] =
        useCloudAPI<Page<WithAppMetadata & WithAllAppTags>>({noop: true}, emptyPage);
    const [versions, setVersions] = useState<AppVersion[]>([]);
    const [selectedEntityType, setSelectedEntityType] = React.useState<AccessEntityType>(AccessEntityType.USER);

    const permissionLevels = [
        {text: prettifyAccessRight(ApplicationAccessRight.LAUNCH), value: ApplicationAccessRight.LAUNCH}
    ];

    async function setPermissionsOnInit(): Promise<void> {
        setPermissionEntries(await loadApplicationPermissionEntries(name));
    }

    // Loading of permission entries
    useEffect(() => {
        setPermissionsOnInit();
    }, []);

    // Loading of application versions
    useEffect(() => {
        const appVersions: AppVersion[] = [];
        apps.data.items.forEach(item => {
            appVersions.push({version: item.metadata.version, isPublic: item.metadata.public});
        });
        setVersions(appVersions);
    }, [apps.data.items]);


    useEffect(() => props.onInit(), []);

    useEffect(() => {
        setAppParameters(listByName({name, itemsPerPage: 50, page: 0}));
        props.setRefresh(() => {
            setAppParameters(listByName({name, itemsPerPage: 50, page: 0}));
        });
        return () => props.setRefresh();
    }, [name]);

    useEffect(() => {
        props.setLoading(commandLoading || apps.loading);
    }, [commandLoading, apps.loading]);

    const appTitle = apps.data.items.length > 0 ? apps.data.items[0].metadata.title : name;
    const tags = apps.data.items.length > 0 ? apps.data.items[0].tags : [];
    const newTagField = useRef<HTMLInputElement>(null);
    const userEntityField = React.useRef<HTMLInputElement>(null);
    const projectEntityField = React.useRef<HTMLInputElement>(null);
    const groupEntityField = React.useRef<HTMLInputElement>(null);
    const [modalType, setModalType] = React.useState<"PROJECT" | "GROUP" | null>(null);
    Modal.setAppElement("#app");

    if (Client.userRole !== "ADMIN") return null;
    return (
        <MainContainer
            additional={
                <Modal style={defaultModalStyle} isOpen={modalType !== null}>
                    {!modalType ? null :
                        <ListSelector
                            key={modalType}
                            type={modalType}
                            onSelect={selection => {
                                if (modalType === "GROUP") {
                                    groupEntityField.current!.value = selection;
                                } else if (modalType === "PROJECT") {
                                    projectEntityField.current!.value = selection;
                                } else {
                                    snackbarStore.addFailure("Unhandled modalType: " + modalType, false)
                                }
                                setModalType(null);
                            }}
                        />}
                </Modal>
            }

            header={(
                <Heading.h1>
                    <AppToolLogo name={name} type={"APPLICATION"} size={"64px"} cacheBust={logoCacheBust} />
                    {" "}
                    {appTitle}
                </Heading.h1>
            )}

            sidebar={(
                <VerticalButtonGroup>
                    <Button fullWidth as="label">
                        Upload Logo
                        <HiddenInputField
                            type="file"
                            onChange={async e => {
                                const target = e.target;
                                if (target.files) {
                                    const file = target.files[0];
                                    target.value = "";
                                    if (file.size > 1024 * 512) {
                                        snackbarStore.addFailure("File exceeds 512KB. Not allowed.", false);
                                    } else {
                                        if (await uploadLogo({name, file, type: "APPLICATION"})) {
                                            setLogoCacheBust("" + Date.now());
                                        }
                                    }
                                    dialogStore.success();
                                }
                            }}
                        />
                    </Button>

                    <Button
                        type="button"
                        color="red"
                        disabled={commandLoading}
                        onClick={async () => {
                            await invokeCommand(clearLogo({type: "APPLICATION", name}));
                            setLogoCacheBust("" + Date.now());
                        }}
                    >
                        Remove Logo
                    </Button>
                </VerticalButtonGroup>
            )}

            main={(
                <Flex flexDirection="column">
                    <Box maxWidth="800px" width="100%" ml="auto" mr="auto">
                        <Heading.h2>Tags</Heading.h2>
                        <Box mb={46} mt={26}>
                            {tags.map(tag => (
                                <Flex key={tag} mb={16}>
                                    <Box flexGrow={1}>
                                        <Tag key={tag} label={tag} />
                                    </Box>
                                    <Box>
                                        <Button
                                            color={"red"}
                                            type={"button"}

                                            disabled={commandLoading}
                                            onClick={async () => {
                                                await invokeCommand(deleteApplicationTag({applicationName: name, tags: [tag]}));
                                                setAppParameters(listByName({...appParameters.parameters}));
                                            }}
                                        >

                                            <Icon size={16} name="trash" />
                                        </Button>
                                    </Box>
                                </Flex>
                            ))}
                            <form
                                onSubmit={async e => {
                                    e.preventDefault();
                                    if (commandLoading) return;

                                    const tagField = newTagField.current;
                                    if (tagField === null) return;

                                    const tagValue = tagField.value;
                                    if (tagValue === "") return;

                                    await invokeCommand(createApplicationTag({applicationName: name, tags: [tagValue]}));
                                    setAppParameters(listByName({...appParameters.parameters}));

                                    tagField.value = "";
                                }}
                            >
                                <Flex>
                                    <Box flexGrow={1}>
                                        <Input type="text"
                                            ref={newTagField}
                                            rightLabel
                                            height={35} />
                                    </Box>
                                    <Button disabled={commandLoading} type="submit" width={100} attached>Add tag</Button>
                                </Flex>
                            </form>
                        </Box>
                    </Box>
                    <Box maxWidth="800px" width="100%" ml="auto" mr="auto" mt="25px">
                        <Heading.h2>Permissions</Heading.h2>
                        <Box mt={16}>
                            <form onSubmit={onPermissionsSubmit}>
                                <Flex height={45}>
                                    <InputLabel width={350} leftLabel>
                                        <ClickableDropdown
                                            chevron
                                            width="180px"
                                            onChange={(val: AccessEntityType) => setSelectedEntityType(val)}
                                            trigger={
                                                <Box as="span" minWidth="220px">
                                                    {prettifyEntityType(selectedEntityType)}
                                                </Box>
                                            }
                                            options={entityTypes}
                                        />
                                    </InputLabel>
                                    {selectedEntityType === AccessEntityType.USER ? (
                                        <Input
                                            rightLabel
                                            leftLabel
                                            required
                                            type="text"
                                            ref={userEntityField}
                                            placeholder="Username"
                                        />
                                    ) : (
                                            <>
                                                <Input
                                                    leftLabel
                                                    rightLabel
                                                    required
                                                    onClick={() => IS_GROUP_AND_PROJECT_SEARCHING_WIP ? null : setModalType("PROJECT")}
                                                    width={180}
                                                    type="text"
                                                    readOnly={!IS_GROUP_AND_PROJECT_SEARCHING_WIP}
                                                    ref={projectEntityField}
                                                    placeholder="Project name"
                                                />
                                                <Input
                                                    leftLabel
                                                    rightLabel
                                                    required
                                                    disabled={!IS_GROUP_AND_PROJECT_SEARCHING_WIP && (projectEntityField.current?.value ?? "") === ""}
                                                    onClick={() => IS_GROUP_AND_PROJECT_SEARCHING_WIP ? null : setModalType("GROUP")}
                                                    readOnly={!IS_GROUP_AND_PROJECT_SEARCHING_WIP}
                                                    width={180}
                                                    type="text"
                                                    ref={groupEntityField}
                                                    placeholder="Group name"
                                                />
                                            </>
                                        )}
                                    <InputLabel width={300} rightLabel>
                                        <ClickableDropdown
                                            chevron
                                            width="180px"
                                            onChange={(val: ApplicationAccessRight.LAUNCH) => setAccess(val)}
                                            trigger={<Box as="span" minWidth="250px">{prettifyAccessRight(access)}</Box>}
                                            options={permissionLevels}
                                        />
                                    </InputLabel>
                                    <Button attached width="300px" disabled={commandLoading} type={"submit"}>Add permission</Button>
                                </Flex>
                            </form>
                        </Box>
                        <Flex key={5} mb={16} mt={26}>
                            <Box width={800}>
                                {(permissionEntries.length > 0) ? (
                                    <Table>
                                        <LeftAlignedTableHeader>
                                            <TableRow>
                                                <TableHeaderCell width="300px">Name</TableHeaderCell>
                                                <TableHeaderCell>Permission</TableHeaderCell>
                                                <TableHeaderCell width={50}>Delete</TableHeaderCell>
                                            </TableRow>
                                        </LeftAlignedTableHeader>
                                        <tbody>
                                            {permissionEntries.map((permissionEntry, index) => (
                                                <TableRow key={index}>
                                                    <TableCell>
                                                        {(permissionEntry.entity.user) ? (
                                                            permissionEntry.entity.user
                                                        ) : (
                                                                `${permissionEntry.entity.project?.title} / ${permissionEntry.entity.group?.title}`
                                                            )}</TableCell>
                                                    <TableCell>{prettifyAccessRight(permissionEntry.permission)}</TableCell>
                                                    <TableCell textAlign="right">
                                                        <Button
                                                            color={"red"}
                                                            type={"button"}
                                                            onClick={() => addStandardDialog({
                                                                title: `Are you sure?`,
                                                                message: (
                                                                    <Box>
                                                                        <Text>
                                                                            Remove permission for {(permissionEntry.entity.user) ? (
                                                                                permissionEntry.entity.user
                                                                            ) : (
                                                                                    `${permissionEntry.entity.project?.title} / ${permissionEntry.entity.group?.title}`
                                                                                )}
                                                                        </Text>
                                                                    </Box>
                                                                ),
                                                                onConfirm: async () => {
                                                                    await invokeCommand(updateApplicationPermission(
                                                                        {
                                                                            applicationName: name,
                                                                            changes: [
                                                                                {
                                                                                    entity: {
                                                                                        user: permissionEntry.entity.user,
                                                                                        project: permissionEntry.entity.project ? permissionEntry.entity.project.id : null,
                                                                                        group: permissionEntry.entity.group ? permissionEntry.entity.group.id : null
                                                                                    },
                                                                                    rights: permissionEntry.permission,
                                                                                    revoke: true
                                                                                }
                                                                            ]
                                                                        }
                                                                    ));
                                                                    await setPermissionEntries(await loadApplicationPermissionEntries(name));
                                                                }
                                                            })}
                                                        >
                                                            <Icon size={16} name="trash" />
                                                        </Button>
                                                    </TableCell>
                                                </TableRow>
                                            ))}
                                        </tbody>
                                    </Table>
                                ) : (
                                        <Text textAlign="center">No explicit permissions set for this application</Text>
                                    )}
                            </Box>
                        </Flex>
                    </Box>
                    <Box maxWidth="800px" width="100%" ml="auto" mr="auto" mt="25px">
                        <Heading.h2>Versions</Heading.h2>
                        <Box mb={26} mt={26}>
                            <Table>
                                <LeftAlignedTableHeader>
                                    <TableRow>
                                        <TableHeaderCell width={100}>Version</TableHeaderCell>
                                        <TableHeaderCell>Settings</TableHeaderCell>
                                        <TableHeaderCell width={50}>Delete</TableHeaderCell>
                                    </TableRow>
                                </LeftAlignedTableHeader>
                                <tbody>
                                    {versions.map(version => (
                                        <TableRow key={version.version}>
                                            <TableCell>
                                                <WordBreakBox>
                                                    {version.version}
                                                </WordBreakBox>
                                            </TableCell>
                                            <TableCell>
                                                <Box mb={26} mt={16}>
                                                    <Label fontSize={2}>
                                                        <Flex>
                                                            <Checkbox
                                                                checked={version.isPublic}
                                                                onChange={stopPropagation}
                                                                onClick={() => {
                                                                    Client.post(`/hpc/apps/setPublic`, {
                                                                        appName: name,
                                                                        appVersion: version.version,
                                                                        public: !version.isPublic
                                                                    });

                                                                    setVersions(versions.map(v =>
                                                                        (v.version === version.version) ?
                                                                            {
                                                                                version: v.version,
                                                                                isPublic: !v.isPublic
                                                                            } : v
                                                                    ));
                                                                }}
                                                            />
                                                            <Box ml={8} mt="2px">Public</Box>
                                                        </Flex>
                                                    </Label>
                                                    {version.isPublic ? (
                                                        <Box ml={28}>Everyone can see and launch this version of {appTitle}.</Box>
                                                    ) : (
                                                            <Box ml={28}>Access to this version is restricted as defined in Permissions.</Box>
                                                        )}
                                                </Box>
                                            </TableCell>
                                            <TableCell textAlign="right">
                                                <Button
                                                    color="red"
                                                    type="button"
                                                    onClick={() => addStandardDialog({
                                                        title: `Delete ${name} version ${version.version}`,
                                                        message: (
                                                            <Box>
                                                                <Text>
                                                                    Are you sure?
                                                                </Text>
                                                            </Box>
                                                        ),
                                                        onConfirm: async () => {
                                                            await Client.delete("/hpc/apps", {appName: name, appVersion: version.version});
                                                            setAppParameters(listByName({...appParameters.parameters}));
                                                        },
                                                        confirmText: "Delete"
                                                    })}
                                                >
                                                    <Icon size={16} name="trash" />
                                                </Button>
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </tbody>
                            </Table>
                        </Box>
                    </Box>
                </Flex>
            )}
        />
    );

    async function onPermissionsSubmit(e: React.FormEvent<HTMLFormElement>) {
        e.preventDefault();
        if (commandLoading) return;

        if (selectedEntityType === AccessEntityType.USER) {
            const userField = userEntityField.current;
            if (userField === null) return;

            const userValue = userField.value;
            if (userValue === "") return;

            await invokeCommand(updateApplicationPermission({
                applicationName: name,
                changes: [{
                    entity: {user: userValue, project: null, group: null},
                    rights: access,
                    revoke: false
                }]
            }));
            setPermissionEntries(await loadApplicationPermissionEntries(name));
            userField.value = "";
        } else if (selectedEntityType === AccessEntityType.PROJECT_GROUP) {
            const projectField = projectEntityField.current;
            if (projectField === null) return;

            const projectValue = projectField.value;
            if (projectValue === "") return;

            const groupField = groupEntityField.current;
            if (groupField === null) return;

            const groupValue = groupField.value;
            if (groupValue === "") return;

            await invokeCommand(updateApplicationPermission(
                {
                    applicationName: name,
                    changes: [
                        {
                            entity: {user: null, project: projectValue, group: groupValue},
                            rights: access,
                            revoke: false
                        }
                    ]
                }
            ));
            setPermissionEntries(await loadApplicationPermissionEntries(name));
            projectField.value = "";
            groupField.value = "";
        }
    }
};

const WordBreakBox = styled(Box)`
    word-break: break-word;
    width: 100%;
`;

const mapDispatchToProps = (
    dispatch: Dispatch<Actions.Type | HeaderActions | StatusActions | LoadingAction>
): AppOperations => ({
    onInit: () => {
        dispatch(updatePageTitle("Application Studio/Apps"));
        dispatch(setPrioritizedSearch("applications"));
        dispatch(setActivePage(SidebarPages.AppStore));
    },

    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),

    setLoading: loading => dispatch(loadingAction(loading))
});

interface ListSelectorProps {
    type: "GROUP" | "PROJECT";
    onSelect(selection: string): void;
}

function ListSelector({type, onSelect}: ListSelectorProps): JSX.Element {
    const [groups, fetchGroups, groupParams] = useCloudAPI(type === "GROUP" ? {noop: true} : {noop: true}, emptyPage);
    const [projects, fetchProjects, projectParams] = useCloudAPI(type === "PROJECT" ? {noop: true} : {noop: true}, emptyPage);
    const ref = React.useRef<number>();
    const searchRef = React.useRef<HTMLInputElement>(null);

    const onKeyUp = React.useCallback(() => {
        if (ref.current !== -1) {
            window.clearTimeout(ref.current);
        }
        ref.current = (window.setTimeout(() => {
            console.log("TODO");
            // TODO
            if (type === "GROUP") {
                // Search in groups by searchRef;
            } else if (type === "PROJECT") {
                // Search in projects by searchRef;
            }
        }, 500));

    }, [searchRef.current, fetchGroups, fetchProjects]);


    // FIXME: Should be based on groups.data and project.data
    const content = type === "GROUP" ? ["GROUP1", "GROUP2", "GROUP3", "GROUP4"] : ["PROJECT1", "PROJECT2", "PROJECT3"];

    return (
        <Box>
            <Input
                key={type}
                onKeyUp={onKeyUp}
                autoComplete="off"
                ref={searchRef}
                placeholder={`Enter ${prettierString(type)} name`}
            />
            <List>
                {content.map(c => (
                    <ListRow
                        key={c}
                        left={c}
                        right={<Button onClick={() => onSelect(c)}>Select</Button>}
                    />
                ))}
            </List>
        </Box>
    );
}




export default connect(null, mapDispatchToProps)(App);
