-- =====================================================================
-- 03. Тестовые данные (согласованные между собой).
--   psql -U postgres -h localhost -d city_reports -f db/03_seed.sql
-- =====================================================================
\set ON_ERROR_STOP on
-- файлы в UTF-8 (на Windows psql иначе читает их в кодировке консоли)
\encoding UTF8

BEGIN;
SET ROLE city_owner;
SET search_path = city;

-- Тестовые учётные записи. В БД лежат только BCrypt-хеши (cost 12).
-- Пароли для учебного стенда (они же — в README и в переменных коллекции Postman):
--   admin      / Admin#City2026   — ADMIN
--   dispatcher / Dispatch#2026    — DISPATCHER
--   ivanov     / Ivanov#2026      — RESIDENT
--   petrova    / Petrova#2026     — RESIDENT
--   sidorov    / Sidorov#2026     — RESIDENT (заблокирован администратором)
-- На реальном сервере такие пароли недопустимы: администратора создают с уникальным паролем.
INSERT INTO users (username, password_hash, role, status, full_name, email, created_at, updated_at) VALUES
    ('admin',      '$2a$12$oREQ72MTYBY7Xdk/xzViZe32zuaAcZ9fwIh2wjBb6FHtFsrgKsUIS', 'ADMIN',      'ACTIVE',
                   'Администратор системы',  'admin@example.com',      now(), now()),
    ('dispatcher', '$2a$12$L.DAI39ZwSQigVr91x3sMeLPQczMWh0TsEe5ustF1arcH.ZLEK7lG', 'DISPATCHER', 'ACTIVE',
                   'Дзуцева Мадина Руслановна', 'dispatcher@example.com', now(), now()),
    ('ivanov',     '$2a$12$pzivsnIs8i3ftqW93r.GeuqkrNKKSN4ep5h94rl7QEv.U6CjvcFGO', 'RESIDENT',   'ACTIVE',
                   'Иванов Иван Иванович',   'ivanov@example.com',     now(), now()),
    ('petrova',    '$2a$12$1nyKBU30TSjpvFcQWnS6yeyxlKuWpluUAtjaKib1ivKPxGZtlTxii', 'RESIDENT',   'ACTIVE',
                   'Петрова Анна Сергеевна', 'petrova@example.com',    now(), now()),
    ('sidorov',    '$2a$12$EFTCjoDi34FqO5gzPN9AnenkgMEY4yBErXwfxwcFGFMQFljb7wVqy', 'RESIDENT',   'BLOCKED',
                   'Сидоров Пётр Олегович',  'sidorov@example.com',    now(), now());

INSERT INTO city_objects (address, type, state, created_at, updated_at) VALUES
    ('ул. Ленина, 10',                  'ROAD',       'NEEDS_REPAIR', now(), now()),  -- новое обращение
    ('Парк Победы',                     'PARK',       'UNDER_REPAIR', now(), now()),  -- идут работы
    ('ул. Мира, 5, детская площадка',   'PLAYGROUND', 'GOOD',         now(), now()),  -- отремонтировано
    ('пр. Коста, 15, фонарь №3',        'LIGHTING',   'DANGEROUS',    now(), now()),  -- новое обращение
    ('ул. Кирова, 3, тротуар',          'SIDEWALK',   'NEEDS_REPAIR', now(), now()),  -- наряд запланирован
    ('ул. Гагарина, 1',                 'BUILDING',   'GOOD',         now(), now());  -- без обращений

INSERT INTO work_crews (name, active_work_limit, created_at, updated_at) VALUES
    ('Дорожная бригада №1', 2, now(), now()),
    ('Бригада озеленения',  1, now(), now()),
    ('Бригада электриков',  3, now(), now());

INSERT INTO work_crew_supported_types (crew_id, object_type)
SELECT c.id, t.type
FROM work_crews c
JOIN (VALUES ('Дорожная бригада №1', 'ROAD'), ('Дорожная бригада №1', 'SIDEWALK'),
             ('Бригада озеленения',  'PARK'), ('Бригада озеленения',  'PLAYGROUND'),
             ('Бригада электриков',  'LIGHTING'), ('Бригада электриков', 'BUILDING')) AS t(crew, type)
  ON t.crew = c.name;

INSERT INTO city_reports (object_id, author_id, description, status, created_at, updated_at)
SELECT o.id, u.id, r.description, r.status, now(), now()
FROM (VALUES
        ('ул. Ленина, 10',                'ivanov',  'Яма на проезжей части у остановки',        'NEW'),
        ('Парк Победы',                   'petrova', 'Сломаны скамейки у фонтана',               'IN_PROGRESS'),
        ('ул. Мира, 5, детская площадка', 'sidorov', 'Сломаны качели',                           'RESOLVED'),
        ('пр. Коста, 15, фонарь №3',      'ivanov',  'Фонарь накренился, оголены провода',       'NEW'),
        ('ул. Кирова, 3, тротуар',        'petrova', 'Разбита тротуарная плитка',                'IN_PROGRESS')
     ) AS r(address, username, description, status)
JOIN city_objects o ON o.address = r.address
JOIN users u        ON u.username = r.username;

INSERT INTO work_orders (report_id, crew_id, deadline, status, result_description,
                         created_at, updated_at, started_at, completed_at)
SELECT rep.id, c.id, current_date + w.days, w.status, w.result,
       now(), now(), w.started, w.completed
FROM (VALUES
        ('Сломаны скамейки у фонтана', 'Бригада озеленения',  7, 'IN_PROGRESS', NULL,
             now() - interval '1 day', NULL::timestamptz),
        ('Сломаны качели',             'Бригада озеленения', 0, 'DONE', 'Качели заменены на новые',
             now() - interval '3 days', now() - interval '1 day'),
        ('Разбита тротуарная плитка',  'Дорожная бригада №1', 10, 'PLANNED', NULL,
             NULL::timestamptz, NULL::timestamptz)
     ) AS w(description, crew, days, status, result, started, completed)
JOIN city_reports rep ON rep.description = w.description
JOIN work_crews c     ON c.name = w.crew;

RESET ROLE;
COMMIT;
