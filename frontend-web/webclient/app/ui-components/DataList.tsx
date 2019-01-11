import * as React from "react";
import { Box, Input, FormField, Icon } from "ui-components";
import ClickableDropdown from "./ClickableDropdown";
import { allLicenses } from "Project/licenses";
import * as Fuse from "fuse.js";

const identifierTypes = [
    {
        text: "Cited by",
        value: "isCitedBy"
    },
    {
        text: "Cites",
        value: "cites"
    },
    {
        text: "Supplement to",
        value: "isSupplementTo"
    },
    {
        text: "Supplemented by",
        value: "“isSupplementedBy”"
    },
    {
        text: "New version of",
        value: "isNewVersionOf"
    },
    {
        text: "Previous version of",
        value: "isPreviousVersionOf"
    },
    {
        text: "Part of",
        value: "“isPartOf”"
    },
    {
        text: "Has part",
        value: "“hasPart”"
    },
    {
        text: "Compiles",
        value: "compiles"
    },
    {
        text: "Is compiled by",
        value: "isCompiledBy"
    },
    {
        text: "Identical to",
        value: "isIdenticalTo"
    },
    {
        text: "Alternative identifier",
        value: "IsAlternateIdentifier"
    }
];

export const contentValuePairLicenses = allLicenses.map(it => ({ content: it.name, value: it.identifier }))
export const contentValuePairIdentifierTypes = identifierTypes.map(it => ({ content: it.text, value: it.value }))

type ContentValuePair = { content: string, value: string };

interface DataListProps {
    options: ContentValuePair[];
    onSelect: (value: string) => void
    placeholder: string
    width?: number | string
    clearOnSelect?: boolean
}
export class DataList extends React.PureComponent<DataListProps, { text: string, fuse: Fuse<ContentValuePair> }> {
    constructor(props: DataListProps) {
        super(props);
        this.state = {
            text: "",
            fuse: new Fuse(this.props.options, this.options)
        }
    }

    private readonly totalShown = 8;

    private onSelect(content: string, value: string) {
        this.props.onSelect(value);
        if (this.state.text && this.props.clearOnSelect) this.setState(() => ({ text: "" }));
        else this.setState(() => ({ text: content }))
    }

    get options(): Fuse.FuseOptions<ContentValuePair> {
        return {
            shouldSort: true,
            threshold: 0.2,
            location: 0,
            distance: 100,
            maxPatternLength: 32,
            minMatchCharLength: 1,
            keys: [
                "content"
            ]
        };
    }

    render() {
        const results = this.state.text ? this.state.fuse.search(this.state.text) : this.props.options.slice(0, this.totalShown);
        return (
            <ClickableDropdown colorOnHover={results.length !== 0} fullWidth trigger={
                <FormField>
                    <Input
                        placeholder={this.props.placeholder}
                        autoComplete="off"
                        type="text"
                        value={this.state.text}
                        onChange={({ target }) => this.setState(() => ({ text: target.value }))}
                    />
                    <Icon name="chevronDown" mb="9px" size={14} />
                </FormField>
            }>
                {results.map(({ content, value }) => (
                    <Box key={content} onClick={() => this.onSelect(content, value)} mb="0.5em">
                        {content}
                    </Box>
                ))}
                {results.length > this.totalShown ? <Box mb="0.5em">...</Box> : null}
                {results.length === 0 ? <Box mb="0.5em">No Results</Box> : null}
            </ClickableDropdown>);
    }
}

export default DataList;