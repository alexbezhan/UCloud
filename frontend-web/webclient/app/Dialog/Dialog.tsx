import {dialogStore} from "Dialog/DialogStore";
import * as React from "react";
import {useEffect, useState} from "react";
import * as ReactModal from "react-modal";

const Dialog: React.FunctionComponent = () => {
    const [dialogs, setDialogs] = useState<JSX.Element[]>([]);

    useEffect(() => {
        const subscription = (dialogs: JSX.Element[]): void => setDialogs(dialogs);

        dialogStore.subscribe(subscription);
        return () => dialogStore.unsubscribe(subscription);
    }, []);

    const current = dialogs.length > 0 ? dialogs[0] : null;
    return (
        <ReactModal
            isOpen={!!current}
            shouldCloseOnEsc
            ariaHideApp={false}
            onRequestClose={() => dialogStore.failure()}
            onAfterOpen={() => undefined}
            style={{
                content: {
                    top: "50%",
                    left: "50%",
                    right: "auto",
                    bottom: "auto",
                    marginRight: "-50%",
                    transform: "translate(-50%, -50%)",
                    background: "",
                    overflow: "visible"
                }
            }}
        >
            {current}
        </ReactModal>
    );
};

export default Dialog;
