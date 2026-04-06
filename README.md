# Weather API

A Spring Boot REST API that returns current weather and forecast data for any city.
Responses are cached in Redis for 12 hours to minimise calls to the Visual Crossing
weather data provider.

Project roadmap.sh url: https://roadmap.sh/projects/weather-api-wrapper-service

## Architecture

```
Client → WeatherController → WeatherService → CacheService (Redis)
                                           ↘ VisualCrossingClient (on cache miss)
```

## Prerequisites

| Requirement | Version |
|-------------|---------|
| Java        | 21      |
| Docker      | 24+     |
| Maven       | 3.9+ (or use the included `./mvnw` wrapper) |

## Environment Variables

| Variable          | Required | Default     | Description                                      |
|-------------------|----------|-------------|--------------------------------------------------|
| `WEATHER_API_KEY` | Yes      | —           | Visual Crossing API key (no default — app will not start without it) |
| `REDIS_HOST`      | No       | `localhost` | Redis hostname                                   |
| `REDIS_PORT`      | No       | `6379`      | Redis port                                       |
| `REDIS_PASSWORD`  | No       | _(none)_    | Redis password. If set, Redis starts with `requirepass` and the app authenticates on connect. Leave unset for passwordless local dev. |
| `TRUSTED_PROXIES` | No       | _(none)_    | Comma-separated list of trusted reverse-proxy IPs (e.g. your load balancer). `X-Forwarded-For` is only honoured for connections from these IPs. Leave unset when running without a proxy. |

Get a free Visual Crossing API key at https://www.visualcrossing.com/

## Running the application

> **Note:** Do not run the local Maven app and `docker-compose up` (full stack) at the same time —
> both bind to port 8080 on the host and will conflict.

### Redis only (run the app via Maven locally)

```bash
export WEATHER_API_KEY=your_api_key_here

# Start Redis in Docker (binds to 127.0.0.1:6379 — localhost only)
docker compose up redis -d

# Run the app
./mvnw spring-boot:run
```

### Full stack via Docker Compose

```bash
export WEATHER_API_KEY=your_api_key_here
docker compose up --build
```

### With Redis password (optional but recommended for non-local environments)

```bash
export WEATHER_API_KEY=your_api_key_here
export REDIS_PASSWORD=your_redis_password
docker compose up --build
```

### Behind a reverse proxy (e.g. nginx, AWS ALB)

Set `TRUSTED_PROXIES` to the IP(s) of your proxy so that `X-Forwarded-For` is
honoured for rate limiting. Without this, the header is ignored and the raw TCP
address is always used (which is the safe default).

```bash
export TRUSTED_PROXIES=10.0.0.5
```

## API Reference

| Resource | URL |
|----------|-----|
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| OpenAPI YAML | [`openapi.yml`](openapi-spec/openapi.yml) |

> **Note:** The Swagger UI and OpenAPI endpoints are enabled by default. Disable them in
> production by setting `springdoc.swagger-ui.enabled=false` and `springdoc.api-docs.enabled=false`.


### `GET /api/v1/weather/{city}`

Returns current conditions and a multi-day forecast for the given city. City names are case-insensitive.

| Parameter | Type   | Required | Description           |
|-----------|--------|----------|-----------------------|
| `city`    | string | Yes      | City name (path variable, must not be blank) |

### Response codes

| Status | Meaning                                                               |
|--------|-----------------------------------------------------------------------|
| `200`  | Success — weather data returned (from cache or live fetch)            |
| `400`  | Blank city name or malformed request parameter                        |
| `401`  | Invalid or missing `WEATHER_API_KEY`                                  |
| `404`  | City not recognised by the weather data provider                      |
| `429`  | Rate limit exceeded — more than 20 requests per minute from this IP   |
| `500`  | Unexpected internal error                                             |
| `502`  | Weather data provider returned a server error                         |
| `503`  | Weather data provider is unreachable (timeout / network failure)      |

## Running tests

```bash
./mvnw test
```

## Rate limiting

Each client IP is limited to **20 requests per minute** using a token-bucket
algorithm (Bucket4j). Bucket state is stored in Redis so the limit is enforced
consistently across all replicas. Exceeding the limit returns HTTP 429.

The client IP is resolved from the direct TCP connection by default. If the app
runs behind a trusted reverse proxy, set `TRUSTED_PROXIES` to its IP so that
`X-Forwarded-For` is used instead (see [Environment Variables](#environment-variables)).

## Security notes

| Area | Detail |
|------|--------|
| Redis exposure | The Redis container binds to `127.0.0.1:6379` only — not accessible from outside the host. |
| Redis auth | Set `REDIS_PASSWORD` to require authentication. Omit for passwordless local dev. |
| Rate limit IP spoofing | `X-Forwarded-For` is ignored unless the request arrives from a `TRUSTED_PROXIES` IP. |
| Swagger UI | Enabled by default. Disable in production via `springdoc.swagger-ui.enabled=false`. |
