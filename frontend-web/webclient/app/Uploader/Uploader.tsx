import {Cloud} from "Authentication/SDUCloudObject";
import {ReduxObject, Sensitivity} from "DefaultObjects";
import {File as SDUCloudFile} from "Files";
import {Refresh} from "Navigation/Header";
import * as React from "react";
import Dropzone from "react-dropzone";
import * as Modal from "react-modal";
import {connect} from "react-redux";
import {useHistory, useLocation} from "react-router";
import {Dispatch} from "redux";
import {SnackType} from "Snackbar/Snackbars";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import {
    Button,
    ButtonGroup,
    Divider,
    Heading,
    Icon,
    OutlineButton,
    Progress,
    Select,
    Text
} from "ui-components";
import {Box, Flex} from "ui-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {Dropdown, DropdownContent} from "ui-components/Dropdown";
import Error from "ui-components/Error";
import {Spacer} from "ui-components/Spacer";
import {TextSpan} from "ui-components/Text";
import {Toggle} from "ui-components/Toggle";
import {setLoading, setUploaderError, setUploaderVisible, setUploads} from "Uploader/Redux/UploaderActions";
import {removeEntry} from "Utilities/CollectionUtilities";
import {
    archiveExtensions,
    isArchiveExtension,
    replaceHomeFolder,
    sizeToString,
    statFileQuery
} from "Utilities/FileUtilities";
import {getQueryParamOrElse} from "Utilities/URIUtilities";
import {FileIcon, overwriteDialog} from "UtilityComponents";
import {
    addTrailingSlash,
    errorMessageOrDefault,
    iconFromFilePath,
    ifPresent,
    is5xxStatusCode,
    prettierString,
    timestampUnixMs
} from "UtilityFunctions";
import {Upload, UploaderProps, UploaderStateProps, UploadOperations} from ".";
import {bulkUpload, multipartUpload, UploadPolicy} from "./api";

const uploadsFinished = (uploads: Upload[]): boolean => uploads.every((it) => isFinishedUploading(it.uploadXHR));
const finishedUploads = (uploads: Upload[]): number => uploads.filter((it) => isFinishedUploading(it.uploadXHR)).length;
const isFinishedUploading = (xhr?: XMLHttpRequest): boolean => !!xhr && xhr.readyState === XMLHttpRequest.DONE;

const newUpload = (file: File, location: string): Upload => ({
    file,
    conflictFile: undefined,
    resolution: UploadPolicy.RENAME,
    sensitivity: "INHERIT",
    isUploading: false,
    progressPercentage: 0,
    extractArchive: false,
    uploadXHR: undefined,
    uploadEvents: [],
    isPending: false,
    parentPath: location,
    uploadSize: 1
});

const addProgressEvent = (upload: Upload, e: ProgressEvent) => {
    const now = timestampUnixMs();
    upload.uploadEvents = upload.uploadEvents.filter(evt => now - evt.timestamp < 10_000);
    upload.uploadEvents.push({timestamp: now, progressInBytes: e.loaded});
    upload.progressPercentage = (e.loaded / e.total) * 100;
    upload.uploadSize = e.total;
};

export function calculateUploadSpeed(upload: Upload): number {
    if (upload.uploadEvents.length === 0) return 0;

    const min = upload.uploadEvents[0];
    const max = upload.uploadEvents[upload.uploadEvents.length - 1];

    const timespan = max.timestamp - min.timestamp;
    const bytesTransferred = max.progressInBytes - min.progressInBytes;

    if (timespan === 0) return 0;
    return (bytesTransferred / timespan) * 1000;
}

function Uploader(props: UploaderProps) {
    const MAX_CONCURRENT_UPLOADS = 5;
    const [finishedUploadPaths, setFinishedUploadsPaths] = React.useState(new Set<string>());
    const history = useHistory();
    const location = useLocation();

    const modalStyle = {
        // https://github.com/reactjs/react-modal/issues/62
        content: {
            borderRadius: "4px",
            bottom: "auto",
            minHeight: "10rem",
            left: "50%",
            maxHeight: "80vh",
            padding: "2rem",
            position: "fixed",
            right: "auto",
            top: "50%",
            transform: "translate(-50%,-50%)",
            minWidth: "20rem",
            width: "80%",
            maxWidth: "60rem",
            background: ""
        }
    };

    const {uploads} = props;
    return (
        <Modal isOpen={props.visible} shouldCloseOnEsc ariaHideApp={false} onRequestClose={closeModal}
            style={modalStyle}
        >
            <div data-tag={"uploadModal"}>
                <Spacer
                    left={<Heading>Upload Files</Heading>}
                    right={<>
                        {props.loading ? <Refresh onClick={() => undefined} spin /> : null}
                        <Icon name="close" cursor="pointer" data-tag="modalCloseButton" onClick={closeModal} />
                    </>}
                />
                <Divider />
                {finishedUploads(uploads) > 0 ? (
                    <OutlineButton
                        mt="4px"
                        mb="4px"
                        color="green"
                        fullWidth
                        onClick={() => clearFinishedUploads()}
                    >
                        Clear finished uploads
                        </OutlineButton>
                ) : null}
                {uploads.filter(it => !it.isUploading).length >= 5 ?
                    <OutlineButton
                        color="blue"
                        fullWidth
                        mt="4px"
                        mb="4px"
                        onClick={() => props.setUploads(uploads.filter(it => it.isUploading))}
                    >
                        Clear unstarted uploads
                        </OutlineButton> : null}
                <Box>
                    {uploads.map((upload, index) => (
                        <React.Fragment key={index}>
                            <UploaderRow
                                location={props.path}
                                upload={upload}
                                setSensitivity={sensitivity => updateSensitivity(index, sensitivity)}
                                onExtractChange={value => onExtractChange(index, value)}
                                onUpload={() => startUpload(index)}
                                onDelete={it => (it.preventDefault(), removeUpload(index))}
                                onAbort={it => (it.preventDefault(), abort(index))}
                                onClear={it => (it.preventDefault(), clearUpload(index))}
                                setRewritePolicy={policy => setRewritePolicy(index, policy)}
                            />
                            <Divider />
                        </React.Fragment>
                    ))}
                    {uploads.filter(it => !it.isUploading).length > 1 && uploads.filter(it => !it.conflictFile).length ?
                        <Button fullWidth color="green" onClick={startAllUploads}>
                            <Icon name={"upload"} />{" "}Start all!</Button> : null}
                    <Dropzone onDrop={onFilesAdded}>
                        {({getRootProps, getInputProps}) => (
                            <DropZoneBox {...getRootProps()}>
                                <input {...getInputProps()} />
                                <p>
                                    <TextSpan mr="0.5em"><Icon name="upload" /></TextSpan>
                                    <TextSpan mr="0.3em">Drop files here or </TextSpan><a href="#">{" browse"}</a>
                                </p>
                                <p>
                                    <b>Bulk upload</b> supported for file types: <i><code>{archiveExtensions.join(", ")}</code></i>
                                </p>
                            </DropZoneBox>
                        )}
                    </Dropzone>
                </Box>
            </div>
        </Modal>
    );

    async function onFilesAdded(files: File[]): Promise<void> {
        if (files.some(it => it.size === 0))
            snackbarStore.addSnack({
                message: "It is not possible to upload empty files.",
                type: SnackType.Information
            });
        if (files.some(it => it.name.length > 1025))
            snackbarStore.addSnack({
                message: "Filenames can't exceed a length of 1024 characters.",
                type: SnackType.Information
            });
        const filteredFiles =
            files.filter(it => it.size > 0 && it.name.length < 1025).map(it => newUpload(it, props.path));
        if (filteredFiles.length === 0) return;

        props.setLoading(true);
        type PromiseType = ({request: XMLHttpRequest, response: SDUCloudFile} | {status: number, response: string});
        const promises: PromiseType[] = await Promise.all(filteredFiles.map(file =>
            Cloud.get<SDUCloudFile>(statFileQuery(`${props.path}/${file.file.name}`)).then(it => it).catch(it => it)
        ));

        promises.forEach((it, index) => {
            if ("status" in it || is5xxStatusCode(it.request.status))
                filteredFiles[index].error = errorMessageOrDefault(it, "Could not reach backend, try again later");
            else if (it.request.status === 200) filteredFiles[index].conflictFile = it.response;
        });

        if (props.allowMultiple !== false) { // true if no value
            props.setUploads(props.uploads.concat(filteredFiles));
        } else {
            props.setUploads([filteredFiles[0]]);
        }
        props.setLoading(false);
    }

    function beforeUnload(e: {returnValue: string}) {
        e.returnValue = "foo";
        const finished = finishedUploads(props.uploads);
        const total = props.uploads.length;
        snackbarStore.addSnack({
            message: `${finished} out of ${total} files uploaded`,
            type: SnackType.Information
        });
        return e;
    }

    function startPending() {
        const remainingAllowedUploads = MAX_CONCURRENT_UPLOADS - props.activeUploads.length;
        for (let i = 0; i < remainingAllowedUploads; i++) {
            const index = props.uploads.findIndex(it => it.isPending);
            if (index !== -1) startUpload(index);
        }
    }

    function onUploadFinished(upload: Upload, xhr: XMLHttpRequest) {
        xhr.onloadend = () => {
            if (uploadsFinished(props.uploads))
                window.removeEventListener("beforeunload", beforeUnload);
            props.setUploads(props.uploads);
            startPending();
        };
        (finishedUploadPaths.add(upload.parentPath));
        upload.uploadXHR = xhr;
        props.setUploads(props.uploads);
    }

    function startUpload(index: number) {
        const upload = props.uploads[index];
        if (props.activeUploads.length === MAX_CONCURRENT_UPLOADS) {
            upload.isPending = true;
            return;
        }
        upload.isPending = false;
        upload.isUploading = true;
        props.setUploads(props.uploads);

        window.addEventListener("beforeunload", beforeUnload);

        const setError = (err?: string) => {
            props.uploads[index].error = err;
            props.setUploads(props.uploads);
        };

        const uploadParams = {
            file: upload.file,
            sensitivity: upload.sensitivity,
            policy: upload.resolution,
            onProgress: (e: ProgressEvent) => {
                addProgressEvent(upload, e);
                props.setUploads(props.uploads);
            },
            onError: (err: string) => setError(err),
        };

        if (!upload.extractArchive) {
            multipartUpload({
                location: `${upload.parentPath}/${upload.file.name}`,
                ...uploadParams
            }).then(xhr => onUploadFinished(upload, xhr))
                .catch(e => setError(errorMessageOrDefault(e, "An error occurred uploading the file")));
        } else {
            bulkUpload({
                location: upload.parentPath,
                ...uploadParams
            }).then(xhr => onUploadFinished(upload, xhr))
                .catch(e => setError(errorMessageOrDefault(e, "An error occurred uploading the file")));
        }
    }

    function startAllUploads(event: {preventDefault: () => void}) {
        event.preventDefault();
        props.uploads.forEach(it => {if (!it.uploadXHR) it.isPending = true;});
        startPending();
    }

    function removeUpload(index: number) {
        const files = props.uploads.slice();
        if (index < files.length) {
            const remainderFiles = removeEntry(files, index);
            props.setUploads(remainderFiles);
            startPending();
        }
    }

    async function abort(index: number) {
        const upload = props.uploads[index];
        if (!!upload.uploadXHR && upload.uploadXHR.readyState !== XMLHttpRequest.DONE) {
            if (upload.resolution === UploadPolicy.OVERWRITE) {
                const result = await overwriteDialog();
                if (result.cancelled) return;
            }
            upload.uploadXHR.abort();
            removeUpload(index);
            startPending();
        }
    }

    function onExtractChange(index: number, value: boolean) {
        const uploads = props.uploads;
        uploads[index].extractArchive = value;
        props.setUploads(uploads);
    }

    function updateSensitivity(index: number, sensitivity: Sensitivity) {
        const uploads = props.uploads;
        uploads[index].sensitivity = sensitivity;
        props.setUploads(uploads);
    }

    function clearUpload(index: number) {
        props.setUploads(removeEntry(props.uploads, index));
    }

    function clearFinishedUploads() {
        props.setUploads(props.uploads.filter(it => !isFinishedUploading(it.uploadXHR)))
    }

    function setRewritePolicy(index: number, policy: UploadPolicy) {
        const {uploads} = props;
        uploads[index].resolution = policy;
        props.setUploads(uploads);
    }

    function closeModal() {
        props.setUploaderVisible(false);
        const {uploads} = props;
        if (finishedUploads(uploads) !== uploads.length || uploads.length === 0) return;
        const path = getQueryParamOrElse({history, location}, "path", "");
        if ([...finishedUploadPaths].includes(path)) {
            if (!!props.parentRefresh) props.parentRefresh();
        }
    }
}

const DropZoneBox = styled(Box)`
    width: 100%;
    height: 100px;
    border-width: 2px;
    border-color: rgb(102, 102, 102);
    border-style: dashed;
    border-radius: 5px;
    margin: 16px 0 16px 0;

    & > p {
        margin: 16px;
    }
`;

const privacyOptions = [
    {text: "Inherit", value: "INHERIT"},
    {text: "Private", value: "PRIVATE"},
    {text: "Confidential", value: "CONFIDENTIAL"},
    {text: "Sensitive", value: "SENSITIVE"}
];

const UploaderRow = (p: {
    upload: Upload,
    location: string,
    setSensitivity: (key: Sensitivity) => void,
    onExtractChange?: (value: boolean) => void,
    onUpload?: (e: React.MouseEvent<any>) => void,
    onDelete?: (e: React.MouseEvent<any>) => void,
    onAbort?: (e: React.MouseEvent<any>) => void
    onClear?: (e: React.MouseEvent<any>) => void
    setRewritePolicy?: (policy: UploadPolicy) => void
    onCheck?: (checked: boolean) => void
}) => {

    const fileInfo = p.location !== p.upload.parentPath ? (<Dropdown>
        <Icon style={{pointer: "cursor"}} ml="10px" name="info" color="white" color2="black" />
        <DropdownContent width="auto" visible colorOnHover={false} color="white" backgroundColor="black">
            Will be uploaded to: {addTrailingSlash(replaceHomeFolder(p.location, Cloud.homeFolder))}{p.upload.file.name}
        </DropdownContent>
    </Dropdown>) : null;

    const fileTitle = <span>
        <b>{p.upload.file.name} </b>
        ({sizeToString(p.upload.file.size)}){fileInfo}<ConflictFile file={p.upload.conflictFile} />
    </span>;
    let body: React.ReactNode;
    if (!!p.upload.error) {
        body = <>
            <Box width={0.5}>
                {fileTitle}
            </Box>
            <Spacer pr="4px" width={0.5}
                left={<Text color="red">{p.upload.error}</Text>}
                right={<Button color="red" onClick={e => ifPresent(p.onDelete, c => c(e))}
                    data-tag={"removeUpload"}>
                    <Icon name="close" />
                </Button>}
            />
        </>;
    } else if (!p.upload.isUploading) {
        body = <>
            <Box width={0.7}>
                <Spacer
                    left={fileTitle}
                    right={p.upload.conflictFile ? <PolicySelect setRewritePolicy={p.setRewritePolicy!} /> : null}
                />
                <br />
                {isArchiveExtension(p.upload.file.name) ?
                    <Flex data-tag="extractArchive">
                        <label>Extract archive?</label>
                        <Box ml="0.5em" />
                        <Toggle
                            scale={1.3}
                            checked={p.upload.extractArchive}
                            onChange={() => ifPresent(p.onExtractChange, c => c(!p.upload.extractArchive))}
                        />
                    </Flex> : null}
            </Box>
            <Error error={p.upload.error} />
            <Box width={0.3}>
                <ButtonGroup width="100%">
                    {!p.upload.isPending ?
                        <Button
                            data-tag={"startUpload"}
                            color="green"
                            disabled={!!p.upload.error}
                            onClick={e => ifPresent(p.onUpload, c => c(e))}
                        >
                            <Icon name="cloud upload" />Upload
                        </Button>
                        :
                        <Button color="blue" disabled>Pending</Button>
                    }
                    <Button color="red" onClick={e => ifPresent(p.onDelete, c => c(e))} data-tag={"removeUpload"}>
                        <Icon name="close" />
                    </Button>
                </ButtonGroup>
                <Flex justifyContent="center" pt="0.3em">
                    <ClickableDropdown
                        chevron
                        trigger={prettierString(p.upload.sensitivity)}
                        onChange={key => p.setSensitivity(key as Sensitivity)}
                        options={privacyOptions}
                    />
                </Flex>
            </Box>
        </>;
    } else { // Uploading
        body = <>
            <Box width={0.25}>
                {fileTitle}
                <br />
                {isArchiveExtension(p.upload.file.name) ?
                    (p.upload.extractArchive ?
                        <span><Icon name="checkmark" color="green" />Extracting archive</span> :
                        <span><Icon name="close" color="red" /> <i>Not</i> extracting archive</span>)
                    : null}
            </Box>
            <ProgressBar upload={p.upload} />
            <Box width={0.22}>
                {!isFinishedUploading(p.upload.uploadXHR) ?
                    <Button
                        fullWidth
                        color="red"
                        onClick={e => ifPresent(p.onAbort, c => c(e))}
                        data-tag={"cancelUpload"}
                    >
                        Cancel
                    </Button>
                    :
                    <Button
                        fullWidth
                        color="red"
                        onClick={e => ifPresent(p.onClear, c => c(e))}
                        data-tag={"removeUpload"}
                    >
                        <Icon name="close" />
                    </Button>}
            </Box>
        </>;
    }

    return (
        <Flex flexDirection="row" data-tag={"uploadRow"}>
            <Box width={0.04} textAlign="center">
                <FileIcon fileIcon={iconFromFilePath(p.upload.file.name, "FILE", Cloud.homeFolder)} />
            </Box>
            <Flex width={0.96}>{body}</Flex>
        </Flex>
    );
};

const ProgressBar = ({upload}: {upload: Upload}) => (
    <Box width={0.45} ml="0.5em" mr="0.5em" pl="0.5" pr="0.5">
        <Progress
            active={upload.progressPercentage !== 100}
            color="green"
            label={`${upload.progressPercentage.toFixed(2)}% (${sizeToString(calculateUploadSpeed(upload))}/s)`}
            percent={upload.progressPercentage}
        />
    </Box>
);

interface PolicySelect {
    setRewritePolicy: (policy: UploadPolicy) => void;
}

const PolicySelect = ({setRewritePolicy}: PolicySelect) =>
    <Flex mt="-12px" width="200px" mr="0.5em">
        <Select
            width="200px"
            defaultValue="Rename"
            onChange={e => setRewritePolicy(e.target.value.toUpperCase() as UploadPolicy)}
        >
            <option>Rename</option>
            <option>Overwrite</option>
        </Select>
    </Flex>;

interface ConflictFile {
    file?: SDUCloudFile;
}

const ConflictFile = ({file}: ConflictFile) => !!file ?
    <Box>File already exists in folder, {sizeToString(file.size!)}</Box> : null;

const mapStateToProps = ({uploader}: ReduxObject): UploaderStateProps => ({
    activeUploads: uploader.uploads.filter(it => it.uploadXHR && it.uploadXHR.readyState !== XMLHttpRequest.DONE),
    path: uploader.path,
    visible: uploader.visible,
    allowMultiple: true,
    uploads: uploader.uploads,
    error: uploader.error,
    loading: uploader.loading,
    parentRefresh: uploader.onFilesUploaded
});

const mapDispatchToProps = (dispatch: Dispatch): UploadOperations => ({
    setUploads: uploads => dispatch(setUploads(uploads)),
    setUploaderError: err => dispatch(setUploaderError(err)),
    setUploaderVisible: visible => dispatch(setUploaderVisible(visible, Cloud.homeFolder)),
    setLoading: loading => dispatch(setLoading(loading)),
});

export default connect<UploaderStateProps, UploadOperations>(mapStateToProps, mapDispatchToProps)(Uploader);
