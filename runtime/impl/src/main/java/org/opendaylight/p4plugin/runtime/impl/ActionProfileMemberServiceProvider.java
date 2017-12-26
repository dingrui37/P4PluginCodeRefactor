/*
 * Copyright © 2017 zte and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.p4plugin.runtime.impl;

import io.grpc.StatusRuntimeException;
import org.opendaylight.p4plugin.runtime.impl.device.DeviceManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.p4plugin.runtime.member.rev170808.*;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ActionProfileMemberServiceProvider implements P4pluginRuntimeMemberService {
    private static final Logger LOG = LoggerFactory.getLogger(ActionProfileMemberServiceProvider.class);
    private final DeviceManager manager = DeviceManager.getInstance();
    private final ExecutorService executorService = Executors.newFixedThreadPool(1);

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
    public Future<RpcResult<Void>> addActionProfileMember(AddActionProfileMemberInput input) {

    }

    @Override
    public Future<RpcResult<java.lang.Void>> modifyActionProfileMember(ModifyActionProfileMemberInput input) {

    }

    @Override
    public Future<RpcResult<java.lang.Void>> deleteActionProfileMember(DeleteActionProfileMemberInput input) {

    }

    @Override
    public Future<RpcResult<ReadActionProfileOutput>> readActionProfile(ReadActionProfileInput input) {

    }

}
