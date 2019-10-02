import {Cloud} from "Authentication/SDUCloudObject";
import {KeyCode} from "DefaultObjects";
import * as React from "react";
import {useEffect, useRef, useState} from "react";
import {connect} from "react-redux";
import {SnackType} from "Snackbar/Snackbars";
import {snackbarStore} from "Snackbar/SnackbarStore";
import * as Heading from "ui-components/Heading";
import {errorMessageOrDefault} from "UtilityFunctions";
import Box from "./Box";
import Button from "./Button";
import ClickableDropdown from "./ClickableDropdown";
import Flex from "./Flex";
import Icon from "./Icon";
import Label from "./Label";
import Radio from "./Radio";
import {TextSpan} from "./Text";
import TextArea from "./TextArea";

const enum SupportType {
    SUGGESTION = "SUGGESTION",
    BUG = "BUG"
}

function Support() {
    const textArea = useRef<HTMLTextAreaElement>(null);
    const supportBox = useRef<HTMLTextAreaElement>(null);
    const [loading, setLoading] = useState(false);
    const [visible, setVisible] = useState(false);
    const [type, setType] = useState(SupportType.SUGGESTION);

    function handleESC(e: KeyboardEvent) {
        if (e.keyCode === KeyCode.ESC) setVisible(false);
    }

    function handleClickOutside(event) {
        if (supportBox.current && !supportBox.current.contains(event.target) && visible)
            setVisible(false);
    }

    async function onSubmit(event: React.FormEvent) {
        event.preventDefault();
        const text = textArea.current;
        if (!!text) {
            try {
                setLoading(true);
                await Cloud.post("/support/ticket", {message: `${type}: ${text.value}`});
                text.value = "";
                setVisible(false);
                setLoading(false);
                snackbarStore.addSnack({message: "Support ticket submitted!", type: SnackType.Success});
            } catch (e) {
                snackbarStore.addSnack({
                    message: errorMessageOrDefault(e, "An error occured"),
                    type: SnackType.Failure
                });
            }
        }
    }

    useEffect(() => {
        document.addEventListener("keydown", handleESC);
        document.addEventListener("mousedown", handleClickOutside);
        return () => {
            document.removeEventListener("keydown", handleESC);
            document.removeEventListener("mousedown", handleClickOutside);
        };
    }, []);

    return (
        <ClickableDropdown colorOnHover={false} keepOpenOnClick trigger={
            <Flex width="48px" justifyContent="center">
                <Icon name={"chat"} size="24px" color="headerIconColor" color2="headerBg" />
            </Flex>
        }
            width="650px"
            height="350px"
            right="10px"
            top="37px"
        >
            <Box color="text">
                <Heading.h3>Support Form</Heading.h3>
                <Flex mt="3px">
                    <Label>
                        <Radio
                            checked={type === SupportType.SUGGESTION}
                            onChange={() => setType(SupportType.SUGGESTION)}
                        />
                        Suggestion
                        <Icon name="suggestion" size="1.5em" ml=".5em" />
                    </Label>
                    <Label>
                        <Radio
                            checked={type === SupportType.BUG}
                            onChange={() => setType(SupportType.BUG)}
                        />
                        Bug
                        <Icon name="bug" size="1.5em" ml=".5em" />
                    </Label>
                </Flex>
                {type === SupportType.SUGGESTION ? <p>Describe your suggestion and we will look into it.</p> :
                    <p>Describe your problem below and we will investigate it.</p>}
                <form onSubmit={e => onSubmit(e)}>
                    <TextArea width="100%" ref={textArea} rows={6} />
                    <Button mt="6px" fullWidth type="submit" disabled={loading}>
                        <Icon name="mail" size="1.5em" mr=".5em" color="white" color2="midGray" />
                        <TextSpan fontSize={2}>Send</TextSpan>
                    </Button>
                </form>
            </Box>
        </ClickableDropdown>);
}

export default connect(null, null)(Support);
