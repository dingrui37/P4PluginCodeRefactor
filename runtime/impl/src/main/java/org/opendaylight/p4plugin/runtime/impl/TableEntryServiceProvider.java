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
import com.google.common.util.concurrent.SettableFuture;
import io.grpc.StatusRuntimeException;
import org.opendaylight.p4plugin.p4runtime.proto.Update;
import org.opendaylight.p4plugin.p4runtime.proto.WriteRequest;
import org.opendaylight.p4plugin.runtime.impl.device.DeviceManager;
import org.opendaylight.p4plugin.runtime.impl.device.P4Device;
import org.opendaylight.p4plugin.runtime.impl.table.entry.TableEntryGenerator;
import org.opendaylight.p4plugin.runtime.impl.table.entry.TableEntryOperator;
import org.opendaylight.yang.gen.v1.urn.opendaylight.p4plugin.runtime.table.rev170808.*;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;

public class TableEntryServiceProvider implements P4pluginRuntimeTableService {
    private static final Logger LOG = LoggerFactory.getLogger(TableServiceProvider.class);
    private final DeviceManager manager = DeviceManager.getInstance();

    @Override
    public Future<RpcResult<java.lang.Void>> addTableEntry(AddTableEntryInput input) {
        if (input == null) {
            return RpcResultBuilder.<Void>failed()
                    .withError(RpcError.ErrorType.APPLICATION, "Input is null").buildFuture();
        }

        String nodeId = input.getNid();
        Optional<P4Device> optional = manager.findConfiguredDevice(nodeId);
        SettableFuture<RpcResult<Void>> future = SettableFuture.create();

        if (optional.isPresent()) {
            P4Device device = optional.get();
            WriteRequest request = new TableEntryGenerator(device).generate(input, Update.Type.INSERT);
            try {
                device.write(request);
                future.set(RpcResultBuilder.success((Void) null).build());
            } catch (StatusRuntimeException e) {
                String errMsg = String.format("RPC failed, Status = %s, Reason = %s", e.getStatus(), e.getMessage());
                LOG.info("Add table entry " + errMsg);
                future.set(RpcResultBuilder.<Void>failed()
                        .withError(RpcError.ErrorType.APPLICATION, errMsg).build());
            }
        } else {
            future.set(RpcResultBuilder.<Void>failed()
                    .withError(RpcError.ErrorType.APPLICATION, "Cannot find device.").build());
        }

        return future;
    }

    @Override
    public Future<RpcResult<java.lang.Void>> modifyTableEntry(ModifyTableEntryInput input) {
        if (input == null) {
            return RpcResultBuilder.<Void>failed()
                    .withError(RpcError.ErrorType.APPLICATION, "Input is null").buildFuture();
        }

        String nodeId = input.getNid();
        Optional<P4Device> optional = manager.findConfiguredDevice(nodeId);
        SettableFuture<RpcResult<Void>> future = SettableFuture.create();
        if (optional.isPresent()) {
            P4Device device = optional.get();
            WriteRequest request = new TableEntryOperator(device).modify(input);
            try {
                device.write(request);
                future.set(RpcResultBuilder.success((Void) null).build());
            } catch (StatusRuntimeException e) {
                String errMsg = String.format("RPC failed, Status = %s, Reason = %s", e.getStatus(), e.getMessage());
                LOG.info("Modify table entry " + errMsg);
                future.set(RpcResultBuilder.<Void>failed()
                        .withError(RpcError.ErrorType.APPLICATION, errMsg).build());
            }
        } else {
            future.set(RpcResultBuilder.<Void>failed()
                    .withError(RpcError.ErrorType.APPLICATION, "Cannot find device.").build());
        }

        return future;
    }

    @Override
    public Future<RpcResult<java.lang.Void>> deleteTableEntry(DeleteTableEntryInput input) {
        if (input == null) {
            return RpcResultBuilder.<Void>failed()
                    .withError(RpcError.ErrorType.APPLICATION, "Input is null").buildFuture();
        }

        String nodeId = input.getNid();
        Optional<P4Device> optional = manager.findConfiguredDevice(nodeId);
        SettableFuture<RpcResult<Void>> future = SettableFuture.create();

        if (optional.isPresent()) {
            P4Device device = optional.get();
            WriteRequest request = new TableEntryOperator(device).delete(input);
            try {
                device.write(request);
                future.set(RpcResultBuilder.success((Void) null).build());
            } catch (StatusRuntimeException e) {
                String errMsg = String.format("RPC failed, Status = %s, Reason = %s", e.getStatus(), e.getMessage());
                LOG.info("Delete table entry " + errMsg);
                future.set(RpcResultBuilder.<Void>failed()
                        .withError(RpcError.ErrorType.APPLICATION, errMsg).build());
            }
        } else {
            future.set(RpcResultBuilder.<Void>failed()
                    .withError(RpcError.ErrorType.APPLICATION, "Cannot find device.").build());
        }

        return future;
    }

    @Override
    public Future<RpcResult<ReadTableEntryOutput>> readTableEntry(ReadTableEntryInput input) {
        Preconditions.checkArgument(input != null, "Read table entry RPC input is null.");
        ReadTableEntryOutputBuilder builder = new ReadTableEntryOutputBuilder();
        String nodeId = input.getNodeId();
        String tableName = input.getTable();
        try {
            List<String> result = new TableEntryOperator(nodeId).read(tableName);
            builder.setContent(result);
            builder.setResult(true);
        } catch (Exception e) {
            builder.setResult(false);
            e.printStackTrace();
        }
        return Futures.immediateFuture(RpcResultBuilder.success(builder.build()).build());
    }
}