import {MachineTypes} from "Applications/MachineTypes";
import {OptionalParameters} from "Applications/OptionalParameters";
import {InputDirectoryParameter} from "Applications/Widgets/FileParameter";
import {AdditionalPeerParameter} from "Applications/Widgets/PeerParameter";
import {callAPI} from "Authentication/DataHook";
import {Cloud} from "Authentication/SDUCloudObject";
import {emptyPage} from "DefaultObjects";
import {dialogStore} from "Dialog/DialogStore";
import {File as CloudFile, FileResource, SortBy, SortOrder} from "Files";
import FileSelector from "Files/FileSelector";
import {listDirectory} from "Files/LowLevelFileTable";
import LoadingIcon from "LoadingIcon/LoadingIcon";
import {MainContainer} from "MainContainer/MainContainer";
import {setLoading, updatePageTitle} from "Navigation/Redux/StatusActions";
import PromiseKeeper from "PromiseKeeper";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {SnackType} from "Snackbar/Snackbars";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import {AccessRight, Page} from "Types";
import {Box, Button, ContainerForText, Flex, Label, OutlineButton, VerticalButtonGroup} from "ui-components";
import BaseLink from "ui-components/BaseLink";
import ClickableDropdown from "ui-components/ClickableDropdown";
import * as Heading from "ui-components/Heading";
import Input, {HiddenInputField} from "ui-components/Input";
import Link from "ui-components/Link";
import {
    checkForMissingParameters,
    extractValuesFromWidgets,
    findKnownParameterValues,
    hpcFavoriteApp,
    hpcJobQueryPost,
    isFileOrDirectoryParam,
    validateOptionalFields
} from "Utilities/ApplicationUtilities";
import {removeEntry} from "Utilities/CollectionUtilities";
import {
    allFilesHasAccessRight,
    checkIfFileExists,
    expandHomeFolder,
    fetchFileContent,
    fileTablePage, getFilenameFromPath,
    statFileOrNull,
    statFileQuery
} from "Utilities/FileUtilities";
import {addStandardDialog} from "UtilityComponents";
import {errorMessageOrDefault, removeTrailingSlash} from "UtilityFunctions";
import {
    AdditionalMountedFolder,
    ApplicationParameter,
    FullAppInfo,
    JobSchedulingOptionsForInput,
    ParameterTypes,
    RunAppProps,
    RunAppState,
    RunOperations,
    WithAppInvocation,
    WithAppMetadata,
} from ".";
import {AppHeader} from "./View";
import {Parameter} from "./Widgets/Parameter";
import {RangeRef} from "./Widgets/RangeParameters";

const hostnameRegex = new RegExp(
    "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*" +
    "([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])\$"
);

class Run extends React.Component<RunAppProps, RunAppState> {
    constructor(props: Readonly<RunAppProps>) {
        super(props);
        this.state = {
            promises: new PromiseKeeper(),
            jobSubmitted: false,
            initialSubmit: false,

            parameterValues: new Map(),
            mountedFolders: [],
            additionalPeers: [],
            schedulingOptions: {
                maxTime: {
                    hours: 0,
                    minutes: 0,
                    seconds: 0
                },
                numberOfNodes: 1,
                tasksPerNode: 1,
                name: React.createRef()
            },
            favorite: false,
            favoriteLoading: false,
            fsShown: false,
            previousRuns: emptyPage,
            reservation: React.createRef(),

            sharedFileSystems: {mounts: []}
        };
    }

    public componentDidMount() {
        this.props.updatePageTitle();
        const name = this.props.match.params.appName;
        const version = this.props.match.params.appVersion;
        this.state.promises.makeCancelable(
            this.retrieveApplication(name, version)
        );
    }

    public componentWillUnmount = () => this.state.promises.cancelPromises();

    public componentDidUpdate(prevProps: Readonly<RunAppProps>, prevState: Readonly<RunAppState>) {
        if (prevProps.match.params.appName !== this.props.match.params.appName ||
            prevProps.match.params.appVersion !== this.props.match.params.appVersion) {
            this.state.promises.makeCancelable(
                this.retrieveApplication(this.props.match.params.appName, this.props.match.params.appVersion)
            );
        }

        if (prevState.application !== this.state.application && this.state.application !== undefined) {
            this.fetchPreviousRuns();
        }
    }

    public render() {
        const {application, jobSubmitted, schedulingOptions, parameterValues} = this.state;
        if (!application) return <MainContainer main={<LoadingIcon size={18} />} />;

        const parameters = application.invocation.parameters;
        const mandatory = parameters.filter(parameter => !parameter.optional);
        const visible = parameters.filter(parameter =>
            parameter.optional && (parameter.visible === true || parameterValues.get(parameter.name)!.current != null)
        );
        const optional = parameters.filter(parameter =>
            parameter.optional && parameter.visible !== true && parameterValues.get(parameter.name)!.current == null);

        const onParameterChange = (parameter: ApplicationParameter, isVisible: boolean) => {
            parameter.visible = isVisible;
            if (!isVisible) {
                parameterValues.set(parameter.name, React.createRef<HTMLSelectElement | HTMLInputElement>());
            }
            this.setState(() => ({application: this.state.application}));
        };

        const mapParamToComponent = (parameter: ApplicationParameter) => {
            const ref = parameterValues.get(parameter.name)!;
            function handleParamChange() {
                onParameterChange(parameter, false);
            }
            return (
                <Parameter
                    key={parameter.name}
                    initialSubmit={this.state.initialSubmit}
                    parameterRef={ref}
                    parameter={parameter}
                    onParamRemove={handleParamChange}
                    application={application}
                />
            );
        };

        const mandatoryParams = mandatory.map(mapParamToComponent);
        const visibleParams = visible.map(mapParamToComponent);

        return (
            <MainContainer
                headerSize={48}
                header={(
                    <Flex ml="50px">
                        <AppHeader slim application={application} />
                    </Flex>
                )}

                sidebar={(
                    <VerticalButtonGroup>
                        <OutlineButton
                            onClick={() => importParameterDialog(
                                file => this.importParameters(file),
                                () => this.setState(() => ({fsShown: true}))
                            )}
                            fullWidth
                            color="darkGreen"
                            as="label"
                        >
                            Import parameters
                        </OutlineButton>
                        <Button fullWidth disabled={this.state.favoriteLoading} onClick={() => this.toggleFavorite()}>
                            {this.state.favorite ? "Remove from favorites" : "Add to favorites"}
                        </Button>
                        <Button
                            type="button"
                            onClick={this.onSubmit}
                            disabled={jobSubmitted}
                            color="blue"
                        >
                            Submit
                        </Button>
                    </VerticalButtonGroup>
                )}

                additional={(
                    <FileSelector
                        onFileSelect={it => {
                            if (!!it) this.onImportFileSelected(it);
                            this.setState(() => ({fsShown: false}));
                        }}
                        trigger={null}
                        visible={this.state.fsShown}
                    />
                )}

                main={(
                    <ContainerForText>
                        <form onSubmit={this.onSubmit} style={{width: "100%"}}>
                            {
                                this.state.previousRuns.items.length <= 0 ? null : (
                                    <RunSection>
                                        <Label>Load parameters from a previous run:</Label>
                                        <Flex flexDirection={"row"} flexWrap={"wrap"}>
                                            {
                                                this.state.previousRuns.items.slice(0, 5).map((file, idx) => (
                                                    <Box mr={"0.8em"} key={idx}>
                                                        <BaseLink
                                                            href={"#"}
                                                            onClick={async e => {
                                                                e.preventDefault();

                                                                try {
                                                                    this.props.setLoading(true);
                                                                    await this.fetchAndImportParameters(
                                                                        {path: `${file.path}/JobParameters.json`}
                                                                    );
                                                                } catch (ex) {
                                                                    snackbarStore.addFailure(
                                                                        "Could not find a parameters file for this " +
                                                                        "run. Try a different run."
                                                                    );
                                                                } finally {
                                                                    this.props.setLoading(false);
                                                                }
                                                            }}
                                                        >
                                                            {getFilenameFromPath(file.path)}
                                                        </BaseLink>
                                                    </Box>
                                                ))
                                            }
                                        </Flex>
                                    </RunSection>
                                )}
                            <RunSection>
                                <JobSchedulingOptions
                                    onChange={this.onJobSchedulingParamsChange}
                                    options={schedulingOptions}
                                    reservationRef={this.state.reservation}
                                    app={application}
                                />
                            </RunSection>

                            {mandatoryParams.length === 0 ? null : (
                                <RunSection>
                                    <Heading.h4>Mandatory Parameters ({mandatoryParams.length})</Heading.h4>
                                    {mandatoryParams}
                                </RunSection>
                            )}

                            {visibleParams.length <= 0 ? null : (
                                <RunSection>
                                    <Heading.h4>Additional Parameters Used</Heading.h4>
                                    {visibleParams}
                                </RunSection>
                            )}

                            {
                                !application.invocation.shouldAllowAdditionalMounts ? null : (
                                    <RunSection>
                                        <Flex alignItems={"center"}>
                                            <Box flexGrow={1}>
                                                <Heading.h4>Select additional folders to use</Heading.h4>
                                            </Box>

                                            <Button
                                                type="button"
                                                ml="5px"
                                                lineHeight={"16px"}
                                                onClick={() => this.addFolder()}
                                            >
                                                Add folder
                                            </Button>
                                        </Flex>

                                        <Box mb={8} mt={8}>
                                            {this.state.mountedFolders.length !== 0 ? (
                                                <>
                                                    Your files will be available at <code>/work/</code>.
                                                    You can view changes to your {" "}
                                                    <Link
                                                        to={fileTablePage(Cloud.homeFolder)}
                                                        target={"_blank"}
                                                    >
                                                        files
                                                    </Link> {" "}
                                                    at the end of the job.
                                                </>
                                            ) : (
                                                    <>
                                                        If you need to use your {" "}
                                                        <Link
                                                            to={fileTablePage(Cloud.homeFolder)}
                                                            target={"_blank"}
                                                        >
                                                            files
                                                        </Link>
                                                        {" "}
                                                        in this job then click {" "}
                                                        <BaseLink
                                                            href={"#"}
                                                            onClick={e => {
                                                                e.preventDefault();
                                                                this.addFolder();
                                                            }}>
                                                            "Add folder"
                                                        </BaseLink>
                                                        {" "}
                                                        to select the relevant
                                                        files.
                                                </>
                                                )}
                                        </Box>

                                        {this.state.mountedFolders.every(it => it.readOnly) ? "" :
                                            "Note: Giving folders read/write access will make the startup and shutdown of the application longer."}

                                        {this.state.mountedFolders.map((entry, i) => (
                                            <Box key={i} mb="7px">
                                                <InputDirectoryParameter
                                                    application={application}
                                                    defaultValue={entry.defaultValue}
                                                    initialSubmit={false}
                                                    parameterRef={entry.ref}
                                                    unitWidth={"180px"}
                                                    onRemove={() => {
                                                        this.setState(s => ({
                                                            mountedFolders: removeEntry(s.mountedFolders, i)
                                                        }));
                                                    }}
                                                    parameter={{
                                                        type: ParameterTypes.InputDirectory,
                                                        name: "",
                                                        optional: true,
                                                        title: "",
                                                        description: "",
                                                        defaultValue: "",
                                                        visible: true,
                                                        unitName: (
                                                            <ClickableDropdown
                                                                chevron
                                                                width="180px"
                                                                onChange={key => {
                                                                    const {mountedFolders} = this.state;
                                                                    mountedFolders[i].readOnly = key === "READ";
                                                                    this.setState(() => ({mountedFolders}));
                                                                }}
                                                                trigger={entry.readOnly ?
                                                                    "Read only" : "Read/Write"
                                                                }
                                                                options={[
                                                                    {text: "Read only", value: "READ"},
                                                                    {text: "Read/Write", value: "READ/WRITE"}
                                                                ]}
                                                            />
                                                        ),
                                                    }}
                                                />
                                            </Box>
                                        ))}
                                    </RunSection>
                                )}

                            {!application.invocation.shouldAllowAdditionalPeers ? null : (
                                <RunSection>
                                    <Flex>
                                        <Box flexGrow={1}>
                                            <Heading.h4>Connect to other jobs</Heading.h4>
                                        </Box>
                                        <Button
                                            type={"button"}
                                            lineHeight={"16px"}
                                            onClick={() => this.connectToJob()}
                                        >
                                            Connect to job
                                        </Button>
                                    </Flex>
                                    <Box mb={8} mt={8}>
                                        {this.state.additionalPeers.length !== 0 ? (
                                            <>
                                                You will be able contact the <b>job</b> using its <b>hostname</b>.
                                                File systems used by the <b>job</b> are automatically added to this job.
                                            </>
                                        ) : (
                                                <>
                                                    If you need to use the services of another job click{" "}
                                                    <BaseLink
                                                        href={"#"}
                                                        onClick={e => {
                                                            e.preventDefault();
                                                            this.connectToJob();
                                                        }}>
                                                        "Connect to job".
                                                    </BaseLink>
                                                    {" "}
                                                    These services include networking and shared application file systems.
                                            </>
                                            )}
                                    </Box>

                                    {
                                        this.state.additionalPeers.map((entry, i) => (
                                            <AdditionalPeerParameter
                                                jobIdRef={entry.jobIdRef}
                                                nameRef={entry.nameRef}
                                                onRemove={() => this.setState(s =>
                                                    ({additionalPeers: removeEntry(s.additionalPeers, i)}))
                                                }
                                                hideLabels={i !== 0}
                                                key={i}
                                            />
                                        ))
                                    }
                                </RunSection>
                            )}

                            <RunSection>
                                {optional.length <= 0 ? null : (
                                    <OptionalParameters
                                        parameters={optional}
                                        onUse={p => onParameterChange(p, true)}
                                    />
                                )}
                            </RunSection>
                        </form>
                    </ContainerForText>
                )}
            />
        );
    }

    private async fetchPreviousRuns() {
        if (this.state.application === undefined) return;
        try {
            const previousRuns = await callAPI<Page<CloudFile>>(listDirectory({
                path: Cloud.homeFolder + `Jobs/${this.state.application.metadata.title}`,
                page: 0,
                itemsPerPage: 25,
                attrs: [FileResource.PATH],
                order: SortOrder.DESCENDING,
                sortBy: SortBy.CREATED_AT
            }));
            this.setState(s => ({previousRuns}));
        } catch {
            // Do nothing
        }
    }

    private onJobSchedulingParamsChange = (field: string | number, value: number, timeField: string) => {
        const {schedulingOptions} = this.state;
        if (timeField) {
            schedulingOptions[field][timeField] = !isNaN(value) ? value : null;
        } else {
            schedulingOptions[field] = value;
        }
        this.setState(() => ({schedulingOptions}));
    }

    private onSubmit = async (event: React.FormEvent) => {
        if (!this.state.application) return;
        if (this.state.jobSubmitted) return;
        const {invocation} = this.state.application;
        this.setState(() => ({initialSubmit: true}));

        const parameters = extractValuesFromWidgets({
            map: this.state.parameterValues,
            appParameters: this.state.application!.invocation.parameters,
            cloud: Cloud
        });

        if (!checkForMissingParameters(parameters, invocation)) return;
        if (!validateOptionalFields(invocation, this.state.parameterValues)) return;

        // Validate max time
        const maxTime = extractJobInfo(this.state.schedulingOptions).maxTime;
        if (maxTime.hours === 0 && maxTime.minutes === 0 && maxTime.seconds === 0) {
            snackbarStore.addFailure("Scheduling times must be more than 0 seconds.", 5000);
            return;
        }

        const mounts = this.state.mountedFolders.filter(it => it.ref.current && it.ref.current.value).map(it => {
            const expandedValue = expandHomeFolder(it.ref.current!.value, Cloud.homeFolder);
            return {
                source: expandedValue,
                destination: removeTrailingSlash(expandedValue).split("/").pop()!,
                readOnly: it.readOnly
            };
        });

        {
            // Validate additional mounts
            for (const mount of mounts) {
                if (!mount.readOnly) {
                    const stat = await statFileOrNull(mount.source);
                    if (stat !== null) {
                        if (!allFilesHasAccessRight(AccessRight.WRITE, [stat])) {
                            snackbarStore.addFailure(
                                `Cannot mount ${mount.source} as read/write because share is read-only`,
                                5000
                            );

                            return;
                        }
                    }
                }
            }
        }

        const peers = [] as Array<{name: string, jobId: string}>;
        {
            // Validate additional mounts
            for (const peer of this.state.additionalPeers) {
                const jobIdField = peer.jobIdRef.current;
                const hostnameField = peer.nameRef.current;
                if (jobIdField === null || hostnameField === null) continue; // This should not happen

                const jobId = jobIdField.value;
                const hostname = hostnameField.value;

                if (hostname === "" && jobId === "") continue;
                if (hostname === "" || jobId === "") {
                    snackbarStore.addFailure(
                        "A connection is missing a job or hostname",
                        5000
                    );

                    return;
                }

                if (!hostnameRegex.test(hostname)) {
                    snackbarStore.addFailure(
                        `The connection '${hostname}' has an invalid hostname.` +
                        `Hostnames cannot contain spaces or special characters.`,
                        5000
                    );
                    return;
                }

                peers.push({name: hostname, jobId});
            }
        }

        const {name} = this.state.schedulingOptions;
        const jobName = name.current && name.current.value;
        let reservation = this.state.reservation.current ? this.state.reservation.current.value : null;
        if (reservation === "") reservation = null;

        const job = {
            application: {
                name: this.state.application!.metadata.name,
                version: this.state.application!.metadata.version
            },
            parameters,
            numberOfNodes: this.state.schedulingOptions.numberOfNodes,
            tasksPerNode: this.state.schedulingOptions.tasksPerNode,
            maxTime,
            mounts,
            peers,
            reservation,
            type: "start",
            name: !!jobName ? jobName : null
        };

        try {
            this.setState({jobSubmitted: true});
            this.props.setLoading(true);
            const req = await Cloud.post(hpcJobQueryPost, job);
            this.props.history.push(`/applications/results/${req.response.jobId}`);
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "An error ocurred submitting the job."));
            this.setState(() => ({jobSubmitted: false}));
        } finally {
            this.props.setLoading(false);
        }
    }

    private async toggleFavorite() {
        if (!this.state.application) return;
        const {name, version} = this.state.application.metadata;
        this.setState(() => ({favoriteLoading: true}));
        try {
            await this.state.promises.makeCancelable(Cloud.post(hpcFavoriteApp(name, version))).promise;
            this.setState(() => ({favorite: !this.state.favorite}));
        } catch (e) {
            snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred"));
        } finally {
            this.setState(() => ({favoriteLoading: false}));
        }
    }

    private async retrieveApplication(name: string, version: string): Promise<void> {
        try {
            this.props.setLoading(true);
            const {response} = await this.state.promises.makeCancelable(
                Cloud.get<FullAppInfo>(`/hpc/apps/${encodeURI(name)}/${encodeURI(version)}`)
            ).promise;
            const app = response;
            const toolDescription = app.invocation.tool.tool.description;
            const parameterValues = new Map<string, React.RefObject<HTMLInputElement | HTMLSelectElement | RangeRef>>();

            app.invocation.parameters.forEach(it => {
                if (Object.values(ParameterTypes).includes(it.type)) {
                    parameterValues.set(it.name, React.createRef<HTMLInputElement>());
                } else if (it.type === "boolean") {
                    parameterValues.set(it.name, React.createRef<HTMLSelectElement>());
                } else if (it.type === "range") {
                    parameterValues.set(it.name, React.createRef<RangeRef>());
                }
            });
            this.setState(() => ({
                application: app,
                favorite: app.favorite,
                parameterValues,
                schedulingOptions: {
                    maxTime: toolDescription.defaultTimeAllocation,
                    numberOfNodes: toolDescription.defaultNumberOfNodes,
                    tasksPerNode: toolDescription.defaultTasksPerNode,
                    name: this.state.schedulingOptions.name
                }
            }));
        } catch (e) {
            snackbarStore.addFailure(errorMessageOrDefault(e, `An error occurred fetching ${name}`));
        } finally {
            this.props.setLoading(false);
        }
    }

    private importParameters(file: File) {
        const thisApp = this.state.application;
        if (!thisApp) return;

        const fileReader = new FileReader();
        fileReader.onload = async () => {
            const rawInputFile = fileReader.result as string;
            try {
                const {
                    application,
                    parameters,
                    numberOfNodes,
                    mountedFolders,
                    tasksPerNode,
                    maxTime,
                    siteVersion
                } = JSON.parse(rawInputFile);
                // Verify metadata
                if (application.name !== thisApp.metadata.name) {
                    snackbarStore.addSnack({
                        message: "Application name does not match",
                        type: SnackType.Failure
                    });
                    return;
                } else if (application.version !== thisApp.metadata.version) {
                    snackbarStore.addSnack({
                        message: "Application version does not match. Some parameters may not be filled out correctly.",
                        type: SnackType.Information
                    });
                }

                // Finds the values to parameters that are still valid in this version of the application.
                const userInputValues = findKnownParameterValues({
                    nameToValue: parameters,
                    allowedParameterKeys: thisApp.invocation.parameters.map(it => ({
                        name: it.name, type: it.type
                    })),
                    siteVersion
                });

                const parametersFromUser = Object.keys(userInputValues);

                {
                    // Remove invalid input files from userInputValues
                    const fileParams = thisApp.invocation.parameters.filter(p => isFileOrDirectoryParam(p));
                    const invalidFiles: string[] = [];
                    for (const paramKey in fileParams) {
                        const param = fileParams[paramKey];
                        if (!!userInputValues[param.name]) {
                            const path = expandHomeFolder(userInputValues[param.name], Cloud.homeFolder);
                            if (!await checkIfFileExists(path, Cloud)) {
                                invalidFiles.push(userInputValues[param.name]);
                                delete userInputValues[param.name];
                            }
                        }
                    }

                    if (invalidFiles.length > 0) {
                        snackbarStore.addSnack({
                            message: `Extracted files don't exists: ${invalidFiles.join(", ")}`,
                            type: SnackType.Failure
                        });
                    }
                }

                {
                    // Verify and load additional mounts
                    const validMountFolders = [] as AdditionalMountedFolder[];
                    // tslint:disable-next-line:prefer-for-of
                    for (let i = 0; i < mountedFolders.length; i++) {
                        if (await checkIfFileExists(expandHomeFolder(mountedFolders[i].ref, Cloud.homeFolder), Cloud)) {
                            const ref = React.createRef<HTMLInputElement>();
                            validMountFolders.push({ref, readOnly: mountedFolders[i].readOnly});
                        }
                    }

                    this.setState(() => ({mountedFolders: this.state.mountedFolders.concat(validMountFolders)}));
                    const emptyMountedFolders = this.state.mountedFolders.slice(
                        this.state.mountedFolders.length - mountedFolders.length
                    );
                    emptyMountedFolders.forEach((it, index) => it.ref.current!.value = mountedFolders[index].ref);
                }

                {
                    // Make sure all the fields we are using are visible
                    parametersFromUser.forEach(key =>
                        thisApp.invocation.parameters.find(it => it.name === key)!.visible = true
                    );
                }

                {
                    // Initialize widget values
                    parametersFromUser.forEach(key => {
                        thisApp.invocation.parameters.find(it => it.name === key)!.visible = true;
                        const ref = this.state.parameterValues.get(key);
                        if (ref && ref.current) {
                            if ("value" in ref.current) ref.current.value = userInputValues[key];
                            else (ref.current.setState(() => ({bounds: userInputValues[key] as any})));
                            this.state.parameterValues.set(key, ref);
                        }
                    });
                }

                this.setState(() => ({
                    application: thisApp,
                    schedulingOptions: extractJobInfo({
                        maxTime,
                        numberOfNodes,
                        tasksPerNode,
                        name: this.state.schedulingOptions.name
                    })
                }));
            } catch (e) {
                console.warn(e);
                snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred"));
            }
        };
        fileReader.readAsText(file);
    }

    private onImportFileSelected(file: {path: string}) {
        if (!file.path.endsWith(".json")) {
            addStandardDialog({
                title: "Continue?",
                message: "The selected file's extension is not \"json\" which is the required format.",
                confirmText: "Continue",
                onConfirm: () => this.fetchAndImportParameters(file)
            });
            return;
        }
        this.fetchAndImportParameters(file);
    }

    private fetchAndImportParameters = async (file: {path: string}) => {
        const fileStat = await Cloud.get<CloudFile>(statFileQuery(file.path));
        if (fileStat.response.size! > 5_000_000) {
            snackbarStore.addFailure("File size exceeds 5 MB. This is not allowed not allowed.");
            return;
        }
        const response = await fetchFileContent(file.path, Cloud);
        if (response.ok) this.importParameters(new File([await response.blob()], "params"));
    }

    private addFolder() {
        this.setState(s => ({
            mountedFolders: s.mountedFolders.concat([
                {
                    ref: React.createRef<HTMLInputElement>(),
                    readOnly: true
                }
            ])
        }));
    }

    private connectToJob() {
        this.setState(s => ({
            additionalPeers: s.additionalPeers.concat([{
                jobIdRef: React.createRef(),
                nameRef: React.createRef()
            }])
        }));
    }
}

const RunSection = styled(Box)`
    margin-bottom: 32px;
    margin-top: 16px;
`;

interface SchedulingFieldProps {
    text: string;
    field: string;
    subField?: string;
    onChange: (field: string, value: number, subField?: string) => void;
    value?: number;
    defaultValue?: number;
    min?: number;
    max?: number;
}

const SchedulingField: React.FunctionComponent<SchedulingFieldProps> = props => (
    <Label>
        {props.text}

        <Input
            type="number"
            step="1"
            min={props.min}
            max={props.max}
            value={props.value == null || isNaN(props.value) ? "0" : props.value}
            placeholder={`${props.defaultValue}`}
            onChange={({target: {value}}) => {
                const parsed = parseInt(value, 10);
                props.onChange(props.field, parsed, props.subField);
            }}
        />
    </Label>
);

interface JobSchedulingOptionsProps {
    onChange: (a, b, c) => void;
    options: JobSchedulingOptionsForInput;
    app: WithAppMetadata & WithAppInvocation;
    reservationRef: React.RefObject<HTMLInputElement>;
}

const JobSchedulingOptions = (props: JobSchedulingOptionsProps) => {
    if (!props.app) return null;
    const {maxTime, numberOfNodes, tasksPerNode, name} = props.options;

    return (
        <>
            <Flex mb="4px" mt="4px">
                <Label>
                    Job name
                    <Input ref={name} placeholder={"Example: Analysis with parameters XYZ"} />
                </Label>
            </Flex>

            <Flex mb="1em">
                <SchedulingField
                    min={0}
                    max={200}
                    field="maxTime"
                    subField="hours"
                    text="Hours"
                    value={maxTime.hours}
                    onChange={props.onChange}
                />
                <Box ml="4px" />
                <SchedulingField
                    min={0}
                    max={59}
                    field="maxTime"
                    subField="minutes"
                    text="Minutes"
                    value={maxTime.minutes}
                    onChange={props.onChange}
                />
                <Box ml="4px" />
                <SchedulingField
                    min={0}
                    max={59}
                    field="maxTime"
                    subField="seconds"
                    text="Seconds"
                    value={maxTime.seconds}
                    onChange={props.onChange}
                />
            </Flex>

            {!props.app.invocation.allowMultiNode ? null : (
                <Flex mb="1em">
                    <SchedulingField
                        min={1}
                        field="numberOfNodes"
                        text="Number of Nodes"
                        value={numberOfNodes}
                        onChange={props.onChange}
                    />
                </Flex>
            )}

            <Box>
                <Label>Machine type</Label>
                <MachineTypes inputRef={props.reservationRef} />
            </Box>
        </>
    );
};

function extractJobInfo(jobInfo: JobSchedulingOptionsForInput): JobSchedulingOptionsForInput {
    const extractedJobInfo = {
        maxTime: {hours: 0, minutes: 0, seconds: 0},
        numberOfNodes: 1,
        tasksPerNode: 1,
        name: jobInfo.name
    };
    const {maxTime, numberOfNodes, tasksPerNode} = jobInfo;
    extractedJobInfo.maxTime.hours = Math.abs(maxTime.hours);
    extractedJobInfo.maxTime.minutes = Math.abs(maxTime.minutes);
    extractedJobInfo.maxTime.seconds = Math.abs(maxTime.seconds);
    extractedJobInfo.numberOfNodes = numberOfNodes;
    extractedJobInfo.tasksPerNode = tasksPerNode;
    return extractedJobInfo;
}

const mapDispatchToProps = (dispatch: Dispatch): RunOperations => ({
    updatePageTitle: () => dispatch(updatePageTitle("Run Application")),
    setLoading: loading => dispatch(setLoading(loading))
});

export default connect(null, mapDispatchToProps)(Run);

export function importParameterDialog(importParameters: (file: File) => void, showFileSelector: () => void) {
    dialogStore.addDialog((
        <Box>
            <Box>
                <Button fullWidth as="label">
                    Upload file
                <HiddenInputField
                        type="file"
                        onChange={e => {
                            if (e.target.files) {
                                const file = e.target.files[0];
                                if (file.size > 10_000_000) {
                                    snackbarStore.addFailure("File exceeds 10 MB. Not allowed.");
                                } else {
                                    importParameters(file);
                                }
                                dialogStore.success();
                            }
                        }} />
                </Button>
                <Button mt="6px" fullWidth onClick={() => (dialogStore.success(), showFileSelector())}>
                    Select file from SDUCloud
            </Button>
            </Box>
            <Flex mt="20px">
                <Button onClick={() => dialogStore.success()} color="red" mr="5px">Cancel</Button>
            </Flex>
        </Box>
    ), () => undefined);
}
