import * as React from "react";
import { Icon, Header } from "semantic-ui-react";

export const FileIcon = ({ name, size, link = false, className = "", color }) =>
    link ?
        <Icon.Group className={className} size={size}>
            <Icon name={name} color={color} />
            <Icon corner color="grey" name="share" />
        </Icon.Group> :
        <Icon name={name} size={size} color={color} />

export const RefreshButton = ({ loading, onClick }: { loading: boolean, onClick: () => void }) => (
    <Icon
        size="small"
        link
        circular
        name="sync"
        onClick={() => onClick()} loading={loading}
    />
);

export const WebSocketSupport = () =>
    !("WebSocket" in window) ?
        (<Header as="h3">
            <small>WebSockets are not supported in this browser. Notifications won't be updated automatically.</small>
        </Header>) : null;