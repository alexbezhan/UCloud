version  = "0.1.0"

application {
    mainClassName = "dk.sdu.cloud.project.favorite.MainKt"
}

dependencies {
    implementation(project(":auth-service:api"))
    implementation(project(":project-service:api"))
}
