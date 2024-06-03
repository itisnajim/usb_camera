import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'usb_camera_platform_interface.dart';

/// An implementation of [UsbCameraPlatform] that uses method channels.
class MethodChannelUsbCamera extends UsbCameraPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('usb_camera');

  // EventChannel for the image stream
  @visibleForTesting
  final imageStreamChannel = const EventChannel('usb_camera/image_stream');

  @override
  late final Stream<Uint8List?> imageStream =
      imageStreamChannel.receiveBroadcastStream().map((event) {
    return event as Uint8List?;
  });

  // EventChannel for the image stream
  @visibleForTesting
  final videoStreamChannel = const EventChannel('usb_camera/video_stream');

  @override
  late final Stream<Uint8List?> videoStream =
      videoStreamChannel.receiveBroadcastStream().map((event) {
    return event as Uint8List?;
  });

  @override
  Future<List<Map<String, dynamic>>> list() async {
    final List<dynamic> cameras = await methodChannel.invokeMethod('list');
    return cameras.map((c) => (c as Map).cast<String, dynamic>()).toList();
  }

  @override
  Future<void> open(String cameraId) async {
    await methodChannel.invokeMethod('open', {'cameraId': cameraId});
  }

  @override
  Future<void> close() async {
    await methodChannel.invokeMethod('close');
  }

  @override
  Future<void> startCapture() async {
    await methodChannel.invokeMethod('startCapture');
  }

  @override
  Future<void> stopCapture() async {
    await methodChannel.invokeMethod('stopCapture');
  }
}
