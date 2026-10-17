# PDF Forge — Android MVP (v0.1)

Native Android app using Kotlin and Android platform PDF APIs. Minimum Android 10 (API 29). No network permission; documents processed locally.

## Features
- Convert one or more images to a PDF
- Merge PDFs (rasterized)
- Save the first page of a PDF as a new PDF (initial split prototype)
- Attempt image-based compression (not guaranteed to reduce file size)
- Save through Android's document picker

## Important limitations
The initial merge, split and compression implementation rasterizes pages. It does NOT preserve selectable text, links, forms or vector content. The 'Split PDF' button currently exports ONLY the first page, not all pages. Compression is experimental and can increase file size. Encrypted/password-protected PDFs may fail. This is a functional prototype, not a full-featured production PDF editor.

## Build from Android phone only
1. Create a new **public or private** GitHub repository named `PDFForge`.
2. Upload the contents of this ZIP to the repository root, preserving folders. GitHub mobile browser may make folder uploads difficult. An Android Git client supporting ZIP extraction and folder commits, or GitHub Codespaces in a browser, can be used.
3. Open GitHub repository > Actions > Build Android APK > Run workflow (or push to main).
4. Wait for a successful workflow. Open the run > Artifacts > `PDFForge-debug-apk` and download the ZIP.
5. Extract `app-debug.apk`, open it and allow installation from your browser/files app when Android asks.

## Roadmap
- v0.2: true lossless merge/split/extract, reorder and rotate pages using a dedicated PDF library
- v0.3: compression presets, accurate file size comparisons
- v0.4: document conversion, camera scanning, OCR
- v0.5: annotation, signatures, encryption, Play Store preparation

## Development
Build with JDK 17, Android SDK 35, Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21.
