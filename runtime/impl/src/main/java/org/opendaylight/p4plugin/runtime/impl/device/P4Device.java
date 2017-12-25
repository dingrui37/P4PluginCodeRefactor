/*
 * Copyright © 2017 zte and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.p4plugin.runtime.impl.device;

import com.google.protobuf.ByteString;
import org.opendaylight.p4plugin.p4info.proto.Table;
import org.opendaylight.p4plugin.runtime.impl.channel.P4RuntimeStub;
import org.opendaylight.p4plugin.runtime.impl.table.action.AbstractActionParser;
import org.opendaylight.p4plugin.runtime.impl.table.action.DirectActionParser;
import org.opendaylight.p4plugin.runtime.impl.table.action.GroupActionParser;
import org.opendaylight.p4plugin.runtime.impl.table.action.MemberActionParser;
import org.opendaylight.p4plugin.runtime.impl.table.match.*;
import org.opendaylight.p4plugin.runtime.impl.utils.Utils;
import org.opendaylight.p4plugin.p4config.proto.P4DeviceConfig;
import org.opendaylight.p4plugin.p4info.proto.P4Info;
import org.opendaylight.p4plugin.p4runtime.proto.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.p4plugin.runtime.table.rev170808.TableEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.p4plugin.runtime.table.rev170808.table.entry.ActionType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.p4plugin.runtime.table.rev170808.table.entry.action.type.ACTIONPROFILEGROUP;
import org.opendaylight.yang.gen.v1.urn.opendaylight.p4plugin.runtime.table.rev170808.table.entry.action.type.ACTIONPROFILEMEMBER;
import org.opendaylight.yang.gen.v1.urn.opendaylight.p4plugin.runtime.table.rev170808.table.entry.action.type.DIRECTACTION;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * The relationship between P4Device, P4RuntimeStub, P4RuntimeChannel and Stream channel
 * is shown below.
 *    ---------------------------                      ---------------------------
 *  |           Device A        |                    |          Device B         |
 *  |                           |                    |                           |
 *  |  -----------------------  |                    |  -----------------------  |
 *  | |          Stub         | |                    | |          Stub         | |
 *  | |                       | |                    | |                       | |
 *  | |  -------------------  | |                    | |  -------------------  | |
 *  | | |  Stream Channel A | | |                    | | |  Stream Channel B | | |
 *  | | |                   | | |                    | | |                   | | |
 *  | |  -------------------  | |                    | |  -------------------  | |
 *  |  -----------------------  |                    |  -----------------------  |
 *   ---------------------------                      ---------------------------
 *               / \                                               / \
 *                |                                                 |
 *                 -------------------      ------------------------
 *                                    |    |
 *                                   \ /  \ /
 *   ----------------------------------------------------------------------------
 *  |                             gRPC channel                                   |
 *   ----------------------------------------------------------------------------
 *                     |                                     |
 *                     |     ---------------------------     |
 *                     |    |       Stream A Data       |    |
 *                     |     ---------------------------     |
 *                     |     ---------------------------     |
 *                     |    |       Stream B Data       |    |
 *                     |     ---------------------------     |
 *                     |     ---------------------------     |
 *                     |    |       Stream B Data       |    |
 *                     |     ---------------------------     |
 *                     |     ---------------------------     |
 *                     |    |       Stream A Data       |    |
 *                     |     ---------------------------     |
 *                     |                                     |
 *   -----------------------------------------------------------------------------
 *  |                                 Switch                                      |
 *  |   -----------------------------------------------------------------------   |
 *  |  |                            gRPC server                                |  |
 *  |   -----------------------------------------------------------------------   |
 *  |                / \                                       / \                |
 *  |                 |                                         |                 |
 *  |                 |                                         |                 |
 *  |                \ /                                       \ /                |
 *  |   ----------------------------               ----------------------------   |
 *  |  |                            |             |                            |  |
 *  |  |           Device A         |             |           Device B         |  |
 *  |  |                            |             |                            |  |
 *  |   ----------------------------               ----------------------------   |
 *   -----------------------------------------------------------------------------
 */
public class P4Device  {
    private static final Logger LOG = LoggerFactory.getLogger(P4Device.class);
    private P4RuntimeStub runtimeStub;
    private P4Info runtimeInfo;
    private ByteString deviceConfig;
    private String ip;
    private Integer port;
    private Long deviceId;
    private String nodeId;
    private State state = State.Unknown;
    private P4Device() {}

    public int getTableId(String tableName) {
        Optional<Table> optional = runtimeInfo.getTablesList()
                .stream()
                .filter(table -> table.getPreamble().getName().equals(tableName))
                .findFirst();
        return optional.orElseThrow(()-> new IllegalArgumentException("Invalid table name"))
                .getPreamble().getId();
    }

    public String getTableName(int tableId) {
        Optional<org.opendaylight.p4plugin.p4info.proto.Table> optional = runtimeInfo.getTablesList()
                .stream()
                .filter(table -> table.getPreamble().getId() == tableId)
                .findFirst();
        return optional.orElseThrow(()-> new IllegalArgumentException("Invalid table id"))
                .getPreamble().getName();
    }

    public int getMatchFieldId(String tableName, String matchFieldName) {
        Optional<org.opendaylight.p4plugin.p4info.proto.Table> tableContainer = runtimeInfo.getTablesList()
                .stream()
                .filter(table -> table.getPreamble().getName().equals(tableName))
                .findFirst();

        Optional<org.opendaylight.p4plugin.p4info.proto.MatchField> matchFieldContainer =
                tableContainer.orElseThrow(()-> new IllegalArgumentException("Invalid table name"))
                        .getMatchFieldsList()
                        .stream()
                        .filter(matchField -> matchField.getName().equals(matchFieldName))
                        .findFirst();

        return matchFieldContainer.orElseThrow(()-> new IllegalArgumentException("Invalid match field name"))
                .getId();
    }

    public String getMatchFieldName(int tableId, int matchFieldId) {
        Optional<org.opendaylight.p4plugin.p4info.proto.Table> tableContainer = runtimeInfo.getTablesList()
                .stream()
                .filter(table -> table.getPreamble().getId() == tableId)
                .findFirst();

        Optional<org.opendaylight.p4plugin.p4info.proto.MatchField> matchFieldContainer =
                tableContainer.orElseThrow(()-> new IllegalArgumentException("Invalid table id"))
                        .getMatchFieldsList()
                        .stream()
                        .filter(matchField -> matchField.getId() == (matchFieldId))
                        .findFirst();

        return matchFieldContainer.orElseThrow(()-> new IllegalArgumentException("Invalid match field id"))
                .getName();
    }

    public int getMatchFieldWidth(String tableName, String matchFieldName) {
        Optional<org.opendaylight.p4plugin.p4info.proto.Table> tableContainer = runtimeInfo.getTablesList()
                .stream()
                .filter(table -> table.getPreamble().getName().equals(tableName))
                .findFirst();

        Optional<org.opendaylight.p4plugin.p4info.proto.MatchField> matchFieldContainer =
                tableContainer.orElseThrow(()-> new IllegalArgumentException("Invalid table name"))
                        .getMatchFieldsList()
                        .stream()
                        .filter(matchField -> matchField.getName().equals(matchFieldName))
                        .findFirst();

        return (matchFieldContainer.orElseThrow(()-> new IllegalArgumentException("Invalid match field name"))
                .getBitwidth() + 7 ) / 8;
    }

    public int getActionId(String actionName) {
        Optional<org.opendaylight.p4plugin.p4info.proto.Action> optional = runtimeInfo.getActionsList()
                .stream()
                .filter(action -> action.getPreamble().getName().equals(actionName))
                .findFirst();
        return optional.orElseThrow(()-> new IllegalArgumentException("Invalid action name"))
                .getPreamble().getId();
    }

    public String getActionName(int actionId) {
        Optional<org.opendaylight.p4plugin.p4info.proto.Action> optional = runtimeInfo.getActionsList()
                .stream()
                .filter(action -> action.getPreamble().getId() == actionId)
                .findFirst();
        return optional.orElseThrow(()-> new IllegalArgumentException("Invalid action id"))
                .getPreamble().getName();
    }

    public int getParamId(String actionName, String paramName) {
        Optional<org.opendaylight.p4plugin.p4info.proto.Action> actionContainer = runtimeInfo.getActionsList()
                .stream()
                .filter(action -> action.getPreamble().getName().equals(actionName))
                .findFirst();

        Optional<org.opendaylight.p4plugin.p4info.proto.Action.Param> paramContainer =
                actionContainer.orElseThrow(()-> new IllegalArgumentException("Invalid action name"))
                        .getParamsList()
                        .stream()
                        .filter(param -> param.getName().equals(paramName))
                        .findFirst();

        return paramContainer.orElseThrow(()-> new IllegalArgumentException("Invalid param name"))
                .getId();
    }

    public String getParamName(int actionId, int paramId) {
        Optional<org.opendaylight.p4plugin.p4info.proto.Action> actionContainer = runtimeInfo.getActionsList()
                .stream()
                .filter(action -> action.getPreamble().getId() == actionId)
                .findFirst();

        Optional<org.opendaylight.p4plugin.p4info.proto.Action.Param> paramContainer =
                actionContainer.orElseThrow(()-> new IllegalArgumentException("Invalid action id"))
                        .getParamsList()
                        .stream()
                        .filter(param -> param.getId() == paramId)
                        .findFirst();

        return paramContainer.orElseThrow(()-> new IllegalArgumentException("Invalid param id"))
                .getName();
    }

    public int getParamWidth(String actionName, String paramName) {
        Optional<org.opendaylight.p4plugin.p4info.proto.Action> actionContainer = runtimeInfo.getActionsList()
                .stream()
                .filter(action -> action.getPreamble().getName().equals(actionName))
                .findFirst();

        Optional<org.opendaylight.p4plugin.p4info.proto.Action.Param> paramContainer =
                actionContainer.orElseThrow(()-> new IllegalArgumentException("Invalid action name"))
                        .getParamsList()
                        .stream()
                        .filter(param -> param.getName().equals(paramName))
                        .findFirst();

        return (paramContainer.orElseThrow(()-> new IllegalArgumentException("Invalid param name"))
                .getBitwidth() + 7 ) / 8;
    }

    public int getActionProfileId(String actionProfileName) {
        Optional<org.opendaylight.p4plugin.p4info.proto.ActionProfile> optional = runtimeInfo.getActionProfilesList()
                .stream()
                .filter(actionProfile -> actionProfile.getPreamble().getName().equals(actionProfileName))
                .findFirst();
        return optional.orElseThrow(()-> new IllegalArgumentException("Invalid action profile name"))
                .getPreamble().getId();
    }

    public String getActionProfileName(Integer actionProfileId) {
        Optional<org.opendaylight.p4plugin.p4info.proto.ActionProfile> optional = runtimeInfo.getActionProfilesList()
                .stream()
                .filter(actionProfile -> actionProfile.getPreamble().getId() == actionProfileId)
                .findFirst();
        return optional.orElseThrow(()-> new IllegalArgumentException("Invalid action profile id"))
                .getPreamble().getName();
    }

    public P4RuntimeStub getRuntimeStub() {
        return runtimeStub;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public String getIp() {
        return ip;
    }

    public Integer getPort() {
        return port;
    }

    public State getDeviceState() {
        return state;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setDeviceState(State state) {
        this.state = state;
    }

    public boolean isConfigured() {
        return runtimeInfo != null
            && deviceConfig != null
            && getDeviceState() == State.Configured;
    }

    public boolean connectToDevice() {
        return runtimeStub.connect();
    }

    public void shutdown() {
        runtimeStub.shutdown();
    }

    public SetForwardingPipelineConfigResponse setPipelineConfig() {
        ForwardingPipelineConfig.Builder configBuilder = ForwardingPipelineConfig.newBuilder();
        P4DeviceConfig.Builder p4DeviceConfigBuilder = P4DeviceConfig.newBuilder();
        p4DeviceConfigBuilder.setDeviceData(deviceConfig);
        configBuilder.setP4Info(runtimeInfo);

        configBuilder.setP4DeviceConfig(p4DeviceConfigBuilder.build().toByteString());
        configBuilder.setDeviceId(deviceId);
        SetForwardingPipelineConfigRequest request = SetForwardingPipelineConfigRequest.newBuilder()
                .setAction(SetForwardingPipelineConfigRequest.Action.VERIFY_AND_COMMIT)
                .addConfigs(configBuilder)
                .build();

        SetForwardingPipelineConfigResponse response;
        response = runtimeStub.getBlockingStub().setForwardingPipelineConfig(request);
        setDeviceState(State.Configured);
        return response;
    }

    public GetForwardingPipelineConfigResponse getPipelineConfig() {
        GetForwardingPipelineConfigRequest request = GetForwardingPipelineConfigRequest.newBuilder()
                .addDeviceIds(deviceId)
                .build();
        GetForwardingPipelineConfigResponse response;
        response = runtimeStub.getBlockingStub().getForwardingPipelineConfig(request);
        return response;
    }

    public WriteResponse write(WriteRequest request) {
        WriteResponse response = runtimeStub.getBlockingStub().write(request);
        return response;
    }

    public Iterator<ReadResponse> read(ReadRequest request) {
        Iterator<ReadResponse> responses;
        responses = runtimeStub.getBlockingStub().read(request);
        return responses;
    }

    public void transmitPacket(byte[] payload) {
        runtimeStub.transmitPacket(payload);
    }



    private TableAction buildTableAction(ActionType actionType) {
        AbstractActionParser parser;
        if (actionType instanceof DIRECTACTION) {
            parser = new DirectActionParser(this, (DIRECTACTION)actionType);
        } else if (actionType instanceof ACTIONPROFILEMEMBER) {
            parser = new MemberActionParser((ACTIONPROFILEMEMBER) actionType);
        } else if (actionType instanceof ACTIONPROFILEGROUP) {
            parser = new GroupActionParser(((ACTIONPROFILEGROUP) actionType));
        } else {
            throw new IllegalArgumentException("Invalid action type");
        }
        return parser.parse();
    }

    private FieldMatch buildFieldMatch(Field field, String tableName) {
        AbstractMatchFieldParser parser;
        MatchType matchType = field.getMatchType();
        String fieldName = field.getFieldName();
        if (matchType instanceof EXACT) {
            parser = new ExactMatchParser(this, (EXACT) matchType, tableName, fieldName);
        } else if (matchType instanceof LPM) {
            parser = new LpmMatchParser(this, (LPM) matchType, tableName, fieldName);
        } else if (matchType instanceof TERNARY) {
            parser = new TernaryMatchParser(this, (TERNARY) matchType, tableName, fieldName);
        } else if (matchType instanceof RANGE) {
            parser = new RangeMatchParser(this, (RANGE) matchType, tableName, fieldName);
        } else {
            throw new IllegalArgumentException("Invalid match type");
        }
        return parser.parse();
    }

    /**
     * Input table entry serialize to protobuf message. Used for adding and modifying an entry.
     * When this method is called, the device must be configured.
     */
    public org.opendaylight.p4plugin.p4runtime.proto.TableEntry toMessage(TableEntry input) {
        String tableName = input.getTable();
        int tableId = getTableId(tableName);
        org.opendaylight.p4plugin.p4runtime.proto.TableEntry.Builder tableEntryBuilder =
                org.opendaylight.p4plugin.p4runtime.proto.TableEntry.newBuilder();
        List<Field> fields = input.getField();
        fields.forEach(field -> {
            org.opendaylight.p4plugin.p4runtime.proto.FieldMatch fieldMatch = buildFieldMatch(field, tableName);
            tableEntryBuilder.addMatch(fieldMatch);
        });
        ActionType actionType = input.getActionType();
        org.opendaylight.p4plugin.p4runtime.proto.TableAction tableAction = buildTableAction(actionType);
        tableEntryBuilder.setPriority(input.getPriority());
        tableEntryBuilder.setControllerMetadata(input.getControllerMetadata().longValue());
        tableEntryBuilder.setTableId(tableId);
        tableEntryBuilder.setAction(tableAction);
        return tableEntryBuilder.build();
    }

    /**
     * Used for deleting table entry, when delete a table entry, only need table name
     * and match fields actually. BTW, this the only way for search a table entry,
     * not support table entry id.
     */
    public org.opendaylight.p4plugin.p4runtime.proto.TableEntry toMessage(EntryKey input) {
        String tableName = input.getTable();
        int tableId = getTableId(tableName);
        org.opendaylight.p4plugin.p4runtime.proto.TableEntry.Builder tableEntryBuilder =
                org.opendaylight.p4plugin.p4runtime.proto.TableEntry.newBuilder();
        List<Field> fields = input.getField();
        fields.forEach(field -> {
            org.opendaylight.p4plugin.p4runtime.proto.FieldMatch fieldMatch =
                    buildFieldMatch(field, tableName);
            tableEntryBuilder.addMatch(fieldMatch);
        });
        tableEntryBuilder.setTableId(tableId);
        return tableEntryBuilder.build();
    }

    /**
     * Input action profile member serialize to protobuf message, used for adding and
     * modifying a member. When this method is called, the device must be configured.
     */
    public org.opendaylight.p4plugin.p4runtime.proto.ActionProfileMember toMessage(ActionProfileMember member) {
        String actionName = member.getActionName();
        Long memberId = member.getMemberId();
        String actionProfile = member.getActionProfile();

        org.opendaylight.p4plugin.p4runtime.proto.ActionProfileMember.Builder memberBuilder =
                org.opendaylight.p4plugin.p4runtime.proto.ActionProfileMember.newBuilder();
        org.opendaylight.p4plugin.p4runtime.proto.Action.Builder actionBuilder =
                org.opendaylight.p4plugin.p4runtime.proto.Action.newBuilder();

        actionBuilder.setActionId(getActionId(actionName));
        member.getActionParam().forEach(actionParam -> {
            org.opendaylight.p4plugin.p4runtime.proto.Action.Param.Builder paramBuilder =
                    org.opendaylight.p4plugin.p4runtime.proto.Action.Param.newBuilder();
            String paramName = actionParam.getParamName();
            ParamValueType valueType = actionParam.getParamValueType();
            int paramId = getParamId(actionName, paramName);
            int paramWidth = getParamWidth(actionName, paramName);

            if (valueType instanceof PARAMVALUETYPESTRING) {
                String valueStr = ((PARAMVALUETYPESTRING) valueType).getParamStringValue();
                byte[] valueByteArr = Utils.strToByteArray(valueStr, paramWidth);
                paramBuilder.setValue(ByteString.copyFrom(valueByteArr));
            } else if (valueType instanceof PARAMVALUETYPEBINARY) {
                byte[] valueBytes = ((PARAMVALUETYPEBINARY) valueType).getParamBinaryValue();
                paramBuilder.setValue(ByteString.copyFrom(valueBytes, 0, paramWidth));
            } else {
                throw new IllegalArgumentException("Invalid value type.");
            }

            paramBuilder.setParamId(paramId);
            actionBuilder.addParams(paramBuilder);
        });

        memberBuilder.setAction(actionBuilder);
        memberBuilder.setActionProfileId(getActionProfileId(actionProfile));
        memberBuilder.setMemberId(memberId.intValue());
        return memberBuilder.build();
    }

    /**
     * Used for delete one member in action profile.table. When delete a member,
     * only need action profile name and member id.
     */
    public org.opendaylight.p4plugin.p4runtime.proto.ActionProfileMember toMessage(MemberKey key) {
        Long memberId = key.getMemberId();
        String actionProfile = key.getActionProfile();
        org.opendaylight.p4plugin.p4runtime.proto.ActionProfileMember.Builder memberBuilder =
                org.opendaylight.p4plugin.p4runtime.proto.ActionProfileMember.newBuilder();
        memberBuilder.setActionProfileId(getActionProfileId(actionProfile));
        memberBuilder.setMemberId(memberId.intValue());
        return memberBuilder.build();
    }

    /**
     * Input action profile group serialize to protobuf message, used for add/modify.
     * When this method is called, the device must be configured.
     */
    public org.opendaylight.p4plugin.p4runtime.proto.ActionProfileGroup toMessage(ActionProfileGroup group) {
        Long groupId = group.getGroupId();
        String actionProfile = group.getActionProfile();
        org.opendaylight.yang.gen.v1.urn
                .opendaylight.p4plugin.runtime.table.rev170808.ActionProfileGroup.GroupType
                type = group.getGroupType();
        Integer maxSize = group.getMaxSize();

        org.opendaylight.p4plugin.p4runtime.proto.ActionProfileGroup.Builder groupBuilder =
                org.opendaylight.p4plugin.p4runtime.proto.ActionProfileGroup.newBuilder();
        groupBuilder.setActionProfileId(getActionProfileId(actionProfile));
        groupBuilder.setGroupId(groupId.intValue());
        groupBuilder.setType(org.opendaylight.p4plugin.p4runtime.proto.ActionProfileGroup
                .Type.valueOf(type.toString()));
        groupBuilder.setMaxSize(maxSize);

        group.getGroupMember().forEach(groupMember -> {
            org.opendaylight.p4plugin.p4runtime.proto.ActionProfileGroup.Member.Builder builder =
                    org.opendaylight.p4plugin.p4runtime.proto.ActionProfileGroup.Member.newBuilder();
            builder.setWatch(groupMember.getWatch().intValue());
            builder.setWeight(groupMember.getWeight().intValue());
            builder.setMemberId(groupMember.getMemberId().intValue());
            groupBuilder.addMembers(builder);
        });
        return groupBuilder.build();
    }

    /**
     * Used for delete one group in action profile. When delete a group,
     * only need action profile name and group id.
     */
    public org.opendaylight.p4plugin.p4runtime.proto.ActionProfileGroup toMessage(GroupKey key) {
        Long groupId = key.getGroupId();
        String actionProfile = key.getActionProfile();
        org.opendaylight.p4plugin.p4runtime.proto.ActionProfileGroup.Builder groupBuilder =
                org.opendaylight.p4plugin.p4runtime.proto.ActionProfileGroup.newBuilder();
        groupBuilder.setActionProfileId(getActionProfileId(actionProfile));
        groupBuilder.setGroupId(groupId.intValue());
        return groupBuilder.build();
    }

    /**
     * Table entry object to human-readable string, for read table entry.
     */
    public String toString(org.opendaylight.p4plugin.p4runtime.proto.TableEntry entry) {
        org.opendaylight.p4plugin.p4runtime.proto.TableAction tableAction = entry.getAction();
        int tableId = entry.getTableId();
        String tableName = getTableName(tableId);
        StringBuffer buffer = new StringBuffer();
        buffer.append(tableName).append(" ");

        List<org.opendaylight.p4plugin.p4runtime.proto.FieldMatch> fieldList = entry.getMatchList();
        fieldList.forEach(field -> {
            int fieldId = field.getFieldId();
            switch (field.getFieldMatchTypeCase()) {
                case EXACT: {
                    org.opendaylight.p4plugin.p4runtime.proto.FieldMatch.Exact exact = field.getExact();
                    buffer.append(String.format("%s = ", getMatchFieldName(tableId, fieldId)));
                    buffer.append(Utils.byteArrayToStr(exact.getValue().toByteArray()));
                    buffer.append(":exact");
                    break;
                }

                case LPM: {
                    org.opendaylight.p4plugin.p4runtime.proto.FieldMatch.LPM lpm = field.getLpm();
                    buffer.append(String.format("%s = ", getMatchFieldName(tableId, fieldId)));
                    buffer.append(Utils.byteArrayToStr(lpm.getValue().toByteArray()));
                    buffer.append("/");
                    buffer.append(String.valueOf(lpm.getPrefixLen()));
                    buffer.append(":lpm");
                    break;
                }

                case TERNARY: {
                    org.opendaylight.p4plugin.p4runtime.proto.FieldMatch.Ternary ternary = field.getTernary();
                    buffer.append(String.format("%s = ", getMatchFieldName(tableId, fieldId)));
                    buffer.append(Utils.byteArrayToStr(ternary.getValue().toByteArray()));
                    buffer.append("/");
                    buffer.append(String.valueOf(ternary.getMask()));//TODO
                    break;
                }
                //TODO
                case RANGE:
                    break;
                default:
                    break;
            }
        });

        switch (tableAction.getTypeCase()) {
            case ACTION: {
                int actionId = tableAction.getAction().getActionId();
                List<org.opendaylight.p4plugin.p4runtime.proto.Action.Param> paramList =
                        tableAction.getAction().getParamsList();
                buffer.append(" ").append(getActionName(actionId)).append("(");
                paramList.forEach(param -> {
                    int paramId = param.getParamId();
                    buffer.append(String.format("%s", getParamName(actionId, paramId)));
                    buffer.append(" = ");
                    buffer.append(String.format("%s", Utils.byteArrayToStr(param.getValue().toByteArray())));
                });
                buffer.append(")");
                break;
            }

            case ACTION_PROFILE_MEMBER_ID: {
                int memberId = entry.getAction().getActionProfileMemberId();
                buffer.append(" member id = ").append(memberId);
                break;
            }

            case ACTION_PROFILE_GROUP_ID: {
                int groupId = entry.getAction().getActionProfileGroupId();
                buffer.append(" group id = ").append(groupId);
                break;
            }

            default:break;
        }
        return new String(buffer);
    }

    /**
     * Action profile member to human-readable string.
     */
    public String toString(org.opendaylight.p4plugin.p4runtime.proto.ActionProfileMember member) {
        int profileId = member.getActionProfileId();
        int memberId = member.getMemberId();
        org.opendaylight.p4plugin.p4runtime.proto.Action action = member.getAction();
        List<org.opendaylight.p4plugin.p4runtime.proto.Action.Param> paramList = action.getParamsList();
        int actionId = action.getActionId();
        String actionProfile = getActionProfileName(profileId);
        StringBuffer buffer = new StringBuffer();
        buffer.append(String.format("%s - %d", actionProfile, memberId));
        buffer.append(" ").append(getActionName(actionId)).append("(");
        paramList.forEach(param -> {
            int paramId = param.getParamId();
            buffer.append(String.format("%s", getParamName(actionId, paramId)));
            buffer.append(" = ");
            buffer.append(String.format("%s", Utils.byteArrayToStr(param.getValue().toByteArray())));
        });
        buffer.append(")");
        return new String(buffer);
    }

    /**
     * Action profile group to human-readable string.
     */
    public String toString(org.opendaylight.p4plugin.p4runtime.proto.ActionProfileGroup group) {
        int profileId = group.getActionProfileId();
        int groupId = group.getGroupId();
        String actionProfile = getActionProfileName(profileId);
        StringBuffer buffer = new StringBuffer();
        buffer.append(String.format("%s - %d : ", actionProfile, groupId));
        group.getMembersList().forEach(member -> buffer.append(member.getMemberId()).append(" "));
        return new String(buffer);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private P4Info runtimeInfo_;
        private ByteString deviceConfig_;
        private Long deviceId_;
        private String nodeId_;
        private String ip_;
        private Integer port_;

        public Builder setIp(String ip) {
            this.ip_ = ip;
            return this;
        }

        public Builder setPort(Integer port) {
            this.port_ = port;
            return this;
        }

        public Builder setRuntimeInfo(P4Info p4Info) {
            this.runtimeInfo_ = p4Info;
            return this;
        }

        public Builder setDeviceConfig(ByteString config) {
            this.deviceConfig_ = config;
            return this;
        }

        public Builder setDeviceId(Long deviceId) {
            this.deviceId_ = deviceId;
            return this;
        }

        public Builder setNodeId(String nodeId) {
            this.nodeId_ = nodeId;
            return this;
        }

        public P4Device build() {
            P4Device device = new P4Device();
            device.deviceConfig = deviceConfig_;
            device.deviceId = deviceId_;
            device.nodeId = nodeId_;
            device.ip = ip_;
            device.port = port_;
            device.runtimeInfo = runtimeInfo_;
            device.runtimeStub = new P4RuntimeStub(nodeId_, deviceId_, ip_, port_);
            return device;
        }
    }

    public enum State {
        Unknown,
        Connected,
        Configured,
    }
}
