# =====================================================================
#  SOPORTE360 SAS — Registro y análisis de solicitudes de soporte
#  Práctica de Paradigmas · Versión en PYTHON (misma lógica que Scala)
# =====================================================================
#
#  ÍNDICE DE MÓDULOS (separados por un bloque de comentario grande,
#  en el mismo orden que la versión en Scala):
#
#    MÓDULO 1 · MODELO      -> clase Solicitud y valores válidos
#    MÓDULO 2 · VALIDACIÓN  -> RF13 (verificar cada dato)
#    MÓDULO 3 · ENTRADA     -> RF01 (registro por teclado)
#    MÓDULO 4 · ANÁLISIS    -> RF03 a RF12 (cálculos)
#    MÓDULO 5 · REPORTES    -> RF02 a RF12 y RF14 (presentación)
#    MÓDULO 6 · MAIN        -> punto de entrada
#
#  REGLAS QUE CUMPLE TODO EL CÓDIGO (las mismas de Scala):
#    - Sin for ni while (tampoco comprensiones de listas): se usan
#      map, filter, reduce, sorted... y recursión para repetir preguntas.
#    - Sin variables que cambien: los datos son tuplas y dataclasses
#      congeladas (frozen=True), que no se pueden modificar.
#    - Sin try/except: los errores son valores (Valido / Invalido).
#    - Solo biblioteca estándar de Python (re, dataclasses, functools).
#    - Requiere Python 3.10 o superior (usa match/case).
# =====================================================================

import re
from dataclasses import dataclass
from functools import reduce


# =====================================================================
# =====================================================================
#  MÓDULO 1 · MODELO — Datos del negocio
# ---------------------------------------------------------------------
#  Qué es una solicitud de soporte (RF01) y cuáles son los valores
#  permitidos para categoría, prioridad y estado.
#  Si cambia un texto (ej: "Finalizado"), se cambia solo aquí.
# =====================================================================
# =====================================================================

PRIORIDAD_ALTA = "Alta"
ESTADO_PENDIENTE = "Pendiente"
ESTADO_EN_PROCESO = "En proceso"
ESTADO_FINALIZADO = "Finalizado"

CATEGORIAS_VALIDAS = ("Hardware", "Software", "Redes")
PRIORIDADES_VALIDAS = (PRIORIDAD_ALTA, "Media", "Baja")
ESTADOS_VALIDOS = (ESTADO_PENDIENTE, ESTADO_EN_PROCESO, ESTADO_FINALIZADO)


# frozen=True: equivale a la case class de Scala, sus datos no se pueden cambiar.
@dataclass(frozen=True)
class Solicitud:
    id: int
    cliente: str
    tecnico: str
    categoria: str
    prioridad: str
    estado: str
    horas: float
    costo_por_hora: float

    # RF05: valor económico de esta solicitud.
    @property
    def valor_economico(self) -> float:
        return self.horas * self.costo_por_hora

    @property
    def esta_finalizada(self) -> bool:
        return self.estado == ESTADO_FINALIZADO


# =====================================================================
# =====================================================================
#  MÓDULO 2 · VALIDACIÓN — RF13: verificación de información
# ---------------------------------------------------------------------
#  Cada función revisa UN dato y devuelve:
#    Valido(dato limpio) -> el dato es válido        (Right en Scala)
#    Invalido(mensaje)   -> el dato es inválido      (Left en Scala)
#  Funciones puras: no leen teclado ni imprimen.
# =====================================================================
# =====================================================================

@dataclass(frozen=True)
class Valido:
    valor: object


@dataclass(frozen=True)
class Invalido:
    mensaje: str


# Python no tiene toIntOption / toDoubleOption: convertir "abc" con int()
# lanza un error. Para no usar try/except, primero se revisa el formato
# con una expresión regular y solo se convierte si es un número.
PATRON_ENTERO = re.compile(r"-?\d+")
PATRON_DECIMAL = re.compile(r"-?\d+(\.\d+)?")


def a_entero(texto: str):
    texto_limpio = texto.strip()
    return int(texto_limpio) if PATRON_ENTERO.fullmatch(texto_limpio) else None


def a_decimal(texto: str):
    texto_limpio = texto.strip()
    return float(texto_limpio) if PATRON_DECIMAL.fullmatch(texto_limpio) else None


# Equivale a flatMap de Scala: si el resultado es válido, aplica la
# siguiente validación; si es inválido, devuelve el mismo error.
def encadenar(resultado, siguiente_validacion):
    match resultado:
        case Valido(valor):
            return siguiente_validacion(valor)
        case _:
            return resultado


# Cliente y técnico: el texto no puede quedar vacío.
def validar_texto_no_vacio(texto: str, campo: str):
    texto_limpio = texto.strip()
    if texto_limpio == "":
        return Invalido(f"El campo {campo} no puede estar vacío.")
    return Valido(texto_limpio)


# Categoría, prioridad y estado: deben estar en la lista permitida.
# Acepta mayúsculas o minúsculas y devuelve el valor oficial ("redes" -> "Redes").
def validar_opcion(texto: str, opciones_validas: tuple, campo: str):
    opcion_oficial = next(
        filter(lambda opcion: opcion.lower() == texto.strip().lower(), opciones_validas), None)
    match opcion_oficial:
        case None:
            return Invalido(f"Valor no válido para {campo}. Opciones: {', '.join(opciones_validas)}.")
        case _:
            return Valido(opcion_oficial)


# Horas y costo por hora: número no negativo.
def validar_numero_no_negativo(texto: str, campo: str):
    match a_decimal(texto):
        case None:
            return Invalido(f"El valor de {campo} debe ser un número (use punto para decimales, ej: 1.5).")
        case numero if numero < 0:
            return Invalido(f"El valor de {campo} no puede ser negativo.")
        case numero:
            return Valido(numero)


# Cantidad N de solicitudes a registrar: entero, puede ser 0.
def validar_cantidad(texto: str):
    match a_entero(texto):
        case None:
            return Invalido("La cantidad debe ser un número entero.")
        case cantidad if cantidad < 0:
            return Invalido("La cantidad no puede ser negativa.")
        case cantidad:
            return Valido(cantidad)


# Identificador: primero debe ser entero positivo y luego no estar repetido.
def validar_id(texto: str, ids_registrados: tuple):
    return encadenar(validar_entero_positivo(texto, "identificador"),
                     lambda id_valido: validar_id_no_repetido(id_valido, ids_registrados))


def validar_entero_positivo(texto: str, campo: str):
    match a_entero(texto):
        case None:
            return Invalido(f"El {campo} debe ser un número entero.")
        case numero if numero <= 0:
            return Invalido(f"El {campo} debe ser mayor que cero.")
        case numero:
            return Valido(numero)


def validar_id_no_repetido(id_solicitud: int, ids_registrados: tuple):
    if any(map(lambda id_registrado: id_registrado == id_solicitud, ids_registrados)):
        return Invalido(f"El identificador {id_solicitud} ya está registrado.")
    return Valido(id_solicitud)


# =====================================================================
# =====================================================================
#  MÓDULO 3 · ENTRADA — RF01: registro de solicitudes por teclado
# ---------------------------------------------------------------------
#  Lee cada dato, lo revisa con el MÓDULO 2 y, si es inválido,
#  informa el error y lo vuelve a pedir (RF13).
#  Las N solicitudes se registran con reduce (equivale a foldLeft).
# =====================================================================
# =====================================================================

# Pide un dato hasta que sea válido. Sin ciclos: si el dato es inválido,
# la función se llama a sí misma (recursión).
def leer_dato_valido(mensaje: str, validar):
    match validar(input(mensaje)):
        case Valido(valor):
            return valor
        case Invalido(error):
            print(f"  Error: {error} Intente de nuevo.")
            return leer_dato_valido(mensaje, validar)


def leer_cantidad() -> int:
    return leer_dato_valido("¿Cuántas solicitudes desea registrar? ", validar_cantidad)


# Lee una solicitud completa. Recibe los ids ya usados para evitar repetidos.
def leer_solicitud(numero: int, ids_registrados: tuple) -> Solicitud:
    print(f"\n--- Solicitud {numero} ---")
    return Solicitud(
        id=leer_dato_valido("Identificador: ",
                            lambda texto: validar_id(texto, ids_registrados)),
        cliente=leer_dato_valido("Cliente: ",
                                 lambda texto: validar_texto_no_vacio(texto, "cliente")),
        tecnico=leer_dato_valido("Técnico responsable: ",
                                 lambda texto: validar_texto_no_vacio(texto, "técnico")),
        categoria=leer_dato_valido(f"Categoría ({'/'.join(CATEGORIAS_VALIDAS)}): ",
                                   lambda texto: validar_opcion(texto, CATEGORIAS_VALIDAS, "categoría")),
        prioridad=leer_dato_valido(f"Prioridad ({'/'.join(PRIORIDADES_VALIDAS)}): ",
                                   lambda texto: validar_opcion(texto, PRIORIDADES_VALIDAS, "prioridad")),
        estado=leer_dato_valido(f"Estado ({'/'.join(ESTADOS_VALIDOS)}): ",
                                lambda texto: validar_opcion(texto, ESTADOS_VALIDOS, "estado")),
        horas=leer_dato_valido("Horas dedicadas: ",
                               lambda texto: validar_numero_no_negativo(texto, "horas")),
        costo_por_hora=leer_dato_valido("Costo por hora: ",
                                        lambda texto: validar_numero_no_negativo(texto, "costo por hora")),
    )


# Agrega una solicitud nueva a las ya registradas (devuelve una tupla NUEVA).
def agregar_solicitud(registradas: tuple, numero: int) -> tuple:
    ids_registrados = tuple(map(lambda solicitud: solicitud.id, registradas))
    return registradas + (leer_solicitud(numero, ids_registrados),)


# Registra N solicitudes sin ciclos: reduce recorre 1..N y acumula la tupla.
def registrar_solicitudes(cantidad: int) -> tuple:
    return reduce(agregar_solicitud, range(1, cantidad + 1), ())


def registrar() -> tuple:
    return registrar_solicitudes(leer_cantidad())


# =====================================================================
# =====================================================================
#  MÓDULO 4 · ANÁLISIS — RF03 a RF12 (y datos para RF14)
# ---------------------------------------------------------------------
#  Funciones puras: reciben la tupla de solicitudes y devuelven un
#  resultado. Nunca leen teclado ni imprimen.
#  Criterio de empates (siempre el mismo):
#    1) por el valor del reporte, de mayor a menor
#    2) si empatan, por nombre (A-Z) o por id (menor a mayor)
#  "Sin resultado" se representa con None (Option en Scala).
# =====================================================================
# =====================================================================

# Resultado del RF07: resumen de un técnico.
@dataclass(frozen=True)
class ResumenTecnico:
    tecnico: str
    cantidad_solicitudes: int
    horas_acumuladas: float
    servicios_finalizados: int


# ---------- Funciones de apoyo (reutilizadas) ----------

# Promedio de una tupla. Si está vacía devuelve None (no hay promedio).
def promedio(valores: tuple):
    return reduce(lambda a, b: a + b, valores) / len(valores) if len(valores) > 0 else None


def contar(solicitudes: tuple, condicion) -> int:
    return len(tuple(filter(condicion, solicitudes)))


# Cuenta cuántas solicitudes tienen cada valor permitido de un campo.
def contar_segun(solicitudes: tuple, valores_permitidos: tuple, campo) -> tuple:
    return tuple(map(
        lambda valor: (valor, contar(solicitudes, lambda solicitud: campo(solicitud) == valor)),
        valores_permitidos))


# Equivale a groupBy de Scala: {clave: tupla de solicitudes}.
# Cada paso crea un diccionario NUEVO en lugar de modificar el anterior.
def agrupar_por(solicitudes: tuple, clave) -> dict:
    return reduce(
        lambda grupos, solicitud: {**grupos, clave(solicitud): grupos.get(clave(solicitud), ()) + (solicitud,)},
        solicitudes, {})


def primero_o_nada(elementos):
    return next(iter(elementos), None)


# ---------- RF03: solicitudes sin finalizar ----------

def solicitudes_pendientes(solicitudes: tuple) -> tuple:
    return tuple(filter(lambda solicitud: solicitud.estado == ESTADO_PENDIENTE, solicitudes))


def solicitudes_en_proceso(solicitudes: tuple) -> tuple:
    return tuple(filter(lambda solicitud: solicitud.estado == ESTADO_EN_PROCESO, solicitudes))


# Separa en (finalizadas, sin finalizar). Se usa en el RF14.
def separar_por_finalizacion(solicitudes: tuple) -> tuple:
    return (tuple(filter(lambda solicitud: solicitud.esta_finalizada, solicitudes)),
            tuple(filter(lambda solicitud: not solicitud.esta_finalizada, solicitudes)))


# ---------- RF04: distribución por categorías ----------

def contar_por_categoria(solicitudes: tuple) -> tuple:
    return contar_segun(solicitudes, CATEGORIAS_VALIDAS, lambda solicitud: solicitud.categoria)


def categoria_mayor_demanda(solicitudes: tuple):
    con_solicitudes = filter(lambda par: par[1] > 0, contar_por_categoria(solicitudes))
    return primero_o_nada(sorted(con_solicitudes, key=lambda par: (-par[1], par[0])))


# ---------- RF05: análisis económico ----------

def valor_total(solicitudes: tuple) -> float:
    return sum(map(lambda solicitud: solicitud.valor_economico, solicitudes))


def valor_promedio(solicitudes: tuple):
    return promedio(tuple(map(lambda solicitud: solicitud.valor_economico, solicitudes)))


# ---------- RF06: tres solicitudes de mayor costo ----------

def tres_mas_costosas(solicitudes: tuple) -> tuple:
    return tuple(sorted(solicitudes, key=lambda solicitud: (-solicitud.valor_economico, solicitud.id))[:3])


# ---------- RF07: desempeño de los técnicos ----------

def crear_resumen_tecnico(tecnico: str, del_tecnico: tuple) -> ResumenTecnico:
    return ResumenTecnico(
        tecnico,
        len(del_tecnico),
        sum(map(lambda solicitud: solicitud.horas, del_tecnico)),
        contar(del_tecnico, lambda solicitud: solicitud.esta_finalizada))


# Un resumen por técnico, ordenado por nombre.
def resumen_por_tecnico(solicitudes: tuple) -> tuple:
    grupos = agrupar_por(solicitudes, lambda solicitud: solicitud.tecnico)
    resumenes = map(lambda par: crear_resumen_tecnico(par[0], par[1]), grupos.items())
    return tuple(sorted(resumenes, key=lambda resumen: resumen.tecnico))


# Mayor carga = más horas registradas. None si no hay técnicos.
def tecnico_mayor_carga(solicitudes: tuple):
    return primero_o_nada(sorted(resumen_por_tecnico(solicitudes),
                                 key=lambda resumen: (-resumen.horas_acumuladas, resumen.tecnico)))


# ---------- RF08: clientes frecuentes ----------

def clientes_frecuentes(solicitudes: tuple) -> tuple:
    grupos = agrupar_por(solicitudes, lambda solicitud: solicitud.cliente)
    conteos = map(lambda par: (par[0], len(par[1])), grupos.items())
    frecuentes = filter(lambda par: par[1] > 1, conteos)
    return tuple(sorted(frecuentes, key=lambda par: (-par[1], par[0])))


# ---------- RF09: ingresos por cliente ----------

def valor_por_cliente(solicitudes: tuple) -> tuple:
    grupos = agrupar_por(solicitudes, lambda solicitud: solicitud.cliente)
    valores = map(lambda par: (par[0], valor_total(par[1])), grupos.items())
    return tuple(sorted(valores, key=lambda par: (-par[1], par[0])))


def tres_clientes_mayor_valor(solicitudes: tuple) -> tuple:
    return valor_por_cliente(solicitudes)[:3]


def cliente_mayor_valor(solicitudes: tuple):
    return primero_o_nada(valor_por_cliente(solicitudes))


# ---------- RF10: solicitudes críticas ----------

# Prioridad Alta y todavía Pendiente o En proceso (es decir, no finalizada).
def solicitudes_criticas(solicitudes: tuple) -> tuple:
    criticas = filter(lambda solicitud: solicitud.prioridad == PRIORIDAD_ALTA and not solicitud.esta_finalizada,
                      solicitudes)
    return tuple(sorted(criticas, key=lambda solicitud: solicitud.id))


# ---------- RF11: tiempos elevados ----------

def promedio_horas(solicitudes: tuple):
    return promedio(tuple(map(lambda solicitud: solicitud.horas, solicitudes)))


def solicitudes_con_horas_elevadas(solicitudes: tuple) -> tuple:
    match promedio_horas(solicitudes):
        case None:
            return ()
        case promedio_de_horas:
            elevadas = filter(lambda solicitud: solicitud.horas > promedio_de_horas, solicitudes)
            return tuple(sorted(elevadas, key=lambda solicitud: (-solicitud.horas, solicitud.id)))


# ---------- RF12: clasificación por prioridad ----------

def contar_por_prioridad(solicitudes: tuple) -> tuple:
    return contar_segun(solicitudes, PRIORIDADES_VALIDAS, lambda solicitud: solicitud.prioridad)


def porcentaje_prioridad_alta(solicitudes: tuple):
    if len(solicitudes) == 0:
        return None
    return contar(solicitudes, lambda solicitud: solicitud.prioridad == PRIORIDAD_ALTA) * 100.0 / len(solicitudes)


# =====================================================================
# =====================================================================
#  MÓDULO 5 · REPORTES — RF02 a RF12 y RF14: presentación
# ---------------------------------------------------------------------
#  Muestra en pantalla los resultados del MÓDULO 4. Aquí no se calcula.
#    Condición 6: colecciones vacías -> mensaje claro.
#    Condición 7: dinero con dos decimales.
# =====================================================================
# =====================================================================

TEXTO_SIN_DATOS = "Sin datos"
TEXTO_SIN_SOLICITUDES = "No hay solicitudes registradas."


# ---------- Formatos ----------

def formato_dinero(valor: float) -> str:
    return f"${valor:,.2f}"


def formato_horas(horas: float) -> str:
    return f"{horas:.2f} h"


def formato_porcentaje(porcentaje: float) -> str:
    return f"{porcentaje:.2f}%"


# Singular o plural según la cantidad: "1 solicitud", "3 solicitudes".
def texto_solicitudes(cantidad: int) -> str:
    return "1 solicitud" if cantidad == 1 else f"{cantidad} solicitudes"


# None -> "Sin datos"; si hay valor, lo formatea.
def texto_opcional(valor, formatear) -> str:
    return TEXTO_SIN_DATOS if valor is None else formatear(valor)


# ---------- Ayudas de presentación ----------

def titulo(texto: str) -> None:
    print()
    print("=" * 70)
    print(f" {texto}")
    print("=" * 70)


# Muestra cada elemento en una línea, o un mensaje si no hay elementos.
def mostrar_lista(elementos: tuple, mensaje_si_vacia: str, formatear) -> None:
    if len(elementos) == 0:
        print(f"  {mensaje_si_vacia}")
    else:
        print("\n".join(map(lambda elemento: f"  {formatear(elemento)}", elementos)))


# Agrega la posición a cada elemento: (a, b) -> ((1, a), (2, b)).
def numerar(elementos: tuple) -> tuple:
    return tuple(zip(range(1, len(elementos) + 1), elementos))


def describir_solicitud(solicitud: Solicitud) -> str:
    return (f"#{solicitud.id} | {solicitud.cliente} | Técnico: {solicitud.tecnico} | "
            f"{solicitud.categoria} | {solicitud.prioridad} | {solicitud.estado} | "
            f"{formato_horas(solicitud.horas)} x {formato_dinero(solicitud.costo_por_hora)} = "
            f"{formato_dinero(solicitud.valor_economico)}")


# ---------- RF02: consulta general ----------

def mostrar_solicitudes(solicitudes: tuple) -> None:
    titulo(f"RF02 · Solicitudes registradas ({len(solicitudes)})")
    mostrar_lista(solicitudes, TEXTO_SIN_SOLICITUDES, describir_solicitud)


# ---------- RF03: sin finalizar ----------

def mostrar_sin_finalizar(solicitudes: tuple) -> None:
    titulo("RF03 · Solicitudes sin finalizar")
    pendientes = solicitudes_pendientes(solicitudes)
    en_proceso = solicitudes_en_proceso(solicitudes)
    print(f" Pendientes ({len(pendientes)}):")
    mostrar_lista(pendientes, "Ninguna.", describir_solicitud)
    print(f" En proceso ({len(en_proceso)}):")
    mostrar_lista(en_proceso, "Ninguna.", describir_solicitud)


# ---------- RF04: categorías ----------

def mostrar_categorias(solicitudes: tuple) -> None:
    titulo("RF04 · Distribución por categorías")
    print("\n".join(map(lambda par: f"  {par[0]}: {par[1]}", contar_por_categoria(solicitudes))))
    match categoria_mayor_demanda(solicitudes):
        case (categoria, cantidad):
            print(f" Mayor demanda: {categoria} ({texto_solicitudes(cantidad)})")
        case None:
            print(f" Mayor demanda: {TEXTO_SIN_DATOS}")


# ---------- RF05: análisis económico ----------

def mostrar_analisis_economico(solicitudes: tuple) -> None:
    titulo("RF05 · Análisis económico")
    mostrar_lista(solicitudes, TEXTO_SIN_SOLICITUDES,
                  lambda solicitud: f"#{solicitud.id} {solicitud.cliente}: "
                                    f"{formato_dinero(solicitud.valor_economico)}")
    print(f" Valor total:    {formato_dinero(valor_total(solicitudes))}")
    print(f" Valor promedio: {texto_opcional(valor_promedio(solicitudes), formato_dinero)}")


# ---------- RF06: mayor costo ----------

def mostrar_mas_costosas(solicitudes: tuple) -> None:
    titulo("RF06 · Tres solicitudes de mayor costo")
    mostrar_lista(numerar(tres_mas_costosas(solicitudes)), TEXTO_SIN_SOLICITUDES,
                  lambda par: f"{par[0]}. {describir_solicitud(par[1])}")


# ---------- RF07: técnicos ----------

def describir_tecnico(resumen: ResumenTecnico) -> str:
    return (f"{resumen.tecnico}: {texto_solicitudes(resumen.cantidad_solicitudes)} | "
            f"{formato_horas(resumen.horas_acumuladas)} | finalizadas: {resumen.servicios_finalizados}")


def mostrar_tecnicos(solicitudes: tuple) -> None:
    titulo("RF07 · Desempeño de los técnicos")
    mostrar_lista(resumen_por_tecnico(solicitudes), "No hay técnicos registrados.", describir_tecnico)
    mayor_carga = texto_opcional(tecnico_mayor_carga(solicitudes), describir_tecnico)
    print(f" Mayor carga de trabajo: {mayor_carga}")


# ---------- RF08: clientes frecuentes ----------

def mostrar_clientes_frecuentes(solicitudes: tuple) -> None:
    titulo("RF08 · Clientes frecuentes (más de una solicitud)")
    mostrar_lista(clientes_frecuentes(solicitudes), "Ningún cliente tiene más de una solicitud.",
                  lambda par: f"{par[0]}: {texto_solicitudes(par[1])}")


# ---------- RF09: ingresos por cliente ----------

def mostrar_clientes_mayor_valor(solicitudes: tuple) -> None:
    titulo("RF09 · Tres clientes de mayor valor económico")
    mostrar_lista(numerar(tres_clientes_mayor_valor(solicitudes)), "No hay clientes registrados.",
                  lambda par: f"{par[0]}. {par[1][0]}: {formato_dinero(par[1][1])}")


# ---------- RF10: críticas ----------

def mostrar_criticas(solicitudes: tuple) -> None:
    titulo("RF10 · Solicitudes críticas (Alta y sin finalizar)")
    mostrar_lista(solicitudes_criticas(solicitudes), "No hay solicitudes críticas.",
                  lambda solicitud: f"#{solicitud.id} | Cliente: {solicitud.cliente} | "
                                    f"Técnico: {solicitud.tecnico} | {solicitud.categoria} | "
                                    f"{formato_horas(solicitud.horas)} | {solicitud.estado}")


# ---------- RF11: tiempos elevados ----------

def mostrar_horas_elevadas(solicitudes: tuple) -> None:
    titulo("RF11 · Servicios con tiempo superior al promedio")
    print(f" Tiempo promedio de atención: {texto_opcional(promedio_horas(solicitudes), formato_horas)}")
    mostrar_lista(solicitudes_con_horas_elevadas(solicitudes), "Ninguna supera el promedio.",
                  describir_solicitud)


# ---------- RF12: prioridades ----------

def mostrar_prioridades(solicitudes: tuple) -> None:
    titulo("RF12 · Clasificación por prioridad")
    print("\n".join(map(lambda par: f"  {par[0]}: {par[1]}", contar_por_prioridad(solicitudes))))
    porcentaje = texto_opcional(porcentaje_prioridad_alta(solicitudes), formato_porcentaje)
    print(f" Porcentaje de prioridad Alta: {porcentaje}")


# ---------- RF14: resumen ejecutivo ----------
# Reutiliza las funciones del MÓDULO 4: aquí no se calcula nada nuevo.

def mostrar_resumen_ejecutivo(solicitudes: tuple) -> None:
    finalizadas, sin_finalizar = separar_por_finalizacion(solicitudes)
    categoria_mayor = texto_opcional(categoria_mayor_demanda(solicitudes),
                                     lambda par: f"{par[0]} ({texto_solicitudes(par[1])})")
    tecnico_mayor = texto_opcional(tecnico_mayor_carga(solicitudes),
                                   lambda resumen: f"{resumen.tecnico} ({formato_horas(resumen.horas_acumuladas)})")
    cliente_mayor = texto_opcional(cliente_mayor_valor(solicitudes),
                                   lambda par: f"{par[0]} ({formato_dinero(par[1])})")
    tiempo_promedio = texto_opcional(promedio_horas(solicitudes), formato_horas)

    titulo("RF14 · RESUMEN EJECUTIVO")
    print(f"  Total de solicitudes registradas:   {len(solicitudes)}")
    print(f"  Finalizadas / sin finalizar:        {len(finalizadas)} / {len(sin_finalizar)}")
    print(f"  Categoría con mayor demanda:        {categoria_mayor}")
    print(f"  Técnico con más horas registradas:  {tecnico_mayor}")
    print(f"  Valor económico total:              {formato_dinero(valor_total(solicitudes))}")
    print(f"  Tiempo promedio de atención:        {tiempo_promedio}")
    print(f"  Solicitudes críticas:               {len(solicitudes_criticas(solicitudes))}")
    print(f"  Cliente de mayor valor económico:   {cliente_mayor}")


# ---------- Todos los reportes en orden ----------

def mostrar_todos_los_reportes(solicitudes: tuple) -> None:
    mostrar_solicitudes(solicitudes)
    mostrar_sin_finalizar(solicitudes)
    mostrar_categorias(solicitudes)
    mostrar_analisis_economico(solicitudes)
    mostrar_mas_costosas(solicitudes)
    mostrar_tecnicos(solicitudes)
    mostrar_clientes_frecuentes(solicitudes)
    mostrar_clientes_mayor_valor(solicitudes)
    mostrar_criticas(solicitudes)
    mostrar_horas_elevadas(solicitudes)
    mostrar_prioridades(solicitudes)


# =====================================================================
# =====================================================================
#  MÓDULO 6 · MAIN — Punto de entrada
# ---------------------------------------------------------------------
#  Solo coordina el flujo:
#    1) registrar solicitudes (RF01, RF13)
#    2) mostrar los análisis (RF02 a RF12)
#    3) mostrar el resumen ejecutivo (RF14)
# =====================================================================
# =====================================================================

def main() -> None:
    print("=" * 70)
    print(" SOPORTE360 SAS — Registro y análisis de solicitudes de soporte")
    print("=" * 70)

    solicitudes = registrar()

    mostrar_todos_los_reportes(solicitudes)
    mostrar_resumen_ejecutivo(solicitudes)

    print("\nFin del reporte.")


if __name__ == "__main__":
    main()
