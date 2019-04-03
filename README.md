# Description
This plugin is a fusion of [cordova-plugin-deeplinks](https://github.com/e-imaxina/cordova-plugin-deeplinks) and [cordova-plugin-browsertab](https://github.com/google/cordova-plugin-browsertab).

The plugin is used to operate a redirection via browserTab on a deeplink.

# Usage
## BrowserTab
```
public close() {
 (<any>window).DeeplinkPlugin.close();
}

private _open(url: string) {
 (<any>window).DeeplinkPlugin.openUrl(url);
}
```
## Deeplink
```
const routes = {
    '/callback': 'KeycloakController',
    '/callback-logout': 'KeycloakController',
    'profile': 'ProfilePage',
    'my-orders': 'OrdersPage',
};
(<any>window).DeeplinkPlugin.route(routes,
    (match) => {
        console.log('DEEPLINKS MATCH !!', match);
    },
    (nomatch) => {
        console.error('Got a deeplink that didn\'t match', nomatch);
    }
);
```
