#!/usr/bin/env bash
# Ejecuta el programa con cada archivo de pruebas/entradas/ como si un usuario
# escribiera esas respuestas, y guarda lo que muestra en pruebas/salidas/.
# Uso: bash pruebas/ejecutar_pruebas.sh
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
command -v scala-cli >/dev/null 2>&1 || { echo "ERROR: falta scala-cli"; exit 1; }

echo "==> Compilando"
scala-cli compile . || { echo "ERROR: no compila"; exit 1; }

for entrada in pruebas/entradas/*.txt; do
  nombre="$(basename "$entrada" .txt)"
  echo "==> Ejecutando $nombre"
  # Límite de 120 s y de tamaño, por si un caso quedara pidiendo datos sin fin.
  perl -e 'alarm 120; exec @ARGV' scala-cli run . -q < "$entrada" 2>&1 \
    | head -c 300000 > "pruebas/salidas/$nombre.txt"
done
echo "==> Listo. Salidas en pruebas/salidas/"
