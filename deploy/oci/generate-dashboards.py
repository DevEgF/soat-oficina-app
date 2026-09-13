#!/usr/bin/env python3
"""Generate importable private New Relic dashboards; no credentials required."""
import json
from pathlib import Path

ACCOUNT = 8439293


def widget(title, query, row, column=1, width=6, kind="line"):
    return {"title": title, "layout": {"column": column, "row": row, "width": width, "height": 3},
            "visualization": {"id": "viz." + kind},
            "rawConfiguration": {"nrqlQueries": [{"accountId": ACCOUNT, "query": query}]}}


def dashboard(env):
    api = f"FROM Transaction WHERE appName = 'oficina-{env}'"
    logs = f"FROM Log WHERE cluster_name = 'oficina-oci' AND namespace_name = '{env}'"
    auth = logs + " AND service = 'oficina-auth' AND statusCode IS NOT NULL"
    daily = f"FROM OficinaBusinessDaily WHERE environment = '{env}'"
    stages = f"FROM OficinaBusinessStageSnapshot WHERE environment = '{env}'"
    infra = f"WHERE clusterName = 'oficina-oci' AND namespaceName = '{env}'"
    business = [
        widget("OS por dia - ultimos 7 dias (America/Sao_Paulo)", f"SELECT latest(createdCount) AS 'Criadas', latest(finalizedCount) AS 'Finalizadas', latest(deliveredCount) AS 'Entregues' {daily} FACET businessDate SINCE 30 minutes ago LIMIT 7", 1, width=12, kind="table"),
        widget("Tempo medio por etapa (ms) - janela de 7 dias", f"SELECT latest(averageDurationMs) AS 'Media ms' {stages} AND sampleCount > 0 FACET stage SINCE 30 minutes ago", 4, kind="bar"),
        widget("Amostras por etapa", f"SELECT latest(sampleCount) AS 'OS' {stages} FACET stage SINCE 30 minutes ago", 4, 7, kind="table"),
        widget("Falhas tecnicas de processamento registradas", f"SELECT sum(WorkOrderProcessingFailures) {logs} AND WorkOrderProcessingFailures IS NOT NULL FACET Operation SINCE 8 hours ago TIMESERIES", 7),
        widget("Coleta de negocio - heartbeat (1 = recebido)", f"SELECT latest(healthy) AS 'Coleta ativa' FROM OficinaTelemetryHeartbeat WHERE environment = '{env}' SINCE 15 minutes ago", 7, 7, kind="billboard"),
    ]
    business.append({"title": "Como interpretar", "layout": {"column": 1, "row": 10, "width": 12, "height": 3}, "visualization": {"id": "viz.markdown"}, "rawConfiguration": {"text": "Dados reais do Neon, atualizados a cada 5 minutos. Contagens usam latest para evitar duplicacao de snapshots. Sem amostras significa ausencia de dados, nao sucesso comprovado. Etapas: diagnostico = criacao ate diagnostico; aprovacao = envio do orcamento ate aprovacao; execucao = inicio ate finalizacao; finalizacao = finalizacao ate entrega. Homologacao inclui jornada sintetica de aceite. Alertas e entrega de notificacoes exigem evidencia propria."}})
    technical = [
        widget("API - requisicoes por minuto", f"SELECT rate(count(*), 1 minute) {api} SINCE 8 hours ago TIMESERIES", 1),
        widget("API - latencia p50/p95/p99 (ms)", f"SELECT percentile(duration * 1000, 50, 95, 99) {api} SINCE 8 hours ago TIMESERIES", 1, 7),
        widget("API - HTTP 4xx e 5xx", f"SELECT filter(count(*), WHERE `http.statusCode` >= 400 AND `http.statusCode` < 500) AS '4xx', filter(count(*), WHERE `http.statusCode` >= 500) AS '5xx' {api} SINCE 8 hours ago TIMESERIES", 4),
        widget("API - erros APM (%)", f"SELECT percentage(count(*), WHERE error IS true) {api} SINCE 8 hours ago TIMESERIES", 4, 7),
        widget("Auth OCI - respostas por status", f"SELECT count(*) {auth} FACET statusCode SINCE 8 hours ago TIMESERIES", 7),
        widget("Auth OCI - latencia (ms)", f"SELECT percentile(durationMs, 50, 95, 99) {auth} SINCE 8 hours ago TIMESERIES", 7, 7),
        widget("Correlacao - erros recentes (sem corpo de requisicao)", f"SELECT timestamp, service, level, requestId, `trace.id`, statusCode {logs} AND (level = 'ERROR' OR statusCode >= 500) SINCE 8 hours ago LIMIT 30", 10, width=12, kind="table"),
        widget("Traces recentes API", f"SELECT timestamp, name, traceId, duration {api} AND traceId IS NOT NULL SINCE 8 hours ago LIMIT 20", 13, width=12, kind="table"),
    ]
    infrastructure = [
        widget("VM - CPU utilizada (cores)", "SELECT average(cpuUsedCores) FROM K8sNodeSample WHERE clusterName = 'oficina-oci' FACET nodeName SINCE 8 hours ago TIMESERIES", 1),
        widget("VM - memoria working set (MiB)", "SELECT average(memoryWorkingSetBytes) / 1048576 FROM K8sNodeSample WHERE clusterName = 'oficina-oci' FACET nodeName SINCE 8 hours ago TIMESERIES", 1, 7),
        widget("Deployments - replicas desejadas e prontas", f"SELECT latest(podsDesired), latest(podsReady), latest(podsMissing) FROM K8sDeploymentSample {infra} FACET deploymentName SINCE 15 minutes ago", 4, width=12, kind="table"),
        widget("Pods - readiness", f"SELECT latest(isReady) FROM K8sPodSample {infra} FACET podName SINCE 15 minutes ago", 7, kind="table"),
        widget("Pods - memoria (MiB)", f"SELECT average(memoryWorkingSetBytes) / 1048576 FROM K8sPodSample {infra} FACET podName SINCE 8 hours ago TIMESERIES", 7, 7),
        widget("Node - Ready e pressao de memoria/disco", "SELECT latest(`condition.Ready`), latest(`condition.MemoryPressure`), latest(`condition.DiskPressure`) FROM K8sNodeSample WHERE clusterName = 'oficina-oci' FACET nodeName SINCE 15 minutes ago", 10, width=12, kind="table"),
    ]
    return {"name": f"Oficina - {env.upper()} - Aceite Fase 3", "permissions": "PRIVATE", "pages": [
        {"name": "Negocio", "widgets": business}, {"name": "API e autenticacao", "widgets": technical},
        {"name": "Kubernetes OCI", "widgets": infrastructure}]}


if __name__ == "__main__":
    destination = Path(__file__).parent / "dashboards"
    destination.mkdir(exist_ok=True)
    for environment in ("hml", "prod"):
        (destination / f"{environment}.json").write_text(json.dumps(dashboard(environment), indent=2) + "\n", encoding="utf-8")
