# Спринт 2 «Партия и доступ» (19–30 октября 2026)

| Задача | Где | Issue |
|--------|-----|-------|
| S2-01 Сущности и миграции: площадка, птичник, зона, партия | `V20__production_structure.sql`, `StructureController`, `FlockController`, Swagger | #19 |
| S2-02 Экран партии: посадка, закрытие, возраст | `/flocks`, `/flocks/[id]` | #20 |
| S2-03 Ежедневный учёт: падёж, выбраковка, взвешивания, корм, вода | вкладки «Учёт по дням» и «Взвешивания», история правок | #21 |
| S2-04 Роли и права | [04-roles-matrix.md](04-roles-matrix.md), `SecurityConfig`, `AccessService`, `RoleMatrixIntegrationTest` | #22 |
| S2-05 Управление пользователями, назначение на птичники | `/admin/users`, `UserAdminController` | #23 |
| S2-06 Журнал действий | `audit_log`, `/admin/audit`, вкладка «История изменений» партии | #24 |
| S2-07 Привязка датчиков к птичнику и зоне | `/admin/structure`, `PATCH /api/v1/sensors/{id}/location` | #25 |
| S2-08 Спецификация движка правил | [08-rules-engine-spec.md](08-rules-engine-spec.md) | #26 |
| S2-09 Протокол интеграции датчиков и учёта | [09-integration-protocol.md](09-integration-protocol.md) | #27 |
| S2-10 Макеты экранов | [10-mockups.md](10-mockups.md) | #28 |
| S2-11 Нормы по корму, воде, весу и стаду | [11-norms-production.md](11-norms-production.md), [norms-production.csv](norms-production.csv) | #29 |
| S2-12 Тестирование спринтов 1–2 | [12-test-report.md](12-test-report.md) | #30 |
