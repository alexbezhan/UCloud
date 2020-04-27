import {configure, mount} from "enzyme";
import * as Adapter from "enzyme-adapter-react-16";
import * as React from "react";
import {Provider} from "react-redux";
import {MemoryRouter} from "react-router";
import {create} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import UserCreation from "../../app/Admin/UserCreation";
import PromiseKeeper from "../../app/PromiseKeeper";
import theme from "../../app/ui-components/theme";
import {store} from "../../app/Utilities/ReduxUtilities";

configure({adapter: new Adapter()});

const userCreation = () => (
    <Provider store={store}>
        <MemoryRouter>
            <ThemeProvider theme={theme}>
                <UserCreation />
            </ThemeProvider>
        </MemoryRouter>
    </Provider>
);

describe("UserCreation", () => {
    test("Mount", () => expect(create(userCreation()).toJSON()).toMatchSnapshot());

    test.skip("Update username field", () => {
        const uC = mount(userCreation());
        uC.find("FormField").findWhere(it => it.props().label === "Username").find("input").simulate("change", {target: {value: "username"}});
        expect(uC.state()).toEqual({
            promiseKeeper: new PromiseKeeper(),
            submitted: false,
            username: "username",
            password: "",
            repeatedPassword: "",
            usernameError: false,
            passwordError: false
        });
    });

    test.skip("Update password field", () => {
        const uC = mount(userCreation());
        uC.find("FormField").findWhere(it => it.props().label === "Password").find("input").simulate("change", {target: {value: "password"}});
        expect(uC.state()).toEqual({
            promiseKeeper: new PromiseKeeper(),
            submitted: false,
            username: "",
            password: "password",
            repeatedPassword: "",
            usernameError: false,
            passwordError: false
        });
    });

    test.skip("Update repeated password field", () => {
        const uC = mount(userCreation());
        uC.find("FormField").findWhere(it => it.props().label === "Repeat password").find("input").simulate("change", {target: {value: "repeatWord"}});
        expect(uC.state()).toEqual({
            promiseKeeper: new PromiseKeeper(),
            submitted: false,
            username: "",
            password: "",
            repeatedPassword: "repeatWord",
            usernameError: false,
            passwordError: false
        });
    });

    test.skip("Submit with missing username, causing errors to be rendered", () => {
        const uC = mount(userCreation());
        uC.find("FormField").findWhere(it => it.props().label === "Password").find("input").simulate("change", {target: {value: "password"}});
        uC.find("FormField").findWhere(it => it.props().label === "Repeat password").find("input").simulate("change", {target: {value: "password"}});
        uC.find("Button").findWhere(it => it.props().content === "Create user").simulate("submit");
        expect(uC.state("usernameError")).toBe(true);
        expect(uC.state("passwordError")).toBe(false);
    });

    test.skip("Submit with missing password fields, causing errors to be rendered", () => {
        const uC = mount(userCreation());
        uC.find("FormField").findWhere(it => it.props().label === "Username").find("input").simulate("change", {target: {value: "username"}});
        uC.find("Button").findWhere(it => it.props().content === "Create user").simulate("submit");
        expect(uC.state("passwordError")).toBe(true);
        expect(uC.state("usernameError")).toBe(false);
    });

    test.skip("Submit with non matching password fields, causing errors to be rendered", () => {
        const uC = mount(userCreation());
        uC.find("FormField").findWhere(it => it.props().label === "Password").find("input").simulate("change", {target: {value: "passwordAlso"}});
        uC.find("FormField").findWhere(it => it.props().label === "Repeat password").find("input").simulate("change", {target: {value: "password"}});
        uC.find("Button").findWhere(it => it.props().content === "Create user").simulate("submit");
        expect(uC.state("usernameError")).toBe(true);
        expect(uC.state("passwordError")).toBe(true);
    });
});
