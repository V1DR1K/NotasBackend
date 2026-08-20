# NotasBackend

Backend inicial de Cuaderno. Java 21, Spring Boot 3.5.5, Maven, PostgreSQL 17, Flyway, JPA, Validation, Security, JWT y Actuator.

## Ejecucion local

```bash
docker compose up --build
curl -c cookies.txt http://localhost:8080/api/auth/csrf
curl -c cookies.txt -b cookies.txt -H 'Content-Type: application/json' \
  -d '{"username":"tomas","password":"tomas"}' \
  http://localhost:8080/api/auth/login
```

El login emite el JWT en una cookie `HttpOnly`, `SameSite=Lax`. El token nunca se devuelve como JSON ni se guarda en `localStorage`. Las operaciones que cambian datos requieren el valor `token` de `GET /api/auth/csrf` en el header `X-XSRF-TOKEN`. En produccion configurar `JWT_SECURE_COOKIE=true` y servir detras de HTTPS.

Health: `GET /api/actuator/health`.

## Endpoints

Todos los endpoints de negocio usan `/api`, sin versionado `/api/v1`.

Los listados paginados responden exactamente `content`, `page`, `size`, `totalElements`, `totalPages`, `first` y `last`.

Auth: `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`, `GET /api/auth/csrf`.

Configuracion: `GET/POST /api/config/day-statuses`, `PATCH/DELETE /api/config/day-statuses/{code}`; los mismos verbos y forma para `finance-concepts`, `finance-categories` y `note-categories`. Las modificaciones requieren ADMIN. La respuesta comun es `ConfigOptionResponse(code,label,emoji,sortOrder,active)` y PATCH es parcial, sin posibilidad de cambiar `code`. Los defaults iniciales del usuario son statuses `green`, `yellow`, `red`; conceptos `monthly_payment`, `freelance`, `weekly_purchase`, `fuel`, `usd_purchase`, `investment_fund`, `transfer`, `other`; categorias financieras `work`, `extra`, `food`, `mobility`, `dollars`, `market`, `home`, `leisure`, `health`, `other`; y categorias de notas `ideas`, `personal`, `work`.

Mi Dia: `GET/POST /api/day-entries`, `GET/PATCH/DELETE /api/day-entries/{id}`. El request de alta es:

```json
{"date":"2026-08-20","statusCode":"green","feeling":"Productivo y tranquilo","description":"Buen dia"}
```

GET acepta `date`, `from`, `to`, `statusCode`, `page`, `size` y `sort`. `feeling` es texto obligatorio de hasta 120 caracteres y `description` texto obligatorio de hasta 3000. La respuesta usa `date`, `status` anidado como `ConfigOptionResponse`, `feeling` y `description`.

Notas: `GET/POST /api/notes`, `GET/PATCH/DELETE /api/notes/{id}`. El request es:

```json
{"title":"Idea","body":"Texto de la nota","categoryCode":"ideas","date":"2026-08-20"}
```

GET acepta `categoryCode`, `date`, `from`, `to`, `search`, `page`, `size` y `sort`. `title` admite 180 caracteres, `body` 10000, `categoryCode` y `date` son obligatorios en altas. PATCH rechaza strings vacios.

Finanzas: `GET/POST /api/finance/movements`, `GET/PATCH/DELETE /api/finance/movements/{id}`, `GET /api/finance/summary?from=YYYY-MM-DD&to=YYYY-MM-DD`, `GET /api/finance/exchange-rate/usd` y `POST /api/finance/exchange-rate/usd` solo ADMIN.

El request de movimiento es:

```json
{"date":"2026-08-20","bucket":"EXPENSE","conceptCode":"weekly_purchase","categoryCode":"food","amountArs":12500.00,"note":"Supermercado"}
```

Los buckets validos son exclusivamente `INCOME`, `EXPENSE` e `INVESTED`. GET acepta `bucket`, `date`, `conceptCode`, `categoryCode`, `from`, `to`, `minAmount`, `maxAmount`, `page`, `size` y `sort`. `amountArs` es positivo y la respuesta expone `amount: {ars,usd,exchangeRate}`.

La regla de USD es ARS por USD: `usd = ars / exchangeRate`. El snapshot se guarda al crear el movimiento y no se recalcula aunque cambie el proveedor. El summary usa `cash = income - expense - invested`, contiene tambien `exchangeRate` con `currency,buy,sell,average,fetchedAt,source`, y calcula sus valores USD con la cotizacion actual; los movimientos historicos conservan su propio snapshot. El proveedor opcional debe devolver un numero, `{ "rate": 1000 }` o `{ "buy": 990, "sell": 1010 }`; si no existe o falla, se usa el fallback manual persistido y luego `EXCHANGE_RATE_FALLBACK`.

Archivos: `GET/POST /api/file-folders`, `PATCH/DELETE /api/file-folders/{id}` y `GET/POST /api/files`, `GET/PATCH/DELETE /api/files/{id}`, `GET /api/files/{id}/download`. Upload multipart usa las parts `file` y `folderId`. GET files acepta `folderId`, `kind`, `name`, `from`, `to`, `page`, `size` y `sort`. El backend calcula `name`, `extension`, `mimeType`, `sizeBytes` y `kind`, que se serializa en lowercase (`document`, `image`, etc.); la respuesta siempre incluye `downloadUrl`, `folder`, `uploadedAt` y `updatedAt`. Las carpetas no son anidadas y no se puede borrar una carpeta con archivos activos.

Dashboard: `GET /api/dashboard`, con `dayEntriesCount`, `notesCount`, `filesCount`, `financeMovementsCount`, `financeSummary`, `recentNotes`, `recentFiles`, `recentDays` y `recentMovements`.

## Configuracion

Variables principales: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET` (minimo 32 bytes), `JWT_EXPIRATION`, `JWT_SECURE_COOKIE`, `CORS_ALLOWED_ORIGINS`, `APP_INITIAL_USER_USERNAME`, `APP_INITIAL_USER_PASSWORD`, `FILE_STORAGE_ROOT`, `FILE_MAX_SIZE`, `EXCHANGE_RATE_PROVIDER_URL` y `EXCHANGE_RATE_FALLBACK`.

El usuario inicial se crea solo si el username no existe y siempre con rol ADMIN. Los defaults locales parametrizados son `tomas`/`tomas`; no se actualiza una contraseña existente al reiniciar. `EXCHANGE_RATE_TIMEOUT_MS` limita el proveedor externo (3000 ms por defecto), y el fallback persistido/configurado evita bloquear altas.

Los archivos se guardan fuera de PostgreSQL bajo `FILE_STORAGE_ROOT` (default `/var/lib/cuaderno/files`), nunca como Base64. Las claves de storage son UUID, las rutas se normalizan contra la raiz y el borrado es logico. V1 incluye la tabla `audit_events`; esta versión no afirma auditoría avanzada ni genera eventos todavía.

## Verificacion

```bash
mvn test
mvn package
```
