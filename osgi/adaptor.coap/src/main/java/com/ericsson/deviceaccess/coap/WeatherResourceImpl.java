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
package com.ericsson.deviceaccess.coap;

/**
 * WeatherResourceImpl
 *
 * This class provides the actual implementation of the WeatherResource Service Schema in that it retrieves 
 * the weather report via the GenericDeviceImpl object.
 */

import com.ericsson.deviceaccess.api.GenericDeviceActionContext;
import com.ericsson.deviceaccess.api.GenericDeviceException;
import com.ericsson.deviceaccess.spi.service.misc.WheaterResourceBase;

public class WeatherResourceImpl extends WheaterResourceBase {
    CoAPDevice coap;

    public WeatherResourceImpl(CoAPDevice coap) {
        System.out.println("[WeatherResourceImpl]: created new resource");
        this.coap = coap;
    }

    public void executeGetReport(GenericDeviceActionContext sac) {
        sac.getResult().getValue().setStringValue(WheaterResourceBase.ACTION_getReport_RES_report, getProperties().getStringValue(WheaterResourceBase.ACTION_getReport_RES_report));
    }

    public GetReportResult executeGetReport() throws GenericDeviceException {
        GetReportResult res = new GetReportResult();
        res.report = getProperties().getStringValue(WheaterResourceBase.ACTION_getReport_RES_report);
        return res;

    }

    protected void refreshProperties() {
        // NOP
    }
}