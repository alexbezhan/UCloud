import React from "react";
import { Button, Table, Header, Responsive, Icon } from "semantic-ui-react";
import { Link } from "react-router-dom";
import { toLowerCaseAndCapitalize } from "../../UtilityFunctions";
import { NotConnectedToZenodo } from "../../ZenodoPublishingUtilities";
import { updatePageTitle } from "../../Actions/Status";
import { fetchPublications, setZenodoLoading } from "../../Actions/Zenodo";
import { connect } from "react-redux";
import { dateToString } from "../../Utilities/DateUtilities";
import { List } from "../Pagination/List";

class ZenodoHome extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            sorting: {
                lastSorting: "lastUpdate",
                asc: true,
            }
        };
        const { updatePageTitle, fetchPublications } = props;
        updatePageTitle("Zenodo Publications");
        fetchPublications(0, 10);
    }

    render() {
        const { connected, loading, fetchPublications, page } = this.props;
        if (!connected && !loading) {
            return (<NotConnectedToZenodo />);
        } else {
            return (
                <React.Fragment>
                    <Icon className="float-right" link circular name="sync" onClick={() => fetchPublications(page.pageNumber, page.itemsPerPage)} loading={loading} />
                    <Header as="h2">
                        <Header.Content className="mobile-padding">
                            Upload progress
                        </Header.Content>
                        <Responsive as={Header.Subheader} minWidth={768}>
                            Connected to Zenodo
                        </Responsive>
                    </Header>
                    <List
                        loading={loading}
                        itemsPerPage={page.itemsPerPage}
                        currentPage={page.pageNumber}
                        pageRenderer={(page) =>
                            <Table basic="very">
                                <TableHeader />
                                <Table.Body>
                                    {page.items.map((it, i) => (<PublicationRow publication={it} key={i} />))}
                                </Table.Body>
                            </Table>
                        }
                        results={page}
                        onItemsPerPageChanged={(size) => fetchPublications(0, size)}
                        onPageChanged={(pageNumber) => fetchPublications(pageNumber, page.itemsPerPage)}
                    />
                    <Link to="/zenodo/publish/">
                        <Button className="top-margin">Create new upload</Button>
                    </Link>
                </React.Fragment >

            );
        }
    }
}

const TableHeader = () => (
    <Table.Header>
        <Table.Row>
            <Table.HeaderCell>ID</Table.HeaderCell>
            <Table.HeaderCell>Name</Table.HeaderCell>
            <Table.HeaderCell>Status</Table.HeaderCell>
            <Table.HeaderCell />
            <Table.HeaderCell>Info</Table.HeaderCell>
            <Table.HeaderCell>Last update</Table.HeaderCell>
        </Table.Row>
    </Table.Header>
);

const PublicationRow = ({ publication }) => {
    let actionButton = null;
    if (publication.zenodoAction) {
        actionButton = (
            <a href={publication.zenodoAction} target="_blank">
                <Button content="Finish publication at Zenodo" />
            </a>);
    }
    return (
        <Table.Row>
            <Table.Cell>{publication.id}</Table.Cell>
            <Table.Cell>{publication.name}</Table.Cell>
            <Table.Cell>{toLowerCaseAndCapitalize(publication.status)}</Table.Cell>
            <Table.Cell>{actionButton}</Table.Cell>
            <Table.Cell>
                <Link to={`/zenodo/info/${window.encodeURIComponent(publication.id)}`}>
                    <Button>Show More</Button>
                </Link>
            </Table.Cell>
            <Table.Cell>{dateToString(publication.modifiedAt)}</Table.Cell>
        </Table.Row>);
}

const mapDispatchToProps = (dispatch) => ({
    fetchPublications: (pageNo, pageSize) => {
        dispatch(setZenodoLoading(true));
        dispatch(fetchPublications(pageNo, pageSize))
    },
    updatePageTitle: () => dispatch(updatePageTitle("Zenodo Overview"))
});

const mapStateToProps = (state) => ({ connected, loading, page } = state.zenodo);
export default connect(mapStateToProps, mapDispatchToProps)(ZenodoHome);
