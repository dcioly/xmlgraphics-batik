/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included with this distribution in  *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.batik.css.parser;

import org.w3c.css.sac.Selector;
import org.w3c.css.sac.SelectorList;

/**
 * This class implements the {@link SelectorList} interface.
 *
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion</a>
 * @version $Id$
 */
public class CSSSelectorList implements SelectorList {

    /**
     * The list.
     */
    protected Selector[] list = new Selector[3];

    /**
     * The list length.
     */
    protected int length;

    /**
     * <b>SAC</b>: Returns the length of this selector list
     */    
    public int getLength() {
        return length;
    }

    /**
     * <b>SAC</b>: Returns the selector at the specified index, or
     * <code>null</code> if this is not a valid index.  
     */
    public Selector item(int index) {
        if (index < 0 || index >= length) {
            return null;
        }
        return list[index];
    }

    /**
     * Appends an item to the list.
     */
    public void append(Selector item) {
        if (length == list.length) {
            Selector[] tmp = list;
            list = new Selector[list.length * 3 / 2];
            for (int i = 0; i < tmp.length; i++) {
                list[i] = tmp[i];
            }
        }
        list[length++] = item;
    }
}
