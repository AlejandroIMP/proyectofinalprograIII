/* Generador del Manual Técnico — Floristería UMG (Proyecto 2, Programación III) */
const fs = require("fs");
const path = require("path");
const docx = require("C:/Users/aleja/AppData/Roaming/npm/node_modules/docx");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  Header, Footer, AlignmentType, LevelFormat, TabStopType, TabStopPosition,
  TableOfContents, HeadingLevel, BorderStyle, WidthType, ShadingType,
  VerticalAlign, PageNumber, PageBreak
} = docx;

/* ---------- Paleta ---------- */
const VERDE   = "1B4332";
const VERDE2  = "2D6A4F";
const GRIS_BG = "F2F2F2";
const GRIS_HDR= "D5E8E0";
const AZUL_BG = "EAF2FB";

const CONTENT_W = 9360;

/* ---------- Helpers de texto ---------- */
function h1(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun(text)] });
}
function h2(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun(text)] });
}
function h3(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun(text)] });
}
function p(text, opts = {}) {
  return new Paragraph({
    spacing: { after: 120, line: 276 },
    children: [new TextRun({ text, ...opts })]
  });
}
function pRuns(runs) {
  return new Paragraph({ spacing: { after: 120, line: 276 }, children: runs });
}
function bullet(text, level = 0) {
  return new Paragraph({
    numbering: { reference: "bullets", level },
    spacing: { after: 60 },
    children: typeof text === "string" ? [new TextRun(text)] : text
  });
}
function numbered(text) {
  return new Paragraph({
    numbering: { reference: "nums", level: 0 },
    spacing: { after: 60 },
    children: typeof text === "string" ? [new TextRun(text)] : text
  });
}
/* Bloque de código: párrafos monoespaciados con fondo gris */
function code(lines) {
  return lines.map((ln, i) =>
    new Paragraph({
      shading: { type: ShadingType.CLEAR, fill: GRIS_BG },
      spacing: { after: 0, line: 240 },
      indent: { left: 120, right: 120 },
      children: [new TextRun({ text: ln === "" ? " " : ln, font: "Consolas", size: 17 })]
    })
  );
}
function codeBlock(lines) {
  // envuelve con un poco de aire arriba y abajo
  const out = [new Paragraph({ spacing: { after: 0 }, children: [new TextRun({ text: " ", size: 8 })] })];
  out.push(...code(lines));
  out.push(new Paragraph({ spacing: { after: 120 }, children: [new TextRun({ text: " ", size: 8 })] }));
  return out;
}

/* ---------- Helper de tabla ---------- */
const thin = { style: BorderStyle.SINGLE, size: 1, color: "BBBBBB" };
const cellBorders = { top: thin, bottom: thin, left: thin, right: thin };

function tcell(content, { width, header = false, fill, bold = false, mono = false } = {}) {
  const runs = (Array.isArray(content) ? content : [content]).map(t =>
    new TextRun({ text: String(t), bold: header || bold, color: header ? "FFFFFF" : "000000",
                  font: mono ? "Consolas" : "Arial", size: header ? 18 : 18 })
  );
  return new TableCell({
    borders: cellBorders,
    width: { size: width, type: WidthType.DXA },
    shading: { type: ShadingType.CLEAR, fill: header ? VERDE2 : (fill || "FFFFFF") },
    margins: { top: 60, bottom: 60, left: 100, right: 100 },
    verticalAlign: VerticalAlign.CENTER,
    children: [new Paragraph({ children: runs })]
  });
}
function table(headers, rows, widths) {
  const headerRow = new TableRow({
    tableHeader: true,
    children: headers.map((hd, i) => tcell(hd, { width: widths[i], header: true }))
  });
  const dataRows = rows.map((r, ri) =>
    new TableRow({
      children: r.map((c, i) => {
        const mono = typeof c === "object" && c.mono;
        const val  = typeof c === "object" ? c.t : c;
        return tcell(val, { width: widths[i], fill: ri % 2 ? GRIS_BG : "FFFFFF", mono });
      })
    })
  );
  return new Table({
    width: { size: CONTENT_W, type: WidthType.DXA },
    columnWidths: widths,
    rows: [headerRow, ...dataRows]
  });
}

function spacer(after = 120) {
  return new Paragraph({ spacing: { after }, children: [new TextRun("")] });
}

/* =====================================================================
 *  PORTADA
 * ===================================================================== */
const portada = [
  new Paragraph({ spacing: { before: 1200, after: 0 }, alignment: AlignmentType.CENTER,
    children: [new TextRun({ text: "Universidad Mariano Gálvez de Guatemala", bold: true, size: 28, color: VERDE })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 600 },
    children: [new TextRun({ text: "Facultad de Ingeniería en Sistemas — Programación III, Sección A", size: 22, color: "555555" })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 800, after: 0 },
    children: [new TextRun({ text: "MANUAL TÉCNICO", bold: true, size: 56, color: VERDE2 })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 0 },
    children: [new TextRun({ text: "Sistema de Gestión Comercial de Floristería", bold: true, size: 32, color: "000000" })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 120, after: 800 },
    children: [new TextRun({ text: "Proyecto 2 — Tabla Hash, Pipeline JDBC Oracle, API REST de Grafos y Visualización Web", italics: true, size: 22, color: "555555" })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 1600, after: 0 },
    children: [new TextRun({ text: "Tecnologías:", bold: true, size: 22 })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 0 },
    children: [new TextRun({ text: "Java 25 · Oracle Database · JavaFX 25 · HTTP Server nativo · Vis.js · Apache POI · Maven", size: 20, color: "333333" })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 1400, after: 0 },
    children: [new TextRun({ text: "Versión 1.1 — Mayo 2026", size: 20, color: "777777" })] }),
  new Paragraph({ children: [new PageBreak()] })
];

/* =====================================================================
 *  TABLA DE CONTENIDO
 * ===================================================================== */
const toc = [
  h1("Tabla de Contenido"),
  new TableOfContents("Tabla de Contenido", { hyperlink: true, headingStyleRange: "1-3" }),
  new Paragraph({ children: [new PageBreak()] })
];

/* =====================================================================
 *  1. INTRODUCCIÓN
 * ===================================================================== */
const sec1 = [
  h1("1. Introducción"),
  h2("1.1 Propósito del manual"),
  p("Este manual técnico documenta de forma completa el Sistema de Gestión Comercial de Floristería desarrollado como Proyecto 2 del curso Programación III. Está dirigido a desarrolladores, evaluadores y al personal técnico responsable de instalar, ejecutar, mantener y extender el sistema. Describe la arquitectura, la estructura del código, el modelo de datos en Oracle, los componentes principales, los procedimientos de instalación y ejecución, la referencia de la API REST, las pruebas automatizadas y la solución de problemas frecuentes."),
  h2("1.2 Alcance del sistema"),
  p("El sistema implementa los cuatro grandes bloques exigidos por la rúbrica del proyecto:"),
  bullet("Estructura de datos propia: una tabla hash genérica implementada desde cero (sin java.util.HashMap), con encadenamiento separado, rehash automático y métricas de rendimiento."),
  bullet("Persistencia en Oracle Database: esquema relacional con seis tablas, particionamiento por rango de año, sequences, restricciones de integridad, datos semilla y un procedimiento PL/SQL de verificación."),
  bullet("Pipeline de carga JDBC: lectura de los catálogos desde Oracle hacia la tabla hash, midiendo tiempo de carga y colisiones, con fuente sintética como alternativa de respaldo."),
  bullet("Grafos comerciales y visualización: un grafo dirigido que modela las relaciones de negocio, expuesto mediante una API REST nativa y visualizado en un frontend web con Vis.js; complementado con una interfaz de escritorio JavaFX."),
  h2("1.3 Audiencia"),
  p("Se asume que el lector posee conocimientos intermedios de Java, SQL, conceptos de bases de datos relacionales y nociones de estructuras de datos (tablas hash y grafos). No se requiere experiencia previa con el código específico del proyecto."),
];

/* =====================================================================
 *  2. DESCRIPCIÓN GENERAL
 * ===================================================================== */
const sec2 = [
  h1("2. Descripción General del Sistema"),
  h2("2.1 Objetivo"),
  p("Gestionar el catálogo comercial de una floristería (productos florales, proveedores/marcas, clientes y facturación) utilizando una tabla hash personalizada como índice principal en memoria, alimentada desde Oracle Database, y ofrecer consultas analíticas de trazabilidad comercial mediante grafos dirigidos accesibles por API REST y visualizados en la web."),
  h2("2.2 Características principales"),
  bullet("Tabla hash genérica con búsquedas en tiempo promedio O(1), conteo estricto de colisiones y rehash automático al 75 % de factor de carga."),
  bullet("Doble fuente de datos intercambiable en tiempo de ejecución: Oracle real o datos sintéticos, seleccionable por variable de entorno, perfil Maven, flag de línea de comandos o ComboBox en la GUI."),
  bullet("Cinco consultas de grafo dirigido sobre las relaciones Cliente → Factura → Detalle → Ítem → Proveedor."),
  bullet("API REST nativa (sin frameworks externos) servida en el puerto 8085, con soporte CORS."),
  bullet("Frontend HTML5 con Vis.js para visualización interactiva de grafos y de la tabla hash."),
  bullet("Interfaz de escritorio JavaFX con heatmap de slots, animaciones de búsqueda y reportes con gráficas."),
  bullet("Reportes 4.1 y 4.2 exportables a CSV (RFC 4180) y JSON sin dependencias externas."),
  bullet("Reportes 4.1, 4.2 y 4.3 generables en Microsoft Word (.docx) con Apache POI, descargables desde la web e incluyendo la medición del tiempo de respuesta de cada operación."),
  bullet("Altas en tiempo real desde la web: crear una factura completa (que se refleja en el grafo) y agregar ítems/proveedores a la tabla hash mostrando si hubo colisión."),
  bullet("Salida controlada del programa con liberación de estructuras mediante shutdown hook."),
  h2("2.3 Correspondencia con las secciones de la rúbrica"),
  table(
    ["Sección", "Requisito", "Componente que lo implementa"],
    [
      ["2", "Tabla hash + GUI + reportes", "CustomHashTable, HashTableApp, ReportService"],
      ["3.1", "Lectura BD + tiempos y colisiones", "DatabaseCatalogoSource, SP_VERIFICAR_CATALOGO"],
      ["3.2", "Búsquedas hash O(1)", "CustomHashTable.get()"],
      ["3.3", "Grafos dirigidos de negocio", "CommercialGraph, GraphRestApi"],
      ["3.4", "Salida controlada", "Main (shutdown hook)"],
      ["4", "Reportes en Word (.docx) con tiempos", "WordReportExporter, ComercioDao"],
      ["4-5", "API REST + visualización web", "GraphRestApi, web/index.html"],
    ],
    [1400, 4000, 3960]
  ),
];

/* =====================================================================
 *  3. REQUISITOS
 * ===================================================================== */
const sec3 = [
  h1("3. Requisitos del Sistema"),
  h2("3.1 Requisitos de software"),
  table(
    ["Componente", "Versión", "Notas"],
    [
      ["JDK (Java)", "25", "Usa records, text blocks, var, switch expressions"],
      ["Maven", "3.9+", "Incluido con IntelliJ/NetBeans; gestiona dependencias"],
      ["Oracle Database", "XE 21c / 19c+", "Soporta PARTITION BY RANGE y sequences"],
      ["ojdbc11", "23.3.0.23.09", "Driver JDBC (auto-registro vía SPI)"],
      ["Apache POI", "5.2.5", "poi-ooxml: generación de reportes .docx"],
      ["JavaFX", "25", "Controls + Graphics (GUI de escritorio)"],
      ["JUnit Jupiter", "5.10.2", "Pruebas unitarias y de integración"],
      ["Navegador web", "Chrome/Edge/Firefox", "Para el frontend Vis.js (CDN)"],
    ],
    [2400, 2200, 4760]
  ),
  h2("3.2 Requisitos de hardware (mínimos sugeridos)"),
  bullet("Procesador: doble núcleo a 2.0 GHz o superior."),
  bullet("Memoria RAM: 4 GB (8 GB recomendado si se ejecuta Oracle en la misma máquina o VM)."),
  bullet("Espacio en disco: 2 GB para el JDK, dependencias Maven y la base de datos."),
  bullet("Conexión de red: requerida para descargar Vis.js desde el CDN y para conectarse a la VM de Oracle."),
];

/* =====================================================================
 *  4. ARQUITECTURA
 * ===================================================================== */
const sec4 = [
  h1("4. Arquitectura del Sistema"),
  h2("4.1 Visión por capas"),
  p("El sistema sigue una arquitectura en capas con una abstracción central (CatalogoSource) que desacopla el origen de los datos de las capas de presentación. Esto permite alternar entre Oracle y datos sintéticos sin modificar la GUI, la CLI ni la API."),
  table(
    ["Capa", "Responsabilidad", "Clases / artefactos"],
    [
      ["Presentación", "CLI, GUI de escritorio y frontend web", "Main, HashTableApp, ReportsWindow, web/index.html"],
      ["Servicios / API", "API REST, reportes, escrituras, exportación", "GraphRestApi, ReportService, ReportExporter, WordReportExporter, ComercioDao"],
      ["Dominio / Lógica", "Grafos y estructura hash", "CommercialGraph, CustomHashTable, Nodo, Arista"],
      ["Acceso a datos", "Fuentes de catálogo (Oracle / sintético)", "CatalogoSource, DatabaseCatalogoSource, SyntheticCatalogoSource, CatalogoSources"],
      ["Modelo", "Entidades inmutables (records)", "ItemFloral, ProveedorOrigen, Cliente, Factura, TipoCliente, DetalleFactura"],
      ["Persistencia", "Oracle Database", "install.sql (6 tablas + PL/SQL)"],
    ],
    [1700, 3460, 4200]
  ),
  h2("4.2 Patrón de fuente de datos (CatalogoSource)"),
  p("La interfaz CatalogoSource define tres operaciones: cargar() (catálogo de ítems), cargarMarcas() (proveedores) y descripcion(). Existen dos implementaciones:"),
  bullet("DatabaseCatalogoSource: lee desde Oracle vía JDBC; mide tiempo de carga y colisiones."),
  bullet("SyntheticCatalogoSource: genera 200 ítems y 3 marcas en memoria, útil cuando Oracle no está disponible."),
  p("La clase utilitaria CatalogoSources.defaultSource() decide automáticamente cuál usar: si la variable ORACLE_URL está definida (como variable de entorno del SO o como propiedad JVM -D), retorna la fuente Oracle; de lo contrario, la sintética."),
  h2("4.3 Flujo de datos en el arranque"),
  numbered("Main resuelve la fuente con seleccionarFuente() (respeta el flag --source y el auto-detect)."),
  numbered("Se invoca source.cargar() y source.cargarMarcas(), poblando dos tablas hash independientes."),
  numbered("Si la fuente es Oracle, cargar() ejecuta primero el procedimiento PL/SQL SP_VERIFICAR_CATALOGO."),
  numbered("Se ejecutan métricas, búsquedas y reportes; los reportes se exportan a la carpeta reports/."),
  numbered("Se registra un shutdown hook y se levanta el servidor HTTP en el puerto 8085."),
  numbered("El frontend web y/o la GUI consumen las tablas hash y los grafos."),
];

/* =====================================================================
 *  5. ESTRUCTURA DEL PROYECTO
 * ===================================================================== */
const sec5 = [
  h1("5. Estructura del Proyecto"),
  h2("5.1 Árbol de paquetes"),
  ...codeBlock([
    "proyectofinal1/",
    "├── pom.xml                     (Maven: ojdbc11, poi-ooxml, JavaFX; perfil dbfloristeria)",
    "├── install.sql                 (DDL Oracle + datos semilla + PL/SQL)",
    "├── web/",
    "│   └── index.html              (Frontend Vis.js: grafos + tabla hash + altas + reportes)",
    "└── src/",
    "    ├── main/java/umg/edu/gt/floristeria/",
    "    │   ├── Main.java            (demo CLI + arranque del servidor)",
    "    │   ├── hash/",
    "    │   │   └── CustomHashTable.java",
    "    │   ├── model/              (6 records de dominio)",
    "    │   │   ├── ItemFloral.java        ProveedorOrigen.java",
    "    │   │   ├── Cliente.java           TipoCliente.java",
    "    │   │   └── Factura.java           DetalleFactura.java",
    "    │   ├── service/",
    "    │   │   ├── CatalogoSource.java          (interfaz)",
    "    │   │   ├── DatabaseCatalogoSource.java  (Oracle/JDBC, lectura)",
    "    │   │   ├── SyntheticCatalogoSource.java (en memoria)",
    "    │   │   ├── CatalogoSources.java         (factory/auto-detect)",
    "    │   │   ├── ComercioDao.java             (escrituras + recorrido 4.3)",
    "    │   │   ├── ReportService.java           (reportes 4.1 y 4.2)",
    "    │   │   ├── ReporteGrafoCliente.java     (DTO jerárquico del 4.3)",
    "    │   │   ├── ReportExporter.java          (CSV / JSON)",
    "    │   │   └── WordReportExporter.java      (reportes .docx con POI)",
    "    │   ├── graph/",
    "    │   │   ├── CommercialGraph.java   Nodo.java   Arista.java",
    "    │   ├── api/",
    "    │   │   └── GraphRestApi.java      (servidor HTTP :8085)",
    "    │   ├── ui/",
    "    │   │   ├── HashTableApp.java      (GUI JavaFX)",
    "    │   │   ├── ReportsWindow.java     (ventana de reportes)",
    "    │   │   └── HashTableLauncher.java (workaround JavaFX)",
    "    │   └── util/",
    "    │       └── Durations.java         (formato de tiempos)",
    "    └── test/java/umg/edu/gt/floristeria/",
    "        ├── hash/CustomHashTableTest.java",
    "        └── service/DatabaseCatalogoSourceIT.java",
  ]),
  h2("5.2 Resumen de paquetes"),
  table(
    ["Paquete", "Contenido", "Clases"],
    [
      ["(raíz)", "Punto de entrada CLI", "Main"],
      ["hash", "Estructura de datos propia", "CustomHashTable"],
      ["model", "Entidades de dominio (records)", "6 records"],
      ["service", "Fuentes de datos, escrituras y reportes", "9 clases (incl. ComercioDao, WordReportExporter)"],
      ["graph", "Modelo de grafo dirigido", "CommercialGraph, Nodo, Arista"],
      ["api", "Servidor REST nativo", "GraphRestApi"],
      ["ui", "Interfaz JavaFX", "HashTableApp, ReportsWindow, HashTableLauncher"],
      ["util", "Utilidades transversales", "Durations"],
    ],
    [1500, 4000, 3860]
  ),
];

/* =====================================================================
 *  6. MODELO DE DATOS
 * ===================================================================== */
const sec6 = [
  h1("6. Modelo de Datos (Oracle)"),
  h2("6.1 Diagrama entidad-relación (descripción)"),
  p("El esquema consta de seis tablas relacionadas. TIPO_CLIENTE clasifica a los CLIENTE. Cada CLIENTE puede tener varias FACTURA. Cada FACTURA contiene varias líneas en DETALLE_FACTURA, y cada línea referencia un ITEM_FLORAL. Cada ITEM_FLORAL proviene de un PROVEEDOR_ORIGEN (la marca/finca)."),
  ...codeBlock([
    "TIPO_CLIENTE 1───∞ CLIENTE 1───∞ FACTURA 1───∞ DETALLE_FACTURA",
    "                                                      │",
    "                                                      ∞",
    "                            PROVEEDOR_ORIGEN 1───∞ ITEM_FLORAL",
  ]),
  h2("6.2 Definición de tablas"),
  h3("TIPO_CLIENTE"),
  table(["Columna", "Tipo", "Restricción"],
    [["id_tipo_cliente", "NUMBER(10)", "PK"],
     ["nombre_tipo", "VARCHAR2(50)", "NOT NULL, UNIQUE"],
     ["descuento_base", "NUMBER(5,2)", "DEFAULT 0, CHECK 0-100"]],
    [3000, 2600, 3760]),
  h3("CLIENTE"),
  table(["Columna", "Tipo", "Restricción"],
    [["id_cliente", "NUMBER(10)", "PK"],
     ["nit", "VARCHAR2(15)", "NOT NULL, UNIQUE"],
     ["nombre_completo", "VARCHAR2(100)", "NOT NULL"],
     ["direccion", "VARCHAR2(150)", "nullable"],
     ["id_tipo_cliente", "NUMBER(10)", "FK → TIPO_CLIENTE"]],
    [3000, 2600, 3760]),
  h3("PROVEEDOR_ORIGEN"),
  table(["Columna", "Tipo", "Restricción"],
    [["id_proveedor", "NUMBER(10)", "PK"],
     ["nombre_finca", "VARCHAR2(100)", "NOT NULL"],
     ["pais_origen", "VARCHAR2(50)", "NOT NULL"]],
    [3000, 2600, 3760]),
  h3("ITEM_FLORAL"),
  table(["Columna", "Tipo", "Restricción"],
    [["id_item", "NUMBER(10)", "PK"],
     ["nombre_flor", "VARCHAR2(100)", "NOT NULL"],
     ["precio_unitario", "NUMBER(10,2)", "CHECK > 0"],
     ["id_proveedor", "NUMBER(10)", "FK → PROVEEDOR_ORIGEN"]],
    [3000, 2600, 3760]),
  h3("FACTURA (particionada por rango de año)"),
  table(["Columna", "Tipo", "Restricción"],
    [["id_factura", "NUMBER(10)", "PK"],
     ["fecha_emision", "DATE", "NOT NULL, clave de partición"],
     ["id_cliente", "NUMBER(10)", "FK → CLIENTE"],
     ["serie", "VARCHAR2(10)", "NOT NULL"],
     ["numero_documento", "NUMBER(10)", "UNIQUE(serie, numero_documento)"]],
    [3000, 2600, 3760]),
  p("Particiones: p_2024 (< 2025-01-01), p_2025 (< 2026-01-01), p_2026 (< 2027-01-01) y p_max (MAXVALUE, absorbe facturas con fecha futura creadas en vivo). Índice local idx_factura_cliente."),
  h3("DETALLE_FACTURA"),
  table(["Columna", "Tipo", "Restricción"],
    [["id_detalle", "NUMBER(10)", "PK"],
     ["id_factura", "NUMBER(10)", "FK → FACTURA"],
     ["id_item", "NUMBER(10)", "FK → ITEM_FLORAL"],
     ["cantidad", "NUMBER(10)", "CHECK > 0"],
     ["precio_venta", "NUMBER(10,2)", "CHECK >= 0"]],
    [3000, 2600, 3760]),
  h2("6.3 Procedimiento PL/SQL (Sección 3.1)"),
  p("SP_VERIFICAR_CATALOGO lee los conteos de las tablas de catálogo y registra el tiempo de lectura del lado de Oracle mediante DBMS_UTILITY.GET_TIME, imprimiendo los resultados con DBMS_OUTPUT. Es invocado por DatabaseCatalogoSource.cargar() antes del SELECT principal."),
  ...codeBlock([
    "CREATE OR REPLACE PROCEDURE SP_VERIFICAR_CATALOGO IS",
    "    v_inicio NUMBER; v_fin NUMBER;",
    "    v_cnt_item NUMBER; v_cnt_prov NUMBER; v_cnt_tipo NUMBER;",
    "BEGIN",
    "    v_inicio := DBMS_UTILITY.GET_TIME;",
    "    SELECT COUNT(*) INTO v_cnt_item FROM ITEM_FLORAL;",
    "    SELECT COUNT(*) INTO v_cnt_prov FROM PROVEEDOR_ORIGEN;",
    "    SELECT COUNT(*) INTO v_cnt_tipo FROM TIPO_CLIENTE;",
    "    v_fin := DBMS_UTILITY.GET_TIME;",
    "    DBMS_OUTPUT.PUT_LINE('ITEM_FLORAL: ' || v_cnt_item);",
    "    DBMS_OUTPUT.PUT_LINE('Tiempo Oracle: '||((v_fin-v_inicio)*10)||' ms');",
    "END SP_VERIFICAR_CATALOGO;",
  ]),
  h2("6.4 Datos semilla"),
  p("install.sql siembra: 4 tipos de cliente, 4 proveedores (Holanda, Ecuador, Guatemala, Colombia), 5 clientes, 10 ítems florales (IDs 50-59), 10 facturas (años 2024-2026) y 25 líneas de detalle. Esta cantidad garantiza que las cinco consultas de grafo devuelvan resultados representativos."),
];

/* =====================================================================
 *  7. COMPONENTES PRINCIPALES
 * ===================================================================== */
const sec7 = [
  h1("7. Componentes Principales"),

  h2("7.1 Tabla Hash Personalizada (CustomHashTable)"),
  p("Estructura de datos genérica CustomHashTable<K,V> implementada desde cero, sin usar java.util.HashMap. Resuelve colisiones por encadenamiento separado (listas enlazadas en cada slot)."),
  h3("Características de diseño"),
  bullet("Capacidad inicial: 101 (número primo, para distribuir mejor los índices)."),
  bullet("Función hash: (key.hashCode() & 0x7FFFFFFF) % capacity. El enmascarado con 0x7FFFFFFF fuerza el valor a positivo y evita el bug clásico de Math.abs(Integer.MIN_VALUE)."),
  bullet("Rehash automático al superar 0.75 de factor de carga, duplicando al siguiente primo (101 → 211 → 431…)."),
  bullet("Conteo estricto de colisiones: una actualización de clave existente NO cuenta como colisión; solo una clave nueva en un slot ya ocupado."),
  bullet("get() retorna un SearchResult con métricas: valor, posición en la tabla, número de probes y duración en nanosegundos."),
  h3("API pública principal"),
  table(["Método", "Retorno", "Descripción"],
    [["put(K, V)", "void", "Inserta o actualiza; dispara rehash si procede"],
     ["get(K)", "SearchResult<V>", "Búsqueda con métricas de rendimiento"],
     ["containsKey(K)", "boolean", "Verifica existencia de la clave"],
     ["getSize()", "int", "Número de elementos almacenados"],
     ["getCapacity()", "int", "Tamaño actual del arreglo de slots"],
     ["getCollisionCount()", "int", "Colisiones reales acumuladas"],
     ["chainLengthAt(int)", "int", "Longitud de cadena en un slot"],
     ["keysAt(int)", "List<K>", "Claves encadenadas en un slot"],
     ["entries()", "List<Entry<K,V>>", "Recorrido completo con slot físico"]],
    [2600, 2600, 4160]),

  h2("7.2 Modelos de dominio"),
  p("Seis records inmutables de Java representan las entidades. Los records generan automáticamente constructor, accessors, equals, hashCode y toString."),
  table(["Record", "Campos"],
    [["ItemFloral", "id, nombreFlor, precio, idProveedor"],
     ["ProveedorOrigen", "id, nombreFinca, pais"],
     ["Cliente", "id, nit, nombre, direccion, idTipoCliente"],
     ["TipoCliente", "id, nombre, descuento"],
     ["Factura", "id, fechaEmision, idCliente, serie, numeroDocumento"],
     ["DetalleFactura", "id, idFactura, idItem, cantidad, precioVenta"]],
    [2600, 6760]),

  h2("7.3 Pipeline de carga JDBC (DatabaseCatalogoSource)"),
  p("Lee el catálogo desde Oracle vía JDBC nativo. Las credenciales se obtienen del entorno o de propiedades JVM mediante el factory fromEnv(). La URL se normaliza automáticamente: acepta formatos cortos como localhost:1521/umg y antepone el prefijo jdbc:oracle:thin:@// cuando falta."),
  bullet("cargar(): ejecuta SP_VERIFICAR_CATALOGO, luego SELECT sobre ITEM_FLORAL; construye la tabla hash midiendo tiempo total y reportando colisiones y capacidad."),
  bullet("cargarMarcas(): SELECT sobre PROVEEDOR_ORIGEN; construye una segunda tabla hash."),
  bullet("Usa try-with-resources para Connection, Statement y ResultSet (cierre automático)."),
  bullet("Propaga SQLException sin tragarla; la capa de presentación decide el comportamiento."),
  p("CatalogoSources.defaultSource() centraliza la decisión Oracle vs. sintético consultando ORACLE_URL en el entorno y en las propiedades del sistema."),

  h2("7.4 Servicios de reporte (ReportService y ReportExporter)"),
  p("ReportService genera dos reportes como listas de records puros, independientes del medio de salida:"),
  bullet("Reporte 4.1 — Productos registrados: para cada ítem reporta su clave hash, slot físico, número de probes y tiempo de recuperación en nanosegundos."),
  bullet("Reporte 4.2 — Producto y su marca: busca el proveedor de cada ítem en la tabla hash de marcas (independiente) y reporta el tiempo de esa búsqueda específica."),
  p("ReportExporter persiste ambos reportes a CSV (UTF-8 con BOM, escape RFC 4180) y JSON (con metadatos: reporte, generadoEn, totalFilas, tiempoPromedioNs, tiempoMaximoNs). Los tiempos se guardan siempre en nanosegundos para no perder precisión."),

  h2("7.5 Utilidad de tiempos (Durations)"),
  p("Convierte nanosegundos a la unidad más legible con sufijo explícito (ns, µs, ms, s), de modo que los reportes nunca dejen ambigüedad sobre la escala."),
  table(["Entrada (ns)", "Salida"],
    [["347", "347 ns"], ["1 500", "1.50 µs"], ["2 500 000", "2.500 ms"], ["3 750 000 000", "3.750 s"]],
    [4680, 4680]),

  h2("7.6 Grafos comerciales (CommercialGraph, Nodo, Arista)"),
  p("CommercialGraph construye grafos dirigidos en memoria a partir de consultas JDBC. Nodo encapsula (id, label, tipo) y Arista (origenId, destinoId, relacion). Si ORACLE_URL no está configurada, cada método limpia las listas y retorna un grafo vacío en lugar de fallar."),
  p("Tipos de nodo y su color en la visualización: CLIENTE (azul, elipse), FACTURA (rojo, caja), ITEM (verde, diamante), PROVEEDOR (amarillo, hexágono)."),
  h3("Consultas implementadas (Sección 3.3)"),
  table(["Método", "Relación modelada"],
    [["construirGrafoTrazabilidad(idFactura)", "Cliente → Factura → Ítem → Proveedor"],
     ["construirGrafoClienteProductos(idCliente)", "Cliente → Facturas → Ítems → Proveedor"],
     ["construirGrafoProveedorImpacto(idProveedor)", "Proveedor → Ítems → Facturas → Clientes"],
     ["construirGrafoClienteProductosPorAnio(...)", "Igual que cliente-productos, filtrado por rango de años"],
     ["construirGrafoTrazabilidadInversa(idItem)", "Ítem → Facturas → Clientes que lo compraron"]],
    [4400, 4960]),

  h2("7.7 API REST nativa (GraphRestApi)"),
  p("Servidor HTTP construido con com.sun.net.httpserver (sin Spring ni Quarkus), escuchando en el puerto 8085. Registra contextos para las cinco consultas de grafo, la lectura y alta de las dos tablas hash (GET/POST), la creación de facturas (POST), el listado de clientes, los tres reportes Word (.docx) y el frontend estático. Todos los endpoints aplican cabeceras CORS (GET, POST, OPTIONS) y manejan el pre-flight OPTIONS. La serialización JSON es manual y usa Locale.ROOT para garantizar el punto decimal; los cuerpos POST se reciben como application/x-www-form-urlencoded."),

  h2("7.8 Frontend web (web/index.html)"),
  p("Página única que carga Vis.js desde CDN. Ofrece dos pestañas:"),
  bullet("Grafos Comerciales: selector de las cinco consultas, campo de ID, inputs de rango de años (visibles solo para la consulta por año) y lienzo interactivo con leyenda de colores."),
  bullet("Tabla Hash: visualiza el catálogo o las marcas con estadísticas (capacidad, tamaño, factor de carga, colisiones, slots ocupados/vacíos, cadena máxima) y una tabla de slots con barra de color por longitud de cadena."),

  h2("7.9 Interfaz de escritorio JavaFX (HashTableApp)"),
  p("GUI que consume un CatalogoSource inyectado. Capacidades: heatmap de slots coloreado por longitud de cadena, panel de métricas en vivo, búsqueda animada (parpadeo verde en hit, rojo en miss), detalle de slot al hacer clic y ComboBox para alternar entre fuente sintética y Oracle en tiempo de ejecución. ReportsWindow muestra los reportes 4.1 y 4.2 en TableView con gráficas de distribución de tiempos y botones de exportación."),
  p("HashTableLauncher es una clase que NO extiende Application; sirve para evitar el error “JavaFX runtime components are missing” al ejecutar desde el classpath en el IDE."),

  h2("7.10 Escrituras en tiempo real (ComercioDao)"),
  p("ComercioDao centraliza las escrituras a Oracle, reutilizando el mismo patrón de credenciales que el resto del proyecto. Permite demostrar el comportamiento del sistema en vivo desde la web:"),
  bullet("crearFactura(idCliente, lineas): inserta una FACTURA y sus DETALLE_FACTURA en una sola transacción (commit/rollback), calculando el subtotal de cada línea. Genera los IDs con NVL(MAX(id),base)+1 para no chocar con los datos semilla. Tras crearla, el frontend muestra automáticamente la trazabilidad de la nueva factura en el grafo."),
  bullet("insertarItem / insertarMarca: agregan un registro al catálogo y, en paralelo, a la tabla hash en memoria. El endpoint reporta el slot asignado, si hubo colisión (cadena > 1 en ese slot), la longitud de la cadena, si ocurrió un rehash y el conteo de colisiones, evidenciando el funcionamiento de la estructura."),
  bullet("recorridoCliente(idCliente): consulta y agrupa las facturas del cliente por año (2024-2026) para alimentar el reporte 4.3, midiendo el tiempo de respuesta del recorrido."),

  h2("7.11 Reportes Microsoft Word (WordReportExporter)"),
  p("WordReportExporter genera los tres reportes de la sección 4 como documentos .docx usando Apache POI (XWPF). Cada método devuelve un byte[] que la API escribe directamente en la respuesta de descarga (Content-Disposition: attachment). Todos incluyen la medición del tiempo de respuesta con Durations.human(...):"),
  bullet("4.1 Productos registrados: tabla con ID, nombre, precio, clave hash, slot, probes y tiempo de búsqueda por registro, más el promedio y el máximo. Refleja el estado actual de la tabla hash (incluye ítems agregados en vivo)."),
  bullet("4.2 Producto y su marca: tabla con el tiempo de búsqueda de la marca asociada a cada producto en su tabla hash independiente."),
  bullet("4.3 Cliente → Facturas → Productos: encabezado con el tiempo del recorrido; secciones por año (2024, 2025, 2026) y, dentro de cada una, una tabla por factura con sus líneas (ítem, cantidad, subtotal, marca, país). Requiere Oracle."),
  p("Las descargas se disparan desde botones en la web (pestaña Tabla Hash para 4.1/4.2; pestaña Grafos, con selector de cliente, para 4.3). Los exportadores CSV/JSON previos (ReportExporter) se conservan."),
];

/* =====================================================================
 *  8. INSTALACIÓN Y CONFIGURACIÓN
 * ===================================================================== */
const sec8 = [
  h1("8. Instalación y Configuración"),
  h2("8.1 Preparar la base de datos"),
  p("Ejecute el script install.sql contra la instancia de Oracle. El script es idempotente: elimina las tablas y sequences previas antes de recrearlas, crea el procedimiento PL/SQL e inserta los datos semilla."),
  ...codeBlock([
    "# Desde PowerShell (las comillas protegen el $ de la contraseña)",
    'sqlplus ' + "'" + 'system/"Umg$2026"@//localhost:1521/umg' + "'" + ' "@install.sql"',
  ]),
  p("Verifique la instalación ejecutando el procedimiento:"),
  ...codeBlock([
    "SET SERVEROUTPUT ON;",
    "EXEC SP_VERIFICAR_CATALOGO;",
    "-- Debe reportar: ITEM_FLORAL 10, PROVEEDOR_ORIGEN 4, TIPO_CLIENTE 4",
  ]),
  h2("8.2 Configurar las credenciales de Oracle"),
  p("El sistema lee ORACLE_URL, ORACLE_USER y ORACLE_PASS. Acepta cualquiera de estas tres vías (en orden de prioridad: variable de entorno del SO, luego propiedad JVM):"),
  h3("Opción A — Perfil Maven dbfloristeria (recomendada)"),
  p("El pom.xml incluye un perfil que inyecta las credenciales. Actívelo así:"),
  ...codeBlock([
    "mvn exec:java -Pdbfloristeria      # demo CLI con Oracle",
    "mvn javafx:run -Pdbfloristeria     # GUI con Oracle",
    "# En NetBeans: clic derecho proyecto > Set Configuration > dbfloristeria",
  ]),
  h3("Opción B — Variables de entorno del sistema (Windows)"),
  p("Panel de control > Variables de entorno > Variables de usuario > Nueva. Cree ORACLE_URL, ORACLE_USER y ORACLE_PASS, luego reinicie el IDE."),
  h3("Opción C — VM Options del IDE"),
  ...codeBlock([
    "-DORACLE_URL=jdbc:oracle:thin:@//localhost:1521/umg",
    "-DORACLE_USER=system",
    "-DORACLE_PASS=Umg$2026",
  ]),
  p("Nota sobre la URL: el sistema normaliza automáticamente la URL. Puede escribir simplemente localhost:1521/umg y el prefijo jdbc:oracle:thin:@// se añade en tiempo de ejecución."),
];

/* =====================================================================
 *  9. GUÍA DE EJECUCIÓN
 * ===================================================================== */
const sec9 = [
  h1("9. Guía de Ejecución"),
  h2("9.1 Modos de ejecución"),
  table(["Modo", "Comando", "Resultado"],
    [["CLI (demo)", "mvn exec:java", "Métricas, búsquedas, reportes y servidor REST"],
     ["CLI + Oracle", "mvn exec:java -Pdbfloristeria", "Igual, pero con datos reales de Oracle"],
     ["GUI escritorio", "mvn javafx:run", "Interfaz JavaFX con heatmap y reportes"],
     ["Forzar sintético", "mvn exec:java -Dexec.args=--source=synth", "Ignora Oracle aunque esté configurado"],
     ["Web", "Abrir http://localhost:8085/", "Frontend Vis.js (con el servidor activo)"]],
    [2000, 4000, 3360]),
  h2("9.2 Demo de consola"),
  p("Al ejecutar Main sin argumentos, el programa: (1) carga el catálogo, (2) muestra métricas de la tabla hash, (3) realiza tres búsquedas exitosas y dos fallidas sobre IDs reales de la tabla, (4) verifica un reemplazo sin alterar el conteo de colisiones, (5) imprime los reportes 4.1 y 4.2, (6) los exporta a la carpeta reports/ y (7) levanta el servidor REST."),
  h2("9.3 Salida controlada (Sección 3.4)"),
  p("Al recibir Ctrl+C o SIGTERM, un shutdown hook libera las estructuras en memoria e informa cuántos ítems y marcas se liberaron, confirmando el cierre ordenado de las conexiones."),
];

/* =====================================================================
 *  10. REFERENCIA API
 * ===================================================================== */
const sec10 = [
  h1("10. Referencia de la API REST"),
  h2("10.1 Endpoints disponibles"),
  table(["Método y ruta", "Parámetros", "Descripción"],
    [[{t:"GET /api/grafo/trazabilidad", mono:true}, "factura={id}", "Trazabilidad completa de una factura"],
     [{t:"GET /api/grafo/cliente-productos", mono:true}, "cliente={id}", "Productos comprados por un cliente"],
     [{t:"GET /api/grafo/proveedor-impacto", mono:true}, "proveedor={id}", "Facturas/clientes impactados por un proveedor"],
     [{t:"GET /api/grafo/cliente-productos-anio", mono:true}, "cliente, anioInicio, anioFin", "Productos del cliente en un rango de años"],
     [{t:"GET /api/grafo/trazabilidad-inversa", mono:true}, "item={id}", "Clientes que compraron un ítem dado"],
     [{t:"GET /api/hash/catalogo", mono:true}, "—", "Serializa la tabla hash de ítems"],
     [{t:"GET /api/hash/marcas", mono:true}, "—", "Serializa la tabla hash de proveedores"],
     [{t:"POST /api/hash/catalogo", mono:true}, "id, nombre, precio, idProveedor", "Agrega ítem; reporta slot/colisión/rehash"],
     [{t:"POST /api/hash/marcas", mono:true}, "id, nombreFinca, pais", "Agrega proveedor; reporta colisión"],
     [{t:"POST /api/grafo/factura", mono:true}, "cliente, items=50:3,51:2", "Crea factura+detalles; devuelve idFactura"],
     [{t:"GET /api/clientes", mono:true}, "—", "Lista de clientes (para formularios)"],
     [{t:"GET /api/reporte/productos.docx", mono:true}, "—", "Reporte 4.1 en Word"],
     [{t:"GET /api/reporte/producto-marca.docx", mono:true}, "—", "Reporte 4.2 en Word"],
     [{t:"GET /api/reporte/grafo-cliente.docx", mono:true}, "cliente={id}", "Reporte 4.3 en Word (requiere Oracle)"],
     [{t:"GET /", mono:true}, "—", "Sirve web/index.html"]],
    [3700, 2660, 3000]),
  h2("10.2 Formato de respuesta de grafos"),
  ...codeBlock([
    "{",
    '  "nodes": [',
    '    {"id": "CLI_101", "label": "Alejandro Sian", "group": "CLIENTE"},',
    '    {"id": "FAC_2001", "label": "Factura #2001", "group": "FACTURA"}',
    "  ],",
    '  "edges": [',
    '    {"from": "CLI_101", "to": "FAC_2001", "label": "COMPRO_EN"}',
    "  ]",
    "}",
  ]),
  h2("10.3 Formato de respuesta de tabla hash"),
  ...codeBlock([
    "{",
    '  "tabla": "Catalogo de Items Florales",',
    '  "capacity": 101, "size": 10, "collisionCount": 0,',
    '  "loadFactor": "0.0990",',
    '  "entries": [',
    '    {"slot": 50, "id": 50, "nombre": "Tulipanes...", "precio": 25.00, "idProveedor": 5}',
    "  ]",
    "}",
  ]),
  h2("10.4 Ejemplos de invocación"),
  ...codeBlock([
    'curl "http://localhost:8085/api/grafo/trazabilidad?factura=2001"',
    'curl "http://localhost:8085/api/grafo/trazabilidad-inversa?item=50"',
    'curl "http://localhost:8085/api/hash/catalogo"',
    '# Alta de ítem en la tabla hash (form-urlencoded)',
    'curl -X POST "http://localhost:8085/api/hash/catalogo" \\',
    '     -d "id=151&nombre=Rosa+Test&precio=9.99&idProveedor=5"',
    '# Descargar el reporte 4.1 en Word',
    'curl -OJ "http://localhost:8085/api/reporte/productos.docx"',
  ]),
];

/* =====================================================================
 *  11. PRUEBAS
 * ===================================================================== */
const sec11 = [
  h1("11. Pruebas Automatizadas"),
  h2("11.1 Pruebas unitarias (CustomHashTableTest)"),
  p("Siete pruebas JUnit 5 validan el comportamiento de la tabla hash, incluyendo el forzado determinista de colisiones (claves 1, 102, 203, 304 que colisionan en el slot 1 con capacidad 101) y el manejo del caso límite hashCode() = Integer.MIN_VALUE mediante un record EvilKey."),
  ...codeBlock(["mvn test", "# Resultado esperado: Tests run: 7, Failures: 0, Errors: 0"]),
  h2("11.2 Prueba de integración (DatabaseCatalogoSourceIT)"),
  p("Test condicional anotado con @EnabledIfEnvironmentVariable(named=\"ORACLE_URL\", matches=\".+\"). Solo se ejecuta cuando hay una instancia de Oracle configurada; de lo contrario se omite silenciosamente. Verifica que cargar() y cargarMarcas() traigan los datos sembrados por install.sql."),
  ...codeBlock([
    "mvn verify                  # sin Oracle: IT se omite",
    "mvn verify -Pdbfloristeria  # con Oracle: IT se ejecuta",
  ]),
];

/* =====================================================================
 *  12. SOLUCIÓN DE PROBLEMAS
 * ===================================================================== */
const sec12 = [
  h1("12. Solución de Problemas"),
  table(["Síntoma", "Causa", "Solución"],
    [
      ["JavaFX runtime components are missing", "La clase principal extiende Application y JavaFX está en el classpath", "Ejecutar HashTableLauncher como main, o usar mvn javafx:run"],
      ["No suitable driver found for localhost:1521/umg", "URL sin el prefijo jdbc:oracle:thin:@//", "Resuelto por normalizarUrl(); verifique que ORACLE_URL no esté mal definida en el SO"],
      ["ORA-00904: NOMBRE_COMPLETO identificador no válido", "Esquema desactualizado (columna antigua)", "Re-ejecutar install.sql (es idempotente)"],
      ["PLS-00103: símbolo SQL inesperado", "Alias de columna llamado sql (palabra reservada)", "Usar alias ddl (ya corregido en install.sql)"],
      ["Expected double-quoted property name in JSON", "Locale español usaba coma decimal en el JSON", "Resuelto forzando Locale.ROOT en la serialización"],
      ["The url cannot be null (al usar grafos)", "ORACLE_URL no configurada", "Definir credenciales o usar el perfil dbfloristeria"],
      ["NullPointerException en ejecutarBusquedas", "IDs de búsqueda hardcodeados no existían en Oracle", "Resuelto: el demo toma IDs reales de entries()"],
    ],
    [2700, 3000, 3660]),
];

/* =====================================================================
 *  13. GLOSARIO
 * ===================================================================== */
const sec13 = [
  h1("13. Glosario"),
  table(["Término", "Definición"],
    [["Tabla hash", "Estructura que mapea claves a valores mediante una función hash, con acceso promedio O(1)."],
     ["Encadenamiento separado", "Técnica de resolución de colisiones que almacena en cada slot una lista de elementos."],
     ["Factor de carga", "Relación size/capacity; al superar 0.75 se dispara el rehash."],
     ["Rehash", "Redimensionamiento de la tabla al siguiente primo, reubicando todos los elementos."],
     ["Probe", "Cada nodo recorrido en la cadena de un slot durante una búsqueda."],
     ["Grafo dirigido", "Conjunto de nodos unidos por aristas con dirección, modela relaciones de negocio."],
     ["JDBC", "Java Database Connectivity: API estándar de Java para acceso a bases de datos."],
     ["CORS", "Mecanismo que permite que un navegador consuma una API de otro origen."],
     ["Shutdown hook", "Hilo que la JVM ejecuta al terminar el proceso, para liberar recursos."]],
    [2600, 6760]),
];

/* =====================================================================
 *  ENSAMBLADO
 * ===================================================================== */
const allChildren = [
  ...sec1, ...sec2, ...sec3, ...sec4, ...sec5, ...sec6,
  ...sec7, ...sec8, ...sec9, ...sec10, ...sec11, ...sec12, ...sec13
];

const doc = new Document({
  creator: "Floristería UMG",
  title: "Manual Técnico — Sistema de Gestión Comercial de Floristería",
  styles: {
    default: { document: { run: { font: "Arial", size: 22 } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 30, bold: true, font: "Arial", color: VERDE },
        paragraph: { spacing: { before: 320, after: 160 }, outlineLevel: 0,
          border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: VERDE2, space: 4 } } } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 25, bold: true, font: "Arial", color: VERDE2 },
        paragraph: { spacing: { before: 220, after: 120 }, outlineLevel: 1 } },
      { id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 22, bold: true, font: "Arial", color: "333333" },
        paragraph: { spacing: { before: 160, after: 80 }, outlineLevel: 2 } },
    ]
  },
  numbering: {
    config: [
      { reference: "bullets",
        levels: [
          { level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT,
            style: { paragraph: { indent: { left: 720, hanging: 360 } } } },
          { level: 1, format: LevelFormat.BULLET, text: "◦", alignment: AlignmentType.LEFT,
            style: { paragraph: { indent: { left: 1440, hanging: 360 } } } }
        ] },
      { reference: "nums",
        levels: [
          { level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT,
            style: { paragraph: { indent: { left: 720, hanging: 360 } } } }
        ] },
    ]
  },
  sections: [
    /* Portada: sin header/footer numerado */
    {
      properties: {
        page: { size: { width: 12240, height: 15840 },
                margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 } }
      },
      children: portada
    },
    /* Cuerpo: con header y footer numerado, empezando por la TOC */
    {
      properties: {
        page: { size: { width: 12240, height: 15840 },
                margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 } }
      },
      headers: {
        default: new Header({ children: [ new Paragraph({
          alignment: AlignmentType.RIGHT,
          border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: "CCCCCC", space: 2 } },
          children: [new TextRun({ text: "Manual Técnico — Floristería UMG", size: 16, color: "888888" })]
        }) ] })
      },
      footers: {
        default: new Footer({ children: [ new Paragraph({
          alignment: AlignmentType.CENTER,
          children: [
            new TextRun({ text: "Página ", size: 16, color: "888888" }),
            new TextRun({ children: [PageNumber.CURRENT], size: 16, color: "888888" }),
            new TextRun({ text: " de ", size: 16, color: "888888" }),
            new TextRun({ children: [PageNumber.TOTAL_PAGES], size: 16, color: "888888" })
          ]
        }) ] })
      },
      children: [...toc, ...allChildren]
    }
  ]
});

Packer.toBuffer(doc).then(buffer => {
  const preferido = path.join(__dirname, "Manual_Tecnico_Floristeria_UMG.docx");
  let out = preferido;
  try {
    fs.writeFileSync(out, buffer);
  } catch (e) {
    // El archivo preferido está bloqueado (abierto en Word): usa nombre alterno.
    out = path.join(__dirname, "Manual_Tecnico_Floristeria_UMG_v1.1.docx");
    fs.writeFileSync(out, buffer);
  }
  console.log("OK -> " + out + "  (" + buffer.length + " bytes)");
});
