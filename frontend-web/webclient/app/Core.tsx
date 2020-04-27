import Activity from "Activity/Page";
import {DetailedDowntime} from "Admin/Downtime/DetailedDowntime";
import DowntimeManagement from "Admin/DowntimeManagement";
import LicenseServers from "Admin/LicenseServers";
import AdminOverview from "Admin/Overview";
import UserCreation from "Admin/UserCreation";
import {dispatchUserAction, onLogin} from "App";
import ApplicationsBrowse from "Applications/Browse";
import DetailedResult from "Applications/DetailedResult";
import * as ApplicationsInstalled from "Applications/Installed";
import Runs from "Applications/Runs";
import ApplicationsOverview from "Applications/Overview";
import Run from "Applications/Run";
import AppStudioApps from "Applications/Studio/App";
import AppStudioPage from "Applications/Studio/Page";
import AppStudioTools from "Applications/Studio/Tool";
import ApplicationView from "Applications/View";
import {Client} from "Authentication/HttpClientInstance";
import Dashboard from "Dashboard/Dashboard";
import Dialog from "Dialog/Dialog";
import {ErrorBoundary} from "ErrorBoundary/ErrorBoundary";
import FileInfo from "Files/FileInfo";
import FilePreview from "Files/FilePreview";
import Files from "Files/Files";
import {History} from "history";
import {LoginPage} from "Login/Login";
import Wayf from "Login/Wayf";
import {MainContainer} from "MainContainer/MainContainer";
import {USER_LOGIN} from "Navigation/Redux/HeaderReducer";
import NoVNCClient from "NoVNC/NoVNCClient";
import {Playground} from "Playground/Playground";
import ProjectList from "Project/List";
import ProjectView from "Project/View";
import * as React from "react";
import {Route, RouteComponentProps, Switch} from "react-router-dom";
import Search from "Search/Search";
import ServiceLicenseAgreement from "ServiceLicenseAgreement";
import * as Share from "Shares";
import Snackbars from "Snackbar/Snackbars";
import Sidebar from "ui-components/Sidebar";
import Uploader from "Uploader/Uploader";
import AvataaarModification from "UserSettings/Avataaar";
import UserSettings from "UserSettings/UserSettings";
import {inDevEnvironment} from "UtilityFunctions";
import GroupsOverview from "Project/GroupView";

const NotFound = (): JSX.Element => (<MainContainer main={<div><h1>Not found.</h1></div>} />);

const Core = (): JSX.Element => (
    <>
        <Dialog />
        <Snackbars />
        <Uploader />
        <Sidebar />
        <ErrorBoundary>
            <Switch>
                <Route exact path="/login" component={LoginPage} />
                <Route exact path="/loginSuccess" component={LoginSuccess} />
                <Route exact path="/login/wayf" component={Wayf} />
                <Route exact path="/" component={requireAuth(Dashboard)} />
                <Route exact path="/dashboard" component={requireAuth(Dashboard)} />

                <Route exact path="/files/info" component={requireAuth(FileInfo)} />
                <Route exact path="/files/preview" component={requireAuth(FilePreview)} />
                <Route exact path="/files" component={requireAuth(Files)} />

                <Route exact path="/activity" component={requireAuth(Activity)} />

                <Route exact path="/novnc" component={requireAuth(NoVNCClient)} />

                <Route exact path="/applications" component={requireAuth(ApplicationsBrowse)} />
                <Route exact path="/applications/overview" component={requireAuth(ApplicationsOverview)} />
                <Route
                    exact
                    path="/applications/installed"
                    component={requireAuth(ApplicationsInstalled.default)}
                />
                <Route
                    exact
                    path="/applications/details/:appName/:appVersion"
                    component={requireAuth(ApplicationView)}
                />
                <Route exact path="/applications/results" component={requireAuth(Runs)} />
                <Route exact path="/applications/results/:jobId" component={requireAuth(DetailedResult)} />
                <Route exact path="/applications/:appName/:appVersion" component={requireAuth(Run)} />

                <Route exact path={"/applications/studio"} component={requireAuth(AppStudioPage)} />
                <Route exact path={"/applications/studio/t/:name"} component={requireAuth(AppStudioTools)} />
                <Route exact path={"/applications/studio/a/:name"} component={requireAuth(AppStudioApps)} />

                {!inDevEnvironment() ? null : <Route exact path={"/playground"} component={Playground} />}

                <Route exact path="/shares" component={requireAuth(Share.List)} />

                <Route exact path="/admin" component={requireAuth(AdminOverview)} />
                <Route exact path="/admin/userCreation" component={requireAuth(UserCreation)} />
                <Route exact path="/admin/licenseServers" component={requireAuth(LicenseServers)} />
                <Route exact path="/admin/downtime" component={requireAuth(DowntimeManagement)} />

                <Route exact path="/downtime/detailed/:id" component={requireAuth(DetailedDowntime)} />

                <Route
                    exact
                    path="/users/settings"
                    component={requireAuth(UserSettings, {requireTwoFactor: false})}
                />
                <Route exact path="/users/avatar" component={requireAuth(AvataaarModification)} />

                <Route exact path="/search/:priority" component={requireAuth(Search)} />

                <Route exact path="/projects" component={requireAuth(ProjectList)} />
                <Route exact path="/projects/view/:id" component={requireAuth(ProjectView)} />
                <Route exact path="/projects/groups/:group?" component={requireAuth(GroupsOverview)} />

                <Route
                    exact
                    path="/sla"
                    component={requireAuth(ServiceLicenseAgreement, {requireTwoFactor: false, requireSla: false})}
                />

                <Route component={NotFound} />
            </Switch>
        </ErrorBoundary>
    </>
);

interface RequireAuthOpts {
    requireTwoFactor?: boolean;
    requireSla?: boolean;
}

function requireAuth<T>(Delegate: React.FunctionComponent<T>, opts?: RequireAuthOpts): React.FunctionComponent<T> {
    return (props: T & RouteComponentProps) => {
        const info = Client.userInfo;
        if (!Client.isLoggedIn || info === undefined) {
            props.history.push("/login");
            return null;
        }

        if (opts === undefined || opts.requireSla !== false) {
            if (info.serviceLicenseAgreement === false) {
                props.history.push("/sla");
                return null;
            }
        }

        if (opts === undefined || opts.requireTwoFactor) {
            if (info.principalType === "password" && Client.userRole === "USER" &&
                info.twoFactorAuthentication === false) {
                props.history.push("/users/settings");
                return null;
            }
        }

        return <Delegate {...props} />;
    };
}

const LoginSuccess = (props: {history: History}): null => {
    dispatchUserAction(USER_LOGIN);
    onLogin();
    props.history.push("/");
    return null;
};

export default Core;
