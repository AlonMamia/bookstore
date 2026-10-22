# Persistence rules

Read this before touching entities, repositories, migrations, mappers, filters, or
pagination.

## Flyway & schema

- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate validates the schema, it never
  creates or alters it. Any entity change that affects columns/constraints requires a
  matching migration, or the app fails to start (schema mismatch).
- Migrations live in `src/main/resources/db/migration`, named `Vx.y__description.sql`
  (see `V1__init_schema.sql` → `V1.5__add_50_books.sql`). Once a migration has shipped
  (merged, let alone deployed), never edit or delete it — Flyway checksums it and other
  environments have already applied it. Add a new version instead.
- `spring.flyway.schemas` is driven by `DB_SCHEMA` — pp and prod share one RDS instance
  but isolate via Postgres schema, not separate databases (see `infra/rds.tf`). Don't
  assume pp/prod have independent schemas when reasoning about data.

## Entities

- Lombok convention: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor` on
  entities (not `@Data` — entities don't get generated `equals`/`hashCode`/`toString`
  off all fields the way `@Data` would).
- Use `@Version` for optimistic locking on any entity that can be concurrently updated
  (see `Book.version`). If you add a new mutable, concurrently-written entity, consider
  whether it needs one too.
- Associations are `FetchType.LAZY` (see `Order`/`OrderItem`). When a query needs the
  association's data, fetch it explicitly (fetch join or `@EntityGraph`) rather than
  letting it lazy-load — especially inside any loop over a collection, which is an N+1.

## DTOs & mappers

- Controllers never return entities or accept them as request bodies. Responses are
  MapStruct-generated `*DtoOut` types (`@Mapper(componentModel = "spring")`); inputs are
  validated `*Request` types (jakarta.validation annotations).
- Add new mappings to the existing feature's mapper interface rather than creating a
  new mapper per DTO (see `BookMapper`, `UserMapper`).
- Paginated endpoints return `PageResponse<T>` (`shared/model`), built via
  `PageResponse.from(page)` — never return Spring Data's `Page<T>` directly from a
  controller.

## Filtering

- Multi-field, optional-criteria queries go through a `Specification` combined with
  `JpaSpecificationExecutor` (see `BookSpecification` + `BookFilter`), not hand-written
  JPQL/`@Query` string-concatenation. Filter record fields are boxed types (e.g.
  `Boolean`, not `boolean`) so an absent query param binds to "no filter" instead of
  failing `@ModelAttribute` binding.

## Transactions & concurrency

- No service in this codebase currently declares `@Transactional`. Most existing
  service methods are effectively a single repository call. If you add a multi-step
  write that must succeed or fail atomically, add `@Transactional` deliberately — don't
  assume it's already covered just because the method looks similar to an existing one.
- Uniqueness today is enforced at the DB level (`UNIQUE` constraints on `users.email`,
  `books.isbn`), and the existing "check exists, then insert" pattern
  (`AuthService.register`) is check-then-act: a losing race under concurrent requests
  surfaces as an unhandled `DataIntegrityViolationException` (generic 500 from the
  catch-all handler), not the friendly 409 the pre-check path produces. If you're
  reviewing or extending code with this pattern, call this out rather than silently
  treating it as already handled.
