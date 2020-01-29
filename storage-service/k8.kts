//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "storage"
    version = "3.1.5"

    withAmbassador(null) {
        services.add(
            AmbassadorMapping(
                """
                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: storage_list
                    prefix: ^/*/api/files(/(lookup|stat))?${'$'}
                    prefix_regex: true
                    rewrite: ""
                    service: storage:8080
                    timeout_ms: 0
                    method: GET
                    headers:
                      x-no-load: true
                    precedence: 10

                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: storage_list_2
                    timeout_ms: 0
                    rewrite: ""
                    prefix: /api/files/
                    service: storage:8080
                    use_websocket: true

                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: storage_list_3
                    timeout_ms: 0
                    rewrite: ""
                    prefix: ^/api/files(/)?${'$'}
                    prefix_regex: true
                    service: storage:8080
                    method: DELETE

                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: storage_list_4
                    timeout_ms: 0
                    rewrite: ""
                    prefix: ^/api/files/workspaces(/)?${'$'}
                    prefix_regex: true
                    service: storage:8080
                    use_websocket: true                
                """.trimIndent()
            )
        )
    }

    val deployment = withDeployment {
        deployment.spec.replicas = 2
    }

    withPostgresMigration(deployment)

    withAdHocJob(
        deployment,
        nameSuffix = "scan",
        additionalArgs = {
            listOf("--scan") + remainingArgs
        }
    )
}
