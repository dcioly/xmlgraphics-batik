/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included with this distribution in  *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.batik.bridge;

import java.awt.Color;
import java.awt.Composite;
import java.awt.GraphicsEnvironment;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.CharacterIterator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.batik.dom.svg.SVGOMDocument;
import org.apache.batik.dom.util.XLinkSupport;
import org.apache.batik.dom.util.XMLSupport;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.gvt.GraphicsNodeRenderContext;
import org.apache.batik.gvt.TextNode;
import org.apache.batik.gvt.text.GVTAttributedCharacterIterator;
import org.apache.batik.util.CSSConstants;
import org.apache.batik.util.SVGConstants;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.css.CSSPrimitiveValue;
import org.w3c.dom.css.CSSStyleDeclaration;
import org.w3c.dom.css.CSSValue;
import org.w3c.dom.css.CSSValueList;

/**
 * Bridge class for the &lt;text> element.
 *
 * @author <a href="bill.haneman@ireland.sun.com>Bill Haneman</a>
 * @version $Id$
 */
public class SVGTextElementBridge implements GraphicsNodeBridge,
                                             CSSConstants,
                                             SVGConstants,
                                             ErrorConstants {

    /**
     * Constructs a new bridge for the &lt;text> element.
     */
    public SVGTextElementBridge() {}

    /**
     * Creates a <tt>GraphicsNode</tt> according to the specified parameters.
     *
     * @param ctx the bridge context to use
     * @param e the element that describes the graphics node to build
     * @return a graphics node that represents the specified element
     */
    public GraphicsNode createGraphicsNode(BridgeContext ctx, Element e) {

        TextNode node = new TextNode();
        // 'transform'
        String s = e.getAttributeNS(null, SVG_TRANSFORM_ATTRIBUTE);
        if (s.length() != 0) {
            node.setTransform
                (SVGUtilities.convertTransform(e, SVG_TRANSFORM_ATTRIBUTE, s));
        }
        // 'visibility'
        node.setVisible(CSSUtilities.convertVisibility(e));

        UnitProcessor.Context uctx = UnitProcessor.createContext(ctx, e);

        // 'x' attribute - default is 0
        s = e.getAttributeNS(null, SVG_X_ATTRIBUTE);
        float x = 0;
        if (s.length() != 0) {
            x = UnitProcessor.svgHorizontalCoordinateToUserSpace
                (s, SVG_X_ATTRIBUTE, uctx);
        }

        // 'y' attribute - default is 0
        s = e.getAttributeNS(null, SVG_Y_ATTRIBUTE);
        float y = 0;
        if (s.length() != 0) {
            y = UnitProcessor.svgVerticalCoordinateToUserSpace
                (s, SVG_Y_ATTRIBUTE, uctx);
        }

        node.setLocation(new Point2D.Float(x, y));
        return node;
    }

    /**
     * Builds using the specified BridgeContext and element, the
     * specified graphics node.
     *
     * @param ctx the bridge context to use
     * @param e the element that describes the graphics node to build
     * @param node the graphics node to build
     */
    public void buildGraphicsNode(BridgeContext ctx,
                                  Element e,
                                  GraphicsNode node) {
        e.normalize();
        AttributedString as = buildAttributedString(ctx, e, node);
        ((TextNode)node).setAttributedCharacterIterator(as.getIterator());

        // 'filter'
        node.setFilter(CSSUtilities.convertFilter(e, node, ctx));
        // 'mask'
        node.setMask(CSSUtilities.convertMask(e, node, ctx));
        // 'clip-path'
        node.setClip(CSSUtilities.convertClipPath(e, node, ctx));

        // bind the specified element and its associated graphics node if needed
        if (ctx.isDynamic()) {
            ctx.bind(e, node);
        }
    }

    /**
     * Returns false as text is not a container.
     */
    public boolean isComposite() {
        return false;
    }

    /**
     * Performs an update according to the specified event.
     *
     * @param evt the event describing the update to perform
     */
    public void update(BridgeMutationEvent evt) {
        throw new Error("Not implemented");
    }

    /**
     * Creates a <tt>TextNode</tt>.
     */
    protected GraphicsNode instantiateGraphicsNode() {
        return new TextNode();
    }

    // -----------------------------------------------------------------------
    // -----------------------------------------------------------------------
    // -----------------------------------------------------------------------
    // -----------------------------------------------------------------------

    /**
     * Creates the attributed string which represents the given text
     * element children.
     *
     * @param ctx the bridge context to use
     * @param element the text element
     * @param textNode the textNode that will be used to paint the text
     */
    protected AttributedString buildAttributedString(BridgeContext ctx,
                                                     Element element,
                                                     GraphicsNode node) {

        AttributedString result = null;
        List l = buildAttributedStrings
            (ctx, element, node, true, new LinkedList());

        // Simple cases
        switch (l.size()) {
        case 0:
            return new AttributedString(" ");
        case 1:
            return (AttributedString)l.get(0);
        }

        //
        // Merge the attributed strings
        //
        List buffers = new LinkedList();
        List maps = new LinkedList();
        Iterator it = l.iterator();

        // First pass: build the string buffer.
        StringBuffer sb = new StringBuffer();
        while (it.hasNext()) {
            AttributedString s = (AttributedString)it.next();
            AttributedCharacterIterator aci = s.getIterator();
            // Build the StringBuffer
            char c = aci.first();
            for (; c != CharacterIterator.DONE; c = aci.next()) {
                sb.append(c);
            }
        }
        result = new AttributedString(sb.toString());

        // Second pass: decorate the attributed string.
        int i=0;
        it = l.iterator();
        while (it.hasNext()) {
            AttributedString s = (AttributedString)it.next();
            AttributedCharacterIterator aci = s.getIterator();
            Iterator attrIter = aci.getAllAttributeKeys().iterator();
            while (attrIter.hasNext()) { // for each attribute key...
                AttributedCharacterIterator.Attribute key
                    = (AttributedCharacterIterator.Attribute) attrIter.next();
                int begin;
                int end;
                aci.first();
                do {
                    begin = aci.getRunStart(key);
                    end = aci.getRunLimit(key);
                    aci.setIndex(begin);
                    Object value = aci.getAttribute(key);
                    //System.out.println("Adding attribute "+key+": "+value+" from "+(i+begin)+"->"+(i+end));
                    result.addAttribute(key, value, i+begin, i+end);
                    aci.setIndex(end);
                } while (end < aci.getEndIndex()); // more runs in aci
            }
            i += aci.getEndIndex();
        }
        return result;
    }

    /**
     * Creates the attributed strings which represent the given text
     * element children.
     */
    protected List buildAttributedStrings(BridgeContext ctx,
                                          Element element,
                                          GraphicsNode node,
                                          boolean top,
                                          LinkedList result) {

        // !!! return two lists
        Map m = getAttributeMap(ctx, element, node);
        String s = XMLSupport.getXMLSpace(element);
        boolean preserve = s.equals("preserve");
        boolean first = true;
        boolean last;
        boolean stripFirst = !preserve;
        boolean stripLast = !preserve;
        Element nodeElement = element;
        AttributedString as = null;

        if (!result.isEmpty()) {
            as = (AttributedString) result.getLast();
        }

        for (Node n = element.getFirstChild();
             n != null;
             n = n.getNextSibling()) {

            last = n.getNextSibling() == null;

            int lastChar = (as != null) ?
                (as.getIterator().last()) : CharacterIterator.DONE;
            stripFirst = !preserve && first &&
                (top ||
                 (lastChar == ' ') ||
                 (lastChar == CharacterIterator.DONE));

            switch (n.getNodeType()) {
            case Node.ELEMENT_NODE:

                nodeElement = (Element)n;

                if (n.getLocalName().equals("tspan")
                    || n.getLocalName().equals("altGlyph")) {
                    buildAttributedStrings(ctx,
                                           nodeElement,
                                           node,
                                           false,
                                           result);
                } else if (n.getLocalName().equals("tref")) {
                    String uriStr = XLinkSupport.getXLinkHref((Element)n);
                    Element ref = ctx.getReferencedElement((Element)n, uriStr);
                    s = getElementContent(ref);
                    Map map = getAttributeMap(ctx, nodeElement, node);
                    int[] indexMap = new int[s.length()];
                    as = createAttributedString(s, map, indexMap, preserve,
                                                stripFirst, last && top);
                    if (as != null) {
                        // NOTE: we get position attributes from the
                        // surrounding text or tspan node, not the tref
                        // link target
                        addGlyphPositionAttributes
                            (as, true, indexMap, ctx, nodeElement);
                        stripLast = !preserve &&
                            (as.getIterator().first() == ' ');
                        if (stripLast) {
                            AttributedString las
                                = (AttributedString) result.removeLast();
                            if (las != null) {
                                AttributedCharacterIterator iter
                                    = las.getIterator();
                                int endIndex = iter.getEndIndex()-1;
                                if (iter.setIndex(endIndex) == ' ') {
                                    las = new AttributedString
                                        (las.getIterator (null, iter.getBeginIndex(), endIndex));
                                }
                                result.add(las);
                            }
                        }
                        result.add(as);
                    }
                }
                break;
            case Node.TEXT_NODE:
                s = n.getNodeValue();
                int[] indexMap = new int[s.length()];
                as = createAttributedString
                    (s, m, indexMap, preserve, stripFirst, last && top);
                if (as != null) {
                    if (first) {
                        addGlyphPositionAttributes
                            (as, !top, indexMap, ctx, element);
                    }
                    stripLast =
                        !preserve && (as.getIterator().first() == ' ');
                    if (stripLast && !result.isEmpty()) {
                        AttributedString las =
                            (AttributedString) result.removeLast();
                        if (las != null) {
                            AttributedCharacterIterator iter
                                = las.getIterator();
                            int endIndex = iter.getEndIndex()-1;
                            if (iter.setIndex(endIndex) == ' ') {
                                las = new AttributedString
                                    (las.getIterator(null,
                                                     iter.getBeginIndex(), endIndex));
                            }
                            result.add(las);
                        }
                    }
                    result.add(as);
                }
            }
            first = false;
        }
        return result;
    }

    /**
     * Returns the content of the given element.
     */
    protected String getElementContent(Element e) {
        StringBuffer result = new StringBuffer();
        for (Node n = e.getFirstChild();
             n != null;
             n = n.getNextSibling()) {
            switch (n.getNodeType()) {
            case Node.ELEMENT_NODE:
                result.append(getElementContent((Element)n));
                break;
            case Node.TEXT_NODE:
                result.append(n.getNodeValue());
            }
        }
        return result.toString();
    }

    /**
     * Creates an attributes string from the content of the given string.
     */
    protected AttributedString createAttributedString(String s,
                                                      Map m,
                                                      int[] indexMap,
                                                      boolean preserve,
                                                      boolean stripfirst,
                                                      boolean striplast) {
        AttributedString as = null;
        StringBuffer sb = new StringBuffer();
        if (preserve) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                case 10:
                case 13:
                case '\t':
                    sb.append(' ');
                    break;
                default:
                    sb.append(c);
                }
                indexMap[i] = i;
            }
        } else {
            boolean space = false;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                case 10:
                case 13:
                    break; // should break, newlines are not whitespace
                case ' ':
                case '\t':
                    if (!space) {
                        sb.append(' ');
                        indexMap[sb.length()-1] = i;
                        space = true;
                    }
                    break;
                default:
                    sb.append(c);
                    indexMap[sb.length()-1] = i;
                    space = false;
                }
            }
            if (stripfirst) {
                while (sb.length() > 0) {
                    if (sb.charAt(0) == ' ') {
                        sb.deleteCharAt(0);
                        System.arraycopy(indexMap, 1, indexMap, 0, sb.length());
                    } else {
                        break;
                    }
                }
            }
            if (striplast) {
                int len;
                while ((len = sb.length()) > 0) {
                    if (sb.charAt(len - 1) == ' ') {
                        sb.deleteCharAt(len - 1);
                    } else {
                        break;
                    }
                }
            }
            for (int i=sb.length(); i<indexMap.length; ++i) {
                indexMap[i] = -1;
            }
        }
        if (sb.length() > 0) {
            as = new AttributedString(sb.toString(), m);
        }
        return as;
    }

    /**
     * Adds glyph position attributes to an AttributedString.
     */
    protected void addGlyphPositionAttributes(AttributedString as,
                                              boolean isChild,
                                              int[] indexMap,
                                              BridgeContext ctx,
                                              Element element) {

        UnitProcessor.Context uctx
            = UnitProcessor.createContext(ctx, element);

        int asLength = as.getIterator().getEndIndex();
        // AttributedStrings always start at index 0, we hope!

        // glyph and sub-element positions
        String s = element.getAttributeNS(null, SVG_X_ATTRIBUTE);
        if (s.length() != 0) {
            float x[] = TextUtilities.svgHorizontalCoordinateArrayToUserSpace
                (element, SVG_X_ATTRIBUTE, s, ctx);

            as.addAttribute(GVTAttributedCharacterIterator.TextAttribute.X,
                            new Float(Float.NaN), 0, asLength);

            if ((x.length > 1) || (isChild)) {
                as.addAttribute(GVTAttributedCharacterIterator.TextAttribute.EXPLICIT_LAYOUT, new Boolean(true), 0, asLength);
            }

            for (int i=0; i<asLength; ++i) {
                if (i < x.length) {
                    as.addAttribute(GVTAttributedCharacterIterator.TextAttribute.X, new Float(x[i]), i, i+1);
                }
            }
        }
        // parse the y attribute, (default is 0)
        s = element.getAttributeNS(null, SVG_Y_ATTRIBUTE);
        if (s.length() != 0) {
            float y[] = TextUtilities.svgVerticalCoordinateArrayToUserSpace
                (element, SVG_Y_ATTRIBUTE, s, ctx);

            as.addAttribute(GVTAttributedCharacterIterator.TextAttribute.Y,
                            new Float(Float.NaN), 0, asLength);

            if ((y.length > 1) || (isChild)) {
                as.addAttribute
                    (GVTAttributedCharacterIterator.TextAttribute.EXPLICIT_LAYOUT, new Boolean(true), 0, asLength);
            }

            for (int i=0; i<asLength; ++i) {
                if (i < y.length) {
                    as.addAttribute
                        (GVTAttributedCharacterIterator.TextAttribute.Y,
                         new Float(y[i]), i, i+1);
                }
            }
        }

        s = element.getAttributeNS(null, SVG_DX_ATTRIBUTE);
        if (s.length() != 0) {
            float x[] = TextUtilities.svgHorizontalCoordinateArrayToUserSpace
                (element, SVG_DX_ATTRIBUTE, s, ctx);

            as.addAttribute
                (GVTAttributedCharacterIterator.TextAttribute.DX,
                 new Float(Float.NaN), 0, asLength);

            for (int i=0; i<asLength; ++i) {
                if (i < x.length) {
                    as.addAttribute
                        (GVTAttributedCharacterIterator.TextAttribute.DX,
                         new Float(x[i]), i, i+1);
                }
            }
        }

        // parse the y attribute, (default is 0)
        s = element.getAttributeNS(null, SVG_DY_ATTRIBUTE);
        if (s.length() != 0) {
            float y[] = TextUtilities.svgVerticalCoordinateArrayToUserSpace
                (element, SVG_DY_ATTRIBUTE, s, ctx);

            as.addAttribute
                (GVTAttributedCharacterIterator.TextAttribute.DY,
                 new Float(Float.NaN), 0, asLength);

            for (int i=0; i<asLength; ++i) {
                if (i < y.length) {
                    as.addAttribute
                        (GVTAttributedCharacterIterator.TextAttribute.DY,
                         new Float(y[i]), i, i+1);
                }
            }
        }
    }

    /**
     * Returns the map to pass to the current characters.
     */
    protected Map getAttributeMap(BridgeContext ctx,
                                  Element element,
                                  GraphicsNode node) {

        CSSStyleDeclaration cssDecl = CSSUtilities.getComputedStyle(element);
        UnitProcessor.Context uctx = UnitProcessor.createContext(ctx, element);

        Map result = new HashMap();
        CSSPrimitiveValue v;
        String s;
        float f;
        short t;

        result.put(GVTAttributedCharacterIterator.TextAttribute.TEXT_COMPOUND_DELIMITER, element);

        // Text-anchor
        v = (CSSPrimitiveValue)cssDecl.getPropertyCSSValue
            (CSS_TEXT_ANCHOR_PROPERTY);
        s = v.getStringValue();
        TextNode.Anchor a;
        switch (s.charAt(0)) {
        case 's':
            a = TextNode.Anchor.START;
            break;
        case 'm':
            a = TextNode.Anchor.MIDDLE;
            break;
        default:
            a = TextNode.Anchor.END;
        }
        result.put(GVTAttributedCharacterIterator.TextAttribute.ANCHOR_TYPE, a);

        // Font size, in user space units.
        float fs = TextUtilities.convertFontSize(element, ctx, cssDecl, uctx);

        result.put(TextAttribute.SIZE, new Float(fs));

        // Font family
        CSSValueList ff = (CSSValueList)cssDecl.getPropertyCSSValue
            (CSS_FONT_FAMILY_PROPERTY);
        s = null;
        for (int i = 0; s == null && i < ff.getLength(); i++) {
            v = (CSSPrimitiveValue)ff.item(i);
            s = (String)fonts.get(v.getStringValue());
        }
        s = (s == null) ? "SansSerif" : s;
        result.put(TextAttribute.FAMILY, s);

        // Font weight
        // TODO: improve support for relative values
        // (e.g. "lighter", "bolder")
        v = (CSSPrimitiveValue)cssDecl.getPropertyCSSValue
            (CSS_FONT_WEIGHT_PROPERTY);
        if (v.getPrimitiveType() == CSSPrimitiveValue.CSS_IDENT) {
            //System.out.println("CSS Font Weight "+v.getStringValue());
            if (v.getStringValue().charAt(0) == 'n') {
                result.put(TextAttribute.WEIGHT,
                           TextAttribute.WEIGHT_REGULAR);
            } else if (v.getStringValue().charAt(0) == 'l') {
                result.put(TextAttribute.WEIGHT,
                           TextAttribute.WEIGHT_LIGHT);
            } else {
                result.put(TextAttribute.WEIGHT,
                           TextAttribute.WEIGHT_BOLD);
            }
        } else {
            //System.out.println("CSS Font Weight "+v.getFloatValue(CSSPrimitiveValue.CSS_NUMBER));
            switch ((int)v.getFloatValue(CSSPrimitiveValue.CSS_NUMBER)) {
            case 100:
                result.put(TextAttribute.WEIGHT,
                           TextAttribute.WEIGHT_EXTRA_LIGHT);
                break;
            case 200:
                result.put(TextAttribute.WEIGHT,
                           TextAttribute.WEIGHT_LIGHT);
                break;
            case 300:
                result.put(TextAttribute.WEIGHT,
                           TextAttribute.WEIGHT_DEMILIGHT);
                break;
            case 400:
                result.put(TextAttribute.WEIGHT,
                           TextAttribute.WEIGHT_REGULAR);
                break;
            case 500:
                result.put(TextAttribute.WEIGHT,
                           TextAttribute.WEIGHT_SEMIBOLD);
                break;
            case 600:
                result.put(TextAttribute.WEIGHT,
                           //TextAttribute.WEIGHT_DEMIBOLD);
                           TextAttribute.WEIGHT_BOLD);
                break;
            case 700:
                result.put(TextAttribute.WEIGHT,
                           TextAttribute.WEIGHT_BOLD);
                break;
            case 800:
                result.put(TextAttribute.WEIGHT,
                           //TextAttribute.WEIGHT_EXTRABOLD);
                           TextAttribute.WEIGHT_BOLD);
                break;
            case 900:
                result.put(TextAttribute.WEIGHT,
                           //TextAttribute.WEIGHT_ULTRABOLD);
                           TextAttribute.WEIGHT_BOLD);
            }
        }

        // Text baseline adjustment.
        // TODO: support for <percentage> and <length> values.
        v = (CSSPrimitiveValue)cssDecl.getPropertyCSSValue
            (CSS_BASELINE_SHIFT_PROPERTY);
        if (v.getPrimitiveType() == CSSPrimitiveValue.CSS_IDENT) {
            s = v.getStringValue();
            //System.out.println("Baseline-shift: "+s);
            switch (s.charAt(2)) {
            case 'p': //suPerscript
                result.put(GVTAttributedCharacterIterator.
                           TextAttribute.BASELINE_SHIFT,
                           TextAttribute.SUPERSCRIPT_SUPER);
                break;
            case 'b': //suBscript
                result.put(GVTAttributedCharacterIterator.
                           TextAttribute.BASELINE_SHIFT,
                           TextAttribute.SUPERSCRIPT_SUB);
                break;
            case 's': //baSeline
                break;
            }
        } else if (v.getPrimitiveType() == CSSPrimitiveValue.CSS_PERCENTAGE) {
            f = v.getFloatValue(v.getPrimitiveType());
            result.put(GVTAttributedCharacterIterator.TextAttribute.BASELINE_SHIFT,
                       new Float(f*fs/100f));
        } else {
            // TODO
            f = UnitProcessor.cssOtherLengthToUserSpace
                (v, CSS_BASELINE_SHIFT_PROPERTY, uctx);

            // XXX: HORIZONTAL LENGTH not appropriate for vertical layout!

            result.put(GVTAttributedCharacterIterator.TextAttribute.BASELINE_SHIFT, new Float(f));
        }

        // Unicode-bidi mode
        // full support requires revision: see comments
        // below regarding 'direction'

        v = (CSSPrimitiveValue)cssDecl.getPropertyCSSValue
            (CSS_UNICODE_BIDI_PROPERTY);
        s = v.getStringValue();
        if (s.charAt(0) == 'n') {
            result.put(TextAttribute.BIDI_EMBEDDING,
                       new Integer(0));
        } else {

            // Text direction
            // XXX: this needs to coordinate with the unicode-bidi
            // property, so that when an explicit reversal
            // occurs, the BIDI_EMBEDDING level is
            // appropriately incremented or decremented.
            // Note that direction is implicitly handled by unicode
            // BiDi algorithm in most cases, this property
            // is only needed when one wants to override the
            // normal writing direction for a string/substring.

            v = (CSSPrimitiveValue)cssDecl.getPropertyCSSValue
                (CSS_DIRECTION_PROPERTY);
            String rs = v.getStringValue();
            switch (rs.charAt(0)) {
            case 'l':
                result.put(TextAttribute.RUN_DIRECTION,
                           TextAttribute.RUN_DIRECTION_LTR);
                break;
            case 'r':
                result.put(TextAttribute.RUN_DIRECTION,
                           TextAttribute.RUN_DIRECTION_RTL);
                switch (s.charAt(0)) {
                case 'b': // bidi-override
                    result.put(TextAttribute.BIDI_EMBEDDING,
                               new Integer(-1));
                    break;
                case 'e': // embed
                    result.put(TextAttribute.BIDI_EMBEDDING,
                               new Integer(1));
                    break;
                }
                break;
            }
        }

        // Writing mode

        v = (CSSPrimitiveValue)cssDecl.getPropertyCSSValue
            (CSS_WRITING_MODE_PROPERTY);
        s = v.getStringValue();
        switch (s.charAt(0)) {
        case 'l':
            result.put(GVTAttributedCharacterIterator.
                       TextAttribute.WRITING_MODE,
                       GVTAttributedCharacterIterator.
                       TextAttribute.WRITING_MODE_LTR);
            break;
        case 'r':
            result.put(GVTAttributedCharacterIterator.
                       TextAttribute.WRITING_MODE,
                       GVTAttributedCharacterIterator.
                       TextAttribute.WRITING_MODE_RTL);
            break;
        case 't':
            break;
        }

        // Font style
        v = (CSSPrimitiveValue)cssDecl.getPropertyCSSValue
            (CSS_FONT_STYLE_PROPERTY);
        s = v.getStringValue();
        switch (s.charAt(0)) {
        case 'n':
            result.put(TextAttribute.POSTURE,
                       TextAttribute.POSTURE_REGULAR);
            break;
        case 'o':
        case 'i':
            result.put(TextAttribute.POSTURE,
                       TextAttribute.POSTURE_OBLIQUE);
        }

        // Font stretch
        v = (CSSPrimitiveValue)cssDecl.getPropertyCSSValue
            (CSS_FONT_STRETCH_PROPERTY);
        s = v.getStringValue();
        switch (s.charAt(0)) {
        case 'u':
            if (s.charAt(6) == 'c') {
                result.put(TextAttribute.WIDTH,
                           TextAttribute.WIDTH_CONDENSED);
            } else {
                result.put(TextAttribute.WIDTH,
                           TextAttribute.WIDTH_EXTENDED);
            }
            break;
        case 'e':
            if (s.charAt(6) == 'c') {
                result.put(TextAttribute.WIDTH,
                           TextAttribute.WIDTH_CONDENSED);
            } else {
                if (s.length() == 8) {
                    result.put(TextAttribute.WIDTH,
                               TextAttribute.WIDTH_SEMI_EXTENDED);
                } else {
                    result.put(TextAttribute.WIDTH,
                               TextAttribute.WIDTH_EXTENDED);
                }
            }
            break;
        case 's':
            if (s.charAt(6) == 'c') {
                result.put(TextAttribute.WIDTH,
                           TextAttribute.WIDTH_SEMI_CONDENSED);
            } else {
                result.put(TextAttribute.WIDTH,
                           TextAttribute.WIDTH_SEMI_EXTENDED);
            }
            break;
        default:
            result.put(TextAttribute.WIDTH,
                       TextAttribute.WIDTH_REGULAR);
        }

        // text spacing properties...

        // Letter Spacing
        v = (CSSPrimitiveValue)cssDecl.getPropertyCSSValue
            (CSS_LETTER_SPACING_PROPERTY);
        t = v.getPrimitiveType();
        if (t != CSSPrimitiveValue.CSS_IDENT) {
            f = UnitProcessor.cssHorizontalCoordinateToUserSpace
                (v, CSS_LETTER_SPACING_PROPERTY, uctx);

            // XXX: HACK: Assuming horizontal length units is wrong,
            // layout might be vertical!

            result.put(GVTAttributedCharacterIterator.
                       TextAttribute.LETTER_SPACING,
                       new Float(f));
            result.put(GVTAttributedCharacterIterator.
                       TextAttribute.CUSTOM_SPACING,
                       new Boolean(true));
        }

        // Word spacing
        v = (CSSPrimitiveValue)cssDecl.getPropertyCSSValue
            (CSS_WORD_SPACING_PROPERTY);
        t = v.getPrimitiveType();
        if (t != CSSPrimitiveValue.CSS_IDENT) {
            f = UnitProcessor.cssOtherLengthToUserSpace
                (v, CSS_WORD_SPACING_PROPERTY, uctx);

            // XXX: HACK: Assuming horizontal length units is wrong,
            // layout might be vertical!

            result.put(GVTAttributedCharacterIterator.TextAttribute.WORD_SPACING,
                       new Float(f));
            result.put(GVTAttributedCharacterIterator.
                       TextAttribute.CUSTOM_SPACING,
                       new Boolean(true));
        }

        // Kerning
        s = element.getAttributeNS(null, ATTR_KERNING);
        if (s.length() != 0) {
            f = UnitProcessor.svgHorizontalLengthToUserSpace
                (s, ATTR_KERNING, uctx);

            // XXX: Assuming horizontal length units is wrong,
            // layout might be vertical!

            result.put(GVTAttributedCharacterIterator.TextAttribute.KERNING,
                       new Float(f));
            result.put(GVTAttributedCharacterIterator.
                       TextAttribute.CUSTOM_SPACING,
                       new Boolean(true));
        }

        // textLength
        s = element.getAttributeNS(null, SVGConstants.ATTR_TEXT_LENGTH);
        if (s.length() != 0) {
            f = UnitProcessor.svgHorizontalLengthToUserSpace
                (s, ATTR_TEXT_LENGTH, uctx);

            // XXX: Assuming horizontal length units is wrong,
            // layout might be vertical!

            result.put(GVTAttributedCharacterIterator.TextAttribute.BBOX_WIDTH,
                       new Float(f));
            // lengthAdjust
            s = element.getAttributeNS(null, SVGConstants.ATTR_LENGTH_ADJUST);

            if (s.length() < 10) {
                result.put(GVTAttributedCharacterIterator.
                           TextAttribute.LENGTH_ADJUST,
                           GVTAttributedCharacterIterator.TextAttribute.ADJUST_SPACING);
                result.put(GVTAttributedCharacterIterator.
                           TextAttribute.CUSTOM_SPACING,
                           new Boolean(true));
            } else {
                result.put(GVTAttributedCharacterIterator.
                           TextAttribute.LENGTH_ADJUST,
                           GVTAttributedCharacterIterator.TextAttribute.ADJUST_ALL);
            }

        }

        // Opacity
        Composite composite = CSSUtilities.convertOpacity(element);
        result.put(GVTAttributedCharacterIterator.TextAttribute.OPACITY,
                   composite);

        // Fill
        Paint p = PaintServer.convertFillPaint(element, node, ctx);
        if (p != null) {
            result.put(TextAttribute.FOREGROUND, p);
        }

        // Stroke Paint
        Paint sp = PaintServer.convertStrokePaint(element, node, ctx);
        if (sp != null) {
            result.put
                (GVTAttributedCharacterIterator.TextAttribute.STROKE_PAINT, sp);
        }

        // Stroke
        Stroke stroke = PaintServer.convertStroke(element, ctx);
        if(stroke != null){
            result.put(GVTAttributedCharacterIterator.TextAttribute.STROKE,
                       stroke);
        }

        // Text decoration
        CSSValue cssVal = cssDecl.getPropertyCSSValue
            (CSS_TEXT_DECORATION_PROPERTY);
        t = cssVal.getCssValueType();
        if (t == CSSValue.CSS_VALUE_LIST) {
            CSSValueList lst = (CSSValueList)cssVal;
            for (int i = 0; i < lst.getLength(); i++) {
                v = (CSSPrimitiveValue)lst.item(i);
                s = v.getStringValue();
                switch (s.charAt(0)) {
                case 'u':
                    result.put(
                               GVTAttributedCharacterIterator.TextAttribute.UNDERLINE,
                               GVTAttributedCharacterIterator.TextAttribute.UNDERLINE_ON);
                    if (sp != null) {
                        result.put(GVTAttributedCharacterIterator.
                                   TextAttribute.UNDERLINE_STROKE_PAINT, sp);
                    }
                    if (stroke != null) {
                        result.put(GVTAttributedCharacterIterator.
                                   TextAttribute.UNDERLINE_STROKE, stroke);
                    }
                    if (p != null) {
                        result.put(GVTAttributedCharacterIterator.
                                   TextAttribute.UNDERLINE_PAINT, p);
                    }
                    break;
                case 'o':
                    result.put(GVTAttributedCharacterIterator.TextAttribute.OVERLINE,
                               GVTAttributedCharacterIterator.TextAttribute.OVERLINE_ON);
                    break;
                case 'l':
                    result.put
                        (GVTAttributedCharacterIterator.TextAttribute.STRIKETHROUGH,
                         GVTAttributedCharacterIterator.TextAttribute.STRIKETHROUGH_ON);
                }
            }
        }

        return result;
    }


    protected final static Map fonts = new HashMap(11);
    static {
        fonts.put("serif",           "Serif");
        fonts.put("Times",           "Serif");
        fonts.put("Times New Roman", "Serif");
        fonts.put("sans-serif",      "SansSerif");
        fonts.put("cursive",         "Dialog");
        fonts.put("fantasy",         "Symbol");
        fonts.put("monospace",       "Monospaced");
        fonts.put("monospaced",      "Monospaced");
        fonts.put("Courier",         "Monospaced");

        //
        // Load all fonts. Work around
        //
        /* Note to maintainer:  font init code should not be here!
         * we should not have dependencies in the Bridge
         * on java2d Fonts - they are not relevant to non-rasterizing
         * renderers!
         * We should instead support a list of fonts, in order of preference,
         * as CSS allows, and defer resolving these names to actual
         * implementation-dependent font names until render time.
         *
         *                -Bill Haneman
         */
        GraphicsEnvironment env;
        env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        // System.out.println("Initializing fonts .... please wait");
        String fontNames[] = env.getAvailableFontFamilyNames();
        int nFonts = fontNames != null ? fontNames.length : 0;
        // System.out.println("Done initializing " + nFonts + " fonts");
        for(int i=0; i<nFonts; i++){
            fonts.put(fontNames[i], fontNames[i]);
            //System.out.println(fontNames[i]);
        }
    }
}
