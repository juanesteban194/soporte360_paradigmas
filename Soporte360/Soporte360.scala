// =====================================================================
//  SOPORTE360 SAS — Registro y análisis de solicitudes de soporte
//  Práctica de Scala · Paradigmas de Programación
// =====================================================================
//
//  ÍNDICE DE MÓDULOS (cada uno es un object, separado por un bloque
//  de comentario grande como este):
//
//    MÓDULO 1 · MODELO      -> case class Solicitud y valores válidos
//    MÓDULO 2 · VALIDACIÓN  -> RF13 (verificar cada dato)
//    MÓDULO 3 · ENTRADA     -> RF01 (registro por teclado)
//    MÓDULO 4 · ANÁLISIS    -> RF03 a RF12 (cálculos)
//    MÓDULO 5 · REPORTES    -> RF02 a RF12 y RF14 (presentación)
//    MÓDULO 6 · MAIN        -> punto de entrada
//
//  REGLAS QUE CUMPLE TODO EL CÓDIGO:
//    - Sin for, while ni do-while: se usan map, filter, foldLeft,
//      groupBy, sortBy... y recursión para repetir preguntas.
//    - Sin var: todos los datos son val (inmutables).
//    - Sin try/catch ni throw: los errores son valores (Either, Option).
//    - Sin bibliotecas externas ni bases de datos.
// =====================================================================

import scala.io.StdIn
import scala.annotation.tailrec

// =====================================================================
// =====================================================================
//  MÓDULO 1 · MODELO — Datos del negocio
// ---------------------------------------------------------------------
//  Qué es una solicitud de soporte (RF01) y cuáles son los valores
//  permitidos para categoría, prioridad y estado.
//  Si cambia un texto (ej: "Finalizado"), se cambia solo aquí.
// =====================================================================
// =====================================================================

// Una solicitud de soporte técnico (RF01).
// case class: guarda datos inmutables y se compara por su contenido.
case class Solicitud(
  id: Int,
  cliente: String,
  tecnico: String,
  categoria: String,
  prioridad: String,
  estado: String,
  horas: Double,
  costoPorHora: Double
) {
  // RF05: valor económico de esta solicitud.
  def valorEconomico: Double = horas * costoPorHora

  def estaFinalizada: Boolean = estado == Modelo.estadoFinalizado
}

object Modelo {
  // Nombres de valores que se usan en los análisis.
  // Si cambia un texto, se cambia solo aquí.
  val prioridadAlta: String = "Alta"
  val estadoPendiente: String = "Pendiente"
  val estadoEnProceso: String = "En proceso"
  val estadoFinalizado: String = "Finalizado"

  // Valores permitidos (RF13 valida contra estas listas).
  val categoriasValidas: List[String] = List("Hardware", "Software", "Redes")
  val prioridadesValidas: List[String] = List(prioridadAlta, "Media", "Baja")
  val estadosValidos: List[String] = List(estadoPendiente, estadoEnProceso, estadoFinalizado)
}

// =====================================================================
// =====================================================================
//  MÓDULO 2 · VALIDACIÓN — RF13: verificación de información
// ---------------------------------------------------------------------
//  Cada función revisa UN dato y devuelve:
//    Right(dato limpio) -> el dato es válido
//    Left(mensaje)      -> el dato es inválido y se explica por qué
//  Funciones puras: no leen teclado ni imprimen.
// =====================================================================
// =====================================================================

object Validacion {

  // Cliente y técnico: el texto no puede quedar vacío.
  def validarTextoNoVacio(texto: String, campo: String): Either[String, String] = {
    val textoLimpio = texto.trim
    if (textoLimpio.isEmpty) Left(s"El campo $campo no puede estar vacío.")
    else Right(textoLimpio)
  }

  // Categoría, prioridad y estado: deben estar en la lista permitida.
  // Acepta mayúsculas o minúsculas y devuelve el valor oficial ("redes" -> "Redes").
  def validarOpcion(texto: String, opcionesValidas: List[String], campo: String): Either[String, String] =
    opcionesValidas.find(opcion => opcion.equalsIgnoreCase(texto.trim)) match {
      case Some(opcionOficial) => Right(opcionOficial)
      case None => Left(s"Valor no válido para $campo. Opciones: ${opcionesValidas.mkString(", ")}.")
    }

  // Horas y costo por hora: número no negativo.
  def validarNumeroNoNegativo(texto: String, campo: String): Either[String, Double] =
    texto.trim.toDoubleOption match {
      case None => Left(s"El valor de $campo debe ser un número (use punto para decimales, ej: 1.5).")
      case Some(numero) if numero.isNaN || numero.isInfinite => Left(s"El valor de $campo debe ser un número válido.")
      case Some(numero) if numero < 0 => Left(s"El valor de $campo no puede ser negativo.")
      case Some(numero) => Right(numero)
    }

  // Cantidad N de solicitudes a registrar: entero, puede ser 0.
  def validarCantidad(texto: String): Either[String, Int] =
    texto.trim.toIntOption match {
      case None => Left("La cantidad debe ser un número entero.")
      case Some(cantidad) if cantidad < 0 => Left("La cantidad no puede ser negativa.")
      case Some(cantidad) => Right(cantidad)
    }

  // Identificador: primero debe ser entero positivo y luego no estar repetido.
  // flatMap encadena las dos validaciones: si la primera falla, la segunda no se ejecuta.
  def validarId(texto: String, idsRegistrados: List[Int]): Either[String, Int] =
    validarEnteroPositivo(texto, "identificador")
      .flatMap(id => validarIdNoRepetido(id, idsRegistrados))

  def validarEnteroPositivo(texto: String, campo: String): Either[String, Int] =
    texto.trim.toIntOption match {
      case None => Left(s"El $campo debe ser un número entero.")
      case Some(numero) if numero <= 0 => Left(s"El $campo debe ser mayor que cero.")
      case Some(numero) => Right(numero)
    }

  def validarIdNoRepetido(id: Int, idsRegistrados: List[Int]): Either[String, Int] =
    if (idsRegistrados.exists(idRegistrado => idRegistrado == id)) Left(s"El identificador $id ya está registrado.")
    else Right(id)
}

// =====================================================================
// =====================================================================
//  MÓDULO 3 · ENTRADA — RF01: registro de solicitudes por teclado
// ---------------------------------------------------------------------
//  Lee cada dato, lo revisa con el MÓDULO 2 y, si es inválido,
//  informa el error y lo vuelve a pedir (RF13).
//  Las N solicitudes se registran con foldLeft (sin ciclos).
// =====================================================================
// =====================================================================

object Entrada {

  // Pide un dato hasta que sea válido.
  // - mensaje: lo que se muestra al usuario.
  // - validar: la función de Validacion que revisa el texto escrito.
  // [A] es el tipo del dato que se obtiene: Int, Double o String.
  // Sin ciclos: si el dato es inválido, la función se llama a sí misma (recursión).
  // @tailrec garantiza que esa recursión no llene la memoria.
  @tailrec
  def leerDatoValido[A](mensaje: String, validar: String => Either[String, A]): A = {
    print(mensaje)
    val textoEscrito = Option(StdIn.readLine()).getOrElse("")
    validar(textoEscrito) match {
      case Right(datoValido) => datoValido
      case Left(error) =>
        println(s"  Error: $error Intente de nuevo.")
        leerDatoValido(mensaje, validar)
    }
  }

  def leerCantidad(): Int =
    leerDatoValido("¿Cuántas solicitudes desea registrar? ", Validacion.validarCantidad)

  // Lee una solicitud completa. Recibe los ids ya usados para evitar repetidos.
  def leerSolicitud(numero: Int, idsRegistrados: List[Int]): Solicitud = {
    println(s"\n--- Solicitud $numero ---")
    val id = leerDatoValido("Identificador: ",
      texto => Validacion.validarId(texto, idsRegistrados))
    val cliente = leerDatoValido("Cliente: ",
      texto => Validacion.validarTextoNoVacio(texto, "cliente"))
    val tecnico = leerDatoValido("Técnico responsable: ",
      texto => Validacion.validarTextoNoVacio(texto, "técnico"))
    val categoria = leerDatoValido(s"Categoría (${Modelo.categoriasValidas.mkString("/")}): ",
      texto => Validacion.validarOpcion(texto, Modelo.categoriasValidas, "categoría"))
    val prioridad = leerDatoValido(s"Prioridad (${Modelo.prioridadesValidas.mkString("/")}): ",
      texto => Validacion.validarOpcion(texto, Modelo.prioridadesValidas, "prioridad"))
    val estado = leerDatoValido(s"Estado (${Modelo.estadosValidos.mkString("/")}): ",
      texto => Validacion.validarOpcion(texto, Modelo.estadosValidos, "estado"))
    val horas = leerDatoValido("Horas dedicadas: ",
      texto => Validacion.validarNumeroNoNegativo(texto, "horas"))
    val costoPorHora = leerDatoValido("Costo por hora: ",
      texto => Validacion.validarNumeroNoNegativo(texto, "costo por hora"))

    Solicitud(id, cliente, tecnico, categoria, prioridad, estado, horas, costoPorHora)
  }

  // Registra N solicitudes sin ciclos: foldLeft recorre 1..N y acumula la lista.
  // En cada vuelta recibe las solicitudes ya registradas, así se validan los ids repetidos.
  def registrarSolicitudes(cantidad: Int): List[Solicitud] =
    (1 to cantidad).foldLeft(List.empty[Solicitud]) { (registradas, numero) =>
      val idsRegistrados = registradas.map(solicitud => solicitud.id)
      registradas :+ leerSolicitud(numero, idsRegistrados)
    }

  // Punto de entrada del registro: pide N y luego las N solicitudes.
  def registrar(): List[Solicitud] =
    registrarSolicitudes(leerCantidad())
}

// =====================================================================
// =====================================================================
//  MÓDULO 4 · ANÁLISIS — RF03 a RF12 (y datos para RF14)
// ---------------------------------------------------------------------
//  Funciones puras: reciben la lista de solicitudes y devuelven un
//  resultado. Nunca leen teclado ni imprimen.
//  Criterio de empates (siempre el mismo):
//    1) por el valor del reporte, de mayor a menor
//    2) si empatan, por nombre (A-Z) o por id (menor a mayor)
// =====================================================================
// =====================================================================

object Analisis {

  // Resultado del RF07: resumen de un técnico.
  case class ResumenTecnico(
    tecnico: String,
    cantidadSolicitudes: Int,
    horasAcumuladas: Double,
    serviciosFinalizados: Int
  )

  // ---------- Funciones de apoyo (reutilizadas) ----------

  // Promedio de una lista. Si está vacía devuelve None (no hay promedio).
  def promedio(valores: List[Double]): Option[Double] =
    valores.reduceOption((a, b) => a + b).map(suma => suma / valores.size)

  // Cuenta cuántas solicitudes tienen cada valor permitido de un campo.
  // campo: función que saca el dato a contar (ej: la categoría de la solicitud).
  // Incluye los valores con 0 solicitudes.
  def contarSegun(solicitudes: List[Solicitud], valoresPermitidos: List[String],
                  campo: Solicitud => String): List[(String, Int)] =
    valoresPermitidos.map(valor => (valor, solicitudes.filter(solicitud => campo(solicitud) == valor).size))

  // ---------- RF03: solicitudes sin finalizar ----------

  def solicitudesPendientes(solicitudes: List[Solicitud]): List[Solicitud] =
    solicitudes.filter(solicitud => solicitud.estado == Modelo.estadoPendiente)

  def solicitudesEnProceso(solicitudes: List[Solicitud]): List[Solicitud] =
    solicitudes.filter(solicitud => solicitud.estado == Modelo.estadoEnProceso)

  // Separa en (finalizadas, sin finalizar). Se usa en el RF14.
  def separarPorFinalizacion(solicitudes: List[Solicitud]): (List[Solicitud], List[Solicitud]) =
    solicitudes.partition(solicitud => solicitud.estaFinalizada)

  // ---------- RF04: distribución por categorías ----------

  def contarPorCategoria(solicitudes: List[Solicitud]): List[(String, Int)] =
    contarSegun(solicitudes, Modelo.categoriasValidas, solicitud => solicitud.categoria)

  // None si no hay solicitudes registradas.
  def categoriaMayorDemanda(solicitudes: List[Solicitud]): Option[(String, Int)] =
    contarPorCategoria(solicitudes)
      .filter { case (_, cantidad) => cantidad > 0 }
      .sortBy { case (categoria, cantidad) => (-cantidad, categoria) }
      .headOption

  // ---------- RF05: análisis económico ----------

  def valorTotal(solicitudes: List[Solicitud]): Double =
    solicitudes.map(solicitud => solicitud.valorEconomico).sum

  def valorPromedio(solicitudes: List[Solicitud]): Option[Double] =
    promedio(solicitudes.map(solicitud => solicitud.valorEconomico))

  // ---------- RF06: tres solicitudes de mayor costo ----------

  def tresMasCostosas(solicitudes: List[Solicitud]): List[Solicitud] =
    solicitudes
      .sortBy(solicitud => (-solicitud.valorEconomico, solicitud.id))
      .take(3)

  // ---------- RF07: desempeño de los técnicos ----------

  // Un resumen por técnico, ordenado por nombre.
  def resumenPorTecnico(solicitudes: List[Solicitud]): List[ResumenTecnico] =
    solicitudes
      .groupBy(solicitud => solicitud.tecnico)
      .map { case (tecnico, delTecnico) =>
        ResumenTecnico(
          tecnico,
          delTecnico.size,
          delTecnico.map(solicitud => solicitud.horas).sum,
          delTecnico.filter(solicitud => solicitud.estaFinalizada).size
        )
      }
      .toList
      .sortBy(resumen => resumen.tecnico)

  // Mayor carga = más horas registradas. None si no hay técnicos.
  def tecnicoMayorCarga(solicitudes: List[Solicitud]): Option[ResumenTecnico] =
    resumenPorTecnico(solicitudes)
      .sortBy(resumen => (-resumen.horasAcumuladas, resumen.tecnico))
      .headOption

  // ---------- RF08: clientes frecuentes ----------

  // (cliente, cantidad de solicitudes) solo para quienes tienen más de una.
  def clientesFrecuentes(solicitudes: List[Solicitud]): List[(String, Int)] =
    solicitudes
      .groupBy(solicitud => solicitud.cliente)
      .map { case (cliente, delCliente) => (cliente, delCliente.size) }
      .toList
      .filter { case (_, cantidad) => cantidad > 1 }
      .sortBy { case (cliente, cantidad) => (-cantidad, cliente) }

  // ---------- RF09: ingresos por cliente ----------

  // (cliente, valor total de sus solicitudes), de mayor a menor valor.
  def valorPorCliente(solicitudes: List[Solicitud]): List[(String, Double)] =
    solicitudes
      .groupBy(solicitud => solicitud.cliente)
      .map { case (cliente, delCliente) => (cliente, valorTotal(delCliente)) }
      .toList
      .sortBy { case (cliente, valor) => (-valor, cliente) }

  def tresClientesMayorValor(solicitudes: List[Solicitud]): List[(String, Double)] =
    valorPorCliente(solicitudes).take(3)

  // Para el RF14. None si no hay solicitudes.
  def clienteMayorValor(solicitudes: List[Solicitud]): Option[(String, Double)] =
    valorPorCliente(solicitudes).headOption

  // ---------- RF10: solicitudes críticas ----------

  // Prioridad Alta y todavía Pendiente o En proceso (es decir, no finalizada).
  def solicitudesCriticas(solicitudes: List[Solicitud]): List[Solicitud] =
    solicitudes
      .filter(solicitud => solicitud.prioridad == Modelo.prioridadAlta && !solicitud.estaFinalizada)
      .sortBy(solicitud => solicitud.id)

  // ---------- RF11: tiempos elevados ----------

  def promedioHoras(solicitudes: List[Solicitud]): Option[Double] =
    promedio(solicitudes.map(solicitud => solicitud.horas))

  // Solicitudes con horas por encima del promedio, de mayor a menor.
  def solicitudesConHorasElevadas(solicitudes: List[Solicitud]): List[Solicitud] =
    promedioHoras(solicitudes) match {
      case Some(promedioDeHoras) =>
        solicitudes
          .filter(solicitud => solicitud.horas > promedioDeHoras)
          .sortBy(solicitud => (-solicitud.horas, solicitud.id))
      case None => List.empty
    }

  // ---------- RF12: clasificación por prioridad ----------

  def contarPorPrioridad(solicitudes: List[Solicitud]): List[(String, Int)] =
    contarSegun(solicitudes, Modelo.prioridadesValidas, solicitud => solicitud.prioridad)

  // Porcentaje de solicitudes con prioridad Alta. None si no hay solicitudes.
  def porcentajePrioridadAlta(solicitudes: List[Solicitud]): Option[Double] =
    if (solicitudes.isEmpty) None
    else Some(solicitudes.filter(solicitud => solicitud.prioridad == Modelo.prioridadAlta).size * 100.0 / solicitudes.size)
}

// =====================================================================
// =====================================================================
//  MÓDULO 5 · REPORTES — RF02 a RF12 y RF14: presentación
// ---------------------------------------------------------------------
//  Muestra en pantalla los resultados del MÓDULO 4. Aquí no se calcula.
//    Condición 6: listas vacías -> mensaje claro.
//    Condición 7: dinero con dos decimales.
// =====================================================================
// =====================================================================

object Reportes {

  // Textos que se repiten en varios reportes: se escriben una sola vez.
  val textoSinDatos: String = "Sin datos"
  val textoSinSolicitudes: String = "No hay solicitudes registradas."

  // ---------- Formatos ----------

  def formatoDinero(valor: Double): String = f"$$$valor%,.2f"

  def formatoHoras(horas: Double): String = f"$horas%.2f h"

  def formatoPorcentaje(porcentaje: Double): String = f"$porcentaje%.2f%%"

  // Singular o plural según la cantidad: "1 solicitud", "3 solicitudes".
  def textoSolicitudes(cantidad: Int): String =
    if (cantidad == 1) "1 solicitud" else s"$cantidad solicitudes"

  // Option -> texto: si hay valor lo formatea, si no hay muestra "Sin datos".
  def textoOpcional[A](valor: Option[A], formatear: A => String): String =
    valor.map(formatear).getOrElse(textoSinDatos)

  // ---------- Ayudas de presentación ----------

  def titulo(texto: String): Unit = {
    println()
    println("=" * 70)
    println(s" $texto")
    println("=" * 70)
  }

  // Muestra cada elemento en una línea, o un mensaje si la lista está vacía.
  def mostrarLista[A](elementos: List[A], mensajeSiVacia: String, formatear: A => String): Unit =
    if (elementos.isEmpty) println(s"  $mensajeSiVacia")
    else elementos.foreach(elemento => println(s"  ${formatear(elemento)}"))

  // Agrega la posición a cada elemento: List(a, b) -> List((1, a), (2, b)).
  def numerar[A](elementos: List[A]): List[(Int, A)] =
    (1 to elementos.size).toList.zip(elementos)

  def describirSolicitud(solicitud: Solicitud): String =
    s"#${solicitud.id} | ${solicitud.cliente} | Técnico: ${solicitud.tecnico} | " +
      s"${solicitud.categoria} | ${solicitud.prioridad} | ${solicitud.estado} | " +
      s"${formatoHoras(solicitud.horas)} x ${formatoDinero(solicitud.costoPorHora)} = " +
      s"${formatoDinero(solicitud.valorEconomico)}"

  // ---------- RF02: consulta general ----------

  def mostrarSolicitudes(solicitudes: List[Solicitud]): Unit = {
    titulo(s"RF02 · Solicitudes registradas (${solicitudes.size})")
    mostrarLista(solicitudes, textoSinSolicitudes, describirSolicitud)
  }

  // ---------- RF03: sin finalizar ----------

  def mostrarSinFinalizar(solicitudes: List[Solicitud]): Unit = {
    titulo("RF03 · Solicitudes sin finalizar")
    val pendientes = Analisis.solicitudesPendientes(solicitudes)
    val enProceso = Analisis.solicitudesEnProceso(solicitudes)
    println(s" Pendientes (${pendientes.size}):")
    mostrarLista(pendientes, "Ninguna.", describirSolicitud)
    println(s" En proceso (${enProceso.size}):")
    mostrarLista(enProceso, "Ninguna.", describirSolicitud)
  }

  // ---------- RF04: categorías ----------

  def mostrarCategorias(solicitudes: List[Solicitud]): Unit = {
    titulo("RF04 · Distribución por categorías")
    Analisis.contarPorCategoria(solicitudes).foreach { case (categoria, cantidad) =>
      println(s"  $categoria: $cantidad")
    }
    Analisis.categoriaMayorDemanda(solicitudes) match {
      case Some((categoria, cantidad)) => println(s" Mayor demanda: $categoria (${textoSolicitudes(cantidad)})")
      case None => println(s" Mayor demanda: $textoSinDatos")
    }
  }

  // ---------- RF05: análisis económico ----------

  def mostrarAnalisisEconomico(solicitudes: List[Solicitud]): Unit = {
    titulo("RF05 · Análisis económico")
    mostrarLista(solicitudes, textoSinSolicitudes,
      solicitud => s"#${solicitud.id} ${solicitud.cliente}: ${formatoDinero(solicitud.valorEconomico)}")
    println(s" Valor total:    ${formatoDinero(Analisis.valorTotal(solicitudes))}")
    println(s" Valor promedio: ${textoOpcional(Analisis.valorPromedio(solicitudes), formatoDinero)}")
  }

  // ---------- RF06: mayor costo ----------

  def mostrarMasCostosas(solicitudes: List[Solicitud]): Unit = {
    titulo("RF06 · Tres solicitudes de mayor costo")
    mostrarLista(numerar(Analisis.tresMasCostosas(solicitudes)), textoSinSolicitudes,
      { case (posicion, solicitud) => s"$posicion. ${describirSolicitud(solicitud)}" })
  }

  // ---------- RF07: técnicos ----------

  def describirTecnico(resumen: Analisis.ResumenTecnico): String =
    s"${resumen.tecnico}: ${textoSolicitudes(resumen.cantidadSolicitudes)} | " +
      s"${formatoHoras(resumen.horasAcumuladas)} | finalizadas: ${resumen.serviciosFinalizados}"

  def mostrarTecnicos(solicitudes: List[Solicitud]): Unit = {
    titulo("RF07 · Desempeño de los técnicos")
    mostrarLista(Analisis.resumenPorTecnico(solicitudes), "No hay técnicos registrados.", describirTecnico)
    val mayorCarga = textoOpcional(Analisis.tecnicoMayorCarga(solicitudes), describirTecnico)
    println(s" Mayor carga de trabajo: $mayorCarga")
  }

  // ---------- RF08: clientes frecuentes ----------

  def mostrarClientesFrecuentes(solicitudes: List[Solicitud]): Unit = {
    titulo("RF08 · Clientes frecuentes (más de una solicitud)")
    mostrarLista(Analisis.clientesFrecuentes(solicitudes), "Ningún cliente tiene más de una solicitud.",
      { case (cliente, cantidad) => s"$cliente: ${textoSolicitudes(cantidad)}" })
  }

  // ---------- RF09: ingresos por cliente ----------

  def mostrarClientesMayorValor(solicitudes: List[Solicitud]): Unit = {
    titulo("RF09 · Tres clientes de mayor valor económico")
    mostrarLista(numerar(Analisis.tresClientesMayorValor(solicitudes)), "No hay clientes registrados.",
      { case (posicion, (cliente, valor)) => s"$posicion. $cliente: ${formatoDinero(valor)}" })
  }

  // ---------- RF10: críticas ----------

  def mostrarCriticas(solicitudes: List[Solicitud]): Unit = {
    titulo("RF10 · Solicitudes críticas (Alta y sin finalizar)")
    mostrarLista(Analisis.solicitudesCriticas(solicitudes), "No hay solicitudes críticas.",
      solicitud => s"#${solicitud.id} | Cliente: ${solicitud.cliente} | Técnico: ${solicitud.tecnico} | " +
        s"${solicitud.categoria} | ${formatoHoras(solicitud.horas)} | ${solicitud.estado}")
  }

  // ---------- RF11: tiempos elevados ----------

  def mostrarHorasElevadas(solicitudes: List[Solicitud]): Unit = {
    titulo("RF11 · Servicios con tiempo superior al promedio")
    println(s" Tiempo promedio de atención: ${textoOpcional(Analisis.promedioHoras(solicitudes), formatoHoras)}")
    mostrarLista(Analisis.solicitudesConHorasElevadas(solicitudes), "Ninguna supera el promedio.",
      describirSolicitud)
  }

  // ---------- RF12: prioridades ----------

  def mostrarPrioridades(solicitudes: List[Solicitud]): Unit = {
    titulo("RF12 · Clasificación por prioridad")
    Analisis.contarPorPrioridad(solicitudes).foreach { case (prioridad, cantidad) =>
      println(s"  $prioridad: $cantidad")
    }
    println(s" Porcentaje de prioridad Alta: ${textoOpcional(Analisis.porcentajePrioridadAlta(solicitudes), formatoPorcentaje)}")
  }

  // ---------- RF14: resumen ejecutivo ----------
  // Reutiliza las funciones de Analisis: aquí no se calcula nada nuevo.

  def mostrarResumenEjecutivo(solicitudes: List[Solicitud]): Unit = {
    val (finalizadas, sinFinalizar) = Analisis.separarPorFinalizacion(solicitudes)

    val categoriaMayor = Analisis.categoriaMayorDemanda(solicitudes) match {
      case Some((categoria, cantidad)) => s"$categoria (${textoSolicitudes(cantidad)})"
      case None => textoSinDatos
    }
    val tecnicoMayor = Analisis.tecnicoMayorCarga(solicitudes) match {
      case Some(resumen) => s"${resumen.tecnico} (${formatoHoras(resumen.horasAcumuladas)})"
      case None => textoSinDatos
    }
    val clienteMayor = Analisis.clienteMayorValor(solicitudes) match {
      case Some((cliente, valor)) => s"$cliente (${formatoDinero(valor)})"
      case None => textoSinDatos
    }
    val tiempoPromedio = textoOpcional(Analisis.promedioHoras(solicitudes), formatoHoras)

    titulo("RF14 · RESUMEN EJECUTIVO")
    println(s"  Total de solicitudes registradas:   ${solicitudes.size}")
    println(s"  Finalizadas / sin finalizar:        ${finalizadas.size} / ${sinFinalizar.size}")
    println(s"  Categoría con mayor demanda:        $categoriaMayor")
    println(s"  Técnico con más horas registradas:  $tecnicoMayor")
    println(s"  Valor económico total:              ${formatoDinero(Analisis.valorTotal(solicitudes))}")
    println(s"  Tiempo promedio de atención:        $tiempoPromedio")
    println(s"  Solicitudes críticas:               ${Analisis.solicitudesCriticas(solicitudes).size}")
    println(s"  Cliente de mayor valor económico:   $clienteMayor")
  }

  // ---------- Todos los reportes en orden ----------

  def mostrarTodosLosReportes(solicitudes: List[Solicitud]): Unit = {
    mostrarSolicitudes(solicitudes)
    mostrarSinFinalizar(solicitudes)
    mostrarCategorias(solicitudes)
    mostrarAnalisisEconomico(solicitudes)
    mostrarMasCostosas(solicitudes)
    mostrarTecnicos(solicitudes)
    mostrarClientesFrecuentes(solicitudes)
    mostrarClientesMayorValor(solicitudes)
    mostrarCriticas(solicitudes)
    mostrarHorasElevadas(solicitudes)
    mostrarPrioridades(solicitudes)
  }
}

// =====================================================================
// =====================================================================
//  MÓDULO 6 · MAIN — Punto de entrada
// ---------------------------------------------------------------------
//  Solo coordina el flujo:
//    1) Entrada  -> registrar solicitudes (RF01, RF13)
//    2) Reportes -> mostrar los análisis (RF02 a RF12)
//    3) Reportes -> mostrar el resumen ejecutivo (RF14)
// =====================================================================
// =====================================================================

object Main {
  def main(args: Array[String]): Unit = {
    println("=" * 70)
    println(" SOPORTE360 SAS — Registro y análisis de solicitudes de soporte")
    println("=" * 70)

    val solicitudes = Entrada.registrar()

    Reportes.mostrarTodosLosReportes(solicitudes)
    Reportes.mostrarResumenEjecutivo(solicitudes)

    println("\nFin del reporte.")
  }
}
