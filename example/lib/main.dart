import 'package:flutter/material.dart';

import 'package:flutter/services.dart';
import 'package:usb_camera/usb_camera.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _usbCamera = UsbCamera.instance;
  List<Map<String, dynamic>> _cameraList = [];
  String? _selectedCameraId;
  bool _isCameraOpen = false;
  bool _isCapturing = false;
  Uint8List? _imageData;

  @override
  void initState() {
    super.initState();
    _initCameraList();
    _listenToImageStream();
  }

  void _initCameraList() async {
    final cameras = await _usbCamera.list();
    print("_initCameraList ${cameras.runtimeType} $cameras");
    setState(() {
      _cameraList = cameras;
    });
  }

  void _listenToImageStream() {
    _usbCamera.imageStream.listen((Uint8List? imageData) {
      setState(() {
        _imageData = imageData;
      });
    }, onError: (error) {
      // Handle any errors that occur in the stream.
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Column(
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
            Expanded(child: _buildImageDisplay()),
          ],
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
      items: _cameraList
          .map<DropdownMenuItem<String>>((Map<String, dynamic> camera) {
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
        print(
            "_isCameraOpen $_isCameraOpen _selectedCameraId $_selectedCameraId");
        if (_isCameraOpen) {
          await _usbCamera.close();
        } else {
          if (_selectedCameraId != null) {
            print("opening..");
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

  Widget _buildImageDisplay() {
    if (_imageData != null) {
      return Image.memory(_imageData!);
    }
    return const Text('No image data');
  }
}
