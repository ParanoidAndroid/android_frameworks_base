package com.android.server.hybird;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.os.Handler;
import android.os.IHybridService;
import android.os.Looper;
import android.os.Message;

import android.os.AsyncTask;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.AtomicFile;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;
import android.util.TimeUtils;
import android.view.WindowManager;
import android.util.Xml;

import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.XmlUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.util.List;
import 	android.content.pm.PackageInfo;
import android.content.pm.PackageManager;


public class HybridService extends IHybridService.Stub {
    private static final String TAG = "HybridService";
    static final boolean DEBUG = true;

    static final long WRITE_DELAY = DEBUG ? 1000 : 60*1000;
    static final String FILE_NAME = "hybrid-props.xml";

    private static final File dataDir = Environment.getDataDirectory();
    private static final File systemDir = new File(dataDir, "system");

    private static  AtomicFile mFile = null;
    private Handler mHandler = null;
    private static PackageManager pm = null;
    Context mContext = null;

    final HashMap<String, Props> mProps = new HashMap<String, Props>();

    String packageName = "com.android.systemui";

    boolean mWriteScheduled;
    final Runnable mWriteRunner = new Runnable() {
        public void run() {
            synchronized (HybridService.this) {
                mWriteScheduled = false;
                AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                    @Override protected Void doInBackground(Void... params) {
                        writeState();
                        return null;
                    }
                };
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
            }
        }
    };

    public void setPackage(String name) {
        packageName = name; 
    }

    public HybridService(Context context) {
        mContext = context; 
        AtomicFile mFile = new AtomicFile(new File (systemDir, FILE_NAME));
        mHandler = new Handler();
        pm = mContext.getPackageManager();
    }

    public final static class Props {
         final String packageName;
         boolean expanded;
         boolean active;
         int dpi;
         int layout;
         String statusBarColor;
         String statusBarIconColor;
         String statusBarButtonGlowColor;
         String navBarColor;
         String navBarButtonColor;
         String navBarGlowColor;

        public Props(String pname) {
            packageName = pname;
        }
    }


    public void testWrite() { 
        writeState();
    }


 // ================================================================================
    // Always called from UI thread
    // ================================================================================

    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump Hybrid from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }
    }

    public void shutdown() {
        Slog.w(TAG, "Writing Hybird props before shutdown...");
        boolean doWrite = false;
        synchronized (this) {
            if (mWriteScheduled) {
                mWriteScheduled = false;
                doWrite = true;
            }        }
        if (doWrite) {
            writeState();
        }
    }

    void readState() {
        synchronized (mFile) {
            synchronized (this) {
                FileInputStream stream;
                try {
                    stream = mFile.openRead();
                } catch (FileNotFoundException e) {
                    Slog.i(TAG, "No existing app Hybrid Props " + mFile.getBaseFile() + "; starting empty");
                    return;
                }
                boolean success = false;
                try {
                    XmlPullParser parser = Xml.newPullParser();
                    parser.setInput(stream, null);
                    int type;
                    while ((type = parser.next()) != XmlPullParser.START_TAG
                            && type != XmlPullParser.END_DOCUMENT) {
                        ;
                    }

                    if (type != XmlPullParser.START_TAG) {
                        throw new IllegalStateException("no start tag found");
                    }

                    int outerDepth = parser.getDepth();
                    while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                            && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                        if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                            continue;
                        }

                        String tagName = parser.getName();
                        if (tagName.equals("pkg")) {
                            readPackages(parser);
                        } else {
                            Slog.w(TAG, "Unknown element under <hybrid-props>: "
                                    + parser.getName());
                            XmlUtils.skipCurrentTag(parser);
                        }
                    }
                    success = true;
                } catch (IllegalStateException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (NullPointerException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (NumberFormatException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (XmlPullParserException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (IOException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (IndexOutOfBoundsException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } finally {
                    if (!success) {
                        mProps.clear();
                    }
                    try {
                        stream.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
    }

    void readPackages(XmlPullParser parser) throws NumberFormatException,
            XmlPullParserException, IOException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if (tagName.equals("pkg")) {
                String pkgName = parser.getAttributeValue(null, "pkg");
                Props prop = new Props(pkgName);

                String active = parser.getAttributeValue(null, "ac");
                if (active != null) {
                    prop.active = Boolean.parseBoolean(active);
                }
                String expanded = parser.getAttributeValue(null, "ex");
                if (expanded != null) {
                    prop.expanded = Boolean.parseBoolean(expanded);
                }
                String dpi = parser.getAttributeValue(null, "di");
                if (dpi != null) {
                    prop.dpi = Integer.parseInt(dpi);
                }
                String layout = parser.getAttributeValue(null, "lt");
                if (layout != null) {
                    prop.layout = Integer.parseInt(layout);
                }
                String statusBarColor = parser.getAttributeValue(null, "sb");
                if (statusBarColor != null) {
                    prop.statusBarColor = statusBarColor;
                }
                String statusBarIconColor = parser.getAttributeValue(null, "si");
                if (statusBarIconColor != null) {
                    prop.statusBarIconColor = statusBarIconColor;
                }
                String navBarColor = parser.getAttributeValue(null, "nb");
                if (navBarColor != null) {
                    prop.navBarColor = navBarColor;
                }
                String navBarButtonColor = parser.getAttributeValue(null, "nc");
                if (navBarButtonColor != null) {
                    prop.navBarButtonColor = navBarButtonColor;
                }
                String navBarGlowColor = parser.getAttributeValue(null, "ng");
                if (navBarGlowColor != null) {
                    prop.navBarGlowColor = navBarGlowColor;
                }
                mProps.put("pkgName", prop);
            } else {
                Slog.w(TAG, "Unknown element under <pkg>: "
                        + parser.getName());
                XmlUtils.skipCurrentTag(parser);
            }
        }
    }

 
    void writeState() {
        synchronized (mFile) {

        List<PackageInfo> packages = pm.getInstalledPackages(0);

            FileOutputStream stream;
            try {
                stream = mFile.startWrite();
            } catch (IOException e) {
                Slog.w(TAG, "Failed to write state: " + e);
                return;
            }

            try {
                XmlSerializer out = new FastXmlSerializer();
                out.setOutput(stream, "utf-8");
                out.startDocument(null, true);
                out.startTag(null, "hybrid-props");

                if (packages != null) {
                    PackageInfo p;
                    for (int i=0; i<packages.size(); i++) {
                        out.startTag(null, "pkg");
                        p = packages.get(i);
                        out.attribute(null, "p", p.packageName);
                        out.endTag(null, "pkg");
                    }
                }

                out.endTag(null, "hybrid-props");
                out.endDocument();
                mFile.finishWrite(stream);
            } catch (IOException e) {
                Slog.w(TAG, "Failed to write state, restoring backup.", e);
                mFile.failWrite(stream);
            }
        }
    }

    public Props getProp(String packageName) {
        Props prop = mProps.get(packageName);
        if (prop == null) {
            prop = new Props(packageName);
            mProps.put(packageName, prop);
        }
        return prop;
    }

    public HybridService() {
        //mHandler = new Handler();
        readState();
    }

   public boolean getActive() {
        //return getProp(packageName).active
        return true;
    }

  public  boolean isTablet() { 
         // if (getProp(SYSTEMUI).dpi)
         //    return false;
         //   }
        return true;

    }

   public boolean isExpanded() {
        // return getProp(packageName).expanded;
        return false;
    }

    public void setExpanded(boolean expanded) {
          getProp(packageName).expanded = expanded;
    }

  public  int getLayout() {
        return 360;
    }

  public  void setLayout(String layout) {
       // getProp(packageName).layout = layout;
    }

  public  int getDpi() {
        // return getProp(packageName).dpi;
        return 320;

    }

  public  void setDpi(String dpi) {
         // getProp(SYSTEMUI).dpi = dpi;
    }

  public  int getDensity() {
        return 320;
    }

 public   int getScaledDensity() {
        return 320;
    }

/*
    public void packageRemoved(String packageName) {
        synchronized (this) {
            HashMap<String, Ops> pkgs = mpRs.get(uid);
            if (pkgs != null) {
                if (pkgs.remove(packageName) != null) {
                    if (pkgs.size() <= 0) {
                        mUidOps.remove(uid);
                    }
                    scheduleWriteLocked();
                }
            }
        }
    }
*/

    /**
    * 
    */
	public void setNavbarColor(String val) {
        getProp(packageName).navBarColor = val;
    }

    /**
    * 
    */
	public String getNavbarColor(String val) {
        return getProp(packageName).navBarColor;
    }

    /**
    * 
    */
	public void setStatusBarColor(String val) {
        getProp(packageName).statusBarColor = val;
    }

    /**
    * 
    */
	public String getStatusBarColor(String val) {
        return getProp(packageName).statusBarColor;
    }

}
