CREATE OR REPLACE FUNCTION swatch_billable_usage_update_last_modified_column()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.last_modified = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
