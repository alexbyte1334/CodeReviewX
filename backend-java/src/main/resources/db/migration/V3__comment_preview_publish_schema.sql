-- Add GitHub publish result fields for local comment previews.

ALTER TABLE review_comment_preview ADD COLUMN IF NOT EXISTS github_comment_id BIGINT;
ALTER TABLE review_comment_preview ADD COLUMN IF NOT EXISTS publish_error_message VARCHAR(1000);
ALTER TABLE review_comment_preview ADD COLUMN IF NOT EXISTS published_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_comment_preview_publish_status
    ON review_comment_preview (publish_status);
