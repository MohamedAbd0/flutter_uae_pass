//
//  UserInterfaceInfo.swift
//  UaePassDemo
//
//  Created by Mohammed Gomaa on 5/8/19.
//  Copyright © 2019 Mohammed Gomaa. All rights reserved.
//

import UIKit

@objc public class UserInterfaceInfo: NSObject {
    @objc public class func topViewController() -> UIViewController? {
        let windows = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
        guard let windowRootViewController = (windows.first { $0.isKeyWindow } ?? windows.first)?.rootViewController else {
            return nil
        }
        return findTopViewController(candidateViewController: windowRootViewController)
    }
    
    private class func findTopViewController(candidateViewController: UIViewController) -> UIViewController {
        if let presentedVC = candidateViewController.presentedViewController {
            return findTopViewController(candidateViewController: presentedVC)
        } else if let tabBarVC = candidateViewController as? UITabBarController,
            let selectedVC = tabBarVC.selectedViewController {
            return findTopViewController(candidateViewController: selectedVC)
            
        } else if let navigationVC = candidateViewController as? UINavigationController,
            let visibleVC = navigationVC.visibleViewController {
            return findTopViewController(candidateViewController: visibleVC)
        } else {
            return candidateViewController
        }
    }
}
