{
  description = "clojure-dev-repl";
  inputs = {
    nixpkgs.url = "https://flakehub.com/f/NixOS/nixpkgs/0.1"; # tracks nixpkgs unstable branch
    devshell.url = "github:numtide/devshell";
    devshell.inputs.nixpkgs.follows = "nixpkgs";
    devenv.url = "https://flakehub.com/f/ramblurr/nix-devenv/*";
    devenv.inputs.nixpkgs.follows = "nixpkgs";
    clj-helpers.url = "github:outskirtslabs/clojure-nix-locker-helpers";
    clj-helpers.inputs.nixpkgs.follows = "nixpkgs";
  };
  outputs =
    inputs@{
      self,
      devenv,
      devshell,
      clj-helpers,
      ...
    }:
    let
      jdk = "jdk25";
      mkClojure = pkgs: pkgs.clojure.override { jdk = pkgs.${jdk}; };
      mkLocker =
        pkgs:
        clj-helpers.lib.mkLockfile {
          inherit pkgs;
          jdk = pkgs.${jdk};
          src = ./.;
          lockfile = "./deps-lock.json";
        };
      lockerCommand =
        pkgs:
        let
          clojure = mkClojure pkgs;
        in
        ''
          export HOME="$tmp/home"
          export GITLIBS="$tmp/home/.gitlibs"
          unset CLJ_CACHE CLJ_CONFIG XDG_CACHE_HOME XDG_CONFIG_HOME XDG_DATA_HOME

          ${clojure}/bin/clojure -Srepro -P -M:test
        '';
    in
    devenv.lib.mkFlake ./. {
      inherit inputs;
      withOverlays = [
        devshell.overlays.default
        devenv.overlays.default
      ];
      packages = {
        locker = pkgs: (mkLocker pkgs).commandLocker (lockerCommand pkgs);
      };
      checks =
        pkgs:
        let
          clojure = mkClojure pkgs;
          jdkPackage = pkgs.${jdk};
          clojureLocker = mkLocker pkgs;
        in
        {
          tests = pkgs.stdenv.mkDerivation {
            pname = "clojure-dev-repl-tests";
            version = "0.0.0";
            src = ./.;
            nativeBuildInputs = [
              pkgs.babashka
              clojure
              pkgs.git
              jdkPackage
            ];
            JAVA_HOME = jdkPackage.home;
            buildPhase = ''
              runHook preBuild

              source ${clojureLocker.shellEnv}
              export JAVA_HOME="${jdkPackage.home}"
              export JAVA_CMD="${jdkPackage}/bin/java"

              bb test

              runHook postBuild
            '';
            installPhase = ''
              runHook preInstall

              mkdir -p "$out"
              touch "$out/success"

              runHook postInstall
            '';
          };
        };
      devShell =
        pkgs:
        pkgs.devshell.mkShell {
          imports = [
            devenv.capsules.base
            devenv.capsules.clojure
          ];
          packages = [
            self.packages.${pkgs.system}.locker
          ];
        };
    };
}
