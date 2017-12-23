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
import io.grpc.StatusRuntimeException;
import org.opendaylight.p4plugin.p4runtime.proto.Update;
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
import java.util.concurrent.Future;

public class TableServiceProvider implements P4pluginRuntimeTableService {
    private static final Logger LOG = LoggerFactory.getLogger(TableServiceProvider.class);
    private final DeviceManager manager =  DeviceManager.getInstance();

    private <T> Future<RpcResult<T>> rpcFailed(String errMsg) {
        return RpcResultBuilder.<T>failed()
                .withError(RpcError.ErrorType.APPLICATION, errMsg)
                .buildFuture();
    }

    @Override
    public Future<RpcResult<java.lang.Void>> addTableEntry(AddTableEntryInput input) {
        if (input == null) {
            return rpcFailed("Input is null.");
        }

        String nodeId = input.getNid();
        P4Device device = manager.findConfiguredDevice(nodeId);

        if (device == null) {
            return rpcFailed(String.format("Cannot find node = %s.", nodeId));
        }

        try {
            new TableEntryOperator(device).add(input);
            return RpcResultBuilder.success((Void)null).buildFuture();
        } catch (StatusRuntimeException e) {
            return rpcFailed(e.getMessage());
        }
    }

    @Override
    public Future<RpcResult<ModifyTableEntryOutput>> modifyTableEntry(ModifyTableEntryInput input) {
        Preconditions.checkArgument(input != null, "Modify table entry RPC input is null.");
        ModifyTableEntryOutputBuilder builder = new ModifyTableEntryOutputBuilder();
        String nodeId = input.getNodeId();
        try {
            boolean result = new TableEntryOperator(nodeId).modify(input);
            builder.setResult(result);
        } catch (Exception e) {
            builder.setResult(false);
            e.printStackTrace();
        }
        return Futures.immediateFuture(RpcResultBuilder.success(builder.build()).build());
    }

    @Override
    public Future<RpcResult<DeleteTableEntryOutput>> deleteTableEntry(DeleteTableEntryInput input) {
        Preconditions.checkArgument(input != null, "Delete table entry RPC input is null.");
        DeleteTableEntryOutputBuilder builder = new DeleteTableEntryOutputBuilder();
        String nodeId = input.getNodeId();
        try {
            boolean result = new TableEntryOperator(nodeId).delete(input);
            builder.setResult(result);
        } catch (Exception e) {
            builder.setResult(false);
            e.printStackTrace();
        }
        return Futures.immediateFuture(RpcResultBuilder.success(builder.build()).build());
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
