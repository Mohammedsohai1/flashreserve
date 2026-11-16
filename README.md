# FlashReserve --- High-Concurrency Inventory Reservation Platform

FlashReserve is a Spring Boot backend for a flash-sale style commerce
workflow. It focuses on safe inventory reservation under concurrent
requests, duplicate-request protection, order lifecycle management,
payment verification, and asynchronous order events.

**Live API documentation:**
https://flashreserve.duckdns.org/swagger-ui/index.html\
**OpenAPI specification:** https://flashreserve.duckdns.org/v3/api-docs\
**Docker image:** `sohai1007/flashreserve-app:latest`

> **Deployment note:** The public deployment is a demo/staging
> environment. Razorpay is configured for Test Mode and email is
> configured for Mailtrap Sandbox. Do not use it to process real
> customer payments or send production notifications.

------------------------------------------------------------------------

## Table of contents

-   [Core capabilities](#core-capabilities)
-   [Architecture](#architecture)
-   [Key engineering workflows](#key-engineering-workflows)
-   [Technology stack](#technology-stack)
-   [API overview](#api-overview)
-   [Pagination, filtering, and
    sorting](#pagination-filtering-and-sorting)
-   [Run locally with Docker Compose](#run-locally-with-docker-compose)
-   [Configuration](#configuration)
-   [Database migrations](#database-migrations)
-   [Testing](#testing)
-   [Deployment](#deployment)
-   [Security notes](#security-notes)
-   [Known limitations and next
    steps](#known-limitations-and-next-steps)

## Core capabilities

### Inventory and order management

-   Create products with an initial inventory quantity (ADMIN
    operation).
-   Browse products and retrieve product details.
-   Search products by name.
-   Filter products by minimum price, maximum price, and in-stock
    status.
-   Create customer orders while reserving inventory.
-   Retrieve an order by ID with customer-ownership enforcement.
-   Cancel eligible unpaid orders and restore their reserved inventory.
-   Paginated administrator order listing.
-   Automatically expire unpaid orders after a configurable time window.

### Idempotent order creation

-   `POST /orders` requires an `Idempotency-Key` request header.
-   A SHA-256 fingerprint is generated from the product ID, quantity,
    and normalized customer email.
-   The idempotency key is locked in the database while the request is
    processed.
-   Repeating the same key and same request returns the previously
    created order instead of creating another one.
-   Reusing the same key with a different request fingerprint is
    rejected as an idempotency conflict.

This protects order creation against client retries, network timeouts,
and concurrent duplicate submissions.

### Concurrency-aware inventory handling

-   Inventory reservation uses a database-side conditional quantity
    decrement. The operation succeeds only when sufficient stock is
    available; otherwise the request fails.
-   Order cancellation obtains a database row lock for the order before
    changing its state and restoring inventory.
-   Order state checks prevent cancellation of orders that are already
    paid or otherwise not eligible.
-   Transaction boundaries keep the order and inventory changes together
    so a failed order operation does not leave a partial reservation.

### Redis caching

-   Redis is used as the Spring cache provider.
-   Product search results are cached using the search/filter/page/sort
    inputs as part of the cache key.
-   Product detail data is cached separately.
-   Product and inventory mutations trigger product-search cache
    eviction after transaction commit.
-   Cache TTL and null-cache behavior are configured centrally.

### Kafka and reliable event handling

-   Kafka is used for asynchronous order-related event processing.
-   A transactional outbox stores events in PostgreSQL so event creation
    participates in the business transaction.
-   An outbox publisher polls for pending events and publishes them to
    Kafka.
-   Published and failed event states are recorded for retry/diagnostic
    handling.
-   Processed-event tracking supports duplicate event detection by
    consumers.

### Payments and webhooks

-   Creates Razorpay orders for application orders.
-   Verifies the Razorpay checkout signature.
-   Fetches payment details from Razorpay and checks order ID, amount,
    and currency against the application payment record.
-   Handles captured, authorized, and failed payment states.
-   Receives Razorpay webhooks and verifies/processes webhook events.
-   Prevents payment operations from being performed against another
    customer's order.

### Authentication and authorization

-   User registration and login.
-   Stateless JWT access tokens using HS256.
-   BCrypt password hashing.
-   Role-based authorization for ADMIN operations.
-   Customer ownership checks for order and payment access.
-   Password-reset flow using OTP verification and a reset token.

### Notifications and scheduled tasks

-   Email notifications for order-related events.
-   Password-reset OTP email.
-   Scheduled expiration of unpaid orders; expired orders are cancelled
    and reserved inventory is restored.
-   Configurable order expiration timeout and polling interval.

### API documentation and validation

-   OpenAPI/Swagger UI documentation.
-   Request validation using Jakarta Bean Validation.
-   Centralized error response handling.
-   Explicit pagination bounds and allowed product sort fields.

------------------------------------------------------------------------

## Architecture

``` text
                         Clients
                  (Web UI / API consumers)
                           |
                         HTTPS
                           |
                    Nginx reverse proxy
                           |
                    Spring Boot API
                     (Java 21)
                           |
       +-------------------+--------------------+
       |                   |                    |
   PostgreSQL             Redis                Kafka
       |               Cache layer          Event broker
       |                   |                    ^
       |                   |                    |
       +-- Orders          +-- Product          |
       +-- Products            search/details  |
       +-- Inventory                              |
       +-- Idempotency keys                        |
       +-- Payments                                |
       +-- Outbox events --------------------------+
       +-- Processed events

                    External integrations
                     |              |
                  Razorpay       SMTP provider
                  payments       (Mailtrap in demo)
```

The application, PostgreSQL, Redis, and Kafka run as separate Docker
Compose services. Nginx terminates public HTTPS traffic and
reverse-proxies requests to the application.

## Key engineering workflows

### 1. Order creation and idempotency

1.  The authenticated customer submits `POST /orders` with an
    `Idempotency-Key`.
2.  The API verifies that the request email matches the authenticated
    JWT subject.
3.  The service calculates a request fingerprint.
4.  The idempotency row is created/locked and checked.
5.  If the key already exists with the same fingerprint, the original
    order response is returned.
6.  If the key exists with a different fingerprint, the request is
    rejected.
7.  For a new key, the service checks the product and reserves stock
    using the database inventory update.
8.  The order and idempotency record are saved in the transaction.

### 2. Inventory reservation

The inventory repository performs a conditional database update rather
than relying on a read-then-write quantity change. This avoids
overselling when concurrent requests attempt to reserve the last
available units. A reservation that cannot decrement the requested
quantity is rejected as insufficient inventory.

### 3. Order cancellation and expiration

Cancellation locks the order row, validates its current state and
ownership, changes the order to `CANCELLED`, and restores each order
item's quantity. A scheduled task periodically finds old unpaid
`CREATED` orders and invokes the same cancellation workflow.

### 4. Payment confirmation

The application creates a Razorpay order for an application order. On
verification, it checks the checkout signature and fetches the payment
from Razorpay. The fetched Razorpay order ID, amount, and currency are
compared with the stored payment record before the application marks
payment as captured. Webhook processing is also available as a payment
status notification path.

### 5. Asynchronous events

Business events are recorded in the PostgreSQL outbox as part of the
transaction. A publisher sends pending outbox records to Kafka. The
application tracks publication failures and processed events so event
handling can be retried and duplicate processing can be detected.

------------------------------------------------------------------------

## Technology stack

Area                    Technology
  ----------------------- --------------------------------------
Language                Java 21
Backend framework       Spring Boot 4.1.1
API                     Spring MVC / REST
Persistence             Spring Data JPA, Hibernate
Database                PostgreSQL 17
Schema migrations       Flyway
Caching                 Redis 7, Spring Cache
Messaging               Apache Kafka 4.1.1
Authentication          Spring Security, JWT (HS256), BCrypt
Payments                Razorpay Java SDK
Email                   Spring Mail / SMTP
API documentation       SpringDoc OpenAPI / Swagger UI
Containerization        Docker, Docker Compose
Reverse proxy and TLS   Nginx, Let's Encrypt / Certbot
Build                   Maven

------------------------------------------------------------------------

## API overview

The deployed Swagger UI is the authoritative interactive API reference:

**https://flashreserve.duckdns.org/swagger-ui/index.html**

  -----------------------------------------------------------------------------------------
Method            Endpoint                       Purpose                Access
  ----------------- ------------------------------ ---------------------- -----------------
POST              `/auth/register`               Register a user        Public

POST              `/auth/login`                  Authenticate and       Public
receive JWT

POST              `/auth/forgot-password`        Request password-reset Public
OTP

POST              `/auth/verify-otp`             Verify reset OTP       Public

POST              `/auth/reset-password`         Reset password using   Public
reset token

GET               `/products`                    Browse/search/filter   Public
products

GET               `/products/{productId}`        Get product details    Public

POST              `/products`                    Create product and     ADMIN
initial stock

POST              `/orders`                      Create order and       Authenticated
reserve stock          customer

GET               `/orders/{orderId}`            Retrieve an order      Owner or ADMIN

POST              `/orders/{orderId}/cancel`     Cancel an eligible     Owner or ADMIN
unpaid order

GET               `/orders`                      List orders with       ADMIN
pagination

POST              `/payments/orders/{orderId}`   Create Razorpay        Order owner
payment order

POST              `/payments/verify`             Verify payment         Order owner

POST              `/payments/webhook`            Receive Razorpay       Public endpoint;
webhook                signature checked
by webhook
handler
  -----------------------------------------------------------------------------------------

**Order creation example**

``` http
POST /orders
Authorization: Bearer <JWT>
Idempotency-Key: order-request-10001
Content-Type: application/json
```

``` json
{
  "productId": 1,
  "quantity": 2,
  "customerEmail": "customer@example.com"
}
```

Use a new idempotency key for a logically new order. Reuse the same key
when retrying the same request after a timeout.

------------------------------------------------------------------------

## Pagination, filtering, and sorting

### Product search

`GET /products` supports:

Parameter       Default Description
  ------------- --------- -----------------------------------------
`search`            --- Product-name search
`minPrice`          --- Minimum price
`maxPrice`          --- Maximum price
`inStock`           --- When `true`, return products with stock
`page`              `0` Zero-based page number
`size`             `10` Page size; maximum `100`
`sortBy`         `name` `id`, `name`, `price`, or `createdAt`
`direction`       `asc` `asc` or `desc`

Example:

``` http
GET /products?search=phone&minPrice=100&maxPrice=1000&inStock=true&page=0&size=10&sortBy=price&direction=asc
```

### Admin order listing

`GET /orders?page=0&size=20`

-   Zero-based page number.
-   Default page size is 20.
-   Maximum page size is 100.
-   Requires ADMIN authorization.

Spring Data `Page` responses include page metadata such as page number,
page size, total elements, and total pages.

------------------------------------------------------------------------

## Run locally with Docker Compose

### Prerequisites

-   Java 21 (for running/tests outside Docker)
-   Docker Desktop with Docker Compose
-   Git

### 1. Clone the repository

``` bash
git clone <YOUR_GITHUB_REPOSITORY_URL>
cd <YOUR_REPOSITORY_DIRECTORY>
```

### 2. Create a local `.env`

Create a `.env` file in the project root. Populate it with your own
development credentials. Do not commit this file.

``` dotenv
DB_USERNAME=postgres
DB_PASSWORD=replace_with_a_local_database_password

MAIL_HOST=your_smtp_host
MAIL_PORT=2525
MAIL_USERNAME=your_smtp_username
MAIL_PASSWORD=your_smtp_password
MAIL_FROM=your_verified_sender

RAZORPAY_KEY_ID=your_razorpay_test_key_id
RAZORPAY_KEY_SECRET=your_razorpay_test_key_secret
RAZORPAY_WEBHOOK_SECRET=your_webhook_secret

JWT_SECRET_BASE64=your_base64_encoded_secret_of_at_least_32_bytes
```

Use sandbox/test credentials for local development. The JWT secret must
decode to at least 32 bytes.

### 3. Start the services

``` bash
docker compose up -d --build
```

Check service status:

``` bash
docker compose ps
```

Follow application logs:

``` bash
docker compose logs -f app
```

### 4. Open the API

-   Swagger UI: http://localhost:8081/swagger-ui/index.html
-   OpenAPI JSON: http://localhost:8081/v3/api-docs
-   Products: http://localhost:8081/products

The local `compose.yaml` maps the application to host port `8081`,
PostgreSQL to `127.0.0.1:5433`, Redis to `127.0.0.1:6379`, and Kafka to
`127.0.0.1:9092`. Database, Redis, and Kafka ports are bound to loopback
for local development.

### 5. Stop the services

``` bash
docker compose down
```

This keeps named volumes. To delete local database, Redis, and Kafka
data as well:

``` bash
docker compose down -v
```

**Warning:** `down -v` permanently deletes the data stored in the
Compose volumes.

------------------------------------------------------------------------

## Configuration

The application reads configuration from environment variables. Do not
hardcode credentials in source files.

  -----------------------------------------------------------------------
Variable                            Purpose
  ----------------------------------- -----------------------------------
`DB_HOST`, `DB_PORT`, `DB_NAME`     PostgreSQL connection

`DB_USERNAME`, `DB_PASSWORD`        Database credentials

`REDIS_HOST`, `REDIS_PORT`          Redis connection

`KAFKA_BOOTSTRAP_SERVERS`           Kafka bootstrap address

`MAIL_HOST`, `MAIL_PORT`            SMTP server

`MAIL_USERNAME`, `MAIL_PASSWORD`,   SMTP credentials and sender
`MAIL_FROM`

`RAZORPAY_KEY_ID`,                  Razorpay API credentials
`RAZORPAY_KEY_SECRET`

`RAZORPAY_WEBHOOK_SECRET`           Razorpay webhook signature secret

`JWT_SECRET_BASE64`                 Base64-encoded JWT signing secret
-----------------------------------------------------------------------

Additional application properties configure the JWT issuer and 15-minute
token lifetime, product-cache TTL, order expiration timeout, and outbox
polling interval.

------------------------------------------------------------------------

## Database migrations

Flyway manages the schema. Migrations currently cover:

-   Products and inventory.
-   Orders and order items.
-   Idempotency keys, request fingerprints, and idempotency locking.
-   Transactional outbox events.
-   Processed-event tracking.
-   Payments and processed webhook events.
-   Customer email on orders.
-   Users and roles.

Hibernate is configured with `ddl-auto=validate`; schema changes are
expected to be managed through Flyway migrations.

------------------------------------------------------------------------

## Testing

The repository includes unit and integration tests covering areas such
as:

-   Application context startup.
-   Product search and product detail APIs.
-   Product and inventory cache invalidation.
-   Idempotency under concurrent requests.
-   Product inventory concurrency.
-   Order cancellation concurrency and rollback behavior.
-   Order transaction behavior.
-   Payment service behavior.
-   Razorpay webhook handling.

Run the test suite with:

``` bash
./mvnw test
```

On Windows:

``` powershell
.\mvnw.cmd test
```

**Current test note:** During development, one product-search cache
integration test was known to fail
(`ProductSearchCacheIntegrationTest.shouldReflectInventoryChangesAfterSearchCacheEviction`).
Re-run the complete suite and resolve/confirm this test before treating
the project as release-ready. Do not interpret the presence of tests as
proof that every test currently passes.

------------------------------------------------------------------------

## Deployment

The project contains:

-   `Dockerfile` --- multi-stage build for the Spring Boot application.
-   `compose.yaml` --- local development stack.
-   `compose.prod.yaml` --- EC2 deployment stack using the published
    Docker Hub image.
-   Nginx reverse proxy configuration on the EC2 host.
-   Let's Encrypt certificate managed by Certbot.

The demo deployment is available at:

-   **Swagger:** https://flashreserve.duckdns.org/swagger-ui/index.html
-   **Products API:** https://flashreserve.duckdns.org/products

The TLS certificate renewal was also verified using Certbot's dry-run
renewal command.

For the EC2 deployment, keep `.env` on the server with restrictive file
permissions and never commit it. PostgreSQL, Redis, and Kafka should not
be exposed directly to the public internet. Back up PostgreSQL data and
test restoration before relying on the deployment for important data.

------------------------------------------------------------------------

## Security notes

-   Never commit `.env`, API keys, JWT secrets, passwords, private keys,
    or production credentials.
-   Use separate credentials and secrets for local, staging, and
    production environments.
-   Rotate any credential that has accidentally been committed or
    exposed.
-   Keep database, Redis, and Kafka private; expose only the reverse
    proxy over HTTP/HTTPS.
-   Use Razorpay Test Mode for demos. Enable live payments only after
    configuring live credentials, webhooks, reconciliation, and
    operational controls.
-   Mailtrap Sandbox captures test messages; it does not deliver real
    customer email. Configure a production email provider and verify the
    sender/domain before real delivery.
-   Review Swagger exposure, rate limiting, monitoring, backups, and
    alerting before a production launch.

------------------------------------------------------------------------

## Known limitations and next steps

The current deployment demonstrates the application workflow but is not
a high-availability production architecture. Before a real flash sale or
production launch, consider:

1.  Resolve and add regression coverage for the known product-search
    cache integration-test failure.
2.  Run load and concurrency tests against realistic traffic and
    database sizing.
3.  Add metrics, dashboards, centralized logs, and alerting.
4.  Automate PostgreSQL backups and regularly test restore procedures.
5.  Use managed/high-availability database, Redis, and Kafka services or
    design appropriate replication and recovery.
6.  Add CI/CD with immutable image tags and automated test gates.
7.  Complete production Razorpay activation, webhook configuration,
    reconciliation, and production email setup.
8.  Review API rate limiting, secrets management, and Swagger access
    policy.

------------------------------------------------------------------------

## License

No license file is currently included. Add a license before presenting
the repository as open source.
