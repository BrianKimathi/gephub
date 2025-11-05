INSERT INTO products (code, name) VALUES ('meets', 'Meets')
ON CONFLICT (code) DO NOTHING;

INSERT INTO products (code, name) VALUES ('builder', 'Builder')
ON CONFLICT (code) DO NOTHING;

