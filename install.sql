-- =====================================================================
--  Proyecto 2 - Floristería UMG
--  Script único de instalación para Oracle Database
-- ---------------------------------------------------------------------
--  Uso:  sqlplus usuario/clave@MV_UMG @install.sql
--  Idempotente: limpia tablas y sequences previos antes de recrear.
-- =====================================================================

SET DEFINE OFF
SET ECHO ON
SET SERVEROUTPUT ON

PROMPT =======================================================
PROMPT  INSTALACION BD - PROYECTO 2 FLORISTERIA UMG
PROMPT =======================================================

-- ---------------------------------------------------------------------
--  Limpieza idempotente: ignora ORA-00942 (tabla no existe)
--  y ORA-02289 (sequence no existe).
-- ---------------------------------------------------------------------
BEGIN
    FOR t IN (SELECT 'DROP TABLE ' || table_name
                     || ' CASCADE CONSTRAINTS PURGE' AS ddl
              FROM   user_tables
              WHERE  table_name IN ('DETALLE_FACTURA','FACTURA','ITEM_FLORAL',
                                    'PROVEEDOR_ORIGEN','CLIENTE','TIPO_CLIENTE'))
    LOOP
        EXECUTE IMMEDIATE t.ddl;
    END LOOP;

    FOR s IN (SELECT 'DROP SEQUENCE ' || sequence_name AS ddl
              FROM   user_sequences
              WHERE  sequence_name LIKE 'SEQ\_%' ESCAPE '\')
    LOOP
        EXECUTE IMMEDIATE s.ddl;
    END LOOP;
END;
/

PROMPT --- Creando esquema ---

-- 1. Catálogo: TIPO_CLIENTE
CREATE TABLE TIPO_CLIENTE (
    id_tipo_cliente NUMBER(10) PRIMARY KEY,
    nombre_tipo     VARCHAR2(50)  NOT NULL UNIQUE,
    descuento_base  NUMBER(5,2)   DEFAULT 0.00 NOT NULL,
    CONSTRAINT ck_tc_descuento CHECK (descuento_base BETWEEN 0 AND 100)
);

-- 2. CLIENTE (atributo "nombre_completo" para alinearse con CommercialGraph)
CREATE TABLE CLIENTE (
    id_cliente       NUMBER(10) PRIMARY KEY,
    nit              VARCHAR2(15)  NOT NULL UNIQUE,
    nombre_completo  VARCHAR2(100) NOT NULL,
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

-- 5. FACTURA particionada por rango de año (rúbrica: histórico 2024-2026)
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

PROMPT --- Sembrando catálogos ---

INSERT INTO TIPO_CLIENTE VALUES (1, 'Minorista Regular',     0.00);
INSERT INTO TIPO_CLIENTE VALUES (2, 'Mayorista Alianzas',   10.50);
INSERT INTO TIPO_CLIENTE VALUES (3, 'Eventos Premium',       5.00);
INSERT INTO TIPO_CLIENTE VALUES (4, 'Floristería Afiliada', 15.00);

-- ── Proveedores (4 países) ──────────────────────────────────────────
INSERT INTO PROVEEDOR_ORIGEN VALUES (5, 'Finca Países Bajos S.A.',  'Holanda');
INSERT INTO PROVEEDOR_ORIGEN VALUES (6, 'Floricola Quiteña',        'Ecuador');
INSERT INTO PROVEEDOR_ORIGEN VALUES (7, 'Cooperativa Antigua',      'Guatemala');
INSERT INTO PROVEEDOR_ORIGEN VALUES (8, 'Hacienda Cundinamarca',    'Colombia');

-- ── Clientes (5 clientes de distintos tipos) ────────────────────────
INSERT INTO CLIENTE VALUES (101, '458921-5', 'Alejandro Sian',        'Chimaltenango, Guatemala', 2);
INSERT INTO CLIENTE VALUES (102, '770115-K', 'Eventos Sky S.A.',       'Zona 10, Guatemala',       3);
INSERT INTO CLIENTE VALUES (103, '112233-4', 'Maria Lopez Flores',     'Zona 1, Quetzaltenango',   1);
INSERT INTO CLIENTE VALUES (104, '556677-8', 'Floristeria El Jardin',  'Zona 14, Guatemala',       4);
INSERT INTO CLIENTE VALUES (105, '998877-6', 'Hotel Gran Vista',       'Antigua Guatemala',        3);

-- ── Items florales (10 productos, 4 proveedores) ────────────────────
INSERT INTO ITEM_FLORAL VALUES (50, 'Tulipanes Holandeses Premium', 25.00, 5);
INSERT INTO ITEM_FLORAL VALUES (51, 'Rosas Ecuatorianas Long Stem', 18.50, 6);
INSERT INTO ITEM_FLORAL VALUES (52, 'Arreglo Funerario Estándar',   80.00, 7);
INSERT INTO ITEM_FLORAL VALUES (53, 'Centro de Mesa Mixto',         55.00, 5);
INSERT INTO ITEM_FLORAL VALUES (54, 'Girasoles Colombianos',        12.00, 8);
INSERT INTO ITEM_FLORAL VALUES (55, 'Orquídeas Guatemaltecas',      45.00, 7);
INSERT INTO ITEM_FLORAL VALUES (56, 'Rosas Rojas Premium',          22.00, 5);
INSERT INTO ITEM_FLORAL VALUES (57, 'Lirios del Valle',             16.00, 6);
INSERT INTO ITEM_FLORAL VALUES (58, 'Claveles Colombianos',          8.00, 8);
INSERT INTO ITEM_FLORAL VALUES (59, 'Margaritas Mixtas',             6.50, 7);

-- ── Facturas (10 facturas: 2024, 2025, 2026 para cubrir rango de años) ──
INSERT INTO FACTURA (id_factura, fecha_emision, id_cliente, serie, numero_documento)
VALUES (2001, TO_DATE('2024-05-12','YYYY-MM-DD'), 101, 'A', 1023);
INSERT INTO FACTURA (id_factura, fecha_emision, id_cliente, serie, numero_documento)
VALUES (2002, TO_DATE('2025-02-14','YYYY-MM-DD'), 102, 'A', 1024);
INSERT INTO FACTURA (id_factura, fecha_emision, id_cliente, serie, numero_documento)
VALUES (2003, TO_DATE('2024-03-20','YYYY-MM-DD'), 101, 'A', 1025);
INSERT INTO FACTURA (id_factura, fecha_emision, id_cliente, serie, numero_documento)
VALUES (2004, TO_DATE('2024-08-15','YYYY-MM-DD'), 103, 'A', 1026);
INSERT INTO FACTURA (id_factura, fecha_emision, id_cliente, serie, numero_documento)
VALUES (2005, TO_DATE('2024-11-30','YYYY-MM-DD'), 104, 'B', 2001);
INSERT INTO FACTURA (id_factura, fecha_emision, id_cliente, serie, numero_documento)
VALUES (2006, TO_DATE('2025-01-14','YYYY-MM-DD'), 101, 'A', 1027);
INSERT INTO FACTURA (id_factura, fecha_emision, id_cliente, serie, numero_documento)
VALUES (2007, TO_DATE('2025-04-22','YYYY-MM-DD'), 103, 'A', 1028);
INSERT INTO FACTURA (id_factura, fecha_emision, id_cliente, serie, numero_documento)
VALUES (2008, TO_DATE('2025-06-10','YYYY-MM-DD'), 105, 'B', 2002);
INSERT INTO FACTURA (id_factura, fecha_emision, id_cliente, serie, numero_documento)
VALUES (2009, TO_DATE('2025-09-03','YYYY-MM-DD'), 104, 'B', 2003);
INSERT INTO FACTURA (id_factura, fecha_emision, id_cliente, serie, numero_documento)
VALUES (2010, TO_DATE('2026-02-28','YYYY-MM-DD'), 102, 'A', 1029);

-- ── Detalles (25 líneas, múltiples ítems por factura) ───────────────
-- Factura 2001 – Alejandro, may-2024: tulipanes + rosas + centro de mesa
INSERT INTO DETALLE_FACTURA VALUES ( 1, 2001, 50, 12,  300.00);
INSERT INTO DETALLE_FACTURA VALUES ( 2, 2001, 56,  5,  110.00);
INSERT INTO DETALLE_FACTURA VALUES ( 3, 2001, 53,  2,  110.00);

-- Factura 2002 – Eventos Sky, feb-2025: rosas ecuat. + orquídeas + claveles
INSERT INTO DETALLE_FACTURA VALUES ( 4, 2002, 51, 50,  925.00);
INSERT INTO DETALLE_FACTURA VALUES ( 5, 2002, 55,  3,  135.00);
INSERT INTO DETALLE_FACTURA VALUES ( 6, 2002, 58, 10,   80.00);

-- Factura 2003 – Alejandro, mar-2024: arreglo funerario + girasoles
INSERT INTO DETALLE_FACTURA VALUES ( 7, 2003, 52,  1,   80.00);
INSERT INTO DETALLE_FACTURA VALUES ( 8, 2003, 54,  8,   96.00);

-- Factura 2004 – Maria Lopez, ago-2024: tulipanes + lirios
INSERT INTO DETALLE_FACTURA VALUES ( 9, 2004, 50,  6,  150.00);
INSERT INTO DETALLE_FACTURA VALUES (10, 2004, 57,  4,   64.00);

-- Factura 2005 – El Jardin, nov-2024: rosas ecuat. + orquídeas + margaritas
INSERT INTO DETALLE_FACTURA VALUES (11, 2005, 51, 20,  370.00);
INSERT INTO DETALLE_FACTURA VALUES (12, 2005, 55,  2,   90.00);
INSERT INTO DETALLE_FACTURA VALUES (13, 2005, 59, 30,  195.00);

-- Factura 2006 – Alejandro, ene-2025: rosas premium + claveles
INSERT INTO DETALLE_FACTURA VALUES (14, 2006, 56,  8,  176.00);
INSERT INTO DETALLE_FACTURA VALUES (15, 2006, 58, 15,  120.00);

-- Factura 2007 – Maria Lopez, abr-2025: tulipanes + arreglo funerario
INSERT INTO DETALLE_FACTURA VALUES (16, 2007, 50, 10,  250.00);
INSERT INTO DETALLE_FACTURA VALUES (17, 2007, 52,  1,   80.00);

-- Factura 2008 – Hotel Gran Vista, jun-2025: centro de mesa + girasoles + lirios
INSERT INTO DETALLE_FACTURA VALUES (18, 2008, 53,  3,  165.00);
INSERT INTO DETALLE_FACTURA VALUES (19, 2008, 54,  5,   60.00);
INSERT INTO DETALLE_FACTURA VALUES (20, 2008, 57,  6,   96.00);

-- Factura 2009 – El Jardin, sep-2025: tulipanes + rosas premium + margaritas
INSERT INTO DETALLE_FACTURA VALUES (21, 2009, 50, 15,  375.00);
INSERT INTO DETALLE_FACTURA VALUES (22, 2009, 56,  4,   88.00);
INSERT INTO DETALLE_FACTURA VALUES (23, 2009, 59, 20,  130.00);

-- Factura 2010 – Eventos Sky, feb-2026: orquídeas + claveles
INSERT INTO DETALLE_FACTURA VALUES (24, 2010, 55,  5,  225.00);
INSERT INTO DETALLE_FACTURA VALUES (25, 2010, 58, 25,  200.00);

COMMIT;

PROMPT =======================================================
PROMPT  Instalacion completa.
PROMPT  Tablas: 6    |  Tipos cliente: 4    |  Proveedores: 4
PROMPT  Items: 10    |  Clientes: 5         |  Facturas: 10
PROMPT  Detalles: 25 |  Anos cubiertos: 2024-2026
PROMPT =======================================================

-- ================================================================
-- Procedimiento PL/SQL — Sección 3.1 de la rúbrica
-- Verifica los catálogos y registra conteos + tiempo de lectura
-- Uso desde SQL*Plus:  SET SERVEROUTPUT ON; EXEC SP_VERIFICAR_CATALOGO;
-- ================================================================
CREATE OR REPLACE PROCEDURE SP_VERIFICAR_CATALOGO IS
    v_inicio     NUMBER;
    v_fin        NUMBER;
    v_cnt_item   NUMBER;
    v_cnt_prov   NUMBER;
    v_cnt_tipo   NUMBER;
BEGIN
    v_inicio := DBMS_UTILITY.GET_TIME;   -- centésimas de segundo

    SELECT COUNT(*) INTO v_cnt_item FROM ITEM_FLORAL;
    SELECT COUNT(*) INTO v_cnt_prov FROM PROVEEDOR_ORIGEN;
    SELECT COUNT(*) INTO v_cnt_tipo FROM TIPO_CLIENTE;

    v_fin := DBMS_UTILITY.GET_TIME;

    DBMS_OUTPUT.PUT_LINE('=== SP_VERIFICAR_CATALOGO ===');
    DBMS_OUTPUT.PUT_LINE('ITEM_FLORAL     : ' || v_cnt_item || ' registros');
    DBMS_OUTPUT.PUT_LINE('PROVEEDOR_ORIGEN: ' || v_cnt_prov || ' registros');
    DBMS_OUTPUT.PUT_LINE('TIPO_CLIENTE    : ' || v_cnt_tipo || ' registros');
    DBMS_OUTPUT.PUT_LINE('Tiempo Oracle   : ' || ((v_fin - v_inicio) * 10) || ' ms');
    DBMS_OUTPUT.PUT_LINE('==============================');
END SP_VERIFICAR_CATALOGO;
/

PROMPT Procedimiento SP_VERIFICAR_CATALOGO creado correctamente.
