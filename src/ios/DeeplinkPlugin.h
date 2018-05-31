#import <Cordova/CDVPlugin.h>
#import <SafariServices/SafariServices.h>

@interface DeeplinkPlugin : CDVPlugin {
  // Handlers for URL events
  NSMutableArray *_handlers;
  CDVPluginResult *_lastEvent;
}

// User-plugin command handler
- (void)canOpenApp:(CDVInvokedUrlCommand *)command;
- (void)onDeepLink:(CDVInvokedUrlCommand *)command;
- (void)getHardwareInfo:(CDVInvokedUrlCommand *)command;

// Internal deeplink and CUA handlers
- (BOOL)handleLink:(NSURL *)url;
- (BOOL)handleContinueUserActivity:(NSUserActivity *)userActivity;

- (void)sendToJs;

- (CDVPluginResult*)createResult:(NSURL *)url;
- (void)isAvailable:(CDVInvokedUrlCommand *)command;
- (void)openUrl:(CDVInvokedUrlCommand *)command;
- (void)close:(CDVInvokedUrlCommand *)command;
@end
