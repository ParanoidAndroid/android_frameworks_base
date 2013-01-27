/*
 * Copyright (C) 2013 ParanoidAndroid Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.hybrid;

import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.provider.Settings;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;

public class HybridManager extends ExtendedPropertiesUtils {

    private static final String TAG = "HybridManager";
    private static final boolean DEBUG = false;

    public static final String SYSTEM_PATH = "/system/etc/paranoid/";
    public static final String CONFIGURATION_FILE = "properties.conf";
    public static final String PARANOID_PROPERTIES = SYSTEM_PATH + CONFIGURATION_FILE;

    public static final String PRESET_PREFIX = "%";
    public static final String TYPE_PRESET = "preset";
    public static final String TYPE_DPI = "dpi";
    public static final String TYPE_LAYOUT = "layout";
    public static final String TYPE_FORCE = "force";
    public static final String TYPE_LARGE = "large";
    public static final String TYPE_EXPAND = "expand";
    public static final String TYPE_LANDSCAPE = "land";
    public static final String TYPE_COLORS = "colors";
    public static final String TYPE_MANCOL = "mancol";

    public static final String[] COLOR_SETTINGS = {Settings.System.NAV_BAR_COLOR,
        Settings.System.NAV_BUTTON_COLOR, Settings.System.NAV_GLOW_COLOR,
        Settings.System.STATUS_BAR_COLOR, Settings.System.STATUS_ICON_COLOR};
    public static final int[] COLOR_DEF_CODES = 
            {0xFF000000, 0xB2FFFFFF, 0xFFFFFFFF, 0xFF000000, 0xFF33B5E5};
    public static final int COLOR_DEF_SIZE = 5;

    public static ArrayList<PropertyHolder> sPropertyArray;

    public static PropertyContainer sGlobalHook = new PropertyContainer();
    public static PropertyContainer sLocalHook = new PropertyContainer();

    public static ApplicationInfo sApplicationInfo;

    /**
     * Holder for basic property structure
     */
    public static class PropertyHolder {
        private String property;
        private String type;
        private String value;

        public PropertyHolder(String property, String type, String value) {
            this.property = property;
            this.type = type;
            this.value = value;
        }

        public String getProperty() {
            return property;
        }

        public String getType() {
            return type;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Enum interface to allow different override modes
     */
    public static enum OverrideMode {
        HYBRID_MANAGER, APP_INFO, FULL_NAME, FULL_NAME_EXCLUDE, PACKAGE_NAME
    }

    /**
     * Set app configuration for the input argument <code>container</code>.
     *
     * @param  container  instance containing app details
     */
    public static void setAppConfiguration(PropertyContainer container,
            boolean isGlobal) {
        if(true){ // Don't allow switching off hybrid
            String path = container.getPath();
            boolean isSystemApp = path.contains("system/app");
            int defaultDpi = Integer.parseInt(getPresetValue(PRESET_PREFIX
                    + (isSystemApp ? "system_default_dpi" : (path.isEmpty()
                    ? "0" : "user_default_dpi"))));
            int defaultLayout = Integer.parseInt(getPresetValue(PRESET_PREFIX
                    + (isSystemApp ? "system_default_layout" : (path.isEmpty()
                    ? "0" : "user_default_layout"))));

            container = getProperty(container.getInfo().processName);

            if (container.getDpi() == 0) {
                container.setDpi(defaultDpi);
            }

            if (container.getLayout() == 0) {
                container.setLayout(defaultLayout);
            }

            container.setActive(true);
            if(isGlobal) sGlobalHook = container;
            else sLocalHook = container;
        }
    }

    /**
     * Overrides current hook with input parameter <code>mode</code>, wich
     * is an enum interface that stores basic override possibilities.
     *
     * @param  input  object to be overriden
     * @param  mode  enum interface
     */
    public void overrideHook(Object input, OverrideMode mode) {
        if (isInitialized() && input != null) {
            ApplicationInfo tempInfo = null;
            HybridManager tempProps;

            switch (mode) {
                case HYBRID_MANAGER:
                    tempProps = (HybridManager) input;
                    if (tempProps.sLocalHook.isActive()) {
                        sLocalHook.setId(tempProps.sLocalHook.getId());
                        sLocalHook.setPid(tempProps.sLocalHook.getPid());
                        sLocalHook.setInfo(tempProps.sLocalHook.getInfo());
                        sLocalHook.setPath(tempProps.sLocalHook.getPath());
                        sLocalHook.setActive(tempProps.sLocalHook.isActive());
                        sLocalHook.setLayout(tempProps.sLocalHook.getLayout());
                        sLocalHook.setDpi(tempProps.sLocalHook.getDpi());
                        sLocalHook.setForce(tempProps.sLocalHook.isForce());
                        sLocalHook.setLarge(tempProps.sLocalHook.isLarge());
                    }
                    return;
                case APP_INFO:
                    sLocalHook.setInfo((ApplicationInfo) input);
                    break;
                case FULL_NAME:
                    sLocalHook.setInfo(getAppInfoFromPath((String) input));
                    break;
                case FULL_NAME_EXCLUDE:
                    tempInfo = getAppInfoFromPath((String) input);
                    if (tempInfo != null && (!isHooked() ||
                            getProperty(tempInfo.packageName).isForce())) {
                        sLocalHook.setInfo(tempInfo);
                    }
                    break;
                case PACKAGE_NAME:
                    sLocalHook.setInfo(getAppInfoFromPackageName(
                            (String) input));
                    break;
            }

            if (sLocalHook.getInfo() != null) {
                sLocalHook.setId(sLocalHook.getInfo().packageName);
                sLocalHook.setPid(android.os.Process.myPid());
                sLocalHook.setPath(sLocalHook.getInfo().sourceDir.substring(0, 
                        sLocalHook.getInfo().sourceDir.lastIndexOf("/")));

                setAppConfiguration(sLocalHook, false);
            }
        }
    }

    /**
     * This methods are used to retrieve specific information either for 
     * local or global hook
     */
    public static boolean isHooked() {
        String id = sGlobalHook.getId();
        if(id == null) return false;
        return isInitialized() && !id.equals("android") && !id.isEmpty();
    }

    public String getName() {
        return sLocalHook.isActive() ? sLocalHook.getId() : sGlobalHook.getId();
    }

    public int getPid() {
        return sLocalHook.isActive() ? sLocalHook.getPid() : sGlobalHook.getPid();
    }

    public boolean isActive() {
        return sLocalHook.isActive() ? sLocalHook.isActive() : sGlobalHook.isActive();
    }

    public int getLayout() {
        return sLocalHook.isActive() ? sLocalHook.getLayout() : sGlobalHook.getLayout();
    }

    public int getDpi() {
        return sLocalHook.isActive() ? sLocalHook.getDpi() : sGlobalHook.getDpi();
    }

    public boolean isForce() {
        return sLocalHook.isActive() ? sLocalHook.isForce() : sGlobalHook.isForce();
    }

    public boolean isLarge() {
        return sLocalHook.isActive() ? sLocalHook.isLarge() : sGlobalHook.isLarge();
    }

    public boolean isLand() {
        return sLocalHook.isActive() ? sLocalHook.isLand() : sGlobalHook.isLand();
    }

    public static boolean isTablet() {
        getPropertyArray();
        return getProperty("com.android.systemui").getLayout() >= 1000;
    }

    /**
     * Returns an {@link Integer}, equivalent to what other classes will actually 
     * load for the input argument <code>what</code>. it differs from 
     * {@link #getProperty(String) getProperty}, because the values
     * returned will never be zero.
     *
     * @param  what  a string containing the property to checkout
     * @return the actual integer value of the selected property
     * @see getProperty
     */
    public static int getIntegerProperty(String what) {
        int result = 0;
        for(PropertyHolder prop : sPropertyArray) {
            String property = prop.getProperty();
            String type = prop.getType();
            if (what.startsWith(property) &&
                    (what.endsWith(type) || type.equals(TYPE_PRESET))) {
                String value = prop.getValue();
                if(isParsableToInt(value)) {
                    result = Integer.parseInt(value);
                    if(result != 0) break;
                }

                ApplicationInfo info = getAppInfoFromPackageName(property);
                if(info != null) {
                    if(info.sourceDir.substring(0, info.sourceDir
                            .lastIndexOf("/")).contains("system/app")) {
                        result = Integer.parseInt(type.equals(TYPE_DPI) ?
                                getPresetValue(PRESET_PREFIX + "system_default_dpi") :
                                getPresetValue(PRESET_PREFIX + "system_default_layout"));
                    } else {
                        result = Integer.parseInt(type.equals(TYPE_DPI) ?
                                getPresetValue(PRESET_PREFIX + "user_default_dpi") :
                                getPresetValue(PRESET_PREFIX + "user_default_layout"));
                    }
                }

                if(result == 0) {
                    result = Integer.parseInt(what.endsWith(TYPE_DPI) ?
                            getPresetValue(PRESET_PREFIX + "rom_default_dpi") :
                            getPresetValue(PRESET_PREFIX + "rom_default_layout"));
                }
            }
        }
        return result;
    }

    /**
     * Returns a {@link android.hybrid.PropertyContainer}, containing the 
     * result of the configuration for the input argument <code>prop</code>.
     * If the property is not found returns null.
     *
     * @param  prop  a string containing the property to checkout
     * @return current stored values of property
     */
    public static PropertyContainer getProperty(String packageName) {
        return getProperty(packageName, false);
    }

    /**
     * Returns a {@link android.hybrid.PropertyContainer}, containing the 
     * result of the configuration for the input argument <code>prop</code>.
     * If the property is not found returns null.
     *
     * @param  prop  a string containing the property to checkout
     * @param  reloadConfig  whether to use preload array 
     *                       or fetch values from config file
     * @return current stored values of property
     */
    public static PropertyContainer getProperty(String packageName,
            boolean reloadConfig) {
        PropertyContainer container = new PropertyContainer(packageName);
        if(sPropertyArray == null || reloadConfig) getPropertyArray();
        for(PropertyHolder prop : sPropertyArray) {
            if (prop.getProperty().equals(packageName)) {
                String type = prop.getType();
                String value = prop.getValue();
                if (type.equals(TYPE_DPI)) {
                    container.setDpi(getIntegerValue(value));
                } else if (type.equals(TYPE_LAYOUT)) {
                    container.setLayout(getIntegerValue(value));
                } else if (type.equals(TYPE_FORCE)) {
                    container.setForce(getIntegerValue(value) == 1);
                } else if (type.equals(TYPE_LARGE)) {
                    container.setLarge(getIntegerValue(value) == 1);
                } else if (type.equals(TYPE_EXPAND)) {
                    container.setExpand(getIntegerValue(value) == 1);
                } else if (type.equals(TYPE_LANDSCAPE)) {
                    container.setLand(getIntegerValue(value) == 1);
                } else if (type.equals(TYPE_COLORS)) {
                    String[] colors = value.split("\\|");
                    // Don't allow incomplete color codes
                    if (colors.length == COLOR_DEF_SIZE) {
                        container.setColors(colors);
                    }
                } else if (type.equals(TYPE_MANCOL)) {
                    container.setMancol(getIntegerValue(value) == 1);
                }
            }
        }
        return container;
    }

    /**
     * Returns a {@String}, containing the value for a preset argument
     * stored on the configuration file. If the preset is not found 
     * returns null.
     *
     * @param  prop  a preset containing the property to checkout
     * @return preset value
     */
    public static String getPresetValue(String preset) {
        if(sPropertyArray == null) getPropertyArray();
        for(PropertyHolder prop : sPropertyArray) {
            if (prop.getProperty().equals(preset) &&
                    prop.getType().equals(TYPE_PRESET)) {
                return prop.getValue();
            }
        }
        return getSpecificProperty(preset);
    }

    /**
     * Returns an {@link Integer}, containing the real value of the string
     * stored.
     *
     * @see {@link PropertyHolder}
     * @param  value the value to evaluate
     * @return integer value of the property
     */
    private static int getIntegerValue(String value) {
        return isParsableToInt(value) ? Integer.parseInt(value)
                : Integer.parseInt(getPresetValue(value));
    }

    /**
     * Stores an {@link ArrayList}, of {@link PropertyHolder} fetched from the 
     * configuration file.
     *
     */
    public static void getPropertyArray() {
        sPropertyArray = new ArrayList();
        String[] props = readFile(PARANOID_PROPERTIES).split("\n");
        for (String prop : props) {
            // Skip comments and blank spaces
            if (!prop.startsWith("#") && !prop.isEmpty()) {
                // Presets need a different treatment
                if (prop.startsWith(PRESET_PREFIX)) {
                    String[] item = prop.split("=");
                    String type = TYPE_PRESET;
                    PropertyHolder holder = new PropertyHolder(item[0], type, item[1]);
                    sPropertyArray.add(holder);
                } else {
                    int splitter = prop.lastIndexOf(".");
                    String property = prop.substring(0, splitter);
                    String type = prop.substring(splitter + 1, 
                            prop.indexOf("="));
                    String value = prop.split("=")[1];
                    PropertyHolder holder = new PropertyHolder(property, type, value);
                    sPropertyArray.add(holder);
                }
            }
        }
    }

    /**
     * Fetches a property directly from the configuration file. If the value
     * is not found, returns "0" as a {@link String}.
     *
     * @param  property  the property to checkout
     * @return property value
     */
    public static String getSpecificProperty(String property) {
        String[] props = readFile(PARANOID_PROPERTIES).split("\n");
        for (String prop : props) {
            if (!prop.startsWith("#") && !prop.isEmpty()) {
                String[] values = prop.split("=");
                if(values[0].equals(property)) {
                    return values[1];
                }
            }
        }
        return String.valueOf(0);
    }

    /**
     * Reads a file, defined in the input argument <code>fileName</code>
     * and returns its content as a {@link String}.
     *
     * @param  fileName file to be read
     */
    public static String readFile(String fileName) {
        String output = "";
        try {
            String charsetName = "UTF-8";
            FileInputStream f = new FileInputStream(fileName);
            FileChannel ch = f.getChannel();
            MappedByteBuffer mbb = ch.map(FileChannel.MapMode.READ_ONLY,
                    0L, ch.size());
            while (mbb.hasRemaining()) {
                CharBuffer cb =  Charset.forName(charsetName).decode(mbb);
                output += cb.toString();
            }
        } catch (FileNotFoundException e){
            if(DEBUG) Log.e(TAG, "file not found: " + fileName);
        } catch (IOException e){
            // fly away!
        }
        return output;
    }

    /**
     * Returns a {@link Boolean}, meaning if the input argument is an integer
     * number.
     *
     * @param  str  the string to be tested
     * @return the string is an integer number
     */
    public static boolean isParsableToInt(String str) {
        try {
            int i = Integer.parseInt(str);
            return true;
        } catch(NumberFormatException nfe) {
            return false;
        }
    }
}
