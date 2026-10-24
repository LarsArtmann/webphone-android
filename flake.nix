{
  description = "webphone-android: the Android native-call shell for webphone";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          # The Android SDK family is unfree (Google's terms — the
          # same terms every Android Studio install accepts). This
          # shell builds an Android app; allowUnfree scopes to exactly
          # that toolchain and accepts the SDK license.
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };
        android = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "36" ];
          # 34.0.0: AGP 8.7's packaging default; 35.0.0: the first
          # zipalign carrying the -P 16 flag (the 16 KB page-size
          # verification the platform decision mandates per release).
          buildToolsVersions = [ "34.0.0" "35.0.0" ];
          includeEmulator = false;
          includeSystemImages = false;
        };
        # The self-serve device train (scripts/emulator-train.sh): a
        # SEPARATE shell so the everyday build shell stays lean —
        # emulator + system image weigh gigabytes. Enter it only when
        # training: `nix develop .#emulator -c bash scripts/emulator-train.sh`
        # (KVM required; on hosts without /dev/kvm the script is the
        # documentation of what a real device train does).
        emulatorSdk = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "36" ];
          buildToolsVersions = [ "35.0.0" ];
          includeEmulator = true;
          includeSystemImages = true;
          systemImageTypes = [ "google_apis" ];
          abiVersions = [ "x86_64" ];
        };
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            pkgs.jdk21
            pkgs.gradle
            android.androidsdk
          ];
          ANDROID_HOME = "${android.androidsdk}/libexec/android-sdk";
          ANDROID_SDK_ROOT = "${android.androidsdk}/libexec/android-sdk";
          JAVA_HOME = pkgs.jdk21.home;
        };
        devShells.emulator = pkgs.mkShell {
          packages = [
            pkgs.jdk21
            emulatorSdk.androidsdk
          ];
          ANDROID_HOME = "${emulatorSdk.androidsdk}/libexec/android-sdk";
          ANDROID_SDK_ROOT = "${emulatorSdk.androidsdk}/libexec/android-sdk";
          JAVA_HOME = pkgs.jdk21.home;
        };
      });
}
