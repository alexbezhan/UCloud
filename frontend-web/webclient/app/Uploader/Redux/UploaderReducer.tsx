export const SET_UPLOADER_CALLBACK = "SET_UPLOADER_CALLBACK";
export const SET_UPLOADER_UPLOADS = "SET_UPLOADER_UPLOADS";
export const SET_UPLOADER_VISIBLE = "SET_UPLOADER_VISIBLE";
export const SET_UPLOADER_ERROR = "SET_UPLOADER_ERROR";
export const SET_UPLOADER_LOADING = "SET_UPLOADER_LOADING";
export const APPEND_UPLOADS = "APPEND_UPLOADS";

import {initUploads, UploaderReduxObject} from "DefaultObjects";
import {UploaderActions} from "./UploaderActions";

const uploader = (state: UploaderReduxObject = initUploads(), action: UploaderActions): UploaderReduxObject => {
    switch (action.type) {
        case SET_UPLOADER_ERROR:
        case SET_UPLOADER_UPLOADS:
        case SET_UPLOADER_VISIBLE:
        case SET_UPLOADER_LOADING:
        case SET_UPLOADER_CALLBACK: {
            return {...state, ...action.payload};
        }
        case APPEND_UPLOADS: {
            return {...state, uploads: state.uploads.concat([action.upload])};
        }
        default: {
            return state;
        }
    }
};

export default uploader;
