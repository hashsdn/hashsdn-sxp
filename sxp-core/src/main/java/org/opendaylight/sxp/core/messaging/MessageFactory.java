/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.sxp.core.messaging;

import static org.opendaylight.sxp.core.Constants.MESSAGE_HEADER_LENGTH_LENGTH;
import static org.opendaylight.sxp.core.Constants.MESSAGE_HEADER_TYPE_LENGTH;
import static org.opendaylight.sxp.core.Constants.MESSAGE_LENGTH_MAX;

import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.opendaylight.sxp.core.messaging.legacy.LegacyMessageFactory;
import org.opendaylight.sxp.util.ArraysUtil;
import org.opendaylight.sxp.util.exception.ErrorCodeDataLengthException;
import org.opendaylight.sxp.util.exception.message.ErrorMessageException;
import org.opendaylight.sxp.util.exception.message.attribute.AddressLengthException;
import org.opendaylight.sxp.util.exception.message.attribute.AttributeLengthException;
import org.opendaylight.sxp.util.exception.message.attribute.AttributeNotFoundException;
import org.opendaylight.sxp.util.exception.message.attribute.AttributeVariantException;
import org.opendaylight.sxp.util.exception.message.attribute.CapabilityLengthException;
import org.opendaylight.sxp.util.exception.message.attribute.HoldTimeMaxException;
import org.opendaylight.sxp.util.exception.message.attribute.HoldTimeMinException;
import org.opendaylight.sxp.util.exception.message.attribute.SecurityGroupTagValueException;
import org.opendaylight.sxp.util.exception.message.attribute.TlvNotFoundException;
import org.opendaylight.sxp.util.exception.unknown.UnknownNodeIdException;
import org.opendaylight.sxp.util.exception.unknown.UnknownPrefixException;
import org.opendaylight.sxp.util.exception.unknown.UnknownSxpMessageTypeException;
import org.opendaylight.sxp.util.exception.unknown.UnknownVersionException;
import org.opendaylight.sxp.util.filtering.SxpBindingFilter;
import org.opendaylight.sxp.util.inet.NodeIdConv;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.database.rev160308.Sgt;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.database.rev160308.SxpBindingFields;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.database.rev160308.peer.sequence.fields.PeerSequence;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.AttributeType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.CapabilityAttributeFields;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.CapabilityType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.ConnectionMode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.ErrorCode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.ErrorCodeNonExtended;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.ErrorSubCode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.ErrorType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.MessageType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.SxpHeader;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.SxpPayload;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.Version;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.attributes.fields.Attribute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.attributes.fields.attribute.attribute.optional.fields.CapabilitiesAttribute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.sxp.messages.ErrorMessageBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.sxp.messages.KeepaliveMessageBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.sxp.messages.Notification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.sxp.messages.OpenMessage;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.sxp.messages.OpenMessageBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.sxp.messages.PurgeAllMessageBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.sxp.messages.UpdateMessageBuilder;

/**
 * MessageFactory class contains logic for creating and parsing messages
 */
public class MessageFactory {

    /**
     * Creates Error message based on provided error code
     *
     * @param errorCode    ErrorCodeNonExtended defining type of error message
     * @param errorSubCode ErrorSubCode defining error  sub type
     * @param data         Byte Array containing additional information
     * @return ByteBuf representation of Error
     * @throws ErrorCodeDataLengthException If message Length is to long
     */
    public static ByteBuf createError(ErrorCode errorCode, ErrorSubCode errorSubCode, byte[] data)
            throws ErrorCodeDataLengthException {
        if (data == null) {
            data = new byte[0];
        } else if (data.length > 10) {
            throw new ErrorCodeDataLengthException("Variable message length - 10 bytes.");
        }

        byte _errorCode = errorCode != null ? (byte) errorCode.getIntValue() : 0x00;
        byte _errorSubCode = errorSubCode != null ? (byte) errorSubCode.getIntValue() : 0x00;
        byte[]
                payload =
                ArraysUtil.combine(new byte[] {ArraysUtil.setBit(_errorCode, 8, true), _errorSubCode, 0x00, 0x00},
                        data);
        return getMessage(MessageType.Error, payload);
    }

    /**
     * @return Generated KeepAlive message
     */
    public static ByteBuf createKeepalive() {
        return getMessage(MessageType.Keepalive, new byte[0]);
    }

    /**
     * Creates OpenMessage using provided values
     *
     * @param version   Version included in message
     * @param nodeMode  ConnectionMode included in message
     * @param nodeID    NodeId included in message
     * @param attribute Attribute included in message
     * @return ByteBuf representation of created OpenMessage
     * @throws AttributeVariantException If attribute variant isn't supported
     * @throws UnknownVersionException   If version isn't supported
     * @throws CapabilityLengthException If some Attributes has incorrect length
     */
    private static ByteBuf createOpen(Version version, ConnectionMode nodeMode, NodeId nodeID, Attribute attribute)
            throws AttributeVariantException, UnknownVersionException, CapabilityLengthException {
        AttributeList attributes = createOpenAttribute(version, nodeMode, nodeID);
        if (attribute != null) {
            attributes.add(attribute);
        }
        // Add optional attributes..
        byte[]
                payload =
                ArraysUtil.combine(new byte[] {0x00, 0x00, 0x00, (byte) version.getIntValue(), 0x00, 0x00, 0x00,
                        (byte) nodeMode.getIntValue()}, attributes.toBytes());
        return getMessage(MessageType.Open, payload);
    }

    /**
     * Creates OpenMessage using provided values
     *
     * @param version               Version included in message
     * @param nodeMode              ConnectionMode included in message
     * @param nodeID                NodeId included in message
     * @param holdTimeMinAcceptable Minimal acceptable Hold time included in message
     * @return ByteBuf representation of created OpenMessage
     * @throws HoldTimeMinException      If Min hold time isn't in range of [0, 65535]
     * @throws AttributeVariantException If attribute variant isn't supported
     * @throws UnknownVersionException   If version isn't supported
     * @throws CapabilityLengthException If some Attributes has incorrect length
     */
    public static ByteBuf createOpen(Version version, ConnectionMode nodeMode, NodeId nodeID, int holdTimeMinAcceptable)
            throws HoldTimeMinException, AttributeVariantException, UnknownVersionException, CapabilityLengthException {
        return createOpen(version, nodeMode, nodeID, AttributeFactory.createHoldTime(holdTimeMinAcceptable));
    }

    /**
     * Creates OpenMessage using provided values
     *
     * @param version     Version included in message
     * @param nodeMode    ConnectionMode included in message
     * @param nodeID      NodeId included in message
     * @param holdTimeMin Minimal Hold time included in message
     * @param holdTimeMax Maximal Hold time included in message
     * @return ByteBuf representation of created OpenMessage
     * @throws HoldTimeMaxException      If Max hold time is greater than minimal
     * @throws HoldTimeMinException      If Min hold time isn't in range of [0, 65535]
     * @throws AttributeVariantException If attribute variant isn't supported
     * @throws UnknownVersionException   If version isn't supported
     * @throws CapabilityLengthException If some Attributes has incorrect length
     */
    public static ByteBuf createOpen(Version version, ConnectionMode nodeMode, NodeId nodeID, int holdTimeMin,
            int holdTimeMax)
            throws HoldTimeMaxException, HoldTimeMinException, AttributeVariantException, UnknownVersionException,
            CapabilityLengthException {
        return createOpen(version, nodeMode, nodeID, AttributeFactory.createHoldTime(holdTimeMin, holdTimeMax));
    }

    /**
     * Creates Attribute into OpenMessage according to connection mode and version
     *
     * @param version  Version of connection
     * @param nodeMode ConnectionMode of connection
     * @param nodeID   NodeId included in message
     * @return List of Attributes
     * @throws UnknownVersionException   If version isn't supported
     * @throws CapabilityLengthException If some Attributes has incorrect length
     */
    private static AttributeList createOpenAttribute(Version version, ConnectionMode nodeMode, NodeId nodeID)
            throws UnknownVersionException, CapabilityLengthException {
        AttributeList attributes = new AttributeList();
        if (nodeMode.equals(ConnectionMode.Speaker)) {
            attributes.add(AttributeFactory.createSxpNodeId(nodeID));
        } else if (nodeMode.equals(ConnectionMode.Listener)) {
            attributes.add(AttributeFactory.createCapabilities(version));
        }
        return attributes;
    }

    /**
     * Creates OpenRespMessage using provided values
     *
     * @param version   Version included in message
     * @param nodeMode  ConnectionMode included in message
     * @param nodeID    NodeId included in message
     * @param attribute Attribute included in message
     * @return ByteBuff representation of created message
     * @throws UnknownVersionException   If version isn't supported
     * @throws CapabilityLengthException If some Attributes has incorrect length
     * @throws AttributeVariantException If attribute variant isn't supported
     */
    private static ByteBuf createOpenResp(Version version, ConnectionMode nodeMode, NodeId nodeID, Attribute attribute)
            throws UnknownVersionException, CapabilityLengthException, AttributeVariantException {
        AttributeList attributes = createOpenAttribute(version, nodeMode, nodeID);
        if (attribute != null) {
            attributes.add(attribute);
        }
        // Add optional attributes..
        byte[]
                payload =
                ArraysUtil.combine(new byte[] {0x00, 0x00, 0x00, (byte) version.getIntValue(), 0x00, 0x00, 0x00,
                        (byte) nodeMode.getIntValue()}, attributes.toBytes());
        return getMessage(MessageType.OpenResp, payload);
    }

    /**
     * Creates OpenRespMessage using provided values
     *
     * @param version  Version included in message
     * @param nodeMode ConnectionMode included in message
     * @param nodeID   NodeId included in message
     * @return ByteBuff representation of created message
     * @throws UnknownVersionException   If version isn't supported
     * @throws CapabilityLengthException If some Attributes has incorrect length
     * @throws AttributeVariantException If attribute variant isn't supported
     */
    public static ByteBuf createOpenResp(Version version, ConnectionMode nodeMode, NodeId nodeID)
            throws CapabilityLengthException, UnknownVersionException, AttributeVariantException {
        return createOpenResp(version, nodeMode, nodeID, null);
    }

    /**
     * Creates OpenRespMessage using provided values
     *
     * @param version               Version included in message
     * @param nodeMode              ConnectionMode included in message
     * @param nodeID                NodeId included in message
     * @param holdTimeMinAcceptable Minimal acceptable Hold time included in message
     * @return ByteBuff representation of OpenRespMessage
     * @throws HoldTimeMinException      If Min hold time isn't in range of [0, 65535]
     * @throws UnknownVersionException   If version isn't supported
     * @throws CapabilityLengthException If some Attributes has incorrect length
     * @throws AttributeVariantException If attribute variant isn't supported
     */
    public static ByteBuf createOpenResp(Version version, ConnectionMode nodeMode, NodeId nodeID,
            int holdTimeMinAcceptable)
            throws HoldTimeMinException, CapabilityLengthException, UnknownVersionException, AttributeVariantException {
        return createOpenResp(version, nodeMode, nodeID, AttributeFactory.createHoldTime(holdTimeMinAcceptable));
    }

    /**
     * Creates OpenRespMessage using provided values
     *
     * @param version     Version included in message
     * @param nodeMode    ConnectionMode included in message
     * @param nodeID      NodeId included in message
     * @param holdTimeMin Minimal Hold time included in message
     * @param holdTimeMax Maximal Hold time included in message
     * @return ByteBuff representation of OpenRespMessage
     * @throws HoldTimeMaxException      If Max hold time is greater than minimal
     * @throws HoldTimeMinException      If Min hold time isn't in range of [0, 65535]
     * @throws UnknownVersionException   If version isn't supported
     * @throws CapabilityLengthException If some Attributes has incorrect length
     * @throws AttributeVariantException If attribute variant isn't supported
     */
    public static ByteBuf createOpenResp(Version version, ConnectionMode nodeMode, NodeId nodeID, int holdTimeMin,
            int holdTimeMax)
            throws HoldTimeMaxException, HoldTimeMinException, CapabilityLengthException, UnknownVersionException,
            AttributeVariantException {
        return createOpenResp(version, nodeMode, nodeID, AttributeFactory.createHoldTime(holdTimeMin, holdTimeMax));
    }

    /**
     * @return Generate PurgeAll message
     */
    public static ByteBuf createPurgeAll() {
        return getMessage(MessageType.PurgeAll, new byte[0]);
    }

    /**
     * Creates UpdateMessage using provided values
     *
     * @param deleteBindings Bindings that will be deleted
     * @param addBindings    Bindings that will be added
     * @param nodeId         NodeId included in message
     * @return ByteBuf representation of UpdateMessage
     * @throws SecurityGroupTagValueException If some Sgt isn't in rage [2, 65519]
     * @throws AttributeVariantException      If some attribute variant isn't supported
     */
    public static <R extends SxpBindingFields, T extends SxpBindingFields> ByteBuf createUpdate(List<R> deleteBindings,
            List<T> addBindings, NodeId nodeId, List<CapabilityType> capabilities, SxpBindingFilter bindingFilter)
            throws SecurityGroupTagValueException, AttributeVariantException {
        AttributeList attributes = new AttributeList();
        List<IpPrefix> ipv4Prefixes = new ArrayList<>();
        List<IpPrefix> ipv6Prefixes = new ArrayList<>();

        // Processing of binding delete attributes.
        if (deleteBindings != null && !deleteBindings.isEmpty()) {
            deleteBindings.forEach(b -> {
                if (b.getIpPrefix().getIpv4Prefix() != null) {
                    ipv4Prefixes.add(b.getIpPrefix());
                } else if (b.getIpPrefix().getIpv6Prefix() != null) {
                    ipv6Prefixes.add(b.getIpPrefix());
                }
            });
            // Binding delete attributes include any of IPv4-Del-Prefix,
            // IPv6-Del-Prefix, Del-IPv4, or Del-IPv6 attributes.
            if (!ipv4Prefixes.isEmpty()) {
                attributes.add(AttributeFactory.createIpv4DeletePrefix(ipv4Prefixes, AttributeFactory._oNpCe));
                ipv4Prefixes.clear();
            }
            if (!ipv6Prefixes.isEmpty()) {
                attributes.add(AttributeFactory.createIpv6DeletePrefix(ipv6Prefixes, AttributeFactory._oNpCe));
                ipv6Prefixes.clear();
            }
        }

        // Processing of binding add attributes.
        if (addBindings != null && !addBindings.isEmpty()) {
            Sgt sgt = null;
            PeerSequence peerSequence = null;
            for (T binding : addBindings) {
                if (bindingFilter != null && bindingFilter.apply(binding))
                    continue;
                if ((!binding.getPeerSequence().equals(peerSequence) || !binding.getSecurityGroupTag().equals(sgt))) {

                    if (!ipv4Prefixes.isEmpty() && sgt != null && peerSequence != null) {
                        attributes.add(AttributeFactory.createIpv4AddPrefix(ipv4Prefixes, capabilities.contains(
                                CapabilityType.Ipv4Unicast) ? AttributeFactory._oNpCe : AttributeFactory._OnpCe));
                        ipv4Prefixes.clear();
                    }
                    if (!ipv6Prefixes.isEmpty() && sgt != null && peerSequence != null) {
                        attributes.add(AttributeFactory.createIpv6AddPrefix(ipv6Prefixes, capabilities.contains(
                                CapabilityType.Ipv6Unicast) ? AttributeFactory._oNpCe : AttributeFactory._OnpCe));
                        ipv6Prefixes.clear();
                    }

                    List<NodeId> peers = NodeIdConv.getPeerSequence(binding.getPeerSequence());
                    peers.add(0, nodeId);
                    attributes.add(AttributeFactory.createPeerSequence(peers));
                    attributes.add(AttributeFactory.createSourceGroupTag(binding.getSecurityGroupTag().getValue()));

                    sgt = binding.getSecurityGroupTag();
                    peerSequence = binding.getPeerSequence();
                }

                if (binding.getIpPrefix().getIpv4Prefix() != null) {
                    ipv4Prefixes.add(binding.getIpPrefix());
                } else if (binding.getIpPrefix().getIpv6Prefix() != null) {
                    ipv6Prefixes.add(binding.getIpPrefix());
                }

            }
            if (!ipv4Prefixes.isEmpty()) {
                attributes.add(AttributeFactory.createIpv4AddPrefix(ipv4Prefixes, capabilities.contains(
                        CapabilityType.Ipv4Unicast) ? AttributeFactory._oNpCe : AttributeFactory._OnpCe));
                ipv4Prefixes.clear();
            }
            if (!ipv6Prefixes.isEmpty()) {
                attributes.add(AttributeFactory.createIpv6AddPrefix(ipv6Prefixes, capabilities.contains(
                        CapabilityType.Ipv6Unicast) ? AttributeFactory._oNpCe : AttributeFactory._OnpCe));
                ipv6Prefixes.clear();
            }
        }
        return getMessage(MessageType.Update, attributes.toBytes());
    }

    /**
     * Decodes Byte Array into specific message
     *
     * @param version    Version used for decoding
     * @param headerType Type of header
     * @param payload    Byte Array containing message
     * @return Decoded message
     * @throws ErrorMessageException          If version Mismatch occurs
     * @throws UnknownPrefixException         If some attribute has incorrect or none Prefix
     * @throws AddressLengthException         If address length of some attribute is incorrect
     * @throws AttributeLengthException       If length of some attribute is incorrect
     * @throws UnknownHostException           If some attribute have incorrect or none address
     * @throws UnknownNodeIdException         If NodeId isn't found or is incorrect
     * @throws TlvNotFoundException           If Tvl isn't found
     * @throws UnknownSxpMessageTypeException If data contains unsupported message
     */
    private static Notification decode(Version version, byte[] headerType, byte[] payload)
            throws ErrorMessageException, UnknownPrefixException, AddressLengthException, AttributeLengthException,
            UnknownHostException, UnknownNodeIdException, TlvNotFoundException, UnknownSxpMessageTypeException,
            AttributeVariantException {
        MessageType messageType = MessageType.forValue(ArraysUtil.bytes2int(headerType));

        // Remote can send OpenResp with different version
        if (messageType == MessageType.OpenResp || messageType == MessageType.Open) {
            final Version remoteVersion = extractVersion(payload);
            // Override version setting for parsing
            if (remoteVersion != version) {
                version = remoteVersion;
            }
        }

        if (isLegacy(version)) {
            switch (messageType) {
                case Open:
                    return LegacyMessageFactory.decodeOpen(payload);
                case OpenResp:
                    return LegacyMessageFactory.decodeOpenResp(payload);
                case Update:
                    return LegacyMessageFactory.decodeUpdate(payload);
                case Error:
                    return decodeErrorMessage(payload);
                case PurgeAll:
                    return decodePurgeAll(payload);
                default:
                    break;
            }
        } else if (version.equals(Version.Version4)) {
            switch (messageType) {
                case Open:
                    return decodeOpen(payload);
                case OpenResp:
                    return decodeOpenResp(payload);
                case Update:
                    return decodeUpdate(payload);
                case Error:
                    return decodeErrorMessage(payload);
                case PurgeAll:
                    return decodePurgeAll(payload);
                case Keepalive:
                    return decodeKeepalive(payload);
            }
        }
        throw new UnknownSxpMessageTypeException();
    }

    /**
     * Decode ErrorMessage from Byte Array
     *
     * @param payload Byte Array containing message
     * @return Decoded Error message
     */
    public static Notification decodeErrorMessage(byte[] payload) {
        ErrorMessageBuilder messageBuilder = new ErrorMessageBuilder();
        messageBuilder.setType(MessageType.Error);
        messageBuilder.setLength(MESSAGE_HEADER_LENGTH_LENGTH + MESSAGE_HEADER_TYPE_LENGTH + payload.length);
        messageBuilder.setPayload(payload);

        if (ArraysUtil.getBit(payload[0], 8) == 1) {
            messageBuilder.setErrorType(ErrorType.Extended);
            messageBuilder.setErrorCode(ErrorCode.forValue(payload[0] & 0x7F));
            messageBuilder.setErrorSubCode(ErrorSubCode.forValue(payload[1]));
            messageBuilder.setData(ArraysUtil.readBytes(payload, 2));
            messageBuilder.setInformation(
                    messageBuilder.getErrorCode() + " | " + messageBuilder.getErrorSubCode() + getInformation(
                            messageBuilder.getData()));
            return messageBuilder.build();
        }

        messageBuilder.setErrorType(ErrorType.Legacy);
        messageBuilder.setErrorCodeNonExtended(
                ErrorCodeNonExtended.forValue(ArraysUtil.bytes2int(ArraysUtil.readBytes(payload, 2, 2))));
        messageBuilder.setData(ArraysUtil.readBytes(payload, 4));
        messageBuilder.setInformation(
                messageBuilder.getErrorCodeNonExtended() + getInformation(messageBuilder.getData()));
        return messageBuilder.build();
    }

    /**
     * Decodes KeepAliveMessage from byte Array
     *
     * @param payload Byte Array containing message
     * @return Decoded KeepAlive message
     */
    public static Notification decodeKeepalive(byte[] payload) {
        KeepaliveMessageBuilder messageBuilder = new KeepaliveMessageBuilder();
        messageBuilder.setType(MessageType.Keepalive);
        messageBuilder.setLength(MESSAGE_HEADER_LENGTH_LENGTH + MESSAGE_HEADER_TYPE_LENGTH + payload.length);
        return messageBuilder.build();
    }

    /**
     * Decodes OpenMessage from Byte Array
     *
     * @param payload Byte Array containing message
     * @return Decoded Open message
     * @throws AddressLengthException   If address length of some attribute is incorrect
     * @throws AttributeLengthException If length of some attribute is incorrect
     * @throws UnknownNodeIdException   If NodeId isn't found or is incorrect
     * @throws UnknownPrefixException   If some attribute has incorrect or none Prefix
     * @throws TlvNotFoundException     If Tvl isn't found
     * @throws UnknownHostException     If some attribute have incorrect or none address
     */
    public static Notification decodeOpen(byte[] payload)
            throws AttributeLengthException, AddressLengthException, UnknownNodeIdException, UnknownPrefixException,
            TlvNotFoundException, UnknownHostException, AttributeVariantException {
        OpenMessageBuilder messageBuilder = new OpenMessageBuilder();
        messageBuilder.setType(MessageType.Open);
        messageBuilder.setLength(MESSAGE_HEADER_LENGTH_LENGTH + MESSAGE_HEADER_TYPE_LENGTH + payload.length);
        messageBuilder.setPayload(payload);

        Version version = extractVersion(payload);
        ConnectionMode nodeMode = ConnectionMode.forValue(ArraysUtil.bytes2int(ArraysUtil.readBytes(payload, 4, 4)));
        AttributeList attributes = AttributeList.decode(ArraysUtil.readBytes(payload, 8));

        messageBuilder.setVersion(version);
        messageBuilder.setSxpMode(nodeMode);
        messageBuilder.setAttribute(attributes);
        return messageBuilder.build();
    }

    /**
     * Decode OpenRespMessage from Byre Array
     *
     * @param payload Byte Array containing message
     * @return Decoded OpenResp message
     * @throws AddressLengthException   If address length of some attribute is incorrect
     * @throws AttributeLengthException If length of some attribute is incorrect
     * @throws UnknownNodeIdException   If NodeId isn't found or is incorrect
     * @throws UnknownPrefixException   If some attribute has incorrect or none Prefix
     * @throws TlvNotFoundException     If Tvl isn't found
     * @throws UnknownHostException     If some attribute have incorrect or none address
     */
    public static Notification decodeOpenResp(byte[] payload)
            throws AttributeLengthException, AddressLengthException, UnknownNodeIdException, UnknownPrefixException,
            TlvNotFoundException, UnknownHostException, ErrorMessageException, AttributeVariantException {
        OpenMessageBuilder messageBuilder = new OpenMessageBuilder();
        messageBuilder.setType(MessageType.OpenResp);
        messageBuilder.setLength(MESSAGE_HEADER_LENGTH_LENGTH + MESSAGE_HEADER_TYPE_LENGTH + payload.length);
        messageBuilder.setPayload(payload);

        Version version = extractVersion(payload);
        ConnectionMode nodeMode = ConnectionMode.forValue(ArraysUtil.bytes2int(ArraysUtil.readBytes(payload, 4, 4)));
        AttributeList attributes = AttributeList.decode(ArraysUtil.readBytes(payload, 8));

        messageBuilder.setVersion(version);
        messageBuilder.setSxpMode(nodeMode);
        messageBuilder.setAttribute(attributes);
        return messageBuilder.build();
    }

    /**
     * @param payload Byte Array containing message
     * @return Gets Version from message header
     */
    public static Version extractVersion(final byte[] payload) {
        return Version.forValue(ArraysUtil.bytes2int(ArraysUtil.readBytes(payload, 0, 4)));
    }

    /**
     * Decodes PurgeAll message fom provided Byte Array
     *
     * @param payload Byte Array containing message
     * @return Decoded PurgeAll message
     */
    public static Notification decodePurgeAll(byte[] payload) {
        PurgeAllMessageBuilder messageBuilder = new PurgeAllMessageBuilder();
        messageBuilder.setType(MessageType.PurgeAll);
        messageBuilder.setLength(MESSAGE_HEADER_LENGTH_LENGTH + MESSAGE_HEADER_TYPE_LENGTH + payload.length);
        return messageBuilder.build();
    }

    /**
     * Decode UpdateMessage from provided Byte Array
     *
     * @param payload Byte Array containing message
     * @return Notification with decoded UpdateMessage
     * @throws AddressLengthException   If address length of some attribute is incorrect
     * @throws AttributeLengthException If length of some attribute is incorrect
     * @throws UnknownNodeIdException   If NodeId isn't found or is incorrect
     * @throws UnknownPrefixException   If some attribute has incorrect or none Prefix
     * @throws TlvNotFoundException     If Tvl isn't found
     * @throws UnknownHostException     If some attribute have incorrect or none address
     */
    public static Notification decodeUpdate(byte[] payload)
            throws AttributeLengthException, AddressLengthException, UnknownNodeIdException, UnknownPrefixException,
            TlvNotFoundException, UnknownHostException, AttributeVariantException {
        UpdateMessageBuilder messageBuilder = new UpdateMessageBuilder();
        messageBuilder.setType(MessageType.Update);
        messageBuilder.setLength(MESSAGE_HEADER_LENGTH_LENGTH + MESSAGE_HEADER_TYPE_LENGTH + payload.length);
        messageBuilder.setPayload(payload);

        messageBuilder.setAttribute(AttributeList.decode(payload));
        return messageBuilder.build();
    }

    /**
     * @param data Data to be analyzed
     * @return Gets each element in one String
     */
    private static String getInformation(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (byte aData : data) {
            result.append(aData).append(" ");
        }
        return result.toString().trim();
    }

    /**
     * Generate Message header according message type and create ByteBuff,
     * that includes generated header and provided payload
     *
     * @param messageType Type of message header to be generated
     * @param payload     Data to be included into Message
     * @return ByteBuf representation of message
     */
    protected static ByteBuf getMessage(MessageType messageType, byte[] payload) {
        byte[] header = getMessageHeader(messageType, payload.length);
        int messageLength = header.length + payload.length;
        ByteBuf message = PooledByteBufAllocator.DEFAULT.buffer(messageLength, messageLength);
        message.writeBytes(header);
        message.writeBytes(payload);
        return message;
    }

    /**
     * Generate message header using provided values
     *
     * @param messageType   Type of header
     * @param payloadLength Length of data
     * @return Byte array representing message header
     */
    private static byte[] getMessageHeader(MessageType messageType, int payloadLength) {
        return ArraysUtil.combine(
                ArraysUtil.int2bytes(MESSAGE_HEADER_LENGTH_LENGTH + MESSAGE_HEADER_TYPE_LENGTH + payloadLength),
                new byte[] {0x00, 0x00, 0x00, (byte) messageType.getIntValue()});
    }

    /**
     * @param version Version to be checked
     * @return If is Version 1/2/3
     */
    protected static boolean isLegacy(Version version) {
        return version.equals(Version.Version1) || version.equals(Version.Version2) || version.equals(Version.Version3);

    }

    /**
     * Decode received message into specific message type
     *
     * @param version Version used for decoding
     * @param request ByteBuf containing data to be decoded
     * @return Decoded message
     * @throws ErrorMessageException          If version Mismatch occurs
     * @throws UnknownSxpMessageTypeException If data contains unsupported message
     * @throws AddressLengthException         If address length of some attribute is incorrect
     * @throws UnknownHostException           If some attribute have incorrect or none address
     * @throws AttributeLengthException       If length of some attribute is incorrect
     * @throws TlvNotFoundException           If Tvl isn't found
     * @throws UnknownPrefixException         If some attribute has incorrect or none Prefix
     * @throws UnknownNodeIdException         If NodeId isn't found or is incorrect
     */
    public static Notification parse(Version version, ByteBuf request)
            throws ErrorMessageException, UnknownSxpMessageTypeException, AddressLengthException, UnknownHostException,
            AttributeLengthException, TlvNotFoundException, UnknownPrefixException, UnknownNodeIdException,
            AttributeVariantException {
        request.resetReaderIndex();
        byte[] headerLength, headerType, payload;
        int messageLength;
        try {
            headerLength = new byte[MESSAGE_HEADER_LENGTH_LENGTH];
            request = request.readBytes(headerLength);

            headerType = new byte[MESSAGE_HEADER_TYPE_LENGTH];
            request = request.readBytes(headerType);

            messageLength = ArraysUtil.bytes2int(headerLength);
            int
                    payloadLength =
                    messageLength - (MESSAGE_HEADER_LENGTH_LENGTH + MESSAGE_HEADER_TYPE_LENGTH);

            payload = new byte[payloadLength];
            request = request.readBytes(payload);
        } catch (IndexOutOfBoundsException | NegativeArraySizeException e) {
            throw new ErrorMessageException(ErrorCode.MessageHeaderError, e);
        }

        validate(headerLength.length + headerType.length, payload.length, messageLength);
        return decode(version, headerType, payload);
    }

    /**
     * @param message Notification to be proceed
     * @return Gets String representation of Byte Array
     */
    public static String toString(byte[] message) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < message.length; i++) {
            if (i == MESSAGE_HEADER_LENGTH_LENGTH + MESSAGE_HEADER_TYPE_LENGTH) {
                result.append("| ");
            }
            result.append((0xFF & message[i])).append(" ");
        }
        return result.toString();
    }

    /**
     * @param message Notification to be proceed
     * @return Gets String representation of ByteBuf
     */
    public static String toString(ByteBuf message) {
        message.resetReaderIndex();
        byte[] _message = new byte[message.readableBytes()];
        message.readBytes(_message);
        message.resetReaderIndex();
        return toString(_message);
    }

    /**
     * @param message Notification to be proceed
     * @return Gets String representation of Notification
     */
    public static String toString(Notification message) {
        String result = "Unrecognized";
        byte[] _message = new byte[0];
        if (message instanceof SxpHeader) {
            MessageType messageType = ((SxpHeader) message).getType();
            if (messageType.equals(MessageType.OpenResp)) {
                result = "RESP";
            } else {
                result = messageType.toString().toUpperCase(Locale.ROOT);
            }

            byte[] length = ArraysUtil.int2bytes(((SxpHeader) message).getLength());
            byte[] type = ArraysUtil.int2bytes(messageType.getIntValue());
            _message = ArraysUtil.combine(_message, length, type);
        }
        if (message instanceof SxpPayload) {
            _message = ArraysUtil.combine(_message, ((SxpPayload) message).getPayload());
        }
        return result + " " + toString(_message);
    }

    /**
     * Check if length of message is correct
     *
     * @param headerLength  Length of header
     * @param payloadLength Length od data
     * @param messageLength Total length
     * @throws ErrorMessageException If lengths are incorrect
     */
    private static void validate(int headerLength, int payloadLength, int messageLength) throws ErrorMessageException {
        if (headerLength + payloadLength > MESSAGE_LENGTH_MAX) {
            throw new ErrorMessageException(ErrorCode.MessageHeaderError,
                    new Exception("Message maximum length exceeded"));

        } else if (headerLength + payloadLength != messageLength) {
            throw new ErrorMessageException(ErrorCode.MessageHeaderError,
                    new Exception("Message incorporated length is not consistent"));
        }
    }

    /**
     * Decode Capabilities received from remote peer
     *
     * @param message Message containing capabilities
     * @return List of capabilities
     * @throws AttributeNotFoundException If no Capabilities were found
     */
    public static List<CapabilityType> decodeCapabilities(OpenMessage message) throws AttributeNotFoundException {
        CapabilitiesAttribute
                capabilitiesAttribute =
                (CapabilitiesAttribute) AttributeList.get(Preconditions.checkNotNull(message).getAttribute(),
                        AttributeType.Capabilities);
        return new ArrayList<>(
                Collections2.transform(capabilitiesAttribute.getCapabilitiesAttributes().getCapabilities(),
                        CapabilityAttributeFields::getCode));
    }
}
