"""Local delivery policy checks. Parses YAML without YAML 1.1 'on' coercion."""
from pathlib import Path
import yaml

ROOT = Path(__file__).resolve().parents[1]


def workflow(name):
    return yaml.load((ROOT / ".github/workflows" / name).read_text(encoding="utf-8-sig"), Loader=yaml.BaseLoader)


ci = workflow("ci-cd.yml")
assert set(ci["jobs"]) == {"backend", "image", "helm-smoke", "security"}
for name, job in ci["jobs"].items():
    assert job["name"] == f"app / {name}"
    assert job["runs-on"] == "ubuntu-latest"
for name in ("backend", "image"):
    assert ci["jobs"][name]["services"]["postgres"]["image"] == "postgres:16-alpine"
backend = str(ci["jobs"]["backend"])
for required in ("check bootJar", "npm run lint", "npm test", "npm run build"):
    assert required in backend
helm = str(ci["jobs"]["helm-smoke"])
for required in ("helm lint", "render.py", "kind load", "helm upgrade", "--atomic --wait", "smoke-test.ps1"):
    assert required in helm
assert "trivy" in str(ci["jobs"]["security"])
assert "--exit-code 1" in str(ci["jobs"]["security"])
deploy = workflow("deploy.yml")
job = deploy["jobs"]["deploy"]
assert job["needs"] == "quality"
assert job["permissions"]["id-token"] == "write"
assert job["concurrency"]["cancel-in-progress"] == "false"
assert "prod" in job["environment"] and "hml" in job["environment"]
steps = job["steps"]
for step in steps:
    command = step.get("run", "")
    assert ":latest" not in command
    if "docker push" in command or "docker build" in command:
        assert step.get("if") == "github.ref_name == 'develop'"
assert any("describe-images" in step.get("run", "") for step in steps)
assert any(step.get("run") == "node scripts/promote-image.mjs" and step.get("if") == "github.ref_name == 'main'" for step in steps)
delivery = (ROOT / "scripts/deploy-release.ps1").read_text()
for required in ("--atomic", "--wait", "helm rollback", "smoke-test.ps1", "cleanup-hooks.py"):
    assert required in delivery
for name in ("rollback", "destroy"):
    manual = workflow(f"{name}.yml")
    assert set(manual["on"]) == {"workflow_dispatch"}
    manual_job = manual["jobs"][name]
    assert manual_job["concurrency"]["group"] == "app-${{ inputs.environment }}"
    actions = manual_job["steps"]
    aws_index = next(i for i, step in enumerate(actions) if "configure-aws-credentials" in step.get("uses", ""))
    assert any("Branch does not match environment" in step.get("run", "") for step in actions[:aws_index])
    if name == "destroy":
        assert any("-cne 'DESTROY-soat-oficina-app'" in step.get("run", "") for step in actions[:aws_index])
        assert "delete namespace" not in str(actions)
        assert "delete cluster" not in str(actions)
print("Application workflow policies passed")
