/*
 * Copyright 2020 Licel Corporation.
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
package com.licel.jcardsim.base;

import static com.licel.jcardsim.base.Simulator.ATR_SYSTEM_PROPERTY;
import static com.licel.jcardsim.base.Simulator.DEFAULT_ATR;
import com.licel.jcardsim.samples.PersistentApplet;
import com.licel.jcardsim.utils.AIDUtil;
import com.licel.jcardsim.utils.ByteUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javacard.framework.AID;
import junit.framework.TestCase;
import org.bouncycastle.util.Arrays;

public class PersistentRuntimeTest extends TestCase {
    
    private final byte GET_DATA_INS = 0x01;
    private final byte GET_COUNTER = 0x02;
    private final byte INC_COUNTER = 0x03;
    private final byte GET_DESELECT_COUNTER = 0x04;
    
    String aidStr;
    AID aid;
    Path baseDir;
    
    public PersistentRuntimeTest(String testName) {
        super(testName);
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        baseDir = Files.createTempDirectory(null);
        System.setProperty("persistentSimulatorRuntime.dir", baseDir.toString());
        System.setProperty(ATR_SYSTEM_PROPERTY, DEFAULT_ATR);
        aidStr = "0A0B0C0D0E0F070809";
        aid = AIDUtil.create(aidStr);
    }
    
    @Override
    protected void tearDown() throws Exception {
        deleteDirectory(baseDir.toFile());
        super.tearDown();
    }

    public void testInstallApplet() {
        System.out.println("testInstallApplet");
        
        SimulatorRuntime runtime = new PersistentSimulatorRuntime();
        Simulator instance = new Simulator(runtime);

        Path appletPath = Paths.get(baseDir.toString(), DEFAULT_ATR, aidStr);
        File appletFile = appletPath.toFile();
        assertEquals(false, appletFile.isFile());
        instance.installApplet(aid, PersistentApplet.class);
        assertEquals(true, appletFile.isFile());
    }
    
    public void testDeleteApplet() {
        System.out.println("testDeleteApplet");

        SimulatorRuntime runtime = new PersistentSimulatorRuntime();
        Simulator instance = new Simulator(runtime);
        instance.installApplet(aid, PersistentApplet.class);
        instance.deleteApplet(aid);

        Path appletPath = Paths.get(baseDir.toString(), DEFAULT_ATR, aidStr);
        File appletFile = appletPath.toFile();
        assertEquals(false, appletFile.isFile());

    }
        
    public void testUpdateApplet() {
        System.out.println("testUpdateApplet");

        SimulatorRuntime runtime = new PersistentSimulatorRuntime();
        Simulator instance = new Simulator(runtime);
        instance.installApplet(aid, PersistentApplet.class);

        byte counter1 = incCounter();
        byte counter2 = incCounter();
        byte counter3 = incCounter();

        assertEquals(true, counter1 == 0);
        assertEquals(true, counter2 == 1);
        assertEquals(true, counter3 == 2);
    }
    
    public void testDeSelectApplet() {
        System.out.println("testDeSelectApplet");
        
        AID otherAid = AIDUtil.create("FFFFFFFFFF0F070809");
        SimulatorRuntime runtime = new PersistentSimulatorRuntime();
        Simulator instance = new Simulator(runtime);
        instance.installApplet(aid, PersistentApplet.class);
        instance.installApplet(otherAid, PersistentApplet.class);
        assertEquals(true, instance.selectApplet(aid));
        byte[] response = instance.transmitCommand(new byte[]{0x01, GET_DESELECT_COUNTER, 0x00, 0x00});
        assertSW_9000(response);
        byte counter1 = response[0];
        
        //Select other applet, so the first will be deselected
        assertEquals(true, instance.selectApplet(otherAid));
        
        SimulatorRuntime otherRuntime = new PersistentSimulatorRuntime();
        Simulator otherInstance = new Simulator(otherRuntime);
        otherInstance.loadApplet(aid, PersistentApplet.class);
        assertEquals(true, otherInstance.selectApplet(aid));
        byte[] otherResponse = otherInstance.transmitCommand(new byte[]{0x01, GET_DESELECT_COUNTER, 0x00, 0x00});
        assertSW_9000(otherResponse);
        byte counter2 = otherResponse[0];
        
        assertEquals(true, counter1 == 0);
        assertEquals(true, counter2 == 1);
    }
    
    public void testNullAppletDir() {
        System.out.println("testNullAppletDir");
       
        System.clearProperty("persistentSimulatorRuntime.dir");
        SimulatorRuntime runtime = new PersistentSimulatorRuntime();
        Simulator instance = new Simulator(runtime);
        instance.installApplet(aid, PersistentApplet.class);
        assertEquals(true, instance.selectApplet(aid));
        byte[] response = instance.transmitCommand(new byte[]{0x01, GET_DATA_INS, 0x00, 0x00});
        assertSW_9000(response);
    }
    
    public void testSerializeApplet() {
        System.out.println("testSerializeApplet");

        SimulatorRuntime runtime = new PersistentSimulatorRuntime();
        Simulator instance = new Simulator(runtime);
        instance.installApplet(aid, PersistentApplet.class);
        assertEquals(true, instance.selectApplet(aid));
        byte[] response = instance.transmitCommand(new byte[]{0x01, GET_DATA_INS, 0x00, 0x00});
        assertSW_9000(response);
        
        SimulatorRuntime otherRuntime = new PersistentSimulatorRuntime();
        Simulator otherInstance = new Simulator(otherRuntime);
        otherInstance.loadApplet(aid, PersistentApplet.class);
        assertEquals(true, otherInstance.selectApplet(aid));
        byte[] otherResponse = otherInstance.transmitCommand(new byte[]{0x01, GET_DATA_INS, 0x00, 0x00});
        assertSW_9000(otherResponse);
        
        assertEquals(true, Arrays.areEqual(response, otherResponse));
    }
    
    private byte incCounter() {
        SimulatorRuntime tmpRuntime = new PersistentSimulatorRuntime();
        Simulator tmpInst = new Simulator(tmpRuntime);
        //load from a file already installed applet
        tmpInst.loadApplet(aid, PersistentApplet.class);
        assertEquals(true, tmpInst.selectApplet(aid));

        byte[] getCounterResponse = tmpInst.transmitCommand(new byte[]{0x01, GET_COUNTER, 0x00, 0x00});
        assertSW_9000(getCounterResponse);
        byte counter = getCounterResponse[0];        

        byte[] incCounterResponse = tmpInst.transmitCommand(new byte[]{0x01, INC_COUNTER, 0x00, 0x00});
        assertSW_9000(incCounterResponse);
        
        return counter;
    }
    
    private void assertSW_9000(byte[] response) {
        ByteUtil.requireSW(response, 0x9000);
    }
    
    static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }
}