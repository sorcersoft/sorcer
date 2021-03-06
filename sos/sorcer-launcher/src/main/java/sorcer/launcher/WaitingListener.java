/*
 * Copyright 2014 Sorcersoft.com S.A.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sorcer.launcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Rafał Krupiński
 */
public class WaitingListener extends NullSorcerListener {
    private static final Logger log = LoggerFactory.getLogger(WaitingListener.class);

    /**
     * no - before start
     * start - after start, before end
     * end - after end
     */
    private WaitMode state = WaitMode.no;
    private Exception exception;

    @Override
    public void sorcerStarted() {
        state = WaitMode.start;
        synchronized (this) {
            notify();
        }
    }

    @Override
    public void sorcerEnded(Exception e) {
        state = WaitMode.end;
        exception = e;
        synchronized (this) {
            notify();
        }
    }

    @Override
    public void processDown(Process process) {
        if (state == WaitMode.no)
            exception = new IllegalStateException("SORCER down before started " + process);
        state = WaitMode.end;
        synchronized (this) {
            notify();
        }
    }

    public void wait(WaitMode mode) throws Exception {
        if (mode == WaitMode.no)
            return;
        synchronized (this) {
            while (state.isBefore(mode))
                try {
                    wait();
                } catch (InterruptedException e) {
                    log.warn("Interrupted");
                }
        }
        if (exception != null)
            throw exception;
    }
}
