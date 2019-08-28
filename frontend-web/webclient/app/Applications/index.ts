import {AnalysisReduxObject, ResponsiveReduxObject} from "DefaultObjects";
import {SortOrder} from "Files";
import {History} from "history";
import {SetStatusLoading} from "Navigation/Redux/StatusActions";
import PromiseKeeper from "PromiseKeeper";
import * as React from "react";
import {SharedFileSystemMount} from "Applications/FileSystems";
import {match} from "react-router";
import {ParameterValues} from "Utilities/ApplicationUtilities";

export interface Analysis {
    name: string;
    checked?: boolean;
    status: string;
    state: AppState;
    jobId: string;
    appName: string;
    appVersion: string;
    createdAt: number;
    modifiedAt: number;
    expiresAt?: number;
    owner: string;
    metadata: ApplicationMetadata
}

export type AnalysesStateProps = AnalysisReduxObject & {responsive: ResponsiveReduxObject}
export type AnalysesProps = AnalysesStateProps & AnalysesOperations;

type FetchJobsOperation = (
    itemsPerPage: number,
    pageNumber: number,
    sortOrder: SortOrder,
    sortBy: RunsSortBy,
    minTimestamp?: number,
    maxTimestamp?: number,
    filter?: AppState
) => void;

export interface AnalysesOperations {
    setLoading: (loading: boolean) => void;
    fetchJobs: FetchJobsOperation;
    onInit: () => void;
    setRefresh: (refresh?: () => void) => void;
    checkAnalysis: (jobId: string, checked: boolean) => void;
    checkAllAnalyses: (checked: boolean) => void;
}

export interface DetailedResultOperations {
    setPageTitle: (jobId: string) => void;
    setLoading: (loading: boolean) => void;
    setRefresh: (refresh?: () => void) => void;
}

export interface DetailedResultProps extends DetailedResultOperations {
    match: match<{jobId: string}>;
    history: History;
}

export interface Application {
    favorite: boolean;
    owner: string;
    createdAt: number;
    modifiedAt: number;
    description: ApplicationDescription;
    tool: ApplicationTool;
    imageUrl: string;
}

interface ApplicationTool {
    owner: string;
    createdAt: number;
    modifiedAt: number;
    description: {
        info: ApplicationInfo
        container: string
        defaultNumberOfNodes: number
        defaultTasksPerNode: number
        defaultAllocationTime: MaxTime
        requiredModules: string[]
        authors: string[]
        title: string
        description: string
        backend: string
        license: string
    };
}

interface ApplicationInfo {
    name: string;
    version: string;
}

export interface ApplicationDescription {
    info: ApplicationInfo;
    tool: ApplicationInfo;
    authors: string[];
    title: string;
    description: string;
    invocation: any[]; // FIXME: Add type
    parameters: ApplicationParameter[];
    outputFileGlobs: string[];
    website?: string;
    resources: {multiNodeSupport: boolean};
    tags: string[];
}

export enum AppState {
    VALIDATED = "VALIDATED",
    PREPARED = "PREPARED",
    SCHEDULED = "SCHEDULED",
    RUNNING = "RUNNING",
    TRANSFER_SUCCESS = "TRANSFER_SUCCESS",
    SUCCESS = "SUCCESS",
    FAILURE = "FAILURE",
    CANCELLING = "CANCELLING"
}

export interface DetailedResultState {
    name: string;
    complete: boolean;
    appState: AppState;
    failedState?: AppState;
    status: string;
    app?: ApplicationMetadata;
    stdout: string;
    stderr: string;
    stdoutLine: number;
    stderrLine: number;
    reloadIntervalId: number;
    promises: PromiseKeeper;
    outputFolder?: string;
    appType?: ApplicationType;
    webLink?: string;
    timeLeft: number | null;
}

export type StdElement = {scrollTop: number, scrollHeight: number} | null

export interface MaxTime {
    hours: number;
    minutes: number;
    seconds: number;
}

export interface MaxTimeForInput {
    hours: number;
    minutes: number;
    seconds: number;
}

export interface JobSchedulingOptionsForInput {
    maxTime: MaxTimeForInput;
    numberOfNodes: number;
    tasksPerNode: number;
    name: React.RefObject<HTMLInputElement>;
}

export interface AdditionalMountedFolder {
    readOnly: boolean;
    ref: React.RefObject<HTMLInputElement>;
    defaultValue?: string;
}

export interface AdditionalPeer {
    nameRef: React.RefObject<HTMLInputElement>;
    jobIdRef: React.RefObject<HTMLInputElement>;
}

export interface RunAppState {
    promises: PromiseKeeper;
    jobSubmitted: boolean;
    initialSubmit: boolean;
    application?: FullAppInfo;
    parameterValues: ParameterValues;
    schedulingOptions: JobSchedulingOptionsForInput;
    favorite: boolean;
    favoriteLoading: boolean;
    mountedFolders: AdditionalMountedFolder[];
    additionalPeers: AdditionalPeer[];
    fsShown: boolean;
    sharedFileSystems: { mounts: SharedFileSystemMount[] };
}

export interface RunOperations extends SetStatusLoading {
    updatePageTitle: () => void;
}

export interface RunAppProps extends RunOperations {
    match: match<{appName: string, appVersion: string}>;
    history: History;
    updatePageTitle: () => void;
}

export interface NumberParameter extends BaseParameter {
    defaultValue: {value: number, type: "double" | "int"} | null;
    min: number | null;
    max: number | null;
    step: number | null;
    type: ParameterTypes.Integer | ParameterTypes.FloatingPoint;
}

export interface BooleanParameter extends BaseParameter {
    defaultValue: {value: boolean, type: "bool"} | null;
    trueValue?: string | null;
    falseValue?: string | null;
    type: ParameterTypes.Boolean;
}

export interface InputFileParameter extends BaseParameter {
    defaultValue: string | null;
    type: ParameterTypes.InputFile;
}

export interface InputDirectoryParameter extends BaseParameter {
    defaultValue: string | null;
    type: ParameterTypes.InputDirectory;
}

export interface TextParameter extends BaseParameter {
    defaultValue: {value: string, type: "string"} | null;
    type: ParameterTypes.Text;
}

export interface PeerParameter extends BaseParameter {
    suggestedApplication: string | null
    type: ParameterTypes.Peer
}

export interface SharedFileSystemParameter extends BaseParameter {
    fsType: "EPHEMERAL" | "PERSISTENT"
    mountLocation: string
    exportToPeers: boolean
    type: ParameterTypes.SharedFileSystem
}

interface BaseParameter {
    name: string;
    optional: boolean;
    title: string;
    description: string;
    unitName?: string | React.ReactNode | null;
    type: string;
    visible?: boolean;
}

export type ApplicationParameter = 
	InputFileParameter | 
	InputDirectoryParameter | 
	NumberParameter | 
	BooleanParameter |
    TextParameter | 
    PeerParameter | 
    SharedFileSystemParameter;

type Invocation = WordInvocation | VarInvocation;

interface WordInvocation {
    type: "word";
    word: string;
}

interface VarInvocation {
    type: "var";
    variableNames: string[];
    prefixGlobal: string;
    suffixGlobal: string;
    prefixVariable: string;
    suffixVariable: string;
    variableSeparator: string;
}

interface Info {name: string; version: string;}
export interface Description {
    info: Info;
    tool: Info;
    authors: string[];
    title: string;
    description: string;
    invocation: Invocation[];
    parameters: ApplicationParameter[];
    outputFileGlobs: [string, string];
    tags: string[];
}

export enum ParameterTypes {
    InputFile = "input_file",
    InputDirectory = "input_directory",
    Integer = "integer",
    FloatingPoint = "floating_point",
    Text = "text",
    Boolean = "boolean",
    Peer = "peer",
    SharedFileSystem = "shared_file_system"
}

export interface SearchFieldProps {
    onSubmit: () => void;
    icon: string;
    placeholder: string;
    value: string;
    loading: boolean;
    onValueChange: (value: string) => void;
}

export interface DetailedApplicationSearchReduxState {
    hidden: boolean;
    appName: string;
    appVersion: string;
    tags: string;
    error?: string;
    loading: boolean;
}

export interface DetailedApplicationOperations {
    setAppName: (n: string) => void;
    setVersionName: (v: string) => void;
    fetchApplicationsFromName: (q: string, i: number, p: number, c?: Function) => void;
    fetchApplicationsFromTag: (t: string, i: number, p: number, c?: Function) => void;
}



// New interfaces
export interface ApplicationMetadata {
    name: string;
    version: string;
    authors: string[];
    title: string;
    description: string;
    tags: string[];
    website?: string;
}

type ApplicationType = "BATCH" | "VNC" | "WEB"

export interface ApplicationInvocationDescription {
    tool: Tool;
    invocation: Invocation[];
    parameters: ApplicationParameter[];
    outputFileGlobs: string[];
    applicationType: ApplicationType;
    shouldAllowAdditionalMounts: boolean
    shouldAllowAdditionalPeers: boolean
    allowMultiNode: boolean
}

interface Resources {
    multiNodeSupport: boolean;
    coreRequirements: number;
    memoryRequirementsMb: number;
    gpuRequirements: number;
    tempStorageRequirementsGb: number;
    persistentStorageRequirementsGb: number;
}

interface Tool {
    name: string;
    version: string;
    tool: ToolReference;
}

interface ToolReference {
    owner: string;
    createdAt: number;
    modifiedAt: number;
    description: ToolDescription;
}

interface NameAndVersion {
    name: string;
    version: string;
}

interface ToolDescription {
    info: NameAndVersion;
    container: string;
    defaultNumberOfNodes: number;
    defaultTasksPerNode: number;
    defaultTimeAllocation: MaxTime;
    requiredModules: string[];
    authors: string[];
    title: string;
    description: string;
    backend: string;
    license: string;
}

export interface WithAppMetadata {
    metadata: ApplicationMetadata;
}

export interface WithAppInvocation {
    invocation: ApplicationInvocationDescription;
}

export interface WithAppFavorite {
    favorite: boolean;
}

export enum RunsSortBy {
    state = "STATE",
    application = "APPLICATION",
    startedAt = "STARTED_AT",
    lastUpdate = "LAST_UPDATE",
    createdAt = "CREATED_AT",
    name = "NAME"
}
export interface WithAllAppTags {
    tags: string[];
}

export type FullAppInfo = WithAppFavorite & WithAppInvocation & WithAppMetadata & WithAllAppTags;
