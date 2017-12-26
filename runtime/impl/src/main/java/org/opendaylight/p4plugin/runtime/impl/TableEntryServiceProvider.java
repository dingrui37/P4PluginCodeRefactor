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
import org.opendaylight.p4plugin.runtime.impl.channel.P4RuntimeChannel;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TableEntryServiceProvider implements P4pluginRuntimeTableService {
    private static final Logger LOG = LoggerFactory.getLogger(TableEntryServiceProvider.class);
    private DeviceManager manager;
    private ExecutorService executorService;

    public void init() {
        executorService = Executors.newFixedThreadPool(2);
        manager = DeviceManager.getInstance();
        LOG.info("P4plugin table service provider initiated.");
    }

    public void close() {
        executorService.shutdown();
        LOG.info("P4plugin table service provider closed.");
    }

    private String getErrMsg(StatusRuntimeException e) {
        return String.format("RPC exception, Status = %s, Reason = %s", e.getStatus(), e.getMessage());
    }

    private <T> RpcResult<T> rpcResultFailed(String errMsg) {
        return RpcResultBuilder.<T>failed()
                .withError(RpcError.ErrorType.APPLICATION, errMsg).build();
    }

    private <T> RpcResult<T> rpcResultSuccess(T value) {
        return RpcResultBuilder.success(value).build();
    }

    @Override
    public Future<RpcResult<java.lang.Void>> addTableEntry(AddTableEntryInput input) {
        return executorService.submit(()-> {
            String nodeId = input.getNid();
            Optional<P4Device> optional = manager.findConfiguredDevice(nodeId);
            if (optional.isPresent()) {
                try {
                    optional.get().addTableEntry(input);
                    return rpcResultSuccess((Void)null);
                } catch (StatusRuntimeException e) {
                    return rpcResultFailed(getErrMsg(e));
                }
            } else {
                return rpcResultFailed("Cannot find device.");
            }
        });
    }

    @Override
    public Future<RpcResult<java.lang.Void>> modifyTableEntry(ModifyTableEntryInput input) {
        return executorService.submit(()-> {
            String nodeId = input.getNid();
            Optional<P4Device> optional = manager.findConfiguredDevice(nodeId);
            if (optional.isPresent()) {
                try {
                    optional.get().modifyTableEntry(input);
                    return rpcResultSuccess((Void)null);
                } catch (StatusRuntimeException e) {
                    return rpcResultFailed(getErrMsg(e));
                }
            } else {
                return rpcResultFailed("Cannot find device.");
            }
        });
    }

    @Override
    public Future<RpcResult<java.lang.Void>> deleteTableEntry(DeleteTableEntryInput input) {
        return executorService.submit(()-> {
            String nodeId = input.getNid();
            Optional<P4Device> optional = manager.findConfiguredDevice(nodeId);
            if (optional.isPresent()) {
                try {
                    optional.get().deleteTableEntry(input);
                    return rpcResultSuccess(null);
                } catch (StatusRuntimeException e) {
                    return rpcResultFailed(getErrMsg(e));
                }
            } else {
                return rpcResultFailed("Cannot find device.");
            }
        });
    }

    @Override
    public Future<RpcResult<ReadTableEntryOutput>> readTableEntry(ReadTableEntryInput input) {
        return executorService.submit(()-> {
            String nodeId = input.getNid();
            String tableName = input.getTableName();
            Optional<P4Device> optional = manager.findConfiguredDevice(nodeId);
            if (optional.isPresent()) {
                try {
                    ReadTableEntryOutputBuilder builder = new ReadTableEntryOutputBuilder();
                    List<String> result = optional.get().readTableEntry(tableName);
                    builder.setEntries(result);
                    return rpcResultSuccess(builder.build());
                } catch (StatusRuntimeException e) {
                    return rpcResultFailed(getErrMsg(e));
                }
            } else {
                return rpcResultFailed("Cannot find device.");
            }
        });
    }
}