import * as jwt from "jsonwebtoken";
import {
    inDevEnvironment,
    inRange,
    inSuccessRange,
    is5xxStatusCode
} from "UtilityFunctions";
import {ReduxObject} from "DefaultObjects";
import {Store} from "redux";
import {SnackType} from "Snackbar/Snackbars";
import {snackbarStore} from "Snackbar/SnackbarStore";

export interface Override {
    path: string,
    method: { value: string }
    destination: {
        scheme?: string
        host?: string
        port: number
    }
}

interface CallParameters {
    method: string;
    path: string;
    body?: object;
    context?: string;
    maxRetries?: number;
    disallowProjects?: boolean;
}

/**
 * Represents an instance of the SDUCloud object used for contacting the backend, implicitly using JWTs.
 */
export default class SDUCloud {
    private readonly context: string;
    private readonly serviceName: string;
    private readonly authContext: string;
    private readonly redirectOnInvalidTokens: boolean;

    private apiContext: string;
    private accessToken: string;
    private csrfToken: string;
    private decodedToken: any;
    private forceRefresh: boolean = false;
    private overridesPromise: Promise<void> | null = null;

    private projectId: string | undefined = undefined;
    private projectAccessToken: string | undefined = undefined;
    private projectDecodedToken: any | undefined = undefined;

    overrides: Override[] = [];

    constructor() {
        let context = location.protocol + '//' +
            location.hostname +
            (location.port ? ':' + location.port : '');

        let serviceName: string;
        switch (location.hostname) {
            case "localhost":
                serviceName = "local-dev-csrf";
                break;

            default:
                serviceName = "web-csrf";
                break;
        }

        this.context = context;
        this.serviceName = serviceName;

        this.apiContext = "/api";
        this.authContext = "/auth";

        this.decodedToken = null;
        this.redirectOnInvalidTokens = false;

        let accessToken = SDUCloud.storedAccessToken;
        let csrfToken = SDUCloud.storedCsrfToken;
        if (accessToken && csrfToken) {
            this.setTokens(accessToken, csrfToken);
        }

        if (process.env.NODE_ENV === "development") {
            this.overridesPromise = (async () => {
                const jsonResponse: Override[] = await (await fetch("http://localhost:9900/")).json();
                console.log("Got back the following response:", jsonResponse);
                this.overrides = jsonResponse;
            })();
        }
    }

    initializeStore(store: Store<ReduxObject>) {
        store.subscribe(() => {
            const project = store.getState().project.project;
            if (project !== this.projectId) {
                console.log("Something");
                this.projectId = project;
                this.projectDecodedToken = undefined;
                this.projectAccessToken = undefined;
            }
        });
    }

    /**
     * Makes an AJAX call to the API. This will automatically add relevant authorization headers.
     * If the user's JWT has expired this will automatically attempt to refresh it.
     *
     * The path argument should be without the context, it should also not include the /api/ part of the context.
     * For example, to call `GET https://cloud.sdu.dk/api/files?path=/home/foobar` you should use
     * `cloud.call("GET", "/files?path=/home/foobar/")`
     *
     * The body argument is assumed to be JSON.
     *
     * @param {string} method - the HTTP method
     * @param {string} path - the path, should not contain context or /api/
     * @param {object} body - the request body, assumed to be a JS object to be encoded as JSON.
     * @param {string} context - the base of the request (e.g. "/api")
     * @param {number} maxRetries - the amount of times the call should be retried on failure (Default: 5).
     * @param {disallowProjects} disallowProjects - true if this call should not use the project token (Default: false).
     * @return {Promise} promise
     */
    async call(
        {
            method,
            path,
            body,
            context = this.apiContext,
            maxRetries = 5,
            disallowProjects = false
        }: CallParameters
    ): Promise<any> {
        if (this.overridesPromise != null) {
            try {
                await this.overridesPromise;
            } catch (ignored) {
            }
            this.overridesPromise = null;
        }

        if (path.indexOf("/") !== 0) path = "/" + path;
        return this.receiveAccessTokenOrRefreshIt(disallowProjects).then(token => {
            return new Promise((resolve, reject) => {
                let req = new XMLHttpRequest();
                req.open(method, this.computeURL(context, path));
                req.setRequestHeader("Authorization", `Bearer ${token}`);
                req.setRequestHeader("Content-Type", "application/json; charset=utf-8");
                req.responseType = "text"; // Explicitly set, otherwise issues with empty response

                const rejectOrRetry = (parsedResponse?) => {
                    if (req.status === 401) {
                        this.forceRefresh = true;
                    }

                    if (maxRetries >= 1 && is5xxStatusCode(req.status)) {
                        this.call({
                            method: method,
                            path: path,
                            body: body,
                            context: context,
                            maxRetries: maxRetries - 1
                        })
                            .catch(e => reject(e)).then(e => resolve(e));
                    } else {
                        reject({request: req, response: parsedResponse});
                    }
                };

                req.onload = async () => {
                    try {
                        let responseContentType = req.getResponseHeader("content-type");
                        let parsedResponse = req.response.length === 0 ? "{}" : req.response;

                        // JSON Parsing
                        if (responseContentType !== null) {
                            if (responseContentType.indexOf("application/json") !== -1 ||
                                responseContentType.indexOf("application/javascript") !== -1) {
                                parsedResponse = JSON.parse(parsedResponse);
                            }
                        }

                        if (inSuccessRange(req.status)) {
                            resolve({
                                response: parsedResponse,
                                request: req,
                            });
                        } else {
                            rejectOrRetry(parsedResponse);
                        }
                    } catch (e) {
                        rejectOrRetry();
                    }
                };

                if (body) {
                    req.send(JSON.stringify(body));
                } else {
                    req.send();
                }
            });
        });
    }

    public computeURL(context: string, path: string): string {
        let absolutePath = context + path;
        for (let i = 0; i < this.overrides.length; i++) {
            let override = this.overrides[i];
            if (absolutePath.indexOf(override.path) === 0) {
                let scheme = override.destination.scheme ?
                    override.destination.scheme : "http";
                let host = override.destination.host ?
                    override.destination.host : "localhost";
                let port = override.destination.port;

                return scheme + "://" + host + ":" + port + absolutePath;
            }
        }

        return this.context + absolutePath;
    }

    /**
     * Calls with the GET HTTP method. See call(method, path, body)
     */
    public async get<T = any>(path: string, context = this.apiContext,
                              disallowProjects: boolean = false): Promise<{ request: XMLHttpRequest, response: T }> {
        return this.call({method: "GET", path, body: undefined, context, disallowProjects});
    }

    /**
     * Calls with the POST HTTP method. See call(method, path, body)
     */
    public async post<T = any>(path: string, body?: object, context = this.apiContext,
                               disallowProjects: boolean = false): Promise<{ request: XMLHttpRequest, response: T }> {
        return this.call({method: "POST", path, body, context, disallowProjects});
    }

    /**
     * Calls with the PUT HTTP method. See call(method, path, body)
     */
    public async put<T = any>(path: string, body: object, context = this.apiContext,
                              disallowProjects: boolean = false): Promise<{ request: XMLHttpRequest, response: T }> {
        return this.call({method: "PUT", path, body, context, disallowProjects});
    }

    /**
     * Calls with the DELETE HTTP method. See call(method, path, body)
     */
    public async delete<T = void>(path: string, body: object, context = this.apiContext,
                                  disallowProjects: boolean = false): Promise<{ request: XMLHttpRequest, response: T }> {
        return this.call({method: "DELETE", path, body, context, disallowProjects});
    }

    /**
     * Calls with the PATCH HTTP method. See call(method, path, body)
     */
    public async patch<T = any>(path: string, body: object, context = this.apiContext,
                                disallowProjects: boolean = false): Promise<{ request: XMLHttpRequest, response: T }> {
        return this.call({method: "PATCH", path, body, context, disallowProjects});
    }

    /**
     * Calls with the OPTIONS HTTP method. See call(method, path, body)
     */
    public async options<T = any>(path: string, body: object, context = this.apiContext,
                                  disallowProjects: boolean = false): Promise<{ request: XMLHttpRequest, response: T }> {
        return this.call({method: "OPTIONS", path, body, context, disallowProjects});
    }

    /**
     * Calls with the HEAD HTTP method. See call(method, path, body)
     */
    public async head<T = any>(path: string, context = this.apiContext,
                               disallowProjects: boolean = false): Promise<{ request: XMLHttpRequest, response: T }> {
        return this.call({method: "HEAD", path, body: undefined, context, disallowProjects});
    }

    /**
     * Opens up a new page which contains the login page at the auth service. This login page will automatically
     * redirect back to the correct service (using serviceName).
     */
    public openBrowserLoginPage() {
        if (window.location.href !== this.context + "/app/login")
            window.location.href = this.context + "/app/login";
    }

    public openLandingPage() {
        if (window.location.href !== this.context + "/app/")
            window.location.href = this.context + "/app/";
    }

    /**
     * @returns the username of the authenticated user or null
     */
    public get username(): string | undefined {
        let info = this.userInfo;
        if (info) return info.sub;
        else return undefined;
    }

    public get activeUsername(): string | undefined {
        if (this.useProjectToken(false) && !!this.projectDecodedToken) {
            return this.projectDecodedToken.payload.sub;
        } else {
            return this.username;
        }
    }

    /**
     * @returns {string} the homefolder path for the currently logged in user (with trailing slash).
     */
    public get homeFolder(): string {
        let username = this.username;
        if (this.projectId !== undefined) {
            username = this.projectId;
        }
        return `/home/${username}/`
    }

    public get trashFolder(): string {
        return `${this.homeFolder}Trash/`
    }

    public get isLoggedIn(): boolean {
        return this.userInfo != null;
    }

    /**
     * @returns {string} the userrole. Null if none available in the JWT
     */
    get userRole(): string {
        const info = this.userInfo;
        if (info) return info.role;
        return "";
    }

    /**
     * @returns {boolean} whether or not the user is listed as an admin.
     */
    get userIsAdmin(): boolean {
        return this.userRole === "ADMIN";
    }

    /**
     * Returns the userInfo (the payload of the JWT). Be aware that the JWT is not verified, this means that a user
     * will be able to put whatever they want in this. This is normally not a problem since all backend services _will_
     * verify the token.
     */
    private get userInfo(): undefined | JWT {
        let token = this.decodedToken;
        if (!token) return undefined;
        else return this.decodedToken.payload;
    }

    get principalType(): undefined | string {
        const userInfo = this.userInfo;
        if (!userInfo) return undefined;
        else return userInfo.principalType;
    }

    private useProjectToken(disallowProjects: boolean): boolean {
        return this.projectId !== undefined && !disallowProjects;
    }

    private retrieveToken(disallowProjects: boolean): string {
        if (this.useProjectToken(disallowProjects)) {
            return this.projectAccessToken!;
        } else {
            return SDUCloud.storedAccessToken;
        }
    }

    /**
     * Attempts to receive a (non-expired) JWT access token from storage. In case the token has expired at attempt will
     * be made to refresh it. If it is not possible to refresh the token a MissingAuthError will be thrown. This would
     * indicate the user no longer has valid credentials. At this point it would make sense to present the user with
     * the login page.
     *
     * @return {Promise} a promise of an access token
     */
    async receiveAccessTokenOrRefreshIt(disallowProjects: boolean = false): Promise<any> {
        if (this.overridesPromise != null) {
            try {
                await this.overridesPromise;
            } catch (ignored) {
            }
            this.overridesPromise = null;
        }

        let tokenPromise: Promise<any> | null = null;
        if (this.isTokenExpired(disallowProjects) || this.forceRefresh) {
            tokenPromise = this.refresh(disallowProjects);
            this.forceRefresh = false;
        } else {
            tokenPromise = new Promise((resolve, reject) => resolve(this.retrieveToken(disallowProjects)));
        }
        return tokenPromise;
    }

    createOneTimeTokenWithPermission(permission, disallowProjects: boolean = false): Promise<any> {
        return this.receiveAccessTokenOrRefreshIt(disallowProjects)
            .then(token => {
                let oneTimeToken = `${this.context}${this.authContext}/request/?audience=${permission}`;
                return new Promise((resolve, reject) => {
                    let req = new XMLHttpRequest();
                    req.open("POST", oneTimeToken);
                    req.setRequestHeader("Authorization", `Bearer ${token}`);
                    req.setRequestHeader("Content-Type", "application/json");
                    req.onload = () => {
                        try {
                            if (inRange({status: req.status, min: 200, max: 299})) {
                                const response = req.response.length === 0 ? "{}" : req.response;
                                resolve({response: JSON.parse(response), request: req});
                            } else {
                                reject(req.response);
                            }
                        } catch (e) {
                            reject(e.response)
                        }
                    };
                    req.send();
                });
            }).then((data: any) => new Promise((resolve, reject) => resolve(data.response.accessToken)));
    }

    private async refresh(disallowProjects: boolean): Promise<string> {
        const project = this.projectId;
        if (project !== undefined && !disallowProjects) {
            const result = await this.post("/projects/auth", {project}, undefined, true);
            if (inSuccessRange(result.request.status)) {
                const accessToken = result.response.accessToken;
                this.projectAccessToken = accessToken;
                this.projectDecodedToken = this.decodeToken(accessToken);
                return accessToken;
            } else {
                if (result.request.status === 401 || result.request.status === 400) {
                    SDUCloud.clearTokens();
                    this.openBrowserLoginPage();
                }

                throw Error("Unable to refresh token")
            }
        } else {
            let csrfToken = SDUCloud.storedCsrfToken;
            if (!csrfToken) {
                return new Promise((resolve, reject) => {
                    reject(this.missingAuth());
                });
            }

            let refreshPath = this.computeURL(this.authContext, "/refresh/web");
            return new Promise((resolve, reject) => {
                let req = new XMLHttpRequest();
                req.open("POST", refreshPath);
                req.setRequestHeader("X-CSRFToken", csrfToken);
                req.onload = () => {
                    try {
                        if (inSuccessRange(req.status)) {
                            resolve(JSON.parse(req.response));
                        } else {
                            if (req.status === 401 || req.status === 400) {
                                SDUCloud.clearTokens();
                                this.openBrowserLoginPage();
                            }
                            reject({status: req.status, response: req.response});
                        }
                    } catch (e) {
                        reject({status: e.status, response: e.response});
                    }
                };
                req.send();
            }).then((data: any) => {
                return new Promise((resolve, reject) => {
                    this.setTokens(data.accessToken, data.csrfToken);
                    resolve(data.accessToken);
                });
            });
        }
    }

    /**
     * Updates tokens received by the auth service.
     *
     * @param accessToken the (JWT) access token.
     * @param csrfToken the csrf token
     */
    public setTokens(accessToken: string, csrfToken: string): void {
        if (!accessToken) throw this.missingAuth();

        this.accessToken = accessToken;
        SDUCloud.storedAccessToken = accessToken;

        this.csrfToken = csrfToken;
        SDUCloud.storedCsrfToken = csrfToken;


        this.decodedToken = this.decodeToken(accessToken);
    }

    private decodeToken(accessToken: string): any {
        const bail = (): never => {
            SDUCloud.clearTokens();
            this.openBrowserLoginPage();
            return void (0) as never;
        };
        try {
            const token = jwt.decode(accessToken, {complete: true});

            if (token === null) {
                return bail();
            }

            if (typeof token === "string") {
                return bail();
            } else if (typeof token === "object") {
                const payload = token.payload;
                const isValid = "sub" in payload &&
                    "uid" in payload &&
                    "aud" in payload &&
                    "role" in payload &&
                    "iss" in payload &&
                    "exp" in payload &&
                    "extendedByChain" in payload &&
                    "iat" in payload &&
                    "principalType" in payload;

                if (!isValid) {
                    console.log("Bailing. Bad JWT");
                    return bail();
                }

                return token;
            } else {
                return bail();
            }
        } catch (e) {
            return bail();
        }
    }

    public async logout() {
        try {
            const res = await fetch(`${this.context}${this.authContext}/logout/web`, {
                headers: {
                    "X-CSRFToken": SDUCloud.storedCsrfToken,
                    "Content-Type": "application/json",
                },
                method: "POST",
                credentials: "same-origin"
            });
            if (!is5xxStatusCode(res.status)) {
                window.localStorage.removeItem("accessToken");
                window.localStorage.removeItem("csrfToken");
                this.openBrowserLoginPage();
                return;
            }
            throw Error("The server was unreachable, please try again later.")
        } catch (err) {
            snackbarStore.addSnack({message: err.message, type: SnackType.Failure});
        }
    }

    private static clearTokens() {
        SDUCloud.storedAccessToken = "";
        SDUCloud.storedCsrfToken = "";
    }

    static get storedAccessToken(): string {
        return window.localStorage.getItem("accessToken") || "";
    }

    static set storedAccessToken(value: string) {
        window.localStorage.setItem("accessToken", value);
    }

    static get storedCsrfToken(): string {
        return window.localStorage.getItem("csrfToken") || "";
    }

    static set storedCsrfToken(value) {
        window.localStorage.setItem("csrfToken", value);
    }

    private isTokenExpired(disallowProject: boolean) {
        const token = this.useProjectToken(disallowProject) ? this.projectDecodedToken : this.decodedToken;
        if (!token || !token.payload) return true;
        const nowInSeconds = Math.floor(Date.now() / 1000);
        const inOneMinute = nowInSeconds + (60);
        return token.payload.exp < inOneMinute;
    }

    private missingAuth() {
        if (this.redirectOnInvalidTokens) {
            this.openBrowserLoginPage();
            return 0;
        } else {
            return new MissingAuthError();
        }
    }
}

interface JWT {
    sub: string
    uid: number
    lastName?: string
    aud: string
    role: string
    iss: string
    firstNames?: string
    exp: number
    extendedByChain: any[]
    iat: number
    principalType: string
    publicSessionReference?: string
}

export class MissingAuthError {
    private name: string;

    constructor() {
        this.name = "MissingAuthError";
    }
}
