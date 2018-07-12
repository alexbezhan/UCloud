export interface Acl {
    entity: Entity
    right: string
}

export interface Entity {
    type: string
    name: string
    displayName: string
    zone: string
}

export interface Analysis {
    name: string
    status: string
    state: string
    jobId: string
    appName: string
    appVersion: string
    createdAt: number
    modifiedAt: number
    owner: string
}

export interface Application {
    owner: string
    createdAt: number
    modifiedAt: number

    tool: {
        owner: string
        createdAt: number
        modifiedAt: number

        description: {
            name: string
            version: string
        }
    }

    description: {
        info: {
            name: string
            version: string
        }

        title: string
        authors: string[]
        description: string

        parameters: any[]
        invocation: any[]
    }
}

export interface Status {
    title: string
    level: string
    body: string
}

export interface SidebarOption {
    name: string
    icon: string
    href: string
    children?: SidebarOption
}

export interface Publication {
    id: number
    name: string
    zenodoAction: string
    createdAt: number
    modifiedAt: number
}

export interface Notification {
    type: string
    jobId?: string
    ts: number
    status?: string
    id: string
    isRead: boolean
}

export interface Page<T> {
    itemsInTotal: number,
    itemsPerPage: number,

    pageNumber: number,
    items: T[]
}

export enum AccessRight {
    READ = "READ",
    WRITE = "WRITE",
    EXECUTE = "EXECUTE"
}

export interface DropdownOption {
    name: string
    value: string
}

export type Action = { type: String }
export interface SetLoadingAction extends Action { loading: boolean }
export interface ReceivePage<T> extends Action { page: Page<T> }
