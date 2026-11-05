-- Fictional examples, loaded ONLY with the dev profile. Never real library hours.
-- Runs once per database, preserving subsequent edits and avoiding duplicate data.
DO $$
DECLARE
    ronald_id bigint;
    genaro_id bigint;
    room_id bigint;
    backup_id bigint;
    block_id bigint;
    demo_date date := (CURRENT_TIMESTAMP AT TIME ZONE 'America/Chicago')::date + 1;
BEGIN
    INSERT INTO member(name, email) VALUES ('Ronald (Demo)', 'ronald@example.test') RETURNING id INTO ronald_id;
    INSERT INTO member(name, email) VALUES ('Genaro (Demo)', 'genaro@example.test') RETURNING id INTO genaro_id;
    INSERT INTO room(code, name, capacity, type) VALUES ('DEMO-A', 'Demo Room A', 4, 'DEMO') RETURNING id INTO room_id;
    INSERT INTO room(code, name, capacity, type) VALUES ('DEMO-B', 'Demo Room B', 4, 'DEMO') RETURNING id INTO backup_id;
    INSERT INTO library_hours(weekday, opens_at, closes_at)
        SELECT day, time '08:00', time '24:00' FROM generate_series(1, 7) AS day;
    INSERT INTO study_block(kind, date, start_time, end_time, preferred_room_id)
        VALUES ('ONE_TIME', demo_date, '12:00', '21:00', room_id) RETURNING id INTO block_id;
    INSERT INTO study_block_backup_room VALUES (block_id, backup_id, 1);
    INSERT INTO shift(study_block_id, member_id, planned_room_id, starts_at, ends_at,
                      status, booked_room_id, booked_at)
        VALUES (block_id, ronald_id, room_id,
            (demo_date + time '12:00') AT TIME ZONE 'America/Chicago',
            (demo_date + time '15:00') AT TIME ZONE 'America/Chicago',
            'BOOKED', room_id, now());
    INSERT INTO shift(study_block_id, member_id, planned_room_id, starts_at, ends_at, status)
        VALUES (block_id, genaro_id, room_id,
            (demo_date + time '15:00') AT TIME ZONE 'America/Chicago',
            (demo_date + time '18:00') AT TIME ZONE 'America/Chicago', 'ASSIGNED');
    INSERT INTO shift(study_block_id, planned_room_id, starts_at, ends_at)
        VALUES (block_id, room_id,
            (demo_date + time '18:00') AT TIME ZONE 'America/Chicago',
            (demo_date + time '21:00') AT TIME ZONE 'America/Chicago');
END;
$$;
