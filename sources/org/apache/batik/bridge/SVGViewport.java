/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included with this distribution in  *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.batik.bridge;

import java.io.StringReader;
import org.apache.batik.parser.LengthParser;
import org.apache.batik.parser.ParserFactory;
import org.apache.batik.util.SVGConstants;
import org.apache.batik.util.UnitProcessor;
import org.w3c.dom.svg.SVGSVGElement;

/**
 * Defines a viewport using an <tt>SVGSVGElement</tt>.
 *
 * @author <a href="mailto:Thierry.Kormann@sophia.inria.fr">Thierry Kormann</a>
 * @version $Id$
 */
public class SVGViewport implements Viewport, SVGConstants {

    private SVGSVGElement svgSvgElement;
    private UnitProcessor.Context uctx;

    /**
     * Constructs a new viewport with the specified <tt>SVGSVGElement</tt>.
     * @param svgSvgElement the element that defines the viewport
     * @param uctx the unit processor to use
     */
    public SVGViewport(SVGSVGElement svgSvgElement,
                       UnitProcessor.Context uctx) {
        this.svgSvgElement = svgSvgElement;
        this.uctx = uctx;
    }

    /**
     * Returns the width of this <tt>SVGSVGElement</tt>.
     */
    public float getWidth() {
        String s = svgSvgElement.getAttributeNS(null, ATTR_WIDTH);
        LengthParser p = uctx.getParserFactory().createLengthParser();
        UnitProcessor.UnitResolver ur = new UnitProcessor.UnitResolver();
        p.setLengthHandler(ur);
        p.parse(new StringReader(s));
        return UnitProcessor.svgToUserSpace(ur.unit,
                                            ur.value,
                                            svgSvgElement,
                                            UnitProcessor.HORIZONTAL_LENGTH,
                                            uctx);
    }

    /**
     * Returns the height of this viewport.
     */
    public float getHeight() {
        String s = svgSvgElement.getAttributeNS(null, ATTR_HEIGHT);
        LengthParser p = uctx.getParserFactory().createLengthParser();
        UnitProcessor.UnitResolver ur = new UnitProcessor.UnitResolver();
        p.setLengthHandler(ur);
        p.parse(new StringReader(s));
        return UnitProcessor.svgToUserSpace(ur.unit,
                                            ur.value,
                                            svgSvgElement,
                                            UnitProcessor.VERTICAL_LENGTH,
                                            uctx);
    }

}
