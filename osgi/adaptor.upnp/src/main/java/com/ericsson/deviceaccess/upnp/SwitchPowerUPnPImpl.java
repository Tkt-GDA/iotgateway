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
package com.ericsson.deviceaccess.upnp;

import com.ericsson.common.util.LegacyUtil;
import com.ericsson.deviceaccess.api.genericdevice.GDException;
import com.ericsson.deviceaccess.spi.service.homeautomation.power.SwitchPowerBase;
import java.util.HashMap;
import java.util.Map;
import org.osgi.service.upnp.UPnPAction;
import org.osgi.service.upnp.UPnPDevice;
import org.osgi.service.upnp.UPnPException;
import org.osgi.service.upnp.UPnPService;
import org.slf4j.Logger;

public class SwitchPowerUPnPImpl extends SwitchPowerBase implements UPnPDeviceAgent.UpdatePropertyInterface {

    private static UPnPAction getUPnPAction(UPnPDevice device, String actionName) throws UPnPException {
        for (UPnPService service : device.getServices()) {
            UPnPAction action = service.getAction(actionName);
            if (action != null) {
                return action;
            }
        }
        throw new UPnPException(UPnPException.INVALID_ACTION, "No such action supported " + actionName);
    }

    private final UPnPDevice upnpDev;
    private final Logger logger;

    public SwitchPowerUPnPImpl(UPnPDevice upnpDev, UPnPService upnpService, Logger logger) {
        this.upnpDev = upnpDev;
        this.logger = logger;
    }

    @Override
    public void executeSetTarget(int target) throws GDException {
        try {
            UPnPAction action = SwitchPowerUPnPImpl.getUPnPAction(this.upnpDev, "SetTarget");
            Map<String, Object> args = new HashMap<>();
            args.put("newTargetValue", target == 1 ? "True" : "False");
            action.invoke(LegacyUtil.toDictionary(args));
        } catch (UPnPException ex) {
            logger.error("Exception: " + ex);
        } catch (Exception ex) {
            logger.error("Exception: " + ex);
        }

    }

    // @Override
    @Override
    protected void refreshProperties() {
        // TODO Auto-generated method stub

    }

    // @Override
    @Override
    public void updateProperty(String name, Object value) {
        logger.debug("updateProperty(" + name + ")");
        if ("Status".equalsIgnoreCase(name)) {
            if (value instanceof Boolean) {
                logger.debug("updateCurrentTarget(" + value + ")");
                this.updateCurrentTarget(((Boolean) value) ? 1 : 0);
            }
        } else {
            // NOP
        }
    }

}
