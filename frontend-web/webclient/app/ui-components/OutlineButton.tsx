import styled from "styled-components";
import {ButtonStyleProps} from "styled-system";
import Button from "./Button";
import theme, {Theme} from "./theme";

export interface OutlineButtonProps extends ButtonStyleProps {hovercolor?: string;}

// Different from the one in button because of border size
const size = (p: {size: string, theme: Theme}) => {
  switch (p.size) {
    case "tiny":
      return {
        fontSize: `${p.theme.fontSizes[0]}px`,
        padding: "3px 10px"
      };
    case "small":
      return {
        fontSize: `${p.theme.fontSizes[0]}px`,
        padding: "5px 12px"
      };
    case "medium":
      return {
        fontSize: `${p.theme.fontSizes[1]}px`,
        padding: "7.5px 18px"
      };
    case "large":
      return {
        fontSize: `${p.theme.fontSizes[2]}px`,
        padding: "10px 22px"
      };
    default:
      return {
        fontSize: `${p.theme.fontSizes[1]}px`,
        padding: "7.5px 18px"
      };
  }
};

const OutlineButton = styled(Button) <OutlineButtonProps>`
  color: ${props => props.color ? props.theme.colors[props.color] : props.theme.colors.blue};
  border: 2px solid ${props => props.color ? props.theme.colors[props.color] : props.theme.colors.blue};
  border-radius: ${props => props.theme.radius};
  background-color: transparent;

  &:hover {
    color: ${props => (props.disabled ? null : (props.hovercolor ? props.theme.colors[props.hovercolor] : null))};
    border: 2px solid ${props => props.hovercolor ? props.theme.colors[props.hovercolor] : null};
    background-color: transparent;
    transition: ease 0.1s;
  }

  ${size}
`;

OutlineButton.defaultProps = {
  theme
};

OutlineButton.displayName = "OutlineButton";

export default OutlineButton;
