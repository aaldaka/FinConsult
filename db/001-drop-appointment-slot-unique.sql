-- appointments.slot_id is no longer unique (OneToOne -> ManyToOne)
DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT tc.constraint_name
      INTO constraint_name
      FROM information_schema.table_constraints tc
      JOIN information_schema.key_column_usage kcu
        ON tc.constraint_name = kcu.constraint_name
       AND tc.table_schema = kcu.table_schema
     WHERE tc.table_name = 'appointments'
       AND tc.constraint_type = 'UNIQUE'
       AND kcu.column_name = 'slot_id'
     LIMIT 1;

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE appointments DROP CONSTRAINT %I', constraint_name);
        RAISE NOTICE 'Dropped unique constraint % on appointments.slot_id', constraint_name;
    ELSE
        RAISE NOTICE 'No unique constraint on appointments.slot_id - nothing to drop';
    END IF;
END $$;
