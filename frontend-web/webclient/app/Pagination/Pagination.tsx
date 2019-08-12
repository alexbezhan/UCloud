import * as React from "react";
import ClickableDropdown from "ui-components/ClickableDropdown";
import { Text, Flex, Button, theme, Input, OutlineButton } from "ui-components";
import styled from "styled-components";
import { TextSpan } from "ui-components/Text";

const EntriesPerPageSelectorOptions = [
    { key: 1, text: "10", value: 10 },
    { key: 2, text: "25", value: 25 },
    { key: 3, text: "50", value: 50 },
    { key: 4, text: "100", value: 100 }
];

const handleBoundaries = (page: string, maxPage: number) => 
    Math.max(Math.min(parseInt(page), maxPage - 1), 0);

interface PaginationButtons { totalPages: number, currentPage: number, toPage: (p: number) => void }
export function PaginationButtons({ totalPages, currentPage, toPage }: PaginationButtons) {
    if (totalPages <= 1) return null;
    const ref = React.useRef<HTMLInputElement>(null);
    const inputField = (
        <Flex ml="15px" width="75px">
            {totalPages > 20 ? (<>
                <Input defaultValue={"1"} autoComplete="off" type="number" min={1} max={totalPages} ref={ref} />
                <OutlineButton ml="2px" fullWidth onClick={() => toPage(ref.current && handleBoundaries(ref.current.value, totalPages) || 0)}>→</OutlineButton>
            </>) : null}
        </Flex>);
    const half = Math.floor((totalPages - 1) / 2);
    const upperQuarter = Math.floor(half + half / 2);
    const lowerQuarter = Math.floor(half - half / 2);
    const pages = [...new Set([0, totalPages - 1, currentPage - 1, currentPage, currentPage + 1, half, upperQuarter, lowerQuarter].sort((a, b) => a - b))];
    const buttons = pages.filter(i => i >= 0 && i < totalPages).map((it, i, arr) =>
        it - arr[i + 1] < -1 ? ( // If the two numbers do not immediately follow each other, insert ellipses
            <React.Fragment key={it}>
                <PaginationButton onClick={() => toPage(it)}>{it + 1}</PaginationButton>
                <PaginationButton onClick={() => undefined} unclickable>{"..."}</PaginationButton>
            </React.Fragment>
        ) : (
                <PaginationButton key={it} unclickable={currentPage === it} color={currentPage === it ? "gray" : "black"} onClick={() => toPage(it)}>{it + 1}</PaginationButton>
            )
    );
    return (
        <PaginationGroup justifyContent="center" my="1em">
            <PaginationButton onClick={() => toPage(currentPage - 1)} unclickable={currentPage === 0}>{"⟨"}</PaginationButton>
            {buttons}
            <PaginationButton onClick={() => toPage(currentPage + 1)} unclickable={currentPage === totalPages - 1}>{"⟩"}</PaginationButton>
            {inputField}
        </PaginationGroup>
    );
}


const PaginationButtonBase = styled(Button) <{ unclickable?: boolean }>`
    color: ${props => props.theme.colors.text};
    background-color: ${props => props.unclickable ? props.theme.colors.paginationDisabled : "transparent"};
    border-color: ${props => props.theme.colors.borderGray};
    border-width: 1px;
    &:disabled {
        opacity: 1;
    }
    border-right-width: 0px;
    &:hover {
        filter: brightness(100%);
        background-color: ${props => props.unclickable ? null : props.theme.colors.paginationHoverColor};
        cursor: ${props => props.unclickable ? "default" : null};
        transform: none;
    }
`;


const PaginationButton = ({ onClick, ...props }) => (
    props.unclickable ? <PaginationButtonBase {...props} /> : <PaginationButtonBase onClick={onClick} {...props} />
);

PaginationButtonBase.defaultProps = {
    theme
};

const PaginationGroup = styled(Flex)`
    & > ${PaginationButtonBase} {
        width: auto;
        min-width: 42px;

        padding-left: 0px;
        padding-right: 0px;
        border-radius: 0px;
    }

    & > ${PaginationButtonBase}:nth-last-child(2) {
        border-radius: 0 3px 3px 0;
        border-right-width: 1px;
    }

    & > ${PaginationButtonBase}:first-child {
        border-radius: 3px 0 0 3px;
    }
`;

interface EntriesPerPageSelector {
    entriesPerPage: number,
    onChange: (size: number) => void,
    content?: string
}

export const EntriesPerPageSelector = ({
    entriesPerPage,
    onChange,
    content
}: EntriesPerPageSelector) => (
        <ClickableDropdown left={"85px"} minWidth={"80px"} width={"80px"} chevron
            trigger={<TextSpan> {`${content} ${entriesPerPage}`}</TextSpan>}>
            {EntriesPerPageSelectorOptions.map((opt, i) =>
                <Text ml="-17px" pl="17px" mr="-17px" cursor={"pointer"} key={i} onClick={() => entriesPerPage === opt.value ? undefined : onChange(opt.value)}>
                    {opt.text}
                </Text>
            )}
        </ClickableDropdown>
    );