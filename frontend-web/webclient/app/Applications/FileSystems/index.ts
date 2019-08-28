import {APICallParameters} from "Authentication/DataHook";
import {buildQueryString} from "Utilities/URIUtilities";

export interface SharedFileSystem {
    id: string,
    owner: string,
    backend: string,
    title: string,
    createdAt: number
}

export interface SharedFileSystemMount {
    sharedFileSystem: SharedFileSystem,
    mountedAt: string
}

interface CreateFileSystemParameters {
    title: string;
    backend?: string;
}

export const createFileSystem = ({title, backend}: CreateFileSystemParameters): APICallParameters => ({
    method: "PUT",
    path: "/app/fs",
    reloadId: Math.random(),
    payload: {title, backend},
    parameters: {title, backend}
});

export const deleteFileSystem = (id: string): APICallParameters => ({
    method: "DELETE",
    path: `/app/fs/${id}`,
    reloadId: Math.random()
});

interface ListFileSystemsParameters {
    page?: number;
    itemsPerPage?: number;
}

export const listFileSystems = ({page = 0, itemsPerPage = 10}: ListFileSystemsParameters): APICallParameters<ListFileSystemsParameters> => ({
    method: "GET",
    path: buildQueryString("/app/fs", {page, itemsPerPage}),
    parameters: {page, itemsPerPage},
    reloadId: Math.random()
});

export const viewFileSystem = (id: string, calculateSize: boolean = false): APICallParameters => ({
    method: "GET",
    path: buildQueryString(`/app/fs/${id}`, {calculateSize}),
    reloadId: Math.random()
});
