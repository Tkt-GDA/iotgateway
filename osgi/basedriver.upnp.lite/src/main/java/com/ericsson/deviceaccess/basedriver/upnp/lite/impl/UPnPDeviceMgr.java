/*
 * Copyright Ericsson AB 2011-2014. All Rights Reserved.
 *
 * The contents of this file are subject to the Lesser GNU Public License,
 *  (the "License"), either version 2.1 of the License, or
 * (at your option) any later version.; you may not use this file except in
 * compliance with the License. You should have received a copy of the
 * License along with this software. If not, it can be
 * retrieved online at https://www.gnu.org/licenses/lgpl.html. Moreover
 * it could also be requested from Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * BECAUSE THE LIBRARY IS LICENSED FREE OF CHARGE, THERE IS NO
 * WARRANTY FOR THE LIBRARY, TO THE EXTENT PERMITTED BY APPLICABLE LAW.
 * EXCEPT WHEN OTHERWISE STATED IN WRITING THE COPYRIGHT HOLDERS AND/OR
 * OTHER PARTIES PROVIDE THE LIBRARY "AS IS" WITHOUT WARRANTY OF ANY KIND,

 * EITHER EXPRESSED OR IMPLIED, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE. THE ENTIRE RISK AS TO THE QUALITY AND PERFORMANCE OF THE
 * LIBRARY IS WITH YOU. SHOULD THE LIBRARY PROVE DEFECTIVE,
 * YOU ASSUME THE COST OF ALL NECESSARY SERVICING, REPAIR OR CORRECTION.
 *
 * IN NO EVENT UNLESS REQUIRED BY APPLICABLE LAW OR AGREED TO IN WRITING
 * WILL ANY COPYRIGHT HOLDER, OR ANY OTHER PARTY WHO MAY MODIFY AND/OR
 * REDISTRIBUTE THE LIBRARY AS PERMITTED ABOVE, BE LIABLE TO YOU FOR
 * DAMAGES, INCLUDING ANY GENERAL, SPECIAL, INCIDENTAL OR CONSEQUENTIAL
 * DAMAGES ARISING OUT OF THE USE OR INABILITY TO USE THE LIBRARY
 * (INCLUDING BUT NOT LIMITED TO LOSS OF DATA OR DATA BEING RENDERED
 * INACCURATE OR LOSSES SUSTAINED BY YOU OR THIRD PARTIES OR A FAILURE
 * OF THE LIBRARY TO OPERATE WITH ANY OTHER SOFTWARE), EVEN IF SUCH
 * HOLDER OR OTHER PARTY HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.ericsson.deviceaccess.basedriver.upnp.lite.impl;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UPnPDeviceMgr {

    private static final Logger log = LoggerFactory.getLogger(UPnPDeviceMgr.class);
    private static final String SSDP_ADDRESS = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final int SEARCH_WAIT = 60;
    private static final int SEARCH_INTERVAL = 60 * 1000;

    private final Map<String, UPnPDeviceImpl> m_deviceList = new ConcurrentHashMap<>();
    private List m_searchThreads = new ArrayList<>();
    private final Object m_searchThreadSyncObject = new Object();
    private Thread m_listenThread = null;
    private final Object m_listenThreadSyncObjet = new Object();
    private boolean shutdown;
    private MulticastSocket m_listenSocket = null;
    private String lanIP = null;
    private final BundleContext context;
    private UPnPEventHandler eventHandler = null;

    public UPnPDeviceMgr(BundleContext context) {
        this.context = context;

        // toUppercase issue with Turkish Language
        Locale.setDefault(Locale.ENGLISH);
    }

    public void start(String lanIP) {
        this.lanIP = lanIP;

        shutdown = false;

        // Start event handler for incoming device registrations/deregistrations and UPnP events
        eventHandler = new UPnPEventHandler(context);
        eventHandler.start();

        startListenOnMulticast();

        // Start a search thread on all IP addresses of the local machine
        try {
            // Make it possible to bind to a specific interface
            if (lanIP != null) {
                m_searchThreads.add(startSearchThread(new InetSocketAddress(lanIP, 0)));
            } else {
                for (Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces(); interfaces.hasMoreElements();) {
                    NetworkInterface ni = interfaces.nextElement();
                    for (Enumeration<InetAddress> inetAddresses = ni.getInetAddresses(); inetAddresses.hasMoreElements();) {
                        InetAddress ia = inetAddresses.nextElement();
                        if (ia != null && !ia.getHostAddress().contains(":")) {
                            log.debug(ni.getDisplayName() + ", " + ia.getHostAddress());
                            m_searchThreads.add(startSearchThread(new InetSocketAddress(ia.getHostAddress(), 0)));
                        }
                    }
                }
            }
        } catch (SocketException e) {
            log.warn(e.getMessage(), e);
        }
    }

    public void stop() {
        stopListenOnMulticast();
        eventHandler.stop();
        shutdown = true;
    }

    public Map<String, UPnPDeviceImpl> getDevices() {
        return m_deviceList.values()
                .stream()
                .filter(d -> d.isReady())
                .collect(Collectors.toMap(d -> d.getUuid(), Function.identity()));
    }

    // Send SSDP M-SEARCH request to 239.255.255.255:1900 and listen for responses
    private Thread startSearchThread(final SocketAddress bindaddr) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                DatagramSocket socket;
                try {
                    socket = new DatagramSocket(bindaddr);
                } catch (SocketException e) {
                    log.warn(e.getMessage(), e);
                    return;
                }

                while (!shutdown) {
                    try {
                        // Send the M-SEARCH messages from all interfaces
                        try {
                            String msearch = "M-SEARCH * HTTP/1.1\r\n"
                                    + "HOST: " + SSDP_ADDRESS + ":" + SSDP_PORT + "\r\n"
                                    + "MAN: \"ssdp:discover\"\r\n"
                                    + "MX: " + Integer.toString(SEARCH_WAIT) + "\r\n"
                                    + "ST: upnp:rootdevice\r\n"
                                    + "Content-Length: 0\r\n\r\n";

                            DatagramPacket msearchDp = new DatagramPacket(msearch.getBytes(), 0, msearch.getBytes().length, InetAddress.getByName(SSDP_ADDRESS), SSDP_PORT);

                            // Send multicast M-SEARCH message from this network interfaces
                            socket.send(msearchDp);
                        } catch (IOException e) {
                            log.warn(e.getMessage(), e);
                        }
                        byte[] buf = new byte[20000];
                        DatagramPacket dp = new DatagramPacket(buf, buf.length);

                        // Receive the responses for SEARCH_WAIT seconds
                        long startTime = System.currentTimeMillis();
                        while (System.currentTimeMillis() < startTime + SEARCH_WAIT * 1000 && !shutdown) {
                            try {
                                dp.setLength(buf.length);
                                socket.receive(dp);
                                String localIp = socket.getLocalAddress().getHostAddress();
                                UPnPDeviceImpl device = UPnPMessageParser.parseSearchResponse(context, localIp, eventHandler, new String(dp.getData(), 0, dp.getLength()));
                                if (device == null) {
                                    //log.debug(this, "Ignoring invalid SSDP response: " + new String(dp.getData(), 0, dp.getLength()));
                                } else {
                                    UPnPDeviceImpl d = getDevice(device.getUuid());
                                    if (d == null) {
                                        addUPnPDeviceInstance(device);
                                    } else {
                                        d.setAlive();
                                    }
                                }
                            } catch (SocketTimeoutException socketException) {
                            } catch (Exception e2) {
                                //e2.printStackTrace();
                            }
                        }

                        // Remove stale devices
                        getDevices()
                                .values()
                                .stream()
                                .filter(device -> !device.isAlive())
                                .forEach(device -> removeUPnPDeviceInstance(device));
                    } catch (Exception e) {
                        log.warn("Got exception in search thread", e);
                    }
                }
            }
        };
        thread.start();
        return thread;
    }

    private void startListenOnMulticast() {
        m_listenThread = new Thread() {
            @Override
            public void run() {
                try {
                    m_listenSocket = new MulticastSocket(SSDP_PORT);
                    m_listenSocket.joinGroup(InetAddress.getByName(SSDP_ADDRESS));
                    m_listenSocket.setBroadcast(true);

                    byte[] buf = new byte[m_listenSocket.getReceiveBufferSize()];
                    DatagramPacket dp = new DatagramPacket(buf, buf.length);

                    while (!shutdown) {
                        try {
                            dp.setLength(buf.length);
                            m_listenSocket.receive(dp);

                            UPnPDeviceImpl device = UPnPMessageParser.parseNotifyMessage(context, new String(dp.getData(), 0, dp.getLength(), StandardCharsets.UTF_8), eventHandler);
                            if (device == null) {
                                continue;
                            }
                            if (device.isAlive()) {
                                UPnPDeviceImpl oldDevice = getDevice(device.getUuid());
                                if (oldDevice == null) {
                                    addUPnPDeviceInstance(device);
                                    // Add this device if it's new
                                    // TODO: Can use this since we can figure out which interface it's coming from: addUPnPDeviceInstance(device);
                                } else {
                                    // Otherwise update it's timestamp
                                    oldDevice.setAlive();
                                }
                            } else {
                                // This was a byebye message so remove the device
                                removeUPnPDeviceInstance(device);
                            }
                        } catch (Exception e) {
                            log.warn("Error when receiving message", e);
                        }
                    }
                } catch (IOException e) {
                    log.warn("Error when handling socket (local:"
                            + m_listenSocket.getLocalAddress() + ", remote:"
                            + m_listenSocket.getInetAddress() + ")", e);
                } finally {
                    if (m_listenSocket != null) {
                        m_listenSocket.close();
                    }
                }
            }
        };

        m_listenThread.start();
    }

    private void stopListenOnMulticast() {
        if (m_listenSocket != null) {
            try {
                m_listenSocket.close();
            } catch (Exception e) {
                log.warn("Got exception when closing listen socket", e);
            }
        }
    }

    private UPnPDeviceImpl getDevice(String uuid) {
        return m_deviceList.get(uuid.toLowerCase());
    }

    private void removeUPnPDeviceInstance(UPnPDeviceImpl device) {
        m_deviceList.remove(device.getUuid().toLowerCase());
        device.stop();
    }

    private void addUPnPDeviceInstance(UPnPDeviceImpl device) {
        log.debug("Add device which broadcasted itself: " + device.getUuid());
        m_deviceList.put(device.getUuid().toLowerCase(), device);

        try {
            device.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
