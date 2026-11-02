#!/usr/bin/env bash
# Funciones compartidas por instalar.sh y analizar.sh (no ejecutar directamente).

SONAR_VERSION="${SONAR_VERSION:-26.10.0.133078}"
SONAR_URL="http://localhost:9000"
SONAR_HOME="$HOME/sonarqube"
CONF_DIR="$HOME/.sonarqube-local"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_KEY="$(sed -n 's/^sonar.projectKey=//p' "$PROJECT_DIR/sonar-project.properties")"
SONAR_SH="$SONAR_HOME/actual/bin/macosx-universal-64/sonar.sh"

paso()  { printf '\n==> %s\n' "$1"; }
falla() { printf '\nERROR: %s\n' "$1" >&2; exit 1; }

cargar_brew() {
  export HOMEBREW_NO_ASK=1   # sin confirmaciones [y/n]: el script corre desatendido
  if ! command -v brew >/dev/null 2>&1; then
    local b
    for b in /opt/homebrew/bin/brew /usr/local/bin/brew; do
      if [[ -x "$b" ]]; then eval "$("$b" shellenv)"; break; fi
    done
  fi
  command -v brew >/dev/null 2>&1 || falla "Homebrew no está instalado (https://brew.sh)."
}

java21() { printf '%s/bin/java' "$(brew --prefix openjdk@21)"; }

estado_servidor() {
  curl -fsS --max-time 5 "$SONAR_URL/api/system/status" 2>/dev/null \
    | sed -n 's/.*"status":"\([A-Z_]*\)".*/\1/p' || true
}

iniciar_servidor() {
  [[ -x "$SONAR_SH" ]] || falla "SonarQube no está instalado. Ejecuta: bash sonar/instalar.sh"
  if [[ "$(estado_servidor)" == "UP" ]]; then return 0; fi
  export SONAR_JAVA_PATH
  SONAR_JAVA_PATH="$(java21)"
  "$SONAR_SH" status >/dev/null 2>&1 || "$SONAR_SH" start
  echo "Esperando a que SonarQube esté listo (1-3 minutos la primera vez)..."
  local i estado=""
  for i in $(seq 1 120); do
    estado="$(estado_servidor)"
    [[ "$estado" == "UP" ]] && { echo "SonarQube listo en $SONAR_URL"; return 0; }
    sleep 5
  done
  falla "SonarQube no arrancó (estado: ${estado:-sin respuesta}). Revisa los logs en $SONAR_HOME/actual/logs/"
}

token_valido() {
  curl -fsS --max-time 10 -H "Authorization: Bearer $1" \
    "$SONAR_URL/api/authentication/validate" 2>/dev/null | grep -q '"valid":true'
}
