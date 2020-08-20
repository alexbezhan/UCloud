import {FullAppInfo, WithAllAppTags, WithAppFavorite, WithAppInvocation, WithAppMetadata} from "Applications";
import {Client} from "Authentication/HttpClientInstance";
import {LoadableEvent, unwrapCall} from "LoadableContent";
import {hpcFavoriteApp} from "Utilities/ApplicationUtilities";

export enum Tag {
    RECEIVE_APP = "VIEW_APP_RECEIVE_APP",
    RECEIVE_PREVIOUS = "VIEW_APP_RECEIVE_PREVIOUS",
    RECEIVE_FAVORITE = "VIEW_APP_RECEIVE_FAVORITE"
}

export type Type = ReceiveApp | ReceivePrevious | ReceiveFavorite;

type ReceiveApp = PayloadAction<typeof Tag.RECEIVE_APP, LoadableEvent<FullAppInfo>>;
type ReceivePrevious = PayloadAction<typeof Tag.RECEIVE_PREVIOUS, LoadableEvent<Page<FullAppInfo>>>;
type ReceiveFavorite = PayloadAction<typeof Tag.RECEIVE_FAVORITE, LoadableEvent<void>>;

export const fetchApplication = async (name: string, version: string): Promise<ReceiveApp> => ({
    type: Tag.RECEIVE_APP,
    payload: await unwrapCall(
        Client.get<WithAppMetadata & WithAppFavorite & WithAppInvocation & WithAllAppTags>(`/hpc/apps/${encodeURIComponent(name)}/${encodeURIComponent(version)}`)
    )
});

export const fetchPreviousVersions = async (name: string): Promise<ReceivePrevious> => ({
    type: Tag.RECEIVE_PREVIOUS,
    payload: await unwrapCall(
        Client.get<Page<FullAppInfo>>(`/hpc/apps/${encodeURIComponent(name)}`)
    )
});


export const favoriteApplication = async (name: string, version: string): Promise<ReceiveFavorite> => ({
    type: Tag.RECEIVE_FAVORITE,
    payload: await unwrapCall(
        Client.post(hpcFavoriteApp(name, version))
    )
});
