import 'package:flutter/foundation.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'usb_camera_method_channel.dart';

abstract class UsbCameraPlatform extends PlatformInterface {
  /// Constructs a UsbCameraPlatform.
  UsbCameraPlatform() : super(token: _token);

  static final Object _token = Object();

  static UsbCameraPlatform _instance = MethodChannelUsbCamera();

  /// The default instance of [UsbCameraPlatform] to use.
  ///
  /// Defaults to [MethodChannelUsbCamera].
  static UsbCameraPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [UsbCameraPlatform] when
  /// they register themselves.
  static set instance(UsbCameraPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  /// To listen to the image stream
  Stream<Uint8List?> get imageStream;

  /// To listen to the video stream
  Stream<Uint8List?> get videoStream;

  /// Method to list available cameras
  Future<List<Map<String, dynamic>>> list();

  /// Method to open a camera
  Future<void> open(String cameraId);

  /// Method to close the camera
  Future<void> close();

  /// Method to start capture
  Future<void> startCapture();

  /// Method to stop capture
  Future<void> stopCapture();
}
