/*
 * Copyright © 2017 zte and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.p4plugin.runtime.impl.channel;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * P4RuntimeChannel contains a gRPC channel and a list which records
 * the P4RuntimeStubs. Multiple stubs can share the same channel.
 */
public class P4RuntimeChannel {
    private static final Logger LOG = LoggerFactory.getLogger(P4RuntimeStub.class);
    private ManagedChannel channel;
    private List<P4RuntimeStub> stubs;

    public P4RuntimeChannel(String ip, Integer port) {
        this(ManagedChannelBuilder.forAddress(ip, port).usePlaintext(true));
    }

    private P4RuntimeChannel(ManagedChannelBuilder<?> channelBuilder) {
        channel = channelBuilder.build();
        stubs = new ArrayList<>();
        LOG.info("XXXXXX" + channel.getState(true).toString());

    }

    public ManagedChannel getManagedChannel() {
        return channel;
    }

    public void addStub(P4RuntimeStub stub) {
        stubs.add(stub);
    }

    public void removeStub(P4RuntimeStub stub) {
        stubs.remove(stub);
        FlyweightFactory.getInstance().gc();
    }

    public Integer getStubsCount() {
        return stubs.size();
    }

    public void shutdown() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
