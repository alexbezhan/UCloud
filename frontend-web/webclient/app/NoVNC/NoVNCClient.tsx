import * as React from "react";
import RFB from "@novnc/novnc/core/rfb";
import {MainContainer} from "MainContainer/MainContainer";
import {Button, Heading, OutlineButton} from "ui-components";
import {SnackType} from "Snackbar/Snackbars";
import {connect} from "react-redux";
import {errorMessageOrDefault, requestFullScreen} from "UtilityFunctions";
import {getQueryParam, RouterLocationProps} from "Utilities/URIUtilities";
import {Cloud} from "Authentication/SDUCloudObject";
import {cancelJobQuery, cancelJobDialog} from "Utilities/ApplicationUtilities";
import {snackbarStore} from "Snackbar/SnackbarStore";
import PromiseKeeper from "PromiseKeeper";

interface RFB {
    constructor(): RFB

    // Properties
    viewOnly: boolean
    focusOnClick: boolean
    touchButton: number
    clipViewPort: boolean
    dragViewPort: boolean
    scaleViewPort: boolean
    resizeSession: boolean
    showDotCursor: boolean
    background: string
    readonly capabilities: {}
    // Methods
    /**
     * Disconnect from the server.
     */
    disconnect: () => void
    /**
     * Send credentials to server. Should be called after the credentialsrequired event has fired.
     */
    sendCredentials: (credentials: any) => void
    /**
     * Send a key event.
     */
    sendKey: (keysym: number, code: number, down?: boolean) => void
    /**
     * Send Ctrl-Alt-Del key sequence.
     */
    sendCtrlAltDel: () => void
    /**
     * Move keyboard focus to the remote session.
     */
    focus: () => void
    /**
     * Remove keyboard focus from the remote session.
     */
    blur: () => void
    /**
     * Request a shutdown of the remote machine.
     */
    machineShutdown: () => void
    /**
     * Request a reboot of the remote machine.
     */
    machineReboot: () => void
    /**
     * Request a reset of the remote machine.
     */
    machineReset: () => void
    /**
     * Send clipboard contents to server.
     */
    clipboardPasteFrom: (text: string) => void
}

function NoVNCClient(props: RouterLocationProps) {
    const [isConnected, setConnected] = React.useState(false);
    const [isCancelled, setCancelled] = React.useState(false);
    const [rfb, setRFB] = React.useState<RFB | undefined>(undefined);
    const [password, setPassword] = React.useState("");
    const [path, setPath] = React.useState("");
    const jobId = getQueryParam(props, "jobId");
    const [promiseKeeper] = React.useState(new PromiseKeeper());

    React.useEffect(() => {
        promiseKeeper.makeCancelable(Cloud.get(`/hpc/jobs/query-vnc/${jobId}`)).promise.then(it => {
            setPassword(it.response.password);
            setPath(it.response.path);
            connect();
        }).catch(() => undefined);
        return () => {
            if (isConnected) rfb!.disconnect();
            promiseKeeper.cancelPromises();
        }
    }, []);

    function connect() {
        try {
            const protocol = window.location.protocol === "http:" ? "ws:" : "wss:";
            const rfb = new RFB(document.getElementsByClassName("noVNC")[0], `${protocol}//${window.location.host}${path}`, {
                credentials: {password}
            });

            /* FIXME: Doesn't seem to work properly, e.g. if connection fails */
            rfb.addEventListener("disconnect", () => setConnected(false));
            /* FIXME END */
            setRFB(rfb);
            setConnected(true);
        } catch (e) {
            snackbarStore.addSnack({
                message: errorMessageOrDefault(e, "And error ocurred connecting"),
                type: SnackType.Failure
            });
        }
    }

    function disconnect() {
        rfb!.disconnect();
        setConnected(false);
    }

    function toFullScreen() {
        requestFullScreen(document.getElementsByClassName("noVNC")[0]!, () => snackbarStore.addSnack({
            type: SnackType.Failure,
            message: `Fullscreen is not supported for this browser.`
        }));
    }

    function cancelJob() {
        if (!jobId) return;
        cancelJobDialog({
            jobId,
            onConfirm: async () => {
                try {
                    await Cloud.delete(cancelJobQuery, {jobId});
                    snackbarStore.addSnack({
                        type: SnackType.Success,
                        message: "Job has been terminated"
                    });
                    setCancelled(true);
                } catch (e) {
                    snackbarStore.addSnack({
                        type: SnackType.Failure,
                        message: errorMessageOrDefault(e, "An error occurred cancelling the job.")
                    });
                }
            }
        })
    }

    const mountNode = <div className="noVNC"/>;
    const main = <>
        <Heading mb="5px">
            {isConnected ? <OutlineButton ml="15px" mr="10px" onClick={() => disconnect()}>
                    Disconnect
                </OutlineButton> :
                <div><Button ml="15px" onClick={() => connect()}>
                    Connect
                </Button>
                    {!isCancelled ? <Button ml="8px" color="red" onClick={() => cancelJob()}>
                        Cancel Job
                    </Button> : null}
                </div>
            }
            {isConnected ? <OutlineButton onClick={() => toFullScreen()}>Fullscreen</OutlineButton> : null}
        </Heading>
        {mountNode}
    </>;

    return <MainContainer
        main={main}
    />;
}

export default connect(null, null)(NoVNCClient);
