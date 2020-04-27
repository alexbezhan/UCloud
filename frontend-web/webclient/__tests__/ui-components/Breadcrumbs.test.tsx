import {configure, mount} from "enzyme";
import * as Adapter from "enzyme-adapter-react-16";
import * as React from "react";
import {create} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import {BreadCrumbs} from "../../app/ui-components/Breadcrumbs";
import theme from "../../app/ui-components/theme";
configure({adapter: new Adapter()});


describe("Breadcrumbs", () => {
    it("Build breadcrumbs", () => {
        expect(create(
            <ThemeProvider theme={theme}>
                <BreadCrumbs
                    currentPath="/home/mail@mailhost.dk/folder1"
                    navigate={() => undefined}
                    homeFolder={"/home/mail@mailhost.dk"}
                    projectFolder="/project/hello"
                />
            </ThemeProvider>)).toMatchSnapshot();
    });

    it("Build breadcrumbs with empty path", () => {
        expect(create(
            <ThemeProvider theme={theme}>
        <BreadCrumbs
            currentPath=""
            navigate={() => undefined}
            homeFolder={"mail@mailhost.dk"}
            projectFolder="otherthing"
        /></ThemeProvider>)).toMatchSnapshot();
    });

    it("Using navigate", () => {
        const navigate = jest.fn();
        const breadcrumbs = mount(<ThemeProvider theme={theme}><BreadCrumbs
            currentPath="/home/mail@mailhost.dk/folder1"
            navigate={navigate}
            homeFolder={"/home/mail@mailhost.dk"}
            projectFolder="otherthing"
        /></ThemeProvider>);
        breadcrumbs.find("span").first().simulate("click");
        expect(navigate).toHaveBeenCalled();
    });
});
