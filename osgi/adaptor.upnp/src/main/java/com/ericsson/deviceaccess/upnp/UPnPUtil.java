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
import com.ericsson.common.util.StringUtil;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.upnp.UPnPAction;
import org.osgi.service.upnp.UPnPDevice;
import org.osgi.service.upnp.UPnPException;
import org.osgi.service.upnp.UPnPService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UPnPUtil {

    private static final Logger logger = LoggerFactory.getLogger(UPnPDeviceAgent.class);
    public static final String DEVICE_TYPE_MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer";
    public static final String DEVICE_TYPE_MEDIA_SERVER = "urn:schemas-upnp-org:device:MediaServer";
    public static final String DEVICE_TYPE_BINARY_LIGHT = "urn:schemas-upnp-org:device:BinaryLight";
    // public static final String SERVICE_ID_RENDERING_CONTROL =
    // "urn:upnp-org:serviceId:RenderingControlServiceID";
    public static final String SRV_RENDERING_CONTROL = "RenderingControl";
    public static final String SRV_AV_TRANSPORT = "AVTransport";
    public static final String SRV_CONTENT_DIRECTORY = "ContentDirectory";
    private static final String BROWSE_ACTION = "Browse";
    private static final String RESULT = "Result";
    static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final Pattern varPattern = Pattern.compile("<(\\w+)\\s.*val=\"(.*)\".*/");

    public static String getCurrentMediaUri(UPnPDevice dev) throws Exception {
        Properties args = new Properties();
        args.put("InstanceID", "0");
        Dictionary result = getUPnPAction(dev, "GetMediaInfo").invoke(args);
        if (result != null) {
            return (String) result.get("CurrentURI");
        }
        throw new UPnPException(UPnPException.DEVICE_INTERNAL_ERROR,
                "Could not get result for action");
    }

    public static void stopMedia(UPnPDevice dev) throws Exception {
        info("Going to stop playing on " + getFriendlyName(dev));
        Properties args = new Properties();
        args.put("InstanceID", "0");
        getUPnPAction(dev, "Stop").invoke(args);
    }

    public static void pauseMedia(UPnPDevice dev) throws Exception {
        Properties args = new Properties();
        args.put("InstanceID", "0");
        getUPnPAction(dev, "Pause").invoke(args);
    }

    public static void resumeMedia(UPnPDevice dev) throws Exception {
        Properties args = new Properties();
        args.put("InstanceID", "0");
        args.put("Speed", "1");
        getUPnPAction(dev, "Play").invoke(args);
    }

    public static String playMedia(UPnPDevice dev, String url) throws Exception {
        return playMedia(dev, url, "");
    }

    public static String playMedia(UPnPDevice dev, String url, String title) throws Exception {
        info("Going to play " + url + " on " + UPnPUtil.getFriendlyName(dev));
        url = url.trim();
        Properties args = new Properties();
        args.put("InstanceID", "0");
        args.put("CurrentURI", url);
        if (title == null) {
            title = "";
        }

        String contentType = getContentType(url);
        args.put("CurrentURIMetaData", getCurrentURIMetadata(url, title, contentType));
        debug(args.toString());

        getUPnPAction(dev, "SetAVTransportURI").invoke(args);
        debug("SetAVTransportURI successfully invoked");

        args = new Properties();
        args.put("InstanceID", "0");
        args.put("Speed", "1");
        getUPnPAction(dev, "Play").invoke(args);
        debug("Play successfully invoked");
        return contentType;
    }

    private static String getContentType(String url) throws UPnPUtilException {
        String contentType;
        try {
            contentType = HttpClient.getHeader(url, HEADER_CONTENT_TYPE);
            if (contentType == null || !contentType.contains("/")) {
                throw new UPnPUtilException(404, "Could not determine content type");
            }
            debug("Content type is " + contentType);
            if (contentType.contains(";")) {
                /*
                 * remove optional data such as character encoding.
                 * e.g. video/mp4;UTF-8
                 */
                contentType = contentType.substring(0, contentType.indexOf(';'));
            }
        } catch (IOException e) {
            throw new UPnPUtilException(500, e.getMessage());
        }
        /*
         * Workaround for ustream mp4 file which returns audio/mp4
         * 20100917 Kenta
         */
        if (url.contains("ustream")) {
            contentType = "video/mp4";
        }
        return contentType;
    }

    protected static String getCurrentURIMetadata(String url, String title, String contentType) throws UPnPUtilException {
        String itemType = contentType.substring(0, contentType.indexOf('/'));
        String md = "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">";
        md += "<item>";
        md += "<dc:title>" + title + "</dc:title>";
        md += "<upnp:class>object.item." + itemType + "Item</upnp:class>";
        md += "<res protocolInfo=\"http-get:*:" + contentType + ":*\">";
        md += url;
        md += "</res>";
        md += "</item>";
        md += "</DIDL-Lite>";
        return md;
    }

    public static void setVolume(UPnPDevice dev, String volume)
            throws Exception {
        Properties args = new Properties();
        args.put("InstanceID", "0");
        args.put("Channel", "Master");

        args.put("DesiredVolume", volume);
        getUPnPAction(dev, "SetVolume").invoke(args);

    }

    public static Map<String, Object> browse(UPnPDevice dev, Map<String, Object> givenArgs) throws UPnPException {
        UPnPAction action = getUPnPAction(dev, BROWSE_ACTION);
        if (action == null) {
            throw new UPnPException(404, "No such action supported by " + getFriendlyName(dev));
        }
        Map<String, Object> args = getDefaultBrowseArguments();
        for (String argName : action.getInputArgumentNames()) {
            if (givenArgs.containsKey(argName)) {
                debug("Setting " + argName + " to " + givenArgs.get(argName));
                args.put(argName, givenArgs.get(argName));
            }
        }
        try {
            debug(args.toString());
            return LegacyUtil.toMap(action.invoke(LegacyUtil.toDictionary(args)));
        } catch (Exception e) {
            String msg = ("Failed to invoke UPnP action." + e);
            error(msg);
            throw new UPnPException(500, msg);
        }
    }

    private static Map<String, Object> getDefaultBrowseArguments() {
        Map<String, Object> args = new HashMap<>();
        args.put("ObjectID", 0);
        args.put("BrowseFlag", "BrowseDirectChildren");
        args.put("Filter", "*");
        args.put("StartingIndex", 0);
        args.put("RequestedCount", 0);
        args.put("SortCriteria", "");
        return args;
    }

    public static Map<String, String> parseLastChangeEvent(String value) {
        Map<String, String> result = new HashMap<>();
        for (String tag : value.split(">")) {
            Matcher matcher = varPattern.matcher(tag);
            if (matcher.find()) {
                result.put(matcher.group(1), matcher.group(2));
            }
        }
        return result;
    }

    private static UPnPAction getUPnPAction(UPnPDevice device, String actionName) throws UPnPException {
        for (UPnPService service : device.getServices()) {
            UPnPAction action = service.getAction(actionName);
            if (action != null) {
                return action;
            }
        }
        throw new UPnPException(UPnPException.INVALID_ACTION,
                "No such action supported " + actionName);
    }

    public static String getUDN(UPnPDevice dev) {
        return getProperty(dev, UPnPDevice.UDN);
    }

    public static String getDeviceType(UPnPDevice dev) {
        return getProperty(dev, UPnPDevice.TYPE);
    }

    public static String getFriendlyName(UPnPDevice dev) {
        return getProperty(dev, UPnPDevice.FRIENDLY_NAME);
    }

    public static String getModelName(UPnPDevice dev) {
        return getProperty(dev, UPnPDevice.MODEL_NAME);
    }

    public static boolean isMediaRenderer(UPnPDevice dev) {
        String type = getDeviceType(dev);
        return type != null && type.startsWith(DEVICE_TYPE_MEDIA_RENDERER);
    }

    public static boolean isMediaServer(UPnPDevice dev) {
        String type = getDeviceType(dev);
        return type != null && type.startsWith(DEVICE_TYPE_MEDIA_SERVER);
    }

    public static boolean isDimmableLight(UPnPDevice dev) {
        String type = getDeviceType(dev);
        String model = getModelName(dev);
        return type != null && type.startsWith(DEVICE_TYPE_BINARY_LIGHT)
                && model != null && model.startsWith("Intel");
    }

    private static String getProperty(UPnPDevice dev, String name) {
        if (dev != null) {
            String value = (String) dev.getDescriptions(null).get(name);
            debug("Property " + name + " = " + value);
            return value;
        }
        return null;
    }

    public static UPnPDevice getTargetDevice(String uuid, BundleContext context)
            throws UPnPUtilException {
        if (uuid == null) {
            String msg = "Device UUID is not specified";
            throw new UPnPUtilException(400, msg);
        }
        UPnPDevice target;

        try {
            // String uuidFilter = "(" + UPnPDevice.UDN + "=" + uuid + ")";
            for (ServiceReference<UPnPDevice> ref : context.getServiceReferences(UPnPDevice.class, null)) {
                UPnPDevice dev = context.getService(ref);
                if (StringUtil.looseEquals(uuid, dev.getDescriptions(null).get(UPnPDevice.UDN))) {
                    return dev;
                }
            }
        } catch (InvalidSyntaxException e) {
            throw new UPnPUtilException(500, e.getMessage());
        }
        String msg = "No such device found";
        throw new UPnPUtilException(404, msg);
    }

    private static void debug(String msg) {
        if (logger != null) {
            logger.debug(msg);
        }
    }

    private static void info(String msg) {
        if (logger != null) {
            logger.info(msg);
        }
    }

    private static void error(String msg) {
        if (logger != null) {
            logger.error(msg);
        }
    }

    static public String[] parseServiceType(String serviceType) {
        return serviceType.split(":");
    }

    private UPnPUtil() {
    }

    protected static class HttpClient {

        public static String getHeader(String urlStr, String header) throws IOException {
            URL url = new URL(urlStr);

            HttpURLConnection urlconn = (HttpURLConnection) url.openConnection();
            urlconn.setRequestMethod("HEAD");
            urlconn.setInstanceFollowRedirects(true);

            urlconn.connect();
            String value = urlconn.getHeaderField(header);
            urlconn.disconnect();
            return value;
        }

        private HttpClient() {
        }
    }
}
