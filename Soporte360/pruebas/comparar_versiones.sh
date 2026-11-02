#!/usr/bin/env bash
# Prueba y analiza las dos versiones (Scala y Python) para compararlas.
# Uso: bash pruebas/comparar_versiones.sh
set -uo pipefail
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
echo "######## 1/4 Pruebas Scala";   bash "$RAIZ/Soporte360/pruebas/ejecutar_pruebas.sh"
echo "######## 2/4 Pruebas Python";  bash "$RAIZ/Soporte360_Python/pruebas/ejecutar_pruebas.sh"
echo "######## 3/4 Sonar Scala";     bash "$RAIZ/Soporte360/sonar/analizar.sh"
echo "######## 4/4 Sonar Python";    bash "$RAIZ/Soporte360_Python/sonar/analizar.sh"
echo "######## TERMINADO"
