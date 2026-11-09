CREATE TABLE tenant (
    id                    CHAR(36)     NOT NULL PRIMARY KEY,
    name                  VARCHAR(200) NOT NULL,
    timezone              VARCHAR(64)  NOT NULL,
    cancel_cutoff_minutes INT          NOT NULL,
    hold_minutes          INT          NOT NULL
);

CREATE TABLE app_user (
    id            CHAR(36)     NOT NULL PRIMARY KEY,
    tenant_id     CHAR(36)     NOT NULL,
    email         VARCHAR(255) NOT NULL,
    full_name     VARCHAR(200) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    CONSTRAINT uk_user_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT fk_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);

CREATE TABLE service_type (
    id               CHAR(36)      NOT NULL PRIMARY KEY,
    tenant_id        CHAR(36)      NOT NULL,
    name             VARCHAR(200)  NOT NULL,
    duration_minutes INT           NOT NULL,
    buffer_minutes   INT           NOT NULL,
    price            DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_service_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);

CREATE TABLE provider (
    id        CHAR(36)     NOT NULL PRIMARY KEY,
    tenant_id CHAR(36)     NOT NULL,
    name      VARCHAR(200) NOT NULL,
    kind      VARCHAR(100) NOT NULL,
    active    BOOLEAN      NOT NULL,
    CONSTRAINT fk_provider_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);

CREATE TABLE provider_service (
    provider_id CHAR(36) NOT NULL,
    service_id  CHAR(36) NOT NULL,
    PRIMARY KEY (provider_id, service_id)
);

CREATE TABLE working_hours (
    id          CHAR(36) NOT NULL PRIMARY KEY,
    provider_id CHAR(36) NOT NULL,
    day_of_week INT      NOT NULL,
    start_time  TIME     NOT NULL,
    end_time    TIME     NOT NULL
);
CREATE INDEX idx_hours_provider_day ON working_hours (provider_id, day_of_week);

CREATE TABLE time_off (
    id          CHAR(36)     NOT NULL PRIMARY KEY,
    provider_id CHAR(36)     NOT NULL,
    start_at    TIMESTAMP(6) NOT NULL,
    end_at      TIMESTAMP(6) NOT NULL,
    reason      VARCHAR(255) NULL
);
CREATE INDEX idx_timeoff_provider ON time_off (provider_id, start_at);

CREATE TABLE booking (
    id               CHAR(36)     NOT NULL PRIMARY KEY,
    tenant_id        CHAR(36)     NOT NULL,
    provider_id      CHAR(36)     NOT NULL,
    service_id       CHAR(36)     NOT NULL,
    customer_id      CHAR(36)     NOT NULL,
    start_at         TIMESTAMP(6) NOT NULL,
    end_at           TIMESTAMP(6) NOT NULL,
    blocked_until    TIMESTAMP(6) NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    hold_expires_at  TIMESTAMP(6) NULL,
    reminder_sent_at TIMESTAMP(6) NULL,
    status_reason    VARCHAR(255) NULL,
    created_at       TIMESTAMP(6) NOT NULL,
    updated_at       TIMESTAMP(6) NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_booking_provider_start ON booking (tenant_id, provider_id, start_at);
CREATE INDEX idx_booking_customer ON booking (tenant_id, customer_id, start_at);
CREATE INDEX idx_booking_status_hold ON booking (status, hold_expires_at);
CREATE INDEX idx_booking_status_start ON booking (status, start_at);

-- The double-booking guard: one row per provider per 5-minute unit.
CREATE TABLE booked_unit (
    provider_id CHAR(36)     NOT NULL,
    unit_start  TIMESTAMP(6) NOT NULL,
    booking_id  CHAR(36)     NOT NULL,
    PRIMARY KEY (provider_id, unit_start)
);
CREATE INDEX idx_unit_booking ON booked_unit (booking_id);

CREATE TABLE waitlist_entry (
    id          CHAR(36)     NOT NULL PRIMARY KEY,
    tenant_id   CHAR(36)     NOT NULL,
    provider_id CHAR(36)     NOT NULL,
    service_id  CHAR(36)     NOT NULL,
    customer_id CHAR(36)     NOT NULL,
    wanted_date DATE         NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL
);
CREATE INDEX idx_waitlist_lookup ON waitlist_entry (tenant_id, provider_id, wanted_date, status, created_at);

CREATE TABLE notification_log (
    event_id    VARCHAR(64)  NOT NULL PRIMARY KEY,
    tenant_id   CHAR(36)     NOT NULL,
    customer_id CHAR(36)     NOT NULL,
    type        VARCHAR(50)  NOT NULL,
    message     VARCHAR(500) NOT NULL,
    sent_at     TIMESTAMP(6) NOT NULL
);
CREATE INDEX idx_notification_customer ON notification_log (tenant_id, customer_id, sent_at);

CREATE TABLE outbox_event (
    id           CHAR(36)     NOT NULL PRIMARY KEY,
    aggregate_id VARCHAR(64)  NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6) NULL
);
CREATE INDEX idx_outbox_pending ON outbox_event (published_at, created_at);
