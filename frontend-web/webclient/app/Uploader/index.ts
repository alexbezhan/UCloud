import Uploader from "./Uploader";
export {Uploader};
import {Sensitivity} from "DefaultObjects";
import {File as CloudFile} from "Files";
import {UploadPolicy} from "./api";

export interface Upload {
    file: File;
    isUploading: boolean;
    progressPercentage: number;
    uploadSize: number;
    extractArchive: boolean;
    sensitivity: Sensitivity;
    uploadXHR?: XMLHttpRequest;
    conflictFile?: CloudFile;
    resolution: UploadPolicy;
    uploadEvents: Array<{progressInBytes: number, timestamp: number}>;
    isPending: boolean;
    path: string;
    error?: string;
}

export interface UploaderStateProps {
    activeUploads: Upload[];
    error?: string;
    visible: boolean;
    uploads: Upload[];
    allowMultiple?: boolean;
    path: string;
    loading: boolean;
    parentRefresh: () => void;
}

export interface UploadOperations {
    setUploads: (uploads: Upload[]) => void;
    setUploaderError: (err?: string) => void;
    setUploaderVisible: (visible: boolean) => void;
    setLoading: (loading: boolean) => void;
}

export type UploaderProps = UploadOperations & UploaderStateProps;
