import * as React from "react";
import styled from "styled-components";
import Box from "./Box";
import Flex from "./Flex";
import Text from "./Text";
import {default as theme, ThemeColor} from "./theme";

interface ProgressBaseProps {
    height?: number | string;
    value?: number | string;
    active?: boolean;
    label?: string;
}

const ProgressBase = styled(Box)<ProgressBaseProps>`
    border-radius: 5px;
    background-color: ${props => props.theme.colors[props.color!]};
    height: ${props => props.height};

    /* From semantic-ui-css */
    ${props => props.active ? `animation: progress-active 2s ease infinite;` : null}

    @keyframes progress-active {
        0% {
            opacity: 0.3;
            width: 0;
        }
        100% {
            opacity: 0;
            width: 100%;
        }
    }
`;

ProgressBase.defaultProps = {
    color: "green",
    height: "30px",
    active: false,
    theme
};

interface Progress {
    color: ThemeColor;
    percent: number;
    active: boolean;
    label: string;
}

const Progress = ({color, percent, active, label}: Progress) => (
    <>
        <ProgressBase height="30px" style={{width: "100%"}} color="lightGray">
            <ProgressBase height="30px" color={color} style={{width: `${percent}%`}}>
                {active ? <ProgressBase height="30px" active style={{width: "100%"}} color="black"/> : null}
            </ProgressBase>
        </ProgressBase>
        {label ? <Flex justifyContent="center"><Text>{label}</Text></Flex> : null}
    </>
);

ProgressBase.displayName = "ProgressBase";

export default Progress;
