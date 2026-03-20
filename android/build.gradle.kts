plugins {
    id("com.kurly.android.base.library")
    id("com.kurly.android.compose")
}

android {
    namespace = "com.kurly.loupe"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
}
