version = "0.2.3"

application {
    mainClassName = "dk.sdu.cloud.app.license.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":project-service:api"))
}
