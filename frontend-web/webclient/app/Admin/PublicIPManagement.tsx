import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import format from "date-fns/format";
import {emptyPage} from "DefaultObjects";
import {MainContainer} from "MainContainer/MainContainer";
import * as React from "react";
import {useDispatch} from "react-redux";
import styled from "styled-components";
import {Button, ButtonGroup, List, Text, Truncate} from "ui-components";
import {ListRow} from "ui-components/List";
import {setUploaderCallback} from "Uploader/Redux/UploaderActions";
import {addStandardDialog, Lorem} from "UtilityComponents";

const baseContext = "/hpc/ip/";

function listAddressApplicationsForApprovalRequest(payload: PaginationRequest): APICallParameters<PaginationRequest> {
    return {
        path: `${baseContext}review-applications`,
        method: "GET",
        payload
    };
}

function rejectAddress(id: number): APICallParameters<{id: number}> {
    return {
        path: `${baseContext}reject`,
        method: "POST",
        payload: {id}
    };
}

function acceptAddress(id: number): APICallParameters<{id: number}> {
    return {
        path: `${baseContext}reject`,
        method: "POST",
        payload: {id}
    };
}

interface AddressApplication {
    id: number;
    application: string;
    createdAt: number;
}

export function PublicIPManagement(): JSX.Element {
    const [ipsForApproval, setParams, params] = useCloudAPI<Page<AddressApplication>>(
        listAddressApplicationsForApprovalRequest({itemsPerPage: 25, page: 0}),
        emptyPage);

    const [, sendCommand] = useAsyncCommand();
    const dispatch = useDispatch();

    const reload = (): void => setParams({...params});
    React.useEffect(() => {
        dispatch(setUploaderCallback(() => reload()));
    }, [reload]);

    return (<MainContainer
        main={
            <List>
                {ipsForApproval.data.items.map(it =>
                    <ListRow
                        key={it.id}
                        left={
                            <HoverTruncate width="calc(100% - 50px)" fontSize={20}>
                                <Lorem />
                            </HoverTruncate>
                        }
                        icon={null}
                        leftSub={<Text fontSize={0} color="gray">Submitted {format(new Date(it.createdAt), "d LLL yyyy HH:mm")}</Text>}
                        right={<ButtonGroup ml="-30px" width="200px">
                            <Button color="green" onClick={() => {
                                addStandardDialog({
                                    title: "Approve IP?",
                                    message: "",
                                    onConfirm: async () => {
                                        await sendCommand(acceptAddress(it.id));
                                        reload();
                                    },
                                    confirmText: "Approve"
                                });
                            }}>Approve</Button>
                            <Button color="red" onClick={async () => {
                                addStandardDialog({
                                    title: "Reject IP?",
                                    message: "",
                                    onConfirm: async () => {
                                        await sendCommand(rejectAddress(it.id));
                                        reload();
                                    },
                                    confirmText: "Reject"
                                });
                            }}>Reject</Button>
                        </ButtonGroup>}
                    />
                )}
            </List >
        }
    />);
}

const HoverTruncate = styled(Truncate)`
    &:hover {
        white-space: normal;
    }
`;
