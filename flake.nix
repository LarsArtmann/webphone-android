{
  description = "webphone-android: the Android native-call shell for webphone";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
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
          platformVersions = [ "37" ];
          # 36.0.0: AGP 9's floor and packaging default; its zipalign
          # carries the -P 16 flag — the 16 KB page-size verification
          # the platform decision mandates per release.
          buildToolsVersions = [ "36.0.0" ];
          includeEmulator = false;
          includeSystemImages = false;
        };
        # The self-serve device train (scripts/emulator-train.sh): a
        # SEPARATE shell so the everyday build shell stays lean —
        # emulator + system image weigh gigabytes. Enter it only when
        # training: `nix develop .#emulator -c bash scripts/emulator-train.sh`
        # (KVM required; on hosts without /dev/kvm the script is the
        # documentation of what a real device train does). The platform
        # stays 36 until the API 37 system image is deliberately pulled
        # (multi-GB; the existing AVD is API 36).
        emulatorSdk = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "36" ];
          buildToolsVersions = [ "36.0.0" ];
          includeEmulator = true;
          includeSystemImages = true;
          systemImageTypes = [ "google_apis" ];
          abiVersions = [ "x86_64" ];
        };
      in
      {
        # `nix fmt` (and BuildFlow's nix-fmt roundtrip, which re-evaluates
        # a COPY of this flake in /tmp) needs an explicit formatter
        # attribute. The ready-made wrappers misbehave there: bare
        # `nixfmt` refuses argument-less invocation, its directory mode
        # is deprecated, and `nixfmt-tree` pins its tree root to the
        # store path it lives in. Walk the .nix files ourselves instead.
        formatter = pkgs.writeShellScriptBin "nix-fmt" ''
          if [ "$#" -gt 0 ]; then
            exec ${pkgs.nixfmt}/bin/nixfmt "$@"
          fi
          exec find . -name '*.nix' -not -path './.git/*' -print0 |
            xargs -0 -r ${pkgs.nixfmt}/bin/nixfmt
        '';
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
      }
    );
}
