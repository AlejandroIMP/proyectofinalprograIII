# Guía Arquitectónica y Técnica: Proyecto 2 — Sistema de Gestión Comercial de Floristería

**Curso:** Programación III | **Sección:** A | **Fecha de Entrega:** 30 de mayo de 2026

**Entorno Tecnológico Obligatorio:**
- Oracle Database (MV UMG, Oracle 19c+ recomendado)
- Java 25 (API REST sobre `com.sun.net.httpserver` + Tablas Hash propias, sin `java.util.HashMap`)
- HTML5 + JavaScript (visualización de grafos con Vis.js Network)

---

## 0. Estructura del Proyecto y Cómo Ejecutarlo

```
proyectofinal1/
├── pom.xml
├── install.sql                       # Sección 6
├── web/
│   └── index.html                    # Sección 5
└── src/main/java/umg/edu/gt/floristeria/
    ├── Main.java
    ├── hash/CustomHashTable.java     # Sección 2
    ├── model/                        # Records de dominio
    ├── service/DataLoadService.java  # Sección 3
    ├── graph/CommercialGraph.java    # Sección 4
    └── api/GraphRestApi.java         # Sección 4
```

**Pasos de ejecución:**

1. Instalar la base: `sqlplus usuario/clave@MV_UMG @install.sql`
2. Compilar: `mvn clean package` (requiere `ojdbc11` en `pom.xml`, ver al final de esta sección).
3. Levantar API: `java -jar target/proyectofinal1-1.0-SNAPSHOT.jar` (escucha en `:8080`).
4. Abrir `web/index.html` en un navegador (con la API arriba).

**Dependencia Maven obligatoria** (agregar al `pom.xml`):

```xml
<dependencies>
    <dependency>
        <groupId>com.oracle.database.jdbc</groupId>
        <artifactId>ojdbc11</artifactId>
        <version>23.3.0.23.09</version>
    </dependency>
</dependencies>
```

---

## 1. Modelo de Base de Datos en Oracle (Normalizado hasta 3FN)

Correspondencia con las 6 entidades requeridas por la cátedra:

| Entidad cátedra | Entidad floristería | Rol |
|---|---|---|
| TIPO_CLIENTE | TIPO_CLIENTE | Catálogo (Mayorista, Minorista, Eventos, Floristería Afiliada) |
| CLIENTE | CLIENTE | Transaccional |
| MARCA | PROVEEDOR_ORIGEN | Catálogo (fincas de Holanda, Ecuador, Guatemala, Colombia) |
| PRODUCTO | ITEM_FLORAL | Catálogo (Rosas, Tulipanes, Arreglos Fúnebres, Centros de Mesa) |
| FACTURA | FACTURA | Cabecera histórica, particionada por año |
| DETALLE | DETALLE_FACTURA | Líneas de venta |

### 1.1 Script DDL

```sql
-- 1. Catálogo: TIPO_CLIENTE
CREATE TABLE TIPO_CLIENTE (
    id_tipo_cliente NUMBER(10) PRIMARY KEY,
    nombre_tipo     VARCHAR2(50)  NOT NULL UNIQUE,
    descuento_base  NUMBER(5,2)   DEFAULT 0.00 NOT NULL,
    CONSTRAINT ck_tc_descuento CHECK (descuento_base BETWEEN 0 AND 100)
);

-- 2. CLIENTE (3FN: todo atributo depende exclusivamente de la PK)
CREATE TABLE CLIENTE (
    id_cliente       NUMBER(10) PRIMARY KEY,
    nit              VARCHAR2(15)  NOT NULL UNIQUE,
    nombre           VARCHAR2(100) NOT NULL,
    direccion        VARCHAR2(150),
    id_tipo_cliente  NUMBER(10)    NOT NULL,
    CONSTRAINT fk_cliente_tipo
        FOREIGN KEY (id_tipo_cliente) REFERENCES TIPO_CLIENTE(id_tipo_cliente)
);
CREATE INDEX idx_cliente_tipo ON CLIENTE(id_tipo_cliente);

-- 3. PROVEEDOR_ORIGEN (equivalente a MARCA)
CREATE TABLE PROVEEDOR_ORIGEN (
    id_proveedor   NUMBER(10) PRIMARY KEY,
    nombre_finca   VARCHAR2(100) NOT NULL,
    pais_origen    VARCHAR2(50)  NOT NULL
);

-- 4. ITEM_FLORAL (equivalente a PRODUCTO)
CREATE TABLE ITEM_FLORAL (
    id_item          NUMBER(10) PRIMARY KEY,
    nombre_flor      VARCHAR2(100) NOT NULL,
    precio_unitario  NUMBER(10,2) NOT NULL,
    id_proveedor     NUMBER(10)   NOT NULL,
    CONSTRAINT fk_item_proveedor
        FOREIGN KEY (id_proveedor) REFERENCES PROVEEDOR_ORIGEN(id_proveedor),
    CONSTRAINT ck_item_precio CHECK (precio_unitario > 0)
);
CREATE INDEX idx_item_proveedor ON ITEM_FLORAL(id_proveedor);

-- 5. FACTURA — particionada físicamente por año (rúbrica: histórico 2024-2026)
CREATE TABLE FACTURA (
    id_factura        NUMBER(10) NOT NULL,
    fecha_emision     DATE       NOT NULL,
    id_cliente        NUMBER(10) NOT NULL,
    serie             VARCHAR2(10) NOT NULL,
    numero_documento  NUMBER(10)   NOT NULL,
    CONSTRAINT pk_factura PRIMARY KEY (id_factura),
    CONSTRAINT uq_factura_doc UNIQUE (serie, numero_documento),
    CONSTRAINT fk_factura_cliente
        FOREIGN KEY (id_cliente) REFERENCES CLIENTE(id_cliente)
)
PARTITION BY RANGE (fecha_emision) (
    PARTITION p_2024 VALUES LESS THAN (TO_DATE('2025-01-01','YYYY-MM-DD')),
    PARTITION p_2025 VALUES LESS THAN (TO_DATE('2026-01-01','YYYY-MM-DD')),
    PARTITION p_2026 VALUES LESS THAN (TO_DATE('2027-01-01','YYYY-MM-DD'))
);
CREATE INDEX idx_factura_cliente ON FACTURA(id_cliente) LOCAL;

-- 6. DETALLE_FACTURA
CREATE TABLE DETALLE_FACTURA (
    id_detalle    NUMBER(10) PRIMARY KEY,
    id_factura    NUMBER(10) NOT NULL,
    id_item       NUMBER(10) NOT NULL,
    cantidad      NUMBER(10) NOT NULL,
    precio_venta  NUMBER(10,2) NOT NULL,
    CONSTRAINT fk_detalle_factura FOREIGN KEY (id_factura) REFERENCES FACTURA(id_factura),
    CONSTRAINT fk_detalle_item    FOREIGN KEY (id_item)    REFERENCES ITEM_FLORAL(id_item),
    CONSTRAINT ck_detalle_qty     CHECK (cantidad > 0),
    CONSTRAINT ck_detalle_precio  CHECK (precio_venta >= 0)
);
CREATE INDEX idx_detalle_factura ON DETALLE_FACTURA(id_factura);
CREATE INDEX idx_detalle_item    ON DETALLE_FACTURA(id_item);

-- Sequences para generación de PKs
CREATE SEQUENCE seq_cliente   START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE seq_item      START WITH 100  INCREMENT BY 1;
CREATE SEQUENCE seq_factura   START WITH 2000 INCREMENT BY 1;
CREATE SEQUENCE seq_detalle   START WITH 1    INCREMENT BY 1;
CREATE SEQUENCE seq_proveedor START WITH 10   INCREMENT BY 1;
CREATE SEQUENCE seq_tipo_cli  START WITH 1    INCREMENT BY 1;
```

---

## 2. Backend en Java 25 — Tabla Hash Propia (sin `java.util.HashMap`)

La rúbrica exige medir tiempos de búsqueda y conteo de colisiones. Se implementa direccionamiento por **encadenamiento separado** con rehash automático al superar el factor de carga.

### 2.1 `CustomHashTable<K,V>`

```java
package umg.edu.gt.floristeria.hash;

public class CustomHashTable<K, V> {

    private static final int INITIAL_CAPACITY = 101;          // primo
    private static final double LOAD_FACTOR_THRESHOLD = 0.75;

    private Node<K, V>[] table;
    private int size;
    private int collisionCount;   // colisiones acumuladas (slot ya ocupado por otra clave)

    @SuppressWarnings("unchecked")
    public CustomHashTable() {
        this.table = (Node<K, V>[]) new Node[INITIAL_CAPACITY];
    }

    private static final class Node<K, V> {
        final K key;
        V value;
        Node<K, V> next;
        Node(K key, V value) { this.key = key; this.value = value; }
    }

    private int indexFor(K key, int capacity) {
        // (h & 0x7FFFFFFF) evita el bug de Math.abs(Integer.MIN_VALUE)
        return (key.hashCode() & 0x7FFFFFFF) % capacity;
    }

    public void put(K key, V value) {
        if ((double) size / table.length >= LOAD_FACTOR_THRESHOLD) {
            rehash();
        }
        int index = indexFor(key, table.length);
        Node<K, V> head = table[index];

        if (head == null) {
            table[index] = new Node<>(key, value);
            size++;
            return;
        }

        // Slot ocupado: una colisión real si la clave es distinta a todas las del slot.
        Node<K, V> current = head;
        while (true) {
            if (current.key.equals(key)) {     // actualización, NO colisión
                current.value = value;
                return;
            }
            if (current.next == null) {
                current.next = new Node<>(key, value);
                size++;
                collisionCount++;              // se agregó al final de una cadena
                return;
            }
            current = current.next;
        }
    }

    public SearchResult<V> get(K key) {
        long start = System.nanoTime();
        int index = indexFor(key, table.length);
        Node<K, V> current = table[index];
        int probes = 0;
        while (current != null) {
            probes++;
            if (current.key.equals(key)) {
                return new SearchResult<>(current.value, index, probes,
                        System.nanoTime() - start);
            }
            current = current.next;
        }
        return new SearchResult<>(null, index, probes, System.nanoTime() - start);
    }

    public boolean containsKey(K key) { return get(key).value() != null; }

    @SuppressWarnings("unchecked")
    private void rehash() {
        Node<K, V>[] old = table;
        int newCapacity = nextPrime(old.length * 2);
        table = (Node<K, V>[]) new Node[newCapacity];
        size = 0;
        collisionCount = 0;
        for (Node<K, V> head : old) {
            for (Node<K, V> n = head; n != null; n = n.next) {
                put(n.key, n.value);
            }
        }
    }

    private static int nextPrime(int n) {
        while (!isPrime(n)) n++;
        return n;
    }

    private static boolean isPrime(int n) {
        if (n < 2) return false;
        for (int i = 2; (long) i * i <= n; i++) if (n % i == 0) return false;
        return true;
    }

    public int getCollisionCount() { return collisionCount; }
    public int getSize()           { return size; }
    public int getCapacity()       { return table.length; }

    public record SearchResult<T>(T value, int tablePosition, int probes, long durationNanoseconds) {}
}
```

### 2.2 Modelos de Dominio (Records)

```java
package umg.edu.gt.floristeria.model;

public record TipoCliente(int id, String nombre, double descuento) {}
public record Cliente(int id, String nit, String nombre, String direccion, int idTipoCliente) {}
public record ProveedorOrigen(int id, String nombreFinca, String pais) {}
public record ItemFloral(int id, String nombreFlor, double precio, int idProveedor) {}
public record Factura(int id, java.time.LocalDate fechaEmision, int idCliente, String serie, int numeroDocumento) {}
public record DetalleFactura(int id, int idFactura, int idItem, int cantidad, double precioVenta) {}
```

---

## 3. Pipeline de Carga (JDBC → Tabla Hash en Memoria)

```java
package umg.edu.gt.floristeria.service;

import umg.edu.gt.floristeria.hash.CustomHashTable;
import umg.edu.gt.floristeria.model.ItemFloral;
import java.sql.*;

public class DataLoadService {

    // Oracle 19c+: usar Service Name. Para XE legacy: jdbc:oracle:thin:@host:1521:XE
    private final String url;
    private final String user;
    private final String password;

    public DataLoadService(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public CustomHashTable<Integer, ItemFloral> loadItemsCatalog() throws SQLException {
        final String query = """
                SELECT id_item, nombre_flor, precio_unitario, id_proveedor
                FROM   ITEM_FLORAL
                """;
        CustomHashTable<Integer, ItemFloral> catalog = new CustomHashTable<>();
        long t0 = System.currentTimeMillis();

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                ItemFloral item = new ItemFloral(
                        rs.getInt("id_item"),
                        rs.getString("nombre_flor"),
                        rs.getDouble("precio_unitario"),
                        rs.getInt("id_proveedor"));
                catalog.put(item.id(), item);
            }
        }

        long elapsed = System.currentTimeMillis() - t0;
        System.out.printf("Catálogo cargado en %d ms | filas=%d | colisiones=%d | capacidad=%d%n",
                elapsed, catalog.getSize(), catalog.getCollisionCount(), catalog.getCapacity());
        return catalog;
    }
}
```

> **Buena práctica:** las credenciales se inyectan por constructor; la `Main` las lee de variables de entorno (`ORACLE_URL`, `ORACLE_USER`, `ORACLE_PASS`). Nunca las quemes en el código fuente.

---

## 4. Estructura de Grafos y API REST

La rúbrica pide **tres consultas relacionales**. Se exponen los siguientes endpoints:

| Endpoint | Pregunta de negocio |
|---|---|
| `GET /api/grafo/trazabilidad?factura={id}` | Cliente → Factura → Detalle → Ítem → Origen |
| `GET /api/grafo/cliente-productos?cliente={id}` | Productos consumidos por un cliente (histórico) |
| `GET /api/grafo/proveedor-impacto?proveedor={id}` | Clientes alcanzados por un proveedor de origen |

### 4.1 Modelo de Grafo

```java
package umg.edu.gt.floristeria.graph;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CommercialGraph {

    public record Node(String id, String label, String type) {}
    public record Edge(String source, String target, String relation) {}

    private final Set<Node> nodes = new LinkedHashSet<>();
    private final List<Edge> edges = new ArrayList<>();

    public void addNode(String id, String label, String type) {
        nodes.add(new Node(id, label, type));   // record.equals usa todos los campos
    }

    public void addEdge(String source, String target, String relation) {
        edges.add(new Edge(source, target, relation));
    }

    public List<Node> getNodes() { return new ArrayList<>(nodes); }
    public List<Edge> getEdges() { return edges; }

    /** Serialización JSON manual para no requerir librería externa. */
    public String toJson() {
        StringBuilder sb = new StringBuilder("{\"nodes\":[");
        boolean first = true;
        for (Node n : nodes) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"id\":\"").append(esc(n.id()))
              .append("\",\"label\":\"").append(esc(n.label()))
              .append("\",\"type\":\"").append(esc(n.type())).append("\"}");
        }
        sb.append("],\"edges\":[");
        first = true;
        for (Edge e : edges) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"source\":\"").append(esc(e.source()))
              .append("\",\"target\":\"").append(esc(e.target()))
              .append("\",\"relation\":\"").append(esc(e.relation())).append("\"}");
        }
        return sb.append("]}").toString();
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
```

### 4.2 Servidor HTTP (sin dependencias externas)

```java
package umg.edu.gt.floristeria.api;

import com.sun.net.httpserver.HttpServer;
import umg.edu.gt.floristeria.graph.CommercialGraph;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class GraphRestApi {

    public static HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));

        server.createContext("/api/grafo/trazabilidad", exchange -> {
            // CORS
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");

            // En un sistema real esto se construye consultando la BD según el query string.
            CommercialGraph g = sampleTrazabilidad();
            byte[] body = g.toJson().getBytes(StandardCharsets.UTF_8);

            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        server.start();
        System.out.println("API REST escuchando en http://localhost:" + port);
        return server;
    }

    private static CommercialGraph sampleTrazabilidad() {
        CommercialGraph g = new CommercialGraph();
        g.addNode("C101",      "Alejandro Sian",               "Cliente");
        g.addNode("F2024-01",  "Factura #1023 (2024)",         "Factura");
        g.addNode("D1",        "Línea Detalle 1",              "Detalle");
        g.addNode("I50",       "Tulipanes Holandeses",         "ItemFloral");
        g.addNode("P05",       "Finca Países Bajos S.A.",      "ProveedorOrigen");

        g.addEdge("C101",     "F2024-01", "EMITIDA_A");
        g.addEdge("F2024-01", "D1",       "CONTIENE");
        g.addEdge("D1",       "I50",      "CONTABILIZA");
        g.addEdge("I50",      "P05",      "PROVEE_DE");
        return g;
    }
}
```

> **Nota didáctica:** los tipos en el JSON (`Cliente`, `Factura`, `Detalle`, `ItemFloral`, `ProveedorOrigen`) deben coincidir uno-a-uno con las claves del `colorsMapping` del front-end. Si renombras aquí, renombra allá.

---

## 5. Visualización Web del Grafo

```html
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <title>Visor de Grafos Comerciales — Floristería UMG</title>
    <script src="https://unpkg.com/vis-network/standalone/umd/vis-network.min.js"></script>
    <style>
        body { font-family: 'Segoe UI', Tahoma, sans-serif; margin: 20px; background: #fcfcfc; }
        #network-container { width: 100%; height: 600px; border: 1px solid #ddd;
                             background: #fff; border-radius: 8px; }
        h2 { color: #2c3e50; }
        .legend { margin-bottom: 15px; padding: 10px; background: #f0f2f5;
                  border-radius: 4px; display: inline-block; }
        .legend span { font-weight: 600; }
        #status { color: #c00; font-style: italic; min-height: 1.2em; }
    </style>
</head>
<body>

<h2>Trazabilidad Comercial (Cliente → Factura → Detalle → Ítem Floral → Origen)</h2>

<div class="legend">
    <strong>Leyenda:</strong>
    <span style="color:#2b7ce9">■</span> Cliente |
    <span style="color:#5cb85c">■</span> Factura |
    <span style="color:#f0ad4e">■</span> Detalle |
    <span style="color:#d9534f">■</span> Ítem Floral |
    <span style="color:#847ba7">■</span> Origen Finca
</div>

<div id="status">Cargando grafo...</div>
<div id="network-container"></div>

<script>
    const API = 'http://localhost:8080/api/grafo/trazabilidad';

    // Las claves DEBEN coincidir con CommercialGraph.Node.type del backend.
    const COLORS = {
        "Cliente":         "#2b7ce9",
        "Factura":         "#5cb85c",
        "Detalle":         "#f0ad4e",
        "ItemFloral":      "#d9534f",
        "ProveedorOrigen": "#847ba7"
    };

    fetch(API)
        .then(r => {
            if (!r.ok) throw new Error('HTTP ' + r.status);
            return r.json();
        })
        .then(data => {
            const nodes = data.nodes.map(n => ({
                id: n.id,
                label: n.label,
                color: COLORS[n.type] || '#97c2fc',
                shape: 'box',
                font: { color: '#ffffff', size: 14 }
            }));

            const edges = data.edges.map(e => ({
                from: e.source,
                to: e.target,
                label: e.relation,
                arrows: 'to',
                font: { align: 'top', size: 11 },
                color: { color: '#cccccc', highlight: '#2b7ce9' }
            }));

            new vis.Network(
                document.getElementById('network-container'),
                { nodes, edges },
                { physics: { enabled: true,
                             barnesHut: { gravitationalConstant: -2000,
                                          centralGravity: 0.3,
                                          springLength: 95 } } }
            );
            document.getElementById('status').textContent = '';
        })
        .catch(err => {
            document.getElementById('status').textContent =
                'No se pudo conectar a la API (' + err.message + '). ¿Está corriendo java en :8080?';
            console.error(err);
        });
</script>
</body>
</html>
```

---

## 6. `install.sql` — Script Único de Instalación

Tolerante a primera ejecución (sin tablas previas) y a re-ejecuciones (con tablas previas).

```sql
SET DEFINE OFF
SET ECHO ON
SET SERVEROUTPUT ON

PROMPT =======================================================
PROMPT  INSTALACIÓN BD - PROYECTO 2 FLORISTERÍA UMG
PROMPT =======================================================

-- Drop tolerante: ignora ORA-00942 (tabla no existe) y ORA-02289 (sequence no existe)
BEGIN
    FOR t IN (SELECT 'DROP TABLE ' || table_name || ' CASCADE CONSTRAINTS PURGE' AS sql
              FROM   user_tables
              WHERE  table_name IN ('DETALLE_FACTURA','FACTURA','ITEM_FLORAL',
                                    'PROVEEDOR_ORIGEN','CLIENTE','TIPO_CLIENTE'))
    LOOP
        EXECUTE IMMEDIATE t.sql;
    END LOOP;

    FOR s IN (SELECT 'DROP SEQUENCE ' || sequence_name AS sql
              FROM   user_sequences
              WHERE  sequence_name LIKE 'SEQ\_%' ESCAPE '\')
    LOOP
        EXECUTE IMMEDIATE s.sql;
    END LOOP;
END;
/

PROMPT --- Creando esquema (ver Sección 1 para DDL completo) ---
-- << pegar aquí el DDL completo de la Sección 1 >>

PROMPT --- Sembrando catálogos ---
INSERT INTO TIPO_CLIENTE VALUES (1, 'Minorista Regular',  0.00);
INSERT INTO TIPO_CLIENTE VALUES (2, 'Mayorista Alianzas', 10.50);
INSERT INTO TIPO_CLIENTE VALUES (3, 'Eventos Premium',     5.00);
INSERT INTO TIPO_CLIENTE VALUES (4, 'Floristería Afiliada', 15.00);

INSERT INTO PROVEEDOR_ORIGEN VALUES (5, 'Finca Países Bajos S.A.',  'Holanda');
INSERT INTO PROVEEDOR_ORIGEN VALUES (6, 'Floricola Quiteña',        'Ecuador');
INSERT INTO PROVEEDOR_ORIGEN VALUES (7, 'Cooperativa Antigua',      'Guatemala');

INSERT INTO CLIENTE VALUES (101, '458921-5', 'Alejandro Sian',     'Chimaltenango, Guatemala', 2);
INSERT INTO CLIENTE VALUES (102, '770115-K', 'Eventos Sky S.A.',   'Zona 10, Guatemala',       3);

INSERT INTO ITEM_FLORAL VALUES (50, 'Tulipanes Holandeses Premium', 25.00, 5);
INSERT INTO ITEM_FLORAL VALUES (51, 'Rosas Ecuatorianas Long Stem', 18.50, 6);
INSERT INTO ITEM_FLORAL VALUES (52, 'Arreglo Funerario Estándar',   80.00, 7);

INSERT INTO FACTURA (id_factura, fecha_emision, id_cliente, serie, numero_documento)
VALUES (2001, TO_DATE('2024-05-12','YYYY-MM-DD'), 101, 'A', 1023);
INSERT INTO FACTURA (id_factura, fecha_emision, id_cliente, serie, numero_documento)
VALUES (2002, TO_DATE('2025-02-14','YYYY-MM-DD'), 102, 'A', 1024);

INSERT INTO DETALLE_FACTURA VALUES (1, 2001, 50, 12, 300.00);
INSERT INTO DETALLE_FACTURA VALUES (2, 2002, 51, 50, 925.00);

COMMIT;

PROMPT =======================================================
PROMPT  Instalación completa.
PROMPT =======================================================
```

---

## 7. Checklist de Cumplimiento de Rúbrica

- [ ] 6 entidades con normalización 3FN y FKs correctas.
- [ ] Tabla `FACTURA` particionada por año (rango 2024-2026).
- [ ] `SEQUENCE`, índices y `CHECK` constraints en su lugar.
- [ ] Tabla hash propia (sin `java.util.HashMap`) con conteo de colisiones y tiempo en ns.
- [ ] Tres endpoints relacionales (trazabilidad / cliente-productos / proveedor-impacto).
- [ ] Visualización web autónoma con `vis-network`.
- [ ] Script `install.sql` idempotente.
- [ ] `pom.xml` con `ojdbc11` y Java 25 configurado.
