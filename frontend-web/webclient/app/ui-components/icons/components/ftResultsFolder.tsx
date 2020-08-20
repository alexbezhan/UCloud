import * as React from "react";

const SvgFtResultsFolder = (props: any) => (
  <svg
    viewBox="0 0 25 23"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M0 1.5A1.503 1.503 0 011.5 0H9l3 4h10.5A1.5 1.5 0 0124 5.5V8H0V1.5z"
      fill={props.color2 ? props.color2 : "currentcolor"}
    />
    <path
      d="M0 7.5A1.5 1.5 0 011.5 6h21A1.5 1.5 0 0124 7.5l.001 13.5a1.002 1.002 0 01-1 1H1a1 1 0 01-1-1V7.5z"
      fill={undefined}
    />
    <path
      d="M12 8l5.196 3v6L12 20l-5.196-3v-6L12 8z"
      fill={props.color2 ? props.color2 : "currentcolor"}
    />
    <path
      d="M17.446 11.433l-5.197 3-.5-.866 5.197-3 .5.866z"
      fill={undefined}
    />
    <path
      d="M12.249 13.567l-.5.866-5.195-3 .5-.866 5.195 3z"
      fill={undefined}
    />
    <path d="M12.5 20h-1l-.001-6h1l.001 6z" fill={undefined} />
  </svg>
);

export default SvgFtResultsFolder;
