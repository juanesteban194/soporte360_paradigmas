# Soporte360 — Registro y análisis de solicitudes de soporte técnico

Práctica de **Paradigmas de Programación** (Universidad de Medellín): una aplicación de consola,
escrita con **programación funcional**, que registra solicitudes de soporte técnico ingresadas por
teclado y genera reportes para la gerencia de una empresa ficticia de soporte (Soporte360 SAS).

La misma solución está implementada dos veces, con las mismas reglas, para compararlas:

| Versión | Archivo | Cómo ejecutarla |
|---|---|---|
| **Scala 3** (principal) | [`Soporte360/Soporte360.scala`](Soporte360/Soporte360.scala) | `scala-cli run Soporte360` o ▶ en IntelliJ IDEA |
| **Python 3.10+** | [`Soporte360_Python/soporte360.py`](Soporte360_Python/soporte360.py) | `python3 Soporte360_Python/soporte360.py` |

## Qué hace

1. Pregunta cuántas solicitudes se van a registrar (N puede ser 0) y pide los datos de cada una:
   id, cliente, técnico, categoría (Hardware/Software/Redes), prioridad (Alta/Media/Baja),
   estado (Pendiente/En proceso/Finalizado), horas y costo por hora.
2. Valida cada dato al momento (id no repetido, textos no vacíos, opciones permitidas, números no
   negativos). Si un dato es inválido, explica el error y lo vuelve a pedir.
3. Muestra los reportes:

| Requisito | Reporte |
|---|---|
| RF02 | Listado de todas las solicitudes |
| RF03 | Solicitudes pendientes y en proceso |
| RF04 | Cantidad por categoría y la de mayor demanda |
| RF05 | Valor de cada solicitud, valor total y promedio |
| RF06 | Las tres solicitudes de mayor costo |
| RF07 | Resumen por técnico y técnico con mayor carga (horas) |
| RF08 | Clientes con más de una solicitud |
| RF09 | Los tres clientes de mayor valor económico |
| RF10 | Solicitudes críticas (prioridad Alta sin finalizar) |
| RF11 | Solicitudes con horas por encima del promedio |
| RF12 | Cantidad por prioridad y porcentaje de prioridad Alta |
| RF14 | Resumen ejecutivo consolidado |

## Reglas de programación funcional que cumple el código

- **Sin `for`, `while` ni `do-while`** (en Python tampoco comprensiones `[x for …]`): se usan
  `map`, `filter`, `foldLeft`/`reduce`, `groupBy`, `sortBy`, `partition`… y recursión para repetir
  una pregunta.
- **Sin variables mutables (`var`)**: `case class` / `@dataclass(frozen=True)`, `List` / `tuple`.
- **Sin `try`/`catch` ni `throw`**: los errores son valores (`Either`/`Option` en Scala,
  `Valido`/`Invalido` y `None` en Python).
- **Colecciones vacías controladas**: cada reporte muestra "Sin datos" o "No hay…".
- **Valores económicos con dos decimales.**
- **Empates con criterio fijo**: primero el valor (mayor a menor) y luego nombre (A-Z) o id.
- **Solo biblioteca estándar**, sin bases de datos.

## Organización del código

Cada versión está en **un solo archivo** dividido en 6 módulos, separados por bloques de comentario:

| Módulo | Responsabilidad |
|---|---|
| 1 · MODELO | La solicitud y los valores permitidos |
| 2 · VALIDACIÓN | RF13: una función pura por tipo de dato |
| 3 · ENTRADA | RF01: lectura por teclado y registro de N solicitudes |
| 4 · ANÁLISIS | RF03–RF12: cálculos puros (no imprimen) |
| 5 · REPORTES | RF02–RF12 y RF14: solo presentación |
| 6 · MAIN | Coordina el flujo |

## Pruebas

`pruebas/entradas/` contiene 4 escenarios escritos como lo haría un usuario por teclado:

| Caso | Qué comprueba |
|---|---|
| `caso1_normal` | Todos los reportes con 4 solicitudes |
| `caso2_vacio` | N = 0: ningún reporte falla con colecciones vacías |
| `caso3_errores` | Datos inválidos: se rechazan y se vuelven a pedir (RF13) |
| `caso4_empates` | Empates en todos los rankings |

```bash
bash Soporte360/pruebas/ejecutar_pruebas.sh          # Scala
bash Soporte360_Python/pruebas/ejecutar_pruebas.sh   # Python
bash Soporte360/pruebas/comparar_versiones.sh        # ambas + análisis SonarQube
```

Las dos versiones producen **la misma salida** en los 4 casos (solo cambia el texto de un mensaje
cuando se escribe `NaN` como número).

## Calidad (SonarQube Community Build 26.10)

| Métrica | Scala | Python |
|---|---|---|
| Bugs / vulnerabilidades / code smells | 0 / 0 / 0 | 0 / 0 / 0 |
| Calificaciones (fiabilidad, seguridad, mantenibilidad) | A · A · A | A · A · A |
| Código duplicado | 0 % | 0 % |
| Líneas de código | 339 | 340 |
| Funciones | 59 | 67 |

Los scripts de `sonar/` analizan cada versión contra un servidor SonarQube local
(`http://localhost:9000`) y leen el token desde `~/.sonarqube-local/token`, fuera del repositorio.

## Scala vs Python

| Aspecto | Scala | Python |
|---|---|---|
| Inmutabilidad | La impone el compilador (`val`, `case class`) | Depende de la disciplina (`frozen=True`, tuplas) |
| Errores sin excepciones | `Either`, `Option`, `toIntOption`, `toDoubleOption` incluidos | Hubo que crear `Valido`/`Invalido`, `encadenar` (≈ `flatMap`) y validar con expresiones regulares |
| Colecciones | Métodos encadenados: `.filter(…).sortBy(…).take(3)` | Funciones anidadas; `groupBy`, `partition` y `headOption` escritos a mano (por eso 8 funciones más) |
| Recursión | `@tailrec`: no consume pila | Sin optimización de recursión (límite ≈ 1000 llamadas) |
| Tipos | Estáticos: errores al compilar | Dinámicos: errores al ejecutar |

**Conclusión:** para un enunciado que exige inmutabilidad, cero ciclos y cero excepciones, **Scala es
la mejor opción**, porque el lenguaje garantiza esas reglas y ya trae las herramientas funcionales.
Python logra el mismo resultado, pero construyendo a mano lo que Scala incluye y sin que nada impida
romper las reglas por descuido. Su ventaja es una sintaxis más corta y que no requiere compilar.

## Requisitos

- **Scala:** [Scala CLI](https://scala-cli.virtuslab.org/) (descarga Scala 3.9 automáticamente) o IntelliJ IDEA con el plugin de Scala.
- **Python:** Python 3.10 o superior.

---
Autor: Juan Esteban Echavarria Corrales · Ingeniería de Sistemas · Universidad de Medellín
