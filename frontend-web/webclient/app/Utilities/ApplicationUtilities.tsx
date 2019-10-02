import {
    ApplicationInvocationDescription,
    ApplicationMetadata,
    ApplicationParameter,
    JobState,
    ParameterTypes,
    RunsSortBy
} from "Applications";
import {RangeRef} from "Applications/Widgets/RangeParameters";
import Cloud from "Authentication/lib";
import {SortOrder} from "Files";
import * as React from "react";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Page} from "Types";
import {addStandardDialog} from "UtilityComponents";
import {errorMessageOrDefault, removeTrailingSlash} from "UtilityFunctions";
import {expandHomeFolder} from "./FileUtilities";

export const hpcJobQueryPost = "/hpc/jobs";

export const hpcJobQuery = (id: string) => `/hpc/jobs/${encodeURIComponent(id)}`;

export function hpcJobsQuery(
    itemsPerPage: number,
    page: number,
    sortOrder?: SortOrder,
    sortBy?: RunsSortBy,
    minTimestamp?: number,
    maxTimestamp?: number,
    filter?: JobState
): string {
    let query = `/hpc/jobs/?itemsPerPage=${itemsPerPage}&page=${page}`;
    if (sortOrder) query = query.concat(`&order=${sortOrder}`);
    if (sortBy) query = query.concat(`&sortBy=${sortBy}`);
    if (minTimestamp != null) query = query.concat(`&minTimestamp=${minTimestamp}`);
    if (maxTimestamp != null) query = query.concat(`&maxTimestamp=${maxTimestamp}`);
    if (filter != null) query = query.concat(`&filter=${filter}`);
    return query;
}

export function advancedSearchQuery(): string {
    return "/hpc/apps/advancedSearch";
}

export const hpcFavoriteApp = (name: string, version: string) =>
    `/hpc/apps/favorites/${encodeURIComponent(name)}/${encodeURIComponent(version)}`;

export const hpcFavorites = (itemsPerPage: number, pageNumber: number) =>
    `/hpc/apps/favorites?itemsPerPage=${itemsPerPage}&page=${pageNumber}`;

export const hpcApplicationsQuery = (page: number, itemsPerPage: number) =>
    `/hpc/apps?page=${page}&itemsPerPage=${itemsPerPage}`;

interface HPCApplicationsSearchQuery {
    query: string;
    page: number;
    itemsPerPage: number;
}

export const hpcApplicationsTagSearchQuery = ({query, page, itemsPerPage}: HPCApplicationsSearchQuery): string =>
    `/hpc/apps/searchTags?query=${encodeURIComponent(query)}&page=${page}&itemsPerPage=${itemsPerPage}`;

export const cancelJobQuery = `hpc/jobs`;

export const cancelJobDialog = (
    {jobId, onConfirm, jobCount = 1}: {
        jobCount?: number,
        jobId: string,
        onConfirm: () => void
    }
): void =>
    addStandardDialog({
        title: `Cancel job${jobCount > 1 ? "s" : ""}?`,
        message: jobCount === 1 ? `Cancel job: ${jobId}?` : "Cancel jobs?",
        cancelText: "No",
        confirmText: `Cancel job${jobCount > 1 ? "s" : ""}`,
        onConfirm
    });

export const cancelJob = (cloud: Cloud, jobId: string): Promise<{request: XMLHttpRequest, response: void}> =>
    cloud.delete(cancelJobQuery, {jobId});

interface FavoriteApplicationFromPage<T> {
    name: string;
    version: string;
    page: Page<{metadata: ApplicationMetadata, favorite: boolean} & T>;
    cloud: Cloud;
}

/**
 * Favorites an application.
 * @param {Application} Application the application to be favorited
 * @param {Cloud} cloud The cloud instance for requests
 */
export async function favoriteApplicationFromPage<T>(
    {
        name,
        version,
        page,
        cloud
    }: FavoriteApplicationFromPage<T>
): Promise<Page<T>> {
    const a = page.items.find(it => it.metadata.name === name && it.metadata.version === version)!;
    try {
        await cloud.post(hpcFavoriteApp(name, version));
        a.favorite = !a.favorite;
    } catch (e) {
        snackbarStore.addFailure(errorMessageOrDefault(e, `An error ocurred favoriting ${name}`));
    }
    return page;
}


interface StringMap {
    [k: string]: string;
}

interface AllowedParameterKey {
    name: string;
    type: ParameterTypes;
}

interface ExtractParameters {
    nameToValue: StringMap;
    allowedParameterKeys: AllowedParameterKey[];
    siteVersion: number;
}

export const findKnownParameterValues = (
    {
        nameToValue,
        allowedParameterKeys,
        siteVersion
    }: ExtractParameters
): StringMap => {
    const extractedParameters = {};
    if (siteVersion === 1) {
        allowedParameterKeys.forEach(({name, type}) => {
            if (nameToValue[name] !== undefined) {
                if (typeMatchesValue(type, nameToValue[name])) {
                    extractedParameters[name] = nameToValue[name];
                }
            }
        });
    }
    return extractedParameters;
};

export const isFileOrDirectoryParam = ({type}: {type: string}) => type === "input_file" || type === "input_directory";

const typeMatchesValue = (type: ParameterTypes, parameter: string | [number, number]): boolean => {
    switch (type) {
        case ParameterTypes.Boolean:
            return parameter === "Yes" || parameter === "No" || parameter === "";
        case ParameterTypes.Integer:
            return parseInt(parameter as string, 10) % 1 === 0;
        case ParameterTypes.FloatingPoint:
            return typeof parseFloat(parameter as string) === "number";
        case ParameterTypes.Range:
            return typeof parameter === "object" && "size" in parameter;
        case ParameterTypes.Text:
        case ParameterTypes.InputDirectory:
        case ParameterTypes.InputFile:
        case ParameterTypes.SharedFileSystem:
        case ParameterTypes.Peer:
            return typeof parameter === "string";

    }
};

interface ExtractedParameters {
    [key: string]: string | number | boolean |
    {source: string, destination: string;} |
    {min: number, max: number} |
    {fileSystemId: string} |
    {jobId: string};
}

export type ParameterValues = Map<string, React.RefObject<HTMLInputElement | HTMLSelectElement | RangeRef>>;

interface ExtractParametersFromMap {
    map: ParameterValues;
    appParameters: ApplicationParameter[];
    cloud: Cloud;
}

export function extractValuesFromWidgets({map, appParameters, cloud}: ExtractParametersFromMap): ExtractedParameters {
    const extracted: ExtractedParameters = {};
    map.forEach((r, key) => {
        const parameter = appParameters.find(it => it.name === key);
        if (!r.current) return;
        if (("value" in r.current && !r.current.value) || ("checkValidity" in r.current && !r.current.checkValidity())) return;
        if (!parameter) return;
        if ("value" in r.current) {
            switch (parameter.type) {
                case ParameterTypes.InputDirectory:
                case ParameterTypes.InputFile:
                    const expandedValue = expandHomeFolder(r.current.value, cloud.homeFolder);
                    extracted[key] = {
                        source: expandedValue,
                        destination: removeTrailingSlash(expandedValue).split("/").pop()!
                    };
                    return;
                case ParameterTypes.Boolean:
                    switch (r.current.value) {
                        case "Yes":
                            extracted[key] = true;
                            return;
                        case "No":
                            extracted[key] = false;
                            return;
                        default:
                            return;
                    }
                case ParameterTypes.Integer:
                    extracted[key] = parseInt(r.current.value, 10);
                    return;
                case ParameterTypes.FloatingPoint:
                    extracted[key] = parseFloat(r.current.value);
                    return;
                case ParameterTypes.Text:
                    extracted[key] = r.current.value;
                    return;
                case ParameterTypes.SharedFileSystem:
                    extracted[key] = {fileSystemId: r.current.value};
                    return;
                case ParameterTypes.Peer:
                    extracted[key] = {jobId: r.current.value};
                    return;
            }
        } else {
            switch (parameter.type) {
                case ParameterTypes.Range:
                    const {bounds} = r.current.state;
                    extracted[key] = {min: bounds[0], max: bounds[1]};
                    return;
            }
        }
    });
    return extracted;
}

export const inCancelableState = (state: JobState) =>
    state === JobState.VALIDATED ||
    state === JobState.PREPARED ||
    state === JobState.SCHEDULED ||
    state === JobState.RUNNING;


export function validateOptionalFields(
    invocation: ApplicationInvocationDescription,
    parameters: ParameterValues
): boolean {
    const optionalErrors = [] as string[];
    const optionalParams = invocation.parameters.filter(it => it.optional && it.visible).map(it =>
        ({name: it.name, title: it.title})
    );
    optionalParams.forEach(it => {
        const {current} = parameters.get(it.name)!;
        if (current == null || !("checkValidity" in current)) return;
        if (("checkValidity" in current! && !current!.checkValidity())) optionalErrors.push(it.title);
    });

    if (optionalErrors.length > 0) {
        snackbarStore.addFailure(
            `Invalid values for ${optionalErrors.slice(0, 3).join(", ")}
                    ${optionalErrors.length > 3 ? `and ${optionalErrors.length - 3} others` : ""}`,
            5000
        );
        return false;
    }

    return true;
}

export function checkForMissingParameters(
    parameters: ExtractedParameters,
    invocation: ApplicationInvocationDescription
): boolean {
    const PT = ParameterTypes;
    const requiredParams = invocation.parameters.filter(it => !it.optional);
    const missingParameters: string[] = [];
    requiredParams.forEach(rParam => {
        const parameterValue = parameters[rParam.name];
        if (parameterValue == null) missingParameters.push(rParam.title);
        else if ([PT.Boolean, PT.FloatingPoint, PT.Integer, PT.Text].includes[rParam.type] &&
            !["number", "string", "boolean"].includes(typeof parameterValue)) {
            missingParameters.push(rParam.title);
        } else if (rParam.type === ParameterTypes.InputDirectory || rParam.type === ParameterTypes.InputFile) {
            if (!parameterValue["source"]) {
                missingParameters.push(rParam.title);
            }
        } else if (rParam.type === ParameterTypes.SharedFileSystem) {
            if (!parameterValue["fileSystemId"]) {
                missingParameters.push(rParam.title);
            }
        }
    });

    // Check missing values for required input fields.
    if (missingParameters.length > 0) {
        snackbarStore.addFailure(
            `Missing values for ${missingParameters.slice(0, 3).join(", ")}
                ${missingParameters.length > 3 ? `and ${missingParameters.length - 3} others.` : ``}`,
            5000
        );
        return false;
    }
    return true;
}
