import {KeyCode} from "DefaultObjects";
import * as React from "react";
import {Icon} from "ui-components";
import * as Text from "ui-components/Text";
import Box from "./Box";
import {Dropdown, DropdownContent} from "./Dropdown";

interface ClickableDropdownState {open: boolean;}
interface ClickableDropdownProps<T> {
    children?: any;
    keepOpenOnClick?: boolean;
    trigger: React.ReactNode;
    fullWidth?: boolean;
    height?: string | number;
    width?: string | number;
    minWidth?: string;
    left?: string | number;
    top?: string | number;
    bottom?: string | number;
    right?: string | number;
    options?: {text: string; value: T}[];
    chevron?: boolean;
    overflow?: string;
    colorOnHover?: boolean;
    squareTop?: boolean;
    keepOpenOnOutsideClick?: boolean;
    onChange?: (value: T) => void;
    onTriggerClick?: () => void;
}

class ClickableDropdown<T extends string> extends React.Component<ClickableDropdownProps<T>, ClickableDropdownState> {
    private ref = React.createRef<HTMLDivElement>();

    constructor(props: Readonly<ClickableDropdownProps<T>>) {
        super(props);
        this.state = {open: false};
        let neither = true;
        if (props.children) neither = false;
        if (!!props.onChange && !!props.options) neither = false;
        if (neither) throw Error("Clickable dropdown must have either children prop or options and onChange");
        document.addEventListener("mousedown", this.handleClickOutside);
        document.addEventListener("keydown", this.handleEscPress);
    }

    public componentWillUnmount = (): void => {
        document.removeEventListener("mousedown", this.handleClickOutside);
        document.removeEventListener("keydown", this.handleEscPress);
    };

    public render(): JSX.Element {
        const {keepOpenOnClick, onChange, onTriggerClick, ...props} = this.props;
        let children: React.ReactNode[] = [];
        if (props.options !== undefined && onChange) {
            children = props.options.map((opt, i) => (
                <Box
                    cursor="pointer"
                    width="auto"
                    key={i}
                    ml="-17px"
                    pl="15px"
                    mr="-17px"
                    onClick={() => onChange!(opt.value)}
                >
                    {opt.text}
                </Box>
            ));
        } else if (props.children) {
            children = props.children;
        }
        const emptyChildren = (React.Children.map(children, it => it) ?? []).length === 0;
        const width = this.props.fullWidth ? "100%" : this.props.width;
        return (
            <Dropdown data-tag="dropdown" ref={this.ref} fullWidth={this.props.fullWidth}>
                <Text.TextSpan cursor="pointer" onClick={() => {onTriggerClick?.(); this.setState(() => ({open: !this.state.open}))}}>
                    {this.props.trigger}{props.chevron ? <Icon name="chevronDown" size=".7em" ml=".7em" /> : null}
                </Text.TextSpan>
                {emptyChildren || !this.state.open ? null : (
                    <DropdownContent
                        overflow={"visible"}
                        squareTop={this.props.squareTop}
                        cursor="pointer"
                        {...props}
                        width={width}
                        hover={false}
                        visible={this.state.open}
                        onClick={() => !keepOpenOnClick ? this.setState(() => ({open: false})) : null}
                    >
                        {children}
                    </DropdownContent>
                )}
            </Dropdown>
        );
    }

    // https://stackoverflow.com/questions/32553158/detect-click-outside-react-component#42234988
    private handleClickOutside = event => {
        if (this.props.keepOpenOnOutsideClick) return;
        if (this.ref.current && !this.ref.current.contains(event.target) && this.state.open) {
            this.setState(() => ({open: false}));
        }
    };

    private handleEscPress = (event: {keyCode: KeyCode}): void => {
        if (event.keyCode === KeyCode.ESC && this.state.open) this.setState(() => ({open: false}));
    };
}

export default ClickableDropdown;
