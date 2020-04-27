import styled, {keyframes} from "styled-components";
import {color, space, SpaceProps} from "styled-system";
import theme, {Theme, ThemeColor} from "./theme";

// This should be possible by omitting fields in ThemeColors, but doesn't seem to work.
type BGColors = "blue" | "lightBlue" | "green" | "lightGreen" | "red" | "lightRed" | "orange" | "gray" | "lightGray";

export const colorScheme = (props: {theme: Theme, bg: BGColors}) => {
  const badgeColors = {
    blue: {
      backgroundColor: props.theme.colors.blue,
      color: props.theme.colors.white
    },
    lightBlue: {
      backgroundColor: props.theme.colors.lightBlue,
      color: props.theme.colors.darkBlue
    },
    green: {
      backgroundColor: props.theme.colors.green,
      color: props.theme.colors.white
    },
    lightGreen: {
      backgroundColor: props.theme.colors.lightGreen,
      color: props.theme.colors.darkGreen
    },
    red: {
      backgroundColor: props.theme.colors.red,
      color: props.theme.colors.white
    },
    lightRed: {
      backgroundColor: props.theme.colors.lightRed,
      color: props.theme.colors.darkRed
    },
    orange: {
      backgroundColor: props.theme.colors.orange,
      color: props.theme.colors.text
    },
    gray: {
      backgroundColor: props.theme.colors.gray,
      color: props.theme.colors.white
    },
    lightGray: {
      backgroundColor: props.theme.colors.lightGray,
      color: props.theme.colors.text
    }
  };
  return props.bg && badgeColors[props.bg] || badgeColors.lightGray;
};

const fadeIn = keyframes`
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
`;

const Badge = styled.div<SpaceProps & {color?: ThemeColor, bg?: BGColors}>`
  border-radius: 99999px;
  display: inline-block;
  font-size: ${theme.fontSizes[0]}px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: ${theme.letterSpacings.caps};
  ${space} ${colorScheme} ${color};
`;

Badge.displayName = "Badge";

Badge.defaultProps = {
  px: 2,
  py: 1
};

const DevelopmentBadgeBase = styled(Badge)`
  background-color: ${p => p.theme.colors.red};
  margin: 15px 25px 14px 5px;
  color: white;
  animation: ${fadeIn} 1.5s ease 1.5s infinite alternate;
  animation-direction: alternate;
`;

export default Badge;

export {DevelopmentBadgeBase};
