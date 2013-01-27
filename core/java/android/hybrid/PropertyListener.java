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

import android.os.FileObserver;

import java.util.ArrayList;

/**
 * Listens to {@link FileObserver.MODIFY} events on system path and allows
 * registering property listeners by external classes that implement
 * {@link HybridListener}.
 */
public class PropertyListener {

    private PropertyFileObserver mFileObserver;
    private PropertyHandler mPropertyHandler;

    private String[] mOldValues;
    private String mPackageName;

    public interface HybridListener {
        public abstract void onPropertyChanged(PropertyHandler props);
    }    

    public PropertyListener(HybridListener listener) {
        mFileObserver = new PropertyFileObserver(listener);
    }

    public PropertyHandler getHandler() {
        return mPropertyHandler;
    }

    public void startWatching(String packageName) {
        mFileObserver.startWatching();
        mOldValues = HybridManager.readFile(HybridManager
                .PARANOID_PROPERTIES).split("\n");
        mPropertyHandler = new PropertyHandler();
        mPackageName = packageName;
    }

    public void stopWatching() {
        mFileObserver.stopWatching();
    }

    private boolean hasChangedValues() {
        int totalProperties = 0;
        int foundProperties = 0;
        mPropertyHandler.clearProperties();
        String[] newValues = HybridManager.readFile(
                HybridManager.PARANOID_PROPERTIES).split("\n");
        for (String s : newValues) {
            if (s.contains(mPackageName)) {
                totalProperties ++;
                for (int i = 0; i < mOldValues.length; i++) {
                    if (mOldValues[i].equals(s)) {
                        foundProperties ++;
                    }
                }
                mPropertyHandler.addProperty(s);
            }
        }
        return totalProperties != foundProperties;
    }

    public class PropertyHandler {
        private ArrayList<String> properties;

        public PropertyHandler() {
            properties = new ArrayList();
        }

        public void addProperty(String prop) {
            properties.add(prop);
        }

        public String getValueForProperty(String seek, String type) {
            String value = null;
            String prop = seek + "." + type;
            if(properties.isEmpty()) {
                value = HybridManager
                        .getSpecificProperty(prop);
            }
            for (String property : properties) {
                if(property.startsWith(prop)) {
                    value = property.split("=")[1];
                }
            }
            if (value != null &&
                    value.startsWith(HybridManager.PRESET_PREFIX)) {
                return HybridManager.getPresetValue(value);
            } else {
                return value;
            }
        }

        public void clearProperties() {
            properties.clear();   
        }
    }

    public class PropertyFileObserver extends FileObserver {

        private HybridListener mListener;

        public PropertyFileObserver(HybridListener listener) {
            super(HybridManager.SYSTEM_PATH, FileObserver.ALL_EVENTS);
            mListener = listener;
        }

        @Override
        public void onEvent(int event, String path) {
            if (path == null) {
                return;
            }

            if ((FileObserver.MODIFY & event) != 0) {
                if (path.equals(HybridManager.CONFIGURATION_FILE)) {
                    if (hasChangedValues()) {
                        mListener.onPropertyChanged(mPropertyHandler);
                        mOldValues = HybridManager.readFile(HybridManager
                                .PARANOID_PROPERTIES).split("\n");
                    }
                }
            }
        }
    }
}
