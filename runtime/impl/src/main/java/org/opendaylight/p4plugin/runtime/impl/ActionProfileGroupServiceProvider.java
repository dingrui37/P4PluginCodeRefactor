/*
 * Copyright © 2017 zte and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.p4plugin.runtime.impl;

import io.grpc.StatusRuntimeException;
import jdk.nashorn.internal.codegen.CompilerConstants;
import org.opendaylight.p4plugin.runtime.impl.device.DeviceManager;
import org.opendaylight.p4plugin.runtime.impl.device.P4Device;
import org.opendaylight.yang.gen.v1.urn.opendaylight.p4plugin.runtime.group.rev170808.*;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ActionProfileGroupServiceProvider implements P4pluginRuntimeGroupService {
    private static final Logger LOG = LoggerFactory.getLogger(ActionProfileGroupServiceProvider.class);

    private DeviceManager manager;
    private ExecutorService executorService;

    public void init() {
        executorService = Executors.newFixedThreadPool(1);
        manager = DeviceManager.getInstance();
        LOG.info("P4plugin action profile group service provider initiated.");
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

    private Callable<RpcResult<Void>> addGroup(AddActionProfileGroupInput input) {
        return ()->{
            Optional<P4Device> optional = manager.findConfiguredDevice(input.getNid());
            optional.ifPresent(()->{
                try {
                    optional.get().addActionProfileGroup(input);
                    return rpcResultSuccess(null);
                } catch (StatusRuntimeException e) {
                    return rpcResultFailed(getErrMsg(e));
                }
            });
            return rpcResultFailed("Cannot find device");
        };
    }

    private Callable<RpcResult<Void>> modifyGroup(ModifyActionProfileGroupInput input) {
        return ()->{
            Optional<P4Device> optional = manager.findConfiguredDevice(input.getNid());
            optional.ifPresent(()->{
                try {
                    optional.get().modifyActionProfileGroup(input);
                    return rpcResultSuccess(null);
                } catch (StatusRuntimeException e) {
                    return rpcResultFailed(getErrMsg(e));
                }
            });
            return rpcResultFailed("Cannot find device");
        };
    }

    private Callable<RpcResult<Void>> deleteGroup(DeleteActionProfileGroupInput input) {
        return ()->{
            Optional<P4Device> optional = manager.findConfiguredDevice(input.getNid());
            optional.ifPresent(()->{
                try {
                    optional.get().deleteActionProfileGroup((input);
                    return rpcResultSuccess(null);
                } catch (StatusRuntimeException e) {
                    return rpcResultFailed(getErrMsg(e));
                }
            });
            return rpcResultFailed("Cannot find device");
        };
    }

    private Callable<RpcResult<ReadActionProfileGroupOutput>> readGroup(ReadActionProfileGroupInput input) {
        return ()->{
            Optional<P4Device> optional = manager.findConfiguredDevice(input.getNid());
            optional.ifPresent(()->{
                try {
                    List<String> result = optional.get().readActionProfileGroup(input);
                    ReadActionProfileGroupOutputBuilder outputBuilder = new ReadActionProfileGroupOutputBuilder();
                    outputBuilder.setGroups(result);
                    return rpcResultSuccess(outputBuilder.build());
                } catch (StatusRuntimeException e) {
                    return rpcResultFailed(getErrMsg(e));
                }
            });
            return rpcResultFailed("Cannot find device");
        };
    }

    @Override
    public Future<RpcResult<java.lang.Void>> addActionProfileGroup(AddActionProfileGroupInput input) {
        return executorService.submit(addGroup(input));
    }

    @Override
    public Future<RpcResult<Void>> modifyActionProfileGroup(ModifyActionProfileGroupInput input) {
        return executorService.submit(modifyGroup(input));
    }

    @Override
    public Future<RpcResult<java.lang.Void>> deleteActionProfileGroup(DeleteActionProfileGroupInput input) {
        return executorService.submit(deleteGroup(input));
    }

    @Override
    public Future<RpcResult<ReadActionProfileGroupOutput>> readActionProfileGroup(ReadActionProfileGroupInput input) {
        return executorService.submit(readGroup(input));
    }
}
