plugins {
    kotlin("jvm")
}

dependencies {
    // Arbre JSON uniquement (pas de plugin de compilation nécessaire)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}
