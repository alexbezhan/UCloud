import * as React from 'react'
import * as PropTypes from 'prop-types'
import styled, {
  ThemeProvider as StyledThemeProvider,
  injectGlobal
} from 'styled-components'
import nextTheme from './theme'

injectGlobal`body {
  margin: 0;
}`

export const Base = styled("div")<ThemeProviderProps>`
  font-family: ${props => props.theme.font};
  line-height: ${props => props.theme.lineHeights.standard};
  font-weight: ${props => props.theme.fontWeights.medium};

  * {
    box-sizing: border-box;
  }
`

const ThemeProvider = ({ breakpoints = nextTheme.breakpoints, ...props }) => {
  const theme = {
    ...nextTheme,
    breakpoints
  }

  return (
    <StyledThemeProvider theme={theme}>
      <Base {...props} />
    </StyledThemeProvider>
  )
}

interface ThemeProviderProps {
  customBreakpoints?: number[]
}

export default ThemeProvider