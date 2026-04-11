// swift-tools-version:5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "AppLogger",
    platforms: [
        .iOS(.v14),
        .tvOS(.v14)
    ],
    products: [
        .library(
            name: "AppLogger",
            targets: ["AppLogger"]
        )
    ],
    targets: [
        .binaryTarget(
            name: "AppLogger",
            // After building the XCFramework, update this path or URL:
            // Local:  path: "build/AppLogger.xcframework"
            // Remote: url:  "https://github.com/zuccadev-labs/appLoggers/releases/download/v0.2.0-alpha.11/AppLogger.xcframework.zip",
            //         checksum: "<sha256>"
            path: "build/AppLogger.xcframework"
        )
    ]
)
