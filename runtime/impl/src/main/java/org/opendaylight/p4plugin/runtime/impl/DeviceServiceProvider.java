/*
 * Copyright © 2017 zte and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.p4plugin.runtime.impl;

import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.TextFormat;
import io.grpc.StatusRuntimeException;
import org.opendaylight.p4plugin.runtime.impl.device.DeviceManager;
import org.opendaylight.p4plugin.runtime.impl.device.P4Device;
import org.opendaylight.yang.gen.v1.urn.opendaylight.p4plugin.runtime.device.rev170808.P4pluginRuntimeDeviceService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.p4plugin.runtime.device.rev170808.*;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.*;

public class DeviceServiceProvider implements P4pluginRuntimeDeviceService {
    private static final Logger LOG = LoggerFactory.getLogger(DeviceServiceProvider.class);
    private final DeviceManager manager =  DeviceManager.getInstance();

    @Override
    public Future<RpcResult<java.lang.Void>> addDevice(AddDeviceInput input) {
        String nodeId = input.getNid();
        String ip = input.getIp().getValue();
        Integer port = input.getPort().getValue();
        Long deviceId = input.getDid().longValue();
        String runtimeFile = input.getRuntimeFilePath();
        String configFile = input.getConfigFilePath();
        SettableFuture<RpcResult<java.lang.Void>> future = SettableFuture.create();

        try {
            manager.addDevice(nodeId, deviceId, ip, port, runtimeFile, configFile);
            future.set(RpcResultBuilder.success((Void)null).build());
            LOG.info("Add device = [{}/{}/{}:{}/{}/{}] success." , nodeId, deviceId, ip, port, runtimeFile, configFile);
        } catch (IllegalArgumentException | IOException e) {
            future.set(RpcResultBuilder.<Void>failed()
                    .withError(RpcError.ErrorType.APPLICATION, e.getMessage()).build());
        }

        return future;
    }

    @Override
    public Future<RpcResult<java.lang.Void>> removeDevice(RemoveDeviceInput input) {
        manager.removeDevice(input.getNid());
        return RpcResultBuilder.success((Void)null).buildFuture();
    }

    @Override
    public Future<RpcResult<ConnectToDeviceOutput>> connectToDevice(ConnectToDeviceInput input) {
        String nodeId = input.getNid();
        Optional<P4Device> optional= manager.findDevice(nodeId);
        SettableFuture<RpcResult<ConnectToDeviceOutput>> future = SettableFuture.create();

        if (optional.isPresent()) {
            new Thread(() -> {
                ConnectToDeviceOutputBuilder outputBuilder = new ConnectToDeviceOutputBuilder();
                P4Device device = optional.get();
                Boolean connectStatus = device.connectToDevice();
                P4Device.State state = connectStatus ? P4Device.State.Connected : P4Device.State.Unknown;
                device.setDeviceState(state);
                LOG.info("Connect to device = [{}], connect status = [{}]." , nodeId, connectStatus);
                future.set(RpcResultBuilder.success(outputBuilder.setConnectStatus(connectStatus)).build());
            }).start();
        } else {
            future.set(RpcResultBuilder.<ConnectToDeviceOutput>failed()
                    .withError(RpcError.ErrorType.APPLICATION, "Cannot find device.").build());
        }

        return future;
    }

    @Override
    public Future<RpcResult<java.lang.Void>> setPipelineConfig(SetPipelineConfigInput input) {
        String nodeId = input.getNid();
        Optional<P4Device> optional = manager.findDevice(nodeId);
        SettableFuture<RpcResult<Void>> future = SettableFuture.create();

        if (optional.isPresent()) {
            try {
                optional.get().setPipelineConfig();
                future.set(RpcResultBuilder.success((Void)null).build());
            } catch (StatusRuntimeException e) {
                String errMsg = String.format("RPC failed, Status = %s, Reason = %s", e.getStatus(), e.getMessage());
                LOG.info("Set pipeline config " + errMsg);
                future.set(RpcResultBuilder.<Void>failed()
                        .withError(RpcError.ErrorType.APPLICATION, errMsg).build());
            }
        } else {
            future.set(RpcResultBuilder.<Void>failed()
                    .withError(RpcError.ErrorType.APPLICATION, "Cannot find device").build());
        }

        return future;
    }

    @Override
    public Future<RpcResult<GetPipelineConfigOutput>> getPipelineConfig(GetPipelineConfigInput input) {
        String nodeId = input.getNid();
        Optional<P4Device> optional = manager.findConfiguredDevice(nodeId);
        SettableFuture<RpcResult<GetPipelineConfigOutput>> future = SettableFuture.create();

        if (optional.isPresent()) {
            try {
                GetPipelineConfigOutputBuilder outputBuilder = new GetPipelineConfigOutputBuilder();
                String result = TextFormat.printToString(optional.get().getPipelineConfig().getConfigs(0).getP4Info());
                outputBuilder.setP4Info(result);
                future.set(RpcResultBuilder.success(outputBuilder.build()).build());
            } catch (StatusRuntimeException e) {
                String errMsg = String.format("RPC failed, Status = %s, Reason = %s", e.getStatus(), e.getMessage());
                LOG.info("Get pipeline config " + errMsg);
                future.set(RpcResultBuilder.<GetPipelineConfigOutput>failed()
                        .withError(RpcError.ErrorType.APPLICATION, errMsg).build());
            }
        } else {
            future.set(RpcResultBuilder.<GetPipelineConfigOutput>failed()
                    .withError(RpcError.ErrorType.APPLICATION, "Cannot find device").build());
        }

        return future;
    }

    @Override
    public Future<RpcResult<QueryDevicesOutput>> queryDevices() {
        QueryDevicesOutputBuilder outputBuilder = new QueryDevicesOutputBuilder();
        outputBuilder.setNodes(manager.queryNodes());
        return RpcResultBuilder.success(outputBuilder.build()).buildFuture();
    }
}
