INSERT INTO users
  (id, account, display_name, password_hash, role, status, created_at, updated_at, version)
VALUES
  ('01JDEMOUSER000000000000001', 'demo_user', '演示用户',
   '$2y$10$v3DEQfFKuonU05c7tjPA8umGqBpG9ooIRVRpQF.bgHh7bW4kAitUS',
   'DEMO_USER', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  ('01JDEMOUSER000000000000002', 'disabled_user', '停用演示用户',
   '$2y$10$v3DEQfFKuonU05c7tjPA8umGqBpG9ooIRVRpQF.bgHh7bW4kAitUS',
   'DEMO_USER', 'DISABLED', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0);
