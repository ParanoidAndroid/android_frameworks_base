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

public class PropertyContainer {

    // App specific data
    private String id;
    private int pid;
    private ApplicationInfo info;
    private String path = "";
    private boolean active;
    
    // Hybrid properties
    public int dpi;
    public int layout;
    public boolean force;
    public boolean large;
    public boolean expand;
    public boolean land;
    public String[] colors
            = new String[HybridManager.COLOR_DEF_SIZE];
    public boolean mancol;

    public PropertyContainer() {
    }

    public PropertyContainer(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getPid() {
        return pid;
    }

    public void setPid(int pid) {
        this.pid = pid;
    }

    public ApplicationInfo getInfo() {
        return info;
    }

    public void setInfo(ApplicationInfo info) {
        this.info = info;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getDpi() {
        return dpi;
    }

    public void setDpi(int dpi) {
        this.dpi = dpi;
    }

    public int getLayout() {
        return layout;
    }

    public void setLayout(int layout) {
        this.layout = layout;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public boolean isLarge() {
        return large;
    }

    public void setLarge(boolean large) {
        this.large = large;
    }

    public boolean isExpand() {
        return expand;
    }

    public void setExpand(boolean expand) {
        this.expand = expand;
    }

    public boolean isLand() {
        return land;
    }

    public void setLand(boolean land) {
        this.land = land;
    }

    public String[] getColors() {
        for(int i = 0; i < colors.length; i++) {
            if(colors[i] == null) {
                colors[i] = "";
            }
        }
        return colors;
    }

    public void setColors(String[] colors) {
        this.colors = colors;
    }

    public boolean isMancol() {
        return mancol;
    }

    public void setMancol(boolean mancol) {
        this.mancol = mancol;
    }
}
