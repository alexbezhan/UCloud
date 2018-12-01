import * as React from "react";
import { Link } from "react-router-dom";
import { ProjectMetadata } from "./api";
import { DefaultLoading } from "LoadingIcon/LoadingIcon";
import * as ReactMarkdown from "react-markdown";
import { Contributor, getByPath } from "./api";
import { findLicenseByIdentifier } from "./licenses";
import { blankOrUndefined } from "UtilityFunctions";
import {
    Label,
    Icon,
    List,
    Header,
    Popup,
    Grid
} from "semantic-ui-react";
import { getQueryParam, RouterLocationProps } from "Utilities/URIUtilities";
import { projectEditPage } from "Utilities/ProjectUtilities";

interface ViewProps {
    metadata: ProjectMetadata
    canEdit?: boolean
}

const filePathFromProps = (props: RouterLocationProps): string | null => {
    return getQueryParam(props, "filePath");
}

export const View = (props: ViewProps) => {
    const { canEdit } = props;
    const metadata = handleNullArrays(props.metadata);
    const license = metadata.license ? findLicenseByIdentifier(metadata.license) : null;

    return <div>
        <Header as="h1">
            <Header.Content>
                {metadata.title}
                <Header.Subheader>
                    <List horizontal>
                        {metadata.contributors.map((it, idx) => (
                            <ContributorItem contributor={it} key={idx} />
                        ))}
                    </List>
                </Header.Subheader>
            </Header.Content>
        </Header>
        <Grid stackable divided>
            <Grid.Column width={12}>
                <ReactMarkdown source={metadata.description} />
            </Grid.Column>
            <Grid.Column width={4}>
                {canEdit ?
                    <>
                        <Header as="h4">
                            <Icon name="hand pointer" />
                            <Header.Content>Actions</Header.Content>
                        </Header>
                        <List>
                            <List.Item>
                                <Link to={projectEditPage(metadata.sduCloudRoot)}>
                                    <Label color='blue' className="metadata-detailed-tag">
                                        <Icon name='edit' />
                                        Edit
                                    </Label>
                                </Link>
                            </List.Item>
                        </List>
                    </>
                    : null
                }
                <Header as="h4">
                    <Icon name="info" />
                    <Header.Content>About</Header.Content>
                </Header>
                <List>
                    {license ?
                        <List.Item>
                            <a href={license.link} target="_blank" rel="noopener">
                                <Label color='blue' className="metadata-detailed-tag">
                                    <Icon name='book' />
                                    {license.identifier}
                                    <Label.Detail>License</Label.Detail>
                                </Label>
                            </a>
                        </List.Item>
                        : null
                    }
                </List>

                <Header as="h4">
                    <Icon name="hashtag" />
                    <Header.Content>Keywords</Header.Content>
                </Header>
                <List>
                    {
                        metadata.keywords.map((it, idx) => (
                            <List.Item key={idx}>
                                <Label className="metadata-detailed-tag">{it}</Label>
                            </List.Item>
                        ))
                    }
                </List>

                <Header as="h4">
                    <Icon name="bookmark" />
                    <Header.Content>References</Header.Content>
                </Header>
                <List>
                    {
                        metadata.references.map((it, idx) => (
                            <List.Item key={idx}>
                                <PotentialDOIBadge identifier={it} />
                            </List.Item>
                        ))
                    }
                </List>

                <Header as="h4">
                    <Icon name="money" />
                    <Header.Content>Grants</Header.Content>
                </Header>
                <List>
                    {
                        metadata.grants.map((it, idx) => (
                            <List.Item key={idx}>
                                <PotentialDOIBadge identifier={it.id} />
                            </List.Item>
                        ))
                    }
                </List>

            </Grid.Column>
        </Grid>
    </div>;
}

const ContributorItem = (props: { contributor: Contributor }) => {
    const { contributor } = props;
    if (
        !blankOrUndefined(contributor.affiliation) ||
        !blankOrUndefined(contributor.gnd) ||
        !blankOrUndefined(contributor.orcId)
    ) {
        return <Popup
            trigger={
                <List.Item>
                    <a href="#" onClick={(e) => e.preventDefault()}>
                        <Icon name="user" />
                        {contributor.name}
                    </a>
                </List.Item>
            }
            content={
                <>
                    {!blankOrUndefined(contributor.affiliation) ?
                        <p><b>Affiliation:</b> {contributor.affiliation}</p>
                        : null
                    }
                    {!blankOrUndefined(contributor.gnd) ?
                        <p><b>GND:</b> {contributor.gnd}</p>
                        : null
                    }
                    {!blankOrUndefined(contributor.orcId) ?
                        <p>
                            <b>ORCID:</b>
                            {" "}
                            <a href={`https://orcid.org/${contributor.orcId}`} target="_blank" rel="noopener">
                                {contributor.orcId}
                            </a>
                        </p>
                        : null
                    }
                </>
            }
            on="click"
            position="bottom left"
            {...props}
        />
    } else {
        return <List.Item icon="user" content={contributor.name} {...props} />
    }
};

interface ManagedViewState {
    metadata?: ProjectMetadata
    canEdit?: boolean
    errorMessage?: string
}

export class ManagedView extends React.Component<any, ManagedViewState> {
    constructor(props) {
        super(props);
        this.state = {}
    }

    // TODO This is not the correct place to do this!
    componentDidMount() {
        const urlPath = filePathFromProps(this.props as RouterLocationProps);
        if (!!this.state.metadata) return;
        if (!urlPath) {
            console.warn("TODO Not found");
            return;
        }

        getByPath(urlPath)
            .then(it => this.setState(() => ({ metadata: handleNullArrays(it.metadata), canEdit: it.canEdit })))
            .catch(() => console.warn("TODO something went wrong"));
    }

    render() {
        if (!this.state.metadata) {
            return <DefaultLoading size={undefined} loading className="" />;
        } else {
            return <View canEdit={this.state.canEdit} metadata={this.state.metadata} />;
        }
    }
}

// TODO find more elegant solution
const handleNullArrays = (metadata: ProjectMetadata): ProjectMetadata => {
    const mData = { ...metadata };
    mData.contributors = mData.contributors ? mData.contributors : [];
    mData.keywords = mData.keywords ? mData.keywords : [];
    mData.references = mData.references ? mData.references : [];
    mData.grants = mData.grants ? mData.grants : [];
    return mData;
};

const isIdentifierDOI = (identifier: string): boolean => {
    return /^10\..+\/.+$/.test(identifier);
};

const DOIBadge = (props: { identifier: string }) => {
    const { identifier } = props;
    return <a href={`https://doi.org/${identifier}`} target="_blank" rel="noopener">
        <Label className="metadata-detailed-tag" color="blue">
            {identifier}
        </Label>
    </a>;
}

const PotentialDOIBadge = (props: { identifier: string }) => {
    if (isIdentifierDOI(props.identifier)) {
        return <DOIBadge identifier={props.identifier} />;
    }
    return <Label className="metadata-detailed-tag">{props.identifier}</Label>;
};