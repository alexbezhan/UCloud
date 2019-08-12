import * as React from "react";
import {capitalized, errorMessageOrDefault} from "UtilityFunctions"
import {updatePageTitle, setActivePage} from "Navigation/Redux/StatusActions";
import {List} from "Pagination/List";
import {connect} from "react-redux";
import {setLoading, fetchAnalyses, checkAnalysis, checkAllAnalyses} from "./Redux/AnalysesActions";
import {AnalysesProps, AnalysesOperations, Analysis, AppState, RunsSortBy, AnalysesStateProps} from ".";
import {Dispatch} from "redux";
import {Table, TableBody, TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import {MainContainer} from "MainContainer/MainContainer";
import {History} from "history";
import {ReduxObject} from "DefaultObjects";
import {SidebarPages} from "ui-components/Sidebar";
import * as Heading from "ui-components/Heading";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {EntriesPerPageSelector} from "Pagination";
import {Spacer} from "ui-components/Spacer";
import * as moment from "moment";
import "moment/locale/en-gb";
import {JobStateIcon} from "./JobStateIcon";
import {TextSpan} from "ui-components/Text";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {DatePicker} from "ui-components/DatePicker";
import {prettierString} from "UtilityFunctions";
import styled from "styled-components";
import {MasterCheckbox, Arrow} from "UtilityComponents";
import {inCancelableState, cancelJobDialog, cancelJob} from "Utilities/ApplicationUtilities";
import {Cloud} from "Authentication/SDUCloudObject";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {SnackType} from "Snackbar/Snackbars";
import {SortOrder} from "Files";
import {getStartOfWeek} from "Activity/Page";
import Button from "ui-components/Button";
import InputGroup from "ui-components/InputGroup";
import Label from "ui-components/Label";
import Box from "ui-components/Box";
import {ContainerForText} from "ui-components";
import Checkbox from "ui-components/Checkbox";

interface FetchJobsOptions {
    itemsPerPage?: number
    pageNumber?: number
    sortBy?: RunsSortBy
    sortOrder?: SortOrder
    minTimestamp?: number
    maxTimestamp?: number
    filter?: AppState
}

/* FIXME: Almost identical to similar one in FilesTable.tsx */
const JobResultsHeaderCell = styled(TableHeaderCell) <{pointer?: boolean}>`
    background-color: ${({theme}) => theme.colors.white};
    ${({pointer}) => pointer ? "cursor: pointer" : null}
    top: 96px; //topmenu + header size
    z-index: 10;
    position: sticky;
`;

function JobResults(props: AnalysesProps & {history: History}) {

    React.useEffect(() => {
        moment.locale("en-gb");
        props.onInit();
        fetchJobs();
        props.setRefresh(() => fetchJobs());
        return () => props.setRefresh();
    }, []);

    function fetchJobs(options?: FetchJobsOptions) {
        const opts = options || {};
        const {page, setLoading} = props;
        const itemsPerPage = opts.itemsPerPage != null ? opts.itemsPerPage : page.itemsPerPage;
        const pageNumber = opts.pageNumber != null ? opts.pageNumber : page.pageNumber;
        const sortOrder = opts.sortOrder != null ? opts.sortOrder : props.sortOrder;
        const sortBy = opts.sortBy != null ? opts.sortBy : props.sortBy;
        const minTimestamp = opts.minTimestamp != null ? opts.minTimestamp : undefined;
        const maxTimestamp = opts.maxTimestamp != null ? opts.maxTimestamp : undefined;
        const filter = opts.filter != null ? opts.filter : undefined;
        setLoading(true);
        props.fetchJobs(itemsPerPage, pageNumber, sortOrder, sortBy, minTimestamp, maxTimestamp, filter);
        props.setRefresh(() =>
            props.fetchJobs(itemsPerPage, pageNumber, sortOrder, sortBy, minTimestamp, maxTimestamp, filter)
        );
    }

    const {page, loading, history, responsive, sortBy, sortOrder} = props;
    const {itemsPerPage, pageNumber} = page;

    const selectedAnalyses = page.items.filter(it => it.checked);
    const cancelableAnalyses = selectedAnalyses.filter(it => inCancelableState(it.state));

    const hide = responsive.lessThan.lg;
    const masterCheckboxChecked = selectedAnalyses.length === page.items.length && page.items.length > 0;
    const masterCheckbox = <MasterCheckbox
        checked={masterCheckboxChecked}
        onClick={checked => props.checkAllAnalyses(checked)}
    />;

    const content = <List
        customEmptyPage={<Heading.h1>No jobs found.</Heading.h1>}
        loading={loading}
        pageRenderer={page =>
            <ContainerForText>
                <Table>
                    <Header
                        hide={hide}
                        masterCheckbox={masterCheckbox}
                        sortBy={sortBy}
                        sortOrder={sortOrder}
                        fetchJobs={sortBy => fetchJobs({
                            itemsPerPage,
                            pageNumber,
                            sortOrder: sortOrder === SortOrder.ASCENDING ? SortOrder.DESCENDING : SortOrder.ASCENDING,
                            sortBy
                        })}
                    />
                    <TableBody>
                        {page.items.map((a, i) =>
                            <Row
                                hide={hide}
                                to={() => history.push(`/applications/results/${a.jobId}`)}
                                analysis={a}
                                key={i}
                            >
                                <Box><Label>
                                    <Checkbox
                                        checked={a.checked}
                                        onClick={(e: {target: {checked: boolean}}) =>
                                            props.checkAnalysis(a.jobId, e.target.checked)}
                                    />
                                </Label></Box>
                            </Row>)}
                    </TableBody>
                </Table>
            </ContainerForText>
        }
        page={page}
        onPageChanged={pageNumber => fetchJobs({pageNumber})}
    />;

    const [filter, setFilter] = React.useState({text: "Don't filter", value: "Don't filter"});
    const [firstDate, setFirstDate] = React.useState<Date | null>(null);
    const [secondDate, setSecondDate] = React.useState<Date | null>(null);

    const appStates = Object.keys(AppState).map(it => ({text: prettierString(it), value: it}));
    appStates.push({text: "Don't Filter", value: "Don't filter"});

    function fetchJobsInRange(minDate: Date | null, maxDate: Date | null) {
        return () => fetchJobs({
            itemsPerPage,
            pageNumber,
            sortOrder,
            sortBy,
            minTimestamp: minDate == null ? undefined : minDate.getTime(),
            maxTimestamp: maxDate == null ? undefined : maxDate.getTime()
        })
    }

    const now = new Date().getTime();
    const dayInMillis = 24 * 60 * 60 * 1000;
    const yesterday = new Date(new Date().getTime() - dayInMillis);
    const startOfYesterday = yesterday.getTime() - yesterday.getMilliseconds();
    const startOfWeek = getStartOfWeek(new Date()).getTime();

    function updateFilterAndFetchJobs(value: string) {
        setFilter({text: prettierString(value), value});
        fetchJobs({
            itemsPerPage,
            pageNumber,
            sortBy,
            sortOrder,
            filter: value == "Don't filter" ? undefined : value as AppState
        });
    }

    const sidebar = (<Box pt={48}>
        <Heading.h3>
            Quick Filters
        </Heading.h3>
        <Box cursor="pointer" onClick={fetchJobsInRange(new Date(now - new Date().getMilliseconds()), null)}><TextSpan>Today</TextSpan></Box>
        <Box cursor="pointer" onClick={fetchJobsInRange(new Date(startOfYesterday), new Date(startOfYesterday + dayInMillis))}>
            <TextSpan>Yesterday</TextSpan>
        </Box>
        <Box cursor="pointer" onClick={fetchJobsInRange(new Date(startOfWeek), null)}><TextSpan>This week</TextSpan></Box>
        <Box cursor="pointer" onClick={fetchJobsInRange(null, null)}><TextSpan>No filter</TextSpan></Box>
        <Heading.h3 mt={16}>Active Filters</Heading.h3>
        <Label>Filter by app state</Label>
        <ClickableDropdown
            chevron
            trigger={filter.text}
            onChange={updateFilterAndFetchJobs}
            options={appStates.filter(it => it.value != filter.value)}
        />
        <Box mb={16} mt={16}>
            <Label>Job created after</Label>
            <InputGroup>
                <DatePicker
                    placeholderText="Don't filter"
                    isClearable
                    selectsStart
                    showTimeSelect
                    startDate={firstDate}
                    endDate={secondDate}
                    selected={firstDate}
                    onChange={date => (setFirstDate(date), fetchJobsInRange(date, secondDate)())}
                    timeFormat="HH:mm"
                    dateFormat="dd/MM/yy HH:mm"
                />
            </InputGroup>
        </Box>
        <Box mb={16}>
            <Label>Job created before</Label>
            <InputGroup>
                <DatePicker
                    placeholderText="Don't filter"
                    isClearable
                    selectsEnd
                    showTimeSelect
                    startDate={firstDate}
                    endDate={secondDate}
                    selected={secondDate}
                    onChange={date => (setSecondDate(date), fetchJobsInRange(firstDate, date)())}
                    onSelect={d => fetchJobsInRange(firstDate, d)}
                    timeFormat="HH:mm"
                    dateFormat="dd/MM/yy HH:mm"
                />
            </InputGroup>
        </Box>
        <AnalysisOperations cancelableAnalyses={cancelableAnalyses} onFinished={() => fetchJobs()}/>
    </Box>);

    return (<MainContainer
        header={
            <Spacer
                left={null}
                right={
                    <EntriesPerPageSelector
                        content="Jobs per page"
                        entriesPerPage={page.itemsPerPage}
                        onChange={itemsPerPage => fetchJobs({itemsPerPage})}
                    />
                }
            />
        }
        headerSize={48}
        sidebarSize={340}
        main={content}
        sidebar={sidebar}
    />);
}

interface AnalysisOperationsProps {
    cancelableAnalyses: Analysis[]
    onFinished: () => void
}

const AnalysisOperations = ({cancelableAnalyses, onFinished}: AnalysisOperationsProps) =>
    cancelableAnalyses.length === 0 ? null : (
        <Button fullWidth color="red" onClick={() => cancelJobDialog({
            jobCount: cancelableAnalyses.length,
            jobId: cancelableAnalyses[0].jobId,
            onConfirm: async () => {
                try {
                    await Promise.all(cancelableAnalyses.map(a => cancelJob(Cloud, a.jobId)));
                    snackbarStore.addSnack({type: SnackType.Success, message: "Jobs cancelled"});
                } catch (e) {
                    snackbarStore.addFailure(errorMessageOrDefault(e, "An error occured"))
                } finally {
                    onFinished();
                }
            }
        })}>
            Cancel selected ({cancelableAnalyses.length}) jobs
    </Button>);

interface HeaderProps {
    hide: boolean
    masterCheckbox: JSX.Element
    sortBy: RunsSortBy
    sortOrder: SortOrder
    fetchJobs: (sortBy: RunsSortBy) => void
}

const Header = ({hide, sortBy, sortOrder, masterCheckbox, fetchJobs}: HeaderProps) => (
    <TableHeader>
        <TableRow>
            <JobResultsHeaderCell width="4%" textAlign="center">
                {masterCheckbox}
            </JobResultsHeaderCell>
            <JobResultsHeaderCell pointer textAlign="left" onClick={() => fetchJobs(RunsSortBy.state)}>
                <Arrow sortBy={RunsSortBy.state} activeSortBy={sortBy} order={sortOrder}/>
                State
            </JobResultsHeaderCell>
            <JobResultsHeaderCell pointer textAlign="left" onClick={() => fetchJobs(RunsSortBy.application)}>
                <Arrow sortBy={RunsSortBy.application} activeSortBy={sortBy} order={sortOrder}/>
                Application
            </JobResultsHeaderCell>
            {hide ? null :
                <JobResultsHeaderCell pointer textAlign="left" onClick={() => fetchJobs(RunsSortBy.createdAt)}>
                    <Arrow sortBy={RunsSortBy.createdAt} activeSortBy={sortBy} order={sortOrder}/>
                    Created at
                </JobResultsHeaderCell>}
            <JobResultsHeaderCell pointer textAlign="left" onClick={() => fetchJobs(RunsSortBy.lastUpdate)}>
                <Arrow sortBy={RunsSortBy.lastUpdate} activeSortBy={sortBy} order={sortOrder}/>
                Last update
            </JobResultsHeaderCell>
        </TableRow>
    </TableHeader>
);

interface RowProps {
    hide: boolean
    analysis: Analysis
    to: () => void
}
const Row: React.FunctionComponent<RowProps> = ({analysis, to, hide, children}) => {
    const metadata = analysis.metadata;
    return (
        <TableRow cursor={"pointer"}>
            <TableCell textAlign="center">
                {children}
            </TableCell>
            <TableCell onClick={to}><JobStateIcon state={analysis.state} mr={"8px"} /> {capitalized(analysis.state)}
            </TableCell>
            <TableCell onClick={to}>{metadata.title} v{metadata.version}</TableCell>
            {hide ? null : <TableCell onClick={to}>{moment(analysis.createdAt).calendar()}</TableCell>}
            <TableCell onClick={to}>{moment(analysis.modifiedAt).calendar()}</TableCell>
        </TableRow>)
};

const mapDispatchToProps = (dispatch: Dispatch): AnalysesOperations => ({
    setLoading: loading => dispatch(setLoading(loading)),
    fetchJobs: async (itemsPerPage, pageNumber, sortOrder, sortBy, minTimestamp, maxTimestamp, filter) =>
        dispatch(await fetchAnalyses(itemsPerPage, pageNumber, sortOrder, sortBy, minTimestamp, maxTimestamp, filter)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
    onInit: () => {
        dispatch(setActivePage(SidebarPages.Runs));
        dispatch(updatePageTitle("Runs"))
    },
    checkAnalysis: (jobId, checked) => dispatch(checkAnalysis(jobId, checked)),
    checkAllAnalyses: checked => dispatch(checkAllAnalyses(checked))
});

const mapStateToProps = ({analyses, responsive}: ReduxObject): AnalysesStateProps => ({
    ...analyses,
    responsive: responsive!
});
export default connect(mapStateToProps, mapDispatchToProps)(JobResults);
