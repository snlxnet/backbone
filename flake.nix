{
  description = "Light Android dev shell, this version is LLM-generated";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }: let
    system = "aarch64-darwin";
    pkgs = import nixpkgs {
      inherit system;
      config = {
        allowUnfree = true;
        android_sdk.accept_license = true;
      };
    };

    android = pkgs.androidenv.composeAndroidPackages {
      buildToolsVersions = [ "34.0.0" ];
      platformVersions = [ "34" ];
      includeNDK = false;
      includeEmulator = false;
    };

    androidSdk = android.androidsdk;
  in {
    devShells.${system}.default = pkgs.mkShell {
      packages = [
        androidSdk
        pkgs.jdk17
        pkgs.gradle
        pkgs.kotlin-language-server

        pkgs.tinymist
      ];

      shellHook = ''
        export ANDROID_HOME=${androidSdk}/libexec/android-sdk
        export ANDROID_SDK_ROOT=$ANDROID_HOME
        export JAVA_HOME=${pkgs.jdk17}
        export PATH=$ANDROID_HOME/platform-tools:$PATH
      '';
    };
  };
}

