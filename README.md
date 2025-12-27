# CryptoPlatform

A microservices-based platform for live crypto data.

## Architecture

The system consists of three main components:

1.  **Market Gateway** (`/market-gateway`)
    *   **Role**: The "Ingester".
    *   **Technology**: Spring Boot (WebFlux).
    *   **Function**: Connects to Kraken WebSocket API (v2), subscribes to ticker pairs (BTC/USD, ETH/USD), normalizes the data, and writes the latest snapshot to Redis.
    *   **Resilience**: Auto-reconnects with exponential backoff if the WebSocket drops.

2.  **Redis**
    *   **Role**: The "State Store".
    *   **Function**: Holds the latest price snapshot for every symbol.
    *   **Data Structure**: Hashes (key: `latest:BTC-USD`, fields: `price`, `bid`, `ask`, `ts`, etc.).

3.  **API Service** (`/api`)
    *   **Role**: The "Gateway".
    *   **Technology**: Spring Boot (Web).
    *   **Function**: Exposes data to clients via REST and WebSockets.
        *   **REST**: Query latest prices on demand.
        *   **WebSocket**: Subscribe for real-time updates.

4.  **Redis Stream Schema** (`stream:market_ticks`)
    *   **Key**: `stream:market_ticks`
    *   **Values**: `symbol`, `ts`, `bid`, `ask`, `last`, `volume24h`, `change24h`.
    *   **Consumer Group**: `api-ws` (Logic), `api-ws-{uuid}` (Instance broadcast).

---

## Service Layout

| Service | Port | Description |
| :--- | :--- | :--- |
| **API** | `8080` | Main entry point for clients. |
| **Market Gateway** | `8081` | Backend service for Kraken connectivity. |
| **Postgres** | `5432` | Relational DB (Provisioned, currently unused). |
| **Redis** | `6379` | Fast key-value store for live market data. |

---

## Quick Start

1.  **Start the Stack**:
    ```bash
    docker compose up --build
    ```

2.  **Verify APIs**:
    ```bash
    # Get supported markets
    curl http://localhost:8080/markets
    
    # Get latest snapshot
    curl http://localhost:8080/prices/latest
    ```

3.  **Test Live Updates (WebSocket)**:
    *   Open your browser to: [http://localhost:8080/test-ws.html](http://localhost:8080/test-ws.html)
    *   You should see a log of "Connected" followed by ticking price updates.

---

## API Documentation

### REST Endpoints
*   `GET /markets`: List supported symbols.
*   `GET /prices/latest`: Map of all latest prices.
*   `GET /prices/latest?symbol=BTC-USD`: Specific ticker.

### WebSocket Protocol
*   **Endpoint**: `ws://localhost:8080/ws/prices`
*   **Subscribe**:
    ```json
    { "type": "subscribe", "symbols": ["BTC-USD", "ETH-USD"] }
    ```
*   **Update (Server -> Client)**:
    ```json
    { "type": "tick", "symbol": "BTC-USD", "last": 95000.0, "ts": 1700000000 }
    ```

### Authentication
*   **Sign Up**:
    ```bash
    curl -X POST http://localhost:8080/auth/signup \
      -H "Content-Type: application/json" \
      -d '{"email":"user@test.com", "password":"password"}'
    ```
*   **Log In**:
    ```bash
    curl -X POST http://localhost:8080/auth/login \
      -H "Content-Type: application/json" \
      -d '{"email":"user@test.com", "password":"password"}'
    ```
    *Returns: `{"token": "eyJhbG..."}`*
*   **Get Balance**:
    ```bash
    curl http://localhost:8080/account/balance \
      -H "Authorization: Bearer <YOUR_TOKEN>"
    ```

## Debugging & Inspection

### 1. Service Ports
| Service | Port | Access URL |
| :--- | :--- | :--- |
| **Api REST** | `8080` | `http://localhost:8080` |
| **Api WebSocket** | `8080` | `ws://localhost:8080/ws/prices` |
| **Dashboard** | `8080` | `http://localhost:8080/index.html` |
| **Market Gateway** | `8081` | `http://localhost:8081/health` |
| **Redis** | `6379` | `localhost:6379` (via tool or CLI) |
| **Postgres** | `5432` | `localhost:5432` |

### 2. Inspecting Redis (The data source)
To see if data is actually flowing from Kraken -> Redis, you can query Redis directly inside its container.

**One-off check:**
```bash
docker compose exec redis redis-cli HGETALL latest:BTC-USD
```
*   `docker compose exec redis`: Tells Docker to run a command inside the running container named `redis`.
*   `redis-cli`: The command line tool for Redis.
*   `HGETALL latest:BTC-USD`: "Get all fields from the Hash map named `latest:BTC-USD`".

**Live Watch (See it update):**
Run this to poll every second:
```bash
watch -n 1 "docker compose exec redis redis-cli HGETALL latest:BTC-USD"
```
*(Press `Ctrl+C` to exit)*

### 3. Checking Logs
If something isn't working, check the logs to see errors or connection drops.

**Tail all logs:**
```bash
docker compose logs -f
```

**Tail specific service (e.g., market-gateway):**
```bash
docker compose logs -f market-gateway
```
*   `-f`: Follow the logs (live stream) instead of just printing the last few lines.

## Development

*   **Build**: `./gradlew bootJar` (in each service folder).
*   **Logs**: `docker compose logs -f [service_name]`.
