import { Cloud } from "Authentication/SDUCloudObject";
import { PayloadAction, Page } from "Types";
import { Application } from "Applications";
import { LoadableEvent, unwrapCall } from "LoadableContent";

export enum Tag {
    RECEIVE_APP = "VIEW_APP_RECEIVE_APP",
    RECEIVE_PREVIOUS = "VIEW_APP_RECEIVE_PREVIOUS",
    RECEIVE_FAVORITE = "VIEW_APP_RECEIVE_FAVORITE"
}

export type Type = ReceiveApp | ReceivePrevious | ReceiveFavorite;

type ReceiveApp = PayloadAction<typeof Tag.RECEIVE_APP, LoadableEvent<Application>>;
type ReceivePrevious = PayloadAction<typeof Tag.RECEIVE_PREVIOUS, LoadableEvent<Page<Application>>>;
type ReceiveFavorite = PayloadAction<typeof Tag.RECEIVE_FAVORITE, LoadableEvent<void>>;

export const fetchApplication = async (name: string, version: string): Promise<ReceiveApp> => ({
    type: Tag.RECEIVE_APP,
    payload: await unwrapCall(
        Cloud.get<Application>(`/hpc/apps/${encodeURIComponent(name)}/${encodeURIComponent(version)}`)
    )
});

export const fetchPreviousVersions = async (name: string): Promise<ReceivePrevious> => ({
    type: Tag.RECEIVE_PREVIOUS,
    payload: await unwrapCall(
        Cloud.get<Page<Application>>(`/hpc/apps/${encodeURIComponent(name)}`)
    )
});


export const favoriteApplication = async (name: string, version: string): Promise<ReceiveFavorite> => ({
    type: Tag.RECEIVE_FAVORITE,
    payload: await unwrapCall(
        Cloud.post(`/hpc/apps/favorites/${encodeURIComponent(name)}/${encodeURIComponent(version)}`)
    )
});