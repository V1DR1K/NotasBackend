ALTER TABLE app_users ADD COLUMN auth_user_id UUID;
ALTER TABLE app_users ALTER COLUMN password_hash DROP NOT NULL;
CREATE UNIQUE INDEX uq_app_users_auth_user_id ON app_users(auth_user_id) WHERE auth_user_id IS NOT NULL;
