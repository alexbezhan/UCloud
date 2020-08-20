import * as React from "react";

const SvgFiles = (props: any) => (
  <svg
    viewBox="0 0 24 22"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M16.711 4.128H23.4c.33 0 .6.309.6.687v16.5c0 .378-.27.688-.6.688H6.309L16.711 4.128z"
      fill={props.color2 ? props.color2 : "currentcolor"}
    />
    <path
      d="M10.8 4.125h5.911L6.309 22H.6c-.33 0-.6-.309-.6-.687V.688C0 .339.229.049.6 0h6.6l3.6 4.125z"
      fill={undefined}
    />
  </svg>
);

export default SvgFiles;
