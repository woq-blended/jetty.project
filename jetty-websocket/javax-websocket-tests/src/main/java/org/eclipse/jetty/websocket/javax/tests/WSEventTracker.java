//
//  ========================================================================
//  Copyright (c) 1995-2019 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.websocket.javax.tests;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.hamcrest.Matcher;

import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings("unused")
public abstract class WSEventTracker
{
    public final Logger LOG;

    public abstract static class Basic extends WSEventTracker
    {
        public Basic(String id)
        {
            super(id);
        }

        @OnOpen
        public void onOpen(Session session)
        {
            super.onWsOpen(session);
        }

        @OnClose
        public void onClose(CloseReason closeReason)
        {
            super.onWsClose(closeReason);
        }

        @OnError
        public void onError(Throwable cause)
        {
            super.onWsError(cause);
        }
    }

    public Session session;
    public EndpointConfig config;

    public CountDownLatch openLatch = new CountDownLatch(1);
    public CountDownLatch closeLatch = new CountDownLatch(1);
    public AtomicReference<CloseReason> closeDetail = new AtomicReference<>();
    public AtomicReference<Throwable> error = new AtomicReference<>();
    public BlockingQueue<String> messageQueue = new LinkedBlockingDeque<>();
    public BlockingQueue<ByteBuffer> bufferQueue = new LinkedBlockingDeque<>();
    public BlockingQueue<String> events = new LinkedBlockingDeque<>();

    public WSEventTracker()
    {
        this("JsrTrackingEndpoint");
    }

    public WSEventTracker(String id)
    {
        LOG = Log.getLogger(this.getClass().getName() + "." + id);
        LOG.debug("init");
    }

    public void addEvent(String format, Object... args)
    {
        events.offer(String.format(format, args));
    }

    public void assertCloseInfo(String prefix, int expectedCloseStatusCode, Matcher<? super String> reasonMatcher) throws InterruptedException
    {
        CloseReason close = closeDetail.get();
        assertThat(prefix + " close info", close, notNullValue());
        assertThat(prefix + " received close code", close.getCloseCode().getCode(), is(expectedCloseStatusCode));
        assertThat(prefix + " received close reason", close.getReasonPhrase(), reasonMatcher);
    }

    public void assertErrorEvent(String prefix, Matcher<Throwable> throwableMatcher, Matcher<? super String> messageMatcher)
    {
        assertThat(prefix + " error event type", error.get(), throwableMatcher);
        assertThat(prefix + " error event message", error.get().getMessage(), messageMatcher);
    }

    public void assertNoErrorEvents(String prefix)
    {
        assertTrue(error.get() == null, prefix + " error event should not have occurred");
    }

    public void assertNotClosed(String prefix)
    {
        assertTrue(closeLatch.getCount() > 0, prefix + " close event should not have occurred");
    }

    public void assertNotOpened(String prefix)
    {
        assertTrue(openLatch.getCount() > 0, prefix + " onOpen event should not have occurred");
    }

    public void awaitCloseEvent(String prefix) throws InterruptedException
    {
        assertTrue(closeLatch.await(Timeouts.CLOSE_EVENT_MS, TimeUnit.MILLISECONDS), prefix + " onClose event");
    }

    public void awaitOpenEvent(String prefix) throws InterruptedException
    {
        assertTrue(openLatch.await(Timeouts.OPEN_EVENT_MS, TimeUnit.MILLISECONDS), prefix + " onOpen event");
    }

    public void onWsOpen(Session session)
    {
        this.session = session;
        if (LOG.isDebugEnabled())
        {
            LOG.debug("onOpen({})", session);
        }
        this.openLatch.countDown();
    }

    public void onWsOpen(Session session, EndpointConfig config)
    {
        this.session = session;
        this.config = config;
        if (LOG.isDebugEnabled())
        {
            LOG.debug("onOpen({}, {})", session, config);
        }
        this.openLatch.countDown();
    }

    protected void onWsText(String message)
    {
        messageQueue.offer(message);
    }

    protected void onWsBinary(ByteBuffer buffer)
    {
        ByteBuffer copy = DataUtils.copyOf(buffer);
        bufferQueue.offer(copy);
    }

    public void onWsClose(CloseReason closeReason)
    {
        boolean closeTracked = closeDetail.compareAndSet(null, closeReason);
        this.closeLatch.countDown();
        assertTrue(closeTracked, "Close only happened once");
    }

    public void onWsError(Throwable cause)
    {
        assertThat("Error must have value", cause, notNullValue());
        if (error.compareAndSet(null, cause) == false)
        {
            LOG.warn("onError should only happen once - Original Cause", error.get());
            LOG.warn("onError should only happen once - Extra/Excess Cause", cause);
            fail("onError should only happen once!");
        }
    }
}