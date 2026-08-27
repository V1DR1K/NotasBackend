# NotasBackend

Backend inicial de Cuaderno. Java 21, Spring Boot 3.5.5, Maven, PostgreSQL 17, Flyway, JPA, Validation, Security, central Auth, JWT y Actuator.

## Ejecucion local

```bash
docker compose up --build
curl -H 'Content-Type: application/json' \
  -d '{"username":"central-user","password":"central-password"}' \
  http://localhost:8080/api/auth/login
```

El login delega en Auth central y devuelve el access JWT y refresh token como JSON. El frontend mantiene la sesión en `localStorage`, envía `Authorization: Bearer` y rota el refresh token automáticamente cuando el access token vence. La API es stateless y no utiliza cookies ni CSRF por decisión explícita del MVP. Para producción se recomienda definir `AUTH_JWT_AUDIENCE` y activar `AUTH_JWT_REQUIRE_AUDIENCE=true`.

Health: `GET /api/actuator/health`.

## Endpoints

Todos los endpoints de negocio usan `/api`, sin versionado `/api/v1`.

Los listados paginados responden exactamente `content`, `page`, `size`, `totalElements`, `totalPages`, `first` y `last`.

Auth: `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`, `GET /api/auth/me`, `POST /api/auth/change-password`, `GET /api/auth/csrf`.

Configuracion: `GET/POST /api/config/day-statuses`, `PATCH/DELETE /api/config/day-statuses/{code}`; los mismos verbos y forma para `day-feelings`, `finance-items` y `note-categories`. Las modificaciones requieren ADMIN. La respuesta comun es `ConfigOptionResponse(code,label,emoji,sortOrder,active,financeType)` y PATCH es parcial, sin posibilidad de cambiar `code`. Las clasificaciones financieras requieren `financeType`: `INCOME`, `EXPENSE` o `TRANSFER`. Las opciones activas alimentan los formularios y filtros; Transferencia queda reservada para cuentas de inversión.

Mi Dia: `GET/POST /api/day-entries`, `GET/PATCH/DELETE /api/day-entries/{id}` y `POST /api/day-entries/{id}/analyze`. El request de alta es:

```json
{"date":"2026-08-20","description":"Buen dia"}
```

GET acepta `date`, `from`, `to`, `statusCode`, `page`, `size` y `sort`. La descripción es obligatoria de hasta 3000 caracteres. El alta queda inicialmente con `analysisStatus: PENDING`; el análisis completa `status`, `feeling` y `analysisStatus: COMPLETED`. La respuesta usa `date`, `analysisStatus`, `status` anidado como `ConfigOptionResponse`, `feeling` y `description`.

Notas: `GET/POST /api/notes`, `GET/PATCH/DELETE /api/notes/{id}`. El request es:

```json
{"title":"Idea","body":"Texto de la nota","categoryCode":"ideas","date":"2026-08-20"}
```

GET acepta `categoryCode`, `date`, `from`, `to`, `search`, `page`, `size` y `sort`. `title` admite 180 caracteres, `body` 10000, `categoryCode` y `date` son obligatorios en altas. PATCH rechaza strings vacios.

Finanzas: `GET/POST /api/finance/movements`, `GET/PATCH/DELETE /api/finance/movements/{id}`, `GET /api/finance/summary?from=YYYY-MM-DD&to=YYYY-MM-DD`, `GET /api/finance/analytics?from=YYYY-MM-DD&to=YYYY-MM-DD`, `GET /api/finance/accounts`, `PUT /api/finance/accounts/{code}/balance`, `GET /api/finance/exchange-rate/usd` y `POST /api/finance/exchange-rate/usd` solo ADMIN.

La analítica financiera devuelve los totales diarios de ingresos y egresos, además de las sumas agrupadas por `itemCode` para cada bucket. El rango admite hasta 366 días y excluye movimientos eliminados.

Las cuentas financieras muestran los saldos actuales y los movimientos nuevos los actualizan. `DAILY_TNA` proyecta el saldo con capitalización diaria y `MANUAL` conserva el último saldo sincronizado. Para Tomas se crean de forma idempotente MercadoPago (`58938.11` ARS, `18.5` TNA), Inversiones en pesos (`800000` ARS) y Crypto (`6206454.61` ARS). Un ingreso o egreso de MercadoPago modifica esa caja; una transferencia con una inversión mueve el dinero entre MercadoPago y la inversión seleccionada.

El request de movimiento es:

```json
{"date":"2026-08-20","bucket":"EXPENSE","accountCode":"mercadopago","itemCode":"supermercado","amountArs":12500.00,"note":"Supermercado"}
```

Los buckets nuevos son `INCOME` y `EXPENSE` (`INVESTED` queda solo para datos históricos). La caja `mercadopago` admite clasificaciones financieras configuradas como ingresos o egresos; las cuentas de inversión admiten `transferencia` en ambos sentidos. GET acepta `bucket`, `date`, `itemCode`, `from`, `to`, `minAmount`, `maxAmount`, `page`, `size` y `sort`. `amountArs` es positivo y la respuesta expone `accountCode` y `amount: {ars,usd,exchangeRate}`.

La regla de USD es ARS por USD: `usd = ars / exchangeRate`. El snapshot se guarda al crear el movimiento y no se recalcula aunque cambie el proveedor. El summary usa `cash = income - expense - invested`, contiene tambien `exchangeRate` con `currency,buy,sell,average,fetchedAt,source`, y calcula sus valores USD con la cotizacion actual; los movimientos historicos conservan su propio snapshot. El proveedor opcional debe devolver un numero, `{ "rate": 1000 }` o `{ "buy": 990, "sell": 1010 }`; si no existe o falla, se usa el fallback manual persistido y luego `EXCHANGE_RATE_FALLBACK`.

Archivos: `GET/POST /api/file-folders`, `PATCH/DELETE /api/file-folders/{id}` y `GET/POST /api/files`, `GET/PATCH/DELETE /api/files/{id}`, `GET /api/files/{id}/download`. Upload multipart usa las parts `file`, `folderId` y opcionalmente `description`; si no se envía descripción, se usa el nombre original. GET files acepta `folderId`, `kind`, `search` (también `name` como alias), `from`, `to`, `page`, `size` y `sort`; la búsqueda revisa nombre técnico y descripción. El backend calcula `name`, `description`, `extension`, `mimeType`, `sizeBytes` y `kind`, que se serializa en lowercase (`document`, `image`, etc.); la respuesta siempre incluye `downloadUrl`, `folder`, `uploadedAt` y `updatedAt`. Las carpetas no son anidadas y no se puede borrar una carpeta con archivos activos.

Dashboard: `GET /api/dashboard`, con `dayEntriesCount`, `notesCount`, `filesCount`, `financeMovementsCount`, `financeSummary`, `recentNotes`, `recentFiles`, `recentDays` y `recentMovements`.

## Configuracion

Variables principales: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `AUTH_SERVICE_URL`, `AUTH_PUBLIC_KEY_PEM`, `AUTH_JWT_ISSUER`, `AUTH_JWT_AUDIENCE`, `AUTH_CLIENT_TIMEOUT_MS`, `AUTH_DEFAULT_ROLE`, `JWT_SECURE_COOKIE`, `CORS_ALLOWED_ORIGINS`, `FILE_STORAGE_ROOT`, `FILE_MAX_SIZE`, `FILE_MAX_USER_BYTES`, `EXCHANGE_RATE_PROVIDER_URL`, `EXCHANGE_RATE_FALLBACK` y `EXCHANGE_RATE_CACHE_TTL_MS`.

Los usuarios locales se aprovisionan de forma idempotente al iniciar sesión en Auth central. La migración conserva el UUID local y todos sus datos, y agrega el UUID central en `auth_user_id`; los hashes locales existentes ya no participan de la autenticación. El seeder solo crea opciones para usuarios ya mapeados y no crea credenciales. `EXCHANGE_RATE_TIMEOUT_MS` limita el proveedor externo (3000 ms por defecto), y el fallback persistido/configurado evita bloquear altas.

Los archivos se guardan fuera de PostgreSQL bajo `FILE_STORAGE_ROOT` (default `/var/lib/cuaderno/files`), nunca como Base64. Las claves de storage son UUID, las rutas se normalizan contra la raiz y el borrado es logico. V1 incluye la tabla `audit_events`; esta versión no afirma auditoría avanzada ni genera eventos todavía.

## Verificacion

```bash
mvn test
mvn package
```
