import 'usb_camera_method_channel.dart';

class UsbCamera extends MethodChannelUsbCamera {
  static UsbCamera? _instance;
  UsbCamera._();

  static UsbCamera get instance => _instance ??= UsbCamera._();
}
