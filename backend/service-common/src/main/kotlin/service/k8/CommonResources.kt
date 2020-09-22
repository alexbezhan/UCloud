package dk.sdu.cloud.service.k8

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.treeToValue
import dk.sdu.cloud.defaultMapper

object KubernetesResources {
    val pod = KubernetesResourceLocator(API_GROUP_CORE, "v1", "pods")
    val cronJob = KubernetesResourceLocator("batch", "v1beta1", "cronjobs")
    val daemonSet = KubernetesResourceLocator("apps", "v1", "daemonsets")
    val deployment = KubernetesResourceLocator("apps", "v1", "deployments")
    val job = KubernetesResourceLocator("batch", "v1", "jobs")
    val replicaSet = KubernetesResourceLocator("apps", "v1", "replicasets")
    val statefulSet = KubernetesResourceLocator("apps", "v1", "statefulsets")
}

typealias KubernetesTimestamp = String

data class ObjectMeta(
    var name: String? = null,
    var namespace: String? = null,
    var annotations: Map<String, Any?>? = null,
    var clusterName: KubernetesTimestamp? = null,
    var creationTimestamp: String? = null,
    var deletionGracePeriodSeconds: Int? = null,
    var deletionTimestamp: KubernetesTimestamp? = null,
    var finalizers: List<String>? = null,
    var generateName: String? = null,
    var generation: Int? = null,
    var labels: Map<String, Any?>? = null,
    var managedFields: List<Map<String, Any?>>? = null,
    var ownerReferences: List<Map<String, Any?>>? = null,
    var resourceVersion: String? = null,
    var selfLink: String? = null,
    var uid: String? = null,
)

data class WatchEvent<T>(
    val type: String,
    @get:JsonAlias("object") val theObject: T
)

data class Affinity(
    var nodeAffinity: Affinity? = null,
    var podAffinity: Affinity? = null,
    var podAntiAffinity: Affinity? = null,
) {
    data class Node(
        var preferredDuringSchedulingIgnoredDuringExecution: PreferredSchedulingTerm? = null,
        var requiredDuringSchedulingIgnoredDuringExecution: NodeSelector? = null,
    ) {
        data class PreferredSchedulingTerm(
            var preference: NodeSelectorTerm? = null,
            var weight: Int? = null,
        )

        data class NodeSelectorTerm(
            var matchExpressions: NodeSelectorRequirement? = null,
            var matchFields: NodeSelectorRequirement? = null,
        )

        data class NodeSelectorRequirement(
            var key: String? = null,
            var operator: String? = null,
            var values: List<String>? = null,
        )
    }
    data class Pod(
        var preferredDuringSchedulingIgnoredDuringExecution: List<WeightedPodAffinityTerm>? = null,
        var requiredDuringSchedulingIgnoredDuringExecution: List<PodAffinityTerm>? = null,
    )
    data class PodAnti(
        var preferredDuringSchedulingIgnoredDuringExecution: List<WeightedPodAffinityTerm>? = null,
        var requiredDuringSchedulingIgnoredDuringExecution: List<PodAffinityTerm>? = null,
    )

    data class WeightedPodAffinityTerm(
        var podAffinityTerm: PodAffinityTerm? = null,
        var weight: Int? = null,
    )

    data class PodAffinityTerm(
        var labelSelector: LabelSelector? = null,
        var namespaces: List<String>? = null,
        var topologyKey: String? = null,
    )
}

data class LabelSelector(
    var matchExpressions: List<LabelSelectorRequirement>? = null,
    var matchLabels: Map<String, Any?>? = null,
)

data class LabelSelectorRequirement(
    var key: String? = null,
    var operator: String? = null,
    var values: List<String>? = null,
)
data class NodeSelector(
    var key: String? = null,
    var operator: String? = null,
    var values: List<String>? = null,
)

data class LocalObjectReference(
    var name: String? = null
)

data class Pod(
    var apiVersion: String = "v1",
    var kind: String = "Pod",
    var metadata: ObjectMeta? = null,
    var spec: Spec? = null,
    var status: Status? = null
) {
    data class Spec(
        var activeDeadlineSeconds: Int? = null,
        var affinity: Affinity? = null,
        var automountServiceAccountToken: Boolean? = null,
        var containers: List<Container>? = null,
        //var dnsConfig: PodDNSConfig?,
        var dnsPolicy: String? = null,
        var enableServiceLinks: Boolean? = null,
        //var ephemeralContainers: List<EphemeralContainer>?,
        //var hostAliases: List<HostAlias>,
        var hostIPC: Boolean? = null,
        var hostNetwork: Boolean? = null,
        var hostPID: Boolean? = null,
        var hostname: String? = null,
        //var imagePullSecrets: List<LocalObjectReference>?,
        var initContainers: List<Container>? = null,
        var nodeName: String? = null,
        var nodeSelector: Map<String, Any?>? = null,
        var overhead: Map<String, Any?>? = null,
        var preemptionPolicy: String? = null,
        var priority: Int? = null,
        var priorityClassName: String? = null,
        var restartPolicy: String? = null,
        var runtimeClassName: String? = null,
        var schedulerName: String? = null,
        var securityContext: PodSecurityContext? = null,
        var serviceAccountName: String? = null,
        var subdomain: String? = null,
        var tolerations: List<Toleration>? = null,
        var volumes: List<Volume>? = null
    )

    data class SpecTemplate(
        var metadata: ObjectMeta? = null,
        var spec: Spec? = null,
    )

    data class PodSecurityContext(
        var fsGroup: Int? = null,
        var fsGroupChangePolicy: String? = null,
        var runAsGroup: Int? = null,
        var runAsNonRoot: Boolean? = null,
        var runAsUser: Int? = null,
        var supplementalGroups: List<Int>? = null,
        //var sysctls: List<Sysctl>?,
        //var windowsOptions: WindowsSecutiyContextOptions?
        //var seLinuxOptions: SELinuxOptions?,
        //var seccompProfile: SeccompProfile?,
    )

    data class Toleration(
        var effect: String? = null,
        var key: String? = null,
        var operator: String? = null,
        var tolerationSeconds: Int? = null,
        var value: String? = null,
    )

    data class Volume(
        var name: String? = null,
        var emptyDir: EmptyDirVolumeSource? = null,
        var configMap: ConfigMapVolumeSource? = null,
        var secret: SecretVolumeSource? = null,
        var flexVolume: FlexVolumeSource? = null,
        var persistentVolumeClaim: PersistentVolumeClaimSource? = null,
    ) {
        data class EmptyDirVolumeSource(
            var medium: String? = null,
            var sizeLimit: String? = null
        )

        data class ConfigMapVolumeSource(
            var defaultMode: Int? = null,
            var items: List<KeyToPath>? = null,
            var name: String? = null,
            var optional: Boolean? = null,
        )

        data class KeyToPath(
            var key: String? = null,
            var mode: Int? = null,
            var path: String? = null,
        )

        data class SecretVolumeSource(
            var defaultMode: Int? = null,
            var items: List<KeyToPath>? = null,
            var optional: Boolean? = null,
            var secretName: String? = null,
        )

        data class FlexVolumeSource(
            var driver: String? = null,
            var fsType: String? = null,
            var options: Map<String, Any?>? = null,
            var readOnly: Boolean? = null,
            var secretRef: LocalObjectReference? = null
        )

        data class PersistentVolumeClaimSource(
            var claimName: String? = null,
            var readOnly: Boolean? = null,
        )
    }

    data class Status(
        var conditions: List<PodCondition>? = null,
        var containerStatuses: List<ContainerStatus>? = null,
        var hostIP: String? = null,
        var initContainerStatuses: List<ContainerStatus>? = null,
        var message: String? = null,
        var phase: String? = null,
        var podIP: String? = null,
        var podIPs: List<PodIP>? = null,
        var quosClass: String? = null,
        var reason: String? = null,
        var startTime: KubernetesTimestamp? = null,
    )

    data class PodCondition(
        var lastProbeTime: KubernetesTimestamp? = null,
        var lastTransitionTime: KubernetesTimestamp? = null,
        var message: String? = null,
        var reason: String? = null,
        var status: String? = null,
        var type: String? = null,
    )

    data class ContainerStatus(
        var containerID: String? = null,
        var image: String? = null,
        var imageID: String? = null,
        var lastState: ContainerState? = null,
        var name: String? = null,
        var ready: Boolean? = null,
        var restartCount: Int? = null,
        var started: Boolean? = null,
        var state: ContainerState? = null,
    )

    data class ContainerState(
        var running: StateRunning? = null,
        var terminated: StateTerminated? = null,
        var waiting: StateWaiting? = null,
    ) {
        data class StateRunning(
            var startedAt: KubernetesTimestamp? = null,
        )

        data class StateTerminated(
            var containerID: String? = null,
            var exitCode: Int? = null,
            var finishedAt: KubernetesTimestamp? = null,
            var message: String? = null,
            var reason: String? = null,
            var signal: Int? = null,
            var startedAt: KubernetesTimestamp? = null,
        )

        data class StateWaiting(
            var message: String? = null,
            var reason: String? = null,
        )
    }

    data class PodIP(
        var ip: String? = null,
    )

    data class Container(
        var args: List<String>? = null,
        var command: List<String>? = null,
        var env: List<EnvVar>? = null,
        var envFrom: List<EnvFromSource>? = null,
        var image: String? = null,
        var imagePullPolicy: String? = null,
        //var lifecycle: Lifecycle?,
        var livenessProbe: Probe? = null,
        var name: String? = null,
        var ports: List<ContainerPort>? = null,
        var readinessProbe: Probe? = null,
        var resources: ResourceRequirements? = null,
        var securityContext: SecurityContext? = null,
        var startupProbe: Probe? = null,
        var stdin: Boolean? = null,
        var stdinOnce: Boolean? = null,
        var terminationMessagePath: String? = null,
        var terminationMessagePolicy: String? = null,
        var tty: Boolean? = null,
        var volumeDevices: List<VolumeDevice>? = null,
        var volumeMounts: List<VolumeMount>? = null,
        var workingDir: String? = null,
    ) {
        data class Probe(
            var exec: ExecAction? = null,
            var failureThreshold: Int? = null,
            var httpGet: HttpGetAction? = null,
            var initialDelaySeconds: Int? = null,
            var periodSeconds: Int? = null,
            var successThreshold: Int? = null,
            var tcpSocket: TCPSocketAction? = null,
            var timeoutSeconds: Int? = null
        )

        data class ContainerPort(
            var containerPort: Int? = null,
            var hostIP: String? = null,
            var hostPort: Int? = null,
            var name: String? = null,
            var protocol: String? = null,
        )

        data class ResourceRequirements(
            var limits: Map<String, Any?>? = null,
            var requests: Map<String, Any?>? = null,
        )

        data class SecurityContext(
            var allowPrivilegeEscalation: Boolean? = null,
            //var capabilities: Capabilities?,
            var privileged: Boolean? = null,
            var procMount: String? = null,
            var readOnlyRootFilesystem: Boolean? = null,
            var runAsGroup: Int? = null,
            var runAsNonRoot: Boolean? = null,
            var runAsUser: Int? = null,
            //var seLinuxOptions: SELinuxOptions?,
            //var seccompProfile: SeccompProfile?,
            //var windowsOptions: WindowsSecurityContextOptions?,
        )

        data class VolumeDevice(
            var devicePath: String? = null,
            var name: String? = null,
        )

        data class VolumeMount(
            var mountPath: String? = null,
            var mountPropagation: String? = null,
            var name: String? = null,
            var readOnly: Boolean? = null,
            var subPath: String? = null,
            var subPathExpr: String? = null,
        )
    }

    data class ExecAction(var command: List<String>? = null)
    data class HttpGetAction(
        var host: String? = null,
        var httpHeaders: List<Map<String, Any?>>? = null,
        var path: String? = null,
        var port: Any? = null, // String | Int
        var scheme: String? = null
    )
    data class TCPSocketAction(
        var host: String? = null,
        var port: Int? = null,
    )

    data class EnvVar(
        var name: String? = null,
        var value: String? = null,
        var valueFrom: EnvVarSource? = null,
    )

    data class EnvVarSource(
        var configMapKeyRef: ConfigMapKeySelector? = null,
        var fieldRef: ObjectFieldSelector? = null,
        var secretKeyRef: SecretKeySelector? = null,
    ) {
        data class ConfigMapKeySelector(
            var key: String? = null,
            var name: String? = null,
            var optional: Boolean? = null,
        )

        data class ObjectFieldSelector(var fieldPath: String? = null)
        data class SecretKeySelector(
            var key: String? = null,
            var name: String? = null,
            var optional: Boolean? = null,
        )
    }

    data class EnvFromSource(
        var configMapRef: ConfigMapEnvSource? = null,
        var prefix: String? = null,
        var secretRef: SecretEnvSource? = null,
    ) {
        data class ConfigMapEnvSource(
            var name: String? = null,
            var optional: Boolean? = null,
        )
        data class SecretEnvSource(
            var name: String? = null,
            var optional: Boolean? = null,
        )
    }
}

typealias ResourceList = Map<String, String>

inline class KubernetesNode(val raw: JsonNode)
val JsonNode.k8: KubernetesNode get() = KubernetesNode(this)
val KubernetesNode.metadata: ObjectMeta get() = defaultMapper.treeToValue(raw["metadata"])
