import * as React from "react";
import * as API from "./api";
import Table, { TableRow, TableCell, TableBody, TableHeader, TableHeaderCell } from "ui-components/Table";
import { Dropdown, DropdownContent } from "ui-components/Dropdown";
import { Text, Flex } from "ui-components";
import * as moment from "moment";
import { data as MockEvents } from "./mock/events.json";
import styled from "styled-components";

const BreakdownItem: React.FunctionComponent<{ item: API.AccountingEvent }> = props => {
    return <VARow>
        <TableCell>
            <Dropdown>
                <Text fontSize={1} color="text">{moment(new Date(props.item.timestamp)).fromNow()}</Text>
                <DropdownContent>
                    {moment(new Date(props.item.timestamp)).format("llll")}
                </DropdownContent>
            </Dropdown>
        </TableCell>
        <TableCell>
            <Flex>
                <Text fontSize={2}>{props.item.title}</Text>
            </Flex>
        </TableCell>
        <TableCell>
            {props.item.description}
        </TableCell>
    </VARow>;
};

const VARow = styled(TableRow)`
    vertical-align: top;
`;

interface BreakdownProps {
    events?: API.AccountingEvent[]
}

function Breakdown(props: BreakdownProps) {
    const events: API.AccountingEvent[] = props.events || MockEvents.items;
    return <Table>
        <LeftAlignedTableHeader>
            <TableRow>
                <TableHeaderCell>Time</TableHeaderCell>
                <TableHeaderCell>Type</TableHeaderCell>
                <TableHeaderCell>Description</TableHeaderCell>
            </TableRow>
        </LeftAlignedTableHeader>
        <TableBody>
            {events.map((e, idx) => <BreakdownItem item={e} key={idx} />)}
        </TableBody>
    </Table>;
}

const LeftAlignedTableHeader = styled(TableHeader)`
    text-align: left;
`;

export default Breakdown;