import * as React from "react";
import {connect} from "react-redux";
import {updatePageTitle} from "./Redux/StatusActions";
import {Dispatch} from "redux";
import {Box} from "ui-components";
import {MainContainer} from "MainContainer/MainContainer";

const Status = ({status, updatePageTitle}) => {
    updatePageTitle();
    return (
        <MainContainer
            main={<>
                <Box>{status.title}</Box>
                <Box>{status.body}</Box>
            </>}
        />
    );
};

const mapDispatchToProps = (dispatch: Dispatch) => ({
    updatePageTitle: () => dispatch(updatePageTitle("System Status"))
});
const mapStateToProps = ({status}) => ({status: status.status});
export default connect(mapStateToProps, mapDispatchToProps)(Status);