-- =====================================================================
-- 02. Схема: таблицы, связи, ограничения целостности и права.
-- Запускать под postgres, подключившись к базе city_reports:
--   psql -U postgres -h localhost -d city_reports -f db/02_schema.sql
-- ВНИМАНИЕ: скрипт пересоздаёт схему city — все данные будут удалены.
-- =====================================================================
\set ON_ERROR_STOP on
-- файлы в UTF-8 (на Windows psql иначе читает их в кодировке консоли)
\encoding UTF8

BEGIN;

-- Все объекты создаются от имени владельца city_owner, а не суперпользователя.
SET ROLE city_owner;

DROP SCHEMA IF EXISTS city CASCADE;
CREATE SCHEMA city;

-- ---------------------------------------------------------------------
-- Пользователи (учётные записи): жители, диспетчеры, администраторы
-- ---------------------------------------------------------------------
CREATE TABLE city.users (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username               VARCHAR(32)  NOT NULL,
    -- только BCrypt-хеш пароля; открытый пароль в БД не хранится
    password_hash          VARCHAR(100) NOT NULL,
    -- по умолчанию минимальные права — житель
    role                   VARCHAR(20)  NOT NULL DEFAULT 'RESIDENT',
    status                 VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    -- защита от перебора паролей: счётчик неудачных входов и время окончания временной блокировки
    failed_login_attempts  INTEGER      NOT NULL DEFAULT 0,
    locked_until           TIMESTAMPTZ,
    full_name              VARCHAR(100) NOT NULL,
    email                  VARCHAR(254) NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL,
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_users_username  CHECK (username ~ '^[A-Za-z0-9_.-]{3,32}$'),
    CONSTRAINT chk_users_full_name CHECK (btrim(full_name) <> ''),
    CONSTRAINT chk_users_email     CHECK (email ~ '^[^@\s]+@[^@\s]+$'),
    -- даже при ошибке в коде БД не примет пароль в открытом виде: только формат BCrypt ($2a$12$...)
    CONSTRAINT chk_users_password_bcrypt CHECK (password_hash ~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'),
    CONSTRAINT chk_users_role      CHECK (role IN ('RESIDENT','DISPATCHER','ADMIN')),
    CONSTRAINT chk_users_status    CHECK (status IN ('ACTIVE','BLOCKED')),
    CONSTRAINT chk_users_attempts  CHECK (failed_login_attempts >= 0)
);
-- логин уникален без учёта регистра: Ivanov и ivanov — один и тот же пользователь
CREATE UNIQUE INDEX ux_users_username_lower ON city.users (lower(username));

-- ---------------------------------------------------------------------
-- Городские объекты
-- ---------------------------------------------------------------------
CREATE TABLE city.city_objects (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    address     VARCHAR(255) NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    state       VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_objects_address CHECK (btrim(address) <> ''),
    CONSTRAINT chk_objects_type    CHECK (type  IN ('ROAD','SIDEWALK','PARK','PLAYGROUND','LIGHTING','BUILDING','OTHER')),
    CONSTRAINT chk_objects_state   CHECK (state IN ('GOOD','NEEDS_REPAIR','UNDER_REPAIR','DANGEROUS'))
);

-- ---------------------------------------------------------------------
-- Обращения: многие к одному объекту и одному автору
-- ---------------------------------------------------------------------
CREATE TABLE city.city_reports (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    object_id         BIGINT        NOT NULL REFERENCES city.city_objects (id) ON DELETE RESTRICT,
    author_id         BIGINT        NOT NULL REFERENCES city.users (id)        ON DELETE RESTRICT,
    description       VARCHAR(2000) NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    rejection_reason  VARCHAR(1000),
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_reports_description CHECK (btrim(description) <> ''),
    CONSTRAINT chk_reports_status      CHECK (status IN ('NEW','IN_PROGRESS','RESOLVED','REJECTED')),
    -- причина указана тогда и только тогда, когда обращение отклонено
    CONSTRAINT chk_reports_rejection   CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL))
);
CREATE INDEX ix_reports_object ON city.city_reports (object_id);
CREATE INDEX ix_reports_author ON city.city_reports (author_id);

-- ---------------------------------------------------------------------
-- Бригады и поддерживаемые ими типы объектов (связь "один ко многим")
-- ---------------------------------------------------------------------
CREATE TABLE city.work_crews (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name               VARCHAR(100) NOT NULL,
    active_work_limit  INTEGER      NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_crews_name  CHECK (btrim(name) <> ''),
    CONSTRAINT chk_crews_limit CHECK (active_work_limit BETWEEN 1 AND 100)
);

CREATE TABLE city.work_crew_supported_types (
    crew_id      BIGINT      NOT NULL REFERENCES city.work_crews (id) ON DELETE CASCADE,
    object_type  VARCHAR(20) NOT NULL,
    CONSTRAINT pk_crew_types PRIMARY KEY (crew_id, object_type),
    CONSTRAINT chk_crew_types_type CHECK (object_type IN ('ROAD','SIDEWALK','PARK','PLAYGROUND','LIGHTING','BUILDING','OTHER'))
);

-- ---------------------------------------------------------------------
-- Наряды: многие к одному обращению и одной бригаде
-- ---------------------------------------------------------------------
CREATE TABLE city.work_orders (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_id           BIGINT        NOT NULL REFERENCES city.city_reports (id) ON DELETE RESTRICT,
    crew_id             BIGINT        NOT NULL REFERENCES city.work_crews (id)   ON DELETE RESTRICT,
    deadline            DATE          NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    result_description  VARCHAR(2000),
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_orders_status   CHECK (status IN ('PLANNED','IN_PROGRESS','DONE','CANCELLED')),
    -- в работе или выполнен -> известно время начала
    CONSTRAINT chk_orders_started  CHECK (status NOT IN ('IN_PROGRESS','DONE') OR started_at IS NOT NULL),
    -- выполнен -> есть результат и время завершения
    CONSTRAINT chk_orders_done     CHECK (status <> 'DONE'
                                          OR (result_description IS NOT NULL AND completed_at IS NOT NULL)),
    CONSTRAINT chk_orders_dates    CHECK (completed_at IS NULL OR completed_at >= started_at)
);
CREATE INDEX ix_orders_report ON city.work_orders (report_id);
CREATE INDEX ix_orders_crew   ON city.work_orders (crew_id);
-- у одного обращения не может быть двух активных нарядов одновременно
CREATE UNIQUE INDEX ux_orders_one_active_per_report
    ON city.work_orders (report_id) WHERE status IN ('PLANNED','IN_PROGRESS');

RESET ROLE;

-- ---------------------------------------------------------------------
-- Права пользователя приложения (принцип минимальных привилегий):
-- только чтение и изменение строк. Нет прав на CREATE/ALTER/DROP/TRUNCATE,
-- нельзя менять структуру таблиц и создавать свои объекты.
-- ---------------------------------------------------------------------
REVOKE ALL ON SCHEMA city FROM PUBLIC;
GRANT USAGE ON SCHEMA city TO city_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA city TO city_app;

-- ---------------------------------------------------------------------
-- Права пользователя отчётов city_readonly: только чтение.
-- Персональные данные жителей (ФИО, email) и хеши паролей ему недоступны —
-- права на таблицу users выданы только на отдельные столбцы.
-- ---------------------------------------------------------------------
GRANT USAGE ON SCHEMA city TO city_readonly;
GRANT SELECT ON city.city_objects, city.city_reports, city.work_crews,
                city.work_crew_supported_types, city.work_orders TO city_readonly;
GRANT SELECT (id, username, created_at) ON city.users TO city_readonly;

-- в схеме public city_app и city_readonly тоже ничего создать не смогут
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- приложение по умолчанию ищет таблицы в схеме city
ALTER ROLE city_app IN DATABASE city_reports SET search_path = city;
ALTER ROLE city_readonly IN DATABASE city_reports SET search_path = city;
-- отчётные запросы не должны случайно что-то менять: транзакции по умолчанию только на чтение
ALTER ROLE city_readonly IN DATABASE city_reports SET default_transaction_read_only = on;

COMMIT;
