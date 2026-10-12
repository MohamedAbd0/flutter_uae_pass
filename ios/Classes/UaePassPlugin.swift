import Flutter
import UIKit
import WebKit
import Foundation

public class UaePassPlugin: NSObject, FlutterPlugin, FlutterSceneLifeCycleDelegate {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "uae_pass", binaryMessenger: registrar.messenger())
    let instance = UaePassPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
    // Apps using the UIScene life cycle (required from iOS 27) deliver URLs to the scene delegate,
    // older app-delegate based apps deliver them to the application delegate.
    registrar.addApplicationDelegate(instance)
    registrar.addSceneDelegate(instance)
  }

  func getUaePassTokenForCode(code: String, result: @escaping FlutterResult) {
    UAEPASSNetworkRequests.shared.getUAEPassToken(code: code, completion: { (uaePassToken) in
      if let uaePassToken = uaePassToken, let accessToken = uaePassToken.accessToken {
        result(String(accessToken))
      } else {
        result(FlutterError(code: "ERROR", message: "Unable to get user token, Please try again.", details: nil))
      }
    }) { (error) in
      result(FlutterError(code: "ERROR", message: "Unable to get user token, Please try again.", details: nil))
    }
  }

  func getUaePassProfileForToken(token: String, result: @escaping FlutterResult) {
    UAEPASSNetworkRequests.shared.getUAEPassUserProfile(token: token, completion: { (userProfile) in
      if let userProfile = userProfile {
        // Encode the userProfile object into JSON data
        do {
          let userProfileData = try JSONEncoder().encode(userProfile)
          if let userProfileString = String(data: userProfileData, encoding: .utf8) {
            // Pass the userProfileString to Flutter via FlutterResult
            result(userProfileString)
          } else {
            // Error encoding JSON string
            result(FlutterError(code: "ERROR", message: "Failed to encode userProfile as JSON string", details: nil))
          }
        } catch {
          // Error encoding userProfile to JSON data
          result(FlutterError(code: "ERROR", message: "Failed to encode userProfile as JSON data", details: nil))
        }
      } else {
        result(FlutterError(code: "ERROR", message: "Unable to get user profile, Please try again.", details: nil))
      }
    }) { (error) in
      result(FlutterError(code: "ERROR", message: "Unable to get user profile, Please try again.", details: nil))
    }
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    let arguments = call.arguments as? [String: Any] ?? [:]
    switch call.method {
    case "set_up_environment":
      // map the arguments to the expected data type
      let clientID = arguments["client_id"] as? String ?? ""
      let clientSecret = arguments["client_secret"] as? String ?? ""
      let environment = arguments["environment"] as? String ?? ""
      let env = environment == "production" ? UAEPASSEnvirnonment.production : UAEPASSEnvirnonment.staging
      let redirectUri = arguments["redirect_url"] as? String ?? ""
      let state = arguments["state"] as? String ?? ""
      let scope = arguments["scope"] as? String ?? ""
      let scheme = arguments["scheme"] as? String ?? ""
      let language = arguments["language"] as? String ?? "en"

      UAEPASSRouter.shared.environmentConfig = UAEPassConfig(clientID: clientID, clientSecret: clientSecret, env: env)

      UAEPASSRouter.shared.spConfig = SPConfig(redirectUriLogin: redirectUri,
                                               scope: scope,
                                               state: state,
                                               successSchemeURL: scheme + "://",
                                               failSchemeURL: scheme + "://",
                                               signingScope: "urn:safelayer:eidas:sign:process:document",
                                               language: language)
      result(nil)
    case "access_token":
      guard let code = arguments["code"] as? String else {
        result(FlutterError(code: "ERROR", message: "Authorization code is missing.", details: nil))
        return
      }
      getUaePassTokenForCode(code: code, result: result)
    case "profile":
      guard let token = arguments["token"] as? String else {
        result(FlutterError(code: "ERROR", message: "Access token is missing.", details: nil))
        return
      }
      getUaePassProfileForToken(token: token, result: result)
    case "sign_out":
      HTTPCookieStorage.shared.removeCookies(since: Date.distantPast)
      WKWebsiteDataStore.default().fetchDataRecords(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes()) { records in
        records.forEach { record in
          WKWebsiteDataStore.default().removeData(ofTypes: record.dataTypes, for: [record], completionHandler: {})
        }
      }
      UAEPASSRouter.shared.uaePassToken = nil
      result(true)
    case "sign_in":
      signIn(result: result)
    default:
      result(FlutterMethodNotImplemented)
    }
  }

  private func signIn(result: @escaping FlutterResult) {
    guard let presenter = UserInterfaceInfo.topViewController(),
          let webVC = UAEPassWebViewController.instantiate() as? UAEPassWebViewController else {
      result(FlutterError(code: "ERROR", message: "Unable to present UAE PASS login.", details: nil))
      return
    }
    // Wrap in navigation controller for full screen presentation with back button
    let navigationController = UINavigationController(rootViewController: webVC)
    navigationController.modalPresentationStyle = .fullScreen

    webVC.urlString = UAEPassConfiguration.getServiceUrlForType(serviceType: .loginURL)
    webVC.onUAEPassSuccessBlock = { (code: String?) -> Void in
      navigationController.dismiss(animated: true)
      if let code = code {
        result(String(code))
      } else {
        result(FlutterError(code: "ERROR", message: "Unable to get user token, Please try again.", details: nil))
      }
    }
    webVC.onUAEPassFailureBlock = { (response: String?) -> Void in
      navigationController.dismiss(animated: true)
      result(FlutterError(code: "ERROR", message: response, details: nil))
    }
    webVC.loadViewIfNeeded()
    webVC.reloadwithURL(url: webVC.urlString)

    presenter.present(navigationController, animated: true)
  }

  // MARK: - URL handling (return from the UAE PASS app)

  public func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
    return handleOpenURL(url)
  }

  public func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) -> Bool {
    return URLContexts.contains { handleOpenURL($0.url) }
  }

  private func handleOpenURL(_ url: URL) -> Bool {
    let successScheme = HandleURLScheme.externalURLSchemeSuccess()
    let failureScheme = HandleURLScheme.externalURLSchemeFail()
    if !successScheme.isEmpty && url.absoluteString.contains(successScheme) {
      if let webViewController = UserInterfaceInfo.topViewController() as? UAEPassWebViewController {
        webViewController.forceReload()
      }
      return true
    } else if !failureScheme.isEmpty && url.absoluteString.contains(failureScheme) {
      guard let webViewController = UserInterfaceInfo.topViewController() as? UAEPassWebViewController else { return false }
      webViewController.foreceStop()
      return true
    }
    return false
  }
}
