/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.utils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class FuturesTest {

    @Test
    void awaitReturnsTheValue() throws Exception {
        assertEquals("done", Futures.await(CompletableFuture.completedFuture("done")));
    }

    @Test
    void awaitRethrowsTheExceptionTheFutureCompletedWith() {
        IOException failure = new IOException("failed");
        assertSame(failure, assertThrows(IOException.class, () -> Futures.await(CompletableFuture.failedFuture(failure))));
    }

    @Test
    void awaitWrapsAnErrorTheFutureCompletedWith() {
        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> Futures.await(CompletableFuture.failedFuture(new AssertionError("error"))));
        assertInstanceOf(AssertionError.class, exception.getCause());
    }

    @Test
    void interruptingTheWaitingThreadCancelsTheFuture() throws InterruptedException {
        CompletableFuture<String> never = new CompletableFuture<>();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread waiting = new Thread(() -> thrown.set(assertThrows(InterruptedException.class, () -> Futures.await(never))));
        waiting.start();
        waiting.interrupt();
        waiting.join(10_000);
        assertInstanceOf(InterruptedException.class, thrown.get());
        assertTrue(never.isCancelled());
    }
}
