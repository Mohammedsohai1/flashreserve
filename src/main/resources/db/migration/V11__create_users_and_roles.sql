CREATE TABLE app_users
(
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_app_users_email UNIQUE (email),
    CONSTRAINT chk_app_users_role
        CHECK (role IN ('USER', 'ADMIN'))
);