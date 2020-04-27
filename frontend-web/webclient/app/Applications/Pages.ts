import {ApplicationMetadata} from "Applications";
import {buildQueryString} from "Utilities/URIUtilities";

export const view = (name: string, version: string): string =>
    `/applications/details/${encodeURIComponent(name)}/${encodeURIComponent(version)}/`;

export const viewApplication = (application: { name: string, version: string }): string =>
    view(application.name, application.version);

export const run = (name: string, version: string): string =>
    `/applications/${encodeURIComponent(name)}/${encodeURIComponent(version)}/`;

export const runApplication = (application: ApplicationMetadata): string =>
    run(application.name, application.version);

export const results = (): string => `/applications/results`;

export const browse = (itemsPerPage: number = 25, page: number = 0): string =>
    buildQueryString(`/applications`, {itemsPerPage, page});

export const browseByTag = (tag: string, itemsPerPage: number = 25, page: number = 0): string =>
    buildQueryString(`/applications`, {tag, itemsPerPage, page});
