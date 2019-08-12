import {Cloud} from "Authentication/SDUCloudObject";
import {PayloadAction, Page} from "Types";
import {WithAppMetadata, WithAppFavorite} from "Applications";
import {LoadableEvent, unwrapCall} from "LoadableContent";
import {buildQueryString} from "Utilities/URIUtilities";

export enum Tag {
    RECEIVE_APP = "FAVORITE_APPS_RECEIVE_APP"
}

export type Type = ReceiveApp;

type ReceiveApp = PayloadAction<typeof Tag.RECEIVE_APP, LoadableEvent<Page<WithAppMetadata & WithAppFavorite>>>;

export const fetch = async (itemsPerPage: number, page: number): Promise<ReceiveApp> => ({
    type: Tag.RECEIVE_APP,
    payload: await unwrapCall(
        Cloud.get<Page<WithAppMetadata & WithAppFavorite>>(buildQueryString(
            "/hpc/apps/favorites",
            {
                itemsPerPage,
                page
            }
        ))
    )
});