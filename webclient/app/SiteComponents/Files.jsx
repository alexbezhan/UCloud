import React from 'react';
import LoadingIcon from './LoadingIcon';
import {Cloud} from "../../authentication/SDUCloudObject";
import {Link} from 'react-router';
import {Button} from 'react-bootstrap';


class Files extends React.Component {
    constructor(props) {
        super(props);
        let currentPath = (!props.routeParams.splat) ? `home/${Cloud.username}` : props.routeParams.splat;
        this.state = {
            files: [],
            loading: false,
            currentPage: 0,
            filesPerPage: 10,
            masterCheckbox: false,
            currentPath: currentPath,
            selectedFiles: [],
        };
        this.getFiles = this.getFiles.bind(this);
    }

    getFavourites() {
        // TODO
        console.log("GET FAVOURITES TODO")
    }

    favourite(file) {
        // TODO
        console.log("FAVOURITE TODO")
    }

    prevent() {
        // TODO
        console.log("PREVENT TODO")
    }

    getFiles() {
        this.setState({
            loading: true,
        });
        Cloud.get("files?path=/" + this.state.currentPath).then(favourites => {
            favourites.sort((a, b) => {
                if (a.type === "DIRECTORY" && b.type !== "DIRECTORY")
                    return -1;
                else if (b.type === "DIRECTORY" && a.type !== "DIRECTORY")
                    return 1;
                else {
                    return a.path.name.localeCompare(b.path.name);
                }
            });
            this.setState(() => ({
                files: favourites,
                loading: false,
            }));
        });
    }

    componentWillMount() {
        this.getFiles();
    }

    render() {
        return (
            <section>
                <div className="container-fluid">
                    <div className="col-lg-10">
                        <LoadingIcon loading={this.state.loading}/>
                        <Breadcrumbs currentPath={this.state.currentPath}/>
                        <div className="card">
                            <FilesTable files={this.state.files} loading={this.state.loading}
                                        getFavourites={() => this.getFavourites} favourite={() => this.favourite}
                                        prevent={this.prevent}/>
                        </div>
                    </div>
                    <ContextBar selectedFiles={this.state.selectedFiles} getFavourites={() => this.getFavourites()}/>
                </div>
            </section>)
    }
}

function ContextBar(props) {
    return (
        <div className="col-lg-2 visible-lg">
            <div>
                <div className="center-block text-center">
                    <Button className="btn btn-link btn-lg" onClick={() => props.getFavourites()}><i
                        className="icon ion-star"/></Button>
                    <Link className="btn btn-link btn-lg" to={`files?path=/home/${Cloud.username}`}><i
                        className="icon ion-ios-home"/></Link>
                </div>
                <hr/>
                <button className="btn btn-primary ripple btn-block ion-android-upload" onClick="uploadFile"> Upload
                    Files
                </button>
                <br/>
                <button className="btn btn-default ripple btn-block ion-folder" onClick="createFolder">
                    New folder
                </button>
                <br/>
                <hr/>
                <FileOptions selectedFiles={props.selectedFiles}/>
            </div>
        </div>
    )
}

function FileOptions(props) {
    if (!props.selectedFiles.length) {
        return null;
    }
    let rightsLevel = (<h3 if="selectedFiles.length">
        {'Rights level: ' + options.rightsName}<br/>
        {fileText}</h3>);
    return (
        <div>
            <p>
                <button className="btn btn-info rippple btn-block"
                        click="sendToAbacus()"> Send to Abacus 2.0
                </button>
            </p>
            <p>
                <button type="button" className="btn btn-default ripple btn-block ion-share"
                        title="getTitle('share')"
                        click="shareFile(selectedFiles[0].path.name, 'folder')"> Share selected
                    files
                </button>
            </p>
            <p>
                <button className="btn btn-default ripple btn-block ion-ios-download"
                        title="getTitle('download')">
                    Download selected files
                </button>
            </p>
            <p>
                <button type="button" class="btn btn-default btn-block ripple ion-android-star">
                    Favourite selected files
                </button>
            </p>
            <p>
                <button class="btn btn-default btn-block ripple ion-ios-photos"
                        title="getTitle('move')">
                    Move folder
                </button>
            </p>
            <p>
                <button type="button" class="btn btn-default btn-block ripple ion-ios-compose"
                        click="renameFile(selectedFiles[0].path.name, 'folder')"
                        title="getTitle('rename')"
                        disabled="options.rightsLevel < 3 || selectedFiles.length !== 1">
                    Rename file
                </button>
            </p>
            <p>
                <button class="btn btn-danger btn-block ripple ion-ios-trash"
                        title="getTitle('delete')"
                        disabled="options.rightsLevel < 3"
                        click="showFileDeletionPrompt(selectedFiles[0].path)">
                    Delete selected files
                </button>
            </p>
        </div>
    )
}


function FilesTable(props) {
    let noFiles = props.files.length || props.loading ? '' : (<div>
        <h3 className="text-center">
            <small>There are no files in current folder</small>
        </h3>
    </div>);
    return (
        <div className="card-body">
            <Shortcuts getFavourites={props.getFavourites}/>
            {noFiles}
            <div className="card">
                <div className="card-body">
                    <table className="table-datatable table table-hover mv-lg">
                        <thead>
                        <tr>
                            <th className="select-cell disabled"><label className="mda-checkbox">
                                <input name="select" className="select-box"
                                       value="all"
                                       type="checkbox"/><em
                                className="bg-info"/></label></th>
                            <th><span className="text-left">Filename</span></th>
                            <th><span><em className="ion-star"/></span></th>
                            <th><span className="text-left">Last Modified</span></th>
                            <th><span className="text-left">File Owner</span></th>
                        </tr>
                        </thead>
                        <FilesList files={props.files} favourite={props.favourite} prevent={props.prevent}/>
                    </table>
                </div>
            </div>
        </div>)
}

function Shortcuts(props) {
    return (
        <div className="center-block text-center hidden-lg">
            <Button onClick={() => props.getFavourites()}><i className="icon ion-ios-home"/></Button>
        </div>)
}

function Breadcrumbs(props) {
    if (!props.currentPath) {
        return null;
    }
    let paths = props.currentPath.split("/");
    console.log(paths);
    let pathsMapping = [];
    for (let i = 0; i < paths.length; i++) {
        let actualPath = "";
        for (let j = 0; j <= i; j++) {
            actualPath += paths[j] + "/";
        }
        pathsMapping.push({actualPath: actualPath, local: paths[i],})
    }
    let i = 0;
    let breadcrumbs = pathsMapping.map(path =>
        <li key={i++} className="breadcrumb-item">
            <Link to={`files/${path.actualPath}`}>{path.local}</Link>
        </li>
    );
    return (
        <ol className="breadcrumb">
            {breadcrumbs}
        </ol>)
}

function FilesList(props) {
    let i = 0;
    let directories = props.files.filter(it => it.type === "DIRECTORY");
    let files = props.files.filter(it => it.type !== "DIRECTORY");
    let directoryList = directories.map(file =>
        <tr key={i++} className="row-settings clickable-row"
            style={{cursor: "pointer"}}>
            <td className="select-cell"><label className="mda-checkbox">
                <input name="select" className="select-box" value="file"
                       type="checkbox"/><em
                className="bg-info"/></label></td>
            <FileType file={file}/>
            <Favourited file={file} favourite={props.favourite}/>
            <td>{new Date(file.modifiedAt).toLocaleString()}</td>
            <td>{file.acl.length > 1 ? file.acl.length + " collaborators" : file.acl[0].right}</td>
            <td>
                <MobileButtons/>
            </td>
        </tr>
    );
    let filesList = files.map(file =>
        <tr key={i++} className="row-settings clickable-row">
            <td className="select-cell"><label className="mda-checkbox">
                <input name="select" className="select-box" value="file"
                       type="checkbox"/><em
                className="bg-info"/></label></td>
            <FileType file={file}/>
            <Favourited file={file} favourite={props.favourite}/>
            <td>{new Date(file.modifiedAt).toLocaleString()}</td>
            <td>{file.acl.length > 1 ? file.acl.length + " collaborators" : file.acl[0].right}</td>
            <td>
                <MobileButtons/>
            </td>
        </tr>
    );
    return (
        <tbody>
        {directoryList}
        {filesList}
        </tbody>
    )
}

function FileType(props) {
    if (props.file.type === "FILE")
        return (
            <td>
                <a className="ion-android-document"/> {props.file.path.name}
            </td>);
    return (
        <td>
            <Link to={`/files/${props.file.path.path}`}>
                <span className="ion-android-folder"/> {props.file.path.name}
            </Link>
        </td>);
}

function Favourited(props) {
    if (props.file.isStarred) {
        return (<td><a onClick={() => props.favourite(props.file)} className="ion-star"/></td>)
    }
    return (<td><a className="ion-star" onClick={() => props.favourite(props.file.path.uri)}/></td>);
}

function MobileButtons() {
    return null;
    /*
    return (
        <span className="hidden-lg">
                      <div className="pull-right dropdown">
                          <button type="button" data-toggle="dropdown"
                                  className="btn btn-flat btn-flat-icon"
                                  aria-expanded="false"><em className="ion-android-more-vertical"/></button>
                          <ul role="menu" className="dropdown-menu md-dropdown-menu dropdown-menu-right">
                              <li><a className="btn btn-info ripple btn-block"
                                     onClick={sendToAbacus()}> Send to Abacus 2.0</a></li>
                              <li><a className="btn btn-default ripple btn-block ion-share"
                                     onClick="shareFile(file.path.name, 'file')"> Share file</a></li>
                              <li><a
                                  className="btn btn-default ripple btn-block ion-ios-download"> Download file</a></li>
                              <li><a className="btn btn-default ripple ion-ios-photos"> Move file</a></li>
                              <li><a className="btn btn-default ripple ion-ios-compose"
                                     onClick="renameFile(file.path.name, 'file')"> Rename file</a></li>
                              <li><a className="btn btn-danger ripple ion-ios-trash"
                                     onClick="showFileDeletionPrompt(file.path)"> Delete file</a></li>
                          </ul>
                      </div>
                  </span>)*/
}

export default Files;
