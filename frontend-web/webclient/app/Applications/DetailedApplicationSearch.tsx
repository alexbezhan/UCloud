import {DetailedApplicationOperations, DetailedApplicationSearchReduxState} from "Applications";
import {KeyCode, ReduxObject} from "DefaultObjects";
import {SearchStamps} from "Files/DetailedFileSearch";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {Box, Button, Checkbox, Flex, Input, Label} from "ui-components";
import * as Heading from "ui-components/Heading";
import {
    clearTags,
    fetchApplications,
    setAppQuery,
    setShowAllVersions,
    tagAction
} from "./Redux/DetailedApplicationSearchActions";

interface DetailedApplicationSearchProps extends
    DetailedApplicationOperations, DetailedApplicationSearchReduxState {
    onSearch: () => void;
    defaultAppQuery?: string;
}

function DetailedApplicationSearch(props: Readonly<DetailedApplicationSearchProps>) {
    React.useEffect(() => {
        if (!!props.defaultAppQuery) props.setAppQuery(props.defaultAppQuery);
    }, []);

    const ref = React.useRef<HTMLInputElement>(null);

    function onSearch(e: React.FormEvent<HTMLFormElement>) {
        e.preventDefault();
        props.addTag(ref.current!.value);
        ref.current!.value = "";
        props.onSearch();
    }

    return (
        <Flex flexDirection="column" pl="0.5em" pr="0.5em">
            <Box mt="0.5em">
                <form onSubmit={e => onSearch(e)}>
                    <Heading.h5 pb="0.3em" pt="0.5em">Show All Versions</Heading.h5>
                    <Flex>
                        <Label fontSize={1} color="black">
                            <Checkbox
                                checked={(props.showAllVersions)}
                                onChange={e => e.stopPropagation()}
                                onClick={props.setShowAllVersions}
                            />
                        </Label>
                    </Flex>
                    <Heading.h5 pb="0.3em" pt="0.5em">Tags</Heading.h5>
                    <SearchStamps
                        clearAll={() => props.clearTags()}
                        onStampRemove={stamp => props.removeTag(stamp)}
                        stamps={props.tags}
                    />
                    <Input
                        pb="6px"
                        pt="8px"
                        mt="-2px"
                        width="100%"
                        ref={ref}
                        onKeyDown={e => {
                            if (e.keyCode === KeyCode.ENTER) {
                                e.preventDefault();
                                props.addTag(ref.current!.value);
                                ref.current!.value = "";
                            }
                        }}
                        placeholder="Add tag with enter..."
                    />
                    <Button mt="0.5em" type="submit" fullWidth disabled={props.loading} color="blue">Search</Button>
                </form>
            </Box>
        </Flex>);
}

const mapStateToProps = ({detailedApplicationSearch}: ReduxObject) => ({
    ...detailedApplicationSearch,
    sizeCount: detailedApplicationSearch.tags.size
});
const mapDispatchToProps = (dispatch: Dispatch): DetailedApplicationOperations => ({
    setAppQuery: appQuery => dispatch(setAppQuery(appQuery)),
    addTag: tags => dispatch(tagAction("DETAILED_APPS_ADD_TAG", tags)),
    removeTag: tag => dispatch(tagAction("DETAILED_APPS_REMOVE_TAG", tag)),
    clearTags: () => dispatch(clearTags()),
    setShowAllVersions: () => dispatch(setShowAllVersions()),
    fetchApplications: async (body, callback) => {
        dispatch(await fetchApplications(body));
        if (typeof callback === "function") callback();
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(DetailedApplicationSearch);
