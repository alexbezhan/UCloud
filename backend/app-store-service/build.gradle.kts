version = "0.14.7"

application {
    mainClassName = "dk.sdu.cloud.app.store.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":project-service:api"))
    implementation("com.vladmihalcea:hibernate-types-52:2.4.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.9.4")
}
