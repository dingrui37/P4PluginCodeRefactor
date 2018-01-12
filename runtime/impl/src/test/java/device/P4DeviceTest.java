package device;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;
import org.opendaylight.p4plugin.runtime.impl.device.P4Device;
import org.opendaylight.p4plugin.runtime.impl.stub.RuntimeStub;

public class P4DeviceTest {

    @InjectMocks
    private P4Device device =  P4Device.newBuilder()
            .setIp("127.0.0.1")
            .setPort(50051)
            .setNodeId("zte")
            .build();

    @Mock
    private RuntimeStub runtimeStub;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGetConnectState() {
        Mockito.doReturn(false).when(runtimeStub).getConnectState();
        Assert.assertFalse(device.getConnectState());
        Mockito.verify(runtimeStub, Mockito.times(1)).getConnectState();
    }

    @Test
    public void testIsConfigured() {
        Assert.assertFalse(device.isConfigured());
    }

    @Test
    public void test



    @After
    public void after() {

    }

    private static class P4Info {}
}
