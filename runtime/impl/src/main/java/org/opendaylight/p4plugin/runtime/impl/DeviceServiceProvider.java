/*
 * Copyright © 2017 zte and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.p4plugin.runtime.impl;

import com.google.protobuf.TextFormat;
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
import java.util.concurrent.Future;

public class DeviceServiceProvider implements P4pluginRuntimeDeviceService {
    private static final Logger LOG = LoggerFactory.getLogger(DeviceServiceProvider.class);
    private final DeviceManager manager =  DeviceManager.getInstance();

    private <T> Future<RpcResult<T>> rpcFailed(String errMsg) {
        return RpcResultBuilder.<T>failed()
                .withError(RpcError.ErrorType.APPLICATION, errMsg)
                .buildFuture();
    }

    @Override
    public Future<RpcResult<java.lang.Void>> addNode(AddNodeInput input) {
        if (input == null) {
            return rpcFailed("Input is null.");
        }

        String nodeId = input.getNid();
        String ip = input.getIp().getValue();
        Integer port = input.getPort().getValue();
        Long deviceId = input.getDid().longValue();
        String runtimeFile = input.getRuntimeFilePath();
        String configFile = input.getConfigFilePath();

        try {
            manager.addDevice(nodeId, deviceId, ip, port, runtimeFile, configFile);
            return RpcResultBuilder.success((Void)null).buildFuture();
        } catch (IllegalArgumentException | IOException e) {
            return rpcFailed(e.getMessage());
        }
    }

    @Override
    public Future<RpcResult<java.lang.Void>> removeNode(RemoveNodeInput input) {
        if (input == null) {
            return rpcFailed("Input is null.");
        }

        manager.removeDevice(input.getNid());
        return RpcResultBuilder.success((Void)null).buildFuture();
    }

    @Override
    public Future<RpcResult<ConnectToNodeOutput>> connectToNode(ConnectToNodeInput input) {
        if (input == null) {
            return rpcFailed("Input is null.");
        }

        String nodeId = input.getNid();
        P4Device device = manager.findDevice(nodeId);
        if (device == null) {
            return rpcFailed(String.format("Cannot find node = %s.", nodeId));
        }

        ConnectToNodeOutputBuilder outputBuilder = new ConnectToNodeOutputBuilder();
        boolean result = manager.findDevice(nodeId).connectToDevice();
        return RpcResultBuilder.success(outputBuilder.setConnectStatus(result)).buildFuture();
    }

    @Override
    public Future<RpcResult<java.lang.Void>> setPipelineConfig(SetPipelineConfigInput input) {
        if (input == null) {
            return rpcFailed("Input is null.");
        }

        String nodeId = input.getNid();
        P4Device device = manager.findDevice(nodeId);

        if (device == null) {
            return rpcFailed(String.format("Cannot find node = %s.", nodeId));
        }

        try {
            manager.findDevice(nodeId).setPipelineConfig();
            return RpcResultBuilder.success((Void) null).buildFuture();
        } catch (Exception e) { //TODO
            return rpcFailed(e.getMessage());
        }
    }

    @Override
    public Future<RpcResult<GetPipelineConfigOutput>> getPipelineConfig(GetPipelineConfigInput input) {
        if (input == null) {
            return rpcFailed("Input is null.");
        }

        String nodeId = input.getNid();
        P4Device device = manager.findConfiguredDevice(nodeId);
        GetPipelineConfigOutputBuilder outputBuilder = new GetPipelineConfigOutputBuilder();

        if (device == null) {
            return rpcFailed(String.format("Cannot find node = %s.", nodeId));
        }

        try {
            String result = TextFormat.printToString(device.getPipelineConfig().getConfigs(0).getP4Info());
            outputBuilder.setP4Info(result);
            return RpcResultBuilder.success(outputBuilder.build()).buildFuture();
        } catch (IllegalStateException e) {
            return rpcFailed(e.getMessage());
        }
    }

    @Override
    public Future<RpcResult<QueryNodesOutput>> queryNodes() {
        QueryNodesOutputBuilder outputBuilder = new QueryNodesOutputBuilder();
        outputBuilder.setNodes(manager.queryNodes());
        return RpcResultBuilder.success(outputBuilder.build()).buildFuture();
    }

    @Override
    public Future<RpcResult<QueryNodeOutput>> queryNode(QueryNodeInput input) {
        if (input == null) {
            return rpcFailed("Input is null.");
        }

        QueryNodeOutputBuilder outputBuilder = new QueryNodeOutputBuilder();
        outputBuilder.setExistStatus(manager.isNodeExist(input.getNid()));
        return RpcResultBuilder.success(outputBuilder.build()).buildFuture();
    }
}
