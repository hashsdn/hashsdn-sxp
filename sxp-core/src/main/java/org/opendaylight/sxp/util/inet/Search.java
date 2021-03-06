/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.sxp.util.inet;

import com.google.common.base.Preconditions;
import com.google.common.net.InetAddresses;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.opendaylight.sxp.util.exception.connection.NoNetworkInterfacesException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.database.rev160308.SxpBindingFields;
import org.opendaylight.yang.gen.v1.urn.opendaylight.sxp.database.rev160308.master.database.fields.MasterDatabaseBindingBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Search {

    private static final Logger LOG = LoggerFactory.getLogger(Search.class.getName());
    private static int bestAddresPointer = 1;

    private Search() {
    }

    /**
     * Gets Local address selected by heuristic
     *
     * @return InetAddress that isn't virtual and is Up
     * @throws NoNetworkInterfacesException If there is no NetworkInterface available
     */
    public static InetAddress getBestLocalDeviceAddress() throws NoNetworkInterfacesException {

        List<InetAddress> inetAddresses = new ArrayList<>();
        List<NetworkInterface> networkInterfaces;
        try {
            networkInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface networkInterface : networkInterfaces) {
                if (networkInterface.isUp() && !networkInterface.isVirtual()) {
                    LOG.debug("[{}] {}", networkInterface.getName(), networkInterface.getDisplayName());
                    inetAddresses.addAll(Collections.list(networkInterface.getInetAddresses()));
                }
            }
        } catch (SocketException e) {
            throw new NoNetworkInterfacesException();
        }
        inetAddresses.sort(new InetAddressComparator());
        return inetAddresses.get(inetAddresses.size() > bestAddresPointer + 1 ?
                inetAddresses.size() - bestAddresPointer++ : inetAddresses.size() - (bestAddresPointer = 1));
    }

    /**
     * Modify provided List that it will remove all subnet prefixes,
     * and replace them by their expanded substitution.
     *
     * @param bindings List of bindings that will be expanded
     * @param quantity Expansion limit
     * @param <T>      SxpBindingFields ancestors
     * @return List with subnet prefixes replaced by their representatives
     */
    public static <T extends SxpBindingFields> List<T> expandBindings(List<T> bindings, int quantity) {
        if (quantity > 0 && bindings != null && !bindings.isEmpty()) {
            List<T> toAdd = new ArrayList<>();
            bindings.removeIf(b -> {
                int len = IpPrefixConv.getPrefixLength(b.getIpPrefix());
                return (len != 32 && len != 128) && toAdd.addAll(expandBinding(b, quantity));
            });
            bindings.addAll(toAdd);
        }
        return bindings;
    }

    /**
     * Expands specified Binding into subnet.
     * Amount of subnet that will be expanded is limited by quantity.
     *
     * @param binding  Bindings that will be expanded
     * @param quantity Max number to limit the expansion
     * @return List of bindings that were created by expansion into subnet
     */
    public static <T extends SxpBindingFields> List<T> expandBinding(T binding, int quantity) {
        final MasterDatabaseBindingBuilder bindingBuilder = new MasterDatabaseBindingBuilder(binding);
        return expandPrefix(binding.getIpPrefix(), quantity).map(inetAddress -> {
            if (binding.getIpPrefix().getIpv4Prefix() != null) {
                bindingBuilder.setIpPrefix(new IpPrefix(IetfInetUtil.INSTANCE.ipv4PrefixFor(inetAddress, 32)));
            } else {
                bindingBuilder.setIpPrefix(new IpPrefix(IetfInetUtil.INSTANCE.ipv6PrefixFor(inetAddress, 128)));
            }
            return (T) bindingBuilder.build();
        }).collect(Collectors.toList());
    }

    /**
     * @param prefix IpPrefix to be expanded
     * @return Stream of InetAddresses created by expansion
     */
    public static Stream<InetAddress> expandPrefix(final IpPrefix prefix) {
        return expandPrefix(prefix, Long.MAX_VALUE);
    }

    /**
     * @param prefix IpPrefix to be expanded
     * @param limit  Limit of expansion
     * @return Stream of InetAddresses created by expansion
     */
    public static Stream<InetAddress> expandPrefix(final IpPrefix prefix, final long limit) {
        int prefixLength = IpPrefixConv.getPrefixLength(prefix),
                addressFamily =
                        prefix.getIpv4Prefix() != null ? 32 : 128;
        byte[] address = InetAddresses.forString(IpPrefixConv.toString(prefix).split("/")[0]).getAddress(), address_;
        BitSet bitSet = BitSet.valueOf(address);
        if (bitSet.length() >= prefixLength)
            bitSet.clear(prefixLength, bitSet.length());
        address_ = bitSet.toByteArray();
        for (int i = 0; i < address.length; i++) {
            address[i] = i < address_.length ? address_[i] : 0;
        }

        addressFamily -= Integer.parseInt(IpPrefixConv.toString(prefix).split("/")[1]);
        return Stream.iterate(IetfInetUtil.INSTANCE.inetAddressFor(IetfInetUtil.INSTANCE.ipAddressFor(address)),
                InetAddresses::increment)
                .limit(Math.min(addressFamily > 0 ? (long) Math.pow(2, addressFamily) : 0, limit));
    }

    /**
     * @param address IpAddress to be represented by string
     * @return String representation of IpAddress
     */
    public static String getAddress(
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress address) {
        Preconditions.checkNotNull(address);
        if (address.getIpv4Address() != null) {
            return address.getIpv4Address().getValue();
        } else if (address.getIpv6Address() != null) {
            return address.getIpv6Address().getValue();
        }
        throw new IllegalArgumentException("Address " + address + " has illegal value.");
    }
}
