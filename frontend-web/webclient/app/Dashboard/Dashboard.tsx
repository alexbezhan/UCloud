import * as React from "react";
import { DefaultLoading } from "LoadingIcon/LoadingIcon";
import { iconFromFilePath, toLowerCaseAndCapitalize } from "UtilityFunctions";
import { Cloud } from "Authentication/SDUCloudObject"
import { favoriteFile, getParentPath, getFilenameFromPath } from "Utilities/FileUtilities";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { setAllLoading, fetchFavorites, fetchRecentAnalyses, fetchRecentFiles, receiveFavorites, setErrorMessage } from "./Redux/DashboardActions";
import { connect } from "react-redux";
import * as moment from "moment";
import { FileIcon } from "UtilityComponents";
import { DASHBOARD_FAVORITE_ERROR } from "./Redux/DashboardReducer";
import { DashboardProps, DashboardOperations, DashboardStateProps } from ".";
import { Notification, NotificationEntry } from "Notifications";
import { Analysis } from "Applications";
import { File, FileType } from "Files";
import { Dispatch } from "redux";
import { ReduxObject } from "DefaultObjects";
import { Error, Box, Flex, Card, Text, Link } from "ui-components";
import * as Heading from "ui-components/Heading";
import List from "ui-components/List";
import { CardGroup } from "ui-components/Card";
import { TextSpan } from "ui-components/Text";
import { notificationRead } from "Notifications/Redux/NotificationsActions";
import { PaginationButtons } from "Pagination/Pagination";


class Dashboard extends React.Component<DashboardProps> {
    constructor(props) {
        super(props);
        const { favoriteFiles, recentFiles, recentAnalyses } = props;
        props.updatePageTitle();
        if (!favoriteFiles.length && !recentFiles.length && !recentAnalyses.length) {
            props.setAllLoading(true);
        }
        props.fetchFavorites();
        props.fetchRecentFiles();
        props.fetchRecentAnalyses();
    }

    render() {
        const { favoriteFiles, recentFiles, recentAnalyses, notifications, favoriteLoading, recentLoading,
            analysesLoading, errors } = this.props;
        favoriteFiles.forEach(f => f.favorited = true);
        const favoriteOrUnfavorite = (file: File) => {
            favoriteFile(file, Cloud);
            this.props.receiveFavorites(favoriteFiles.filter((f: File) => f.favorited));
        };

        return (
            <React.StrictMode>
                <Error error={errors.join(",\n")} clearError={this.props.errorDismiss} />
                <CardGroup>
                    <DashboardFavoriteFiles
                        files={favoriteFiles}
                        isLoading={favoriteLoading}
                        favorite={file => favoriteOrUnfavorite(file)}
                    />
                    <DashboardRecentFiles files={recentFiles} isLoading={recentLoading} />
                    <DashboardAnalyses analyses={recentAnalyses} isLoading={analysesLoading} />
                    <DashboardNotifications notifications={notifications} />
                </CardGroup>
            </React.StrictMode>
        );
    }
}

const DashboardFavoriteFiles = ({ files, isLoading, favorite }: { files: File[], isLoading: boolean, favorite: (file: File) => void }) => {
    const noFavorites = files.length || isLoading ? null : (<Heading.h6>No favorites found</Heading.h6>);
    return (
        <Card height="auto" width={290} boxShadowSize='md' borderWidth={1} borderRadius={6} style={{ overflow: "hidden" }}>
            <Flex bg="lightGray" color="darkGray" p={3} alignItems="center">
                <Heading.h4>Favorite files</Heading.h4>
            </Flex>
            <Box px={3} py={1}>
                <DefaultLoading loading={isLoading} />
                <Box pb="0.5em" />
                {noFavorites}
                <List>
                    {files.map((file, i) => (
                        <Flex key={i} pt="0.8em" pb="6px">
                            <ListFileContent path={file.path} type={file.fileType} link={false} pixelsWide={200} />
                            <Box ml="auto" />
                            <Box><i className="fas fa-star" style={{ color: "blue", verticalAlign: "middle" }} onClick={() => favorite(file)} /></Box>
                        </Flex>)
                    )}
                </List>
            </Box>
        </Card>)
};

const ListFileContent = ({ path, type, link, pixelsWide }: { path: string, type: FileType, link: boolean, pixelsWide: 117 | 200 }) =>
    <>
        <FileIcon name={iconFromFilePath(path, type, Cloud.homeFolder)} size={undefined} link={link} color="grey" />
        <Link to={`/files/${type === "FILE" ? getParentPath(path) : path}`}>
            <TextSpan mt="-1.5px" fontSize={2} className={`limited-width-string-${pixelsWide}px`}>{getFilenameFromPath(path)}</TextSpan>
        </Link>
    </>

const DashboardRecentFiles = ({ files, isLoading }: { files: File[], isLoading: boolean }) => (
    <Card height="auto" width={290} boxShadowSize='md' borderWidth={1} borderRadius={6} style={{ overflow: "hidden" }}>
        <Flex bg="lightGray" color="darkGray" p={3} alignItems="center">
            <Heading.h4>Recently used files</Heading.h4>
        </Flex>
        <Box px={3} py={1}>
            {isLoading || files.length ? null : (<h3><small>No recently used files</small></h3>)}
            <DefaultLoading loading={isLoading} />
            <Box pb="0.5em" />
            <List>
                {files.map((file, i) => (
                    <Flex key={i} pt="0.8em" pb="6px">
                        <ListFileContent path={file.path} type={file.fileType} link={file.link} pixelsWide={117} />
                        <Box ml="auto" />
                        <Text color="grey">{moment(new Date(file.modifiedAt)).fromNow()}</Text>
                    </Flex>
                ))}
            </List>
        </Box>
    </Card>
);

const DashboardAnalyses = ({ analyses, isLoading }: { analyses: Analysis[], isLoading: boolean }) => (
    <Card height="auto" width={290} boxShadowSize='md' borderWidth={1} borderRadius={6} style={{ overflow: "hidden" }}>
        <Flex bg="lightGray" color="darkGray" p={3} alignItems="center">
            <Heading.h4>Recent Jobs</Heading.h4>
        </Flex>
        <Box px={3} py={1}>
            <DefaultLoading loading={isLoading} />
            {isLoading || analyses.length ? null : (<h3><small>No results found</small></h3>)}
            <Box pb="0.5em" />
            <List>
                {analyses.map((analysis: Analysis, index: number) =>
                    <Flex key={index} pt="0.9em" pb="9.85px">
                        <Box pr="0.3em">
                            <i className={`fa ${statusToIconName(analysis.state)}`} style={{
                                color: statusToColor(analysis.state),
                                alignSelf: "center"
                            }} />
                        </Box>
                        <Link mt="-3px" to={`/applications/results/${analysis.jobId}`}><TextSpan fontSize={2}>{analysis.appName}</TextSpan></Link>
                        <Box ml="auto" />
                        <TextSpan mt="-3px" fontSize={2}>{toLowerCaseAndCapitalize(analysis.state)}</TextSpan>
                    </Flex>
                )}
            </List>
        </Box>
    </Card>
);

const DashboardNotifications = ({ notifications }: { notifications: Notification[] }) => (
    <Card height="auto" width={290} boxShadowSize='md' borderWidth={1} borderRadius={6} style={{ overflow: "hidden" }}>
        <Flex bg="lightGray" color="darkGray" p={3} alignItems="center">
            <Heading.h4>Recent notifications</Heading.h4>
        </Flex>
        <Box px={3} py={1}>
            {notifications.length === 0 ? <h3><small>No notifications</small></h3> : null}
            <List>
                {notifications.slice(0, 7).map((n, i) =>
                    <Flex key={i}>
                        <NotificationEntry notification={n} />
                    </Flex>
                )}
            </List>
        </Box>
    </Card>
);

const statusToIconName = (status: string) => status === "SUCCESS" ? "fa-check" : "fa-times";
const statusToColor = (status: string) => status === "SUCCESS" ? "green" : "red";

const mapDispatchToProps = (dispatch: Dispatch): DashboardOperations => ({
    errorDismiss: () => dispatch(setErrorMessage(DASHBOARD_FAVORITE_ERROR, undefined)),
    updatePageTitle: () => dispatch(updatePageTitle("Dashboard")),
    setAllLoading: (loading) => dispatch(setAllLoading(loading)),
    fetchFavorites: async () => dispatch(await fetchFavorites()),
    fetchRecentFiles: async () => dispatch(await fetchRecentFiles()),
    fetchRecentAnalyses: async () => dispatch(await fetchRecentAnalyses()),
    notificationRead: async id => dispatch(await notificationRead(id)),
    // FIXME: Make action instead
    receiveFavorites: (files) => dispatch(receiveFavorites(files))
});

const mapStateToProps = (state: ReduxObject): DashboardStateProps => {
    const {
        errors,
        favoriteFiles,
        recentFiles,
        recentAnalyses,
        favoriteLoading,
        recentLoading,
        analysesLoading,
    } = state.dashboard;
    return {
        errors,
        favoriteFiles,
        recentFiles,
        recentAnalyses,
        favoriteLoading,
        recentLoading,
        analysesLoading,
        notifications: state.notifications.page.items,
        favoriteFilesLength: favoriteFiles.length // Hack to ensure re-rendering
    };
};

export default connect(mapStateToProps, mapDispatchToProps)(Dashboard);
