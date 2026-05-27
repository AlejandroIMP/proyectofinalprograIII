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
                     || ' CASCADE CONSTRAINTS PURGE' AS sql
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

INSERT INTO PROVEEDOR_ORIGEN VALUES (5, 'Finca Países Bajos S.A.',  'Holanda');
INSERT INTO PROVEEDOR_ORIGEN VALUES (6, 'Floricola Quiteña',        'Ecuador');
INSERT INTO PROVEEDOR_ORIGEN VALUES (7, 'Cooperativa Antigua',      'Guatemala');

INSERT INTO CLIENTE VALUES (101, '458921-5', 'Alejandro Sian',   'Chimaltenango, Guatemala', 2);
INSERT INTO CLIENTE VALUES (102, '770115-K', 'Eventos Sky S.A.', 'Zona 10, Guatemala',       3);

INSERT INTO ITEM_FLORAL VALUES (50, 'Tulipanes Holandeses Premium', 25.00, 5);
INSERT INTO ITEM_FLORAL VALUES (51, 'Rosas Ecuatorianas Long Stem', 18.50, 6);
INSERT INTO ITEM_FLORAL VALUES (52, 'Arreglo Funerario Estándar',   80.00, 7);
INSERT INTO ITEM_FLORAL VALUES (53, 'Centro de Mesa Mixto',         55.00, 5);

INSERT INTO FACTURA (id_factura, fecha_emision, id_cliente, serie, numero_documento)
VALUES (2001, TO_DATE('2024-05-12','YYYY-MM-DD'), 101, 'A', 1023);
INSERT INTO FACTURA (id_factura, fecha_emision, id_cliente, serie, numero_documento)
VALUES (2002, TO_DATE('2025-02-14','YYYY-MM-DD'), 102, 'A', 1024);

INSERT INTO DETALLE_FACTURA VALUES (1, 2001, 50, 12, 300.00);
INSERT INTO DETALLE_FACTURA VALUES (2, 2002, 51, 50, 925.00);

COMMIT;

PROMPT =======================================================
PROMPT  Instalacion completa.
PROMPT  Tablas: 6  |  Tipos cliente: 4  |  Proveedores: 3
PROMPT  Items: 4   |  Clientes: 2       |  Facturas: 2
PROMPT =======================================================
