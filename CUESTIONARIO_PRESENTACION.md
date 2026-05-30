# Cuestionario de Presentación — Proyecto Final Floristería UMG
**Programación III · Sección A · Equipo de 2**

> Documento de estudio para la presentación. Las preguntas están organizadas por
> tema (6.1 Tablas de Hash · 6.2 Grafos) y dentro de cada tema en tres niveles:
> **conceptual**, **implementación** y **demo en vivo**. Cada pregunta lleva
> una respuesta corta (lo que tienes que decir) y, cuando aplica, el
> archivo/línea de referencia para que puedas mostrar el código.

---

## 🎯 Cómo dividir el trabajo

| Integrante | Tema principal | Demo en vivo |
|---|---|---|
| **Integrante A** | 6.1 — Tablas de Hash | Pestaña «Tabla Hash» (cargar / buscar / agregar con colisión) |
| **Integrante B** | 6.2 — Grafos comerciales | Pestaña «Grafos Comerciales» (las 6 consultas + alta de factura) |

Antes de presentar:
1. Tener la VM Oracle **encendida** (sin Oracle los grafos muestran «sin datos»).
2. Levantar el servidor: `mvn exec:java -Pdbfloristeria` en una terminal.
3. Abrir `http://localhost:8085/` en el navegador con DevTools cerrado.
4. Tener este documento abierto en un segundo monitor / celular como respaldo.

---

# 6.1 — Tablas de Hash

## A. Conceptual (lo que el docente puede preguntar de teoría)

### P1. ¿Qué es una tabla hash?
**R:** Una estructura de datos que asocia claves con valores y permite acceso
**en tiempo constante promedio O(1)** aplicando una **función hash** a la clave
para calcular directamente el índice donde está almacenado el valor. Es el
mecanismo detrás de `HashMap`/`dict`/`Map` en casi todos los lenguajes.

### P2. ¿Cómo funciona la función hash que ustedes usan?
**R:** Tomamos `key.hashCode()`, le aplicamos una **máscara de bits**
`& 0x7FFFFFFF` para forzar el signo a positivo, y aplicamos **módulo** sobre
la capacidad actual de la tabla:
```java
int indexFor(K key, int capacity) {
    return (key.hashCode() & 0x7FFFFFFF) % capacity;
}
```
**📍 Archivo:** `src/main/java/umg/edu/gt/floristeria/hash/CustomHashTable.java`, línea ~65.

### P3. ¿Por qué `& 0x7FFFFFFF` y no `Math.abs(...)`?
**R:** Porque `Math.abs(Integer.MIN_VALUE)` devuelve **`Integer.MIN_VALUE`**
(sigue siendo negativo por overflow de complemento a dos). Esto produciría
índices negativos al hacer `% capacity` y rompería la tabla con
`ArrayIndexOutOfBoundsException`. Con la máscara `& 0x7FFFFFFF` apagamos el
bit de signo y siempre obtenemos un entero positivo.
**Demuéstralo:** En `CustomHashTableTest.hashCode_negativoNoLanzaArithmeticException()`
hay una clave `EvilKey` con `hashCode() = Integer.MIN_VALUE` que pasa el test.

### P4. ¿Qué es una colisión?
**R:** Cuando dos claves distintas producen el mismo índice tras aplicar la
función hash. Ejemplo en nuestra tabla: con capacidad 101, las claves `1` y
`102` ambas dan slot `1` (porque `102 % 101 = 1`).

### P5. ¿Cómo resuelven las colisiones?
**R:** Con **encadenamiento separado** (separate chaining): cada slot del
arreglo es la cabeza de una **lista enlazada simple**. Si llegan dos claves al
mismo slot, la segunda se cuelga al final de la cadena. La búsqueda recorre la
cadena comparando claves con `equals`.
**📍 Archivo:** `CustomHashTable.java`, método `put()` líneas ~78-106 y la
clase interna `Node`.

### P6. ¿Cuál es la otra alternativa para resolver colisiones?
**R:** **Direccionamiento abierto** (open addressing): si el slot está
ocupado, se busca el siguiente libre con una estrategia de **probing** (lineal,
cuadrático o doble hashing). Java's `IdentityHashMap` lo usa. Es más eficiente
en uso de memoria pero más complejo y sensible al factor de carga.

### P7. ¿Qué es el factor de carga?
**R:** `factor = size / capacity`. Mide qué tan llena está la tabla. Cuando
crece demasiado, las cadenas se alargan y las búsquedas degradan a **O(n)**.
Por eso disparamos un **rehash** al superar el umbral.

### P8. ¿Cuál es su umbral y qué pasa al superarlo?
**R:** **0.75** (75 %). Al superarlo se dispara `rehash()`:
1. Calcula la siguiente capacidad como el siguiente **número primo ≥ capacidad·2**.
2. Crea un arreglo nuevo de ese tamaño.
3. Re-inserta todas las entradas (re-calculando el índice porque el módulo cambia).
4. Resetea `size` y `collisionCount` para que el nuevo conteo refleje la
   distribución real en la nueva capacidad.

**📍 Archivo:** `CustomHashTable.java`, método `rehash()` líneas ~148-161.

### P9. ¿Por qué la capacidad inicial es un número primo (101)?
**R:** Para **reducir agrupamientos** ante claves con patrones regulares
(p. ej. múltiplos de 10). Si la capacidad fuera 100, todos los IDs múltiplos
de 10 caerían en slots terminados en 0 y se concentrarían en pocos slots.
Con 101 (primo), la distribución es más uniforme.

### P10. ¿Cuál es la complejidad de las operaciones?
| Operación | Caso promedio | Peor caso |
|---|---|---|
| `put(k, v)` | O(1) | O(n) si todo cae en el mismo slot |
| `get(k)` | O(1) | O(n) |
| `containsKey(k)` | O(1) | O(n) |
| `rehash()` (interno) | O(n) | O(n) |

El **peor caso** ocurre con una función hash mala — todos en una sola cadena.

---

## B. Implementación en el proyecto

### P11. ¿Por qué implementaron su propia tabla hash en vez de usar `HashMap`?
**R:** Porque la **rúbrica lo exige** (sección 2 del proyecto): el objetivo
didáctico es demostrar que entendemos cómo funciona internamente. Además,
`HashMap` de Java **no expone** métricas como `collisionCount`,
`chainLengthAt(slot)` ni medición de tiempo por búsqueda — y los reportes 4.1
y 4.2 necesitan exactamente esas métricas.

### P12. ¿Cuántas tablas hash tienen y qué guarda cada una?
**R:** **Tres**, todas instancias de `CustomHashTable<Integer, V>`:
1. **Catálogo** → `CustomHashTable<Integer, ItemFloral>` (productos florales).
2. **Marcas** → `CustomHashTable<Integer, ProveedorOrigen>` (fincas proveedoras).
3. **Tipos de cliente** → `CustomHashTable<Integer, TipoCliente>` (Minorista, Mayorista, Eventos Premium, Floristería Afiliada).

**¿Por qué tres separadas?** Porque la rúbrica 4.2 pide medir el tiempo de
búsqueda de la marca en una **tabla hash independiente** de la del catálogo.

### P13. ¿Qué retorna `get()` además del valor?
**R:** Un **`SearchResult<V>`** (record interno) con 4 campos:
```java
public record SearchResult<T>(T value,
                              int tablePosition,    // slot consultado
                              int probes,           // nodos visitados en la cadena
                              long durationNanoseconds) {}
```
Esto permite **instrumentar** cada búsqueda con métricas reales.

### P14. ¿Cómo miden el tiempo de búsqueda?
**R:** Con `System.nanoTime()` antes y después del recorrido de la cadena. La
duración se reporta en **nanosegundos** internamente y se convierte a unidad
legible (ns / µs / ms / s) con la utilidad `Durations.human(long)`. Esto
satisface la sección 4.1 que exige "tiempo de respuesta del proceso de
búsqueda".

### P15. ¿Qué hace `entries()`?
**R:** Devuelve una lista inmutable de **todas las entradas** en orden
ascendente de slot, con su clave, valor y slot físico. Es lo que usa la GUI
web para dibujar la tabla con sus chips de colores.
**📍 Archivo:** `CustomHashTable.java`, método `entries()` líneas ~223-231.

### P16. ¿De dónde se cargan los datos a las tablas?
**R:** A través de la interfaz **`CatalogoSource`** con dos implementaciones:
- **`SyntheticCatalogoSource`**: genera datos en memoria, sin red. Útil para
  la demo CLI y para pruebas unitarias.
- **`DatabaseCatalogoSource`**: lee Oracle vía JDBC nativo (driver `ojdbc11`).
  Antes de los SELECT principales llama al procedimiento PL/SQL
  `SP_VERIFICAR_CATALOGO()` (sección 3.1 de la rúbrica).

El factory `CatalogoSources.defaultSource()` decide cuál usar según la variable
de entorno `ORACLE_URL`.

### P17. ¿Cómo se detecta una colisión cuando se inserta por POST en la web?
**R:** Después de hacer `catalogoRef.put(id, item)`, llamamos:
- `chainLengthAt(slot)` para saber cuántos elementos hay ahora en el slot.
- Si la clave **no existía** (no es actualización) y `chainLength > 1`,
  declaramos **colisión**.
- Reportamos al frontend: `slot`, `chainLength`, `clavesEnSlot`,
  `collisionDelta` y si hubo `rehash`.

**📍 Archivo:** `api/GraphRestApi.java`, método `agregarItem` líneas ~348-417.

### P18. ¿La actualización de una clave existente cuenta como colisión?
**R:** **No**. El `put()` distingue entre dos casos:
- Clave existente → reemplazo del valor, **no** incrementa `collisionCount`.
- Clave nueva en un slot ya ocupado por otra clave → **sí** incrementa.

Esto es lo que verifica `CustomHashTableTest.put_claveDuplicada_reemplazaValorSinIncrementarColisiones()`.

---

## C. Demo en vivo / preguntas tramposas

### P19. **Demuéstrame una colisión en vivo.**
**Pasos:**
1. Pestaña «Tabla Hash» → seleccionar **Catálogo** → ▶ Cargar.
2. Agregar ítem con `ID=1`, nombre=Rosa, precio=10, idProveedor=5 → ✓ insertado sin colisión.
3. Agregar otro con `ID=102` (mismos datos diferentes en lo demás).
4. Mostrar el banner: «⚠ ¡COLISIÓN! #102 cayó en el slot 1, que ahora encadena 2 elementos».
   - Explicar: `1 % 101 = 1` y `102 % 101 = 1` → ambos caen en slot 1.

### P20. **Demuéstrame un rehash en vivo.**
**Pasos:**
1. Recargar el catálogo sintético con 75 ítems (ya cerca del umbral).
2. Insertar uno más → banner: «🔄 ¡REHASH! La tabla creció a capacidad N».
3. Mostrar que `collisionCount` se reseteó porque la nueva distribución es distinta.

### P21. **Búscame el ítem con ID 50 y muéstrame las métricas.**
**Pasos:**
1. Caja «Buscar ID» → escribir `50` → 🔍 Buscar.
2. Banner: «🔍 #50 encontrado en el slot X · Y probe(s) · Z ns».
3. La fila del slot X queda **resaltada en amarillo** en la tabla de abajo.

### P22. **¿Qué pasa si busco un ID que no existe?**
**R:** Devuelve `found:false` pero con métricas (`slot`, `probes`, `duración`)
del **intento** de búsqueda. Esto muestra el costo de una búsqueda fallida.

### P23. **¿Y si tu hashCode devuelve siempre el mismo número?**
**R:** Toda inserción colisiona → la tabla degenera en una **lista enlazada
gigante** con búsqueda **O(n)**. Es el escenario que demuestra por qué importa
una buena función hash. Java 8+ mitiga esto en `HashMap` convirtiendo cadenas
muy largas en árboles balanceados; nosotros mantenemos lista enlazada porque
es el caso clásico que enseña la materia.

### P24. **¿Por qué el procedimiento `SP_VERIFICAR_CATALOGO` está en PL/SQL y no en Java?**
**R:** Porque la **rúbrica 3.1** exige demostrar el uso de PL/SQL en la base.
Es un procedimiento simple que cuenta `ITEM_FLORAL`, `PROVEEDOR_ORIGEN` y
`TIPO_CLIENTE` e imprime los conteos con `DBMS_OUTPUT`. Java lo invoca con
`CallableStatement` antes de cargar los datos.

---

# 6.2 — Grafos Comerciales

## A. Conceptual

### P1. ¿Qué es un grafo?
**R:** Una estructura matemática **G = (V, E)** compuesta por un conjunto de
**vértices/nodos (V)** y un conjunto de **aristas (E)** que conectan pares de
nodos. Sirve para modelar **relaciones** entre entidades.

### P2. ¿Qué tipo de grafo usan ustedes?
**R:** Un grafo **dirigido y etiquetado**:
- **Dirigido**: cada arista tiene origen → destino (no es simétrica).
- **Etiquetado**: cada arista tiene una etiqueta de relación (`COMPRO_EN`,
  `CONTIENE`, `PROVIENE_DE`, `SUMINISTRA`, `APARECE_EN`, `PERTENECE_A`,
  `COMPRADA_POR`, `CLASIFICA`).

### P3. ¿Cómo lo representan en memoria?
**R:** Con dos listas separadas:
```java
List<Nodo>   nodos   = new ArrayList<>();
List<Arista> aristas = new ArrayList<>();
```
- `Nodo` es un objeto con `id`, `label`, `tipo` (CLIENTE/FACTURA/ITEM/PROVEEDOR/TIPO).
- `Arista` es un objeto con `origenId`, `destinoId`, `relacion`.

Es una **lista de aristas** (edge list), no una matriz de adyacencia. Se eligió
porque el grafo es **disperso** (cada cliente conecta con pocas facturas) y la
matriz desperdiciaría espacio.

### P4. ¿Qué otras representaciones existen?
| Representación | Espacio | Comprobación arista | Iterar vecinos |
|---|---|---|---|
| Matriz de adyacencia | O(V²) | O(1) | O(V) |
| Lista de adyacencia | O(V+E) | O(grado) | O(grado) |
| Lista de aristas (la nuestra) | O(E) | O(E) | O(E) |

La nuestra es la más simple y perfecta para **serializar a JSON** y pasar a
Vis.js, que es el caso de uso del proyecto.

### P5. ¿Qué recorridos clásicos hay sobre un grafo?
**R:** **BFS** (búsqueda en anchura, con cola, encuentra el camino más corto en
grafos no ponderados) y **DFS** (búsqueda en profundidad, con pila/recursión,
útil para detectar ciclos y para ordenamiento topológico). Nosotros no
implementamos recorridos algorítmicos porque las consultas se resuelven en SQL
con JOINs.

---

## B. Implementación en el proyecto

### P6. ¿Por qué un grafo y no solo una tabla?
**R:** Porque las relaciones del negocio son **multi-nivel y entrelazadas**:
un cliente compra varias facturas; cada factura contiene varios ítems; cada
ítem viene de un proveedor; un ítem puede aparecer en muchas facturas (de
distintos clientes). Un grafo expresa estas conexiones de manera natural y
permite responder preguntas como **"¿qué proveedores afectan a este cliente?"**
recorriendo aristas, no haciendo JOINs mentalmente.

### P7. ¿De dónde salen los datos del grafo?
**R:** De **consultas JDBC a Oracle**. Cada método `construirGrafoXxx(...)`
ejecuta un `SELECT` con `JOIN`s, y por cada fila del `ResultSet` crea
nodos y aristas. **📍 Archivo:** `graph/CommercialGraph.java`.

### P8. ¿Cómo evitan nodos duplicados?
**R:** El método `agregarNodo()` **deduplica por id** antes de añadir:
```java
public void agregarNodo(String id, String label, String tipo) {
    if (nodos.stream().noneMatch(n -> n.getId().equals(id))) {
        nodos.add(new Nodo(id, label, tipo));
    }
}
```
Esto evita que un cliente que compra 5 ítems aparezca 5 veces como nodo.
**Las aristas SÍ se duplican** intencionalmente — un cliente que compra dos
veces el mismo ítem genera dos aristas (refleja la multiplicidad).

### P9. ¿Por qué los IDs llevan prefijo (`CLI_`, `FAC_`, `ITM_`, `PRV_`, `TIP_`)?
**R:** Para **evitar colisiones entre namespaces**. El cliente 101 y la
factura 101 no son lo mismo, pero ambos son `101` en sus tablas. Sin prefijo
serían el mismo nodo. Con prefijo: `CLI_101 ≠ FAC_101 ≠ ITM_101`.

### P10. ¿Por qué Vis.js para visualizar y no JavaFX?
**R:** Porque la rúbrica pide una **visualización web** (sección 5) además de
la GUI de escritorio. Vis.js es una librería JavaScript especializada en
grafos interactivos: layout automático con física simulada, zoom, pan,
selección de nodos, colores y formas por grupo. Reinventarlo en JavaFX habría
duplicado trabajo. Además, los reportes y métricas del backend se exponen
vía la misma API REST.

### P11. ¿Cómo viaja el grafo del backend al frontend?
**R:** El servidor REST nativo (`com.sun.net.httpserver.HttpServer`) serializa
los nodos y aristas a JSON con el formato exacto que Vis.js consume:
```json
{
  "nodes": [{"id":"CLI_1","label":"Eventos Sky","group":"CLIENTE"}, ...],
  "edges": [{"from":"CLI_1","to":"FAC_1","label":"COMPRO_EN"}, ...]
}
```
El frontend hace `fetch('/api/grafo/...')` y le pasa el JSON a `new vis.Network(container, data, options)`.

### P12. ¿Qué pasa si Oracle no está disponible?
**R:** El constructor de `CommercialGraph` lee `ORACLE_URL` una sola vez. Si
no está configurado, el método helper `oracleAusente(contexto)` devuelve `true`
y cada `construirGrafoXxx` retorna **con el grafo vacío** sin lanzar
excepción. El frontend muestra «🌿 Sin datos. Verifica que Oracle esté
activo». **Esto es lo que verifica `CommercialGraphUnitTest`**: sin Oracle,
todos los métodos producen `nodos.isEmpty() && aristas.isEmpty()`.

---

## C. Las 6 consultas de grafo

Cada consulta responde **una pregunta del negocio**. Memorizar cuál hace qué.

### P13. ¿Cuáles son las 6 consultas?

| # | Consulta | Pregunta que responde | Método |
|---|---|---|---|
| 1 | **Trazabilidad de Factura** | "Dada esta factura, ¿quién la compró, qué ítems contiene y de dónde vienen?" | `construirGrafoTrazabilidad(idFactura)` |
| 2 | **Productos de Cliente** | "¿Qué ha comprado este cliente y de qué proveedores?" | `construirGrafoClienteProductos(idCliente)` |
| 3 | **Impacto de Proveedor** | "Si este proveedor falla, ¿qué ítems, facturas y clientes se ven afectados?" | `construirGrafoProveedorImpacto(idProveedor)` |
| 4 | **Productos Cliente por Año** | "¿Qué compró este cliente entre los años X y Y?" | `construirGrafoClienteProductosPorAnio(id, ini, fin)` |
| 5 | **Trazabilidad Inversa** | "Dado este ítem, ¿en qué facturas aparece y quiénes lo compraron?" | `construirGrafoTrazabilidadInversa(idItem)` |
| 6 | **Tipo de Cliente → Clientes** | "¿Qué clientes pertenecen a este tipo (Mayorista, Eventos Premium, etc.)?" | `construirGrafoTipoClientes(idTipo)` |

### P14. ¿Qué hace exactamente la consulta 1 (Trazabilidad)?
**R:** Recibe un `idFactura`. Ejecuta un `JOIN` entre
`FACTURA × CLIENTE × DETALLE_FACTURA × ITEM_FLORAL × PROVEEDOR_ORIGEN` filtrado
por `f.id_factura = ?`. Por cada fila crea 4 nodos (cliente, factura, ítem,
proveedor) y 3 aristas:
- `CLIENTE --COMPRO_EN--> FACTURA`
- `FACTURA --CONTIENE--> ITEM`
- `ITEM --PROVIENE_DE--> PROVEEDOR`

Visualmente parece una cadena Cliente→Factura→{Ítem→Proveedor}.

### P15. ¿Por qué hay una consulta "por año" separada?
**R:** La rúbrica 3.3 pide explícitamente **"productos comprados por un
cliente en un rango de años"**. Usamos `EXTRACT(YEAR FROM f.fecha_emision)
BETWEEN ? AND ?` en el WHERE para filtrar.

### P16. ¿Qué hace la consulta de **Trazabilidad Inversa**?
**R:** Es el complemento de la consulta 1. En vez de empezar por una factura,
empieza por un **ítem** y rastrea **hacia atrás**: en qué facturas apareció y
qué clientes lo compraron. Útil para retiros de producto: "vendimos lirios
contaminados — ¿a quién los entregamos?".

### P17. ¿Y la consulta de **Tipo de Cliente**?
**R:** Es la consulta que añadimos al integrar `TIPO_CLIENTE` en tabla hash.
Recibe un `idTipo` (1-4) y dibuja el nodo del tipo (Mayorista, etc.) conectado
por aristas `CLASIFICA` a cada cliente que pertenece a ese tipo. Es la
**visualización de la tercera tabla hash** como grafo.

---

## D. Demo en vivo / preguntas tramposas

### P18. **Muéstrame la trazabilidad de la factura 1.**
**Pasos:**
1. Pestaña «Grafos Comerciales» → consulta «Trazabilidad de Factura» → ID `1` → ▶ Cargar.
2. Aparece un grafo con: nodo azul (cliente) → nodo rojo (factura) → nodos verdes (ítems) → nodos amarillos (proveedores).
3. Explicar la dirección de las flechas y las etiquetas de las aristas.

### P19. **Crea una factura nueva y muéstrame su trazabilidad inmediatamente.**
**Pasos:**
1. En la misma pestaña, abrir el panel «➕ Crear nueva factura».
2. Seleccionar cliente, agregar líneas (ítem + cantidad), botón «Crear factura y ver grafo».
3. Automáticamente cambia la consulta a trazabilidad con el nuevo ID y muestra el grafo de lo recién creado.
4. **Punto bonus**: explicar que el backend hace `INSERT INTO FACTURA + DETALLE_FACTURA` en **una sola transacción** con `conn.setAutoCommit(false)` y `conn.commit()` (sección 3.4).

### P20. **¿Por qué los clientes son círculos azules y las facturas cajas rojas?**
**R:** Por el atributo `group` que se envía con cada nodo. En la configuración
de Vis.js (`VIS_OPTS.groups` en `web/index.html`):
- `CLIENTE` → círculo azul claro
- `FACTURA` → caja roja
- `ITEM` → rombo verde
- `PROVEEDOR` → hexágono amarillo/naranja
- `TIPO` → triángulo morado (el que añadimos)

Es **una sola línea de código por tipo** la que define color y forma.

### P21. **¿Qué pasa si pongo un ID que no existe?**
**R:** El `ResultSet` viene vacío, el método `construirGrafo*` no añade nada,
y el frontend muestra el panel «🌿 Sin datos. Verifica que Oracle esté activo
y que el ID exista».

### P22. **¿Puedes recorrer el grafo con BFS / DFS?**
**R:** En este proyecto **no lo necesitamos** porque las consultas se resuelven
en SQL: el grafo es el **resultado de la consulta**, no el dominio sobre el
que se navega. Si lo necesitáramos (p.ej. para "encontrar el camino más corto
entre cliente A y proveedor B"), implementaríamos BFS sobre la lista de
aristas — pero rompería con el patrón actual donde Oracle decide qué traer.

### P23. **¿Qué hace que un nodo aparezca más grande/más al centro?**
**R:** Vis.js usa una **simulación física** (`barnesHut`): los nodos se repelen
como cargas eléctricas y las aristas actúan como resortes. Tras estabilizarse
(`stabilization.iterations: 200`), el layout converge a una configuración
donde nodos muy conectados quedan en el centro. No es un algoritmo nuestro
sino una funcionalidad de la librería.

### P24. **¿Por qué los reportes Word (.docx) usan los mismos datos del grafo?**
**R:** Porque el reporte 4.3 es la **versión imprimible** del recorrido del
grafo del cliente. El método `ComercioDao.recorridoCliente(idCliente)` ejecuta
una sola query con todos los `JOIN`s necesarios, agrupa los resultados en
estructura `Cliente → Año → Factura → Línea`, mide el tiempo de respuesta,
y `WordReportExporter.grafoCliente(data)` lo convierte a un `.docx` con tablas
formateadas con Apache POI.

---

# 🔥 Preguntas que combinan ambos temas

### PX1. ¿Qué relación hay entre la tabla hash y el grafo?
**R:** La tabla hash es **el catálogo en memoria** (productos, marcas, tipos);
el grafo es la **vista transversal de relaciones comerciales** (qué cliente
compró qué ítem en qué factura). Cuando el frontend pinta un nodo "ITEM 50",
ese ítem está físicamente en la tabla hash del catálogo. La búsqueda del
nombre del ítem cuesta **O(1)** gracias a la tabla hash; sin ella, cada vez
que pintásemos un nodo ítem habría que hacer un SELECT más.

### PX2. ¿Cómo miden ustedes el tiempo de respuesta del grafo?
**R:** En `ComercioDao.recorridoCliente()`:
```java
long t0 = System.nanoTime();
// ... ejecutar query + agrupar resultados ...
long dur = System.nanoTime() - t0;
```
La duración se incluye en el `ReporteGrafoCliente` y se imprime en el .docx
con `Durations.human(...)`. En el frontend, el banner `Tiempo: 47 ms` mide el
ida y vuelta HTTP completo (que incluye servidor + red + parseo JSON).

### PX3. ¿Cuál es la cobertura de tests del proyecto?
**R:** Hay **85 tests** en total:
- 64 unit tests que corren con `mvn test` (siempre verdes, sin Oracle).
- 21 integration tests (`*IT.java`) que corren con `mvn verify -Pdbfloristeria`
  cuando Oracle está arriba.

JaCoCo genera un reporte de cobertura en `target/site/jacoco/index.html`.
La cobertura global de instrucciones sin Oracle es ≈ 70 %; con Oracle sube
significativamente porque se ejercitan `DatabaseCatalogoSource`, `ComercioDao`
y `CommercialGraph`.

### PX4. Si tuvieran que escalar el sistema a 1 millón de clientes y 50 millones de facturas, ¿qué cambiarían?
**R:** (Pregunta de pensamiento crítico, prepárense con):
1. **Tabla hash**: subir la capacidad inicial y monitorear `loadFactor` con
   métricas en producción.
2. **Grafo**: en vez de devolver TODO el grafo en JSON, paginar con cursor
   y materializar solo los nodos visibles en el viewport (lazy loading en Vis.js).
3. **Oracle**: índices en `id_cliente`, `id_factura`, `fecha_emision`. Probable
   conversión de tabla particionada por año.
4. **Caché**: poner Redis delante para las consultas más comunes (clientes
   premium tienen grafo grande).

---

# 📋 Checklist final pre-presentación

- [ ] Oracle VM encendida y accesible en `localhost:1521/umg`
- [ ] `mvn exec:java -Pdbfloristeria` corriendo en una terminal
- [ ] Navegador abierto en `http://localhost:8085/`
- [ ] Probaron al menos una vez: cargar tabla, buscar, agregar, generar reporte
- [ ] Probaron al menos una vez: cada consulta de grafo, crear factura
- [ ] Cada quien sabe qué archivos abrir si el docente pide ver código
- [ ] Tienen este documento accesible (móvil/segundo monitor)
- [ ] Acuerdan quién responde qué: A→Hash, B→Grafos, ambos→preguntas combinadas

**¡Éxito en la presentación!** 🌸
