CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(30)
);

CREATE TABLE permissions (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(30)
);

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL REFERENCES roles(id),
    permission_id BIGINT NOT NULL REFERENCES permissions(id),
    PRIMARY KEY (role_id, permission_id)
);

INSERT INTO roles (title) VALUES
    ('USER'),
    ('ADMIN');