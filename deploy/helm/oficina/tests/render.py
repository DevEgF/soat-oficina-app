"""Semantic Helm contract checks; requires Helm and PyYAML, never contacts a cluster."""
from pathlib import Path
import subprocess
import yaml

CHART = Path(__file__).resolve().parents[1]
ROOT = CHART.parents[2]
FIXTURE = CHART / "tests/fixture-values.yaml"


def render(environment, *extra):
    command = ["helm", "template", f"oficina-{environment}", str(CHART),
               "--namespace", environment, "-f", str(FIXTURE),
               "-f", str(CHART / f"values-{environment}.yaml"), *extra]
    return subprocess.run(command, capture_output=True, text=True, check=True).stdout


def check(environment):
    docs = {item["kind"]: item for item in yaml.safe_load_all(render(environment)) if item}
    deployment = docs["Deployment"]["spec"]["template"]["spec"]
    job = docs["Job"]["spec"]["template"]["spec"]
    config = docs["ConfigMap"]
    spc = docs["SecretProviderClass"]
    assert "Namespace" not in docs and "ServiceAccount" not in docs
    assert docs["Service"]["spec"]["ports"][0]["nodePort"] == (30080 if environment == "hml" else 30081)
    assert docs["HorizontalPodAutoscaler"]["spec"]["minReplicas"] == 1
    assert docs["HorizontalPodAutoscaler"]["spec"]["maxReplicas"] == 3
    metrics = {item["resource"]["name"]: item["resource"]["target"]
               for item in docs["HorizontalPodAutoscaler"]["spec"]["metrics"]}
    assert metrics == {"cpu": {"type": "Utilization", "averageUtilization": 70},
                       "memory": {"type": "Utilization", "averageUtilization": 80}}
    assert docs["PodDisruptionBudget"]["spec"]["minAvailable"] == 1
    for pod, migrating in ((deployment, "false"), (job, "true")):
        assert pod["serviceAccountName"] == "oficina-app"
        container = pod["containers"][0]
        env = {entry["name"]: entry["value"] for entry in container["env"]}
        assert env["SPRING_FLYWAY_ENABLED"] == migrating
        assert env["APP_ENVIRONMENT"] == environment
        assert env["SPRING_CONFIG_IMPORT"] == "configtree:/mnt/secrets/"
        assert "@sha256:" in container["image"]
        volumes = {entry["name"]: entry for entry in pod["volumes"]}
        assert volumes["configuration"]["configMap"]["name"] == config["metadata"]["name"]
        assert volumes["secrets"]["csi"]["volumeAttributes"]["secretProviderClass"] == spc["metadata"]["name"]
    job_env = {entry["name"]: entry["value"] for entry in job["containers"][0]["env"]}
    assert job_env["APP_MIGRATION_ONLY"] == "true"
    assert job_env["SPRING_MAIN_WEB_APPLICATION_TYPE"] == "none"
    assert "sslmode=verify-full" in config["data"]["application.properties"]
    assert "sslrootcert=/etc/ssl/certs/aws-rds-global-bundle.pem" in config["data"]["application.properties"]
    for kind, weight in (("ConfigMap", "-30"), ("SecretProviderClass", "-20"), ("Job", "-10")):
        annotations = docs[kind]["metadata"]["annotations"]
        assert annotations["helm.sh/hook"] == "pre-install,pre-upgrade"
        assert annotations["helm.sh/hook-weight"] == weight
        if kind != "Job":
            assert "hook-succeeded" not in annotations["helm.sh/hook-delete-policy"]
    parameters = spc["spec"]["parameters"]
    assert parameters["usePodIdentity"] == "true"
    aliases = {field["objectAlias"] for obj in yaml.safe_load(parameters["objects"]) for field in obj["jmesPath"]}
    assert aliases == {"spring.datasource.username", "spring.datasource.password", "app.jwt.secret"} | {
        f"app.security.{role}.password" for role in ("master", "admin", "attendant", "technician", "warehouse")}
    annotation = docs["Deployment"]["spec"]["template"]["metadata"]["annotations"]
    assert annotation["instrumentation.opentelemetry.io/inject-java"] == "true"


for environment in ("hml", "prod"):
    check(environment)
local = list(yaml.safe_load_all(render("hml", "-f", str(ROOT / "scripts/kind-values.yaml"))))
assert not any(doc and doc["kind"] == "SecretProviderClass" for doc in local)
for doc in local:
    if doc and doc["kind"] in ("Job", "Deployment"):
        pod = doc["spec"]["template"]["spec"]
        assert pod["serviceAccountName"] == "default"
        assert not any("csi" in volume for volume in pod["volumes"])
        assert pod["containers"][0]["image"] == "oficina:kind-test"
try:
    render("hml", "--set", "image.digest=latest")
    raise AssertionError("mutable deployment image was accepted")
except subprocess.CalledProcessError:
    pass
seed_docs = [doc for doc in yaml.safe_load_all(render("hml", "--set", "demoFixtures.enabled=true")) if doc]
seed_job = next(doc for doc in seed_docs if doc["kind"] == "Job" and doc["metadata"]["name"].endswith("-fixtures"))
assert seed_job["metadata"]["annotations"]["helm.sh/hook"] == "post-install,post-upgrade"
seed_container = seed_job["spec"]["template"]["spec"]["containers"][0]
seed_deployment = next(doc for doc in seed_docs if doc["kind"] == "Deployment")
assert seed_container["image"] == seed_deployment["spec"]["template"]["spec"]["containers"][0]["image"]
assert "PGPASSWORD=\"$(cat /mnt/secrets/spring.datasource.password)\"" in seed_container["command"][2]
assert "set -x" not in seed_container["command"][2]
seed_env = {item["name"]: item["value"] for item in seed_container["env"]}
assert seed_env["PGSSLMODE"] == "verify-full"
assert seed_env["PGOPTIONS"] == "-c search_path=hml"
assert "seed.sql" in next(doc for doc in seed_docs if doc["kind"] == "ConfigMap")["data"]
print("Helm semantic contracts passed: hml, prod, local and invalid digest")
