/*
 * Copyright © 2017 zte and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.p4plugin.runtime.impl.table.entry;

import org.opendaylight.p4plugin.p4runtime.proto.Entity;
import org.opendaylight.p4plugin.p4runtime.proto.Update;
import org.opendaylight.p4plugin.p4runtime.proto.WriteRequest;
import org.opendaylight.p4plugin.runtime.impl.device.P4Device;
import org.opendaylight.yang.gen.v1.urn.opendaylight.p4plugin.runtime.table.rev170808.TableEntry;

public class TableEntryGenerator {
    private P4Device device;
    private Update.Type type;

    public TableEntryGenerator(P4Device device) {
        this.device = device;
    }

    public WriteRequest generate(TableEntry inputEntry, Update.Type type) {
        WriteRequest.Builder requestBuilder = WriteRequest.newBuilder();
        Update.Builder updateBuilder = Update.newBuilder();
        Entity.Builder entityBuilder = Entity.newBuilder();
        entityBuilder.setTableEntry(device.toMessage(inputEntry));
        updateBuilder.setType(type);
        updateBuilder.setEntity(entityBuilder);
        requestBuilder.setDeviceId(device.getDeviceId());
        requestBuilder.addUpdates(updateBuilder);
        return requestBuilder.build();
    }

}
