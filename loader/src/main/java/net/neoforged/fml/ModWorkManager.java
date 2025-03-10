/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import static net.neoforged.fml.Logging.LOADING;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import net.neoforged.fml.loading.FMLConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ModWorkManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final long PARK_TIME = TimeUnit.MILLISECONDS.toNanos(1);

    public interface DrivenExecutor extends Executor {
        boolean selfDriven();

        boolean driveOne();

        default void drive(Runnable ticker) {
            boolean ranOne = false;
            if (!selfDriven()) {
                ticker.run();
                while (true) {
                    if (!driveOne()) break;
                    ranOne = true;
                }
            }
            if (!ranOne) {
                // park for a bit so other threads can schedule
                LockSupport.parkNanos(PARK_TIME);
            }
        }
    }

    private static class SyncExecutor implements DrivenExecutor {
        private ConcurrentLinkedDeque<Runnable> tasks = new ConcurrentLinkedDeque<>();

        @Override
        public boolean driveOne() {
            final Runnable task = tasks.pollFirst();
            if (task != null) {
                task.run();
            }
            return task != null;
        }

        @Override
        public boolean selfDriven() {
            return false;
        }

        @Override
        public void execute(final Runnable command) {
            tasks.addLast(command);
        }
    }

    private static class WrappingExecutor implements DrivenExecutor {
        private final Executor wrapped;

        public WrappingExecutor(final Executor executor) {
            this.wrapped = executor;
        }

        @Override
        public boolean selfDriven() {
            return true;
        }

        @Override
        public boolean driveOne() {
            return false;
        }

        @Override
        public void execute(final Runnable command) {
            wrapped.execute(command);
        }
    }

    private static SyncExecutor syncExecutor;

    public static DrivenExecutor syncExecutor() {
        if (syncExecutor == null)
            syncExecutor = new SyncExecutor();
        return syncExecutor;
    }

    public static DrivenExecutor wrappedExecutor(Executor executor) {
        return new WrappingExecutor(executor);
    }

    private static ForkJoinPool parallelThreadPool;

    public static Executor parallelExecutor() {
        if (parallelThreadPool == null) {
            final int loadingThreadCount = FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.MAX_THREADS);
            LOGGER.debug(LOADING, "Using {} threads for parallel mod-loading", loadingThreadCount);
            parallelThreadPool = new ForkJoinPool(loadingThreadCount, ModWorkManager::newForkJoinWorkerThread, null, false);
        }
        return parallelThreadPool;
    }

    private static ForkJoinWorkerThread newForkJoinWorkerThread(ForkJoinPool pool) {
        ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
        thread.setName("modloading-worker-" + thread.getPoolIndex());
        // The default sets it to the SystemClassloader, so copy the current one.
        thread.setContextClassLoader(Thread.currentThread().getContextClassLoader());
        return thread;
    }
}
