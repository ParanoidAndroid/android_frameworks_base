/*
* aidl file : frameworks/base/core/java/android/os/IHybridService.aidl
* This file contains definitions of functions which are exposed by service 
*/
package android.os;


interface IHybridService {

    boolean getActive();

    boolean isTablet();

    boolean isExpanded();

    void setExpanded(boolean val);

    int getLayout();

    void setLayout(String val);

    int getDpi();

    void setDpi(String val);

    int getDensity();

    int getScaledDensity();

	void setNavbarColor(String val);

	String getNavbarColor(String val);

	void setStatusBarColor(String val);

	String getStatusBarColor(String val);

    void setPackage(String name);

    void testWrite();


}
