-- =============================================================================
-- Admin User Init Script  (LOCAL DEV ONLY — do not run in production)
-- =============================================================================
--
-- Default credentials:
--   email   : admin@dkp.local
--   password: password   ← plaintext for the hash below (change before use)
--
-- The PASSWORD column stores a BCrypt(10) hash.
-- To regenerate a hash for a different password, pick one of:
--
--   1) Java one-liner:
--        new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("YourPassword")
--
--   2) Online generator: https://bcrypt-generator.com  (rounds = 10)
--
--   Then replace the value below.
-- =============================================================================

INSERT INTO product.USER (
    USER_ID,
    USER_UUID,
    EMAIL,
    USERNAME,
    PASSWORD,
    FIRST_NAME,
    LAST_NAME,
    PROVIDER,
    ROLE,
    EMAIL_VERIFIED,
    STATUS,
    ENABLED,
    USR_CREATION,
    DTE_CREATION,
    USR_LAST_MODIFICATION,
    DTE_LAST_MODIFICATION,
    VERSION
)
SELECT
    nextval('product.user_seq'),
    gen_random_uuid()::text,
    'admin@dkp.local',
    'admin',
    '$2a$12$jeY/J10Ndzgyv9YUCx6GC.3E7sEciO0Ry9TJoP16QNwW/6uJE7Pxi',   -- plaintext: "password"
    'Admin',
    NULL,
    'LOCAL',
    'ADMIN',
    true,
    'OFFLINE',
    true,
    'init',
    NOW(),
    'init',
    NOW(),
    0
WHERE NOT EXISTS (
    SELECT 1 FROM product.USER WHERE EMAIL = 'admin@dkp.local'
);
