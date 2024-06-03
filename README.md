# usb_camera

The `usb_camera` plugin is an Android plugin for Flutter that allows you to use external USB cameras (webcams). This plugin facilitates capturing images and videos from USB-connected cameras and streaming the data directly into your Flutter application.


## Features
* List available USB cameras.
* Open and close USB cameras.
* Capture images from the camera.
* Stream camera feed to the app.


## Getting Started
```yaml
dependencies:
  flutter:
    sdk: flutter
  usb_camera:
    git:
      url: https://github.com/itisnajim/usb_camera.git
      ref: main
  permission_handler: ^<latest_version>
```

## Methods

### `list()`
```dart
Future<List<Map<String, dynamic>>> list()
```
Lists all available USB cameras connected to the device. Returns a `Future` that resolves to a list of camera details, where each camera is represented as a `Map<String, dynamic>`.

### `open(String cameraId)`
```dart
Future<void> open(String cameraId)
```
Opens the specified camera. Takes a `cameraId` as a parameter, which is the ID of the camera you want to open. Returns a `Future` that completes when the camera is successfully opened.

### `close()`
```dart
Future<void> close()
```
Closes the currently open camera. Returns a `Future` that completes when the camera is successfully closed.

### `startCapture()`
```dart
Future<void> startCapture()
```
Starts capturing images from the currently open camera. Returns a `Future` that completes when the capture starts successfully.

### `stopCapture()`
```dart
Future<void> stopCapture()
```
Stops capturing images from the currently open camera. Returns a `Future` that completes when the capture stops successfully.

### `imageStream`
```dart
late final Stream<Uint8List?> imageStream
```
A `Stream` that provides image data from the camera in the form of `Uint8List`. This stream can be used to display the camera feed in real-time within your application.

### `videoStream`
```dart
late final Stream<Uint8List?> videoStream
```
A `Stream` that provides video data from the camera in the form of `Uint8List`. This stream can be used to handle video data in real-time within your application.


## Usage
### Import the necessary packages
```dart
import 'dart:typed_data' show Uint8List;
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:usb_camera/usb_camera.dart';
```

### Example Code
Below is an example of how to use the usb_camera plugin in a Flutter application. This example demonstrates listing available cameras, opening a camera, capturing images, and displaying the camera feed in the app.

```dart
class CameraCapture extends StatefulWidget {
  const CameraCapture({super.key});

  @override
  State<CameraCapture> createState() => _CameraCaptureState();
}

class _CameraCaptureState extends State<CameraCapture> {
  final _usbCamera = UsbCamera.instance;
  List<Map<String, dynamic>> _cameraList = [];
  String? _selectedCameraId;
  bool _isCameraOpen = false;
  bool _isCapturing = false;
  Uint8List? lastImageData;

  @override
  void initState() {
    super.initState();
    _initCameraList();
  }

  void _initCameraList() async {
    await [Permission.camera, Permission.storage].request();
    final cameras = await _usbCamera.list();
    setState(() {
      _cameraList = cameras;
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('USB Camera Example'),
        ),
        body: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.start,
                children: [
                  Expanded(
                    flex: 2,
                    child: _buildCameraDropdown(),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    flex: 1,
                    child: _buildOpenCloseButton(),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              _buildStartStopButton(),
              const SizedBox(height: 20),
              Expanded(
                child: StreamBuilder(
                  stream: _usbCamera.imageStream,
                  builder: (context, asyncSnapshot) {
                    if (asyncSnapshot.hasData) {
                      lastImageData = asyncSnapshot.data;
                    }
                    var imageDataToShow = asyncSnapshot.hasData ? asyncSnapshot.data : lastImageData;
                    if (imageDataToShow == null) {
                      return Container(color: Colors.grey);
                    }
                    return Image.memory(
                      imageDataToShow,
                      gaplessPlayback: true,
                      frameBuilder: (context, child, frame, wasSynchronouslyLoaded) {
                        if (wasSynchronouslyLoaded || frame != null) {
                          return child;
                        } else {
                          return lastImageData != null ? Image.memory(lastImageData!) : Container(color: Colors.grey);
                        }
                      },
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildCameraDropdown() {
    return DropdownButton<String>(
      value: _selectedCameraId,
      hint: const Text("Select Camera"),
      onChanged: (String? newValue) {
        setState(() {
          _selectedCameraId = newValue;
        });
      },
      items: _cameraList.map<DropdownMenuItem<String>>((Map<String, dynamic> camera) {
        return DropdownMenuItem<String>(
          value: camera['id'],
          child: Text(camera['id']),
        );
      }).toList(),
    );
  }

  Widget _buildOpenCloseButton() {
    return ElevatedButton(
      onPressed: () async {
        if (_isCameraOpen) {
          await _usbCamera.close();
        } else {
          if (_selectedCameraId != null) {
            await _usbCamera.open(_selectedCameraId!);
          }
        }
        setState(() {
          _isCameraOpen = !_isCameraOpen;
        });
      },
      child: Text(_isCameraOpen ? 'Close Camera' : 'Open Camera'),
    );
  }

  Widget _buildStartStopButton() {
    return ElevatedButton(
      style: ElevatedButton.styleFrom(
        backgroundColor: _isCapturing ? Colors.redAccent : null,
      ),
      onPressed: () async {
        if (_isCapturing) {
          await _usbCamera.stopCapture();
        } else {
          await _usbCamera.startCapture();
        }
        setState(() {
          _isCapturing = !_isCapturing;
        });
      },
      child: Text(_isCapturing ? 'Stop Capture' : 'Start Capture'),
    );
  }
}
```

## Permissions
The `usb_camera` plugin requires camera and storage permissions. Make sure to request these permissions before attempting to list or open cameras:

```dart
void _initCameraList() async {
  await [Permission.camera, Permission.storage].request();
  final cameras = await _usbCamera.list();
  setState(() {
    _cameraList = cameras;
  });
}
```

## License
This project is licensed under the MIT License. See the LICENSE file for details.
