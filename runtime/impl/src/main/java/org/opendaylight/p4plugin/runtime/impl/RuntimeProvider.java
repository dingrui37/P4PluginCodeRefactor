/*
 * Copyright © 2017 zte and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.p4plugin.runtime.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.p4plugin.runtime.impl.channel.P4RuntimeChannel;
import org.opendaylight.p4plugin.runtime.impl.utils.NotificationPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuntimeProvider {
    private static final Logger LOG = LoggerFactory.getLogger(RuntimeProvider.class);
    private final DataBroker dataBroker;
    private final NotificationPublishService notificationPublishService;

    public RuntimeProvider(final DataBroker dataBroker,
                           final NotificationPublishService notificationPublishService) {
        this.dataBroker = dataBroker;
        this.notificationPublishService = notificationPublishService;
    }

    public void init() {
        new P4RuntimeChannel("localhost", 50051).shutdown();//grpc bug
        NotificationPublisher.getInstance().setNotificationService(notificationPublishService);
        LOG.info("P4plugin runtime provider initiated.");
    }

    public void close() {
        LOG.info("P4plugin runtime provider closed.");
    }
}
