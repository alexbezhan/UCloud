import * as React from "react";
import { Card } from "semantic-ui-react";
import * as Pagination from "Pagination";
import { connect } from "react-redux";
import { emptyPage } from "DefaultObjects";

class Projects extends React.Component<any, any> {

    componentDidMount() {
        //fetch projects
    }

    render() {
        return null;
    }
}

const mapStateToProps = (state) => ({});
const mapDispatchToProps = (dispatch) => ({});

export default connect(mapStateToProps, mapDispatchToProps)(Projects);