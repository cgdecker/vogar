/*
 * Copyright (C) 2010 The Android Open Source Project
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

package vogar.android;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.util.Collection;
import javax.inject.Inject;
import javax.inject.Named;
import vogar.Action;
import vogar.Environment;
import vogar.Log;
import vogar.Result;
import vogar.RetrievedFilesFilter;
import vogar.Vogar;
import vogar.commands.Mkdir;
import vogar.tasks.Task;
import vogar.tasks.TaskQueue;

public final class EnvironmentDevice extends Environment {
    @Inject Log log;
    @Inject Mkdir mkdir;
    @Inject AndroidSdk androidSdk;
    @Inject RetrievedFilesFilter retrievedFiles;
    @Inject @Named("runnerDir") File runnerDir;
    @Inject @Named("firstMonitorPort") int firstMonitorPort;
    @Inject @Named("numRunners") int numRunners;
    @Inject @Named("deviceUserHome") File deviceUserHome;

    /** Prepares the device (but doesn't install vogar */
    public final Task prepareDeviceTask = new Task("prepare device") {
        @Override protected Result execute() throws Exception {
            androidSdk.waitForDevice();
            // Even if runner dir is /vogar/run, the grandparent will be / (and non-null)
            androidSdk.waitForNonEmptyDirectory(runnerDir.getParentFile().getParentFile(), 5 * 60);
            androidSdk.remount();
            if (cleanBefore()) {
                androidSdk.rm(runnerDir);
            }
            androidSdk.mkdirs(runnerDir);
            androidSdk.mkdir(vogarTemp());
            androidSdk.mkdir(dalvikCache());
            for (int i = 0; i < numRunners; i++) {
                androidSdk.forwardTcp(firstMonitorPort + i, firstMonitorPort + i);
            }
            if (getDebugPort() != null) {
                androidSdk.forwardTcp(getDebugPort(), getDebugPort());
            }
            androidSdk.mkdirs(deviceUserHome);

            // push ~/.caliperrc to device if found
            File hostCaliperRc = Vogar.dotFile(".caliperrc");
            if (hostCaliperRc.exists()) {
                androidSdk.push(hostCaliperRc, new File(deviceUserHome, ".caliperrc"));
            }
            return Result.SUCCESS;
        }

        @Override public boolean isRunnable() {
            return true;
        }
    };

    File vogarTemp() {
        return new File(runnerDir, "tmp");
    }

    private File dalvikCache() {
        return new File(runnerDir.getParentFile(), "dalvik-cache");
    }

    public AndroidSdk getAndroidSdk() {
        return androidSdk;
    }

    public File getRunnerDir() {
        return runnerDir;
    }

    /**
     * Returns an environment variable assignment to configure where the VM will
     * store its dexopt files. This must be set on production devices and is
     * optional for development devices.
     */
    public String getAndroidData() {
        // The VM wants the parent directory of a directory named "dalvik-cache"
        return "ANDROID_DATA=" + dalvikCache().getParentFile();
    }

    @Override public void installTasks(TaskQueue taskQueue) {
        taskQueue.enqueue(prepareDeviceTask);
    }

    public File actionClassesDirOnDevice(Action action) {
        return new File(runnerDir, action.getName());
    }

    /**
     * Scans {@code dir} for files to grab.
     */
    private void retrieveFiles(File destination, File source, FileFilter filenameFilter)
            throws FileNotFoundException {
        for (File file : androidSdk.ls(source)) {
            if (filenameFilter.accept(file)) {
                log.info("Moving " + file + " to " + destination);
                mkdir.mkdirs(destination);
                androidSdk.pull(file, destination);
            }
        }

        // special case check if this directory exists so that we retrieve results on Caliper
        // failure.
        // TODO figure out which files are directories, and recurse.
        Collection<File> dirs = Collections2.filter(androidSdk.ls(source), new Predicate<File>() {
            public boolean apply(File file) {
                return file.getName().equals("caliper-results");
            }
        });
        for (File subDir : dirs) {
            retrieveFiles(new File(destination, subDir.getName()), subDir, filenameFilter);
        }
    }

    @Override public void cleanup(Action action) {
        try {
            retrieveFiles(new File("./vogar-results"),
                    actionClassesDirOnDevice(action), retrievedFiles);
        } catch (FileNotFoundException e) {
            log.info("Failed to retrieve all files: ", e);
        }
        super.cleanup(action);
        if (cleanAfter()) {
            androidSdk.rm(actionClassesDirOnDevice(action));
        }
    }

    @Override public void shutdown() {
        super.shutdown();
        if (cleanAfter()) {
            androidSdk.rm(runnerDir);
        }
    }
}
