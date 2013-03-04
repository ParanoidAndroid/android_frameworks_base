/*
 * Copyright (C) 2012 Slimroms
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

package com.android.systemui.statusbar.policy;

import java.net.URISyntaxException;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo; 
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager; 
import android.os.Process; 
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Toast; 

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.statusbar.policy.KeyButtonView;
import com.android.systemui.R;

import java.util.List;

public class ExtensibleKeyButtonView extends KeyButtonView {

    final static String ACTION_HOME = "**home**";
    final static String ACTION_BACK = "**back**";
    final static String ACTION_SEARCH = "**search**";
    final static String ACTION_MENU = "**menu**";
    final static String ACTION_POWER = "**power**";
    final static String ACTION_NOTIFICATIONS = "**notifications**";
    final static String ACTION_RECENTS = "**recents**";
    final static String ACTION_SCREENSHOT = "**screenshot**";
    final static String ACTION_IME = "**ime**";
    final static String ACTION_LAST_APP = "**lastapp**";
    final static String ACTION_KILL = "**kill**";
    final static String ACTION_NULL = "**null**";

    private static final String TAG = "Key.Ext";

    IStatusBarService mBarService;

    public String mClickAction, mLongpress;

    public Handler mHandler;

    public ActivityManager mActivityManager;

    public int mInjectKeycode;

    final Object mScreenshotLock = new Object();
    ServiceConnection mScreenshotConnection = null;

    public ExtensibleKeyButtonView(Context context, AttributeSet attrs) {
        this(context, attrs, ACTION_NULL,ACTION_NULL);
    }

    public ExtensibleKeyButtonView(Context context, AttributeSet attrs, String ClickAction, String Longpress) {
        super(context, attrs);

        mHandler = new Handler();
        mActivityManager = (ActivityManager) context.getSystemService(Activity.ACTIVITY_SERVICE);
        mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));
        mClickAction = ClickAction;
        mLongpress = Longpress;
        setCode(0);
        if (ClickAction != null){
            if (ClickAction.equals(ACTION_HOME)) {
                setCode(KeyEvent.KEYCODE_HOME);
                setId(R.id.home);
            } else if (ClickAction.equals(ACTION_BACK)) {
                setCode (KeyEvent.KEYCODE_BACK);
                setId(R.id.back);
            } else if (ClickAction.equals(ACTION_SEARCH)) {
                setCode (KeyEvent.KEYCODE_SEARCH);
            } else if (ClickAction.equals(ACTION_MENU)) {
                setCode (KeyEvent.KEYCODE_MENU);
            } else { // the remaining options need to be handled by OnClick;
                setOnClickListener(mClickListener);
                if (ClickAction.equals(ACTION_RECENTS))
                    setId(R.id.recent_apps);
            }
        }
        setSupportsLongPress (false);
        if (Longpress != null)
            if ((!Longpress.equals(ACTION_NULL)) || (getCode() !=0)) {
                // I want to allow long presses for defined actions, or if
                // primary action is a 'key' and long press isn't defined otherwise
                setSupportsLongPress(true);
                setOnLongClickListener(mLongPressListener);
            }
    }

    public void injectKeyDelayed(int keycode){
        mInjectKeycode = keycode;
        mHandler.removeCallbacks(onInjectKey_Down);
        mHandler.removeCallbacks(onInjectKey_Up);
        mHandler.post(onInjectKey_Down);
        mHandler.postDelayed(onInjectKey_Up,10); // introduce small delay to handle key press
    }

    final Runnable onInjectKey_Down = new Runnable() {
        public void run() {
            final KeyEvent ev = new KeyEvent(mDownTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, mInjectKeycode, 0,
                    0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                    InputDevice.SOURCE_KEYBOARD);
            InputManager.getInstance().injectInputEvent(ev,
                    InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    };

    final Runnable onInjectKey_Up = new Runnable() {
        public void run() {
            final KeyEvent ev = new KeyEvent(mDownTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, mInjectKeycode, 0,
                    0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                    InputDevice.SOURCE_KEYBOARD);
            InputManager.getInstance().injectInputEvent(ev,
                    InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    };

    Runnable mKillTask = new Runnable() {
        public void run() {
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            String defaultHomePackage = "com.android.launcher";
            intent.addCategory(Intent.CATEGORY_HOME);
            final ResolveInfo res = mContext.getPackageManager().resolveActivity(intent, 0);
            if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
                defaultHomePackage = res.activityInfo.packageName;
            }
            boolean targetKilled = false;
            final ActivityManager am = (ActivityManager) mContext
                    .getSystemService(Activity.ACTIVITY_SERVICE);
            List<RunningAppProcessInfo> apps = am.getRunningAppProcesses();
            for (RunningAppProcessInfo appInfo : apps) {
                int uid = appInfo.uid;
                // Make sure it's a foreground user application (not system,
                // root, phone, etc.)
                if (uid >= Process.FIRST_APPLICATION_UID && uid <= Process.LAST_APPLICATION_UID
                        && appInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    if (appInfo.pkgList != null && (appInfo.pkgList.length > 0)) {
                        for (String pkg : appInfo.pkgList) {
                            if (!pkg.equals("com.android.systemui") && !pkg.equals(defaultHomePackage)) {
                                am.forceStopPackage(pkg);
                                targetKilled = true;
                                break;
                            }
                        }
                    } else {
                        Process.killProcess(appInfo.pid);
                        targetKilled = true;
                    }
                }
                if (targetKilled) {
                    Toast.makeText(mContext, R.string.app_killed_message, Toast.LENGTH_SHORT).show();
                    break;
                }
            } 
        }
    };

    private OnClickListener mClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            // the other consts were handled by keycode.

            if (mClickAction.equals(ACTION_NULL)) {
                // who would set a button with no ClickAction?
                // Stranger things have happened.
                return;

            } else if (mClickAction.equals(ACTION_RECENTS)) {
                try {
                    mBarService.toggleRecentApps();
                } catch (RemoteException e) {
                }
                return;
            } else if (mClickAction.equals(ACTION_SCREENSHOT)) {
                takeScreenshot();
                return;
            } else if (mClickAction.equals(ACTION_NOTIFICATIONS)) {
                try {
                    mBarService.toggleNotificationShade();
                } catch (RemoteException e) {
                    // wtf is this
                }
                return;
            } else if (mClickAction.equals(ACTION_IME)) {
                getContext().sendBroadcast(new Intent("android.settings.SHOW_INPUT_METHOD_PICKER"));
                return;
            } else if (mClickAction.equals(ACTION_LAST_APP)) {
                toggleLastApp();
                return;
            } else if (mClickAction.equals(ACTION_KILL)) {
                mHandler.postDelayed(mKillTask,ViewConfiguration.getGlobalActionKeyTimeout());
                return;
	    } else if (mClickAction.equals(ACTION_POWER)) {
                PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                pm.goToSleep(SystemClock.uptimeMillis()); 
            } else {  // we must have a custom uri
                 try {
                     Intent intent = Intent.parseUri(mClickAction, 0);
                     intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                     getContext().startActivity(intent);
                 } catch (URISyntaxException e) {
                     Log.e(TAG, "URISyntaxException: [" + mClickAction + "]");
                 } catch (ActivityNotFoundException e){
                      Log.e(TAG, "ActivityNotFound: [" + mClickAction + "]");
                 }
            }
            return;
        }
    };

    private OnLongClickListener mLongPressListener = new OnLongClickListener() {

        @Override
        public boolean onLongClick(View v) {
            if (mLongpress == null) {
                return true;
            }
            if (mLongpress.equals(ACTION_NULL)) {
                // attempt to keep long press functionality of 'keys' if
                // they haven't been overridden.
                return true;
            } else if (mLongpress.equals(ACTION_HOME)) {
                injectKeyDelayed(KeyEvent.KEYCODE_HOME);
                return true;
            } else if (mLongpress.equals(ACTION_BACK)) {
                injectKeyDelayed(KeyEvent.KEYCODE_BACK);
                return true;
            } else if (mLongpress.equals(ACTION_SEARCH)) {
                injectKeyDelayed(KeyEvent.KEYCODE_SEARCH);
                return true;
            } else if (mLongpress.equals(ACTION_MENU)) {
                injectKeyDelayed(KeyEvent.KEYCODE_MENU);
                return true;
            } else if (mLongpress.equals(ACTION_POWER)) {
                PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                pm.goToSleep(SystemClock.uptimeMillis()); 
                return true;
            } else if (mLongpress.equals(ACTION_IME)) {
                getContext().sendBroadcast(new Intent("android.settings.SHOW_INPUT_METHOD_PICKER"));
                return true;
            } else if (mLongpress.equals(ACTION_KILL)) {
                mHandler.post(mKillTask);
                return true;
            } else if (mLongpress.equals(ACTION_LAST_APP)) {
                toggleLastApp();
                return true;
            } else if (mLongpress.equals(ACTION_RECENTS)) {
                try {
                    mBarService.toggleRecentApps();
                } catch (RemoteException e) {
                    // let it go.
                }
                return true;
            } else if (mLongpress.equals(ACTION_SCREENSHOT)) {
                takeScreenshot();
                return true;
            } else if (mLongpress.equals(ACTION_NOTIFICATIONS)) { 
                try {
                    mBarService.toggleNotificationShade();
                } catch (RemoteException e) {
                    // wtf is this
                }
                return true;
            } else {  // we must have a custom uri
                    try {
                        Intent intent = Intent.parseUri(mLongpress, 0);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        getContext().startActivity(intent);
                    } catch (URISyntaxException e) {
                        Log.e(TAG, "URISyntaxException: [" + mLongpress + "]");
                    } catch (ActivityNotFoundException e){
                        Log.e(TAG, "ActivityNotFound: [" + mLongpress + "]");
                    }
                    return true;
            }
        }
    };

    final Runnable mScreenshotTimeout = new Runnable() {
        @Override
        public void run() {
            synchronized (mScreenshotLock) {
                if (mScreenshotConnection != null) {
                    mContext.unbindService(mScreenshotConnection);
                    mScreenshotConnection = null;
                }
            }
        }
    };

    private void takeScreenshot() {
        synchronized (mScreenshotLock) {
            if (mScreenshotConnection != null) {
                return;
            }
            ComponentName cn = new ComponentName("com.android.systemui",
                    "com.android.systemui.screenshot.TakeScreenshotService");
            Intent intent = new Intent();
            intent.setComponent(cn);
            ServiceConnection conn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    synchronized (mScreenshotLock) {
                        if (mScreenshotConnection != this) {
                            return;
                        }
                        Messenger messenger = new Messenger(service);
                        Message msg = Message.obtain(null, 1);
                        final ServiceConnection myConn = this;
                        Handler h = new Handler(H.getLooper()) {
                            @Override
                            public void handleMessage(Message msg) {
                                synchronized (mScreenshotLock) {
                                    if (mScreenshotConnection == myConn) {
                                        mContext.unbindService(mScreenshotConnection);
                                        mScreenshotConnection = null;
                                        H.removeCallbacks(mScreenshotTimeout);
                                    }
                                }
                            }
                        };
                        msg.replyTo = new Messenger(h);
                        msg.arg1 = msg.arg2 = 0;

                        /*
                         * remove for the time being if (mStatusBar != null &&
                         * mStatusBar.isVisibleLw()) msg.arg1 = 1; if
                         * (mNavigationBar != null &&
                         * mNavigationBar.isVisibleLw()) msg.arg2 = 1;
                         */

                        /* wait for the dialog box to close */
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                        }

                        /* take the screenshot */
                        try {
                            messenger.send(msg);
                        } catch (RemoteException e) {
                        }
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                }
            };
            if (mContext.bindService(intent, conn, mContext.BIND_AUTO_CREATE)) {
                mScreenshotConnection = conn;
                H.postDelayed(mScreenshotTimeout, 10000);
            }
        }
    }

    private void toggleLastApp() {
        int lastAppId = 0;
        int looper = 1;
        String packageName;
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        final ActivityManager am = (ActivityManager) mContext
                .getSystemService(Activity.ACTIVITY_SERVICE);
        String defaultHomePackage = "com.android.launcher";
        intent.addCategory(Intent.CATEGORY_HOME);
        final ResolveInfo res = mContext.getPackageManager().resolveActivity(intent, 0);
        if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
            defaultHomePackage = res.activityInfo.packageName;
        }
        List <ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(5);
        // lets get enough tasks to find something to switch to
        // Note, we'll only get as many as the system currently has - up to 5
        while ((lastAppId == 0) && (looper < tasks.size())) {
            packageName = tasks.get(looper).topActivity.getPackageName();
            if (!packageName.equals(defaultHomePackage) && !packageName.equals("com.android.systemui")) {
                lastAppId = tasks.get(looper).id;
            }
            looper++;
        }
        if (lastAppId != 0) {
            am.moveTaskToFront(lastAppId, am.MOVE_TASK_NO_USER_ACTION);
        }
    }

    private Handler H = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {

            }
        }
    };

}
