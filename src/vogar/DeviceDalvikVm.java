/*
 * Copyright (C) 2009 The Android Open Source Project
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

package vogar;

import java.io.File;
import vogar.commands.AndroidSdk;

/**
 * Execute actions on a Dalvik VM using an Android device or emulator.
 */
final class DeviceDalvikVm extends Vm {

    private static final File USER_HOME = new File("/sdcard");

    DeviceDalvikVm(Environment environment, Mode.Options options, Vm.Options vmOptions) {
        super(environment, options, vmOptions);
    }

    private EnvironmentDevice getEnvironmentDevice() {
        return (EnvironmentDevice) environment;
    }

    private AndroidSdk getSdk() {
        return getEnvironmentDevice().androidSdk;
    }

    @Override protected void prepare() {
        super.prepare();
        // push ~/.caliperrc to device if found
        String caliperrc = ".caliperrc";
        File host = Vogar.dotFile(caliperrc);
        if (host.exists()) {
            File target = new File(USER_HOME, caliperrc);
            getSdk().push(host, target);
        }
    }

    @Override protected void installRunner() {
        // dex everything on the classpath and push it to the device.
        for (File classpathElement : classpath.getElements()) {
            dexAndPush(getSdk().basenameOfJar(classpathElement), classpathElement);
        }
    }

    @Override protected void postCompile(Action action, File jar) {
        dexAndPush(action.getName(), jar);
    }

    private void dexAndPush(String name, File jar) {
        Console.getInstance().verbose("dex and push " + name);

        // make the local dex (inside a jar)
        File localDex = environment.file(name, name + ".dx.jar");
        getSdk().dex(localDex, Classpath.of(jar));

        // post the local dex to the device
        getSdk().push(localDex, deviceDexFile(name));
    }

    private File deviceDexFile(String name) {
        return new File(getEnvironmentDevice().runnerDir, name + ".jar");
    }

    @Override protected VmCommandBuilder newVmCommandBuilder(File workingDirectory) {
        return new VmCommandBuilder()
                .vmCommand("adb", "shell", getEnvironmentDevice().getAndroidData(), "dalvikvm")
                .vmArgs("-Duser.home=" + USER_HOME)
                .vmArgs("-Duser.name=" + AndroidSdk.getDeviceUserName())
                .vmArgs("-Duser.language=en")
                .vmArgs("-Duser.region=US")
                .vmArgs("-Djavax.net.ssl.trustStore=/system/etc/security/cacerts.bks")
                .temp(getEnvironmentDevice().vogarTemp);
    }

    @Override protected Classpath getRuntimeClasspath(Action action) {
        Classpath result = new Classpath();
        result.addAll(deviceDexFile(action.getName()));
        for (File classpathElement : classpath.getElements()) {
            result.addAll(deviceDexFile(getSdk().basenameOfJar(classpathElement)));
        }
        return result;
    }
}