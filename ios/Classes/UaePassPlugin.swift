import Flutter
import UIKit
import WebKit
import Foundation

public class UaePassPlugin: NSObject, FlutterPlugin {
  private var flutterResult:FlutterResult?
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "uae_pass", binaryMessenger: registrar.messenger())
    let instance = UaePassPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
    registrar.addApplicationDelegate(instance)
  }
 func getUaePassTokenForCode(code: String) {
        
        UAEPASSNetworkRequests.shared.getUAEPassToken(code: code, completion: { (uaePassToken) in
            if let uaePassToken = uaePassToken, let accessToken = uaePassToken.accessToken {
              self.flutterResult!(String(accessToken))    
            } else {
              self.flutterResult!(FlutterError(code: "ERROR", message:"Unable to get user token, Please try again.",details: nil)) 
                 return
            }
        }) { (error) in
            self.flutterResult!(FlutterError(code: "ERROR",message:"Unable to get user token, Please try again.",details: nil))
            return
         }
    }

        func getUaePassProfileForToken(token: String) {
          UAEPASSNetworkRequests.shared.getUAEPassUserProfile(token: token, completion: { (userProfile) in
            if let userProfile = userProfile {
            // Encode the userProfile object into JSON data
            do {
                let userProfileData = try JSONEncoder().encode(userProfile)
                if let userProfileString = String(data: userProfileData, encoding: .utf8) {
                    // Pass the userProfileString to Flutter via FlutterResult
                    self.flutterResult!(userProfileString)
                } else {
                    // Error encoding JSON string
                    self.flutterResult!(FlutterError(code: "ERROR", message: "Failed to encode userProfile as JSON string", details: nil))
                }
            } catch {
                // Error encoding userProfile to JSON data
                self.flutterResult!(FlutterError(code: "ERROR", message: "Failed to encode userProfile as JSON data", details: nil))
            }
            } else {
              self.flutterResult!(FlutterError(code: "ERROR", message:"Unable to get user profile, Please try again.",details: nil)) 
              return
            }
        }) { (error) in
              self.flutterResult!(FlutterError(code: "ERROR", message:"Unable to get user profile, Please try again.",details: nil)) 
              return

        }
    }



  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    self.flutterResult=result
    switch call.method {
    case "set_up_environment":
    // map the arguments to the expected data type
      if let arguments = call.arguments as? [String: Any]{
        let clientID = arguments["client_id"] as! String

        let clientSecret = arguments["client_secret"] as! String
        let environment = arguments["environment"] as! String
        let env = environment == "production" ? UAEPASSEnvirnonment.production : UAEPASSEnvirnonment.staging
        let redirectUriLogin = arguments["redirect_uri_login"] as! String
        let redirectUri = arguments["redirect_url"] as! String
        let state = arguments["state"] as! String
        let scope = arguments["scope"] as! String
        let scheme = arguments["scheme"] as! String
        let language = arguments["language"] as! String

        UAEPASSRouter.shared.environmentConfig = UAEPassConfig(clientID: clientID, clientSecret: clientSecret, env: env)

        UAEPASSRouter.shared.spConfig = SPConfig(redirectUriLogin: redirectUri,
                                                 scope: scope,
                                                 state:state,  
                                                 successSchemeURL: scheme+"://",
                                                 failSchemeURL: scheme+"://",
                                                 signingScope: "urn:safelayer:eidas:sign:process:document",
                                                 language: language)
      }
    case "access_token":
      if let arguments = call.arguments as? [String: Any]{
        let code = arguments["code"] as! String
        self.getUaePassTokenForCode(code: code)
      }
    case "profile":
      if let arguments = call.arguments as? [String: Any]{
        let token = arguments["token"] as! String
        self.getUaePassProfileForToken(token: token)
      }

      
    case "sign_out": 
      HTTPCookieStorage.shared.removeCookies(since: Date.distantPast)
      WKWebsiteDataStore.default().fetchDataRecords(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes()) { records in
          records.forEach { record in
              WKWebsiteDataStore.default().removeData(ofTypes: record.dataTypes, for: [record], completionHandler: {})
          }
      }
      UAEPASSRouter.shared.uaePassToken = nil
      self.flutterResult!(true)
    case "sign_in":
    
         
  if let webVC = UAEPassWebViewController.instantiate() as? UAEPassWebViewController {
            webVC.urlString = UAEPassConfiguration.getServiceUrlForType(serviceType: .loginURL)
            webVC.onUAEPassSuccessBlock = {(code: String?) -> Void in
                UIApplication.shared.keyWindow?.rootViewController?.dismiss(animated: true)
                if let code = code {
                    self.flutterResult!(String(code))
                 }
                else{
                    self.flutterResult!(FlutterError(code: "ERROR", message:"Unable to get user token, Please try again.",details: nil)) 
                }
                return
                 
            }
            webVC.onUAEPassFailureBlock = {(response: String?) -> Void in
                UIApplication.shared.keyWindow?.rootViewController?.dismiss(animated: true)
                 self.flutterResult!(FlutterError(code: "ERROR", message:response,details: nil)) 
            }
            webVC.reloadwithURL(url: webVC.urlString)
            UIApplication.shared.keyWindow?.rootViewController?.present(webVC, animated: true)
        }






    default:
      self.flutterResult!(FlutterMethodNotImplemented)
    }
  }

  
 public func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey : Any] = [:]) -> Bool {   

          print("<><><><> appDelegate URL : \(url.absoluteString)")
    
        if url.absoluteString.contains(HandleURLScheme.externalURLSchemeSuccess()) {
            if let topViewController = UserInterfaceInfo.topViewController() {
                if let webViewController = topViewController as? UAEPassWebViewController {
                    webViewController.forceReload()
                } 
            }
            return true
        } else if url.absoluteString.contains(HandleURLScheme.externalURLSchemeFail()) {
            guard let webViewController = UserInterfaceInfo.topViewController() as? UAEPassWebViewController  else { return false}
            webViewController.foreceStop()
            return false
        }
       return false
   }
}
