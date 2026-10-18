-- =====================================================================
-- 01. База данных и роли.
-- Запускать под суперпользователем postgres, подключившись к базе postgres:
--   psql -U postgres -h localhost -d postgres -v app_password=ПАРОЛЬ1 -v readonly_password=ПАРОЛЬ2 -f db/01_create_db_and_roles.sql
-- Пароли передаются параметрами и в файле НЕ хранятся.
--
-- Три роли с разными наборами привилегий:
--   city_owner    — владелец таблиц, создаёт схему (вход запрещён);
--   city_app      — приложение: чтение и изменение строк (SELECT/INSERT/UPDATE/DELETE);
--   city_readonly — отчёты и аналитика: только чтение, без персональных данных жителей.
-- =====================================================================
\set ON_ERROR_STOP on
-- файлы в UTF-8 (на Windows psql иначе читает их в кодировке консоли)
\encoding UTF8

-- city_owner — владелец базы и таблиц. Войти под ним нельзя (NOLOGIN),
-- им пользуется только администратор через SET ROLE при создании схемы.
SELECT 'CREATE ROLE city_owner NOLOGIN'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'city_owner')
\gexec

-- city_app — пользователь, под которым работает приложение.
-- Не суперпользователь, не может создавать базы и роли, не владеет таблицами.
SELECT format('CREATE ROLE city_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT CONNECTION LIMIT 20 PASSWORD %L',
              :'app_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'city_app')
\gexec

-- city_readonly — пользователь для отчётов/аналитики: только чтение.
SELECT format('CREATE ROLE city_readonly LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT CONNECTION LIMIT 5 PASSWORD %L',
              :'readonly_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'city_readonly')
\gexec

-- при повторном запуске просто обновляем пароли
ALTER ROLE city_app WITH PASSWORD :'app_password';
ALTER ROLE city_readonly WITH PASSWORD :'readonly_password';

SELECT 'CREATE DATABASE city_reports OWNER city_owner ENCODING ''UTF8'' TEMPLATE template0'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'city_reports')
\gexec

-- По умолчанию подключаться к новой базе может любая роль (PUBLIC). Убираем это.
REVOKE ALL ON DATABASE city_reports FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE city_reports TO city_app;
GRANT CONNECT ON DATABASE city_reports TO city_readonly;
