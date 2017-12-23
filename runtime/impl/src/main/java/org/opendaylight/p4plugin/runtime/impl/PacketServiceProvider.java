/*
 * Copyright © 2017 zte and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.p4plugin.runtime.impl;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import org.opendaylight.p4plugin.runtime.impl.device.DeviceManager;
import org.opendaylight.p4plugin.runtime.impl.device.P4Device;
import org.opendaylight.yang.gen.v1.urn.opendaylight.p4plugin.runtime.packet.rev170808.P4TransmitPacketInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.p4plugin.runtime.packet.rev170808.P4pluginRuntimePacketService;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

public class PacketServiceProvider implements P4pluginRuntimePacketService {
    private static final Logger LOG = LoggerFactory.getLogger(PacketServiceProvider.class);
    private final DeviceManager manager =  DeviceManager.getInstance();

    private <T> Future<RpcResult<T>> rpcFailed(String errMsg) {
        return RpcResultBuilder.<T>failed()
                .withError(RpcError.ErrorType.APPLICATION, errMsg)
                .buildFuture();
    }

    @Override
    public Future<RpcResult<Void>> p4TransmitPacket(P4TransmitPacketInput input) {
        if (input == null) {
            return rpcFailed("Input is null.");
        }

        String nodeId = input.getNid();
        P4Device device = manager.findConfiguredDevice(nodeId);

        if (device == null) {
            return rpcFailed(String.format("Cannot find node = %s",nodeId));
        }

        device.transmitPacket(input.getPayload());
        return RpcResultBuilder.success((Void)null).buildFuture();
    }
}
