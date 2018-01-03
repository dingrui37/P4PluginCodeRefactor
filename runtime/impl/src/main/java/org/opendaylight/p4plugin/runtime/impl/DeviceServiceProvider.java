/*
 * Copyright © 2017 zte and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.p4plugin.runtime.impl;

import com.google.protobuf.TextFormat;
import org.opendaylight.p4plugin.p4info.proto.P4Info;
import org.opendaylight.p4plugin.runtime.impl.device.DeviceManager;
import org.opendaylight.p4plugin.runtime.impl.device.P4Device;
import org.opendaylight.yang.gen.v1.urn.opendaylight.p4plugin.device.rev170808.*;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.*;

public class DeviceServiceProvider implements P4pluginDeviceService {
    private static final Logger LOG = LoggerFactory.getLogger(DeviceServiceProvider.class);
    private DeviceManager manager;
    private ExecutorService executorService;

    public void init() {
        executorService = Executors.newFixedThreadPool(1);
        manager = DeviceManager.getInstance();
        LOG.info("P4plugin device service provider initiated.");
    }

    public void close() {
        executorService.shutdown();
        LOG.info("P4plugin device service provider closed.");
    }

    private <T> RpcResult<T> rpcResultSuccess(T value) {
        return RpcResultBuilder.success(value).build();
    }

    private Callable<RpcResult<Void>> addDev(AddDeviceInput input) {
        return ()->{
            String nodeId = input.getNid();
            String ip = input.getIp().getValue();
            Integer port = input.getPort().getValue();
            Long deviceId = input.getDid().longValue();
            String runtimeFile = input.getRuntimeFilePath();
            String configFile = input.getConfigFilePath();
            manager.addDevice(nodeId, deviceId, ip, port, runtimeFile, configFile);
            LOG.info("Add device = [{}-{}-{}:{}-{}-{}] success." , nodeId, deviceId, ip, port, runtimeFile, configFile);
            return rpcResultSuccess(null);
        };
    }

    private Callable<RpcResult<Void>> removeDev(RemoveDeviceInput input) {
        return ()->{
            manager.removeDevice(input.getNid());
            return rpcResultSuccess(null);
        };
    }

    private Callable<RpcResult<ConnectToDeviceOutput>> connectToDev(ConnectToDeviceInput input) {
        return ()->{
            String nodeId = input.getNid();
            Optional<P4Device> optional = manager.findDevice(nodeId);
            Boolean connectStatus = optional.orElseThrow(IllegalArgumentException::new).connectToDevice();
            P4Device.State state = connectStatus ? P4Device.State.Connected : P4Device.State.Unknown;
            optional.get().setDeviceState(state);
            LOG.info("Connect to device = [{}], connect status = [{}]." , nodeId, connectStatus);
            ConnectToDeviceOutputBuilder outputBuilder = new ConnectToDeviceOutputBuilder();
            outputBuilder.setConnectStatus(connectStatus);
            return rpcResultSuccess(outputBuilder.build());
        };
    }

    private Callable<RpcResult<java.lang.Void>> setConfig(SetPipelineConfigInput input) {
        return ()->{
            String nodeId = input.getNid();
            Optional<P4Device> optional = manager.findDevice(nodeId);
            optional.orElseThrow(IllegalArgumentException::new).setPipelineConfig();
            optional.get().setDeviceState(P4Device.State.Configured);
            LOG.info("Set device = [{}] pipeline config success", nodeId);
            return rpcResultSuccess(null);
        };
    }

    private Callable<RpcResult<GetPipelineConfigOutput>> getConfig(GetPipelineConfigInput input) {
        return ()->{
            String nodeId = input.getNid();
            Optional<P4Device> optional = manager.findConfiguredDevice(nodeId);
            P4Info p4info = optional.orElseThrow(IllegalArgumentException::new)
                    .getPipelineConfig()
                    .getConfigs(0)
                    .getP4Info();
            String result = TextFormat.printToString(p4info);
            GetPipelineConfigOutputBuilder outputBuilder = new GetPipelineConfigOutputBuilder();
            outputBuilder.setP4Info(result);
            return rpcResultSuccess(outputBuilder.build());
        };
    }

    private Callable<RpcResult<QueryDevicesOutput>> queryDevs() {
        return ()->{
            QueryDevicesOutputBuilder outputBuilder = new QueryDevicesOutputBuilder();
            outputBuilder.setNode(manager.queryNodes());
            return rpcResultSuccess(outputBuilder.build());
        };
    }

    @Override
    public Future<RpcResult<java.lang.Void>> addDevice(AddDeviceInput input) {
        return executorService.submit(addDev(input));
    }

    @Override
    public Future<RpcResult<java.lang.Void>> removeDevice(RemoveDeviceInput input) {
        return executorService.submit(removeDev(input));
    }

    @Override
    public Future<RpcResult<ConnectToDeviceOutput>> connectToDevice(ConnectToDeviceInput input) {
        return executorService.submit(connectToDev(input));
    }

    @Override
    public Future<RpcResult<java.lang.Void>> setPipelineConfig(SetPipelineConfigInput input) {
        return executorService.submit(setConfig(input));
    }

    @Override
    public Future<RpcResult<GetPipelineConfigOutput>> getPipelineConfig(GetPipelineConfigInput input) {
        return executorService.submit(getConfig(input));
    }

    @Override
    public Future<RpcResult<QueryDevicesOutput>> queryDevices() {
        return executorService.submit(queryDevs());
    }
}
