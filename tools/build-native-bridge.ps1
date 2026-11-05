param([string]$NdkRoot=$env:ANDROID_NDK_HOME)
$ErrorActionPreference='Stop'
if(-not $NdkRoot) {throw 'Set ANDROID_NDK_HOME to Android NDK r28 or newer'}
$projectRoot=Split-Path -Parent $PSScriptRoot
$compiler=Join-Path $NdkRoot 'toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe'
foreach($abi in @('arm64-v8a','x86_64')) {
    $target=if($abi -eq 'arm64-v8a') {'aarch64-linux-android26'} else {'x86_64-linux-android26'}
    $output=Join-Path $projectRoot "app/src/main/jniLibs/$abi/libosifyvm.so"
    & $compiler "--target=$target" -shared -fPIC -O2 '-Wl,-z,max-page-size=16384' (Join-Path $projectRoot 'app/src/main/cpp/osify_vm.c') -o $output -ldl -llog
    if($LASTEXITCODE -ne 0) {throw "Native build failed for $abi"}
}
