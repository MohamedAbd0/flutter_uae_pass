import 'dart:convert';
import 'package:flutter/services.dart';
import 'core/uae_pass_platform_interface.dart';
import 'model/profile_data.dart';
export 'core/export.dart';
export 'model/export.dart';

class UaePass {
  Future<void> setUpSandbox({String language = "en"}) async {
    await UaePassPlatform.instance.setUp(
      clientId: "sandbox_stage",
      clientSecret: "sandbox_stage",
      isProduction: false,
      urlScheme: "uaepassdemoappDS",
      state: "123123213",
      redirectUri: "https://oauthtest.com/authorization/return",
      scope: "urn:uae:digitalid:profile",
      language: language,
    );
  }

  Future<void> setUpEnvironment({
    required String clientId,
    required String clientSecret,
    required String urlScheme,
    String state = "HnlHOJTkTb66Y5H",
    bool isProduction = false,
    String redirectUri = "https://oauthtest.com/authorization/return",
    String scope = "urn:uae:digitalid:profile",
    String language = "en",
  }) async {
    await UaePassPlatform.instance.setUp(
      clientId: clientId,
      clientSecret: clientSecret,
      isProduction: isProduction,
      urlScheme: urlScheme,
      state: state,
      redirectUri: redirectUri,
      scope: scope,
      language: language,
    );
  }

  Future<String> signIn() async {
    try {
      return await UaePassPlatform.instance.signIn();
    } on PlatformException catch (e) {
      throw (e.message ?? "Unknown error");
    } catch (e) {
      throw (e.toString());
    }
  }

  Future<String> getAccessToken(String token) async {
    try {
      return await UaePassPlatform.instance.getAccessToken(token);
    } on PlatformException catch (e) {
      throw (e.message ?? "Unknown error");
    } catch (e) {
      throw (e.toString());
    }
  }

  Future<ProfileData?> getProfile(String accessToken) async {
    try {
      final result = await UaePassPlatform.instance.getProfile(accessToken);
      return ProfileData.fromJson(json.decode(result));
    } on PlatformException catch (e) {
      throw (e.message ?? "Unknown error");
    } catch (e) {
      throw (e.toString());
    }
  }

  Future<void> signOut() async {
    try {
      return await UaePassPlatform.instance.signOut();
    } on PlatformException catch (e) {
      throw (e.message ?? "Unknown error");
    } catch (e) {
      throw (e.toString());
    }
  }
}
