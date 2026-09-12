{{- define "oficina.name" -}}
{{- if gt (len .Release.Name) 40 }}{{ fail "release name must be <=40 characters" }}{{ end -}}
{{- .Release.Name -}}
{{- end -}}
{{- define "oficina.revision" -}}
{{ include "oficina.name" . }}-r{{ .Release.Revision }}
{{- end -}}
{{- define "oficina.labels" -}}
app.kubernetes.io/name: oficina
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/part-of: oficina
oficina.environment: {{ .Values.environment | quote }}
{{- end -}}
{{- define "oficina.image" -}}
{{- $repo := required "image.repository is required" .Values.image.repository -}}
{{- if .Values.local -}}
{{ $repo }}:{{ required "local image.tag is required" .Values.image.tag }}
{{- else -}}
{{- if not (regexMatch "^sha256:[a-f0-9]{64}$" .Values.image.digest) }}{{ fail "image.digest must be a sha256 digest" }}{{ end -}}
{{ $repo }}@{{ .Values.image.digest }}
{{- end -}}
{{- end -}}
{{- define "oficina.mounts" -}}
- name: configuration
  mountPath: /etc/oficina
  readOnly: true
{{- if not .Values.local }}
- name: secrets
  mountPath: /mnt/secrets
  readOnly: true
{{- end }}
- name: tmp
  mountPath: /tmp
{{- end -}}
{{- define "oficina.volumes" -}}
- name: configuration
  configMap:
    name: {{ include "oficina.revision" . }}
{{- if not .Values.local }}
- name: secrets
  csi:
    driver: secrets-store.csi.k8s.io
    readOnly: true
    volumeAttributes:
      secretProviderClass: {{ include "oficina.revision" . }}
{{- end }}
- name: tmp
  emptyDir: {}
{{- end -}}
{{- define "oficina.env" -}}
- name: APP_ENVIRONMENT
  value: {{ .Values.environment | quote }}
- name: DB_ENDPOINT
  value: {{ required "db.endpoint is required" .Values.db.endpoint | quote }}
- name: SPRING_PROFILES_ACTIVE
  value: {{ ternary "local" "docker" .Values.local | quote }}
- name: SPRING_CONFIG_ADDITIONAL_LOCATION
  value: file:/etc/oficina/
{{- if not .Values.local }}
- name: SPRING_CONFIG_IMPORT
  value: configtree:/mnt/secrets/
{{- end }}
- name: OTEL_SERVICE_NAME
  value: oficina
- name: OTEL_RESOURCE_ATTRIBUTES
  value: {{ printf "service.version=%s,deployment.environment.name=%s" .Values.gitSha .Values.environment | quote }}
{{- end -}}
