import {Client} from "Authentication/HttpClientInstance";
import {useEffect, useReducer, useState} from "react";
import {defaultErrorHandler} from "UtilityFunctions";

function dataFetchReducer(state, action) {
    switch (action.type) {
        case "FETCH_INIT":
            return {
                ...state,
                loading: true,
                error: undefined
            };
        case "FETCH_SUCCESS":
            return {
                ...state,
                loading: false,
                error: undefined,
                data: action.payload,
            };
        case "FETCH_FAILURE":
            return {
                ...state,
                loading: false,
                error: action.error
            };
        default:
            throw new Error();
    }
}

export interface APICallParameters<Parameters = any, Payload = any> {
    method?: "GET" | "POST" | "DELETE" | "PUT" | "PATCH" | "OPTIONS" | "HEAD";
    path?: string;
    payload?: Payload;
    context?: string;
    maxRetries?: number;
    parameters?: Parameters;
    disallowProjects?: boolean;
    reloadId?: number; // Can be used to force an ID by setting this to a random value
    noop?: boolean; // Used to indicate that this should not be run in a useCloudAPI hook.
    withCredentials?: boolean;
    projectOverride?: string;
}

export interface APIError {
    why: string;
    statusCode: number;
}

export interface APICallState<T> {
    loading: boolean;
    error?: APIError;
    data: T;
}

export function mapCallState<T, T2>(state: APICallState<T>, mapper: (t: T) => T2): APICallState<T2> {
    return {
        ...state,
        data: mapper(state.data)
    };
}

export async function callAPI<T>(parameters: APICallParameters): Promise<T> {
    const method = parameters.method !== undefined ? parameters.method : "GET";
    if (parameters.path === undefined) throw Error("Missing path");
    return (await Client.call({
        method,
        path: parameters.path,
        body: parameters.payload,
        context: parameters.context,
        maxRetries: parameters.maxRetries,
        withCredentials: parameters.withCredentials,
        projectOverride: parameters.projectOverride
    })).response;
}

export async function callAPIWithErrorHandler<T>(
    parameters: APICallParameters
): Promise<T | null> {
    try {
        return await callAPI<T>(parameters);
    } catch (e) {
        defaultErrorHandler(e);
        return null;
    }
}

export function useCloudAPI<T, Parameters = any>(
    callParametersInitial: APICallParameters<Parameters>,
    dataInitial: T
): [APICallState<T>, (params: APICallParameters<Parameters>) => void, APICallParameters<Parameters>] {
    const [params, setParams] = useState(callParametersInitial);

    const [state, dispatch] = useReducer(dataFetchReducer, {
        loading: false,
        error: undefined,
        data: dataInitial
    });

    useEffect(() => {
        let didCancel = false;
        if (params.noop !== true) {
            async function fetchData() {
                if (params.path !== undefined) {
                    dispatch({type: "FETCH_INIT"});

                    try {
                        const result: T = await callAPI(params);

                        if (!didCancel) {
                            dispatch({type: "FETCH_SUCCESS", payload: result});
                        }
                    } catch (e) {
                        if (!didCancel) {
                            const statusCode = e.request.status;
                            let why = "Internal Server Error";
                            if (!!e.response && e.response.why) {
                                why = e.response.why;
                            }

                            dispatch({type: "FETCH_FAILURE", error: {why, statusCode}});
                        }
                    }
                }
            }

            fetchData();
        }

        return () => {
            didCancel = true;
        };
    }, [params]);

    function doFetch(params: APICallParameters): void {
        setParams(params);
    }

    const returnedState: APICallState<T> = {...state};
    return [returnedState, doFetch, params];
}

export function useAsyncCommand(): [boolean, <T = any>(call: APICallParameters) => Promise<T | null>] {
    const [isLoading, setIsLoading] = useState(false);
    let didCancel = false;
    const sendCommand = <T>(call: APICallParameters): Promise<T | null> => {
        return new Promise<T | null>(async (resolve, reject) => {
            if (didCancel) return;

            setIsLoading(true);
            try {
                const result = await callAPIWithErrorHandler<T>(call);
                if (!didCancel) {
                    resolve(result);
                }
            } catch (e) {
                if (!didCancel) {
                    reject(e);
                }
            }

            setIsLoading(false);
        });
    };

    useEffect(() => {
        return () => {
            didCancel = true;
        };
    }, []);

    return [isLoading, sendCommand];
}

export type AsyncWorker = [boolean, string | undefined, (fn: () => Promise<void>) => void];

export function useAsyncWork(): AsyncWorker {
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | undefined>(undefined);
    let didCancel = false;
    useEffect(() => {
        return () => {
            didCancel = true;
        };
    }, []);

    const startWork = async (fn: () => Promise<void>) => {
        if (didCancel) return;
        setError(undefined);
        setIsLoading(true);
        try {
            await fn();
        } catch (e) {
            if (didCancel) return;
            if (!!e.request) {
                let why = "Internal Server Error";
                if (!!e.response && e.response.why) {
                    why = e.response.why;
                } else {
                    why = e.request.statusText;
                }
                setError(why);
            } else if (typeof e === "string") {
                setError(e);
            } else if ("message" in e && typeof e.message === "string") {
                setError(e.message);
            } else {
                setError("Internal error");
            }
        }
        if (!didCancel) setIsLoading(false);
    };

    return [isLoading, error, startWork];
}
