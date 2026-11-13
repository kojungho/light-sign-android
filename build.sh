#!/usr/bin/env bash
set -euo pipefail
src="$(cd "$(dirname "$0")" && pwd)"
sdk="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
bt="$sdk/build-tools/${ANDROID_BUILD_TOOLS:-36.1.0}"
platform="$sdk/platforms/android-36/android.jar"
out="${1:-$src/../light-sign.apk}"
key="${LIGHT_SIGN_KEYSTORE:-$HOME/.android/light-sign.jks}"
build="$(mktemp -d)"
trap 'rm -rf "$build"' EXIT
mkdir -p "$build/classes" "$build/dex" "$(dirname "$key")"
if [[ ! -f "$key" ]]; then
  umask 077
  keytool -genkeypair -keystore "$key" -storepass android -keypass android -alias lightsign -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Light Sign'
fi
"$bt/aapt2" compile --dir "$src/res" -o "$build/res.zip"
"$bt/aapt2" link -I "$platform" --manifest "$src/AndroidManifest.xml" -o "$build/app.apk" "$build/res.zip"
find "$src/src" -name '*.java' > "$build/sources"
javac --release 8 -cp "$platform" -d "$build/classes" @"$build/sources"
jar cf "$build/classes.jar" -C "$build/classes" .
"$bt/d8" --min-api 26 --lib "$platform" --output "$build/dex" "$build/classes.jar"
(cd "$build/dex" && zip -q "$build/app.apk" classes.dex)
"$bt/zipalign" -f 4 "$build/app.apk" "$build/aligned.apk"
"$bt/apksigner" sign --ks "$key" --ks-key-alias lightsign --ks-pass pass:android --key-pass pass:android --out "$out" "$build/aligned.apk"
"$bt/apksigner" verify "$out"
