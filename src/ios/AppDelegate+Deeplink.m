#import "AppDelegate.h"
#import "DeeplinkPlugin.h"
#import <objc/runtime.h>
#import "MainViewController.h"

static NSString *const PLUGIN_NAME = @"DeeplinkPlugin";
/**
 *  Category for the AppDelegate that overrides application:continueUserActivity:restorationHandler method,
 *  so we could handle application launch when user clicks on the link in the browser.
 */
@interface AppDelegate (DeeplinkPlugin)
- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions;
- (void)applicationDidBecomeActive:(UIApplication *)application;
- (BOOL)application:(UIApplication *)application openURL:(NSURL *)url sourceApplication:(NSString *)sourceApplication annotation:(id)annotation;
- (BOOL)application:(UIApplication *)application continueUserActivity:(NSUserActivity *)userActivity restorationHandler:(void (^)(NSArray * _Nullable))restorationHandler;
- (void)application:(UIApplication *)application didReceiveRemoteNotification:(NSDictionary *)userInfo;
@property (nonatomic,retain) NSURL *launchedURL;
@end

@implementation AppDelegate (DeeplinkPlugin)

static id launchedURL;

- (NSURL *)launchedURL {
    return objc_getAssociatedObject(self, &launchedURL);
}

- (void)setLaunchedURL:(NSURL *)url {
    objc_setAssociatedObject(self, &launchedURL, url, OBJC_ASSOCIATION_RETAIN_NONATOMIC) ;
}

- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    if (launchOptions)
    {
        if ([[launchOptions allKeys] containsObject:UIApplicationLaunchOptionsRemoteNotificationKey])
        {
            launchedURL = [NSURL URLWithString:[launchOptions[UIApplicationLaunchOptionsRemoteNotificationKey] objectForKey:@"a4surl"]];
        }
    }
    self.viewController = [[MainViewController alloc] init];
    return [super application:application didFinishLaunchingWithOptions:launchOptions];
}

- (void)applicationDidBecomeActive:(UIApplication *)application {
    if (launchedURL) {
        DeeplinkPlugin *plugin = [self.viewController getCommandInstance:PLUGIN_NAME];

        if(plugin == nil) {
            NSLog(@"Unable to get instance of command plugin");
            return;
        }
        BOOL handled = [plugin handleLink:launchedURL];

        if(!handled) {
            // Pass event through to Cordova
            [[NSNotificationCenter defaultCenter] postNotification:[NSNotification notificationWithName:CDVPluginHandleOpenURLNotification object:launchedURL]];

            // Send notice to the rest of our plugin that we didn't handle this URL
            [[NSNotificationCenter defaultCenter] postNotification:[NSNotification notificationWithName:@"LinksUnhandledURL" object:[launchedURL absoluteString]]];
        }

        launchedURL = nil;
    }
    return;
}

- (BOOL)application:(UIApplication *)application openURL:(NSURL *)url sourceApplication:(NSString *)sourceApplication annotation:(id)annotation {
    DeeplinkPlugin *plugin = [self.viewController getCommandInstance:PLUGIN_NAME];

    if(plugin == nil) {
        NSLog(@"Unable to get instance of command plugin");
        return NO;
    }
    BOOL handled = [plugin handleLink:url];

    if(!handled) {
        // Pass event through to Cordova
        [[NSNotificationCenter defaultCenter] postNotification:[NSNotification notificationWithName:CDVPluginHandleOpenURLNotification object:url]];

        // Send notice to the rest of our plugin that we didn't handle this URL
        [[NSNotificationCenter defaultCenter] postNotification:[NSNotification notificationWithName:@"LinksUnhandledURL" object:[url absoluteString]]];
    }

    return YES;
}

- (BOOL)application:(UIApplication *)application continueUserActivity:(NSUserActivity *)userActivity restorationHandler:(void (^)(NSArray *restorableObjects))restorationHandler {
    // Pass it off to our plugin
    DeeplinkPlugin *plugin = [self.viewController getCommandInstance:PLUGIN_NAME];
    if(plugin == nil) {
        return NO;
    }
    BOOL handled = [plugin handleContinueUserActivity:userActivity];

    if(!handled) {
        // Continue sending the openURL request through
    }
    return YES;
}

- (void)application:(UIApplication *)application didReceiveRemoteNotification:(NSDictionary *)userInfo {
    // Pass the push notification to the plugin
}

@end