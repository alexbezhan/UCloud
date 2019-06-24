import * as Defaults from "../app/DefaultObjects";
import { SortOrder, SortBy } from "../app/Files";
import { SidebarPages } from "../app/ui-components/Sidebar";
import { Analysis } from "../app/Applications";
import { DashboardStateProps } from "../app/Dashboard";

describe("Initialize Redux Objects", () => {
    test("Dashboard", () => {
        expect(Defaults.initDashboard()).toEqual({
            favoriteFiles: [],
            recentFiles: [],
            recentAnalyses: [],
            notifications: [],
            favoriteLoading: false,
            recentLoading: false,
            analysesLoading: false,
        } as DashboardStateProps)
    });

    test("Files", () => {
        const homeFolder = "/home/user@test.dk/"
        expect(JSON.parse(JSON.stringify(Defaults.initFiles(homeFolder)))).toEqual(JSON.parse(JSON.stringify({
            page: Defaults.emptyPage,
            sortOrder: SortOrder.ASCENDING,
            sortBy: SortBy.PATH,
            loading: false,
            error: undefined,
            path: "",
            invalidPath: false,
            filesInfoPath: "",
            sortingColumns: [SortBy.MODIFIED_AT, SortBy.SIZE],
            fileSelectorLoading: false,
            fileSelectorShown: false,
            fileSelectorPage: Defaults.emptyPage,
            fileSelectorPath: homeFolder,
            fileSelectorCallback: () => null,
            fileSelectorError: undefined,
            fileSelectorIsFavorites: false,
            disallowedPaths: []
        })) as Defaults.FilesReduxObject)
    });

    test("Status", () =>
        expect(Defaults.initStatus()).toEqual({
            status: Defaults.DefaultStatus,
            title: "",
            page: SidebarPages.None,
            loading: false
        } as Defaults.StatusReduxObject)
    );

    test("Header", () =>
        expect(Defaults.initHeader()).toEqual({
            prioritizedSearch: "files"
        } as Defaults.HeaderSearchReduxObject)
    );

    test("Notifications", () =>
        expect(Defaults.initNotifications()).toEqual({
            items: [],
            loading: false,
            redirectTo: "",
            error: undefined
        } as Defaults.NotificationsReduxObject)
    );

    test("Analyses", () =>
        expect(Defaults.initAnalyses()).toEqual({
            page: Defaults.emptyPage,
            loading: false,
            error: undefined
        } as Defaults.ComponentWithPage<Analysis>)
    );

    test("Zenodo", () =>
        expect(Defaults.initZenodo()).toEqual({
            connected: false,
            loading: false,
            page: Defaults.emptyPage,
            error: undefined
        } as Defaults.ZenodoReduxObject)
    );

    test("Sidebar", () =>
        expect(Defaults.initSidebar()).toEqual({
            kcCount: 0,
            pp: false,
            options: []
        } as Defaults.SidebarReduxObject)
    );

    test("Uploads", () =>
        expect(JSON.parse(JSON.stringify(Defaults.initUploads()))).toEqual(JSON.parse(JSON.stringify({
            path: "",
            uploads: [],
            loading: false,
            visible: false,
            allowMultiple: false,
            onFilesUploaded: () => null
        })) as Defaults.UploaderReduxObject)
    );
});