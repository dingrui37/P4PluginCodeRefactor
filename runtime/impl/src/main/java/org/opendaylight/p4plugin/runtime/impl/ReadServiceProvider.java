/*
 * Copyright © 2017 zte and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.p4plugin.runtime.impl;

import org.opendaylight.p4plugin.runtime.impl.device.DeviceManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.p4plugin.runtime.read.rev170808.*;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ReadServiceProvider implements P4pluginRuntimeReadService {
    private static final Logger LOG = LoggerFactory.getLogger(ReadServiceProvider.class);
    private DeviceManager manager;
    private ExecutorService executorService;

    public void init() {
        executorService = Executors.newFixedThreadPool(1);
        manager = DeviceManager.getInstance();
        LOG.info("P4plugin read service provider initiated.");
    }

    public void close() {
        executorService.shutdown();
        LOG.info("P4plugin read service provider closed.");
    }

    private <T> RpcResult<T> rpcResultSuccess(T value) {
        return RpcResultBuilder.success(value).build();
    }

    private Callable<RpcResult<ReadTableEntryOutput>> readEntry(ReadTableEntryInput input) {
        return ()->{
            ReadTableEntryOutputBuilder outputBuilder = new ReadTableEntryOutputBuilder();
            List<String> result = manager.findConfiguredDevice(input.getNid())
                    .orElseThrow(IllegalArgumentException::new)
                    .readTableEntry(input.getTableName());
            outputBuilder.setEntries(result);
            return RpcResultBuilder.success(outputBuilder.build()).build();
        };
    }

    private Callable<RpcResult<ReadActionProfileMemberOutput>> readMember(ReadActionProfileMemberInput input) {
        return ()->{
            ReadActionProfileMemberOutputBuilder outputBuilder = new ReadActionProfileMemberOutputBuilder();
            List<String> result = manager.findConfiguredDevice(input.getNid())
                    .orElseThrow(IllegalArgumentException::new)
                    .readActionProfileMember(input.getActionProfileName());
            outputBuilder.setMembers(result);
            return RpcResultBuilder.success(outputBuilder.build()).build();
        };
    }

    private Callable<RpcResult<ReadActionProfileGroupOutput>> readGroup(ReadActionProfileGroupInput input) {
        return ()->{
            ReadActionProfileGroupOutputBuilder outputBuilder = new ReadActionProfileGroupOutputBuilder();
            List<String> result = manager.findConfiguredDevice(input.getNid())
                    .orElseThrow(IllegalArgumentException::new)
                    .readActionProfileGroup(input.getActionProfileName());
            outputBuilder.setGroups(result);
            return RpcResultBuilder.success(outputBuilder.build()).build();
        };
    }

    @Override
    public Future<RpcResult<ReadTableEntryOutput>> readTableEntry(ReadTableEntryInput input) {
        return executorService.submit(readEntry(input));
    }

    @Override
    public Future<RpcResult<ReadActionProfileMemberOutput>> readActionProfileMember(ReadActionProfileMemberInput input) {
        return executorService.submit(readMember(input));
    }

    @Override
    public Future<RpcResult<ReadActionProfileGroupOutput>> readActionProfileGroup(ReadActionProfileGroupInput input) {
        return executorService.submit(readGroup(input));
    }
}
