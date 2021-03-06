import * as React from "react";
import {Link} from "react-router-dom";
import {Flex, ToggleBadge} from "ui-components";
import {Card} from "ui-components/Card";

export const Tabs: React.FunctionComponent = (props) => (
    <Card>
        <Flex>
            {props.children}
        </Flex>
    </Card>
);

interface TabProps {
    selected?: boolean;
    linkTo: string;
}

export const Tab: React.FunctionComponent<TabProps> = (props): JSX.Element => (
    <Link to={props.linkTo}>
        <ToggleBadge
            bg="lightGray"
            pb="12px"
            pt="10px"
            fontSize={2}
            color={"black"}
            selected={props.selected}
        >
            {props.children}
        </ToggleBadge>
    </Link>
);

export const Navigation = (props: {selected?: Pages}): JSX.Element => (
    <Tabs>
        <Tab linkTo="/applications" selected={props.selected === Pages.BROWSE}>Browse</Tab>
        <Tab linkTo="/applications/installed" selected={props.selected === Pages.INSTALLED}>My Applications</Tab>
        <Tab linkTo="/applications/results" selected={props.selected === Pages.RESULTS}>Results</Tab>
    </Tabs>
);

export enum Pages {
    INSTALLED,
    BROWSE,
    RESULTS
}
