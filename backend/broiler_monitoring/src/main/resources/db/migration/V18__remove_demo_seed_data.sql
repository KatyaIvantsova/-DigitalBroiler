-- S1-12: демо-данные больше не живут в основных миграциях.
-- V15/V16 перенесены в db/demo и накатываются только в профиле demo.
-- Здесь удаляем демо-строки, которые V15/V16 уже успели записать в рабочие базы.
-- В профиле demo они вернутся: repeatable-миграции из db/demo идут после версионных.

-- Реальные инциденты, созданные из демо-уведомлений, оставляем, только отвязываем.
UPDATE incidents
SET notification_id = NULL
WHERE code NOT LIKE 'INC-DEMO-%'
  AND notification_id IN (SELECT id FROM notifications WHERE code LIKE 'NOTIF-DEMO-%');

-- История и вложения удаляются каскадно (ON DELETE CASCADE).
DELETE FROM incidents WHERE code LIKE 'INC-DEMO-%';
DELETE FROM notifications WHERE code LIKE 'NOTIF-DEMO-%';
