# Third-Party Notices

## Breezy Weather (weather layout reference)

The Material and RadiantUI weather layouts are independent Jetpack Compose
implementations informed by the screenshots and UI source of
[Breezy Weather](https://github.com/breezy-weather/breezy-weather/tree/0851e22c94ab48e0350fb1c8132fcffc88789c4a),
at commit `0851e22c94ab48e0350fb1c8132fcffc88789c4a`.
The adaptation uses AHUTong's weather models, navigation, and static drawing.
Breezy Weather's upstream source is licensed under
[LGPL-3.0](https://github.com/breezy-weather/breezy-weather/blob/0851e22c94ab48e0350fb1c8132fcffc88789c4a/LICENSE)
with its
[additional notice](https://github.com/breezy-weather/breezy-weather/blob/0851e22c94ab48e0350fb1c8132fcffc88789c4a/LICENSE_ADDITIONAL).
Its source code, icons, fonts, and animated rendering library are not bundled.

## GuiXu (Rust rewrite)

AHUTong Android includes the GuiXu Rust embedded database implementation from:

<https://github.com/Yukon163/GuiXu/tree/rust-rewrite>

GuiXu Rust is a derivative Rust rewrite of the original
[Delsart/GuiXu](https://github.com/Delsart/GuiXu) Kotlin project. The original
project and the Rust rewrite are distributed under the Apache License,
Version 2.0.

The Rust rewrite and subsequent modifications are recorded in the GuiXu
`rust-rewrite` branch under the following contributors:

- 100011646-Wang Zhong Kai
- Yukon163

The complete GuiXu `LICENSE` and `NOTICE` files are copied from the pinned
`GuiXu-Rust` submodule into the APK at build time and are available offline
from the application's open-source license screen.

## CameraX (host, plugin camera capability)

The host app uses the AndroidX Camera libraries (Apache License, Version 2.0):

- `androidx.camera:camera-core`
- `androidx.camera:camera-camera2`
- `androidx.camera:camera-lifecycle`
- `androidx.camera:camera-view`

These libraries live only in the host APK; runtime plugins share them through
the parent ClassLoader and never bundle them.

## OpenCV / document-scanner (plugin `doc_scan`, planned)

The planned document-scanner plugin (`.ahup` v2 package) is expected to embed
[OpenCV](https://opencv.org/) 4.x Java bindings and native library
(Apache License, Version 2.0) and to derive detection/perspective-transform
use cases from [hannesa2/document-scanner](https://github.com/hannesa2/document-scanner)
(MIT License; upstream [Kuama-IT/android-document-scanner](https://github.com/Kuama-IT/android-document-scanner)).
Native libraries ship inside the signed `.ahup` package only and are covered by
its v2 signature payload.
