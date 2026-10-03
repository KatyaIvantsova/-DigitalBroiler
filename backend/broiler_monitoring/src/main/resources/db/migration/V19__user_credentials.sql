-- S1-09: учётные данные для входа. Колонка role остаётся должностью (для отображения),
-- права доступа задаёт access_role. Пользователи без логина войти не могут.
ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS access_role VARCHAR(32) NOT NULL DEFAULT 'OPERATOR';
ALTER TABLE users ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_username_lower ON users (LOWER(username));

ALTER TABLE users ADD CONSTRAINT ck_users_access_role
    CHECK (access_role IN ('OPERATOR', 'TECHNOLOGIST', 'VETERINARIAN', 'MANAGER', 'ADMIN'));
