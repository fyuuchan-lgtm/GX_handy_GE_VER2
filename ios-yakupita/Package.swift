// swift-tools-version: 5.7

import PackageDescription

let package = Package(
    name: "YakupitaCore",
    platforms: [
        .iOS(.v15),
    ],
    products: [
        .library(name: "YakupitaCore", targets: ["YakupitaCore"]),
    ],
    targets: [
        .target(name: "YakupitaCore"),
        .testTarget(name: "YakupitaCoreTests", dependencies: ["YakupitaCore"]),
    ]
)
