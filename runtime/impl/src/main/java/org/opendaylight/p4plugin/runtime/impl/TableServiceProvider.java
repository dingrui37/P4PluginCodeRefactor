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
import org.opendaylight.p4plugin.p4runtime.proto.WriteRequest;
import org.opendaylight.p4plugin.runtime.impl.device.DeviceManager;
import org.opendaylight.p4plugin.runtime.impl.device.P4Device;
import org.opendaylight.p4plugin.runtime.impl.table.profile.ActionProfileGroupOperator;
import org.opendaylight.p4plugin.runtime.impl.table.profile.ActionProfileMemberOperator;
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

public class TableServiceProvider implements P4pluginRuntimeTableService {
    private static final Logger LOG = LoggerFactory.getLogger(TableServiceProvider.class);
    private final DeviceManager manager =  DeviceManager.getInstance();

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
            WriteRequest request = new TableEntryOperator(device).add(input);
            try {
                device.write(request);
                future.set(RpcResultBuilder.success((Void)null).build());
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
                future.set(RpcResultBuilder.success((Void)null).build());
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
                future.set(RpcResultBuilder.success((Void)null).build());
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

    @Override
    public Future<RpcResult<AddActionProfileMemberOutput>> addActionProfileMember(AddActionProfileMemberInput input) {
        Preconditions.checkArgument(input != null, "Add action profile member RPC input is null.");
        AddActionProfileMemberOutputBuilder builder = new AddActionProfileMemberOutputBuilder();
        String nodeId = input.getNodeId();
        try {
            boolean result = new ActionProfileMemberOperator(nodeId).add(input);
            builder.setResult(result);
        } catch (Exception e) {
            builder.setResult(false);
            e.printStackTrace();
        }
        return Futures.immediateFuture(RpcResultBuilder.success(builder.build()).build());
    }

    @Override
    public Future<RpcResult<ModifyActionProfileMemberOutput>> modifyActionProfileMember(
            ModifyActionProfileMemberInput input){
        Preconditions.checkArgument(input != null, "Modify action profile member RPC input is null.");
        ModifyActionProfileMemberOutputBuilder builder = new ModifyActionProfileMemberOutputBuilder();
        String nodeId = input.getNodeId();
        try {
            boolean result = new ActionProfileMemberOperator(nodeId).modify(input);
            builder.setResult(result);
        } catch (Exception e) {
            builder.setResult(false);
            e.printStackTrace();
        }
        return Futures.immediateFuture(RpcResultBuilder.success(builder.build()).build());
    }

    @Override
    public Future<RpcResult<DeleteActionProfileMemberOutput>> deleteActionProfileMember(
            DeleteActionProfileMemberInput input) {
        Preconditions.checkArgument(input != null, "Delete action profile member RPC input is null.");
        DeleteActionProfileMemberOutputBuilder builder = new DeleteActionProfileMemberOutputBuilder();
        String nodeId = input.getNodeId();
        try {
            boolean result = new ActionProfileMemberOperator(nodeId).delete(input);
            builder.setResult(result);
        } catch (Exception e) {
            builder.setResult(false);
            e.printStackTrace();
        }
        return Futures.immediateFuture(RpcResultBuilder.success(builder.build()).build());
    }

    @Override
    public Future<RpcResult<ReadActionProfileMemberOutput>> readActionProfileMember(
            ReadActionProfileMemberInput input) {
        Preconditions.checkArgument(input != null, "Read action profile member RPC input is null.");
        ReadActionProfileMemberOutputBuilder builder = new ReadActionProfileMemberOutputBuilder();
        String nodeId = input.getNodeId();
        String actionProfile = input.getActionProfile();
        try {
            List<String> result = new ActionProfileMemberOperator(nodeId).read(actionProfile);
            builder.setContent(result);
            builder.setResult(true);
        } catch (Exception e) {
            builder.setResult(false);
            e.printStackTrace();
        }
        return Futures.immediateFuture(RpcResultBuilder.success(builder.build()).build());
    }

    @Override
    public Future<RpcResult<AddActionProfileGroupOutput>> addActionProfileGroup(AddActionProfileGroupInput input) {
        Preconditions.checkArgument(input != null, "Add action profile group RPC input is null.");
        AddActionProfileGroupOutputBuilder builder = new AddActionProfileGroupOutputBuilder();
        String nodeId = input.getNodeId();
        try {
            boolean result = new ActionProfileGroupOperator(nodeId).add(input);
            builder.setResult(result);
        } catch (Exception e) {
            builder.setResult(false);
            e.printStackTrace();
        }
        return Futures.immediateFuture(RpcResultBuilder.success(builder.build()).build());
    }

    @Override
    public Future<RpcResult<ModifyActionProfileGroupOutput>> modifyActionProfileGroup(
            ModifyActionProfileGroupInput input) {
        Preconditions.checkArgument(input != null, "Add action profile group RPC input is null.");
        ModifyActionProfileGroupOutputBuilder builder = new ModifyActionProfileGroupOutputBuilder();
        String nodeId = input.getNodeId();
        try {
            boolean result = new ActionProfileGroupOperator(nodeId).modify(input);
            builder.setResult(result);
        } catch (Exception e) {
            builder.setResult(false);
            e.printStackTrace();
        }
        return Futures.immediateFuture(RpcResultBuilder.success(builder.build()).build());
    }

    @Override
    public Future<RpcResult<DeleteActionProfileGroupOutput>> deleteActionProfileGroup(
            DeleteActionProfileGroupInput input) {
        Preconditions.checkArgument(input != null, "Add action profile group RPC input is null.");
        DeleteActionProfileGroupOutputBuilder builder = new DeleteActionProfileGroupOutputBuilder();
        String nodeId = input.getNodeId();
        try {
            boolean result = new ActionProfileGroupOperator(nodeId).delete(input);
            builder.setResult(result);
        } catch (Exception e) {
            builder.setResult(false);
            e.printStackTrace();
        }
        return Futures.immediateFuture(RpcResultBuilder.success(builder.build()).build());
    }

    @Override
    public Future<RpcResult<ReadActionProfileGroupOutput>> readActionProfileGroup(ReadActionProfileGroupInput input) {
        Preconditions.checkArgument(input != null, "Read action profile group RPC input is null.");
        ReadActionProfileGroupOutputBuilder builder = new ReadActionProfileGroupOutputBuilder();
        String nodeId = input.getNodeId();
        String actionProfile = input.getActionProfile();
        try {
            List<String> result = new ActionProfileGroupOperator(nodeId).read(actionProfile);
            builder.setContent(result);
            builder.setResult(true);
        } catch (Exception e) {
            builder.setResult(false);
            e.printStackTrace();
        }
        return Futures.immediateFuture(RpcResultBuilder.success(builder.build()).build());
    }
}
