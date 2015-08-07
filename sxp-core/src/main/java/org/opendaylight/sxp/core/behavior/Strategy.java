/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.sxp.core.behavior;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.opendaylight.sxp.core.SxpConnection;
import org.opendaylight.sxp.core.SxpNode;
import org.opendaylight.sxp.util.exception.ErrorMessageReceivedException;
import org.opendaylight.sxp.util.exception.message.ErrorMessageException;
import org.opendaylight.sxp.util.exception.message.UpdateMessageCompositionException;
import org.opendaylight.sxp.util.exception.message.UpdateMessageConnectionStateException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.node.rev141002.sxp.databases.fields.MasterDatabase;
import org.opendaylight.yangtools.yang.binding.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SXP supports various versions. The details of what is supported in each of
 * the version follows:
 *
 * <pre>
 * +-----+----------+----------+------------+-----------+--------------+
 * | Ver | IPv4     | IPv6     | Subnet     | Loop      | SXP          |
 * |     | Bindings | Bindings | Binding    | Detection | Capability   |
 * |     |          |          | Expansion  |           | Exchange     |
 * +-----+----------+----------+------------+-----------+--------------+
 * | 1   | Yes      | No       | No         | No        | No           |
 * | 2   | Yes      | Yes      | No         | No        | No           |
 * | 3   | Yes      | Yes      | Yes        | No        | No           |
 * | 4   | Yes      | Yes      | Yes        | Yes       | Yes          |
 * +-----+----------+----------+------------+-----------+--------------+
 * </pre>
 */
public interface Strategy {

        Logger LOG = LoggerFactory.getLogger(Strategy.class.getName());

        SxpNode getOwner();

        void onChannelActivation(ChannelHandlerContext ctx, SxpConnection connection);

        void onChannelInactivation(ChannelHandlerContext ctx, SxpConnection connection);

        void onException(ChannelHandlerContext ctx, SxpConnection connection);

        void onInputMessage(ChannelHandlerContext ctx, SxpConnection connection, Notification message)
                throws ErrorMessageException, ErrorMessageReceivedException, UpdateMessageConnectionStateException;

        Notification onParseInput(ByteBuf request) throws ErrorMessageException;

        ByteBuf onUpdateMessage(SxpConnection connection, MasterDatabase masterDatabase)
                throws UpdateMessageCompositionException;
}
