/*
 * Copyright © 2017 zte and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.p4plugin.runtime.impl.channel;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import org.opendaylight.p4plugin.runtime.impl.utils.NotificationPublisher;
import org.opendaylight.p4plugin.runtime.impl.cluster.ElectionId;
import org.opendaylight.p4plugin.runtime.impl.cluster.ElectionIdGenerator;
import org.opendaylight.p4plugin.runtime.impl.cluster.ElectionIdObserver;
import org.opendaylight.p4plugin.runtime.impl.device.DeviceManager;
import org.opendaylight.p4plugin.runtime.impl.utils.Utils;
import org.opendaylight.p4plugin.p4runtime.proto.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.p4plugin.runtime.packet.rev170808.P4PacketReceivedBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * P4RuntimeStub contains a blocking and an async stubs, electionId and etc. In addition,
 * it encapsulates various methods for the upper layers.
 */
public class P4RuntimeStub implements ElectionIdObserver {
    private static final Logger LOG = LoggerFactory.getLogger(P4RuntimeStub.class);
    private P4RuntimeChannel runtimeChannel;
    private P4RuntimeGrpc.P4RuntimeBlockingStub blockingStub;
    private P4RuntimeGrpc.P4RuntimeStub asyncStub;
    private StreamChannel streamChannel;
    private ElectionId electionId;

    public P4RuntimeStub(String nodeId, Long deviceId, String ip, Integer port) {
        init(nodeId, deviceId, ip, port);
    }

    private void init(String nodeId, Long deviceId, String ip, Integer port) {
        runtimeChannel = FlyweightFactory.getInstance().getChannel(ip, port);
        blockingStub = P4RuntimeGrpc.newBlockingStub(runtimeChannel.getManagedChannel());
        asyncStub = P4RuntimeGrpc.newStub(runtimeChannel.getManagedChannel());
        streamChannel = new StreamChannel(nodeId, deviceId);
        electionId = ElectionIdGenerator.getInstance().getElectionId();
        ElectionIdGenerator.getInstance().addObserver(this);
    }

    @Override
    public void update(ElectionId electionId) {
        this.electionId = electionId;
        streamChannel.sendMasterArbitration(electionId);
    }

    public ElectionId getElectionId() {
        return electionId;
    }

    public P4RuntimeGrpc.P4RuntimeBlockingStub getBlockingStub() {
        return blockingStub;
    }

    public P4RuntimeGrpc.P4RuntimeStub getAsyncStub() {
        return asyncStub;
    }

    public boolean connect() {
        streamChannel.openStreamChannel();
        return streamChannel.getStreamChannelState();
    }

    public void transmitPacket(byte[] payload) {
        streamChannel.transmitPacket(payload);
    }

    public void shutdown() {
        ElectionIdGenerator.getInstance().deleteObserver(this);
        streamChannel.shutdown();
    }

    /**
     * There is a single StreamChannel bi-directional stream per (P4RuntimeStub, Switch)
     * pair. The first thing a controller needs to do when it opens the stream is send a
     * MasterArbitrationUpdate message, advertising its election id. This message includes
     * a device id. All subsequent arbitration & packet IO messages on that stream will be
     * for that device.
     */
    public class StreamChannel {
        private Long deviceId;
        private String nodeId;
        private StreamObserver<StreamMessageRequest> observer;
        private CountDownLatch countDownLatch;

        public StreamChannel(String nodeId, Long deviceId) {
            this.deviceId = deviceId;
            this.nodeId = nodeId;
        }

        public boolean getStreamChannelState() {
            boolean state;

            try {
                state = !countDownLatch.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }

            return state;
        }

        public void sendMasterArbitration(ElectionId electionId) {
            StreamMessageRequest.Builder requestBuilder = StreamMessageRequest.newBuilder();
            MasterArbitrationUpdate.Builder masterArbitrationBuilder = MasterArbitrationUpdate.newBuilder();
            Uint128.Builder electionIdBuilder = Uint128.newBuilder();
            electionIdBuilder.setHigh(electionId.getHigh());
            electionIdBuilder.setLow(electionId.getLow());
            masterArbitrationBuilder.setDeviceId(deviceId);
            masterArbitrationBuilder.setElectionId(electionIdBuilder);
            requestBuilder.setArbitration(masterArbitrationBuilder);
            observer.onNext(requestBuilder.build());
        }

        /**
         * Not support metadata in p4_v14, we will support it in the near future.
         */
        public void transmitPacket(byte[] payload) {
            StreamMessageRequest.Builder requestBuilder = StreamMessageRequest.newBuilder();
            PacketOut.Builder packetOutBuilder = PacketOut.newBuilder();
            packetOutBuilder.setPayload(ByteString.copyFrom(payload));
            //metadataList.forEach(packetOutBuilder::addMetadata);
            requestBuilder.setPacket(packetOutBuilder);
            observer.onNext(requestBuilder.build());
            //For debug
            LOG.info("Transmit packet = {} to device = {}.", Utils.bytesToHexString(payload), nodeId);
        }

        private void onPacketReceived(StreamMessageResponse response) {
            switch(response.getUpdateCase()) {
                case PACKET: {
                    P4PacketReceivedBuilder builder = new P4PacketReceivedBuilder();
                    byte[] payload = response.getPacket().getPayload().toByteArray();
                    builder.setNid(nodeId);
                    builder.setPayload(payload);
                    NotificationPublisher.getInstance().notify(builder.build());
                    //For debug
                    LOG.info("Receive packet from device = {}, body = {}.", nodeId, Utils.bytesToHexString(payload));
                    break;
                }
                case ARBITRATION:break;
                case UPDATE_NOT_SET:break;
                default:break;
            }
        }

        private void onStreamChannelError(Throwable t) {
            runtimeChannel.removeStub(P4RuntimeStub.this);
            DeviceManager.getInstance().removeDevice(nodeId);
            countDownLatch.countDown();
            LOG.info("Stream channel on error, reason = {}, node = {}.", t.getMessage(), nodeId);
        }

        public void onStreamChannelCompleted() {
            runtimeChannel.removeStub(P4RuntimeStub.this);
            countDownLatch.countDown();
            LOG.info("Stream channel on complete, node = {}.", nodeId);
        }

        /**
         * Each P4RuntimeStub needs to open a bi-directional stream connection to the server
         * using the streamChannel RPC, the connector should advertise its election id right
         * away using a MasterArbitrationUpdate message.
         */
        public void openStreamChannel() {
            runtimeChannel.addStub(P4RuntimeStub.this);
            countDownLatch = new CountDownLatch(1);
            StreamObserver<StreamMessageResponse> response = new StreamObserver<StreamMessageResponse>() {
                @Override
                public void onNext(StreamMessageResponse response) {
                    onPacketReceived(response);
                }

                @Override
                public void onError(Throwable t) {
                    onStreamChannelError(t);
                }

                @Override
                public void onCompleted() {
                    onStreamChannelCompleted();
                }
            };
            observer = getAsyncStub().streamChannel(response);
            sendMasterArbitration(electionId);
        }

        public void shutdown() {
            if(observer != null) {
                observer.onCompleted();
            }
        }
    }
}