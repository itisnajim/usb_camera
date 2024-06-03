import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:usb_camera/usb_camera_platform_interface.dart';
import 'package:usb_camera/usb_camera_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockUsbCameraPlatform
    with MockPlatformInterfaceMixin
    implements UsbCameraPlatform {
  @override
  Future<void> close() async {}

  @override
  Future<List<Map<String, dynamic>>> list() async => [];

  @override
  Future<void> open(String cameraId) async {}

  @override
  Future<void> startCapture() async {}

  @override
  Future<void> stopCapture() async {}

  @override
  Stream<Uint8List?> get imageStream => Stream.value(null);

  @override
  Stream<Uint8List?> get videoStream => Stream.value(null);
}

void main() {
  final UsbCameraPlatform initialPlatform = UsbCameraPlatform.instance;

  test('$MethodChannelUsbCamera is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelUsbCamera>());
  });
}
