ALTER TABLE review_provider_trace
    ADD COLUMN IF NOT EXISTS prompt_tokens INTEGER;

ALTER TABLE review_provider_trace
    ADD COLUMN IF NOT EXISTS completion_tokens INTEGER;

ALTER TABLE review_provider_trace
    ADD COLUMN IF NOT EXISTS total_tokens INTEGER;
