CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    role           VARCHAR(20)  NOT NULL
);

CREATE TABLE books (
    id             BIGSERIAL PRIMARY KEY,
    title          VARCHAR(255)   NOT NULL,
    isbn           VARCHAR(20)    NOT NULL UNIQUE,
    price          NUMERIC(10, 2) NOT NULL,
    stock_quantity INTEGER        NOT NULL,
    author         VARCHAR(255)   NOT NULL,
    category       VARCHAR(100)   NOT NULL,
    version        BIGINT         NOT NULL DEFAULT 0
);

CREATE TABLE orders (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMP   NOT NULL
);

CREATE TABLE order_items (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT         NOT NULL REFERENCES orders (id),
    book_id    BIGINT         NOT NULL REFERENCES books (id),
    quantity   INTEGER        NOT NULL,
    unit_price NUMERIC(10, 2) NOT NULL
);

CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_book_id ON order_items (book_id);
