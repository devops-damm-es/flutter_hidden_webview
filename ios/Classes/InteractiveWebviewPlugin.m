#import "InteractiveWebviewPlugin.h"
#if __has_include(<interactive_webview/interactive_webview-Swift.h>)
#import <interactive_webview/interactive_webview-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "interactive_webview-Swift.h"
#endif

@implementation InteractiveWebviewPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftInteractiveWebviewPlugin registerWithRegistrar:registrar];
}
@end
