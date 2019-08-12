/*

  MIT License

  Copyright (c) 2017 Pablo Stanley, Fang-Pen Lin
  
  Permission is hereby granted, free of charge, to any person obtaining a copy
  of this software and associated documentation files (the "Software"), to deal
  in the Software without restriction, including without limitation the rights
  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  copies of the Software, and to permit persons to whom the Software is
  furnished to do so, subject to the following conditions:
  
  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.
  
  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
  SOFTWARE.

*/

import * as PropTypes from 'prop-types'
import * as React from 'react'

import Avatar, {AvatarStyle} from './avatar'
import {OptionContext, allOptions} from './options'

export {default as Avatar, AvatarStyle} from './avatar'
export {Option, OptionContext, allOptions} from './options'

import {default as PieceComponent} from './avatar/piece';

export interface Props {
  avatarStyle: string
  style?: React.CSSProperties
  topType?: string
  accessoriesType?: string
  hairColor?: string
  facialHairType?: string
  facialHairColor?: string
  clotheType?: string
  clotheColor?: string
  graphicType?: string
  eyeType?: string
  eyebrowType?: string
  mouthType?: string
  skinColor?: string
  pieceType?: string
  pieceSize?: string
  viewBox?: string
}

export default class AvatarComponent extends React.Component<Props> {
  static childContextTypes = {
    optionContext: PropTypes.instanceOf(OptionContext)
  };
  private optionContext: OptionContext = new OptionContext(allOptions);

  getChildContext() {
    return {optionContext: this.optionContext}
  }

  componentWillMount() {
    this.updateOptionContext(this.props)
  }

  componentWillReceiveProps(nextProps: Props) {
    this.updateOptionContext(nextProps)
  }

  render() {
    const {avatarStyle, style} = this.props;
    return <Avatar avatarStyle={avatarStyle as AvatarStyle} style={style} />
  }

  private updateOptionContext(props: Props) {
    const data: {[index: string]: string} = {};
    for (const option of allOptions) {
      const value = props[option.key];
      if (!value) {
        continue
      }
      data[option.key] = value
    }
    this.optionContext.setData(data)
  }
}

export class Piece extends React.Component<Props> {
  static childContextTypes = {
    optionContext: PropTypes.instanceOf(OptionContext)
  };
  private optionContext: OptionContext = new OptionContext(allOptions);

  getChildContext() {
    return {optionContext: this.optionContext}
  }

  componentWillMount() {
    this.updateOptionContext(this.props)
  }

  componentWillReceiveProps(nextProps: Props) {
    this.updateOptionContext(nextProps)
  }

  render() {
    const {avatarStyle, style, pieceType, pieceSize, viewBox} = this.props;
    return <PieceComponent avatarStyle={avatarStyle as AvatarStyle} style={style} pieceType={pieceType} pieceSize={pieceSize} viewBox={viewBox} />
  }

  private updateOptionContext(props: Props) {
    const data: {[index: string]: string} = {};
    for (const option of allOptions) {
      const value = props[option.key];
      if (!value) {
        continue
      }
      data[option.key] = value
    }
    this.optionContext.setData(data)
  }
}
