import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_uae_pass/flutter_uae_pass.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String? authCode;
  String? accessToken;
  ProfileData? profileData;

  final _uaePass = UaePass();

  @override
  void initState() {
    super.initState();
    _uaePass.setUpSandbox(); // This uses staging - correct for your staging app
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('UAE Pass'),
          centerTitle: true,
        ),
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              MaterialButton(
                onPressed: () => login(),
                child: const Text('Sign in with UAE Pass'),
              ),
              const SizedBox(height: 100),
              if (authCode != null)
                ListTile(
                  title: const Text('Auth Code'),
                  subtitle: Text('${authCode?.substring(0, 6)}............'),
                ),
              if (accessToken != null)
                ListTile(
                  title: const Text('Access Token'),
                  subtitle: Text('${accessToken?.substring(0, 6)}............'),
                ),
              if (profileData != null)
                Column(
                  children: [
                    ListTile(
                      title: const Text('Emirates Id'),
                      subtitle: Text(profileData?.idn ?? ""),
                    ),
                    ListTile(
                      title: const Text('Full Name EN'),
                      subtitle: Text(profileData?.fullnameEN ?? ""),
                    ),
                    ListTile(
                      title: const Text('Full Name AR'),
                      subtitle: Text(profileData?.fullnameAR ?? ""),
                    ),
                    ListTile(
                      title: const Text('Mobile'),
                      subtitle: Text(profileData?.mobile ?? ""),
                    ),
                    ListTile(
                      title: const Text('Nationality EN'),
                      subtitle: Text(profileData?.nationalityEN ?? ""),
                    ),
                  ],
                ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> login() async {
    authCode = null;
    accessToken = null;
    profileData = null;
    setState(() {});
    try {
      authCode = await _uaePass.signIn();
      accessToken = await _uaePass.getAccessToken(authCode ?? "");
      profileData = await _uaePass.getProfile(accessToken ?? "");

      setState(() {});
    } catch (e) {
      if (kDebugMode) {
        print(e.toString());
      }
    }
    setState(() {});
  }
}
