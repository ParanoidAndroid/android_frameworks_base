package android.os; 

import android.os.ActiveAppCallBack;

import java.util.List;

public class ActiveAppBinder {

        private static List<ActiveAppCallback>> CallbackMap = new List<ActiveAppCallback>();
        
        public void bind (ActiveAppCallback callBack){
            callBackMap.add(callBack);
        }

        public static void notifyApps(){
            for(ActiveAppCallback callBacks : CallbackMap) {
                callBacks.appChanged();
            }
        }
    
    }
