plugins {
    kotlin("jvm")
}

dependencies {
    commonDependencies()

    implementation(Dependencies.KOIN)
    implementation(Dependencies.KOTLINX_COROUTINES_CORE)

    implementation(project(":language"))
    implementation(project(":utils"))
    implementation(project(":common"))

    testImplementation(Dependencies.KOTLIN_TEST)
    testImplementation(Dependencies.KOTLIN_TEST_JUNIT)
}
