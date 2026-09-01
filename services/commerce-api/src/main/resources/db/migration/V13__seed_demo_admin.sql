INSERT INTO users
  (id, account, display_name, password_hash, role, status, created_at, updated_at, version)
VALUES
  ('01JDEMOADMIN00000000000001', 'demo_admin', '演示管理员',
   '$2y$10$v3DEQfFKuonU05c7tjPA8umGqBpG9ooIRVRpQF.bgHh7bW4kAitUS',
   'DEMO_ADMIN', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0);
