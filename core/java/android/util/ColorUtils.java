/*
 * Copyright (C) 2012 Imil Ziyaztdinov
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

package android.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;

import java.lang.Math;

public class ColorUtils {

    public static final int[] AVAILABLE_COLORS = {
//            com.android.internal.R.color.black, // to be or not to be?
            com.android.internal.R.color.holo_blue_bright,
            com.android.internal.R.color.holo_blue_dark,
            com.android.internal.R.color.holo_blue_light,
            com.android.internal.R.color.holo_green_dark,
            com.android.internal.R.color.holo_green_light,
            com.android.internal.R.color.holo_orange_dark,
            com.android.internal.R.color.holo_orange_light,
            com.android.internal.R.color.holo_purple,
            com.android.internal.R.color.holo_red_dark,
            com.android.internal.R.color.holo_red_light
    };

    private static final double COMPARATIVE_FACTOR = 3.5;
    private static final double COMPARATIVE_NUMBER = COMPARATIVE_FACTOR*125;
    
    private static int getColorLuminance(int color) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Math.round(((red * 299) + (green * 587)
                +(blue * 114)) / 1000);  
    }

    private static int getLuminanceDifference(int color1, int color2) {
        int lum1 = getColorLuminance(color1);
        int lum2 = getColorLuminance(color2);
        return Math.abs(lum1 - lum2);
    }

    private static int getColorDifference(int color1, int color2) {
        int[] rgb1 = {Color.red(color1), Color.green(color1), Color.blue(color1)};
        int[] rgb2 = {Color.red(color2), Color.green(color2), Color.blue(color2)}; 
        return Math.abs(rgb1[0] - rgb2[0]) +
               Math.abs(rgb1[1] - rgb2[1]) +
               Math.abs(rgb1[2] - rgb2[2]); 
    }  

    public static int getComplementaryColor(int bgcolor, Context context) {
        Resources res = context.getResources();        
        int minKey = 0;
        double lumDiff = 0;
        double colDiff = 0;
        double currValue = 0;
        double minValue = -1;
        for (int i = 0; i < AVAILABLE_COLORS.length; i++) {
            lumDiff = COMPARATIVE_FACTOR * getLuminanceDifference(bgcolor,
                    res.getColor(AVAILABLE_COLORS[i]));
            colDiff = getColorDifference(bgcolor,
                    res.getColor(AVAILABLE_COLORS[i]));
            lumDiff = Math.abs(COMPARATIVE_NUMBER - lumDiff);
            colDiff = Math.abs(COMPARATIVE_NUMBER - colDiff);
            currValue = lumDiff + colDiff;
            if (minValue == -1 || currValue < minValue) {
                minKey = i;
                minValue = currValue;
            }
        }      
        return res.getColor(AVAILABLE_COLORS[minKey]);
    }
}
