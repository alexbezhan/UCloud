import {Cloud as currentCloud} from "Authentication/SDUCloudObject";
import {SensitivityLevel} from "DefaultObjects";
import {Acl, File, FileType, SortBy} from "Files";
import {SnackType} from "Snackbar/Snackbars";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {dateToString} from "Utilities/DateUtilities";
import {getFilenameFromPath, isDirectory, replaceHomeFolder, sizeToString} from "Utilities/FileUtilities";
import {HTTP_STATUS_CODES} from "Utilities/XHRUtils";

/**
 * Sets theme based in input. Either "light" or "dark".
 * @param {boolean} isLightTheme Signifies if the currently selected theme is "light".
 */

export const setSiteTheme = (isLightTheme: boolean): void => {
    const lightTheme = isLightTheme ? "light" : "dark";
    window.localStorage.setItem("theme", lightTheme);
};

/**
 * Returns whether or not the value "light", "dark" or null is stored. 
 * @returns {boolean} True if "light" or null is stored, otherwise "dark".
 */
export const isLightThemeStored = (): boolean => {
    const theme = window.localStorage.getItem("theme");
    if (theme === "dark") return false;
    else return true;
};

/**
 * Capitalizes the input string
 * @param str string to be lowercased and capitalized
 * @return {string}
 */
export const capitalized = (str: string): string => str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();

/**
 * Returns a string based on the amount of users associated with the ACL
 * @param {Acl[]} acls - the list of access controls
 * @return {string}
 */
export const getMembersString = (acls?: Acl[]): string => {
    if (acls === undefined) return "N/A";
    const filteredAcl = acls.filter(it => it.entity !== currentCloud.activeUsername);
    if (filteredAcl.length > 0) {
        return `${acls.length + 1} members`;
    } else {
        return "Only You";
    }
};

export function sortingColumnToValue(sortBy: SortBy, file: File): string {
    switch (sortBy) {
        case SortBy.FILE_TYPE:
            return capitalized(file.fileType);
        case SortBy.PATH:
            return getFilenameFromPath(file.path);
        case SortBy.CREATED_AT:
            return dateToString(file.createdAt!);
        case SortBy.MODIFIED_AT:
            return dateToString(file.modifiedAt!);
        case SortBy.SIZE:
            return sizeToString(file.size!);
        case SortBy.ACL:
            if (file.acl !== null)
                return getMembersString(file.acl);
            else
                return "";
        case SortBy.SENSITIVITY_LEVEL:
            return SensitivityLevel[file.sensitivityLevel!];
    }
}

export const extensionTypeFromPath = (path: string) => extensionType(extensionFromPath(path));
export const extensionFromPath = (path: string): string => {
    const splitString = path.split(".");
    return splitString[splitString.length - 1];
};

type ExtensionType = null | "code" | "image" | "text" | "audio" | "video" | "archive" | "pdf" | "binary"
export const extensionType = (ext: string): ExtensionType => {
    switch (ext) {
        case "md":
        case "swift":
        case "kt":
        case "kts":
        case "js":
        case "jsx":
        case "ts":
        case "tsx":
        case "java":
        case "py":
        case "python":
        case "tex":
        case "r":
        case "c":
        case "h":
        case "cc":
        case "hh":
        case "c++":
        case "h++":
        case "hpp":
        case "cpp":
        case "cxx":
        case "hxx":
        case "html":
        case "lhs":
        case "hs":
        case "sql":
        case "sh":
        case "iol":
        case "ol":
        case "col":
        case "bib":
        case "toc":
        case "jar":
        case "exe":
            return "code";
        case "png":
        case "gif":
        case "tiff":
        case "eps":
        case "ppm":
        case "svg":
        case "jpg":
            return "image";
        case "txt":
        case "xml":
        case "json":
        case "csv":
        case "yml":
        case "plist":
            return "text";
        case "pdf":
            return "pdf";
        case "wav":
        case "mp3":
        case "ogg":
        case "aac":
        case "pcm":
        case "aac":
            return "audio";
        case "mpg":
        case "mp4":
        case "avi":
        case "mov":
        case "wmv": 
            return "video";
        case "gz":
        case "zip":
        case "tar":
        case "tgz":
        case "tbz":
        case "bz2":
            return "archive";
        case "dat":
            return "binary";
        default:
            return null;
    }
};

export interface FtIconProps {
    type: FileType;
    ext?: string;
}

export const iconFromFilePath = (filePath: string, type: FileType, homeFolder: string): FtIconProps => {
    const icon: FtIconProps = {type: "FILE"};
    if (isDirectory({fileType: type})) {
        const homeFolderReplaced = replaceHomeFolder(filePath, homeFolder);
        switch (homeFolderReplaced) {
            case "Home/Jobs":
                icon.type = "RESULTFOLDER";
                break;
            case "Home/Favorites":
                icon.type = "FAVFOLDER";
                break;
            case "Home/Shares":
                icon.type = "SHARESFOLDER";
                break;
            case "Home/Trash":
                icon.type = "TRASHFOLDER";
                break;
            default:
                icon.type = "DIRECTORY";
        }
        return icon;
    }

    const filename = getFilenameFromPath(filePath);
    if (!filename.includes(".")) {
        return icon;
    }
    icon.ext = extensionFromPath(filePath);

    return icon;
};

/**
 *
 * @param params: { status, min, max } (both inclusive)
 */
export const inRange = ({status, min, max}: {status: number, min: number, max: number}): boolean =>
    status >= min && status <= max;
export const inSuccessRange = (status: number): boolean => inRange({status, min: 200, max: 299});
export const removeTrailingSlash = (path: string) => path.endsWith("/") ? path.slice(0, path.length - 1) : path;
export const addTrailingSlash = (path: string) => {
    if (!path) return path;
    else return path.endsWith("/") ? path : `${path}/`;
};

export const shortUUID = (uuid: string): string => uuid.substring(0, 8).toUpperCase();
export const is5xxStatusCode = (status: number) => inRange({status, min: 500, max: 599});
export const blankOrUndefined = (value?: string): boolean => value == null || value.length == 0 || /^\s*$/.test(value);

export const ifPresent = (f: any, handler: (f: any) => void) => {
    if (f) handler(f)
};

// FIXME The frontend can't handle downloading multiple files currently. When fixed, remove === 1 check.
export const downloadAllowed = (files: File[]) =>
    files.length === 1 && files.every(f => f.sensitivityLevel !== "SENSITIVE");

/**
 * Capizalises the input string and replaces _ (underscores) with whitespace.
 * @param str
 */
export const prettierString = (str: string) => capitalized(str).replace(/_/g, " ");

export function defaultErrorHandler(
    error: {request: XMLHttpRequest, response: any}
): number {
    const request: XMLHttpRequest = error.request;
    // FIXME must be solvable more elegantly
    let why: string | null = null;

    if (!!error.response && !!error.response.why) {
        why = error.response.why;
    }

    if (!!request) {
        if (!why) {
            switch (request.status) {
                case 400:
                    why = "Bad request";
                    break;
                case 403:
                    why = "Permission denied";
                    break;
                default:
                    why = "Internal Server Error. Try again later.";
                    break;
            }
        }

        snackbarStore.addSnack({message: why, type: SnackType.Failure});
        return request.status;
    }
    return 500;
}

export function sortByToPrettierString(sortBy: SortBy): string {
    switch (sortBy) {
        case SortBy.ACL:
            return "Members";
        case SortBy.FILE_TYPE:
            return "File Type";
        case SortBy.CREATED_AT:
            return "Created at";
        case SortBy.MODIFIED_AT:
            return "Modified at";
        case SortBy.PATH:
            return "Path";
        case SortBy.SIZE:
            return "Size";
        case SortBy.SENSITIVITY_LEVEL:
            return "File sensitivity";
        default:
            return prettierString(sortBy);
    }
}

export function requestFullScreen(el: Element, onFailure: () => void) {
    // @ts-ignore - Safari compatibility
    if (el.webkitRequestFullScreen) el.webkitRequestFullscreen();
    else if (el.requestFullscreen) el.requestFullscreen();
    else onFailure();
}

export function timestampUnixMs(): number {
    return window.performance &&
        window.performance.now &&
        window.performance.timing &&
        window.performance.timing.navigationStart ?
        window.performance.now() + window.performance.timing.navigationStart :
        Date.now();
}

export function humanReadableNumber(
    value: number,
    sectionDelim: string = ",",
    decimalDelim: string = ".",
    numDecimals: number = 2
): string {
    const regex = new RegExp("\\d(?=(\\d{3})+" + (numDecimals > 0 ? "\\D" : "$") + ")", "g");
    const fixedNumber = value.toFixed(numDecimals);

    return fixedNumber
        .replace(".", decimalDelim)
        .replace(regex, "$&" + sectionDelim);
}

interface CopyToClipboard {
    value: string | undefined;
    message: string;
}

export function copyToClipboard({value, message}: CopyToClipboard) {
    const input = document.createElement("input");
    input.value = value || "";
    document.body.appendChild(input);
    input.select();
    document.execCommand("copy");
    document.body.removeChild(input);
    snackbarStore.addSnack({message, type: SnackType.Success});
}

export function errorMessageOrDefault(
    err: {request: XMLHttpRequest, response: any} | {status: number, response: string},
    defaultMessage: string
): string {
    try {
        if (typeof err === "string") return err;
        if ("status" in err) {
            return err.response;
        } else {
            if (err.response.why) return err.response.why;
            return HTTP_STATUS_CODES[err.request.status] || defaultMessage;
        }
    } catch {
        return defaultMessage;
    }
}

export function delay(ms: number): Promise<void> {
    return new Promise<void>((resolve) => {
        setTimeout(() => resolve(), ms);
    });
}

export const inDevEnvironment = () => process.env.NODE_ENV === "development";

export const generateId = ((): (target: string) => string => {
    const store = new Map<string, number>();
    return (target = "default-target") => {
        const idCount = (store.get(target) || 0) + 1;
        store.set(target, idCount);
        return `${target}${idCount}`;
    };
})();
