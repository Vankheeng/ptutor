ALTER TABLE student_tutor_requests
    ADD COLUMN preferred_province_id uuid,
    ADD COLUMN preferred_district_id uuid,
    ADD COLUMN detail_address varchar(255),
    ADD CONSTRAINT fk_student_tutor_requests_preferred_province
        FOREIGN KEY (preferred_province_id) REFERENCES provinces (id),
    ADD CONSTRAINT fk_student_tutor_requests_preferred_district
        FOREIGN KEY (preferred_district_id) REFERENCES districts (id);
