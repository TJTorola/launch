{
  description = "Simple Android Launcher - NixOS Development Environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    android-nixpkgs = {
      url = "github:tadfisher/android-nixpkgs";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { self, nixpkgs, flake-utils, android-nixpkgs }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            android_sdk.accept_license = true;
            allowUnfree = true;
          };
        };

        android-sdk = android-nixpkgs.sdk.${system} (sdkPkgs: with sdkPkgs; [
          cmdline-tools-latest
          build-tools-34-0-0
          platform-tools
          platforms-android-34
          emulator
        ]);

        fhsEnv = pkgs.buildFHSEnv {
          name = "android-env";
          targetPkgs = pkgs: (with pkgs; [
            android-sdk
            jdk17
            gradle
            kotlin
            glibc
            zlib
            ncurses5
            stdenv.cc.cc
          ]);
          multiPkgs = pkgs: (with pkgs; [
            zlib
          ]);
          runScript = "bash";
          profile = ''
            export ANDROID_HOME="${android-sdk}/share/android-sdk"
            export ANDROID_SDK_ROOT="$ANDROID_HOME"
            export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
            export JAVA_HOME="${pkgs.jdk17}"

            if [ ! -f local.properties ]; then
              echo "sdk.dir=$ANDROID_HOME" > local.properties
              echo "Created local.properties with Android SDK path"
            fi

            echo "Android development environment loaded (FHS)"
            echo "Android SDK: $ANDROID_HOME"
            echo "Java: $(java -version 2>&1 | head -n 1)"
            echo ""
            echo "To build the app: ./gradlew-fhs assembleDebug"
            echo "To install to device: ./gradlew-fhs installDebug"
            echo "To list devices: adb devices"
          '';
        };

      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            android-sdk
            jdk17
            gradle
            kotlin
          ];

          shellHook = ''
            export ANDROID_HOME="${android-sdk}/share/android-sdk"
            export ANDROID_SDK_ROOT="$ANDROID_HOME"
            export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

            if [ ! -f local.properties ]; then
              echo "sdk.dir=$ANDROID_HOME" > local.properties
              echo "Created local.properties with Android SDK path"
            fi

            cat > gradlew-fhs << 'WRAPPER'
#!/usr/bin/env bash
./gradlew --stop 2>/dev/null || true
${fhsEnv}/bin/android-env ./gradlew "$@"
WRAPPER
            chmod +x gradlew-fhs

            echo "Android development environment loaded"
            echo "Android SDK: $ANDROID_HOME"
            echo "Java: $(java -version 2>&1 | head -n 1)"
            echo ""
            echo "IMPORTANT: Use './gradlew-fhs' instead of './gradlew' to build"
            echo ""
            echo "To build the app: ./gradlew-fhs assembleDebug"
            echo "To install to device: ./gradlew-fhs installDebug"
            echo "To list devices: adb devices"
          '';
        };

        packages.android-fhs = fhsEnv;
      }
    );
}