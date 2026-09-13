{{- define "oci.validate" -}}
{{- if not (has .Values.environment (list "hml" "prod")) }}{{ fail "environment must be hml or prod" }}{{ end -}}
{{- if ne .Release.Namespace .Values.environment }}{{ fail "namespace must match environment" }}{{ end -}}
{{- if not (regexMatch "^sha256:[a-f0-9]{64}$" .Values.image.digest) }}{{ fail "image.digest must be a sha256 digest" }}{{ end -}}
{{- if not .Values.existingSecret }}{{ fail "existingSecret is required" }}{{ end -}}
{{- end -}}
{{- define "oci.image" -}}
{{ required "image.repository is required" .Values.image.repository }}@{{ .Values.image.digest }}
{{- end -}}
{{- define "oci.security" -}}
runAsNonRoot: true
runAsUser: 1001
runAsGroup: 1001
fsGroup: 1001
seccompProfile:
  type: RuntimeDefault
{{- end -}}
{{- define "oci.env" -}}
- name: APP_ENVIRONMENT
  value: {{ .Values.environment | quote }}
- name: SPRING_PROFILES_ACTIVE
  value: docker
- name: SPRING_JPA_SHOW_SQL
  value: "false"
- name: SPRING_APPLICATION_JSON
  value: {{ dict "spring.jpa.properties.hibernate.default_schema" .Values.environment | toJson | quote }}
- name: SPRING_FLYWAY_SCHEMAS
  value: {{ .Values.environment | quote }}
- name: SPRING_FLYWAY_DEFAULT_SCHEMA
  value: {{ .Values.environment | quote }}
- name: SPRING_FLYWAY_CREATE_SCHEMAS
  value: "true"
- name: SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE
  value: "4"
- name: SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE
  value: "0"
- name: SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT
  value: "30000"
- name: LOGGING_STRUCTURED_FORMAT_CONSOLE
  value: logstash
{{- end -}}
