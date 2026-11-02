#!/usr/bin/env bash
# Analiza el proyecto con SonarQube, espera el resultado y lo guarda en sonar/resultado/.
# Arranca el servidor si está apagado.
# Uso: bash sonar/analizar.sh
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/comun.sh"

cargar_brew
command -v sonar-scanner >/dev/null 2>&1 || falla "Falta sonar-scanner."
[[ -f "$CONF_DIR/token" ]] || falla "Falta el token en $CONF_DIR/token."

iniciar_servidor

paso "Analizando $PROJECT_KEY"
cd "$PROJECT_DIR"
TOKEN="$(cat "$CONF_DIR/token")"
SONAR_TOKEN="$TOKEN" sonar-scanner

paso "Esperando a que el servidor procese el análisis"
TASK_URL="$(sed -n 's/^ceTaskUrl=//p' .scannerwork/report-task.txt)"
estado_tarea=""
for intento in $(seq 1 60); do
  estado_tarea="$(curl -fsS -H "Authorization: Bearer $TOKEN" "$TASK_URL" 2>/dev/null \
    | sed -n 's/.*"status":"\([A-Z_]*\)".*/\1/p' || true)"
  case "$estado_tarea" in SUCCESS|FAILED|CANCELED) break ;; esac
  sleep 2
done
echo "Procesamiento: ${estado_tarea:-desconocido}"

paso "Guardando resultados en sonar/resultado/"
mkdir -p sonar/resultado
api() { curl -fsS -H "Authorization: Bearer $TOKEN" "$SONAR_URL/api/$1" || echo '{}'; }
api "issues/search?components=$PROJECT_KEY&resolved=false&ps=500" > sonar/resultado/issues.json
api "qualitygates/project_status?projectKey=$PROJECT_KEY"       > sonar/resultado/quality-gate.json
api "measures/component?component=$PROJECT_KEY&metricKeys=ncloc,functions,bugs,vulnerabilities,code_smells,duplicated_lines_density,cognitive_complexity,complexity,reliability_rating,security_rating,sqale_rating" \
                                                                 > sonar/resultado/metricas.json
api "hotspots/search?project=$PROJECT_KEY&ps=500"               > sonar/resultado/hotspots.json

printf '\nResultados: %s/dashboard?id=%s\n' "$SONAR_URL" "$PROJECT_KEY"
