import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:usb_camera/usb_camera_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  MethodChannelUsbCamera platform = MethodChannelUsbCamera();
  const MethodChannel channel = MethodChannel('usb_camera');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      channel,
      (MethodCall methodCall) async {
        return '42';
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });
}
