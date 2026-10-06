# NotasBackend

Backend inicial de Cuaderno. Java 21, Spring Boot 3.5.5, Maven, PostgreSQL 17, Flyway, JPA, Validation, Security, central Auth, JWT y Actuator.

## Ejecucion local

```bash
docker compose up --build
curl -H 'Content-Type: application/json' \
  -d '{"username":"central-user","password":"central-password"}' \
  http://localhost:8080/api/auth/login
```

El login delega en Auth central y devuelve los datos del usuario; los tokens viajan en cookies `HttpOnly`, `SameSite=Strict` y con alcance `/api`. El frontend las envía con credenciales y no guarda tokens en `localStorage`. La API es stateless y no expone un endpoint CSRF; el atributo `SameSite=Strict` limita el envío de cookies desde otros sitios. `JWT_SECURE_COOKIE` activa el atributo `Secure` y vale `true` por defecto; el `docker compose` local lo desactiva para permitir HTTP. Para producción se recomienda definir `AUTH_JWT_AUDIENCE` y activar `AUTH_JWT_REQUIRE_AUDIENCE=true`.

Health: `GET /api/actuator/health`.

## Endpoints

Todos los endpoints de negocio usan `/api`, sin versionado `/api/v1`.

Los listados paginados responden exactamente `content`, `page`, `size`, `totalElements`, `totalPages`, `first` y `last`.

Auth: `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`, `GET /api/auth/me` y `PUT /api/auth/change-password`. Login y refresh renuevan las cookies de sesión; logout las elimina.

Configuracion: `GET/POST /api/config/day-statuses`, `PATCH/DELETE /api/config/day-statuses/{code}`; los mismos verbos y forma para `day-feelings`, `finance-items` y `note-categories`. Las modificaciones requieren ADMIN. La respuesta comun es `ConfigOptionResponse(code,label,emoji,sortOrder,active,financeType)` y PATCH es parcial, sin posibilidad de cambiar `code`. Las clasificaciones financieras requieren `financeType`: `INCOME`, `EXPENSE` o `TRANSFER`. Las opciones activas alimentan los formularios y filtros; Transferencia queda reservada para cuentas de inversión.

Proyectos: `GET/POST /api/config/projects` y `PATCH/DELETE /api/config/projects/{code}`. Se crean Personal, Facultad y Laburo para cada usuario. El nombre, orden y disponibilidad se administran como las demás opciones; Personal es el proyecto predeterminado y no se puede desactivar ni eliminar. Tampoco se puede desactivar o eliminar un proyecto con tareas, notas, archivos, carpetas o eventos activos. Los listados de esos recursos aceptan `projectCode`; omitirlo muestra todos los proyectos. Las altas y ediciones aceptan `projectCode`; si falta en un alta se usa Personal.

La migración V19 conserva las filas existentes y asigna Facultad/Laburo según la categoría de tarea, nota o evento y el nombre de carpeta. El resto queda en Personal. Nunca infiere un proyecto a partir del título o cuerpo del registro.

Calendario: `GET/POST /api/events`, `GET/PATCH/DELETE /api/events/{id}`. Los eventos son de día completo y aceptan `date`, `description` y `categoryCode`; se permiten varios eventos en una misma fecha. GET acepta `date`, `from`, `to`, `categoryCode`, `page`, `size` y `sort`. Las categorías se administran con `GET/POST /api/config/event-categories`, `PATCH/DELETE /api/config/event-categories/{code}` y se crean por defecto `Laburo`, `Facultad`, `Médico` y `Trámites`. Las modificaciones de configuración requieren ADMIN.

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

La analítica financiera considera únicamente ingresos y egresos externos de Mercado Pago; excluye transferencias internas. Devuelve los totales diarios de ingresos y egresos, además de las sumas agrupadas por `itemCode` para cada bucket. El rango admite hasta 366 días y excluye movimientos eliminados.

Las cuentas financieras muestran los saldos actuales y los movimientos nuevos los actualizan. `DAILY_TNA` proyecta el saldo con capitalización diaria y `MANUAL` conserva el último saldo sincronizado. Para Tomas se crean de forma idempotente MercadoPago (`58938.11` ARS, `18.5` TNA), Inversiones en pesos (`800000` ARS) y Crypto (`6206454.61` ARS). Un ingreso o egreso de MercadoPago modifica esa caja; una transferencia con una inversión mueve el dinero entre MercadoPago y la inversión seleccionada.

Las transferencias explícitas usan `POST /api/finance/transfers` y `PATCH /api/finance/transfers/{id}`. El alta recibe `sourceAccountCode`, `destinationAccountCode`, `date`, `amountArs` y `note` opcional; PATCH acepta esos mismos campos de forma parcial. Solo se permiten Mercado Pago (`mercadopago`) ↔ Inversión Pesos (`inversiones_pesos`) y Mercado Pago ↔ Cripto (`crypto`). La respuesta conserva el movimiento original y agrega `movementType`, `sourceAccountCode` y `destinationAccountCode`; el listado acepta `movementType=INCOME|EXPENSE|TRANSFER|INVESTED`. Las rutas anteriores siguen usando la misma operación transaccional. Editar aplica la diferencia neta y eliminar revierte ambos saldos. Un retiro o corrección de Cripto no puede consumir el capital asignado a posiciones abiertas. La actualización manual de saldo es una corrección independiente y no transfiere dinero. No se modifican saldos históricos.

El request de movimiento es:

```json
{"date":"2026-08-20","bucket":"EXPENSE","accountCode":"mercadopago","itemCode":"supermercado","amountArs":12500.00,"note":"Supermercado"}
```

Los buckets nuevos son `INCOME` y `EXPENSE` (`INVESTED` queda solo para datos históricos). La caja `mercadopago` admite clasificaciones financieras configuradas como ingresos o egresos; las cuentas de inversión admiten `transferencia` en ambos sentidos. GET acepta `bucket`, `date`, `itemCode`, `from`, `to`, `minAmount`, `maxAmount`, `page`, `size` y `sort`. `amountArs` es positivo y la respuesta expone `accountCode` y `amount: {ars,usd,exchangeRate}`.

La regla de USD es ARS por USD: `usd = ars / exchangeRate`. El snapshot se guarda al crear el movimiento y no se recalcula aunque cambie el proveedor. El summary usa `cash = income - expense - invested`, contiene tambien `exchangeRate` con `currency,buy,sell,average,fetchedAt,source`, y calcula sus valores USD con la cotizacion actual; los movimientos historicos conservan su propio snapshot. El proveedor opcional debe devolver un numero, `{ "rate": 1000 }` o `{ "buy": 990, "sell": 1010 }`; si no existe o falla, se usa el fallback manual persistido y luego `EXCHANGE_RATE_FALLBACK`.

Cripto / Bitget: las transferencias con `crypto` exigen `exchangeRate` (ARS por USD de la operación P2P, con hasta 8 decimales). Se persisten los USD acreditados/debitados y el costo en ARS por separado: el disponible no cambia con el dólar Blue. `POST /api/finance/crypto/transfers` y el alta anterior de movimientos también exigen esa cotización. La compra usa dólares libres de la billetera y conserva su costo promedio de entrada: `POST /api/finance/crypto/investments` recibe `{ "date":"2026-09-30", "assetCode":"BTCUSDT", "amountUsd":50, "unitPriceUsd":60000 }`. No vuelve a descontar Mercado Pago.

`GET /api/finance/crypto/investments/{id}` consulta un lote; `PATCH /api/finance/crypto/investments/{id}/unit-price` completa o corrige su precio, es idempotente para el mismo valor y exige anular primero sus ventas activas para cambiarlo. El importe invertido se conserva y se recalculan las unidades. La interfaz reconcilia una respuesta de escritura perdida mediante la lectura de ese lote.

`POST /api/finance/crypto/investments/{id}/sales` recibe fecha, `quantity`, `proceedsUsd` y nota opcional. Guarda precio de venta por unidad, costo proporcional en ambas monedas y cotización de costo; la ganancia realizada en USD es lo recibido menos el costo vendido. Los fondos de la venta quedan disponibles en Bitget. Las anulaciones conservan registros y revierten efectos; una venta no puede anularse si sus fondos ya se utilizaron. `GET /api/finance/crypto/summary` agrega `performance` con capital al costo, ventas, costo vendido, rendimiento porcentual, resultados por activo y evolución diaria de ganancias acumuladas. Las métricas excluyen operaciones anuladas y no incluyen valuación de mercado de posiciones abiertas.

`PUT /api/finance/accounts/crypto/balance` acepta `balanceUsd` como total (disponible + posiciones al costo). V20 conserva saldos ARS y movimientos existentes, inicializa USD históricos usando costos de lotes y la última cotización almacenada, y marca `legacyBalanceEstimated` hasta corregir el total real. Los P2P futuros usan siempre su cotización propia. La visualización de posiciones usa cuatro decimales; los registros conservan hasta dieciocho.

Archivos: `GET/POST /api/file-folders`, `PATCH/DELETE /api/file-folders/{id}` y `GET/POST /api/files`, `GET/PATCH/DELETE /api/files/{id}`, `GET /api/files/{id}/download`. Upload multipart usa las parts `file`, `folderId` y opcionalmente `name`; si no se envía nombre, se usa el nombre original. El nombre es también el título/descripción visible y buscable. GET files acepta `folderId`, `kind`, `search` (también `name` como alias), `from`, `to`, `page`, `size` y `sort`. El backend calcula `name`, `description`, `extension`, `mimeType`, `sizeBytes` y `kind`, que se serializa en lowercase (`document`, `image`, etc.); la respuesta siempre incluye `downloadUrl`, `folder`, `uploadedAt` y `updatedAt`. Las carpetas no son anidadas y no se puede borrar una carpeta con archivos activos.

Dashboard: `GET /api/dashboard`, con los contadores globales, `financeSummary`, colecciones recientes y los resúmenes reales `dayStats`, `financeSnapshot`, `storageUsage`, `upcomingEvents` y `recentActivity`. `dayStats` incluye registros del mes, análisis pendientes y el registro de hoy; `financeSnapshot` incluye caja e inversiones actuales junto con ingresos, egresos y cotización del mes; `storageUsage` expone bytes usados y cuota; `upcomingEvents` contiene los próximos 14 días y `recentActivity` consolida las cinco secciones en orden de actualización.

## Configuracion

Variables principales: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `AUTH_SERVICE_URL`, `AUTH_PUBLIC_KEY_PEM`, `AUTH_JWT_ISSUER`, `AUTH_JWT_AUDIENCE`, `AUTH_CLIENT_TIMEOUT_MS`, `AUTH_DEFAULT_ROLE`, `JWT_SECURE_COOKIE`, `CORS_ALLOWED_ORIGINS`, `FILE_STORAGE_ROOT`, `FILE_MAX_SIZE`, `FILE_MAX_USER_BYTES`, `EXCHANGE_RATE_PROVIDER_URL`, `EXCHANGE_RATE_FALLBACK` y `EXCHANGE_RATE_CACHE_TTL_MS`.

Los usuarios locales se aprovisionan de forma idempotente al iniciar sesión en Auth central. La migración conserva el UUID local y todos sus datos, y agrega el UUID central en `auth_user_id`; los hashes locales existentes ya no participan de la autenticación. El seeder solo crea opciones para usuarios ya mapeados y no crea credenciales. `EXCHANGE_RATE_TIMEOUT_MS` limita el proveedor externo (3000 ms por defecto), y el fallback persistido/configurado evita bloquear altas.

Los archivos se guardan fuera de PostgreSQL bajo `FILE_STORAGE_ROOT` (default `/var/lib/cuaderno/files`), nunca como Base64. Las claves de storage son UUID, las rutas se normalizan contra la raiz y el borrado es logico. V1 incluye la tabla `audit_events`; esta versión no afirma auditoría avanzada ni genera eventos todavía.

## Verificacion

```bash
mvn test
mvn package
```

Las pruebas de persistencia financiera usan PostgreSQL 17 mediante Testcontainers y requieren Docker. Con Docker 29, ejecutar `mvn -Dapi.version=1.44 test` para la compatibilidad del cliente Docker administrado por Spring Boot 3.5.
