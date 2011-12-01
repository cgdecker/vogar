/*
 * Copyright (C) 2011 The Android Open Source Project
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

package vogar.tasks;

import java.util.Collection;
import vogar.Console;
import vogar.Result;

/**
 * A task necessary to accomplish the user's requested actions. Tasks have
 * prerequisites; a task must not be run until it reports that it is runnable.
 * Tasks may be run at most once; running a task produces a result.
 */
public abstract class Task {
    private final String name;
    volatile Result result;

    protected Task(String name) {
        this.name = name;
    }

    /**
     * Returns the result of this task; null if this task has not yet completed.
     */
    public Result getResult() {
        return result;
    }

    protected abstract Result execute() throws Exception;

    public abstract boolean isRunnable();

    public final void run(Console console) {
        if (result != null) {
            throw new IllegalStateException();
        }
        try {
            console.verbose("running " + this);
            result = execute();
        } catch (Exception e) {
            e.printStackTrace(); // TODO: capture this properly (for XML etc.)
            result = Result.ERROR;
        }

        if (result != Result.SUCCESS) {
            console.verbose("warning " + this + " " + result);
        } else {
            console.verbose("success " + this);
        }
    }

    @Override public final String toString() {
        return name;
    }

    /**
     * A task that is complete only when {@code tasks} are complete.
     * TODO: don't use tasks as predicates
     */
    public static Task uponSuccessOf(final Collection<Task> tasks) {
        return new Task("completion of " + tasks) {
            @Override protected Result execute() throws Exception {
                for (Task task : tasks) {
                    if (task.getResult() != Result.SUCCESS) {
                        return task.getResult();
                    }
                }
                return Result.SUCCESS;
            }
            @Override public boolean isRunnable() {
                for (Task task : tasks) {
                    if (task.getResult() == null) {
                        return false;
                    }
                }
                return true;
            }
        };
    }
}
