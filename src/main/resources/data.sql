INSERT INTO workorders.technicians
    (technician_id, external_user_id, full_name, email, phone, active)
VALUES
    ('a1000000-0000-4000-8000-000000000001', 'camila.rojas@digitalfix.local', 'Camila Rojas', 'camila.rojas@digitalfix.local', '+56911110001', TRUE),
    ('a1000000-0000-4000-8000-000000000002', 'diego.munoz@digitalfix.local', 'Diego Munoz', 'diego.munoz@digitalfix.local', '+56911110002', TRUE),
    ('a1000000-0000-4000-8000-000000000003', 'valentina.soto@digitalfix.local', 'Valentina Soto', 'valentina.soto@digitalfix.local', '+56911110003', TRUE)
ON CONFLICT (technician_id) DO UPDATE SET
    external_user_id = EXCLUDED.external_user_id,
    full_name = EXCLUDED.full_name,
    email = EXCLUDED.email,
    phone = EXCLUDED.phone,
    active = EXCLUDED.active;