/*
Copyright 2017 Michael Chermside

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.mcherm.invalidationqueue.util;

import org.apache.commons.io.output.ByteArrayOutputStream;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import java.io.IOException;


/**
 * This is an implementation of ServletOutputStream which wraps an underlying
 * ServletOutputStream but buffers all the data written to it until
 * <code>flushBufferedOutput()</code> is called, then writes the buffered
 * data to the wrapped stream.
 * <p>
 * <b>Design Note:</b> This uses <code>ByteArrayOutputStream</code> from the Apache
 * Commons IO library instead of <code>java.io.ByteArrayOutputStream</code> for
 * performance reasons.
 */
public class BufferingServletOutputStream extends ServletOutputStream {

    // ===== Instance Variables and Constructor =====

    private final ServletOutputStream wrapped;
    private final ByteArrayOutputStream bufferedStream;

    /**
     * Constructor.
     *
     * @param wrapped the real ServletOutputStream to be wrapped.
     */
    public BufferingServletOutputStream(ServletOutputStream wrapped) {
        this.wrapped = wrapped;
        this.bufferedStream = new ByteArrayOutputStream();
    }


    // ===== Methods for dealing with the buffer =====

    /**
     * This flushed the buffered content to the real ServletOutputStream.
     */
    public void flushBufferedOutput() throws IOException {
        bufferedStream.writeTo(wrapped);
    }

    // ===== Overridden methods =====

    @Override
    public void write(int b) throws IOException {
        bufferedStream.write(b);
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
        // It is ready immediately (because of buffering)
        try {
            writeListener.onWritePossible();
        } catch(IOException err) {
            // Apparently our attempt to notify that writing was allowed didn't go well.
            // Go ahead and ignore the exception.
        }
    }
}
