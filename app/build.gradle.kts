externalNativeBuild {
    cmake { path = file("src/main/cpp/CMakeLists.txt") }
}
ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
buildTypes {
    release {
        isMinifyEnabled = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
}
