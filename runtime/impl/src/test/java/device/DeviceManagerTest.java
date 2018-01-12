/*
 * Copyright © 2017 zte and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package device;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;
import org.opendaylight.p4plugin.runtime.impl.device.DeviceManager;
import org.opendaylight.p4plugin.runtime.impl.device.P4Device;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class DeviceManagerTest {
    @InjectMocks
    DeviceManager manager = DeviceManager.getInstance();

    @Spy
    private ConcurrentHashMap<String, P4Device> devices;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testIsNodeExist() {
        Assert.assertFalse(manager.isNodeExist("zte"));
        P4Device device = Mockito.mock(P4Device.class);
        devices.put("zte", device);
        Assert.assertTrue(manager.isNodeExist("zte"));
    }

    @Test
    public void testIsTargetExist() {
        Assert.assertFalse(manager.isTargetExist("127.0.0.1", 50051, (long)0));
        P4Device device = Mockito.mock(P4Device.class);
        devices.put("zte", device);
        Mockito.doReturn((long)0).when(device).getDeviceId();
        Mockito.doReturn(50051).when(device).getPort();
        Mockito.doReturn("127.0.0.1").when(device).getIp();
        Assert.assertTrue(manager.isTargetExist("127.0.0.1", 50051, (long)0));
        Mockito.verify(device, Mockito.times(1)).getIp();
        Mockito.verify(device, Mockito.times(1)).getPort();
        Mockito.verify(device, Mockito.times(1)).getDeviceId();
    }

    @Test
    public void testIsDeviceExist() {
        Assert.assertFalse(manager.isDeviceExist("zte", "127.0.0.1", 50051, (long)0));
        P4Device device = Mockito.mock(P4Device.class);
        devices.put("zte", device);
        Mockito.doReturn((long)0).when(device).getDeviceId();
        Mockito.doReturn(50051).when(device).getPort();
        Mockito.doReturn("127.0.0.1").when(device).getIp();
        Assert.assertFalse(manager.isDeviceExist("hw", "127.0.0.1", 50051, (long)0));
        Assert.assertTrue(manager.isDeviceExist("zte", "127.0.0.1", 50051, (long)0));
        Mockito.verify(device, Mockito.times(1)).getIp();
        Mockito.verify(device, Mockito.times(1)).getPort();
        Mockito.verify(device, Mockito.times(1)).getDeviceId();
    }

    @Test
    public void testFindDevice() {
        Assert.assertFalse(manager.findDevice("zte").isPresent());
        P4Device device = Mockito.mock(P4Device.class);
        devices.put("zte", device);
        Mockito.doReturn((long)0).when(device).getDeviceId();
        Mockito.doReturn(50051).when(device).getPort();
        Mockito.doReturn("127.0.0.1").when(device).getIp();
        Assert.assertTrue(manager.findDevice("zte").isPresent());
        Mockito.verify(device, Mockito.times(1)).getIp();
        Mockito.verify(device, Mockito.times(1)).getPort();
        Mockito.verify(device, Mockito.times(1)).getDeviceId();
    }

    @Test(expected = IOException.class)
    public void testAddDevice1() throws IOException {
        manager.addDevice("zte", (long)0, "127.0.0.1", 50051, "config-file-path", "runtime-file-path");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddDevice2() throws IOException {
        P4Device device = Mockito.mock(P4Device.class);
        devices.put("zte", device);
        Mockito.doReturn((long)0).when(device).getDeviceId();
        Mockito.doReturn(50051).when(device).getPort();
        Mockito.doReturn("127.0.0.1").when(device).getIp();
        manager.addDevice("zte", (long)0, "127.0.0.1", 50051, "config-file-path", "runtime-file-path");
        Mockito.verify(device, Mockito.times(1)).getIp();
        Mockito.verify(device, Mockito.times(1)).getPort();
        Mockito.verify(device, Mockito.times(1)).getDeviceId();
    }

    @Test
    public void testRemoveDevice() {
        P4Device device = Mockito.mock(P4Device.class);
        devices.put("zte", device);
        Mockito.doReturn("zte").when(device).getNodeId();
        Assert.assertTrue(manager.findDevice("zte").isPresent());
        manager.removeDevice("zte");
        Assert.assertFalse(manager.findDevice("zte").isPresent());
        Mockito.verify(device, Mockito.times(1)).getNodeId();
    }


    @Test
    public void testFindConfiguredDevice() {
        P4Device device1 = Mockito.mock(P4Device.class);
        P4Device device2 = Mockito.mock(P4Device.class);
        devices.put("zte", device1);
        devices.put("hw", device2);
        Mockito.doReturn(true).when(device1).isConfigured();
        Mockito.doReturn(false).when(device2).isConfigured();
        Assert.assertTrue(manager.findConfiguredDevice("zte").isPresent());
        Assert.assertFalse(manager.findConfiguredDevice("hw").isPresent());
        Mockito.verify(device1, Mockito.times(1)).isConfigured();
        Mockito.verify(device2, Mockito.times(1)).isConfigured();
    }


    @Test
    public void testQueryDevices() {
        P4Device device1 = Mockito.mock(P4Device.class);
        P4Device device2 = Mockito.mock(P4Device.class);
        devices.put("zte", device1);
        devices.put("hw", device2);
        Assert.assertEquals(2, manager.queryDevices().size());
    }

    @After
    public void after() {
        devices.clear();
    }
}
