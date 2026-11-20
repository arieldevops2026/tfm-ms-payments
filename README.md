# tfm-ms-payments

Microservicio de **pagos simulados** del TFM *"Diseño y evaluación de una plataforma GitOps con entrega progresiva gobernada por SLO y telemetría OpenTelemetry para microservicios en Kubernetes"* (Máster en DevOps, UNIR).

Forma parte de la aplicación experimental de tres servicios sobre la que se evalúa la estrategia de despliegue canary gobernada por objetivos de nivel de servicio (SLO). **No procesa pagos reales**: autoriza o rechaza según un monto máximo configurable.

## Papel en el laboratorio

```text
cliente ──► ms-orders ──► ms-products
                     └──► ms-payments   (este servicio: autoriza el pago de la orden)
```

`ms-orders` le solicita la autorización del total de cada orden. Es la dependencia sobre la que se simulan fallos de servicios externos: latencia, errores y caídas de un sistema de pagos.

## Tecnología

- Quarkus 3.40, Java 25, hilos virtuales (`@RunOnVirtualThread`).
- Compilación nativa con GraalVM/Mandrel para la imagen de contenedor.
- Lombok, Hibernate Validator, SmallRye OpenAPI y Health.
- Micrometer (métricas Prometheus) y OpenTelemetry (trazas y logs).

## Regla de negocio

Un pago se **aprueba** (`APPROVED`) si su importe es menor o igual que `payment.max-amount` (5000.00 por defecto). En caso contrario se **rechaza** (`REJECTED`) con el motivo `AMOUNT_LIMIT_EXCEEDED`. Un rechazo es una respuesta de negocio con HTTP 200, no un error: no cuenta para el SLI de errores 5xx.

## Datos

Los pagos se guardan **en memoria**, por réplica, y se conservan solo los últimos 10 000. Se pierden al reiniciar. No hay base de datos, por lo que revertir una versión defectuosa no deja datos persistentes que corregir.

## API

Base: `http://localhost:18002/api/v1`. Contrato completo en `src/main/resources/META-INF/openapi.yaml` y en Swagger UI (`/swagger-ui`).

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/payments/authorize` | Autoriza o rechaza un pago simulado |
| `GET` | `/payments/get-by-id/{id}` | Consulta un pago. `404` si no existe en esta réplica |

```bash
curl -X POST localhost:18002/api/v1/payments/authorize \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"ORD-0001","customerId":"CUS-001","amount":{"currency":"USD","amount":925.50},"paymentMethod":"CARD"}'
```

Cabeceras opcionales de trazabilidad: `session-id`, `username`, `client-ip`, `channel`. `ms-orders` propaga el `uuid` de la petición original.

## Configuración

Toda la configuración se sobrescribe con variables de entorno, sin cambiar el código. Así, una misma imagen sirve como versión estable o como versión candidata defectuosa.

| Variable | Por defecto | Uso |
|---|---|---|
| `APP_VERSION` | `1.0.0` | Versión que aparece en métricas, trazas y logs (`service_version`) |
| `APP_ENVIRONMENT` | `local` | Entorno (`deployment_environment`) |
| `PAYMENT_MAX_AMOUNT` | `5000.00` | Importe máximo aprobado |
| `QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4317` | OpenTelemetry Collector |
| `QUARKUS_OTEL_SDK_DISABLED` | `false` | `true` para no enviar telemetría |
| `FAULT_MODE` | `NONE` | Fallo a inyectar (ver abajo) |
| `FAULT_LATENCY_MS` | `0` | Retardo del modo `LATENCY` |
| `FAULT_TIMEOUT_MS` | `10000` | Espera del modo `TIMEOUT` |
| `FAULT_PROBABILITY` | `1.0` | Proporción de peticiones afectadas (0.0 a 1.0) |

## Inyección de fallos

Permite construir las versiones defectuosas de los experimentos:

| `FAULT_MODE` | Efecto |
|---|---|
| `NONE` | Sin fallo (versión saludable) |
| `LATENCY` | Añade `FAULT_LATENCY_MS` a la respuesta |
| `ERROR` | Responde HTTP 500 |
| `TIMEOUT` | Espera `FAULT_TIMEOUT_MS`. Como supera el timeout de `ms-orders` (3 s), la orden termina en 500 |

```bash
# Versión candidata 1.1.0 con errores 5xx en el 5 % de las peticiones
APP_VERSION=1.1.0 FAULT_MODE=ERROR FAULT_PROBABILITY=0.05 \
  java -jar target/quarkus-app/quarkus-run.jar
```

## Observabilidad

| Señal | Dónde | Uso en el TFM |
|---|---|---|
| Métricas | `/q/metrics` (Prometheus las consulta) | **Decisión** del canary: SLI de latencia y errores |
| Trazas | OTLP al Collector, luego Tempo | Diagnóstico |
| Logs | OTLP al Collector, luego Loki | Diagnóstico, enlazados a la traza por `trace_id` |
| Salud | `/q/health`, `/q/health/ready`, `/q/health/live` | Sondas de Kubernetes |

Las métricas HTTP llevan las etiquetas `service_name`, `service_version` y `deployment_environment`, y el histograma de latencia incluye un bucket exacto en **500 ms**, el umbral del SLO. Ejemplo del SLI de errores, que devuelve 0 cuando no hubo ningún 5xx:

```promql
100 * (sum(rate(http_server_requests_seconds_count{service_name="ms-payments", service_version="1.1.0", status=~"5.."}[1m])) or vector(0))
    / sum(rate(http_server_requests_seconds_count{service_name="ms-payments", service_version="1.1.0"}[1m]))
```

## Ejecutar

```bash
./mvnw quarkus:dev                                    # desarrollo, con recarga en caliente
./mvnw package && java -jar target/quarkus-app/quarkus-run.jar   # JVM
```

En modo dev la telemetría se controla con `%dev.quarkus.otel.sdk.disabled` en `application.properties`. Para ver trazas y logs en local, levanta antes el stack de observabilidad del laboratorio (`local-stack`).

## Imagen nativa

```bash
./mvnw package -Pnative -DskipTests -Dquarkus.native.container-build=true   # ~3-5 min, no requiere GraalVM local
docker build -f src/main/docker/Dockerfile.native-micro \
  --build-arg GIT_SHA=$(git rev-parse HEAD) -t tfm-ms-payments:local .
docker run --rm -p 18002:18002 -e QUARKUS_OTEL_SDK_DISABLED=true tfm-ms-payments:local
```

La imagen parte de una base mínima fijada por digest, corre con un usuario sin privilegios (`1001`) e incluye en la etiqueta `org.opencontainers.image.revision` el commit del que se construyó.

## Limitaciones

- Pagos simulados y datos en memoria: no hay integración con pasarelas ni persistencia.
- `GET /payments/get-by-id/{id}` solo encuentra pagos de la réplica que los creó. Con varias réplicas, como durante un canary, puede responder `404`. El flujo de los experimentos no lo usa.
- Sin autenticación: está pensado para un laboratorio aislado.
