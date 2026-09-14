-- Extend SLA coverage to Problems and Change Requests (same pattern as
-- service_request_id: one nullable, unique FK per backing ticket type).
ALTER TABLE sla_instance
    ADD COLUMN problem_id UUID REFERENCES problem(id),
    ADD COLUMN change_request_id UUID REFERENCES change_request(id);

CREATE UNIQUE INDEX sla_instance_problem_key ON sla_instance(problem_id);
CREATE UNIQUE INDEX sla_instance_change_request_key ON sla_instance(change_request_id);
