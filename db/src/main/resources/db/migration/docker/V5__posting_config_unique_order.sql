-- Prevent duplicate order_seq within the same request_type
ALTER TABLE posting_config
    ADD CONSTRAINT uq_posting_config_request_type_order
        UNIQUE (request_type, order_seq);
