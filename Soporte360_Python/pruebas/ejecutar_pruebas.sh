#!/usr/bin/env bash
# Ejecuta soporte360.py con cada archivo de pruebas/entradas/ y guarda la salida en pruebas/salidas/.
# Uso: bash pruebas/ejecutar_pruebas.sh
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
export PATH="$HOME/anaconda3/bin:/opt/homebrew/bin:/usr/local/bin:$PATH"
command -v python3 >/dev/null 2>&1 || { echo "ERROR: falta python3"; exit 1; }
echo "==> $(python3 --version)"
python3 -c 'import sys; sys.exit(0 if sys.version_info >= (3, 10) else 1)' \
  || { echo "ERROR: se necesita Python 3.10 o superior (usa match/case)"; exit 1; }

for entrada in pruebas/entradas/*.txt; do
  nombre="$(basename "$entrada" .txt)"
  echo "==> Ejecutando $nombre"
  perl -e 'alarm 60; exec @ARGV' python3 soporte360.py < "$entrada" 2>&1 \
    | head -c 300000 > "pruebas/salidas/$nombre.txt"
done
echo "==> Listo. Salidas en pruebas/salidas/"
