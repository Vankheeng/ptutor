ALTER TABLE favorite_tutors
    ADD COLUMN teaching_request_id uuid REFERENCES teaching_requests (id);
