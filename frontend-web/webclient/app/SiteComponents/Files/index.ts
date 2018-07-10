import { Page, File } from "../../Types";
import { History } from "history";

export enum SortOrder {
    ASCENDING = "ASCENDING",
    DESCENDING = "DESCENDING"
}

export enum SortBy {
    TYPE = "TYPE",
    PATH = "PATH",
    CREATED_AT = "CREATED_AT",
    MODIFIED_AT = "MODIFIED_AT",
    SIZE = "SIZE",
    ACL = "ACL",
    FAVORITED = "FAVORITED",
    SENSITIVITY = "SENSITIVITY",
    ANNOTATION = "ANNOTATION"
}

export interface FilesProps extends FilesStateProps, FilesOperations {
    match: { params: string[] }
    history: History
}

export interface FilesStateProps { // Redux Props
    path: string
    page: Page<File>
    loading: boolean
    fileSelectorShown: boolean
    fileSelectorLoading: boolean
    disallowedPaths: string[]
    fileSelectorCallback: Function
    fileSelectorPath: string
    fileSelectorPage: Page<File>
    sortBy: SortBy
    sortOrder: SortOrder
    creatingFolder: boolean
    editFileIndex: number
    // Ignore, used to ensure rerender.
    checkedFilesCount, favFilesCount: number
}

export interface FilesOperations { // Redux operations
    fetchFiles: (path: string, itemsPerPage: number, pageNumber: number, sortOrder: SortOrder, sortBy: SortBy) => void
    fetchSelectorFiles: (path: string, pageNumber: number, itemsPerPage: number) => void
    setFileSelectorCallback: (callback: Function) => void
    checkFile: (checked: boolean, page: Page<File>, newFile: File) => void
    setPageTitle: () => void
    updateFiles: (files: Page<File>) => void
    showFileSelector: (open: boolean) => void
    setDisallowedPaths: (disallowedPaths: string[]) => void
    setCreatingFolder: (creating: boolean) => void
    setEditingFileIndex: (index: number) => void
    resetFolderEditing: () => void
}

export interface FileSelectorProps {
    allowUpload?: boolean
    onFileSelect: Function
    uppy?: any
    path: string
    isRequired: boolean
    canSelectFolders?: boolean
    onlyAllowFolders?: boolean
    remove?: Function
}

export interface FileSelectorState {
    promises: any
    path: string
    loading: boolean
    page: Page<File>
    modalShown: boolean
    breadcrumbs: { path: string, actualPath: string }[]
    uppyOnUploadSuccess: Function
    creatingFolder: boolean
}