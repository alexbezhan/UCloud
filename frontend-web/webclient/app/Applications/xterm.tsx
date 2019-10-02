import * as React from "react";
import {useEffect, useRef, useState} from "react";
import {isLightThemeStored} from "UtilityFunctions";
import {Terminal} from "xterm";
import {FitAddon} from "xterm-addon-fit";
import "xterm/css/xterm.css";

export function useXTerm(): [React.RefObject<HTMLDivElement>, (textToAppend: string) => void, () => void] {
    const [didMount, setDidMount] = useState(false);

    const [term] = useState(() => new Terminal({theme: getTheme()}));
    const [fitAddon] = useState(() => new FitAddon());
    const elem = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (elem.current) {
            if (!didMount) {
                term.loadAddon(fitAddon);
                term.open(elem.current);
                setDidMount(true);
            }
            fitAddon.fit();
        } else if (elem.current === null) {
            setDidMount(false);
        }
    });

    const [storedTheme, setStoredTheme] = useState(isLightThemeStored());

    if (isLightThemeStored() !== storedTheme) {
        setStoredTheme(isLightThemeStored());
        term.setOption("theme", getTheme());
    }

    return [
        elem,
        textToAppend => {
            const remainingString = textToAppend.replace(/\n/g, "\r\n");
            term.write(remainingString);
        },
        () => {
            term.reset();
        }
    ];
}

function getTheme() {
    const themeColors = isLightThemeStored() ? {
        background: "#f5f7f9",
        foreground: "#073642",
    } : {
            background: "#073642",
            foreground: "#e0e0e0",
        };
    return {
        ...themeColors,
        black: "#073642",
        brightBlack: "#002b36",
        white: "#eee8d5",
        brightWhite: "#fdf6e3",
        cursor: "#eee8d5",
        cursorAccent: "#eee8d5",
        brightGreen: "#586e75",
        brightYellow: "#657b83",
        brightBlue: "#839496",
        brightCyan: "#93a1a1",
        yellow: "#b58900",
        brightRed: "#cb4b16",
        red: "#dc322f",
        magenta: "#d33682",
        brightMagenta: "#6c71c4",
        blue: "#268bd2",
        cyan: "#2aa198",
        green: "#859900"
    };
}
