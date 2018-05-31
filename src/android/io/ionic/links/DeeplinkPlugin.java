/**
 * Deeplinks Plugin.
 * License: MIT
 */
package io.ionic.links;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.Date;
import java.util.TimeZone;

import android.util.Log;
import android.content.Intent;
import android.content.Context;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.inputmethod.InputMethodManager;
import android.telephony.TelephonyManager;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Set;
import java.util.Collection;
import java.util.Map;
import java.util.Iterator;
import java.util.List;

import android.content.pm.ResolveInfo;
import android.support.customtabs.CustomTabsIntent;

public class DeeplinkPlugin extends CordovaPlugin {
  private static final String TAG = "DeeplinkPlugin";

  private JSONObject lastEvent;

  private ArrayList<CallbackContext> _handlers = new ArrayList<CallbackContext>();

  public static final int RC_OPEN_URL = 101;

  private static final String LOG_TAG = "BrowserTab";

  /**
   * The service we expect to find on a web browser that indicates it supports custom tabs.
   */
  private static final String ACTION_CUSTOM_TABS_CONNECTION =
    "android.support.customtabs.action.CustomTabsService";

  private boolean mFindCalled = false;
  private String mCustomTabsBrowser;

  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);
    Log.d(TAG, "DeepLinkPlugin: firing up...");

    handleIntent(cordova.getActivity().getIntent());
  }

  @Override
  public void onNewIntent(Intent intent) {
    handleIntent(intent);
  }

  public void handleIntent(Intent intent) {
    final String intentString = intent.getDataString();

    // read intent
    String action = intent.getAction();
    Uri url = intent.getData();
    JSONObject bundleData = this._bundleToJson(intent.getExtras());
    Log.d(TAG, "Got a new intent: " + intentString + " " + intent.getScheme() + " " + action + " " + url);

    // if app was not launched by the url - ignore
    if (!Intent.ACTION_VIEW.equals(action) || url == null) {
      return;
    }

    // store message and try to consume it
    try {
      lastEvent = new JSONObject();
      lastEvent.put("url", url.toString());
      lastEvent.put("path", url.getPath());
      lastEvent.put("queryString", url.getQuery());
      lastEvent.put("scheme", url.getScheme());
      lastEvent.put("host", url.getHost());
      lastEvent.put("fragment", url.getFragment());
      lastEvent.put("extra", bundleData);
      consumeEvents();
    } catch(JSONException ex) {
      Log.e(TAG, "Unable to process URL scheme deeplink", ex);
    }
  }

  public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
    if(action.equals("onDeepLink")) {
      addHandler(args, callbackContext);
    } else if(action.equals("canOpenApp")) {
      String uri = args.getString(0);
      canOpenApp(uri, callbackContext);
    } else if(action.equals("getHardwareInfo")) {
      getHardwareInfo(args, callbackContext);
    } else if ("isAvailable".equals(action)) {
      isAvailable(callbackContext);
    } else if ("openUrl".equals(action)) {
      openUrl(args, callbackContext);
    } else if ("close".equals(action)) {
      // close is a NOP on Android
      return true;
    }
    return true;
  }
  private void isAvailable(CallbackContext callbackContext) {
    String browserPackage = findCustomTabBrowser();
    Log.d(LOG_TAG, "browser package: " + browserPackage);
    callbackContext.sendPluginResult(new PluginResult(
      PluginResult.Status.OK,
      browserPackage != null));
  }

  private void openUrl(JSONArray args, CallbackContext callbackContext) {
    if (args.length() < 1) {
      Log.d(LOG_TAG, "openUrl: no url argument received");
      callbackContext.error("URL argument missing");
      return;
    }

    String urlStr;
    try {
      urlStr = args.getString(0);
    } catch (JSONException e) {
      Log.d(LOG_TAG, "openUrl: failed to parse url argument");
      callbackContext.error("URL argument is not a string");
      return;
    }

    String customTabsBrowser = findCustomTabBrowser();
    if (customTabsBrowser == null) {
      Log.d(LOG_TAG, "openUrl: no in app browser tab available");
      callbackContext.error("no in app browser tab implementation available");
    }

    Intent customTabsIntent = new CustomTabsIntent.Builder().build().intent;
    customTabsIntent.setData(Uri.parse(urlStr));
    customTabsIntent.setPackage(mCustomTabsBrowser);
    cordova.getActivity().startActivity(customTabsIntent);

    Log.d(LOG_TAG, "in app browser call dispatched");
    callbackContext.success();
  }

  private String findCustomTabBrowser() {
    if (mFindCalled) {
      return mCustomTabsBrowser;
    }

    PackageManager pm = cordova.getActivity().getPackageManager();
    Intent webIntent = new Intent(
      Intent.ACTION_VIEW,
      Uri.parse("http://www.example.com"));
    List<ResolveInfo> resolvedActivityList =
      pm.queryIntentActivities(webIntent, PackageManager.GET_RESOLVED_FILTER);

    for (ResolveInfo info : resolvedActivityList) {
      if (!isFullBrowser(info)) {
        continue;
      }

      if (hasCustomTabWarmupService(pm, info.activityInfo.packageName)) {
        mCustomTabsBrowser = info.activityInfo.packageName;
        break;
      }
    }

    mFindCalled = true;
    return mCustomTabsBrowser;
  }

  private boolean isFullBrowser(ResolveInfo resolveInfo) {
    // The filter must match ACTION_VIEW, CATEGORY_BROWSEABLE, and at least one scheme,
    if (!resolveInfo.filter.hasAction(Intent.ACTION_VIEW)
      || !resolveInfo.filter.hasCategory(Intent.CATEGORY_BROWSABLE)
      || resolveInfo.filter.schemesIterator() == null) {
      return false;
    }

    // The filter must not be restricted to any particular set of authorities
    if (resolveInfo.filter.authoritiesIterator() != null) {
      return false;
    }

    // The filter must support both HTTP and HTTPS.
    boolean supportsHttp = false;
    boolean supportsHttps = false;
    Iterator<String> schemeIter = resolveInfo.filter.schemesIterator();
    while (schemeIter.hasNext()) {
      String scheme = schemeIter.next();
      supportsHttp |= "http".equals(scheme);
      supportsHttps |= "https".equals(scheme);

      if (supportsHttp && supportsHttps) {
        return true;
      }
    }

    // at least one of HTTP or HTTPS is not supported
    return false;
  }

  private boolean hasCustomTabWarmupService(PackageManager pm, String packageName) {
    Intent serviceIntent = new Intent();
    serviceIntent.setAction(ACTION_CUSTOM_TABS_CONNECTION);
    serviceIntent.setPackage(packageName);
    return (pm.resolveService(serviceIntent, 0) != null);
  }
  /**
   * Try to consume any waiting intent events by sending them to our plugin
   * handlers. We will only do this if we have active handlers so the message isn't lost.
   */
  private void consumeEvents() {
    if(this._handlers.size() == 0 || lastEvent == null) {
      return;
    }

    for(CallbackContext callback : this._handlers) {
      sendToJs(lastEvent, callback);
    }
    lastEvent = null;
  }

  private void sendToJs(JSONObject event, CallbackContext callback) {
    final PluginResult result = new PluginResult(PluginResult.Status.OK, event);
    result.setKeepCallback(true);
    callback.sendPluginResult(result);
  }

  private void addHandler(JSONArray args, final CallbackContext callbackContext) {
    this._handlers.add(callbackContext);
    this.consumeEvents();
  }

  private JSONObject _bundleToJson(Bundle bundle) {
    if(bundle == null) {
      return new JSONObject();
    }

    JSONObject j = new JSONObject();
    Set<String> keys = bundle.keySet();
    for(String key : keys) {
      try {
        Class<?> jsonClass = j.getClass();
        Class[] cArg = new Class[1];
        cArg[0] = String.class;
        //Workaround for API < 19
        try{
          if(jsonClass.getDeclaredMethod("wrap", cArg) != null){
            j.put(key, JSONObject.wrap(bundle.get(key)));
          }
        }
        catch(NoSuchMethodException e) {
          j.put(key, this._wrap(bundle.get(key)));
        }
      } catch(JSONException ex) {}
    }

    return j;
  }
  //Wrap method not available in JSONObject API < 19
  private Object _wrap(Object o){
    if (o == null) {
      return null;
    }
    if (o instanceof JSONArray || o instanceof JSONObject) {
      return o;
    }
    if (o.equals(null)) {
      return o;
    }
    try {
      if (o instanceof Collection) {
        return new JSONArray((Collection) o);
      } else if (o.getClass().isArray()) {
        return new JSONArray(o);
      }
      if (o instanceof Map) {
        return new JSONObject((Map) o);
      }
      if (o instanceof Boolean ||
        o instanceof Byte ||
        o instanceof Character ||
        o instanceof Double ||
        o instanceof Float ||
        o instanceof Integer ||
        o instanceof Long ||
        o instanceof Short ||
        o instanceof String) {
        return o;
      }
      if (o.getClass().getPackage().getName().startsWith("java.")) {
        return o.toString();
      }
    } catch (Exception ignored) {}
    return null;
  }

  /**
   * Check if we can open an app with a given URI scheme.
   *
   * Thanks to https://github.com/ohh2ahh/AppAvailability/blob/master/src/android/AppAvailability.java
   */
  private void canOpenApp(String uri, final CallbackContext callbackContext) {
    Context ctx = this.cordova.getActivity().getApplicationContext();
    final PackageManager pm = ctx.getPackageManager();

    try {
      pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);
      callbackContext.success();
    } catch(PackageManager.NameNotFoundException e) {}

    callbackContext.error("");
  }

  private void getHardwareInfo(JSONArray args, final CallbackContext callbackContext) {
    String uuid = Settings.Secure.getString(this.cordova.getActivity().getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);

    JSONObject j = new JSONObject();
    try {
      j.put("uuid", uuid);
      j.put("platform", this.getPlatform());
      j.put("tz", this.getTimeZoneID());
      j.put("tz_offset", this.getTimeZoneOffset());
      j.put("os_version", this.getOSVersion());
      j.put("sdk_version", this.getSDKVersion());
    } catch(JSONException ex) {}

    final PluginResult result = new PluginResult(PluginResult.Status.OK, j);
    callbackContext.sendPluginResult(result);
  }

  private boolean isAmazonDevice() {
    if (android.os.Build.MANUFACTURER.equals("Amazon")) {
      return true;
    }
    return false;
  }
  private String getTimeZoneID() {
    TimeZone tz = TimeZone.getDefault();
    return (tz.getID());
  }

  private int getTimeZoneOffset() {
    TimeZone tz = TimeZone.getDefault();
    return tz.getOffset(new Date().getTime()) / 1000 / 60;
  }

  private String getSDKVersion() {
    @SuppressWarnings("deprecation")
    String sdkversion = android.os.Build.VERSION.SDK;
    return sdkversion;
  }
  private String getOSVersion() {
    String osversion = android.os.Build.VERSION.RELEASE;
    return osversion;
  }
  private String getPlatform() {
    String platform;
    if (isAmazonDevice()) {
      platform = "amazon-fireos";
    } else {
      platform = "android";
    }
    return platform;
  }
}
