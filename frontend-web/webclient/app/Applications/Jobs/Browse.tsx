import * as React from "react";
import * as UCloud from "UCloud";
import {useLoading, useTitle} from "Navigation/Redux/StatusActions";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import {useRefreshFunction} from "Navigation/Redux/HeaderActions";
import {useCallback, useEffect, useState} from "react";
import {useCloudAPI} from "Authentication/DataHook";
import * as Heading from "ui-components/Heading";
import {emptyPageV2} from "DefaultObjects";
import {useProjectId} from "Project";
import * as Pagination from "Pagination";
import {MainContainer} from "MainContainer/MainContainer";
import {useHistory} from "react-router";
import {AppToolLogo} from "Applications/AppToolLogo";
import {ListRow, ListRowStat} from "ui-components/List";
import Text, {TextSpan} from "ui-components/Text";
import {prettierString, shortUUID} from "UtilityFunctions";
import {formatRelative} from "date-fns/esm";
import {enGB} from "date-fns/locale";
import {isRunExpired} from "Utilities/ApplicationUtilities";
import {Box, Button, Flex, InputGroup, Label} from "ui-components";
import {Client} from "Authentication/HttpClientInstance";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {DatePicker} from "ui-components/DatePicker";
import {getStartOfDay, getStartOfWeek} from "Activity/Page";
import {JobState} from "./index";
import {JobStateIcon} from "./JobStateIcon";

function mockApp(): UCloud.compute.Job {
    return {
        id: "string112312" + (Math.random() * 10 | 0),
        owner: {launchedBy: Client.username!},
        updates: [{timestamp: new Date().getTime()}],
        billing: {creditsCharged: 200, pricePerUnit: 100},
        parameters: {
            application: {name: "foo", version: "1.3.3.7"},
            product: {id: "bar", category: "foo", provider: "foo"},
            name: "string" + (Math.random() * 100 | 0),
            replicas: 0,
            allowDuplicateJob: false,
            parameters: {},
            resources: [],
            timeAllocation: {
                hours: 5,
                minutes: 0,
                seconds: 0
            },
            resolvedProduct: {
                id: "string",
                pricePerUnit: 120000 /* int64 */,
                category: {id: "foo", provider: "bar"},
                description: "string",
                availability: {type: "available"},
                priority: 0,
                type: "compute"
            },
            resolvedApplication: {
                metadata: {
                    name: "string",
                    version: "1.3.3.7",
                    authors: ["fooey"],
                    title: "string",
                    description: "string",
                    public: true
                },
                invocation: {

                } as any
            }
        }
    }
}


function isJobStateFinal(state?: JobState): boolean {
    if (state === undefined) return false;
    return ["SUCCESS", "FAILURE"].includes(state);
}

const itemsPerPage = 50;

// const items = [mockApp(), mockApp(), mockApp()];


const data: UCloud.PageV2<UCloud.compute.Job> = {
    itemsPerPage: 50,
    items: [
        {
            id: "2cc8edc6-0e58-423c-93cd-bd20f241fd87",
            owner: {
                launchedBy: "user",
                project: undefined
            },
            updates: [
                {
                    timestamp: 1606809728000,
                    state: "SUCCESS",
                    status: undefined
                }
            ],
            billing: {
                creditsCharged: 8,
                pricePerUnit: 1
            },
            parameters: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    category: "u1-standard",
                    provider: "ucloud"
                },
                name: undefined,
                replicas: 1,
                allowDuplicateJob: true,
                parameters: undefined,
                resources: undefined,
                timeAllocation: undefined,
                resolvedProduct: undefined,
                resolvedApplication: undefined
            },
            "output": undefined
        },
        {
            id: "a4f35d51-40bf-496f-bcd9-5813cb5f91db",
            owner: {
                launchedBy: "user"
            },
            updates: [
                {
                    timestamp: 1606809944000,
                    state: "FAILURE"
                }
            ],
            billing: {
                creditsCharged: 0,
                pricePerUnit: 1
            },
            parameters: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    category: "u1-standard",
                    provider: "ucloud"
                },
                name: undefined,
                replicas: 1,
                allowDuplicateJob: true,
            },
            output: undefined
        },
        {
            id: "812cf11a-3a16-4a98-80ce-763613cf560b",
            owner: {
                launchedBy: "user",
                project: undefined
            },
            updates: [
                {
                    timestamp: 1606809998000,
                    state: "SUCCESS",
                    status: undefined
                }
            ],
            billing: {
                creditsCharged: 2,
                pricePerUnit: 1
            },
            parameters: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                replicas: 1,
                allowDuplicateJob: true,
            },
        },
        {
            id: "a2cfcd0a-e24b-443c-bc47-a05fe748530f",
            owner: {
                launchedBy: "user"
            },
            "updates": [
                {
                    "timestamp": 1606810527000,
                    "state": "FAILURE"
                }
            ],
            "billing": {
                "creditsCharged": 0,
                "pricePerUnit": 1
            },
            parameters: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true
            },
        },
        {
            id: "245cd7ba-ec9e-499f-8da0-a6d41c118387",
            "owner": {
                "launchedBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606810591000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 1,
                "pricePerUnit": 1
            },
            parameters: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true,
            },
        },
        {
            id: "51ef3ada-6578-4e3f-aff2-c5591dfe5f36",
            "owner": {
                "launchedBy": "user"
            },
            "updates": [
                {
                    "timestamp": 1606811916000,
                    "state": "FAILURE"
                }
            ],
            "billing": {
                "creditsCharged": 0,
                "pricePerUnit": 1
            },
            parameters: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true
            },
        },
        {
            id: "16f0b2bc-db9b-468e-aadf-d6657cfa2494",
            "owner": {
                "launchedBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606811964000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 1,
                "pricePerUnit": 1
            },
            parameters: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true,
            },
        },
        {
            id: "bd562fdb-249a-43b9-909d-96279bf96d7b",
            "owner": {
                "launchedBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606812024000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 1,
                "pricePerUnit": 1
            },
            parameters: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true
            },
        },
        {
            id: "11d45177-c2c0-4254-9068-03f8ad2cdfeb",
            "owner": {
                "launchedBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606812666000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 11,
                "pricePerUnit": 1
            },
            parameters: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true,
            },
        },
        {
            id: "ad88b9f5-6bc9-40b4-b6be-bde7de38708e",
            "owner": {
                "launchedBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606830470000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 1,
                "pricePerUnit": 1
            },
            parameters: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true
            },
        },
        {
            id: "12a2717f-dd4c-4d24-a948-fe00cadc51e6",
            "owner": {
                "launchedBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606830635000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 1,
                "pricePerUnit": 1
            },
            parameters: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true,
            },
        },
        {
            id: "c7dccff3-ebd6-4ce8-95c8-1a85ed8b56d2",
            "owner": {
                "launchedBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606832894000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 1,
                "pricePerUnit": 1
            },
            parameters: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true,
            },
        },
        {
            id: "2b691748-9ad1-4ed3-bbe3-2f651245a041",
            "owner": {
                "launchedBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606833408000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 7,
                "pricePerUnit": 1
            },
            parameters: {
                application: {
                    name: "alpine",
                    version: "1"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true,
            },
        },
        {
            id: "94606ccd-af7f-43ce-b3bf-95e8858f12a1",
            "owner": {
                "launchedBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606834227000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 0,
                "pricePerUnit": 1
            },
            parameters: {
                application: {
                    name: "alpine",
                    version: "3"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 3,
                "allowDuplicateJob": true,
            },
        },
        {
            id: "aab92994-bed7-43f9-9ef4-cb9a7f60715b",
            "owner": {
                "launchedBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606834506000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 0,
                "pricePerUnit": 1
            },
            parameters: {
                application: {
                    name: "alpine",
                    version: "3"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 3,
                "allowDuplicateJob": true,
            },
        },
        {
            id: "9ce5332c-c52e-4b0a-b935-c5bd9a73f1d3",
            "owner": {
                "launchedBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606834715000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 12,
                "pricePerUnit": 1
            },
            parameters: {
                application: {
                    name: "alpine",
                    version: "2"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                replicas: 1,
                allowDuplicateJob: true,
            },
        },
        {
            id: "383f7238-9e70-4a8d-90a4-db99967300ab",
            owner: {
                launchedBy: "user",
            },
            updates: [
                {
                    timestamp: 1606834732000,
                    state: "SUCCESS",
                }
            ],
            billing: {
                creditsCharged: 1,
                pricePerUnit: 1
            },
            parameters: {
                application: {
                    name: "alpine",
                    version: "3"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                replicas: 1,
                allowDuplicateJob: true
            }
        },
        {
            id: "4f7f4075-1d15-42f0-8e3b-09450b514f9f",
            owner: {
                launchedBy: "user"
            },
            updates: [
                {
                    timestamp: 1606898310000,
                    state: "SUCCESS"
                }
            ],
            billing: {
                creditsCharged: 24,
                pricePerUnit: 1
            },
            parameters: {
                application: {
                    name: "alpine",
                    version: "3"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true
            },
        },
        {
            id: "127d2123-9520-4c41-813c-2d808b2746da",
            "owner": {
                "launchedBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606902107000,
                    "state": "SUCCESS",
                }
            ],
            "billing": {
                "creditsCharged": 62,
                "pricePerUnit": 1
            },
            parameters: {
                application: {
                    name: "coder",
                    version: "1.48.2"
                },
                product: {
                    id: "u1-standard-1",
                    "category": "u1-standard",
                    "provider": "ucloud"
                },
                "replicas": 1,
                "allowDuplicateJob": true,
            }
        },
        {
            id: "83fb8069-6a0e-4d7b-9910-a9e8c63624a5",
            "owner": {
                "launchedBy": "user",
            },
            "updates": [
                {
                    "timestamp": 1606902764000,
                    "state": "RUNNING",
                }
            ],
            "billing": {
                "creditsCharged": 0,
                "pricePerUnit": 1
            },
            parameters: {
                application: {
                    name: "coder",
                    version: "1.48.2"
                },
                product: {
                    id: "u1-standard-1",
                    category: "u1-standard",
                    provider: "ucloud"
                },
                replicas: 1,
                allowDuplicateJob: true
            },
        }
    ],
    "next": "20-cafbbfef-265d-4995-8bad-e44212541a76"
}

export const Browse: React.FunctionComponent = () => {
    useTitle("Runs")
    useSidebarPage(SidebarPages.Runs);

    const [infScrollId, setInfScrollId] = useState(0);
    const [jobs, fetchJobs] = useCloudAPI<UCloud.PageV2<UCloud.compute.Job>>(
        {noop: true},
        emptyPageV2
    );

    const refresh = useCallback(() => {
        fetchJobs(UCloud.compute.jobs.browse({itemsPerPage}));
        setInfScrollId(id => id + 1);
    }, []);

    const history = useHistory();

    useRefreshFunction(refresh);

    useLoading(jobs.loading);
    const projectId = useProjectId();

    useEffect(() => {
        fetchJobs(UCloud.compute.jobs.browse({itemsPerPage}));
    }, [projectId]);

    const loadMore = useCallback(() => {
        fetchJobs(UCloud.compute.jobs.browse({itemsPerPage, next: jobs.data.next}));
    }, [jobs.data]);

    const [checked, setChecked] = useState(new Set<string>());

    const pageRenderer = useCallback((page: UCloud.PageV2<UCloud.compute.Job>): React.ReactNode => (
        page.items.map(job => {
            const isExpired = isRunExpired(job);
            const latestUpdate = job.updates[job.updates.length - 1];
            const state = latestUpdate?.state;
            const hideExpiration = isExpired || job.parameters.timeAllocation === null || isJobStateFinal(state);
            return (
                <ListRow
                    key={job.id}
                    navigate={() => history.push(`/applications/results/${job.id}`)}
                    icon={<AppToolLogo size="36px" type="APPLICATION" name={job.parameters.application.name} />}
                    isSelected={checked.has(job.id)}
                    select={() => {
                        if (checked.has(job.id)) {
                            checked.delete(job.id)
                        } else {
                            checked.add(job.id);
                        }
                        setChecked(new Set(checked));
                    }}
                    left={<Text cursor="pointer">{job.parameters.name ?? shortUUID(job.id)}</Text>}
                    leftSub={<>
                        <ListRowStat color="gray" icon="id">
                            {job.parameters.application.name} v{job.parameters.application.version}
                        </ListRowStat>
                        <ListRowStat color="gray" color2="gray" icon="chrono">
                            {/* TODO: Created at timestamp */}
                            {/* Started {formatRelative(0, new Date(), {locale: enGB})} */}
                        </ListRowStat>
                    </>}
                    right={<>
                        {hideExpiration || job.parameters.timeAllocation == null ? null : (
                            <Text mr="25px">
                                {/* TODO: Expiration */}
                                {/* Expires {formatRelative(0, new Date(), {locale: enGB})} */}
                            </Text>
                        )}
                        <Flex width="110px">
                            <JobStateIcon state={state} isExpired={isExpired} mr="8px" />
                            <Flex mt="-3px">{isExpired ? "Expired" : prettierString(state ?? "")}</Flex>
                        </Flex>
                    </>}
                />
            )
        })
    ), [checked]);

    const cancelableJobs: string[] = [];

    /*
    for (const entry of checked) {
        const job = jobs.data.items.find(it => it.id === entry);
        if (job && checked.has(entry)  && inCancelableState(job.state)) {
            cancelableJobs.push(entry);
        }
    }
    */

    return <MainContainer
        main={
            <Pagination.ListV2
                page={jobs.data}
                loading={jobs.loading}
                onLoadMore={loadMore}
                pageRenderer={pageRenderer}
                infiniteScrollGeneration={infScrollId}
            />
        }

        sidebar={<FilterOptions onUpdateFilter={onUpdateFilter} cancelableJobs={[]} />}
    />;

    function onUpdateFilter(f: FetchJobsOptions) {
        console.log(f);
    }
};

type Filter = {text: string; value: string};
const dayInMillis = 24 * 60 * 60 * 1000;
const appStates: Filter[] =
    (["IN_QUEUE", "RUNNING", "CANCELING", "SUCCESS", "FAILURE"] as JobState[])
        .map(it => ({text: prettierString(it), value: it})).concat([{text: "Don't filter", value: "Don't filter" as JobState}]);


interface FetchJobsOptions {
    itemsPerPage?: number;
    pageNumber?: number;
    minTimestamp?: number;
    maxTimestamp?: number;
    filter?: string;
}

function FilterOptions({onUpdateFilter, cancelableJobs}: {
    onUpdateFilter: (filter: FetchJobsOptions) => void;
    cancelableJobs: string[];
}) {
    const [filter, setFilter] = useState({text: "Don't filter", value: "Don't filter"});
    const [firstDate, setFirstDate] = useState<Date | undefined>();
    const [secondDate, setSecondDate] = useState<Date | undefined>();

    function updateFilterAndFetchJobs(value: string) {
        setFilter({text: prettierString(value), value});
        onUpdateFilter({filter: value, minTimestamp: firstDate?.getTime(), maxTimestamp: secondDate?.getTime()});
    }

    function fetchJobsInRange(start?: Date, end?: Date) {
        onUpdateFilter({filter: filter.value, minTimestamp: start?.getTime(), maxTimestamp: end?.getTime()});
    }

    function fetchJobs() {
        // TODO
    }

    const startOfToday = getStartOfDay(new Date());
    const startOfYesterday = getStartOfDay(new Date(startOfToday.getTime() - dayInMillis));
    const startOfWeek = getStartOfWeek(new Date()).getTime();

    return (
        <Box pt={48}>
            <Heading.h3>
                Quick Filters
            </Heading.h3>
            <Box cursor="pointer" onClick={() => fetchJobsInRange(
                new Date(startOfToday),
                undefined
            )}>
                <TextSpan>Today</TextSpan>
            </Box>
            <Box
                cursor="pointer"
                onClick={() => fetchJobsInRange(
                    new Date(startOfYesterday),
                    new Date(startOfYesterday.getTime() + dayInMillis)
                )}
            >
                <TextSpan>Yesterday</TextSpan>
            </Box>
            <Box
                cursor="pointer"
                onClick={() => fetchJobsInRange(new Date(startOfWeek), undefined)}
            >
                <TextSpan>This week</TextSpan>
            </Box>
            <Box cursor="pointer" onClick={() => fetchJobsInRange()}><TextSpan>No filter</TextSpan></Box>
            <Heading.h3 mt={16}>Active Filters</Heading.h3>
            <Label>Filter by app state</Label>
            <ClickableDropdown
                chevron
                trigger={filter.text}
                onChange={updateFilterAndFetchJobs}
                options={appStates.filter(it => it.value !== filter.value)}
            />
            <Box mb={16} mt={16}>
                <Label>Job created after</Label>
                <InputGroup>
                    <DatePicker
                        placeholderText="Don't filter"
                        isClearable
                        selectsStart
                        showTimeInput
                        startDate={firstDate}
                        endDate={secondDate}
                        selected={firstDate}
                        onChange={(date: Date) => (setFirstDate(date), fetchJobsInRange(date, secondDate))}
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
                        showTimeInput
                        startDate={firstDate}
                        endDate={secondDate}
                        selected={secondDate}
                        onChange={(date: Date) => (setSecondDate(date), fetchJobsInRange(firstDate, date))}
                        onSelect={d => fetchJobsInRange(firstDate, d)}
                        timeFormat="HH:mm"
                        dateFormat="dd/MM/yy HH:mm"
                    />
                </InputGroup>
            </Box>
            <JobOperations cancelableJobs={cancelableJobs} onFinished={() => fetchJobs()} />
        </Box>
    );
}

// Old component pasted below

/*
const StickyBox = styled(Box)`
    position: sticky;
    top: 95px;
    z-index: 50;
`;


const Runs: React.FunctionComponent = () => {
    React.useEffect(() => {
        props.onInit();
        fetchJobs();
        props.setRefresh(() => fetchJobs());
        return (): void => props.setRefresh();
    }, []);

    function fetchJobs(options?: FetchJobsOptions): void {
        const opts = options ?? {};
        const {page, setLoading} = props;
        const itemsPerPage = opts.itemsPerPage != null ? opts.itemsPerPage : page.itemsPerPage;
        const pageNumber = opts.pageNumber != null ? opts.pageNumber : page.pageNumber;
        const sortOrder = opts.sortOrder != null ? opts.sortOrder : props.sortOrder;
        const sortBy = opts.sortBy != null ? opts.sortBy : props.sortBy;
        const minTimestamp = opts.minTimestamp != null ? opts.minTimestamp : undefined;
        const maxTimestamp = opts.maxTimestamp != null ? opts.maxTimestamp : undefined;
        const filterValue = opts.filter && opts.filter !== "Don't filter" ? opts.filter as JobState : undefined;

        setLoading(true);
        props.fetchJobs(itemsPerPage, pageNumber, sortOrder, sortBy, minTimestamp, maxTimestamp, filterValue);
        props.setRefresh(() =>
            props.fetchJobs(itemsPerPage, pageNumber, sortOrder, sortBy, minTimestamp, maxTimestamp, filterValue)
        );
    }

    const {page, loading, sortBy, sortOrder} = props;
    const {itemsPerPage, pageNumber} = page;
    const history = useHistory();

    const selectedAnalyses = page.items.filter(it => it.checked);
    const cancelableAnalyses = selectedAnalyses.filter(it => inCancelableState(it.state));

    const allChecked = selectedAnalyses.length === page.items.length && page.items.length > 0;

    const content = (
        <>
            <StickyBox backgroundColor="white">
                <Spacer
                    left={(
                        <Label ml={10} width="auto">
                            <Checkbox
                                size={27}
                                onClick={() => props.checkAllAnalyses(!allChecked)}
                                checked={allChecked}
                                onChange={stopPropagation}
                            />
                            <Box as={"span"}>Select all</Box>
                        </Label>
                    )}
                    right={(
                        <Box>
                            <ClickableDropdown
                                trigger={(
                                    <>
                                        <Icon
                                            cursor="pointer"
                                            name="arrowDown"
                                            rotation={sortOrder === SortOrder.ASCENDING ? 180 : 0}
                                            size=".7em"
                                            mr=".4em"
                                        />
                                        Sort by: {prettierString(sortBy)}
                                    </>
                                )}
                                chevron
                            >
                                <Box
                                    ml="-16px"
                                    mr="-16px"
                                    pl="15px"
                                    onClick={() => fetchJobs({
                                        sortOrder: sortOrder === SortOrder.ASCENDING ?
                                            SortOrder.DESCENDING : SortOrder.ASCENDING
                                    })}
                                >
                                    <>
                                        {prettierString(sortOrder === SortOrder.ASCENDING ?
                                            SortOrder.DESCENDING : SortOrder.ASCENDING
                                        )}
                                    </>
                                </Box>
                                <Divider />
                                {Object.values(RunsSortBy)
                                    .filter(it => it !== sortBy)
                                    .map((sortByValue: RunsSortBy, j) => (
                                        <Box
                                            ml="-16px"
                                            mr="-16px"
                                            pl="15px"
                                            key={j}
                                            onClick={() =>
                                                fetchJobs({sortBy: sortByValue, sortOrder: SortOrder.ASCENDING})}
                                        >
                                            {prettierString(sortByValue)}
                                        </Box>
                                    ))}
                            </ClickableDropdown>
                        </Box>
                    )}
                />
            </StickyBox>
            <List
                customEmptyPage={<Heading.h1>No jobs found.</Heading.h1>}
                loading={loading}
                pageRenderer={({items}) => (

                }
                page={page}
                onPageChanged={pageNumber => fetchJobs({pageNumber})}
            />
        </>
    );

    const defaultFilter = {text: "Don't filter", value: "Don't filter"};
    const [filter, setFilter] = React.useState(defaultFilter);
    const [firstDate, setFirstDate] = React.useState<Date | null>(null);
    const [secondDate, setSecondDate] = React.useState<Date | null>(null);

    const appStates = Object.keys(JobState)
        .filter(it => it !== "CANCELLING").map(it => ({text: prettierString(it), value: it}));
    appStates.push(defaultFilter);

    function fetchJobsInRange(minDate: Date | null, maxDate: Date | null) {
        return () => fetchJobs({
            itemsPerPage,
            pageNumber,
            sortOrder,
            sortBy,
            minTimestamp: minDate?.getTime() ?? undefined,
            maxTimestamp: maxDate?.getTime() ?? undefined,
            filter: filter.value === "Don't filter" ? undefined : filter.value
        });
    }

    const startOfToday = getStartOfDay(new Date());
    const dayInMillis = 24 * 60 * 60 * 1000;
    const startOfYesterday = getStartOfDay(new Date(startOfToday.getTime() - dayInMillis));
    const startOfWeek = getStartOfWeek(new Date()).getTime();

    function updateFilterAndFetchJobs(value: string): void {
        setFilter({text: prettierString(value), value});
        fetchJobs({
            itemsPerPage,
            pageNumber,
            sortBy,
            sortOrder,
            filter: value === "Don't filter" ? undefined : value as JobState
        });
    }

    const sidebar = (
        <Box pt={48}>
            <Heading.h3>
                Quick Filters
            </Heading.h3>
            <Box cursor="pointer" onClick={fetchJobsInRange(getStartOfDay(new Date()), null)}>
                <TextSpan>Today</TextSpan>
            </Box>
            <Box
                cursor="pointer"
                onClick={fetchJobsInRange(
                    new Date(startOfYesterday),
                    new Date(startOfYesterday.getTime() + dayInMillis)
                )}
            >
                <TextSpan>Yesterday</TextSpan>
            </Box>
            <Box
                cursor="pointer"
                onClick={fetchJobsInRange(new Date(startOfWeek), null)}
            >
                <TextSpan>This week</TextSpan>
            </Box>
            <Box cursor="pointer" onClick={fetchJobsInRange(null, null)}><TextSpan>No filter</TextSpan></Box>
            <Heading.h3 mt={16}>Active Filters</Heading.h3>
            <Label>Filter by app state</Label>
            <ClickableDropdown
                chevron
                trigger={filter.text}
                onChange={updateFilterAndFetchJobs}
                options={appStates.filter(it => it.value !== filter.value)}
            />
            <Box mb={16} mt={16}>
                <Label>Job created after</Label>
                <InputGroup>
                    <DatePicker
                        placeholderText="Don't filter"
                        isClearable
                        selectsStart
                        showTimeInput
                        startDate={firstDate}
                        endDate={secondDate}
                        selected={firstDate}
                        onChange={(date: Date) => (setFirstDate(date), fetchJobsInRange(date, secondDate)())}
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
                        showTimeInput
                        startDate={firstDate}
                        endDate={secondDate}
                        selected={secondDate}
                        onChange={(date: Date) => (setSecondDate(date), fetchJobsInRange(firstDate, date)())}
                        onSelect={d => fetchJobsInRange(firstDate, d)}
                        timeFormat="HH:mm"
                        dateFormat="dd/MM/yy HH:mm"
                    />
                </InputGroup>
            </Box>
            <AnalysisOperations cancelableAnalyses={cancelableAnalyses} onFinished={() => fetchJobs()} />
        </Box>
    );

    return (
        <MainContainer
            header={(
                <Spacer
                    left={null}
                    right={(
                        <Box width="170px">
                            <EntriesPerPageSelector
                                content="Jobs per page"
                                entriesPerPage={page.itemsPerPage}
                                onChange={items => fetchJobs({itemsPerPage: items})}
                            />
                        </Box>
                    )}
                />
            )}
            headerSize={48}
            sidebarSize={340}
            main={content}
            sidebar={sidebar}
        />
    );
}

*/


interface AnalysisOperationsProps {
    cancelableJobs: string[];
    onFinished: () => void;
}

function JobOperations({cancelableJobs, onFinished}: AnalysisOperationsProps): JSX.Element | null {
    if (cancelableJobs.length === 0) return null;
    return (
        <Button
            fullWidth
            color="red"
            onClick={() => {
                /* cancelJobDialog({
                jobCount: cancelableAnalyses.length,
                jobId: cancelableAnalyses[0].id,
                onConfirm: async () => {
                    try {
                        await Promise.all(cancelableAnalyses.map(a => cancelJob(Client, a.id)));
                        snackbarStore.addSuccess("Jobs cancelled", false);
                    } catch (e) {
                        snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred "), false);
                    } finally {
                        onFinished();
                    }
                }
                }) */
                console.log("FOOO-do.")
                onFinished();
            }}
        >
            Cancel selected ({cancelableJobs.length}) jobs
        </Button>
    );
}

export default Browse;