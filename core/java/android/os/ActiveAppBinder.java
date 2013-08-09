package android.os; 

import android.os.ActiveAppCallback;

import java.util.WeakHashMap;

public class ActiveAppBinder {

        // Weak refrenced so as to not prevet a gc
        private static WeakHashMap<String, ActiveAppCallback> callbackMap = new WeakHashMap<String,  ActiveAppCallback> ();
        
        public ActiveAppBinder (ActiveAppCallback callBack){
            callbackMap.put(callBack.getClass().getName(), callBack);
        }

        public static void notifyApps(){
           for (ActiveAppCallback callback: callbackMap.values()) {
                callback.appChanged();
            }
        }
    
    }
