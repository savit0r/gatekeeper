-- Seed standard default usage plans
INSERT INTO usage_plans (id, name, requests_per_window, window_seconds, created_at)
VALUES 
    ('00000000-0000-0000-0000-000000000001', 'Starter', 60, 60, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000002', 'Pro', 1000, 60, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000003', 'Enterprise', 10000, 60, CURRENT_TIMESTAMP)
ON CONFLICT (name) DO NOTHING;
