"""Delete only obsolete hook dependencies after a successful deployment.

Every retained Helm history manifest remains a supported rollback target.
Run only after health success, with the release's environment concurrency lock.
"""
import json
import re
import subprocess
import sys
import yaml


def output(*args):
    return subprocess.check_output(args, text=True)


def retained_names(manifests):
    names = set()
    for manifest in manifests:
        for doc in yaml.safe_load_all(manifest):
            if not doc or doc.get("kind") != "Deployment":
                continue
            for volume in doc["spec"]["template"]["spec"].get("volumes", []):
                if "configMap" in volume:
                    names.add(volume["configMap"]["name"])
                if "csi" in volume:
                    names.add(volume["csi"]["volumeAttributes"]["secretProviderClass"])
    return names


def main():
    environment, release = sys.argv[1:]
    if environment not in ("hml", "prod") or release != f"oficina-{environment}":
        raise ValueError("invalid environment/release scope")
    history = json.loads(output("helm", "history", release, "-n", environment, "-o", "json"))
    if not history or not any(item["status"] == "deployed" for item in history):
        raise ValueError("cleanup requires a deployed release")
    retained = retained_names(output("helm", "get", "manifest", release, "-n", environment,
                                     "--revision", str(item["revision"])) for item in history)
    selector = f"app.kubernetes.io/instance={release},oficina.environment={environment},oficina.hook-resource=true"
    resources = json.loads(output("kubectl", "-n", environment, "get", "configmaps,secretproviderclasses.secrets-store.csi.x-k8s.io",
                                  "-l", selector, "-o", "json"))
    for resource in resources["items"]:
        name = resource["metadata"]["name"]
        if re.fullmatch(re.escape(release) + r"-r\d+", name) and name not in retained:
            subprocess.run(["kubectl", "-n", environment, "delete", resource["kind"], name], check=True)


if __name__ == "__main__":
    main()
