package android.os; 

import android.os.ActiveAppCallback;

import java.util.LinkedList;

public class ActiveAppBinder {

        private static LinkedList<ActiveAppCallback> CallbackMap = new LinkedList<ActiveAppCallback> ();
        
        public ActiveAppBinder (ActiveAppCallback callBack){
            CallbackMap.add(callBack);
        }

        public static void notifyApps(){
            for(ActiveAppCallback callBack : CallbackMap) {
                callBack.appChanged();
            }
        }
    
    }
