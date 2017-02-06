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

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;


/**
 * A wrapper for HttpServletResponse that buffers the content until flushBufferedOutput() is called.
 * <p>
 * HTTP headers, including the Set-Cookie header, are normally passed in the header
 * section, before the body of the response. But also HttpServletResponse is begin
 * streaming body data back to the browser as soon as it is generated, for optimum
 * speed. The problem with this arrangement comes if, during the process of processing
 * the page content, you generate information that should be returned in a cookie --
 * such as the entries in the invalidation queue. Calling <code>setCookie()</code> (or
 * <code>setHeader()</code>) on the HttpServletResponse after the first byte of content
 * has been written to the body will be (silently) ignored.
 * <p>
 * Thus, in order to be able to add the cookie after processing the request, we have to
 * buffer the content of the body, then transmit it only after the cookie has been set.
 * This class supports that behavior: it buffers all content written to the OutputStream
 * until after <code>flushBufferedOutput()</code> has been called. a Filter can create
 * a BufferingHttpServletResponse that wraps the underlying HttpServletResponse and
 * pass that to <code>filterChain.doFilter()</code>, then call
 * <code>flushBufferedOutput()</code> after it returns.
 * <p>
 * In terms of performance, the fact that we buffer the content means that it cannot be
 * streamed to the client until processing is finished. If the framework we were using
 * made effective use of streaming to return huge-sized content with low memory use or
 * to send back the beginning of the page long before the computation is finished, then
 * that might be a performance issue. But Spring (as it is commonly used) generates the
 * entire response in memory (usually in the form of a bind object) before beginning to
 * send it back to the browser, so there is no significant performance impact.
 */
public class BufferingHttpServletResponse extends HttpServletResponseWrapper {

    // ===== Instance Variables and Constructor =====

    private final HttpServletResponse wrapped;
    private BufferingServletOutputStream bufferedOutputStream;

    public BufferingHttpServletResponse(HttpServletResponse wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
        this.bufferedOutputStream = null;
    }

    // ===== Methods for dealing with the buffer =====

    /**
     * Call this method to take the data that has been buffered, and send
     * it to the underlying buffer.
     */
    public void flushBufferedOutput() throws IOException {
        if (this.bufferedOutputStream != null) {
            this.bufferedOutputStream.flushBufferedOutput();
        }
        wrapped.flushBuffer();
    }

    // ===== Overridden methods =====

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (this.bufferedOutputStream == null) {
            ServletOutputStream trueStream = wrapped.getOutputStream();
            this.bufferedOutputStream = new BufferingServletOutputStream(trueStream);
        }
        return this.bufferedOutputStream;
    }

}
