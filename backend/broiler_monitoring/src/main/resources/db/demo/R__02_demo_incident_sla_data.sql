-- Демо-данные для SLA-аналитики инцидентов.
-- 18 инцидентов за последние 30 дней, из них 10 за последние 7 дней.
-- 15 со started_at (12 <= 30 мин = met, 3 > 30 мин = breached),
-- 2 без started_at с возрастом <= 30 мин = pending,
-- 1 без started_at с возрастом > 30 мин = breached.
-- 9 CLOSED с closed_at.
-- Префикс INC-DEMO-SLA — не удаляется V15 (V15 удаляет всё, что не INC-DEMO-%).
-- Скрипт идемпотентный: повторный прогон не создаёт дублей.

-- ── 7-дневное окно (записи 001–010) ──────────────────────────────────────────

INSERT INTO incidents (
    id, code, title, description, status, priority, source, incident_type,
    workshop, house, zone, created_at, detected_at, updated_at,
    started_at, reaction_minutes, resolved_at, closed_at
) VALUES

-- 001) CLOSED, реакция 18 мин
('aaaa0001-0000-4000-8000-000000000001', 'INC-DEMO-SLA-001',
 'Перегрев зоны посадки', 'Температура выше нормы 18 минут.',
 'CLOSED', 'CRITICAL', 'SYSTEM', 'MICROCLIMATE',
 'Бройлерный цех № 1', 'Птичник 1-01', 'Зона посадки',
 NOW() - INTERVAL '6 days',
 NOW() - INTERVAL '6 days',
 NOW() - INTERVAL '6 days' + INTERVAL '6 hours 40 minutes',
 NOW() - INTERVAL '6 days' + INTERVAL '18 minutes',
 18,
 NOW() - INTERVAL '6 days' + INTERVAL '4 hours',
 NOW() - INTERVAL '6 days' + INTERVAL '6 hours 40 minutes'),

-- 002) CLOSED, реакция 22 мин
('aaaa0002-0000-4000-8000-000000000002', 'INC-DEMO-SLA-002',
 'Снижение освещённости', 'Освещение ниже нормы в зоне 2.',
 'CLOSED', 'HIGH', 'SENSOR', 'LIGHTING_ILLUMINANCE_LOW',
 'Бройлерный цех № 1', 'Птичник 1-02', 'Зона 2',
 NOW() - INTERVAL '5 days',
 NOW() - INTERVAL '5 days',
 NOW() - INTERVAL '5 days' + INTERVAL '5 hours 20 minutes',
 NOW() - INTERVAL '5 days' + INTERVAL '22 minutes',
 22,
 NOW() - INTERVAL '5 days' + INTERVAL '3 hours',
 NOW() - INTERVAL '5 days' + INTERVAL '5 hours 20 minutes'),

-- 003) CLOSED, реакция 25 мин
('aaaa0003-0000-4000-8000-000000000003', 'INC-DEMO-SLA-003',
 'Падение потребления воды', 'Просадка по линии поения.',
 'CLOSED', 'HIGH', 'ANALYTICS', 'WATER_SUPPLY',
 'Бройлерный цех № 1', 'Птичник 1-03', 'Линия поения 1',
 NOW() - INTERVAL '4 days',
 NOW() - INTERVAL '4 days',
 NOW() - INTERVAL '4 days' + INTERVAL '8 hours',
 NOW() - INTERVAL '4 days' + INTERVAL '25 minutes',
 25,
 NOW() - INTERVAL '4 days' + INTERVAL '5 hours',
 NOW() - INTERVAL '4 days' + INTERVAL '8 hours'),

-- 004) CLOSED, реакция 12 мин
('aaaa0004-0000-4000-8000-000000000004', 'INC-DEMO-SLA-004',
 'Рост падежа', 'Падеж выше планового.',
 'CLOSED', 'CRITICAL', 'SYSTEM', 'FLOCK_HEALTH',
 'Бройлерный цех № 2', 'Птичник 2-01', 'Основной зал',
 NOW() - INTERVAL '3 days',
 NOW() - INTERVAL '3 days',
 NOW() - INTERVAL '3 days' + INTERVAL '5 hours 40 minutes',
 NOW() - INTERVAL '3 days' + INTERVAL '12 minutes',
 12,
 NOW() - INTERVAL '3 days' + INTERVAL '4 hours',
 NOW() - INTERVAL '3 days' + INTERVAL '5 hours 40 minutes'),

-- 005) CLOSED, реакция 28 мин
('aaaa0005-0000-4000-8000-000000000005', 'INC-DEMO-SLA-005',
 'Загрязнение подстилки', 'Повышенная влажность подстилки.',
 'CLOSED', 'MEDIUM', 'MANUAL', 'SANITATION',
 'Бройлерный цех № 1', 'Птичник 1-01', 'Зона отдыха',
 NOW() - INTERVAL '3 days',
 NOW() - INTERVAL '3 days',
 NOW() - INTERVAL '3 days' + INTERVAL '11 hours 20 minutes',
 NOW() - INTERVAL '3 days' + INTERVAL '28 minutes',
 28,
 NOW() - INTERVAL '3 days' + INTERVAL '6 hours',
 NOW() - INTERVAL '3 days' + INTERVAL '11 hours 20 minutes'),

-- 006) IN_PROGRESS, реакция 15 мин
('aaaa0006-0000-4000-8000-000000000006', 'INC-DEMO-SLA-006',
 'Отклонение кормления', 'Расход корма выше нормы.',
 'IN_PROGRESS', 'HIGH', 'ANALYTICS', 'FEEDING',
 'Бройлерный цех № 1', 'Птичник 1-02', 'Кормовая линия',
 NOW() - INTERVAL '2 days',
 NOW() - INTERVAL '2 days',
 NOW() - INTERVAL '2 days' + INTERVAL '15 minutes',
 NOW() - INTERVAL '2 days' + INTERVAL '15 minutes',
 15,
 NULL, NULL),

-- 007) OPEN, без started_at, возраст 20 мин → pending
('aaaa0007-0000-4000-8000-000000000007', 'INC-DEMO-SLA-007',
 'Аммиак приближается к границе', 'Третий замер подряд растёт.',
 'OPEN', 'MEDIUM', 'SENSOR', 'MICROCLIMATE',
 'Бройлерный цех № 2', 'Птичник 2-01', 'Зона вентиляции',
 NOW() - INTERVAL '20 minutes',
 NOW() - INTERVAL '20 minutes',
 NOW() - INTERVAL '20 minutes',
 NULL, NULL, NULL, NULL),

-- 008) CLOSED, реакция 31 мин → breached
('aaaa0008-0000-4000-8000-000000000008', 'INC-DEMO-SLA-008',
 'Скачок температуры', 'Температура выросла за 31 минуту.',
 'CLOSED', 'HIGH', 'SENSOR', 'MICROCLIMATE',
 'Бройлерный цех № 1', 'Птичник 1-03', 'Зона посадки',
 NOW() - INTERVAL '2 days',
 NOW() - INTERVAL '2 days',
 NOW() - INTERVAL '2 days' + INTERVAL '7 hours',
 NOW() - INTERVAL '2 days' + INTERVAL '31 minutes',
 31,
 NOW() - INTERVAL '2 days' + INTERVAL '5 hours',
 NOW() - INTERVAL '2 days' + INTERVAL '7 hours'),

-- 009) CLOSED, реакция 35 мин → breached
('aaaa0009-0000-4000-8000-000000000009', 'INC-DEMO-SLA-009',
 'Сбой освещения', 'Освещение не включилось по расписанию.',
 'CLOSED', 'MEDIUM', 'SYSTEM', 'LIGHTING_ILLUMINANCE_LOW',
 'Бройлерный цех № 2', 'Птичник 2-01', 'Зона 1',
 NOW() - INTERVAL '1 day',
 NOW() - INTERVAL '1 day',
 NOW() - INTERVAL '1 day' + INTERVAL '4 hours 30 minutes',
 NOW() - INTERVAL '1 day' + INTERVAL '35 minutes',
 35,
 NOW() - INTERVAL '1 day' + INTERVAL '3 hours',
 NOW() - INTERVAL '1 day' + INTERVAL '4 hours 30 minutes'),

-- 010) OPEN, без started_at, возраст 40 мин → breached
('aaaa0010-0000-4000-8000-000000000010', 'INC-DEMO-SLA-010',
 'Производственный показатель ниже плана', 'Средний вес отстаёт.',
 'OPEN', 'LOW', 'ANALYTICS', 'PRODUCTION_METRICS',
 'Бройлерный цех № 1', 'Птичник 1-01', 'Секция 2',
 NOW() - INTERVAL '40 minutes',
 NOW() - INTERVAL '40 minutes',
 NOW() - INTERVAL '40 minutes',
 NULL, NULL, NULL, NULL),

-- ── 30-дневное окно (записи 011–018) ──────────────────────────────────────────

-- 011) CLOSED, реакция 20 мин
('aaaa0011-0000-4000-8000-000000000011', 'INC-DEMO-SLA-011',
 'Колебания влажности', 'Влажность вне нормы.',
 'CLOSED', 'MEDIUM', 'SENSOR', 'MICROCLIMATE',
 'Бройлерный цех № 1', 'Птичник 1-02', 'Зона 3',
 NOW() - INTERVAL '10 days',
 NOW() - INTERVAL '10 days',
 NOW() - INTERVAL '10 days' + INTERVAL '6 hours',
 NOW() - INTERVAL '10 days' + INTERVAL '20 minutes',
 20,
 NOW() - INTERVAL '10 days' + INTERVAL '3 hours',
 NOW() - INTERVAL '10 days' + INTERVAL '6 hours'),

-- 012) CLOSED, реакция 24 мин
('aaaa0012-0000-4000-8000-000000000012', 'INC-DEMO-SLA-012',
 'Проблема с поилкой', 'Поилка не подаёт воду.',
 'CLOSED', 'HIGH', 'MANUAL', 'WATER_SUPPLY',
 'Бройлерный цех № 1', 'Птичник 1-03', 'Линия поения 2',
 NOW() - INTERVAL '12 days',
 NOW() - INTERVAL '12 days',
 NOW() - INTERVAL '12 days' + INTERVAL '7 hours',
 NOW() - INTERVAL '12 days' + INTERVAL '24 minutes',
 24,
 NOW() - INTERVAL '12 days' + INTERVAL '4 hours',
 NOW() - INTERVAL '12 days' + INTERVAL '7 hours'),

-- 013) CLOSED, реакция 27 мин
('aaaa0013-0000-4000-8000-000000000013', 'INC-DEMO-SLA-013',
 'Санитарное нарушение', 'Загрязнение в зоне кормления.',
 'CLOSED', 'MEDIUM', 'MANUAL', 'SANITATION',
 'Бройлерный цех № 2', 'Птичник 2-01', 'Кормовая зона',
 NOW() - INTERVAL '15 days',
 NOW() - INTERVAL '15 days',
 NOW() - INTERVAL '15 days' + INTERVAL '9 hours',
 NOW() - INTERVAL '15 days' + INTERVAL '27 minutes',
 27,
 NOW() - INTERVAL '15 days' + INTERVAL '5 hours',
 NOW() - INTERVAL '15 days' + INTERVAL '9 hours'),

-- 014) CLOSED, реакция 16 мин
('aaaa0014-0000-4000-8000-000000000014', 'INC-DEMO-SLA-014',
 'Отклонение веса', 'Вес ниже плана.',
 'CLOSED', 'LOW', 'ANALYTICS', 'PRODUCTION_METRICS',
 'Бройлерный цех № 1', 'Птичник 1-01', 'Секция 1',
 NOW() - INTERVAL '18 days',
 NOW() - INTERVAL '18 days',
 NOW() - INTERVAL '18 days' + INTERVAL '11 hours 20 minutes',
 NOW() - INTERVAL '18 days' + INTERVAL '16 minutes',
 16,
 NOW() - INTERVAL '18 days' + INTERVAL '6 hours',
 NOW() - INTERVAL '18 days' + INTERVAL '11 hours 20 minutes'),

-- 015) CLOSED, реакция 29 мин
('aaaa0015-0000-4000-8000-000000000015', 'INC-DEMO-SLA-015',
 'Проблема освещения', 'Лампы не работают.',
 'CLOSED', 'HIGH', 'SYSTEM', 'LIGHTING_ILLUMINANCE_LOW',
 'Бройлерный цех № 1', 'Птичник 1-02', 'Зона 1',
 NOW() - INTERVAL '20 days',
 NOW() - INTERVAL '20 days',
 NOW() - INTERVAL '20 days' + INTERVAL '8 hours 40 minutes',
 NOW() - INTERVAL '20 days' + INTERVAL '29 minutes',
 29,
 NOW() - INTERVAL '20 days' + INTERVAL '5 hours',
 NOW() - INTERVAL '20 days' + INTERVAL '8 hours 40 minutes'),

-- 016) OPEN, без started_at, возраст > 30 мин → breached
('aaaa0016-0000-4000-8000-000000000016', 'INC-DEMO-SLA-016',
 'Рост аммиака', 'Датчик фиксирует превышение.',
 'OPEN', 'CRITICAL', 'SENSOR', 'MICROCLIMATE',
 'Бройлерный цех № 2', 'Птичник 2-01', 'Зона вентиляции',
 NOW() - INTERVAL '22 days',
 NOW() - INTERVAL '22 days',
 NOW() - INTERVAL '22 days',
 NULL, NULL, NULL, NULL),

-- 017) IN_PROGRESS, реакция 19 мин
('aaaa0017-0000-4000-8000-000000000017', 'INC-DEMO-SLA-017',
 'Проблема кормления', 'Корм не поступает.',
 'IN_PROGRESS', 'MEDIUM', 'MANUAL', 'FEEDING',
 'Бройлерный цех № 1', 'Птичник 1-01', 'Кормовая линия',
 NOW() - INTERVAL '25 days',
 NOW() - INTERVAL '25 days',
 NOW() - INTERVAL '25 days' + INTERVAL '19 minutes',
 NOW() - INTERVAL '25 days' + INTERVAL '19 minutes',
 19,
 NULL, NULL),

-- 018) OPEN, без started_at, возраст < 30 мин → pending
('aaaa0018-0000-4000-8000-000000000018', 'INC-DEMO-SLA-018',
 'Санитария', 'Требуется проверка.',
 'OPEN', 'LOW', 'MANUAL', 'SANITATION',
 'Бройлерный цех № 2', 'Птичник 2-01', 'Зона отдыха',
 NOW() - INTERVAL '15 minutes',
 NOW() - INTERVAL '15 minutes',
 NOW() - INTERVAL '15 minutes',
 NULL, NULL, NULL, NULL)

ON CONFLICT (code) DO NOTHING;