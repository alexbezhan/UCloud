package dk.sdu.cloud.app.store.util

import dk.sdu.cloud.app.store.api.*
import io.mockk.mockk

val normAppDesc = Application(
    ApplicationMetadata(
        "name",
        "2.2",
        listOf("Authors"),
        "title",
        "app description",
        null,
        true
    ),
    ApplicationInvocationDescription(
        ToolReference("tool", "1.0.0", null),
        mockk(relaxed = true),
        emptyList(),
        listOf("glob"),
        fileExtensions = emptyList()
    )
)

val normAppDescDiffVersion = Application(
    ApplicationMetadata(
        "name",
        "2.3",
        listOf("Authors"),
        "title",
        "app description",
        null,
        true
    ),
    ApplicationInvocationDescription(
        ToolReference("tool", "1.0.0", null),
        mockk(relaxed = true),
        emptyList(),
        listOf("glob"),
        fileExtensions = emptyList()
    )
)


val normAppDesc2 = normAppDesc.withNameAndVersionAndTitle("app", "1.2", "application")

val normAppDesc3 = normAppDesc
    .withInvocation(
        listOf(
            VariableInvocationParameter(listOf("int"), prefixVariable = "--int "),
            VariableInvocationParameter(listOf("great"), prefixVariable = "--great "),
            VariableInvocationParameter(listOf("missing"), prefixGlobal = "--missing ")
        )
    )
    .withParameters(
        listOf(
            ApplicationParameter.Integer("int"),
            ApplicationParameter.Text("great", true),
            ApplicationParameter.Integer("missing", true)
        )
    )

fun Application.withTool(name: String, version: String): Application {
    return copy(
        invocation = invocation.copy(
            tool = ToolReference(name, version, null)
        )
    )
}

fun Application.withNameAndVersion(name: String, version: String): Application {
    return copy(
        metadata = metadata.copy(
            name = name,
            version = version
        )
    )
}

fun Application.withNameAndVersionAndTitle(name: String, version: String, title: String): Application {
    return copy(
        metadata = normAppDesc.metadata.copy(
            name = name,
            version = version,
            title = title
        )
    )
}

fun Application.withInvocation(invocation: List<InvocationParameter>): Application = copy(
    invocation = this.invocation.copy(
        invocation = invocation
    )
)

fun Application.withParameters(parameters: List<ApplicationParameter<*>>): Application = copy(
    invocation = this.invocation.copy(
        parameters = parameters
    )
)

val normToolDesc = NormalizedToolDescription(
    NameAndVersion("tool", "1.0.0"),
    "container",
    2,
    2,
    SimpleDuration(1, 0, 0),
    listOf(""),
    listOf("Author"),
    "title",
    "description",
    ToolBackend.DOCKER,
    "MIT"
)
