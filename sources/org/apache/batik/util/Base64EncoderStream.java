/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included with this distribution in  *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.batik.util;

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.IOException;

/**
 * This class implements a Base64 Character encoder as specified in RFC1113.
 * Unlike some other encoding schemes there is nothing in this encoding
 * that indicates where a buffer starts or ends.
 *
 * This means that the encoded text will simply start with the first line
 * of encoded text and end with the last line of encoded text.
 *
 * @author <a href="deweese@apache.org">Thomas DeWeese</a>
 * @author <a href="vincent.hardy@eng.sun.com">Vincent Hardy</a>
 * @author      Chuck McManis
 * @version $Id$
 * @see         Base64DecoderStream
 */

public class Base64EncoderStream extends OutputStream {

    /** This array maps the 6 bit values to their characters */
    private final static byte pem_array[] = {
    //   0   1   2   3   4   5   6   7
        'A','B','C','D','E','F','G','H', // 0
        'I','J','K','L','M','N','O','P', // 1
        'Q','R','S','T','U','V','W','X', // 2
        'Y','Z','a','b','c','d','e','f', // 3
        'g','h','i','j','k','l','m','n', // 4
        'o','p','q','r','s','t','u','v', // 5
        'w','x','y','z','0','1','2','3', // 6
        '4','5','6','7','8','9','+','/'  // 7
    };

    byte [] atom = new byte[3];
    int     atomLen = 0;
    byte [] encodeBuf = new byte[4];
    int     lineLen = 0;

    PrintStream  out;
    boolean closeOutOnClose;

    public Base64EncoderStream(OutputStream out) {
        this.out = new PrintStream(out);
        closeOutOnClose = true;
    }

    public Base64EncoderStream(OutputStream out, boolean closeOutOnClose) {
        this.out = new PrintStream(out);
        this.closeOutOnClose = closeOutOnClose;
    }

    public void close () throws IOException {
        if (out != null) {
            encodeAtom();
            out.flush();        
            if (closeOutOnClose)
                out.close();
            out=null;
        }
    }

    /**
     * This can't really flush out output since that may generate
     * '=' chars which would indicate the end of the stream.
     * Instead we flush out.  You can only be sure all output is 
     * writen by closing this stream.
     */
    public void flush() throws IOException {
        out.flush();
    }

    public void write(int b) throws IOException {
        atom[atomLen++] = (byte)b;
        if (atomLen == 3)
            encodeAtom();
    }

    public void write(byte []data) throws IOException {
        encodeFromArray(data, 0, data.length);
    }

    public void write(byte [] data, int off, int len) throws IOException {
        encodeFromArray(data, off, len);
    }

    /**
     * enocodeAtom - Take three bytes of input and encode it as 4
     * printable characters. Note that if the length in len is less
     * than three is encodes either one or two '=' signs to indicate
     * padding characters.
     */
    void encodeAtom() throws IOException {
        byte a, b, c;

        switch (atomLen) {
        case 0: return;
        case 1:
            a = atom[0];
            encodeBuf[0] = pem_array[((a >>> 2) & 0x3F)];
            encodeBuf[1] = pem_array[((a <<  4) & 0x30)];
            encodeBuf[2] = encodeBuf[3] = '=';
            break;
        case 2:
            a = atom[0];
            b = atom[1];
            encodeBuf[0] = pem_array[((a >>> 2) & 0x3F)];
            encodeBuf[1] = pem_array[(((a << 4) & 0x30) | ((b >>> 4) & 0x0F))];
            encodeBuf[2] = pem_array[((b  << 2) & 0x3C)];
            encodeBuf[3] = '=';
            break;
        default:
            a = atom[0];
            b = atom[1];
            c = atom[2];
            encodeBuf[0] = pem_array[((a >>> 2) & 0x3F)];
            encodeBuf[1] = pem_array[(((a << 4) & 0x30) | ((b >>> 4) & 0x0F))];
            encodeBuf[2] = pem_array[(((b << 2) & 0x3C) | ((c >>> 6) & 0x03))];
            encodeBuf[3] = pem_array[c & 0x3F];
        }
        if (lineLen == 64) {
            out.println();
            lineLen = 0;
        }
        out.write(encodeBuf);

        lineLen += 4;
        atomLen = 0;
    }

    /**
     * enocodeAtom - Take three bytes of input and encode it as 4
     * printable characters. Note that if the length in len is less
     * than three is encodes either one or two '=' signs to indicate
     * padding characters.
     */
    void encodeFromArray(byte[] data, int offset, int len) 
        throws IOException{
        byte a, b, c;
        if (len == 0)
            return;

        // System.out.println("atomLen: " + atomLen + 
        //                    " len: " + len + 
        //                    " offset:  " + offset);

        if (atomLen != 0) {
            switch(atomLen) {
            case 1:
                atom[1] = data[offset++]; len--; atomLen++;
                if (len == 0) return;
                atom[2] = data[offset++]; len--; atomLen++;
                break;
            case 2:
                atom[2] = data[offset++]; len--; atomLen++;
                break;
            default:
            }
            encodeAtom();
        }

        while (len >=3) {
            a = data[offset++];
            b = data[offset++];
            c = data[offset++];
            
            encodeBuf[0] = pem_array[((a >>> 2) & 0x3F)];
            encodeBuf[1] = pem_array[(((a << 4) & 0x30) | ((b >>> 4) & 0x0F))];
            encodeBuf[2] = pem_array[(((b << 2) & 0x3C) | ((c >>> 6) & 0x03))];
            encodeBuf[3] = pem_array[c & 0x3F];
            out.write(encodeBuf);

            lineLen += 4;
            if (lineLen == 64) {
                out.println();
                lineLen = 0;
            }

            len -=3;
        }

        switch (len) {
        case 1:
            atom[0] = data[offset];
            break;
        case 2:
            atom[0] = data[offset];
            atom[1] = data[offset+1];
            break;
        default:
        }
        atomLen = len;
    }

    
    
}