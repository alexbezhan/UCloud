import { HeaderSearchReduxObject, initHeader } from "DefaultObjects";
import { HeaderActions } from "./HeaderActions";

export const SET_PRIORITIZED_SEARCH = "SET_PRIORITIZED_SEARCH";
export const SET_REFRESH_FUNCTION = "SET_REFRESH_FUNCTION";

const header = (state: HeaderSearchReduxObject = initHeader(), { type, payload }: HeaderActions): HeaderSearchReduxObject => {
    switch (type) {
        case SET_REFRESH_FUNCTION:
        case SET_PRIORITIZED_SEARCH:
            return { ...state, ...payload };
        default: {
            return state;
        }
    }
};

export default header;