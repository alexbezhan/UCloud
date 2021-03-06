import {ParameterProps} from "Applications/Widgets/BaseParameter";
import {BooleanParameter} from "Applications/Widgets/BooleanParameter";
import {EnumerationParameter} from "Applications/Widgets/EnumerationParameter";
import {InputDirectoryParameter, InputFileParameter} from "Applications/Widgets/FileParameter";
import {FloatingParameter, IntegerParameter} from "Applications/Widgets/NumberParameter";
import {PeerParameter} from "Applications/Widgets/PeerParameter";
import {TextParameter} from "Applications/Widgets/TextParameter";
import * as React from "react";
import * as Types from "../index";
import {LicenseServerParameter} from "./LicenseServerParameter";
// import RangeParameter, {RangeRef} from "./RangeParameters";

export const Parameter = (props: ParameterProps): JSX.Element => {
    let component = <div />;
    switch (props.parameter.type) {
        case Types.ParameterTypes.InputFile: {
            const p = {...props, parameterRef: props.parameterRef as React.RefObject<HTMLInputElement>};
            component = <InputFileParameter {...p} />;
            break;
        }
        case Types.ParameterTypes.InputDirectory: {
            const p = {...props, parameterRef: props.parameterRef as React.RefObject<HTMLInputElement>};
            component = <InputDirectoryParameter mountedFolders={[]} {...p} />;
            break;
        }
        case Types.ParameterTypes.Integer:
            component = (
                <IntegerParameter
                    onParamRemove={props.onParamRemove}
                    initialSubmit={props.initialSubmit}
                    parameterRef={props.parameterRef as React.RefObject<HTMLInputElement>}
                    parameter={props.parameter}
                    application={props.application}
                />
            );
            break;
        case Types.ParameterTypes.FloatingPoint:
            component = (
                <FloatingParameter
                    onParamRemove={props.onParamRemove}
                    initialSubmit={props.initialSubmit}
                    parameterRef={props.parameterRef as React.RefObject<HTMLInputElement>}
                    parameter={props.parameter}
                    application={props.application}
                />
            );
            break;
        case Types.ParameterTypes.Text:
            component = (
                <TextParameter
                    onParamRemove={props.onParamRemove}
                    initialSubmit={props.initialSubmit}
                    parameterRef={props.parameterRef}
                    parameter={props.parameter}
                    application={props.application}
                />
            );
            break;
        case Types.ParameterTypes.Boolean:
            component = (
                <BooleanParameter
                    onParamRemove={props.onParamRemove}
                    initialSubmit={props.initialSubmit}
                    parameterRef={props.parameterRef as React.RefObject<HTMLSelectElement>}
                    parameter={props.parameter}
                    application={props.application}
                />
            );
            break;
        case Types.ParameterTypes.Enumeration:
            component = (
                <EnumerationParameter
                    onParamRemove={props.onParamRemove}
                    initialSubmit={props.initialSubmit}
                    parameterRef={props.parameterRef as React.RefObject<HTMLSelectElement>}
                    parameter={props.parameter}
                    application={props.application}
                />
            );
            break;
        case Types.ParameterTypes.Peer:
            component = (
                <PeerParameter
                    parameter={props.parameter}
                    initialSubmit={props.initialSubmit}
                    parameterRef={props.parameterRef}
                    application={props.application}
                />
            );
            break;
        case Types.ParameterTypes.LicenseServer:
            component = (
                <LicenseServerParameter
                    parameter={props.parameter}
                    initialSubmit={props.initialSubmit}
                    parameterRef={props.parameterRef as React.RefObject<HTMLSelectElement>}
                    application={props.application}
                />
            );
        /* case Types.ParameterTypes.Range: {
            component = <RangeParameter
                parameter={props.parameter}
                onParamRemove={props.onParamRemove}
                application={props.application}
                parameterRef={props.parameterRef as React.RefObject<RangeRef>}
                initialSubmit={props.initialSubmit}
            />;
        } */
    }

    return component;
};

