/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.sxp.controller.listeners.sublisteners;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.sxp.controller.core.DatastoreAccess;
import org.opendaylight.sxp.controller.listeners.NodeIdentityListener;
import org.opendaylight.sxp.core.Configuration;
import org.opendaylight.sxp.core.SxpConnection;
import org.opendaylight.sxp.core.SxpNode;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.node.rev160308.SxpNodeIdentity;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.node.rev160308.network.topology.topology.node.SxpDomains;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.node.rev160308.network.topology.topology.node.sxp.domains.SxpDomain;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.node.rev160308.network.topology.topology.node.sxp.domains.SxpDomainKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.node.rev160308.sxp.connections.fields.Connections;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.node.rev160308.sxp.connections.fields.connections.Connection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.node.rev160308.sxp.connections.fields.connections.ConnectionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.protocol.rev141002.ConnectionState;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Configuration.class, DatastoreAccess.class})
public class ConnectionsListenerTest {

    private ConnectionsListener identityListener;
    private DatastoreAccess datastoreAccess;
    private SxpNode sxpNode;
    private SxpConnection connection;

    @Before
    public void setUp() throws Exception {
        datastoreAccess = PowerMockito.mock(DatastoreAccess.class);
        identityListener = new ConnectionsListener(datastoreAccess);
        sxpNode = mock(SxpNode.class);
        connection = mock(SxpConnection.class);
        when(sxpNode.getConnection(any(SocketAddress.class))).thenReturn(connection);
        PowerMockito.mockStatic(Configuration.class);
        PowerMockito.when(Configuration.getRegisteredNode(anyString())).thenReturn(sxpNode);
        PowerMockito.when(Configuration.register(any(SxpNode.class))).thenReturn(sxpNode);
        PowerMockito.when(Configuration.unRegister(anyString())).thenReturn(sxpNode);
    }

    private DataObjectModification<Connection> getObjectModification(
            DataObjectModification.ModificationType modificationType, Connection before, Connection after) {
        DataObjectModification<Connection> modification = mock(DataObjectModification.class);
        when(modification.getModificationType()).thenReturn(modificationType);
        when(modification.getDataAfter()).thenReturn(after);
        when(modification.getDataBefore()).thenReturn(before);
        when(modification.getDataType()).thenReturn(Connection.class);
        return modification;
    }

    private DataObjectModification<Connections> getObjectModification(DataObjectModification<Connection> change) {
        DataObjectModification<Connections> modification = mock(DataObjectModification.class);
        when(modification.getModificationType()).thenReturn(DataObjectModification.ModificationType.WRITE);
        when(modification.getDataType()).thenReturn(Connections.class);
        when(modification.getModifiedChildren()).thenReturn(Collections.singletonList(change));
        return modification;
    }

    private InstanceIdentifier<SxpDomain> getIdentifier() {
        return NodeIdentityListener.SUBSCRIBED_PATH.child(Node.class, new NodeKey(new NodeId("0.0.0.0")))
                .augmentation(SxpNodeIdentity.class)
                .child(SxpDomains.class)
                .child(SxpDomain.class, new SxpDomainKey("GLOBAL"));
    }

    private Connection getConnection(String ip, ConnectionState state, int port) {
        ConnectionBuilder builder = new ConnectionBuilder();
        builder.setTcpPort(new PortNumber(port));
        builder.setState(state);
        builder.setPeerAddress(new IpAddress(ip.toCharArray()));
        return builder.build();
    }

    @Test
    public void testHandleOperational_1() throws Exception {
        identityListener.handleOperational(getObjectModification(DataObjectModification.ModificationType.WRITE, null,
                getConnection("1.1.1.1", ConnectionState.Off, 56)), getIdentifier(), sxpNode);
        verify(sxpNode).addConnection(any(Connection.class), anyString());
    }

    @Test
    public void testHandleOperational_2() throws Exception {
        identityListener.handleOperational(getObjectModification(DataObjectModification.ModificationType.WRITE,
                getConnection("1.1.1.1", ConnectionState.Off, 56), null), getIdentifier(), sxpNode);
        verify(sxpNode).removeConnection(any(InetSocketAddress.class));
    }

    @Test
    public void testHandleOperational_3() throws Exception {
        identityListener.handleOperational(getObjectModification(DataObjectModification.ModificationType.DELETE,
                getConnection("1.1.1.1", ConnectionState.Off, 56), null), getIdentifier(), sxpNode);
        verify(sxpNode).removeConnection(any(InetSocketAddress.class));
    }

    @Test
    public void testHandleOperational_4() throws Exception {
        identityListener.handleOperational(
                getObjectModification(DataObjectModification.ModificationType.SUBTREE_MODIFIED,
                        getConnection("1.1.1.1", ConnectionState.On, 56),
                        getConnection("1.1.1.1", ConnectionState.On, 57)), getIdentifier(), sxpNode);
        verify(connection).shutdown();
    }

    @Test
    public void testHandleOperational_5() throws Exception {
        identityListener.handleOperational(
                getObjectModification(DataObjectModification.ModificationType.SUBTREE_MODIFIED,
                        getConnection("1.1.1.1", ConnectionState.On, 56),
                        getConnection("1.1.1.2", ConnectionState.On, 56)), getIdentifier(), sxpNode);
        verify(connection).shutdown();
    }

    @Test
    public void testGetIdentifier() throws Exception {
        assertNotNull(identityListener.getIdentifier(new ConnectionBuilder().setTcpPort(new PortNumber(64))
                .setPeerAddress(new IpAddress("1.1.1.1".toCharArray()))
                .build(), getIdentifier()));

        assertTrue(identityListener.getIdentifier(new ConnectionBuilder().setTcpPort(new PortNumber(64))
                .setPeerAddress(new IpAddress("1.1.1.1".toCharArray()))
                .build(), getIdentifier()).getTargetType().equals(Connection.class));
    }

    @Test
    public void testHandleChange() throws Exception {
        identityListener.handleChange(Collections.singletonList(getObjectModification(
                getObjectModification(DataObjectModification.ModificationType.WRITE,
                        getConnection("1.1.1.2", ConnectionState.On, 57),
                        getConnection("1.1.1.2", ConnectionState.On, 56)))), LogicalDatastoreType.OPERATIONAL,
                getIdentifier());
        verify(datastoreAccess, never()).putSynchronous(any(InstanceIdentifier.class), any(DataObject.class),
                eq(LogicalDatastoreType.OPERATIONAL));
        verify(datastoreAccess, never()).mergeSynchronous(any(InstanceIdentifier.class), any(DataObject.class),
                eq(LogicalDatastoreType.OPERATIONAL));
        verify(datastoreAccess, never()).checkAndDelete(any(InstanceIdentifier.class),
                eq(LogicalDatastoreType.OPERATIONAL));

        identityListener.handleChange(Collections.singletonList(getObjectModification(
                getObjectModification(DataObjectModification.ModificationType.WRITE, null,
                        getConnection("1.1.1.2", ConnectionState.On, 56)))), LogicalDatastoreType.CONFIGURATION,
                getIdentifier());
        verify(datastoreAccess).checkAndPut(any(InstanceIdentifier.class), any(DataObject.class),
                eq(LogicalDatastoreType.OPERATIONAL), eq(false));

        identityListener.handleChange(Collections.singletonList(getObjectModification(
                getObjectModification(DataObjectModification.ModificationType.WRITE,
                        getConnection("1.1.1.2", ConnectionState.On, 57),
                        getConnection("1.1.1.2", ConnectionState.On, 56)))), LogicalDatastoreType.CONFIGURATION,
                getIdentifier());
        verify(datastoreAccess).merge(any(InstanceIdentifier.class), any(DataObject.class),
                eq(LogicalDatastoreType.OPERATIONAL));

        identityListener.handleChange(Collections.singletonList(getObjectModification(
                getObjectModification(DataObjectModification.ModificationType.DELETE,
                        getConnection("1.1.1.2", ConnectionState.On, 57),
                        getConnection("1.1.1.2", ConnectionState.On, 56)))), LogicalDatastoreType.CONFIGURATION,
                getIdentifier());
        verify(datastoreAccess).checkAndDelete(any(InstanceIdentifier.class), eq(LogicalDatastoreType.OPERATIONAL));
    }

    @Test
    public void testGetModifications() throws Exception {
        assertNotNull(identityListener.getObjectModifications(null));
        assertNotNull(identityListener.getObjectModifications(mock(DataObjectModification.class)));
        assertNotNull(identityListener.getModifications(null));
        DataTreeModification dtm = mock(DataTreeModification.class);
        when(dtm.getRootNode()).thenReturn(mock(DataObjectModification.class));
        assertNotNull(identityListener.getModifications(dtm));
    }
}
