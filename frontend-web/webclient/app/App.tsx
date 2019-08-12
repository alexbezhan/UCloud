import * as React from "react";
import * as ReactDOM from "react-dom";
import {Provider} from "react-redux";
import {BrowserRouter} from "react-router-dom";
import fileInfo from "Files/Redux/FileInfoReducer";
import {theme, UIGlobalStyle} from "ui-components";
import {createGlobalStyle, ThemeProvider} from "styled-components";
import {Cloud} from "Authentication/SDUCloudObject";
import {initObject} from "DefaultObjects";
import Core from "Core";
import avatar from "UserSettings/Redux/AvataaarReducer";
import header, {USER_LOGIN, USER_LOGOUT, CONTEXT_SWITCH} from "Navigation/Redux/HeaderReducer";
import status from "Navigation/Redux/StatusReducer";
import applications from "Applications/Redux/BrowseReducer";
import dashboard from "Dashboard/Redux/DashboardReducer";
import sidebar from "Navigation/Redux/SidebarReducer";
import analyses from "Applications/Redux/AnalysesReducer";
import notifications from "Notifications/Redux/NotificationsReducer";
import uploader from "Uploader/Redux/UploaderReducer";
import activity from "Activity/Redux/ActivityReducer";
import simpleSearch from "Search/Redux/SearchReducer";
import detailedFileSearch from "Files/Redux/DetailedFileSearchReducer";
import detailedApplicationSearch from "Applications/Redux/DetailedApplicationSearchReducer";
import filePreview from "Files/Redux/FilePreviewReducer";
import * as AppRedux from "Applications/Redux";
import * as AccountingRedux from "Accounting/Redux";
import {configureStore} from "Utilities/ReduxUtilities";
import {responsiveStoreEnhancer, createResponsiveStateReducer} from 'redux-responsive';
import {responsiveBP, invertedColors} from "ui-components/theme";
import {findAvatar} from "UserSettings/Redux/AvataaarActions";
import Header from "Navigation/Header";
import {isLightThemeStored, setSiteTheme} from "UtilityFunctions";
import * as ProjectRedux from "Project/Redux";

const store = configureStore(initObject(Cloud.homeFolder), {
    activity,
    dashboard,
    analyses,
    applications,
    header,
    status,
    sidebar,
    uploader,
    notifications,
    simpleSearch,
    detailedFileSearch,
    detailedApplicationSearch,
    fileInfo,
    filePreview,
    ...AppRedux.reducers,
    ...AccountingRedux.reducers,
    avatar,
    loading,
    project: ProjectRedux.reducer,
    responsive: createResponsiveStateReducer(
        responsiveBP,
        {infinity: "xxl"}),
}, responsiveStoreEnhancer);

function loading(state = false, action): boolean {
    switch (action.type) {
        case "LOADING_START":
            return true;
        case "LOADING_END":
            return false;
        default:
            return state;
    }
}

export function dispatchUserAction(type: typeof USER_LOGIN | typeof USER_LOGOUT | typeof CONTEXT_SWITCH) {
    store.dispatch({type})
}

export async function onLogin() {
    const action = await findAvatar();
    if (action !== null) store.dispatch(action);
}

const GlobalStyle = createGlobalStyle`
  ${() => UIGlobalStyle}
`;

Cloud.initializeStore(store);

function App({children}) {
    const [isLightTheme, setTheme] = React.useState(isLightThemeStored());
    const setAndStoreTheme = (isLight: boolean) => (setSiteTheme(isLight), setTheme(isLight));
    return (
        <ThemeProvider theme={isLightTheme ? theme : {...theme, colors: invertedColors}}>
            <>
                <GlobalStyle/>
                <BrowserRouter basename="app">
                    <Header toggleTheme={() => isLightTheme ? setAndStoreTheme(false) : setAndStoreTheme(true)}/>
                    {children}
                </BrowserRouter>
            </>
        </ThemeProvider>
    )
}

ReactDOM.render(
    <Provider store={store}>
        <App>
            <Core/>
        </App>
    </Provider>,
    document.getElementById("app")
);
